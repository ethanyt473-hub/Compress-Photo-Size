package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.CompressionConfig
import com.example.domain.CompressionMode
import com.example.domain.ImageCompressionEngine
import com.example.domain.ImageInfo
import com.example.domain.OutputFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompressionEngineTest {

    private lateinit var context: Context
    private lateinit var engine: ImageCompressionEngine
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = ImageCompressionEngine(context)
        testDir = File(context.cacheDir, "unit_test_images").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun createTestBitmapFile(name: String, width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.BLUE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw multiple shapes to create byte variance
        paint.color = Color.RED
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, paint)
        paint.color = Color.YELLOW
        canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val file = File(testDir, name)
        FileOutputStream(file).use { fos ->
            bitmap.compress(format, 95, fos)
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun testTarget100KbCompression() = runBlocking {
        val file = createTestBitmapFile("large_photo_100kb.jpg", 1600, 1200)
        val imageInfo = ImageInfo(
            uri = Uri.fromFile(file),
            filename = file.name,
            sizeBytes = file.length(),
            width = 1600,
            height = 1200,
            mimeType = "image/jpeg"
        )

        val targetBytes = 100 * 1024L
        val config = CompressionConfig(
            mode = CompressionMode.TargetSize(targetBytes),
            outputFormat = OutputFormat.JPEG
        )

        val result = engine.compress(imageInfo, config)

        assertTrue("Compression should succeed", result.isSuccess)
        assertNotNull("Output path should not be null", result.compressedFilePath)
        val outputFile = File(result.compressedFilePath!!)
        assertTrue("Output file must exist on disk", outputFile.exists())
        assertTrue("Original file must be preserved", file.exists())
        assertTrue("Output size must be <= target size", result.compressedSizeBytes <= targetBytes)
        assertTrue("Target achieved flag must be true", result.targetAchieved)
    }

    @Test
    fun testTarget50KbCompression() = runBlocking {
        val file = createTestBitmapFile("photo_50kb.jpg", 1200, 900)
        val targetBytes = 50 * 1024L
        val imageInfo = ImageInfo(
            uri = Uri.fromFile(file),
            filename = file.name,
            sizeBytes = file.length(),
            width = 1200,
            height = 900,
            mimeType = "image/jpeg"
        )

        val config = CompressionConfig(
            mode = CompressionMode.TargetSize(targetBytes),
            outputFormat = OutputFormat.JPEG
        )

        val result = engine.compress(imageInfo, config)

        assertTrue(result.isSuccess)
        assertTrue("Compressed size must be <= 50 KB", result.compressedSizeBytes <= targetBytes)
    }

    @Test
    fun testTarget500KbCompression() = runBlocking {
        val file = createTestBitmapFile("photo_500kb.jpg", 2000, 1500)
        val targetBytes = 500 * 1024L
        val imageInfo = ImageInfo(
            uri = Uri.fromFile(file),
            filename = file.name,
            sizeBytes = file.length(),
            width = 2000,
            height = 1500,
            mimeType = "image/jpeg"
        )

        val config = CompressionConfig(
            mode = CompressionMode.TargetSize(targetBytes),
            outputFormat = OutputFormat.JPEG
        )

        val result = engine.compress(imageInfo, config)

        assertTrue(result.isSuccess)
        assertTrue(result.compressedSizeBytes <= targetBytes)
    }

    @Test
    fun testQualityMode() = runBlocking {
        val file = createTestBitmapFile("quality_test.jpg", 800, 600)
        val imageInfo = ImageInfo(
            uri = Uri.fromFile(file),
            filename = file.name,
            sizeBytes = file.length(),
            width = 800,
            height = 600,
            mimeType = "image/jpeg"
        )

        val config = CompressionConfig(
            mode = CompressionMode.Quality(50),
            outputFormat = OutputFormat.JPEG
        )

        val result = engine.compress(imageInfo, config)
        assertTrue(result.isSuccess)
        assertTrue(result.compressedSizeBytes > 0)
    }

    @Test
    fun testResizeMode() = runBlocking {
        val file = createTestBitmapFile("resize_test.jpg", 1000, 800)
        val imageInfo = ImageInfo(
            uri = Uri.fromFile(file),
            filename = file.name,
            sizeBytes = file.length(),
            width = 1000,
            height = 800,
            mimeType = "image/jpeg"
        )

        val config = CompressionConfig(
            mode = CompressionMode.Resize(scalePercent = 50, preserveAspectRatio = true),
            outputFormat = OutputFormat.JPEG
        )

        val result = engine.compress(imageInfo, config)
        assertTrue(result.isSuccess)
        assertEquals(500, result.outputWidth)
        assertEquals(400, result.outputHeight)
    }

    @Test
    fun testPngOutput() = runBlocking {
        val file = createTestBitmapFile("test_png.png", 400, 300, Bitmap.CompressFormat.PNG)
        val imageInfo = ImageInfo(
            uri = Uri.fromFile(file),
            filename = file.name,
            sizeBytes = file.length(),
            width = 400,
            height = 300,
            mimeType = "image/png"
        )

        val config = CompressionConfig(
            mode = CompressionMode.Quality(80),
            outputFormat = OutputFormat.PNG
        )

        val result = engine.compress(imageInfo, config)
        assertTrue(result.isSuccess)
        assertEquals(OutputFormat.PNG, result.outputFormat)
    }

    @Test
    fun testCorruptedImageGracefulHandling() = runBlocking {
        val nonExistentFile = File(testDir, "missing_or_corrupted.jpg")
        if (nonExistentFile.exists()) nonExistentFile.delete()

        val imageInfo = ImageInfo(
            uri = Uri.fromFile(nonExistentFile),
            filename = nonExistentFile.name,
            sizeBytes = 0L,
            width = 0,
            height = 0,
            mimeType = "image/jpeg"
        )

        val config = CompressionConfig()
        val result = engine.compress(imageInfo, config)

        assertFalse("Corrupted/missing image should not report success", result.isSuccess)
        assertNotNull("Should provide clear error message", result.errorMessage)
    }

    @Test
    fun testReductionPercentageCalculation() = runBlocking {
        val file = createTestBitmapFile("reduction_test.jpg", 1000, 1000)
        val imageInfo = ImageInfo(
            uri = Uri.fromFile(file),
            filename = file.name,
            sizeBytes = 200 * 1024L,
            width = 1000,
            height = 1000,
            mimeType = "image/jpeg"
        )

        val config = CompressionConfig(
            mode = CompressionMode.TargetSize(100 * 1024L),
            outputFormat = OutputFormat.JPEG
        )

        val result = engine.compress(imageInfo, config)
        assertTrue(result.isSuccess)
        assertTrue("Reduction percentage should be >= 0", result.reductionPercentage >= 0f)
    }
}
