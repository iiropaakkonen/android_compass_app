package com.example.compass_app

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a bitmap that visually matches the semi-circular CompassView.
 *
 * Draw order:
 *   1. Tick marks + cardinal labels inside the semicircle clip.
 *   2. clip released.
 *   3. POI icons in the clear space ABOVE the arc (outside the semicircle).
 *      Radius scales linearly with distance so close POIs sit just above the arc
 *      and distant ones float near the top of the bitmap — mirroring CompassView.
 *      Size and alpha both decrease with distance.
 *
 * Icons are loaded once at base resolution; RectF handles per-marker scaling.
 * The output bitmap is reused across frames to avoid per-frame GC pressure.
 * POI/location data is re-parsed from SharedPreferences at most once every 1.5 s.
 */
object CompassWidgetRenderer {

    private var iconCache: Map<PoiCategory, Bitmap>? = null
    private var cachedIconSize: Int = -1

    // Reusable output bitmap — reallocated only when size changes
    private var outputBitmap: Bitmap? = null
    private var outputWidth  = -1
    private var outputHeight = -1

    // POI data cache — re-read from SharedPreferences at most once per 1500 ms
    private var cachedPois:    List<WidgetPoi>? = null
    private var cachedUserLoc: Location?        = null
    private var poiCacheTimeMs = 0L
    private const val POI_CACHE_TTL_MS = 1500L

    private fun getIconBitmaps(context: Context, sizePx: Int): Map<PoiCategory, Bitmap> {
        if (iconCache != null && cachedIconSize == sizePx) return iconCache!!
        iconCache = PoiCategory.entries.associateWith { category ->
            ContextCompat.getDrawable(context, poiCategoryIcon(category))
                ?.toBitmap(sizePx, sizePx)
                ?: Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }
        cachedIconSize = sizePx
        return iconCache!!
    }

