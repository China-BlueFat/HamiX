package com.zayne.hamix

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class RootCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerJob: Job? = null
    private var progressJob: Job? = null
    private var geteventProcess: Process? = null
    private var lastVolumeDownTimestamp = 0L
    private val processing = AtomicBoolean(false)
    private val volumeDownPressPattern =
        Regex("""\b(KEY_VOLUMEDOWN|VOLUME_DOWN|VOLUMEDOWN)\b\s+DOWN\b""")

    override fun onCreate() {
        super.onCreate()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (listenerJob?.isActive != true) {
            startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        geteventProcess?.destroy()
        listenerJob?.cancel()
        progressJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        listenerJob?.cancel()
        listenerJob = serviceScope.launch {
            while (true) {
                try {
                    val process = ProcessBuilder("su", "-c", "/system/bin/getevent -ql")
                        .redirectErrorStream(true)
                        .start()
                    geteventProcess = process
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            handleGeteventLine(line)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动 root 按键监听失败: ${e.message}", e)
                }
                delay(500)
            }
        }
    }

    private fun handleGeteventLine(line: String) {
        val normalized = line.uppercase()
        if (!volumeDownPressPattern.containsMatchIn(normalized)) return
        val now = System.currentTimeMillis()
        if (now - lastVolumeDownTimestamp <= DOUBLE_PRESS_INTERVAL_MS) {
            lastVolumeDownTimestamp = 0L
            triggerCapture()
        } else {
            lastVolumeDownTimestamp = now
        }
    }

    private fun triggerCapture() {
        if (!processing.compareAndSet(false, true)) return

        serviceScope.launch {
            try {
                IslandNotificationHelper.notifyProcessingStarted(applicationContext, 0)
                startFakeProgressUpdates()

                val screenshotBytes = captureScreenshotPng()
                if (screenshotBytes == null) {
                    Log.e(TAG, "截图失败: empty bytes")
                    return@launch
                }

                val bitmap = decodeOptimizedScreenshot(screenshotBytes)
                if (bitmap == null) {
                    Log.e(TAG, "截图解码失败")
                    return@launch
                }

                try {
                    val recognitionResult = AutoCaptureWorkflow.recognizeBitmap(applicationContext, bitmap)
                    finishProcessingNotification()

                    val savedItem = AutoCaptureWorkflow.persistRecognitionResult(
                        applicationContext,
                        recognitionResult
                    )
                    savedItem?.let {
                        IslandNotificationHelper.notifyPickup(applicationContext, it)
                    }
                    AutoCaptureWorkflow.broadcastRecognitionResult(
                        applicationContext,
                        recognitionResult,
                        savedItem != null
                    )
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动截屏识别失败: ${e.message}", e)
            } finally {
                finishProcessingNotification()
            }
        }
    }

    private fun captureScreenshotPng(): ByteArray? {
        val process = ProcessBuilder("su", "-c", "/system/bin/screencap -p")
            .redirectErrorStream(true)
            .start()

        val bytes = process.inputStream.use { it.readBytes() }
        val exitCode = process.waitFor()
        return if (exitCode == 0 && bytes.isNotEmpty()) {
            bytes
        } else {
            null
        }
    }

    private fun decodeOptimizedScreenshot(bytes: ByteArray): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val maxSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        val sampleSize = if (maxSide <= MAX_OCR_SIDE) {
            1
        } else {
            Integer.highestOneBit((maxSide / MAX_OCR_SIDE).coerceAtLeast(1))
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
        val decodedMaxSide = max(decoded.width, decoded.height)
        if (decodedMaxSide <= MAX_OCR_SIDE) {
            return decoded
        }

        val scale = MAX_OCR_SIDE.toFloat() / decodedMaxSide
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) {
            decoded.recycle()
        }
        return scaled
    }

    private fun finishProcessingNotification() {
        progressJob?.cancel()
        progressJob = null
        processing.set(false)
        IslandNotificationHelper.cancelProcessingNotification(applicationContext)
    }

    private fun startFakeProgressUpdates() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            val progressSteps = listOf(1, 3, 6, 10, 15, 22, 31, 43, 57, 70, 82, 90, 95)
            var index = 0
            while (processing.get()) {
                IslandNotificationHelper.notifyProcessingStarted(
                    applicationContext,
                    progressSteps[index]
                )
                if (index < progressSteps.lastIndex) {
                    index++
                }
                delay(140)
            }
        }
    }

    companion object {
        private const val TAG = "RootCaptureService"
        private const val DOUBLE_PRESS_INTERVAL_MS = 400L
        private const val MAX_OCR_SIDE = 1600
    }
}
