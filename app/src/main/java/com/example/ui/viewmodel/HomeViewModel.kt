package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.AdManager
import com.example.data.db.AppDatabase
import com.example.data.db.HistoryEntity
import com.example.data.file.FileManager
import com.example.data.repository.HistoryRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.BatchItem
import com.example.domain.BatchItemStatus
import com.example.domain.CompressionConfig
import com.example.domain.CompressionMode
import com.example.domain.CompressionResult
import com.example.domain.ImageCompressionEngine
import com.example.domain.ImageInfo
import com.example.domain.OutputFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedImages: List<ImageInfo> = emptyList(),
    val targetBytes: Long = 100 * 1024L,
    val isCustomTarget: Boolean = false,
    val customTargetLabel: String? = null,
    val mode: CompressionMode = CompressionMode.TargetSize(100 * 1024L),
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val isProcessing: Boolean = false,
    val singleResult: CompressionResult? = null,
    val batchItems: List<BatchItem> = emptyList(),
    val batchCurrentIndex: Int = 0,
    val userMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = FileManager(application)
    private val engine = ImageCompressionEngine(application)
    private val database = AppDatabase.getInstance(application)
    private val historyRepository = HistoryRepository(database.historyDao())
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var batchJob: Job? = null

    init {
        // Load initial settings
        val settings = settingsRepository.settings.value
        val target = (settings.defaultTargetKb * 1024).toLong()
        val format = try {
            OutputFormat.valueOf(settings.defaultFormat)
        } catch (_: Exception) {
            OutputFormat.JPEG
        }

        _uiState.update {
            it.copy(
                targetBytes = target,
                mode = CompressionMode.TargetSize(target),
                outputFormat = format
            )
        }
    }

    fun onImagesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val newImages = uris.mapNotNull { fileManager.extractImageInfo(it) }
            _uiState.update { state ->
                val combined = (state.selectedImages + newImages).distinctBy { it.uri.toString() }
                state.copy(
                    selectedImages = combined,
                    singleResult = null
                )
            }
        }
    }

    fun removeImage(image: ImageInfo) {
        _uiState.update { state ->
            state.copy(selectedImages = state.selectedImages.filterNot { it.uri == image.uri })
        }
    }

    fun clearImages() {
        _uiState.update {
            it.copy(
                selectedImages = emptyList(),
                singleResult = null,
                batchItems = emptyList()
            )
        }
    }

    fun setTargetBytes(bytes: Long, customLabel: String? = null) {
        _uiState.update { state ->
            state.copy(
                targetBytes = bytes,
                isCustomTarget = customLabel != null,
                customTargetLabel = customLabel,
                mode = if (state.mode is CompressionMode.TargetSize) {
                    CompressionMode.TargetSize(bytes)
                } else {
                    state.mode
                }
            )
        }
    }

    fun setMode(mode: CompressionMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setOutputFormat(format: OutputFormat) {
        _uiState.update { it.copy(outputFormat = format) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * Compress single selected image.
     */
    fun compressSingle(activity: Activity?, onComplete: () -> Unit) {
        val image = _uiState.value.selectedImages.firstOrNull() ?: return
        val currentSettings = settingsRepository.settings.value

        val config = CompressionConfig(
            mode = _uiState.value.mode,
            outputFormat = _uiState.value.outputFormat,
            preserveExif = !currentSettings.removeExif,
            keepAspectRatio = currentSettings.preserveAspectRatio
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            val result = engine.compress(image, config)

            if (result.isSuccess) {
                // Auto-save if enabled in preferences
                if (currentSettings.autoSave && result.compressedFilePath != null) {
                    fileManager.saveToGallery(result.compressedFilePath)
                }

                // Record in History database
                historyRepository.insertHistory(
                    HistoryEntity(
                        originalFilename = result.originalImage.filename,
                        originalSizeBytes = result.originalImage.sizeBytes,
                        compressedSizeBytes = result.compressedSizeBytes,
                        targetSizeBytes = if (config.mode is CompressionMode.TargetSize) config.mode.targetBytes else 0L,
                        format = result.outputFormat.extension.uppercase(),
                        width = result.outputWidth,
                        height = result.outputHeight,
                        reductionPercentage = result.reductionPercentage,
                        filePath = result.compressedFilePath ?: "",
                        isTargetAchieved = result.targetAchieved
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    singleResult = result
                )
            }

            // Show interstitial ad safely at workflow completion if available
            if (activity != null) {
                AdManager.showInterstitial(activity) {
                    onComplete()
                }
            } else {
                onComplete()
            }
        }
    }

    /**
     * Start Batch compression of multiple images sequentially.
     */
    fun startBatchCompression(activity: Activity?, onComplete: () -> Unit) {
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) return

        val items = images.mapIndexed { index, image ->
            BatchItem(
                id = "${index}_${image.filename}",
                image = image,
                status = BatchItemStatus.PENDING
            )
        }

        _uiState.update {
            it.copy(
                isProcessing = true,
                batchItems = items,
                batchCurrentIndex = 0
            )
        }

        val currentSettings = settingsRepository.settings.value
        val config = CompressionConfig(
            mode = _uiState.value.mode,
            outputFormat = _uiState.value.outputFormat,
            preserveExif = !currentSettings.removeExif,
            keepAspectRatio = currentSettings.preserveAspectRatio
        )

        batchJob = viewModelScope.launch {
            for (i in items.indices) {
                if (batchJob?.isCancelled == true) break

                _uiState.update { state ->
                    val updatedItems = state.batchItems.toMutableList()
                    updatedItems[i] = updatedItems[i].copy(status = BatchItemStatus.PROCESSING)
                    state.copy(batchItems = updatedItems, batchCurrentIndex = i)
                }

                val currentItem = items[i]
                val result = engine.compress(currentItem.image, config)

                if (result.isSuccess) {
                    if (currentSettings.autoSave && result.compressedFilePath != null) {
                        fileManager.saveToGallery(result.compressedFilePath)
                    }

                    historyRepository.insertHistory(
                        HistoryEntity(
                            originalFilename = result.originalImage.filename,
                            originalSizeBytes = result.originalImage.sizeBytes,
                            compressedSizeBytes = result.compressedSizeBytes,
                            targetSizeBytes = if (config.mode is CompressionMode.TargetSize) config.mode.targetBytes else 0L,
                            format = result.outputFormat.extension.uppercase(),
                            width = result.outputWidth,
                            height = result.outputHeight,
                            reductionPercentage = result.reductionPercentage,
                            filePath = result.compressedFilePath ?: "",
                            isTargetAchieved = result.targetAchieved
                        )
                    )
                }

                _uiState.update { state ->
                    val updatedItems = state.batchItems.toMutableList()
                    updatedItems[i] = updatedItems[i].copy(
                        status = if (result.isSuccess) BatchItemStatus.SUCCESS else BatchItemStatus.FAILED,
                        result = result,
                        error = result.errorMessage
                    )
                    state.copy(batchItems = updatedItems)
                }
            }

            _uiState.update { it.copy(isProcessing = false) }

            if (activity != null) {
                AdManager.showInterstitial(activity) {
                    onComplete()
                }
            } else {
                onComplete()
            }
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
        _uiState.update { state ->
            val updated = state.batchItems.map { item ->
                if (item.status == BatchItemStatus.PENDING || item.status == BatchItemStatus.PROCESSING) {
                    item.copy(status = BatchItemStatus.CANCELLED)
                } else item
            }
            state.copy(isProcessing = false, batchItems = updated)
        }
    }

    fun saveResultToGallery(result: CompressionResult): Boolean {
        val path = result.compressedFilePath ?: return false
        val uri = fileManager.saveToGallery(path)
        val success = uri != null
        _uiState.update {
            it.copy(
                userMessage = if (success) "Image saved to Pictures/Compressed!" else "Failed to save image"
            )
        }
        return success
    }

    fun shareResult(result: CompressionResult) {
        val path = result.compressedFilePath ?: return
        fileManager.shareImage(path)
    }

    fun openResult(result: CompressionResult) {
        val path = result.compressedFilePath ?: return
        fileManager.openImage(path)
    }

    fun copyResultDetails(result: CompressionResult) {
        val text = buildString {
            appendLine("— Compression Details —")
            appendLine("File: ${result.originalImage.filename}")
            appendLine("Original Size: ${result.formattedOriginalSize}")
            appendLine("Compressed Size: ${result.formattedCompressedSize}")
            appendLine("Saved: ${result.formattedSavedBytes} (${String.format("%.1f", result.reductionPercentage)}%)")
            appendLine("Dimensions: ${result.outputWidth} × ${result.outputHeight}")
            appendLine("Target Met: ${if (result.targetAchieved) "Yes" else "No"}")
        }
        fileManager.copyToClipboard("Compression Result", text)
        _uiState.update { it.copy(userMessage = "Details copied to clipboard") }
    }
}
