package com.zayne.hamix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
//        startForeground(NOTIFICATION_ID, createServiceNotification())
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

//    private fun createServiceNotification(): Notification {
//        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val channel = NotificationChannel(
//            CHANNEL_ID,
//            "快捷截屏监听",
//            NotificationManager.IMPORTANCE_LOW
//        ).apply {
//            setShowBadge(false)
//            enableVibration(false)
//            setSound(null, null)
//        }
//        manager.createNotificationChannel(channel)
//
//        return Notification.Builder(this, CHANNEL_ID)
//            .setSmallIcon(R.mipmap.ic_launcher)
//            .setContentTitle("快捷截屏监听")
//            .setContentText("双击音量下键自动截屏识别")
//            .setOngoing(true)
//            .setOnlyAlertOnce(true)
//            .build()
//    }

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
        val result = now - lastVolumeDownTimestamp
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
            val screenshotFile = File(cacheDir, "root_capture.png")
            try {
                startFakeProgressUpdates()

                val captureCommand = "/system/bin/screencap -p ${screenshotFile.absolutePath}"
                val process = ProcessBuilder("su", "-c", captureCommand)
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                if (exitCode != 0 || !screenshotFile.exists()) {
                    Log.e(TAG, "截屏失败: exitCode=$exitCode")
                    return@launch
                }

                val bitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)
                if (bitmap == null) {
                    Log.e(TAG, "截图解码失败")
                    return@launch
                }

                try {
                    val saveResult = AutoCaptureWorkflow.processBitmapAndSave(applicationContext, bitmap)
                    saveResult.savedItem?.let { savedItem ->
                        IslandNotificationHelper.notifyPickup(applicationContext, savedItem)
                    }
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动截屏识别失败: ${e.message}", e)
            } finally {
                progressJob?.cancel()
                progressJob = null
                IslandNotificationHelper.cancelProcessingNotification(applicationContext)
                screenshotFile.delete()
                processing.set(false)
            }
        }
    }

    private fun startFakeProgressUpdates() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            val progressSteps = listOf(8, 17, 29, 41, 54, 68, 79, 88, 93)
            var index = 0
            while (processing.get()) {
                IslandNotificationHelper.notifyProcessingStarted(
                    applicationContext,
                    progressSteps[index]
                )
                if (index < progressSteps.lastIndex) {
                    index++
                }
                delay(250)
            }
        }
    }

    companion object {
        private const val TAG = "RootCaptureService"
        private const val CHANNEL_ID = "root_capture_service"
        private const val NOTIFICATION_ID = 2001
        private const val DOUBLE_PRESS_INTERVAL_MS = 400L
    }
}
