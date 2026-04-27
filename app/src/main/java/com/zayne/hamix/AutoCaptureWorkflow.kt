package com.zayne.hamix

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AutoCaptureSaveResult(
    val recognitionResult: RecognitionResult,
    val savedItem: HamiItem?
)

object AutoCaptureWorkflow {
    const val ACTION_CAPTURE_RESULT = "com.zayne.hamix.action.CAPTURE_RESULT"
    const val EXTRA_CODE = "extra_code"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_SUMMARY = "extra_summary"
    const val EXTRA_LOGO_NAME = "extra_logo_name"
    const val EXTRA_FULL_TEXT = "extra_full_text"
    const val EXTRA_SAVED = "extra_saved"

    suspend fun recognizeBitmap(context: Context, bitmap: Bitmap): RecognitionResult {
        val helper = TextRecognitionHelper(context)
        return helper.recognizeAll(bitmap)
    }

    fun persistRecognitionResult(
        context: Context,
        result: RecognitionResult
    ): HamiItem? {
        val code = result.code?.trim().orEmpty()
        if (code.isBlank()) return null

        val sdf = SimpleDateFormat("M月d日", Locale.getDefault())
        val currentDate = sdf.format(Calendar.getInstance().time)
        val newItem = HamiItem(
            code = code,
            category = result.type.ifBlank { "其他" },
            date = currentDate,
            summary = result.brand ?: "",
            logoName = result.logoName
        )
        val currentItems = AppSettings.getHamiItems(context)
        AppSettings.saveHamiItems(context, currentItems + newItem)
        return newItem
    }

    fun broadcastRecognitionResult(
        context: Context,
        result: RecognitionResult,
        saved: Boolean
    ) {
        context.sendBroadcast(
            Intent(NotificationActionReceiver.ACTION_ITEMS_CHANGED).apply {
                setPackage(context.packageName)
            }
        )

        context.sendBroadcast(
            Intent(ACTION_CAPTURE_RESULT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_CODE, result.code ?: "")
                putExtra(EXTRA_CATEGORY, result.type)
                putExtra(EXTRA_SUMMARY, result.brand ?: "")
                putExtra(EXTRA_LOGO_NAME, result.logoName)
                putExtra(EXTRA_FULL_TEXT, result.fullText)
                putExtra(EXTRA_SAVED, saved)
            }
        )
    }

    fun saveRecognitionResult(
        context: Context,
        result: RecognitionResult
    ): AutoCaptureSaveResult {
        val savedItem = persistRecognitionResult(context, result)
        broadcastRecognitionResult(context, result, savedItem != null)
        return AutoCaptureSaveResult(
            recognitionResult = result,
            savedItem = savedItem
        )
    }

    suspend fun processBitmapAndSave(context: Context, bitmap: Bitmap): AutoCaptureSaveResult {
        val result = recognizeBitmap(context, bitmap)
        return saveRecognitionResult(context, result)
    }
}
