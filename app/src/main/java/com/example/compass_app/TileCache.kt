package com.example.compass_app

import kotlin.math.floor

/**
 * Represents a tile coordinate in the grid system.
 * Uses a simplified tile system based on fixed-size cells (default ~500m at equator).
 *
 * The tile key format is: "zoom:tileX:tileY"
 * For simplicity, we use a fixed zoom level (default 14) which gives ~500m tiles.
 */
data class TileCoordinate(
    val zoom: Int,
    val tileX: Int,
    val tileY: Int
) {
    /**
     * Returns a unique string key for this tile.
     */
    fun toKey(): String = "$zoom:$tileX:$tileY"

    companion object {
        // Zoom level 14 gives approximately 500m x 500m tiles at the equator
        // (Each tile at zoom 14 is about 611m x 611m, close enough to our 500m target)
        const val DEFAULT_ZOOM = 14

        /**
         * Converts a geographic location (lat, lon) to a tile coordinate.
         *
         * Uses Web Mercator projection tile coordinates:
         * - The world is divided into 2^zoom × 2^zoom tiles
         * - At zoom 0: 1 tile covers the entire world
         * - At zoom 14: ~2.4 million tiles cover the world (each ~500m × 500m)
         */
        fun fromLocation(location: Location, zoom: Int = DEFAULT_ZOOM): TileCoordinate {
            val n = 1 shl zoom // 2^zoom

            // Convert latitude to tile Y (uses Mercator projection)
            val latRad = Math.toRadians(location.lat.toDouble())
            val tileY = floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()

            // Convert longitude to tile X (simple linear mapping)
            val tileX = floor((location.lon + 180.0) / 360.0 * n).toInt()

            return TileCoordinate(zoom, tileX, tileY)
        }

        /**
         * Gets the center location of a tile.
         * Useful for fetching POIs centered on the tile.
         */
        fun getCenterLocation(tile: TileCoordinate): Location {
            val n = 1 shl tile.zoom

            // Convert tile X to longitude (center of tile)
            val lon = ((tile.tileX + 0.5) / n * 360.0 - 180.0).toFloat()

            // Convert tile Y to latitude (center of tile)
            val latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * (tile.tileY + 0.5) / n)))
            val lat = Math.toDegrees(latRad).toFloat()

            return Location(lat, lon)
        }
    }
}

/**
 * Represents cached data for a single tile.
 */
data class CachedTile(
    val tile: TileCoordinate,
    val pois: List<PointOfInterest>,
    val fetchedAt: Long // Timestamp in milliseconds
) {
    /**
     * Checks if this cached tile has expired based on the TTL.
     */
    fun isExpired(ttlMillis: Long): Boolean {
        return System.currentTimeMillis() - fetchedAt > ttlMillis
    }
}

/**
 * Grid-based tile cache for POI data.
 *
 * Features:
 * - Tiles are keyed by {zoom}:{tileX}:{tileY}
 * - Each tile has a TTL (time-to-live)
 * - Automatic expiration of old tiles
 * - Thread-safe operations
 */
class TileCache(
    private val ttlMinutes: Int = 15 // Default TTL: 15 minutes
) {
    private val cache = mutableMapOf<String, CachedTile>()
    private val ttlMillis = ttlMinutes * 60 * 1000L

    /**
     * Gets cached POIs for a specific tile if available and not expired.
     * Returns null if the tile is not cached or has expired.
     */
    @Synchronized
    fun get(tile: TileCoordinate): List<PointOfInterest>? {
        val key = tile.toKey()
        val cached = cache[key] ?: return null

        return if (cached.isExpired(ttlMillis)) {
            // Remove expired entry
            cache.remove(key)
            null
        } else {
            cached.pois
        }
    }

    /**
     * Stores POIs for a specific tile in the cache.
     */
    @Synchronized
    fun put(tile: TileCoordinate, pois: List<PointOfInterest>) {
        val key = tile.toKey()
        cache[key] = CachedTile(
            tile = tile,
            pois = pois,
            fetchedAt = System.currentTimeMillis()
        )
    }

    /**
     * Checks if a tile is cached and not expired.
     */
    @Synchronized
    fun contains(tile: TileCoordinate): Boolean {
        return get(tile) != null
    }

    /**
     * Clears all cached tiles.
     */
    @Synchronized
    fun clear() {
        cache.clear()
    }

    /**
     * Removes expired tiles from the cache.
     * Call this periodically to prevent memory bloat.
     */
    @Synchronized
    fun cleanExpired() {
        val expired = cache.filter { (_, cachedTile) ->
            cachedTile.isExpired(ttlMillis)
        }.keys

        expired.forEach { cache.remove(it) }
    }

    /**
     * Gets all POIs from all cached tiles (regardless of expiration).
     * Useful for displaying all available data while fetching new tiles.
     */
    @Synchronized
    fun getAllPois(): List<PointOfInterest> {
        return cache.values.flatMap { it.pois }.distinctBy { it.id }
    }

    /**
     * Gets cache statistics for debugging.
     */
    @Synchronized
    fun getStats(): CacheStats {
        val now = System.currentTimeMillis()
        val validTiles = cache.values.count { !it.isExpired(ttlMillis) }
        val expiredTiles = cache.size - validTiles
        val totalPois = cache.values.flatMap { it.pois }.distinctBy { it.id }.size

        return CacheStats(
            totalTiles = cache.size,
            validTiles = validTiles,
            expiredTiles = expiredTiles,
            totalPois = totalPois
        )
    }
}

/**
 * Statistics about the cache state.
 */
data class CacheStats(
    val totalTiles: Int,
    val validTiles: Int,
    val expiredTiles: Int,
    val totalPois: Int
)
