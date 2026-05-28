package com.example.compass_app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Lightweight POI record written by the app and read by widget renderers.
 * Uses string category name so it survives serialization safely.
 */
data class WidgetPoi(
    val name: String,
    val category: String,
    val lat: Float,
    val lon: Float,
    val isCustom: Boolean = false   // true for user-created pins (id < 0)
) {
    fun toCategory(): PoiCategory =
        try { PoiCategory.valueOf(category) } catch (_: Exception) { PoiCategory.OTHER }
}

/**
 * Persistence bridge from the app's ViewModel to the widget renderers.
 *
 * The app calls [save] whenever POIs or user location change.
 * Widget renderers call [getUserLocation] / [getPois] on every render pass.
 *
 * Uses a separate prefs file ("widget_data") to avoid collisions with ThemePrefs.
 */
object WidgetDataStore {

    private const val PREFS_NAME   = "widget_data"
    private const val KEY_USER_LAT = "user_lat"
    private const val KEY_USER_LON = "user_lon"
    private const val KEY_HAS_LOC  = "has_location"
    private const val KEY_POIS     = "pois_json"

    /** Max POIs persisted; nearest-first sorting in the ViewModel means these are the closest. */
    private const val MAX_POIS = 30

    private val gson = Gson()

    /** Called by NearbyViewModel whenever pois or userLocation change. */
    fun save(context: Context, userLocation: Location?, pois: List<PointOfInterest>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            if (userLocation != null) {
                putBoolean(KEY_HAS_LOC, true)
                putFloat(KEY_USER_LAT, userLocation.lat)
                putFloat(KEY_USER_LON, userLocation.lon)
            } else {
                putBoolean(KEY_HAS_LOC, false)
            }
            putString(KEY_POIS, gson.toJson(
                pois.take(MAX_POIS).map { WidgetPoi(it.name, it.category.name, it.location.lat, it.location.lon, it.id < 0) }
            ))
            apply()
        }

        // Notify the list widget so it re-fetches row data immediately
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NearbyListWidget::class.java))
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.nearby_list_view)
        }
    }

    fun getUserLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_LOC, false)) return null
        return Location(prefs.getFloat(KEY_USER_LAT, 0f), prefs.getFloat(KEY_USER_LON, 0f))
    }

    fun getPois(context: Context): List<WidgetPoi> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_POIS, "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<WidgetPoi>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
