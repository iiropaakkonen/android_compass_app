package com.example.compass_app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private data class StoredCustomPoi(
    val id: Long,
    val name: String,
    val category: String,
    val lat: Float,
    val lon: Float
)

class PoiStorage(context: Context) {
    private val prefs = context.getSharedPreferences("poi_storage", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getFavoriteIds(): Set<Long> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        val type = object : TypeToken<Set<Long>>() {}.type
        return gson.fromJson(json, type) ?: emptySet()
    }

    fun isFavorited(id: Long) = id in getFavoriteIds()

    fun setFavorited(id: Long, favorited: Boolean) {
        val current = getFavoriteIds().toMutableSet()
        if (favorited) current.add(id) else current.remove(id)
        prefs.edit().putString("favorites", gson.toJson(current)).apply()
    }

    fun getCustomPois(): List<PointOfInterest> {
        val json = prefs.getString("custom_pois", "[]") ?: "[]"
        val type = object : TypeToken<List<StoredCustomPoi>>() {}.type
        val stored: List<StoredCustomPoi> = gson.fromJson(json, type) ?: emptyList()
        return stored.map { it.toPointOfInterest() }
    }

    fun addCustomPoi(name: String, category: PoiCategory, lat: Float, lon: Float): PointOfInterest {
        val stored = getRawCustomPois().toMutableList()
        val newId = (stored.minOfOrNull { it.id } ?: 0L) - 1L
        val new = StoredCustomPoi(id = newId, name = name, category = category.name, lat = lat, lon = lon)
        stored.add(new)
        prefs.edit().putString("custom_pois", gson.toJson(stored)).apply()
        return new.toPointOfInterest()
    }

    fun deleteCustomPoi(id: Long) {
        val stored = getRawCustomPois().filter { it.id != id }
        prefs.edit().putString("custom_pois", gson.toJson(stored)).apply()
    }

    private fun getRawCustomPois(): List<StoredCustomPoi> {
        val json = prefs.getString("custom_pois", "[]") ?: "[]"
        val type = object : TypeToken<List<StoredCustomPoi>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}

private fun StoredCustomPoi.toPointOfInterest() = PointOfInterest(
    id = id,
    name = name,
    locationType = "custom:pin",
    category = try { PoiCategory.valueOf(category) } catch (_: Exception) { PoiCategory.OTHER },
    location = Location(lat, lon)
)
