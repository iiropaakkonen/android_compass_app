package com.example.compass_app

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.floor

/**
 * FOV (strip) compass widget renderer.
 *
 * Layout:
 *   • Badge area  (top 30 %) — transparent, shows wallpaper through.
 *   • Strip       (bottom 70 %) — themed background, ticks, direction labels, POI icons.
 *
 * POI icons are drawn IN the strip, centred vertically, after ticks/labels so they
 * appear "in front" of the compass content.  The centre-line marker is drawn last
 * so it always stays on top.
 *
 * Custom-POI edge markers (Skyrim style): user-created pins whose bearing falls
 * outside the current ±60 ° FOV are clamped to the left or right edge of the strip
 * so they remain visible at all times.  Up to 2 are stacked per side.
 *
 * The output bitmap is reused across frames to avoid per-frame GC pressure.
 * POI/location data is re-parsed from SharedPreferences at most once every 1.5 s.
 */
object FovCompassWidgetRenderer {

    private const val FOV_DEGREES = 120f

    private val directions = listOf(
        0f to "N", 45f to "NE", 90f to "E", 135f to "SE",
        180f to "S", 225f to "SW", 270f to "W", 315f to "NW"
    )

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    // ── Icon bitmap cache ─────────────────────────────────────────────────────
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

        val bgColor          = ThemePrefs.getBackground(context, isDark)
        val secondaryBgColor = ThemePrefs.getSecondaryBackground(context, isDark)
        val tickColor        = ThemePrefs.getTick(context)
        val accentColor      = ThemePrefs.getAccent(context, isDark)

        // Reuse or (re)allocate the output bitmap
        val bitmap = if (outputBitmap != null && outputWidth == widthPx && outputHeight == heightPx) {
            outputBitmap!!
        } else {
            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
                outputBitmap  = it
                outputWidth   = widthPx
                outputHeight  = heightPx
            }
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val badgeAreaH      = heightPx * 0.30f
        val stripTop        = badgeAreaH
        val stripH          = heightPx - stripTop
        val cx              = widthPx / 2f
        val pixelsPerDegree = widthPx / FOV_DEGREES

        // ── Strip background + border ─────────────────────────────────────────
        val cornerR = stripH * 0.18f
        canvas.drawRoundRect(
            0f, stripTop, widthPx.toFloat(), heightPx.toFloat(), cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, stripTop, 0f, heightPx.toFloat(),
                    intArrayOf(withAlpha(bgColor, 245), withAlpha(secondaryBgColor, 245)),
                    null,
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
        )

