package com.example.compass_app

import android.content.Context
import android.graphics.*
import kotlin.math.abs
import kotlin.math.floor

object FovCompassWidgetRenderer {

    private const val FOV_DEGREES = 120f

    private val directions = listOf(
        0f to "N", 45f to "NE", 90f to "E", 135f to "SE",
        180f to "S", 225f to "SW", 270f to "W", 315f to "NW"
    )

    fun render(context: Context, widthPx: Int, heightPx: Int, heading: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Layout: top ~28% for badge, bottom ~72% for compass strip
        val badgeAreaH = heightPx * 0.30f
        val stripTop = badgeAreaH
        val stripH = heightPx - stripTop
        val cx = widthPx / 2f
        val pixelsPerDegree = widthPx / FOV_DEGREES

        // --- Compass strip background ---
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(245, 14, 16, 24)
            style = Paint.Style.FILL
        }
        val cornerR = stripH * 0.18f
        canvas.drawRoundRect(
            0f, stripTop, widthPx.toFloat(), heightPx.toFloat(),
            cornerR, cornerR, bgPaint
        )

        // Strip border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 160, 170, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(
            0.75f, stripTop + 0.75f, widthPx - 0.75f, heightPx - 0.75f,
            cornerR, cornerR, borderPaint
        )

        // --- Tick marks ---
        val tickBaseY = stripTop + stripH * 0.68f
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 190, 195, 215)
            strokeWidth = 2f
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
                    normalizedAngle % 90 == 0 -> stripH * 0.22f
                    normalizedAngle % 45 == 0 -> stripH * 0.14f
                    else -> stripH * 0.08f
                }
                canvas.drawLine(x, tickBaseY - halfH, x, tickBaseY + halfH, tickPaint)
            }
            tickDeg += 10
        }

        // Horizontal baseline
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 190, 195, 215)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, tickBaseY, widthPx.toFloat(), tickBaseY, linePaint)

        // --- Direction labels ---
        val labelCenterY = stripTop + stripH * 0.36f
        val cardinalTextSize = stripH * 0.40f
        val intercardinalTextSize = stripH * 0.28f
        for ((dirAngle, label) in directions) {
            var diff = ((dirAngle - heading) % 360f + 360f) % 360f
            if (diff > 180f) diff -= 360f
            if (abs(diff) > FOV_DEGREES / 2) continue

            val x = cx + diff * pixelsPerDegree
            val isCardinal = label.length == 1
            val textSize = if (isCardinal) cardinalTextSize else intercardinalTextSize

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (label == "N") Color.argb(255, 230, 60, 60) else Color.WHITE
                this.textSize = textSize
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(label, x, labelCenterY + textSize * 0.38f, textPaint)
        }

        // --- Center marker: vertical line through the strip ---
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        canvas.drawLine(cx, stripTop + stripH * 0.08f, cx, stripTop + stripH * 0.92f, markerPaint)

        return bitmap
    }

    private fun drawOrnament(canvas: Canvas, edgeX: Float, stripTop: Float, stripH: Float, facing: Int) {
        // Arrow-diamond ornament on the strip edge
        val cy = stripTop + stripH / 2f
        val size = stripH * 0.42f
        val tipX = edgeX + facing * size * 1.8f
        val midX = edgeX + facing * size * 0.9f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 30, 35, 50)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 160, 170, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        // Outer diamond
        val outerPath = Path().apply {
            moveTo(edgeX, cy)
            lineTo(midX, cy - size * 0.55f)
            lineTo(tipX, cy)
            lineTo(midX, cy + size * 0.55f)
            close()
        }
        canvas.drawPath(outerPath, fillPaint)
        canvas.drawPath(outerPath, strokePaint)

        // Inner diamond
        val innerSize = size * 0.38f
        val innerPath = Path().apply {
            moveTo(midX - facing * innerSize * 0.5f, cy)
            lineTo(midX, cy - innerSize * 0.5f)
            lineTo(midX + facing * innerSize * 0.5f, cy)
            lineTo(midX, cy + innerSize * 0.5f)
            close()
        }
        canvas.drawPath(innerPath, strokePaint)

        // Small horizontal line from edge to diamond
        canvas.drawLine(edgeX, cy, edgeX + facing * size * 0.3f, cy, strokePaint)
    }

    private fun drawBadge(canvas: Canvas, cx: Float, stripTop: Float, badgeAreaH: Float, text: String) {
        val textSize = badgeAreaH * 0.52f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textWidth = textPaint.measureText(text)
        val padH = badgeAreaH * 0.18f
        val padV = badgeAreaH * 0.14f
        val badgeW = textWidth + padH * 2f
        val badgeH = textSize + padV * 2f
        val badgeCy = badgeAreaH * 0.5f
        val left = cx - badgeW / 2f
        val top = badgeCy - badgeH / 2f
        val right = cx + badgeW / 2f
        val bottom = badgeCy + badgeH / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 14, 16, 24)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 160, 170, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val r = badgeH * 0.25f
        canvas.drawRoundRect(left, top, right, bottom, r, r, bgPaint)
        canvas.drawRoundRect(left, top, right, bottom, r, r, borderPaint)

        // Small connector line from badge to strip
        val connPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 160, 170, 200)
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(cx, bottom, cx, stripTop, connPaint)

        canvas.drawText(text, cx, bottom - padV - textSize * 0.12f, textPaint)
    }

    private fun getCardinalLabel(deg: Int): String = when {
        deg < 23 || deg >= 338 -> "N"
        deg < 68 -> "NE"
        deg < 113 -> "E"
        deg < 158 -> "SE"
        deg < 203 -> "S"
        deg < 248 -> "SW"
        deg < 293 -> "W"
        else -> "NW"
    }
}