    private fun getOrRefreshPois(context: Context): Pair<Location?, List<WidgetPoi>> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (cachedPois == null || now - poiCacheTimeMs > POI_CACHE_TTL_MS) {
            cachedUserLoc  = WidgetDataStore.getUserLocation(context)
            cachedPois     = WidgetDataStore.getPois(context)
            poiCacheTimeMs = now
        }
        return cachedUserLoc to cachedPois!!
    }

    fun render(context: Context, widthPx: Int, heightPx: Int, heading: Float): Bitmap {
        val isDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bgColor   = ThemePrefs.getBackground(context, isDark)
        val tickColor = ThemePrefs.getTick(context)

        // Reuse or (re)allocate the output bitmap
        val result = if (outputBitmap != null && outputWidth == widthPx && outputHeight == heightPx) {
            outputBitmap!!
        } else {
            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
                outputBitmap  = it
                outputWidth   = widthPx
                outputHeight  = heightPx
            }
        }
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)

        val cx         = widthPx / 2f
        val arcCenterY = heightPx.toFloat()   // arc base at bitmap bottom, same as CompassView
        val arcR       = widthPx * 0.47f

        // ── Clip to upper semicircle ──────────────────────────────────────────
        val clipPath = Path().apply {
            arcTo(
                RectF(cx - arcR, arcCenterY - arcR, cx + arcR, arcCenterY + arcR),
                180f, 180f, false
            )
            lineTo(cx, arcCenterY)
            close()
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // ── Tick marks ────────────────────────────────────────────────────────
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = tickColor
            strokeCap = Paint.Cap.ROUND
        }
        for (deg in 0..360 step 2) {
            val isMajor  = deg % 90 == 0
            val isMedium = deg % 45 == 0
            val isMinor  = deg % 10 == 0
            tickPaint.strokeWidth = when {
                isMajor  -> 4f; isMedium -> 3f; isMinor -> 2f; else -> 1f
            }
            val tickLength = when {
                isMajor  -> arcR * 0.12f; isMedium -> arcR * 0.08f
                isMinor  -> arcR * 0.05f; else     -> arcR * 0.02f
            }
            val rad  = Math.toRadians((180 - deg).toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            canvas.drawLine(
                cx + (arcR - 2f) * cosA, arcCenterY + (arcR - 2f) * sinA,
                cx + (arcR - tickLength) * cosA, arcCenterY + (arcR - tickLength) * sinA,
                tickPaint
            )
        }

        // ── Cardinal labels ───────────────────────────────────────────────────
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize  = arcR * 0.20f
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.DEFAULT_BOLD
        }
        val labelVOffset = labelPaint.textSize * 0.35f
        val labelRadius  = arcR * 0.75f
        mapOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, angle) ->
            val rad = Math.toRadians((angle - heading - 90.0))
            val lx  = cx + labelRadius * cos(rad).toFloat()
            val ly  = arcCenterY + labelRadius * sin(rad).toFloat()
            labelPaint.color = if (label == "N") Color.RED else tickColor
            canvas.drawText(label, lx, ly + labelVOffset, labelPaint)
        }

        canvas.restore()   // ← clip released; icons drawn in clear space above the arc

        // ── POI icons above the arc ───────────────────────────────────────────
        val (userLoc, widgetPois) = getOrRefreshPois(context)
        if (userLoc == null || widgetPois.isEmpty()) return result

        val maxDistM      = 1000f
        val baseIconPx    = (arcR * 0.22f).toInt().coerceIn(12, 24)
        val baseIconHalf  = baseIconPx / 2f
        val icons         = getIconBitmaps(context, baseIconPx)

        // Radius range: icons start just past the arc rim, end near the bitmap top
        val minVisualRadius = arcR + baseIconHalf + 4f
        val maxVisualRadius = arcCenterY - baseIconHalf - 6f

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val distPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize  = (arcR * 0.11f).coerceAtLeast(8f)
            textAlign = Paint.Align.CENTER
            color     = tickColor
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }

        // Draw farthest first so closest markers render on top
        val sorted = widgetPois
            .mapNotNull { poi ->
                val d = distanceTo(userLoc, Location(poi.lat, poi.lon)) * 1000f
                if (d > maxDistM) null else poi to d
            }
            .sortedByDescending { it.second }

        for ((poi, distM) in sorted) {
            val normalizedDist = (distM / maxDistM).coerceIn(0f, 1f)

            // Radius scales with distance (close → just above arc, far → near top)
            val poiRadius = minVisualRadius + (maxVisualRadius - minVisualRadius) * normalizedDist

            val bearing = bearingToFloat(userLoc, Location(poi.lat, poi.lon))
            val rad = Math.toRadians((bearing - heading - 90.0))
            val lx  = cx + poiRadius * cos(rad).toFloat()
            val ly  = arcCenterY + poiRadius * sin(rad).toFloat()

            // Skip if behind user, off the top, or off the sides
            if (ly + baseIconHalf >= arcCenterY) continue
            if (ly - baseIconHalf < 0f)          continue
            if (lx - baseIconHalf < 0f || lx + baseIconHalf > widthPx) continue

            // Size and alpha decrease with distance
            val drawSize = (baseIconPx * (1f - normalizedDist * 0.50f)).coerceAtLeast(5f)
            val drawHalf = drawSize / 2f
            val alpha    = (255 * (1f - normalizedDist * 0.60f)).toInt().coerceIn(80, 255)

            val icon = icons[poi.toCategory()] ?: continue
            iconPaint.alpha       = alpha
            iconPaint.colorFilter = PorterDuffColorFilter(
                poiCategoryColorInt(poi.toCategory()), PorterDuff.Mode.SRC_IN
            )
            canvas.drawBitmap(
                icon, null,
                RectF(lx - drawHalf, ly - drawHalf, lx + drawHalf, ly + drawHalf),
                iconPaint
            )

            // Distance label above the icon (toward the top = clear space)
            val distStr = if (distM >= 1000f) "%.1fkm".format(distM / 1000f) else "${distM.toInt()}m"
            distPaint.alpha = alpha
            canvas.drawText(distStr, lx, ly - drawHalf - 2f, distPaint)
        }

        return result
    }
}
