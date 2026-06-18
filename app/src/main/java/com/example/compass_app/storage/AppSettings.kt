package com.example.compass_app

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var invertDragDirection: Boolean
        get() = prefs.getBoolean("invert_drag_direction", true)
        set(value) { prefs.edit().putBoolean("invert_drag_direction", value).apply() }

    var smartFilterEnabled: Boolean
        get() = prefs.getBoolean("smart_filter_enabled", false)
        set(value) { prefs.edit().putBoolean("smart_filter_enabled", value).apply() }

    var compassSmoothing: Boolean
        get() = prefs.getBoolean("compass_smoothing", true)
        set(value) { prefs.edit().putBoolean("compass_smoothing", value).apply() }

    var randomThemeHue: Float?
        get() = prefs.getFloat("random_theme_hue", -1f).let { if (it < 0f) null else it }
        set(value) {
            if (value == null) prefs.edit().remove("random_theme_hue").apply()
            else prefs.edit().putFloat("random_theme_hue", value).apply()
        }
}
