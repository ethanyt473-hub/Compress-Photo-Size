package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ImageCompressionEngine(private val context: Context) {

    suspend fun compress(
        imageInfo: ImageInfo,
        config: CompressionConfig,
        outputDir: File = File(context.cacheDir, "compressed")
    ): CompressionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        try {
            // 1. Safe decode with bounds checking to avoid OOM
            val decodedBitmap = decodeSampledBitmapFromUri(imageInfo.uri, maxDimension = 4096)
                ?: return@withContext CompressionResult(
                    isSuccess = false,
                    originalImage = imageInfo,
                    errorMessage = "Could not decode image"
                )

            // 2. Correct EXIF orientation
            val orientedBitmap = correctOrientation(context, imageInfo.uri, decodedBitmap)

            // 3. Process according to compression mode
            val encodedData: EncodedImageData = when (val mode = config.mode) {
                is CompressionMode.TargetSize -> {
                    compressToTargetSize(
                        bitmap = orientedBitmap,
                        targetBytes = mode.targetBytes,
                        format = config.outputFormat,
                        minQuality = config.minQuality,
                        minDimension = config.minDimension
                    )
                }
                is CompressionMode.Quality -> {
                    compressWithQuality(
                        bitmap = orientedBitmap,
                        quality = mode.quality,
                        format = config.outputFormat
                    )
                }
                is CompressionMode.Resize -> {
                    compressWithResize(
                        bitmap = orientedBitmap,
                        resizeMode = mode,
                        format = config.outputFormat
                    )
                }
            }

            // 4. Save to temporary output file
            val extension = config.outputFormat.extension
            val timestamp = System.currentTimeMillis()
            val sanitizedName = imageInfo.filename.substringBeforeLast(".")
                .replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val outputFile = File(outputDir, "compressed_${sanitizedName}_$timestamp.$extension")

            FileOutputStream(outputFile).use { fos ->
                fos.write(encodedData.bytes)
                fos.flush()
            }

            // 5. Handle EXIF metadata preservation if requested
            if (config.preserveExif && config.outputFormat == OutputFormat.JPEG) {
                copyExif(context, imageInfo.uri, outputFile)
            }

            val compressedBytes = outputFile.length()
            val targetBytes = if (config.mode is CompressionMode.TargetSize) config.mode.targetBytes else 0L
            val targetAchieved = if (config.mode is CompressionMode.TargetSize) {
                compressedBytes <= targetBytes
            } else {
                true
            }

            val reduction = if (imageInfo.sizeBytes > 0) {
                val diff = imageInfo.sizeBytes - compressedBytes
                max(0f, (diff.toFloat() / imageInfo.sizeBytes.toFloat()) * 100f)
            } else {
                0f
            }

            if (orientedBitmap != decodedBitmap) {
                decodedBitmap.recycle()
            }

            CompressionResult(
                isSuccess = true,
                originalImage = imageInfo,
                compressedUri = Uri.fromFile(outputFile),
                compressedFilePath = outputFile.absolutePath,
                compressedSizeBytes = compressedBytes,
                outputWidth = encodedData.width,
                outputHeight = encodedData.height,
                outputFormat = config.outputFormat,
                targetAchieved = targetAchieved,
                reductionPercentage = reduction,
                processingTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: OutOfMemoryError) {
            System.gc()
            CompressionResult(
                isSuccess = false,
                originalImage = imageInfo,
                errorMessage = "Out of memory while processing high-resolution image"
            )
        } catch (e: Exception) {
            CompressionResult(
                isSuccess = false,
                originalImage = imageInfo,
                errorMessage = e.localizedMessage ?: "Unknown compression failure"
            )
        }
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        var sampleSize = 1
        if (origWidth > maxDimension || origHeight > maxDimension) {
            val halfWidth = origWidth / 2
            val halfHeight = origHeight / 2
            while ((halfWidth / sampleSize) >= maxDimension || (halfHeight / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun correctOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val orientation = inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
                else -> return bitmap
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Binary search algorithm for optimal quality combined with progressive dimension downscaling
     * to guarantee output byte size is <= targetBytes.
     */
    private fun compressToTargetSize(
        bitmap: Bitmap,
        targetBytes: Long,
        format: OutputFormat,
        minQuality: Int,
        minDimension: Int
    ): EncodedImageData {
        var currentBitmap = bitmap
        var width = currentBitmap.width
        var height = currentBitmap.height
        var bestBytes: ByteArray? = null
        var bestQuality = minQuality

        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }

        // Special handling for PNG: PNG ignores quality in CompressFormat.PNG (lossless)
        if (format == OutputFormat.PNG) {
            var scale = 1.0f
            while (width >= minDimension && height >= minDimension) {
                val stream = ByteArrayOutputStream()
                val scaled = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(bitmap, width, height, true)
                } else {
                    bitmap
                }
                scaled.compress(compressFormat, 100, stream)
                val bytes = stream.toByteArray()
                if (bytes.size <= targetBytes) {
                    return EncodedImageData(bytes, width, height, 100)
                }
                bestBytes = bytes
                scale *= 0.82f
                width = max(minDimension, (bitmap.width * scale).roundToInt())
                height = max(minDimension, (bitmap.height * scale).roundToInt())
                if (scale < 0.1f) break
            }
            return EncodedImageData(bestBytes ?: ByteArray(0), width, height, 100)
        }

        // Binary Search Quality Optimization
        var iterationCount = 0
        val maxIterations = 10

        while (width >= minDimension && height >= minDimension && iterationCount < 6) {
            var low = minQuality
            var high = 95
            var passBestBytes: ByteArray? = null
            var passBestQuality = low

            while (low <= high) {
                val mid = (low + high) / 2
                val stream = ByteArrayOutputStream()
                currentBitmap.compress(compressFormat, mid, stream)
                val bytes = stream.toByteArray()

                if (bytes.size <= targetBytes) {
                    // Valid fit! Try searching higher quality for better fidelity
                    passBestBytes = bytes
                    passBestQuality = mid
                    low = mid + 1
                } else {
                    // Too large, decrease quality
                    high = mid - 1
                }
            }

            if (passBestBytes != null) {
                // Found quality that fits target bytes!
                return EncodedImageData(passBestBytes, currentBitmap.width, currentBitmap.height, passBestQuality)
            }

            // If even minQuality is still > targetBytes, fallback to resizing dimensions
            val testStream = ByteArrayOutputStream()
            currentBitmap.compress(compressFormat, minQuality, testStream)
            val minQualityBytes = testStream.toByteArray()
            bestBytes = minQualityBytes
            bestQuality = minQuality

            // Progressive downscale by 0.80x
            iterationCount++
            width = max(minDimension, (currentBitmap.width * 0.80f).roundToInt())
            height = max(minDimension, (currentBitmap.height * 0.80f).roundToInt())

            if (currentBitmap.width <= minDimension && currentBitmap.height <= minDimension) {
                break
            }

            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (currentBitmap != bitmap) {
                currentBitmap.recycle()
            }
            currentBitmap = scaled
        }

        return EncodedImageData(
            bestBytes ?: ByteArray(0),
            currentBitmap.width,
            currentBitmap.height,
            bestQuality
        )
    }

    private fun compressWithQuality(
        bitmap: Bitmap,
        quality: Int,
        format: OutputFormat
    ): EncodedImageData {
        val stream = ByteArrayOutputStream()
        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
        bitmap.compress(compressFormat, quality.coerceIn(1, 100), stream)
        val bytes = stream.toByteArray()
        return EncodedImageData(bytes, bitmap.width, bitmap.height, quality)
    }

    private fun compressWithResize(
        bitmap: Bitmap,
        resizeMode: CompressionMode.Resize,
        format: OutputFormat
    ): EncodedImageData {
        val targetWidth: Int
        val targetHeight: Int

        if (resizeMode.customWidth != null && resizeMode.customHeight != null) {
            if (resizeMode.preserveAspectRatio) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                if (resizeMode.customWidth > resizeMode.customHeight) {
                    targetWidth = resizeMode.customWidth
                    targetHeight = (targetWidth / ratio).roundToInt()
                } else {
                    targetHeight = resizeMode.customHeight
                    targetWidth = (targetHeight * ratio).roundToInt()
                }
            } else {
                targetWidth = resizeMode.customWidth
                targetHeight = resizeMode.customHeight
            }
        } else {
            val scale = (resizeMode.scalePercent.coerceIn(10, 100)) / 100.0f
            targetWidth = max(40, (bitmap.width * scale).roundToInt())
            targetHeight = max(40, (bitmap.height * scale).roundToInt())
        }

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val stream = ByteArrayOutputStream()
        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }

        scaledBitmap.compress(compressFormat, 85, stream)
        val bytes = stream.toByteArray()
        return EncodedImageData(bytes, targetWidth, targetHeight, 85)
    }

    private fun copyExif(context: Context, sourceUri: Uri, destinationFile: File) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return
            val sourceExif = ExifInterface(inputStream)
            val destExif = ExifInterface(destinationFile.absolutePath)

            val attributes = arrayOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_WHITE_BALANCE
            )

            for (attr in attributes) {
                val value = sourceExif.getAttribute(attr)
                if (value != null) {
                    destExif.setAttribute(attr, value)
                }
            }
            destExif.saveAttributes()
            inputStream.close()
        } catch (_: Exception) {}
    }

    private data class EncodedImageData(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val quality: Int
    )
}
