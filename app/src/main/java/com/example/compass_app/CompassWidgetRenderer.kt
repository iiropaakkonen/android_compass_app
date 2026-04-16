package com.example.compass_app

import android.content.Context
import android.content.res.Configuration
import android.graphics.*

object CompassWidgetRenderer {

    private const val PREFS_NAME = "widget_prefs"
    private const val KEY_PRIMARY_COLOR = "primary_color"

    fun render(context: Context, widthPx: Int, heightPx: Int, heading: Float): Bitmap {
        val isDarkMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isDarkMode) 0xFF2196F3.toInt() else 0xFF6650A4.toInt()
        val primaryColor = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PRIMARY_COLOR, fallback)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val r = minOf(cx, cy)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r, circlePaint)

        canvas.save()
        canvas.rotate(-heading, cx, cy)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            textSize = r * 0.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("N", cx, cy - r * 0.7f, textPaint)

        val northPath = Path().apply {
            moveTo(cx, cy - r * 0.6f)
            lineTo(cx - r * 0.05f, cy)
            lineTo(cx + r * 0.05f, cy)
            close()
        }
        val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        canvas.drawPath(northPath, northPaint)

        val southPath = Path().apply {
            moveTo(cx, cy + r * 0.6f)
            lineTo(cx - r * 0.05f, cy)
            lineTo(cx + r * 0.05f, cy)
            close()
        }
        val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            style = Paint.Style.FILL
        }
        canvas.drawPath(southPath, southPaint)

        canvas.restore()

        return bitmap
    }
}
