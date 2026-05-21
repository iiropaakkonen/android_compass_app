package com.example.compass_app

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.*

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CompassView(
    heading: Float,
    modifier: Modifier = Modifier,
    pois: List<PointOfInterest> = emptyList(),
    userLocation: Location? = null,
    maxDistanceM: Float = 1000f,
    deduplicateOverlaps: Boolean = false,
    onPoiClick: (PointOfInterest) -> Unit = {}
) {
    val colorUsed = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val pxPerDp = density.density

        val cx = widthPx / 2f

        // Compass size: Encompass ~75% of the screen width
        val arcR = widthPx * 0.375f
        val arcCenterY = heightPx

        val poiIconSizePx = (20f * pxPerDp).toInt()

        val categoryIcons = remember(context, poiIconSizePx) {
            PoiCategory.entries.associateWith { category ->
                ContextCompat.getDrawable(context, poiCategoryIcon(category))
                    ?.toBitmap(poiIconSizePx, poiIconSizePx)
            }
        }
        
        // POIs originate from the outer edge of the compass
        val minVisualRadius = arcR + (2f * pxPerDp)
        
        // Make them extend very far - beyond the screen height if necessary
        // Using a multiplier to push them way out
        val maxVisualRadius = heightPx * 3.0f 

        // ── Step 1: heading-independent selection ────────────────────────────────
        // Project every POI onto the compass using a fixed north-up reference
        // (heading = 0) so the accepted set never changes as the user rotates.
        // Only the POIs list, distance limit, and screen geometry are keys here.
        val acceptedPoiIds: Set<Long>? = remember(
            pois, userLocation, maxDistanceM, deduplicateOverlaps,
            poiIconSizePx, cx, arcCenterY, minVisualRadius, maxVisualRadius
        ) {
            if (!deduplicateOverlaps || userLocation == null) return@remember null

            // Compute fixed-reference positions (heading = 0 / north-up)
            val candidates = pois.mapNotNull { poi ->
                val distKm = distanceTo(userLocation, poi.location)
                val distM = distKm * 1000f
                if (distM > maxDistanceM) return@mapNotNull null
                val bearing = bearingToFloat(userLocation, poi.location)
                val angle = (bearing - 90.0).withRadians() // fixed heading = 0
                val normalizedDist = (distM / maxDistanceM).coerceIn(0f, 1f)
                val r = minVisualRadius + (maxVisualRadius - minVisualRadius) * normalizedDist
                val x = cx + r * cos(angle).toFloat()
                val y = arcCenterY + r * sin(angle).toFloat()
                Triple(Offset(x, y), poi, distM)
            } // pois is already sorted nearest-first from the ViewModel

            val exclusionRadius = poiIconSizePx * 1.8f
            val accepted = mutableSetOf<Long>()
            val placed = mutableListOf<Offset>()
            val countByCategory = mutableMapOf<PoiCategory, Int>()

            for ((pos, poi) in candidates) {
                if ((countByCategory[poi.category] ?: 0) >= 10) continue
                val blocked = placed.any { aPos ->
                    val dx = aPos.x - pos.x
                    val dy = aPos.y - pos.y
                    sqrt(dx * dx + dy * dy) < exclusionRadius
                }
                if (!blocked) {
                    accepted.add(poi.id)
                    placed.add(pos)
                    countByCategory[poi.category] = (countByCategory[poi.category] ?: 0) + 1
                }
            }
            accepted
        }

        // ── Step 2: heading-dependent rendering ──────────────────────────────────
        // Map accepted POIs to their current canvas positions using the live heading.
        val visiblePois = remember(heading, pois, userLocation, maxDistanceM, widthPx, heightPx, arcR, acceptedPoiIds) {
            if (userLocation == null) return@remember emptyList()
            pois.mapNotNull { poi ->
                if (acceptedPoiIds != null && poi.id !in acceptedPoiIds) return@mapNotNull null
                val distKm = distanceTo(userLocation, poi.location)
                val distM = distKm * 1000f
                if (distM > maxDistanceM) return@mapNotNull null
                val bearing = bearingToFloat(userLocation, poi.location)
                val angle = (bearing - heading - 90.0).withRadians()
                val normalizedDist = (distM / maxDistanceM).coerceIn(0f, 1f)
                val poiRadius = minVisualRadius + (maxVisualRadius - minVisualRadius) * normalizedDist
                val x = cx + poiRadius * cos(angle).toFloat()
                val y = arcCenterY + poiRadius * sin(angle).toFloat()
                if (y >= arcCenterY) null
                else Triple(Offset(x, y), poi, distM)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visiblePois) {
                    detectTapGestures { tap ->
                        visiblePois
                            .minByOrNull { (pos, _, _) ->
                                val dx = pos.x - tap.x
                                val dy = pos.y - tap.y
                                dx * dx + dy * dy
                            }
                            ?.let { (pos, poi, _) ->
                                val dx = pos.x - tap.x
                                val dy = pos.y - tap.y
                                if (dx * dx + dy * dy < 40f * 40f) onPoiClick(poi)
                            }
                    }
                }
        ) {
            val topLeft = Offset(cx - arcR, arcCenterY - arcR)
            val arcSize = androidx.compose.ui.geometry.Size(arcR * 2f, arcR * 2f)


            val compassPath = Path().apply {
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

            clipPath(compassPath) {
                /*drawArc(
                    color = colorUsed,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Fill
                )*/

                val tickPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    isAntiAlias = true
                }

                for (deg in 0..360 step 2) {
                    val canvasAngle = (180 - deg - heading).toDouble().withRadians()
                    val isMajor = deg % 90 == 0
                    val isMedium = deg % 45 == 0
                    val isMinor = deg % 10 == 0

                    val tickLength = when {
                        isMajor  -> arcR * 0.12f
                        isMedium -> arcR * 0.08f
                        isMinor  -> arcR * 0.05f
                        else     -> arcR * 0.02f
                    }
                    tickPaint.strokeWidth = when {
                        isMajor  -> 4f
                        isMedium -> 3f
                        isMinor  -> 2f
                        else     -> 1f
                    }

                    val cosA = cos(canvasAngle).toFloat()
                    val sinA = sin(canvasAngle).toFloat()
                    val outerX = cx + (arcR - 2f) * cosA
                    val outerY = arcCenterY + (arcR - 2f) * sinA
                    val innerX = cx + (arcR - tickLength) * cosA
                    val innerY = arcCenterY + (arcR - tickLength) * sinA
                    drawContext.canvas.nativeCanvas.drawLine(outerX, outerY, innerX, innerY, tickPaint)
                }

                val cardinals = mapOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                val intercardinals = mapOf("NE" to 45f, "SE" to 135f, "SW" to 225f, "NW" to 315f)

                val labelRadius = arcR * 0.75f
                val labelPaint = android.graphics.Paint().apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                (cardinals + intercardinals).forEach { (label, angle) ->
                    val canvasAngle = (angle - heading - 90f).toDouble().withRadians()
                    val lx = cx + labelRadius * cos(canvasAngle).toFloat()
                    val ly = arcCenterY + labelRadius * sin(canvasAngle).toFloat()
                    labelPaint.textSize = when (label.length) {
                        1 -> arcR * 0.15f   // N, E, S, W
                        else -> arcR * 0.09f // NE, SE, SW, NW
                    }
                    labelPaint.color = if (label == "N") android.graphics.Color.RED else android.graphics.Color.WHITE
                    drawContext.canvas.nativeCanvas.drawText(label, lx, ly, labelPaint)
                }
            }

            val distancePaint = android.graphics.Paint().apply {
                textSize = 10f * pxPerDp
                textAlign = android.graphics.Paint.Align.CENTER
                color = android.graphics.Color.WHITE
                isAntiAlias = true
                // Add a small shadow to make it more readable against various backgrounds
                setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
            }

            // POI icons — Allowed to go off-screen
            val iconPaint = android.graphics.Paint().apply { isAntiAlias = true }
            val halfIcon = poiIconSizePx / 2f
            visiblePois.forEach { (pos, poi, distM) ->
                val bitmap = categoryIcons[poi.category]
                if (bitmap != null) {
                    iconPaint.colorFilter = PorterDuffColorFilter(
                        poiCategoryColor(poi.category).toArgb(),
                        PorterDuff.Mode.SRC_IN
                    )
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        pos.x - halfIcon,
                        pos.y - halfIcon,
                        iconPaint
                    )
                }


                val distStr = if (distM >= 1000f) {
                    "%.1fkm".format(distM / 1000f)
                } else {
                    "${distM.toInt()}m"
                }
                drawContext.canvas.nativeCanvas.drawText(
                    distStr,
                    pos.x,
                    pos.y + halfIcon + 12f * pxPerDp,
                    distancePaint
                )
            }
        }
    }
}

