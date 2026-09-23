package com.example.domain

import android.net.Uri

data class ImageInfo(
    val uri: Uri,
    val filename: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String
) {
    val formattedSize: String
        get() = formatBytes(sizeBytes)
}

enum class OutputFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

sealed class CompressionMode {
    data class TargetSize(val targetBytes: Long) : CompressionMode()
    data class Quality(val quality: Int) : CompressionMode()
    data class Resize(
        val scalePercent: Int = 80,
        val customWidth: Int? = null,
        val customHeight: Int? = null,
        val preserveAspectRatio: Boolean = true
    ) : CompressionMode()
}

data class CompressionConfig(
    val mode: CompressionMode = CompressionMode.TargetSize(100 * 1024L),
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val preserveExif: Boolean = false,
    val keepAspectRatio: Boolean = true,
    val minQuality: Int = 5,
    val minDimension: Int = 80
)

data class CompressionResult(
    val isSuccess: Boolean,
    val originalImage: ImageInfo,
    val compressedUri: Uri? = null,
    val compressedFilePath: String? = null,
    val compressedSizeBytes: Long = 0L,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val targetAchieved: Boolean = false,
    val reductionPercentage: Float = 0f,
    val processingTimeMs: Long = 0L,
    val errorMessage: String? = null
) {
    val formattedCompressedSize: String
        get() = formatBytes(compressedSizeBytes)

    val formattedOriginalSize: String
        get() = originalImage.formattedSize

    val savedBytes: Long
        get() = (originalImage.sizeBytes - compressedSizeBytes).coerceAtLeast(0L)

    val formattedSavedBytes: String
        get() = formatBytes(savedBytes)
}

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class BatchItem(
    val id: String,
    val image: ImageInfo,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val result: CompressionResult? = null,
    val error: String? = null
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0

    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        bytes >= kb -> String.format("%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
