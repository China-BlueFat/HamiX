package com.zayne.hamix

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "hamix_settings"
    private const val KEY_FLOATING_BOTTOM_BAR = "floating_bottom_bar_enabled"
    private const val KEY_GLASS_EFFECT = "glass_effect_enabled"

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
}
