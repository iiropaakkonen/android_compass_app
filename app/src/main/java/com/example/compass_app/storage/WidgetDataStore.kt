package com.example.compass_app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Stores/pulls data from and to widgets
data class WidgetPoi(
    val name: String,
    val category: String,
    val lat: Float,
    val lon: Float,
    val isCustom: Boolean = false
) {
    fun toCategory(): PoiCategory =
        try { PoiCategory.valueOf(category) } catch (_: Exception) { PoiCategory.OTHER }
}


object WidgetDataStore {

    private const val PREFS_NAME   = "widget_data"
    private const val KEY_USER_LAT = "user_lat"
    private const val KEY_USER_LON = "user_lon"
    private const val KEY_HAS_LOC  = "has_location"
    private const val KEY_POIS     = "pois_json"

    private const val MAX_POIS = 30

    private val gson = Gson()

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
