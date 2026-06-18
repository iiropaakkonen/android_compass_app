package com.example.compass_app

import android.content.Context
import android.graphics.Color

// The Theme that defines the Themes
// Light mode not ready, we are currently only using the dark mode.

object ThemePrefs {

    private const val PREFS_NAME = "widget_prefs"
    private const val KEY_BACKGROUND           = "background_color"
    private const val KEY_SECONDARY_BACKGROUND = "secondary_background_color"
    private const val KEY_TICK                 = "tick_color"
    private const val KEY_ACCENT               = "accent_color"

    // Dark-mode defaults that mirror the app's Hextech palette (Obsidian bg, HextechGold accent)
    private const val DARK_BG_DEFAULT           = 0xFF0A0E1A.toInt()
    private const val DARK_SECONDARY_BG_DEFAULT = 0xFF1A2340.toInt()
    private const val DARK_ACCENT_DEFAULT       = 0xFFC89B3C.toInt()

    // Light-mode defaults (Parchment bg, HextechBlue accent)
    private const val LIGHT_BG_DEFAULT           = 0xFFEDDC91.toInt()
    private const val LIGHT_SECONDARY_BG_DEFAULT = 0xFFD4C47A.toInt()
    private const val LIGHT_ACCENT_DEFAULT       = 0xFF548CB4.toInt()

    fun save(context: Context, backgroundColor: Int, secondaryBackgroundColor: Int, tickColor: Int, accentColor: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_BACKGROUND, backgroundColor)
            .putInt(KEY_SECONDARY_BACKGROUND, secondaryBackgroundColor)
            .putInt(KEY_TICK, tickColor)
            .putInt(KEY_ACCENT, accentColor)
            .apply()
    }

    fun getBackground(context: Context, isDark: Boolean): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_BACKGROUND, if (isDark) DARK_BG_DEFAULT else LIGHT_BG_DEFAULT)

    fun getSecondaryBackground(context: Context, isDark: Boolean): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SECONDARY_BACKGROUND, if (isDark) DARK_SECONDARY_BG_DEFAULT else LIGHT_SECONDARY_BG_DEFAULT)

    fun getTick(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_TICK, Color.WHITE)

    fun getAccent(context: Context, isDark: Boolean): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, if (isDark) DARK_ACCENT_DEFAULT else LIGHT_ACCENT_DEFAULT)
}
