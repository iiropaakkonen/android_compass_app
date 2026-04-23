package com.example.compass_app

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.launch

class NearbyViewModel : ViewModel() {
    var userLocation by mutableStateOf<Location?>(null)
        private set

    var pois by mutableStateOf<List<PointOfInterest>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedPoi by mutableStateOf<PointOfInterest?>(null)

    var maxCompassDistanceM by mutableStateOf(1000f)

    var compassLocked by mutableStateOf(false)
        private set
    var lockedHeading by mutableStateOf(0f)
        private set

    fun toggleCompassLock(currentHeading: Float) {
        if (!compassLocked) lockedHeading = currentHeading
        compassLocked = !compassLocked
    }

    var activeFilters by mutableStateOf(PoiCategory.entries.toSet())

    fun toggleFilter(category: PoiCategory) {
        activeFilters = if (activeFilters.contains(category)) {
            if (activeFilters.size == 1) activeFilters // keep at least one selected
            else activeFilters - category
        } else {
            activeFilters + category
        }
    }

    // Tile-based caching
    private val tileCache = TileCache(ttlMinutes = 15)
    private var currentTile: TileCoordinate? = null
    private val locationService = LocationService()

    // Set of tiles currently being fetched (to prevent duplicate requests)
    private val fetchingTiles = mutableSetOf<String>()

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(fusedLocationClient: FusedLocationProviderClient) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(1f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLoc = locationResult.lastLocation ?: return
                val newLoc = Location(lastLoc.latitude.toFloat(), lastLoc.longitude.toFloat())

                userLocation = newLoc
                onLocationChanged(newLoc)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Called when the user's location changes.
     * Checks if the user has entered a new tile and fetches data if needed.
     */
    private fun onLocationChanged(location: Location) {
        val newTile = TileCoordinate.fromLocation(location)

        // Check if we've moved to a new tile
        if (currentTile != newTile) {
            Log.d("TileCache", "═══════════════════════════════════════")
            Log.d("TileCache", "▶ USER ENTERED NEW TILE: ${newTile.toKey()}")
            Log.d("TileCache", "  Previous tile: ${currentTile?.toKey() ?: "none"}")
            Log.d("TileCache", "  Location: ${location.lat}, ${location.lon}")
            currentTile = newTile

            // Log current cache state
            val stats = tileCache.getStats()
            Log.d("TileCache", "  Cache state: ${stats.validTiles} valid tiles, ${stats.expiredTiles} expired, ${stats.totalPois} total POIs")

            // Fetch data for the new tile if not cached
            checkAndFetchTile(newTile)

            Log.d("TileCache", "═══════════════════════════════════════")
        }

        // Update POI list with all cached data
        updatePoisFromCache()
    }

    /**
     * Checks if a tile needs to be fetched and fetches it if necessary.
     * Triggers:
     * - Tile is not in cache
     * - Tile is in cache but expired (handled automatically by cache.get())
     */
    private fun checkAndFetchTile(tile: TileCoordinate) {
        val tileKey = tile.toKey()

        // Check if tile is already cached and valid
        if (tileCache.contains(tile)) {
            Log.d("TileCache", "✓ CACHE HIT: Tile $tileKey found in cache")
            val cachedPois = tileCache.get(tile)
            Log.d("TileCache", "  → Using ${cachedPois?.size ?: 0} cached POIs (no API call needed)")
            return
        }

        // Check if we're already fetching this tile
        if (fetchingTiles.contains(tileKey)) {
            Log.d("TileCache", "⏳ FETCHING IN PROGRESS: Tile $tileKey is already being fetched")
            return
        }

        // Fetch the tile
        Log.d("TileCache", "✗ CACHE MISS: Need to fetch tile $tileKey from API")
        fetchTile(tile)
    }

    /**
     * Fetches POI data for a specific tile from the API.
     */
    private fun fetchTile(tile: TileCoordinate) {
        val tileKey = tile.toKey()
        fetchingTiles.add(tileKey)

        Log.d("TileCache", "─────────────────────────────────────")
        Log.d("TileCache", "📡 FETCHING TILE: $tileKey")

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                // Get the center of the tile to use as the query location
                val centerLocation = TileCoordinate.getCenterLocation(tile)
                Log.d("TileCache", "  Tile center: ${centerLocation.lat}, ${centerLocation.lon}")
                Log.d("TileCache", "  API query: 2km radius around center")

                // Fetch POIs with 2km radius (covers the tile and surrounding area)
                val startTime = System.currentTimeMillis()
                val fetchedPois = locationService.fetchNearbyPOIs(
                    centerLocation.lat,
                    centerLocation.lon,
                    radius = 2000
                )
                val duration = System.currentTimeMillis() - startTime

                // Store in cache
                tileCache.put(tile, fetchedPois)

                Log.d("TileCache", "✓ API CALL COMPLETED in ${duration}ms")
                Log.d("TileCache", "  → Received ${fetchedPois.size} POIs")
                Log.d("TileCache", "  → Stored in cache with 15min TTL")

                // Update the displayed POI list
                updatePoisFromCache()

            } catch (e: Exception) {
                errorMessage = "Failed to fetch data: ${e.message}"
                Log.e("TileCache", "✗ API CALL FAILED for tile $tileKey", e)
            } finally {
                isLoading = false
                fetchingTiles.remove(tileKey)
                Log.d("TileCache", "─────────────────────────────────────")
            }
        }
    }

    /**
     * Updates the POI list from all cached tiles.
     * Filters POIs by distance from current location and sorts them.
     */
    private fun updatePoisFromCache() {
        val currentLoc = userLocation ?: return

        // Get all POIs from cache
        val allCachedPois = tileCache.getAllPois()

        Log.d("TileCache", "📍 UPDATING DISPLAYED POIs")
        Log.d("TileCache", "  Total POIs in cache: ${allCachedPois.size}")

        // Filter POIs within 1km radius and sort by distance
        val filteredPois = allCachedPois.filter { poi ->
            distanceTo(currentLoc, poi.location) <= 1.0f // 1km radius
        }

        pois = filteredPois.sortedBy { poi ->
            distanceTo(currentLoc, poi.location)
        }

        val cacheStats = tileCache.getStats()
        Log.d("TileCache", "  Loaded tiles: ${cacheStats.validTiles}")
        Log.d("TileCache", "  Display radius: 1.0km")
        Log.d("TileCache", "  POIs within 1km: ${pois.size} (filtered from ${allCachedPois.size})")

        if (pois.isNotEmpty()) {
            val nearest = pois.first()
            val farthest = pois.last()
            Log.d("TileCache", "  Nearest POI: ${nearest.name} (${String.format("%.0fm", distanceTo(currentLoc, nearest.location) * 1000)})")
            Log.d("TileCache", "  Farthest POI: ${farthest.name} (${String.format("%.0fm", distanceTo(currentLoc, farthest.location) * 1000)})")
        }
    }

    /**
     * Forces a refresh of the current tile.
     * Trigger: User explicitly refreshes (pull-to-refresh or button).
     */
    fun refreshPOIs() {
        val tile = currentTile ?: return
        val tileKey = tile.toKey()

        Log.d("TileCache", "═══════════════════════════════════════")
        Log.d("TileCache", "🔄 MANUAL REFRESH TRIGGERED")
        Log.d("TileCache", "  Current tile: $tileKey")

        val statsBefore = tileCache.getStats()
        Log.d("TileCache", "  Cache before clear: ${statsBefore.validTiles} tiles, ${statsBefore.totalPois} POIs")

        // Clear the entire cache to force re-fetch
        tileCache.clear()

        Log.d("TileCache", "  ✓ Cache cleared, forcing API call")
        Log.d("TileCache", "═══════════════════════════════════════")

        // Re-fetch the current tile
        fetchTile(tile)
    }

    /**
     * Gets cache statistics for debugging.
     */
    fun getCacheStats(): CacheStats {
        return tileCache.getStats()
    }

    /**
     * Cleans up expired tiles from the cache.
     * Should be called periodically (e.g., on app resume).
     */
    fun cleanExpiredCache() {
        tileCache.cleanExpired()
    }
}