        // Dot pattern clipped to the strip
        val density    = context.resources.displayMetrics.density
        val dotRadius  = 2f * density
        val dotSpacing = 24f * density
        val dotPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
        canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(RectF(0f, stripTop, widthPx.toFloat(), heightPx.toFloat()), cornerR, cornerR, Path.Direction.CW)
        })
        var dx = 0f
        while (dx < widthPx) {
            var dy = stripTop
            while (dy < heightPx) {
                canvas.drawCircle(dx, dy, dotRadius, dotPaint)
                dy += dotSpacing
            }
            dx += dotSpacing
        }
        canvas.restore()

        canvas.drawRoundRect(
            0.75f, stripTop + 0.75f, widthPx - 0.75f, heightPx - 0.75f, cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accentColor, 140); style = Paint.Style.STROKE; strokeWidth = 1.5f
            }
        )

        // ── Tick marks ────────────────────────────────────────────────────────
        val tickBaseY = stripTop + stripH * 0.68f
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(tickColor, 170); strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        val startTick = floor((heading - FOV_DEGREES / 2) / 10f).toInt() * 10
        val endTick   = floor((heading + FOV_DEGREES / 2) / 10f).toInt() * 10 + 10
        var tickDeg   = startTick
        while (tickDeg <= endTick) {
            val diff = tickDeg.toFloat() - heading
            if (abs(diff) <= FOV_DEGREES / 2) {
                val x = cx + diff * pixelsPerDegree
                val normalizedAngle = ((tickDeg % 360) + 360) % 360
                val halfH = when {
                    normalizedAngle % 90 == 0 -> stripH * 0.22f
                    normalizedAngle % 45 == 0 -> stripH * 0.14f
                    else                      -> stripH * 0.08f
                }
                canvas.drawLine(x, tickBaseY - halfH, x, tickBaseY + halfH, tickPaint)
            }
            tickDeg += 10
        }

        // ── Baseline ─────────────────────────────────────────────────────────
        canvas.drawLine(
            0f, tickBaseY, widthPx.toFloat(), tickBaseY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(tickColor, 70); strokeWidth = 1f; style = Paint.Style.STROKE
            }
        )

        // ── Direction labels ─────────────────────────────────────────────────
        val labelCenterY          = stripTop + stripH * 0.36f
        val cardinalTextSize      = stripH * 0.40f
        val intercardinalTextSize = stripH * 0.28f
        for ((dirAngle, label) in directions) {
            var diff = ((dirAngle - heading) % 360f + 360f) % 360f
            if (diff > 180f) diff -= 360f
            if (abs(diff) > FOV_DEGREES / 2) continue
            val x        = cx + diff * pixelsPerDegree
            val textSize = if (label.length == 1) cardinalTextSize else intercardinalTextSize
            canvas.drawText(
                label, x, labelCenterY + textSize * 0.38f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = if (label == "N") Color.argb(255, 230, 60, 60) else tickColor
                    this.textSize = textSize; textAlign = Paint.Align.CENTER
                    typeface  = Typeface.DEFAULT_BOLD
                }
            )
        }

        // ── POI icons — drawn in the strip, in front of ticks/labels ─────────
        val (userLoc, allPois) = getOrRefreshPois(context)
        if (userLoc != null) {
            val maxDistM     = 1000f
            val iconCenterY  = stripTop + stripH * 0.50f
            val baseIconPx   = (stripH * 0.56f).toInt().coerceIn(28, 70)
            val baseIconHalf = baseIconPx / 2f
            val edgeIconPx   = (baseIconPx * 0.72f).toInt().coerceIn(18, 50)
            val edgeIconHalf = edgeIconPx / 2f

            val icons     = getIconBitmaps(context, baseIconPx)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            data class ComputedPoi(val poi: WidgetPoi, val diff: Float, val distM: Float)

            val inFov     = mutableListOf<ComputedPoi>()
            val leftEdge  = mutableListOf<ComputedPoi>()
            val rightEdge = mutableListOf<ComputedPoi>()

            for (poi in allPois) {
                val poiLoc  = Location(poi.lat, poi.lon)
                val bearing = bearingToFloat(userLoc, poiLoc)
                val distM   = distanceTo(userLoc, poiLoc) * 1000f
                var diff    = ((bearing - heading) % 360f + 360f) % 360f
                if (diff > 180f) diff -= 360f

                when {
                    abs(diff) <= FOV_DEGREES / 2 -> inFov.add(ComputedPoi(poi, diff, distM))
                    poi.isCustom && diff < 0     -> leftEdge.add(ComputedPoi(poi, diff, distM))
                    poi.isCustom && diff > 0     -> rightEdge.add(ComputedPoi(poi, diff, distM))
                }
            }

            inFov.sortByDescending { it.distM }
            leftEdge.sortBy  { abs(it.diff) }
            rightEdge.sortBy { abs(it.diff) }

            val distPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize  = (stripH * 0.14f).coerceAtLeast(7f)
                textAlign = Paint.Align.CENTER
                color     = tickColor
                setShadowLayer(2f, 0f, 0f, Color.BLACK)
            }

            data class InFovRender(
                val poi: WidgetPoi,
                val x: Float,
                val drawHalf: Float,
                val alpha: Int,
                val distM: Float,
                val distStr: String,
                val textY: Float
            )

            // Collect renderable in-FOV items (already farthest-first)
            val renderItems = inFov.mapNotNull { (poi, diff, distM) ->
                val normalizedDist = (distM / maxDistM).coerceIn(0f, 1f)
                val drawSize = (baseIconPx * (1f - normalizedDist * 0.50f)).coerceAtLeast(8f)
                val drawHalf = drawSize / 2f
                val alpha    = (255 * (1f - normalizedDist * 0.60f)).toInt().coerceIn(80, 255)
                val x = cx + diff * pixelsPerDegree
                if (x - drawHalf < 0f || x + drawHalf > widthPx) return@mapNotNull null
                val textY = iconCenterY + drawHalf + distPaint.textSize + 1f
                if (textY >= heightPx - 2f) return@mapNotNull null
                val distStr = if (distM >= 1000f) "%.1fkm".format(distM / 1000f) else "${distM.toInt()}m"
                InFovRender(poi, x, drawHalf, alpha, distM, distStr, textY)
            }

            // Claim text slots: custom markers first, then closest-first
            val claimedX = mutableListOf<Pair<Float, Float>>()
            val showText = mutableSetOf<Int>()
            val textPad  = 4f

            val priorityOrder = renderItems.indices
                .sortedWith(compareByDescending<Int> { renderItems[it].poi.isCustom }.thenBy { renderItems[it].distM })

            for (idx in priorityOrder) {
                val r  = renderItems[idx]
                val hw = distPaint.measureText(r.distStr) / 2f + textPad
                if (claimedX.none { (cl, cr) -> r.x - hw < cr && r.x + hw > cl }) {
                    showText.add(idx)
                    claimedX.add((r.x - hw) to (r.x + hw))
                }
            }

            // Draw icons; text only where permitted
            for ((i, r) in renderItems.withIndex()) {
                val icon = icons[r.poi.toCategory()] ?: continue
                iconPaint.alpha       = r.alpha
                iconPaint.colorFilter = PorterDuffColorFilter(
                    poiCategoryColorInt(r.poi.toCategory()), PorterDuff.Mode.SRC_IN
                )
                canvas.drawBitmap(
                    icon, null,
                    RectF(r.x - r.drawHalf, iconCenterY - r.drawHalf, r.x + r.drawHalf, iconCenterY + r.drawHalf),
                    iconPaint
                )
                if (i in showText) {
                    distPaint.alpha = (r.alpha * 0.85f).toInt()
                    canvas.drawText(r.distStr, r.x, r.textY, distPaint)
                }
            }

            // Edge markers for out-of-FOV custom POIs
            val maxPerSide = (stripH / (edgeIconPx + 4f)).toInt().coerceIn(1, 3)
            val edgeYSlots = (0 until maxPerSide).map { i ->
                iconCenterY + (i - (maxPerSide - 1) / 2f) * (edgeIconPx + 4f)
            }
            // Offset from the rounded corner so icons aren't clipped
            val edgeXLeft  = edgeIconHalf + cornerR * 0.5f + 2f
            val edgeXRight = widthPx - edgeIconHalf - cornerR * 0.5f - 2f

            fun drawEdgeMarker(cpoi: ComputedPoi, x: Float, y: Float) {
                val icon = icons[cpoi.poi.toCategory()] ?: return
                val normalizedDist = (cpoi.distM / maxDistM).coerceIn(0f, 1f)
                val alpha = (255 * (1f - normalizedDist * 0.45f)).toInt().coerceIn(100, 255)
                iconPaint.alpha       = alpha
                iconPaint.colorFilter = PorterDuffColorFilter(
                    poiCategoryColorInt(cpoi.poi.toCategory()), PorterDuff.Mode.SRC_IN
                )
                canvas.drawBitmap(
                    icon, null,
                    RectF(x - edgeIconHalf, y - edgeIconHalf, x + edgeIconHalf, y + edgeIconHalf),
                    iconPaint
                )
            }

            leftEdge.take(maxPerSide).forEachIndexed  { i, c -> drawEdgeMarker(c, edgeXLeft,  edgeYSlots[i]) }
            rightEdge.take(maxPerSide).forEachIndexed { i, c -> drawEdgeMarker(c, edgeXRight, edgeYSlots[i]) }
        }

        // ── Centre marker — drawn last, always on top ─────────────────────────
        canvas.drawLine(
            cx, stripTop + stripH * 0.08f, cx, stripTop + stripH * 0.92f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(tickColor, 230); strokeWidth = 2.5f
                strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
            }
        )

        return bitmap
    }
}
