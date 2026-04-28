package com.zayne.hamix

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File

object IslandNotificationHelper {
    private const val CHANNEL_ID = "pickup_island_channel"
    private const val CHANNEL_NAME = "待取提醒"
    private const val PROCESSING_NOTIFICATION_ID = 10001
    private const val PIC_LOGO_KEY = "miui.focus.pic_logo"
    private const val PIC_TICKER_KEY = "miui.focus.pic_ticker"
    private const val PIC_AOD_KEY = "miui.focus.pic_aod"

    private fun getPickupTitle(category: String): String {
        return when (category) {
            "饮品" -> "饮品待取"
            "餐食" -> "餐食待取"
            "快递" -> "快递待取"
            else -> error("未知分类: $category")
        }
    }

    fun notifyPickup(context: Context, item: HamiItem) {
        if (!hasNotificationPermission(context)) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val logoIcon = loadLogoIcon(context, item.logoName)
        val contentIntent = buildContentPendingIntent(context, item)
        val pickupTitle = getPickupTitle(item.category)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(logoIcon)
            .setContentTitle(pickupTitle)
            .setContentText("${item.summary} ${item.code}")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        if (canUseIsland(context)) {
            val extras = buildIslandExtras(context, item, logoIcon)
            builder.addExtras(extras)
        }

        notificationManager.notify(item.id.toInt(), builder.build())
    }

    fun notifyProcessingStarted(context: Context, progress: Int = 0) {
        if (!hasNotificationPermission(context)) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val appIcon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        val contentIntent = PendingIntent.getActivity(
            context,
            PROCESSING_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(appIcon)
            .setContentTitle("已开始")
            .setContentText("正在截屏并识别")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(100, progress.coerceIn(0, 100), false)

        if (canUseIsland(context)) {
            val extras = buildProcessingIslandExtras(appIcon, progress)
            builder.addExtras(extras)
        }

        notificationManager.notify(PROCESSING_NOTIFICATION_ID, builder.build())
    }

    fun cancelProcessingNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(PROCESSING_NOTIFICATION_ID)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun getLogoDirectory(context: Context): File? {
        val directory = context.getExternalFilesDir("logos") ?: return null
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    private fun findLogoFile(context: Context, logoName: String?): File? {
        if (logoName.isNullOrBlank()) return null
        val directory = getLogoDirectory(context) ?: return null
        return directory.listFiles()
            ?.firstOrNull { file ->
                file.isFile && file.nameWithoutExtension == logoName
            }
    }

    private fun loadLogoBitmap(context: Context, logoName: String?): Bitmap? {
        val logoFile = findLogoFile(context, logoName) ?: return null
        return BitmapFactory.decodeFile(logoFile.absolutePath)
    }

    private fun loadLogoIcon(context: Context, logoName: String?): Icon {
        val bitmap = loadLogoBitmap(context, logoName)
        return bitmap?.let { Icon.createWithBitmap(it) }
            ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
    }

    private fun buildContentPendingIntent(context: Context, item: HamiItem): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("target_item_id", item.id)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDoneActionIntentUri(context: Context, item: HamiItem): String {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, item.id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, item.id.toInt())
            setClass(context, NotificationActionReceiver::class.java)
            setPackage(context.packageName)
        }
        return intent.toUri(Intent.URI_INTENT_SCHEME)
    }

    private fun buildIslandExtras(
        context: Context,
        item: HamiItem,
        logoIcon: Icon
    ): Bundle {
        val pics = Bundle().apply {
            putParcelable(PIC_LOGO_KEY, logoIcon)
            putParcelable(PIC_TICKER_KEY, logoIcon)
            putParcelable(PIC_AOD_KEY, logoIcon)
        }

        return Bundle().apply {
            putBundle("miui.focus.pics", pics)
            putString("miui.focus.param", buildIslandParams(context, item))
        }
    }

    private fun buildProcessingIslandExtras(appIcon: Icon, progress: Int): Bundle {
        val pics = Bundle().apply {
            putParcelable(PIC_LOGO_KEY, appIcon)
            putParcelable(PIC_TICKER_KEY, appIcon)
            putParcelable(PIC_AOD_KEY, appIcon)
        }

        return Bundle().apply {
            putBundle("miui.focus.pics", pics)
            putString("miui.focus.param", buildProcessingIslandParams(progress))
        }
    }

