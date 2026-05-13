package com.example.compass_app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TileCacheStore {

    private const val PREFS_NAME = "tile_cache_store"
    private const val KEY_TILES = "tiles"
    private const val KEY_LAST_OPEN = "last_open_ms"
    private const val ABSENCE_EXPIRY_MS = 3 * 60 * 60 * 1000L

    private val gson = Gson()

    /**
     * Records the current timestamp. Returns true if the app was absent longer than
     * 3 hours, meaning the cache should be treated as expired and cleared.
     */
    fun recordAppOpen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastOpen = prefs.getLong(KEY_LAST_OPEN, 0L)
        val now = System.currentTimeMillis()
        val expired = lastOpen > 0L && (now - lastOpen) > ABSENCE_EXPIRY_MS
        prefs.edit().putLong(KEY_LAST_OPEN, now).apply()
        return expired
    }

    fun save(context: Context, tiles: Map<String, CachedTile>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TILES, gson.toJson(tiles))
            .apply()
    }

    fun load(context: Context): Map<String, CachedTile> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TILES, null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, CachedTile>>() {}.type)
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_TILES).apply()
    }
}
