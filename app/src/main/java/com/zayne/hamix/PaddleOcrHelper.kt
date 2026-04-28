package com.zayne.hamix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.equationl.ncnnandroidppocr.OCR
import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.DrawModel
import com.equationl.ncnnandroidppocr.bean.ImageSize
import com.equationl.ncnnandroidppocr.bean.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class PaddleOcrHelper private constructor(private val context: Context) {

    private var ocr: OCR? = null
    private val initialized = AtomicBoolean(false)
    private val initializing = AtomicBoolean(false)
    @Volatile private var isRecognizing = false

    val isInitialized: Boolean get() = initialized.get()

    data class TextBlock(
        val text: String,
        val boundingBox: Rect?,
        val confidence: Float
    )

    data class RecognizeResult(
        val fullText: String,
        val textBlocks: List<TextBlock>
    )

    suspend fun initAsync(
        modelType: ModelType = ModelType.Mobile,
        imageSize: ImageSize = ImageSize.Size720,
        device: Device = Device.CPU
    ): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@PaddleOcrHelper) {
            if (initialized.get()) {
                return@withContext true
            }

            if (!initializing.compareAndSet(false, true)) {

                while (!initialized.get()) {
                    Thread.sleep(50)
                }
                return@withContext true
            }
        }

        return@withContext try {
            val newOcr = OCR()
            val success = newOcr.initModelFromAssert(
                context.assets,
                modelType,
                imageSize,
                device
            )

            synchronized(this@PaddleOcrHelper) {
                ocr = newOcr
                initialized.set(success)
            }

            if (!success) {
                Log.e(TAG, "PaddleOCR (ncnn) 初始化失败")
                initializing.set(false)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR (ncnn) 初始化异常: ${e.message}", e)
            initializing.set(false)
            false
        }
    }

    fun init(
        modelType: ModelType = ModelType.Mobile,
        imageSize: ImageSize = ImageSize.Size720,
        device: Device = Device.CPU
    ): Boolean {
        synchronized(this@PaddleOcrHelper) {
            if (initialized.get()) return true
        }

        while (initializing.get() && !initialized.get()) {
            Thread.sleep(50)
        }

        if (initialized.get()) return true

        synchronized(this@PaddleOcrHelper) {
            if (initialized.get()) return true

            return try {
                val newOcr = OCR()

                val success = newOcr.initModelFromAssert(
                    context.assets,
                    modelType,
                    imageSize,
                    device
                )
                ocr = newOcr
                initialized.set(success)
                success
            } catch (e: Exception) {
                Log.e(TAG, "PaddleOCR (ncnn) 初始化异常: ${e.message}", e)
                false
            }
        }
    }

    suspend fun recognizeAsync(bitmap: Bitmap): RecognizeResult? = withContext(Dispatchers.Default) {
        recognize(bitmap)
    }

    fun recognize(bitmap: Bitmap): RecognizeResult? {
        val currentOcr: OCR?
        synchronized(this@PaddleOcrHelper) {
            currentOcr = ocr
        }

        if (currentOcr == null || !initialized.get()) {
            Log.e(TAG, "PaddleOCR 未初始化，无法识别")
            return null
        }

        val processedBitmap = if (bitmap.width > 960 || bitmap.height > 960) {
            val scale = 960f / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

            if (scaledBitmap.config != Bitmap.Config.ARGB_8888) {
                val argbBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
                scaledBitmap.recycle()
                argbBitmap
            } else {
                scaledBitmap
            }
        } else {

            if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
        }

        var waitCount = 0
        while (isRecognizing && waitCount < 300) {
            Thread.sleep(100)
            waitCount++
        }
        if (isRecognizing) {
            Log.e(TAG, "PaddleOCR 上一轮识别超时，强制重置")
            isRecognizing = false
        }
        isRecognizing = true

        return try {

            val result = currentOcr.detectBitmap(processedBitmap, DrawModel.None)

            if (result == null) {
                Log.e(TAG, "PaddleOCR (ncnn) 识别返回 null")
                return null
            }

            val parsedResult = parseResult(result)

            parsedResult
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR (ncnn) 识别异常: ${e.message}", e)
            null
        } finally {
            isRecognizing = false
        }
    }

    private fun parseResult(result: com.equationl.ncnnandroidppocr.bean.OcrResult): RecognizeResult {
        val textBlocks = mutableListOf<TextBlock>()
        val fullTextBuilder = StringBuilder()

        result.textLines.forEach { line ->
            val text = line.text ?: ""
            val confidence = line.confidence ?: -1f

            val boundingBox = if (line.points.isNotEmpty()) {
                pointsToRect(line.points)
            } else {
                null
            }

            if (text.isNotEmpty()) {
                textBlocks.add(TextBlock(text, boundingBox, confidence))
            }
        }

        val sortedTextBlocks = textBlocks.sortedByDescending { it.boundingBox?.bottom ?: 0 }

        sortedTextBlocks.forEachIndexed { index, textBlock ->
            if (index > 0) {
                fullTextBuilder.append("\n")
            }
            fullTextBuilder.append(textBlock.text)
        }

        return RecognizeResult(fullTextBuilder.toString(), sortedTextBlocks)
    }

    private fun pointsToRect(points: List<Point>): Rect? {
        if (points.isEmpty()) return null

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        points.forEach { point ->
            minX = minOf(minX, point.x)
            minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x)
            maxY = maxOf(maxY, point.y)
        }

        return if (minX != Int.MAX_VALUE) {
            Rect(minX, minY, maxX, maxY)
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "PaddleOcrHelper"

        @Volatile
        private var instance: PaddleOcrHelper? = null
        private val lock = Any()

        fun getInstance(context: Context): PaddleOcrHelper {
            return instance ?: synchronized(lock) {
                instance ?: PaddleOcrHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }

    }
}
