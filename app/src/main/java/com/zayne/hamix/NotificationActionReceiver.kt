package com.zayne.hamix

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_DONE) return

        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId == -1L) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, itemId.toInt())
        val items = AppSettings.getHamiItems(context).filterNot { it.id == itemId }
        AppSettings.saveHamiItems(context, items)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        context.sendBroadcast(
            Intent(ACTION_ITEMS_CHANGED).setPackage(context.packageName)
        )
    }

    companion object {
        const val ACTION_MARK_DONE = "com.zayne.hamix.action.MARK_DONE"
        const val ACTION_ITEMS_CHANGED = "com.zayne.hamix.action.ITEMS_CHANGED"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
