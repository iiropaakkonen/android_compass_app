package com.example.compass_app

import android.R.attr.width
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas

@Composable
fun CompassView(heading: Float, modifier: Modifier = Modifier) {
    val colorUsed = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = minOf(cx, cy) - 10f

        drawCircle(
            color = colorUsed,
            radius = r,
            center = Offset(cx, cy),
            style = Fill
        )

        rotate(-heading, pivot = Offset(cx, cy)) {
            drawContext.canvas.nativeCanvas.drawText(
                "N",
                cx,
                cy - r * 0.7f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = r * 0.2f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
        val path = Path().apply {
            moveTo(cx, cy - r * 0.6f)
            lineTo(cx - r * 0.05f, cy)
            lineTo(cx + r * 0.05f, cy)
            close()
        }
        drawPath(path, color = Color.Red)

        val southPath = Path().apply {
            moveTo(cx, cy + r * 0.6f)
            lineTo(cx - r * 0.05f, cy)
            lineTo(cx + r * 0.05f, cy)
            close()
        }
        drawPath(southPath, color = Color.Gray)
        }
    }