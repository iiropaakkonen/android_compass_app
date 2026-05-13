package com.example.compass_app

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var invertDragDirection: Boolean
        get() = prefs.getBoolean("invert_drag_direction", true)
        set(value) { prefs.edit().putBoolean("invert_drag_direction", value).apply() }
}