    private fun buildIslandParams(context: Context, item: HamiItem): String {
        val tickerText = "${item.summary} ${item.code}"
        val doneActionIntent = buildDoneActionIntentUri(context, item)
        val pickupTitle = getPickupTitle(item.category)
        return JSONObject().apply {
            put("param_v2", JSONObject().apply {
                put("protocol", 1)
                put("business", "pickup")
                put("updatable", true)
                put("ticker", tickerText)
                put("islandFirstFloat", false)
                put("enableFloat", false)
                put("tickerPic", PIC_TICKER_KEY)
                put("aodTitle", item.code)
                put("aodPic", PIC_AOD_KEY)
                put("chatInfo", JSONObject().apply {
                    put("picProfile", PIC_LOGO_KEY)
                    put("appIconPkg", "")
                    put("title", pickupTitle)
                    put("content", "${item.summary} · ${item.category}")
                    put("colorTitle", "#006EFF")
                    put("type", 2)
                })
                put("picInfo", JSONObject().apply {
                    put("type", 1)
                    put("pic", "miui.focus.pics")
                })
                put("hintInfo", JSONObject().apply {
                    put("type", 1)
                    put("title", item.code)
                    put("actionInfo", JSONObject().apply {
                        put("actionTitle", "完成")
                        put("actionTitleColor", "#FFFFFF")
                        put("actionTitleColorDark", "#FFFFFF")
                        put("actionBgColor", "#3482FF")
                        put("actionBgColorDark", "#3482FF")
                        put("actionIntentType", 2)
                        put("actionIntent", doneActionIntent)
                        put("clickWithCollapse", true)
                    })
                })
                put("param_island", JSONObject().apply {
                    put("islandProperty", 1)
                    put("bigIslandArea", JSONObject().apply {
                        put("imageTextInfoLeft", JSONObject().apply {
                            put("type", 1)
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                put("pic", PIC_LOGO_KEY)
                            })
                        })
                        put("textInfo", JSONObject().apply {
                            put("title", item.code)
                        })
                    })
                    put("smallIslandArea", JSONObject().apply {
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", PIC_LOGO_KEY)
                        })
                    })
                })
            })
        }.toString()
    }

    private fun buildProcessingIslandParams(progress: Int): String {
        val safeProgress = progress.coerceIn(0, 100)
        return JSONObject().apply {
            put("param_v2", JSONObject().apply {
                put("protocol", 1)
                put("business", "pickup")
                put("updatable", true)
                put("ticker", "正在识别")
                put("islandFirstFloat", true)
                put("enableFloat", false)
                put("tickerPic", PIC_TICKER_KEY)
                put("aodTitle", "已开始")
                put("aodPic", PIC_AOD_KEY)
                put("baseInfo", JSONObject().apply {
                    put("title", "识别中")
                    put("colorTitle","#3482FF")
                    put("type", 1)
                })
                put("picInfo", JSONObject().apply {
                    put("type", 1)
                    put("pic", PIC_LOGO_KEY)
                })
                put("multiProgressInfo", JSONObject().apply {
                    put("title", "正在识别")
                    put("progress", safeProgress)
                    put("color", "#3482FF")
                    put("points", 2)
                })
                put("param_island", JSONObject().apply {
                    put("islandProperty", 1)
                    put("bigIslandArea", JSONObject().apply {
                        put("imageTextInfoLeft", JSONObject().apply {
                            put("type", 1)
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                put("pic", PIC_LOGO_KEY)
                            })
                        })
                        put("textInfo", JSONObject().apply {
                            put("title", "正在识别")
                        })
                    })
                    put("smallIslandArea", JSONObject().apply {
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", PIC_LOGO_KEY)
                        })
                    })
                })
            })
        }.toString()
    }

    private fun canUseIsland(context: Context): Boolean {
        return isSupportIsland("persist.sys.feature.island", false) &&
            getFocusProtocolVersion(context) >= 3 &&
            hasFocusPermission(context)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getFocusProtocolVersion(context: Context): Int {
        return Settings.System.getInt(
            context.contentResolver,
            "notification_focus_protocol",
            0
        )
    }

    private fun hasFocusPermission(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val extras = Bundle().apply {
                putString("package", context.packageName)
            }
            val bundle = context.contentResolver.call(uri, "canShowFocus", null, extras)
            bundle?.getBoolean("canShowFocus", false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun isSupportIsland(key: String, defaultValue: Boolean): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            val value = method.invoke(null, key, false)
            if (value is Boolean) value else defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }
}
