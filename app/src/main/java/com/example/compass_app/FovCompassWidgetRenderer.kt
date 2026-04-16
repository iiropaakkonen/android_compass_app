package com.example.compass_app

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import kotlin.math.abs
import kotlin.math.floor

object FovCompassWidgetRenderer {

    private const val PREFS_NAME = "widget_prefs"
    private const val KEY_PRIMARY_COLOR = "primary_color"
    private const val FOV_DEGREES = 120f

    private val directions = listOf(
        0f to "N", 45f to "NE", 90f to "E", 135f to "SE",
        180f to "S", 225f to "SW", 270f to "W", 315f to "NW"
    )

    fun render(context: Context, widthPx: Int, heightPx: Int, heading: Float): Bitmap {
        val isDarkMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isDarkMode) 0xFF2196F3.toInt() else 0xFF6650A4.toInt()
        val primaryColor = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PRIMARY_COLOR, fallback)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
            heightPx * 0.2f, heightPx * 0.2f,
            bgPaint
        )

        val cx = widthPx / 2f
        val pixelsPerDegree = widthPx / FOV_DEGREES

        // Layout zones
        val indicatorHeight = heightPx * 0.18f
        val tickLineY = heightPx * 0.70f
        val labelCenterY = (indicatorHeight + tickLineY - heightPx * 0.08f) / 2f
        val labelRegionHeight = (tickLineY - heightPx * 0.08f) - indicatorHeight
        val cardinalTextSize = labelRegionHeight * 0.65f
        val intercardinalTextSize = labelRegionHeight * 0.43f

        // Tick marks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val startTick = floor((heading - FOV_DEGREES / 2) / 10f).toInt() * 10
        val endTick = floor((heading + FOV_DEGREES / 2) / 10f).toInt() * 10 + 10
        var tickDeg = startTick
        while (tickDeg <= endTick) {
            val diff = tickDeg.toFloat() - heading
            if (abs(diff) <= FOV_DEGREES / 2) {
                val x = cx + diff * pixelsPerDegree
                val normalizedAngle = ((tickDeg % 360) + 360) % 360
                val halfH = when {
                    normalizedAngle % 90 == 0 -> heightPx * 0.20f
                    normalizedAngle % 45 == 0 -> heightPx * 0.13f
                    else -> heightPx * 0.07f
                }
                canvas.drawLine(x, tickLineY - halfH, x, tickLineY + halfH, tickPaint)
            }
            tickDeg += 10
        }

        // Horizontal tick baseline
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 255, 255, 255)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, tickLineY, widthPx.toFloat(), tickLineY, linePaint)

        // Direction labels
        for ((dirAngle, label) in directions) {
            var diff = ((dirAngle - heading) % 360f + 360f) % 360f
            if (diff > 180f) diff -= 360f
            if (abs(diff) > FOV_DEGREES / 2) continue

            val x = cx + diff * pixelsPerDegree
            val isCardinal = label.length == 1
            val textSize = if (isCardinal) cardinalTextSize else intercardinalTextSize

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (label == "N") Color.RED else Color.WHITE
                this.textSize = textSize
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(label, x, labelCenterY + textSize * 0.35f, textPaint)
        }

        // Center indicator: downward-pointing triangle pinned at top center
        val indPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val iw = heightPx * 0.07f
        val indicatorPath = Path().apply {
            moveTo(cx - iw, 0f)
            lineTo(cx + iw, 0f)
            lineTo(cx, indicatorHeight)
            close()
        }
        canvas.drawPath(indicatorPath, indPaint)

        return bitmap
    }
}