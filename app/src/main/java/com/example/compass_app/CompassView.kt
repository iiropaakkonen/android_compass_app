package com.example.compass_app

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas

@Composable
fun CompassView(heading: Float, modifier: Modifier = Modifier) {
    val colorUsed = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val arcR = size.width
        val arcCenterY = size.height + size.height * 0.3f

        val topLeft = Offset(cx - arcR, arcCenterY - arcR)
        val arcSize = androidx.compose.ui.geometry.Size(arcR * 2, arcR * 2)

        val clipPath = Path().apply {
            addArc(
                oval = androidx.compose.ui.geometry.Rect(
                    left = topLeft.x,
                    top = topLeft.y,
                    right = topLeft.x + arcSize.width,
                    bottom = topLeft.y + arcSize.height
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f
            )
            lineTo(cx, arcCenterY)
            close()
        }

        clipPath(clipPath) {
            drawArc(
                color = colorUsed,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Fill
            )

            // Tick marks — inside clipPath so they are bounded by the semicircle
            val tickPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                isAntiAlias = true
            }

            for (deg in 0..360) {
                val canvasAngle = Math.toRadians((180 - deg).toDouble())

                val isMajor = deg % 90 == 0
                val isMedium = deg % 45 == 0
                val isMinor = deg % 10 == 0

                val tickLength = when {
                    isMajor  -> arcR * 0.08f
                    isMedium -> arcR * 0.06f
                    isMinor  -> arcR * 0.04f
                    else     -> arcR * 0.02f
                }

                tickPaint.strokeWidth = when {
                    isMajor  -> 4f
                    isMedium -> 3f
                    isMinor  -> 2f
                    else     -> 1f
                }

                val cos = Math.cos(canvasAngle).toFloat()
                val sin = Math.sin(canvasAngle).toFloat()

                val outerX = cx + (arcR - 2f) * cos
                val outerY = arcCenterY + (arcR - 2f) * sin
                val innerX = cx + (arcR - tickLength) * cos
                val innerY = arcCenterY + (arcR - tickLength) * sin

                drawContext.canvas.nativeCanvas.drawLine(outerX, outerY, innerX, innerY, tickPaint)
            }

            // Cardinal direction labels
            val directions = mapOf(
                "N" to 0f,
                "E" to 90f,
                "S" to 180f,
                "W" to 270f
            )

            val labelRadius = arcR * 0.85f
            val paint = android.graphics.Paint().apply {
                textSize = arcR * 0.12f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            directions.forEach { (label, angle) ->
                val canvasAngle = Math.toRadians((angle - heading - 90f).toDouble())

                val lx = cx + labelRadius * Math.cos(canvasAngle).toFloat()
                val ly = arcCenterY + labelRadius * Math.sin(canvasAngle).toFloat()

                paint.color = if (label == "N") android.graphics.Color.RED
                else android.graphics.Color.WHITE

                drawContext.canvas.nativeCanvas.drawText(label, lx, ly, paint)
            }
        }
    }
}