private fun Double.withRadians(): Double = Math.toRadians(this)

/** Returns the ARGB int for a category — usable from non-Compose (widget renderer) code. */
fun poiCategoryColorInt(category: PoiCategory): Int = poiCategoryColor(category).toArgb()

fun poiCategoryColor(category: PoiCategory): Color = when (category) {
    PoiCategory.FOOD_AND_DRINK          -> Color(0xFFFF9800)
    PoiCategory.ACCOMMODATION           -> Color(0xFF2196F3)
    PoiCategory.SIGHTSEEING_AND_CULTURE -> Color(0xFF9C27B0)
    PoiCategory.LEISURE_AND_ACTIVITIES  -> Color(0xFF4CAF50)
    PoiCategory.HEALTH                  -> Color(0xFFF44336)
    PoiCategory.MONEY                   -> Color(0xFFFFEB3B)
    PoiCategory.TRANSPORT               -> Color(0xFF9E9E9E)
    PoiCategory.GROCERY_AND_FOOD_SHOPS  -> Color(0xFF8BC34A)
    PoiCategory.RETAIL_SHOPPING         -> Color(0xFFE91E63)
    PoiCategory.SERVICES                -> Color(0xFF795548)
    PoiCategory.OTHER                   -> Color(0xFFCFD8DC)
}

@DrawableRes
fun poiCategoryIcon(category: PoiCategory): Int = when (category) {
    PoiCategory.FOOD_AND_DRINK          -> R.drawable.food
    PoiCategory.ACCOMMODATION           -> R.drawable.accommodation
    PoiCategory.SIGHTSEEING_AND_CULTURE -> R.drawable.sightseeing
    PoiCategory.LEISURE_AND_ACTIVITIES  -> R.drawable.leisure
    PoiCategory.HEALTH                  -> R.drawable.health
    PoiCategory.MONEY                   -> R.drawable.money
    PoiCategory.TRANSPORT               -> R.drawable.transport
    PoiCategory.GROCERY_AND_FOOD_SHOPS  -> R.drawable.grocery
    PoiCategory.RETAIL_SHOPPING         -> R.drawable.retail
    PoiCategory.SERVICES                -> R.drawable.services
    PoiCategory.OTHER                   -> R.drawable.services
}
