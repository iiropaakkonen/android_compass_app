package com.example.compass_app

import kotlin.math.floor

data class TileCoordinate(
    val zoom: Int,
    val tileX: Int,
    val tileY: Int
) {
    fun toKey(): String = "$zoom:$tileX:$tileY"

    companion object {
        // Zoom level 14 gives approximately 500m x 500m tiles at the equator
        // (Each tile at zoom 14 is about 611m x 611m, close enough to our 500m target)
        const val DEFAULT_ZOOM = 14

        fun fromLocation(location: Location, zoom: Int = DEFAULT_ZOOM): TileCoordinate {
            val n = 1 shl zoom // 2^zoom

            // Convert latitude to tile Y (uses Mercator projection)
            val latRad = Math.toRadians(location.lat.toDouble())
            val tileY = floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()

            // Convert longitude to tile X (simple linear mapping)
            val tileX = floor((location.lon + 180.0) / 360.0 * n).toInt()

            return TileCoordinate(zoom, tileX, tileY)
        }


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


data class CachedTile(
    val tile: TileCoordinate,
    val pois: List<PointOfInterest>,
    val fetchedAt: Long // Timestamp in milliseconds
) {
    fun isExpired(ttlMillis: Long): Boolean {
        return System.currentTimeMillis() - fetchedAt > ttlMillis
    }
}

class TileCache(
    private val ttlMinutes: Int = 15 // Default TTL: 15 minutes
) {
    private val cache = mutableMapOf<String, CachedTile>()
    private val ttlMillis = ttlMinutes * 60 * 1000L


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

    @Synchronized
    fun put(tile: TileCoordinate, pois: List<PointOfInterest>) {
        val key = tile.toKey()
        cache[key] = CachedTile(
            tile = tile,
            pois = pois,
            fetchedAt = System.currentTimeMillis()
        )
    }


    @Synchronized
    fun contains(tile: TileCoordinate): Boolean {
        return get(tile) != null
    }


    @Synchronized
    fun clear() {
        cache.clear()
    }


    @Synchronized
    fun cleanExpired() {
        val expired = cache.filter { (_, cachedTile) ->
            cachedTile.isExpired(ttlMillis)
        }.keys

        expired.forEach { cache.remove(it) }
    }


    @Synchronized
    fun getAllPois(): List<PointOfInterest> {
        return cache.values.flatMap { it.pois }.distinctBy { it.id }
    }


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

data class CacheStats(
    val totalTiles: Int,
    val validTiles: Int,
    val expiredTiles: Int,
    val totalPois: Int
)
