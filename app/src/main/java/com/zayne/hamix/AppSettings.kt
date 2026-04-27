package com.zayne.hamix

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AppSettings {
    private const val PREFS_NAME = "hamix_settings"
    private const val KEY_FLOATING_BOTTOM_BAR = "floating_bottom_bar_enabled"
    private const val KEY_GLASS_EFFECT = "glass_effect_enabled"
    private const val KEY_HAMI_ITEMS = "hami_items"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFloatingBottomBarEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLOATING_BOTTOM_BAR, true)

    fun setFloatingBottomBarEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLOATING_BOTTOM_BAR, enabled).apply()
    }

    fun isGlassEffectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLASS_EFFECT, true)

    fun setGlassEffectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLASS_EFFECT, enabled).apply()
    }

    fun saveHamiItems(context: Context, items: List<HamiItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("code", item.code)
                put("category", item.category)
                put("date", item.date)
                put("summary", item.summary)
                put("logoName", item.logoName ?: JSONObject.NULL)
            }
            jsonArray.put(obj)
        }
        prefs(context).edit().putString(KEY_HAMI_ITEMS, jsonArray.toString()).apply()
    }

    fun getHamiItems(context: Context): List<HamiItem> {
        val jsonString = prefs(context).getString(KEY_HAMI_ITEMS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                HamiItem(
                    id = obj.optLong("id", System.currentTimeMillis() + i),
                    code = obj.getString("code"),
                    category = obj.getString("category"),
                    date = obj.getString("date"),
                    summary = obj.optString("summary", "摘要文本"),
                    logoName = obj.optString("logoName").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
