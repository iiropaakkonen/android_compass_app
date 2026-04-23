package com.example.compass_app

import android.annotation.SuppressLint
import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.launch

class NearbyViewModel(application: Application) : AndroidViewModel(application) {
    var userLocation by mutableStateOf<Location?>(null)
        private set

    var pois by mutableStateOf<List<PointOfInterest>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedPoi by mutableStateOf<PointOfInterest?>(null)

    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set

    var customPois by mutableStateOf<List<PointOfInterest>>(emptyList())
        private set

    private val poiStorage = PoiStorage(application)
    private val tileCache = TileCache(ttlMinutes = 15)
    private var currentTile: TileCoordinate? = null
    private val locationService = LocationService()
    private val fetchingTiles = mutableSetOf<String>()

    init {
        favorites = poiStorage.getFavoriteIds()
        customPois = poiStorage.getCustomPois()
        pois = customPois
    }

    fun toggleFavorite(poi: PointOfInterest) {
        poiStorage.setFavorited(poi.id, !poiStorage.isFavorited(poi.id))
        favorites = poiStorage.getFavoriteIds()
    }

    fun addCustomPoi(name: String, category: PoiCategory, lat: Float, lon: Float) {
        poiStorage.addCustomPoi(name, category, lat, lon)
        customPois = poiStorage.getCustomPois()
        updatePoisFromCache()
    }

    fun deleteCustomPoi(id: Long) {
        poiStorage.deleteCustomPoi(id)
        customPois = poiStorage.getCustomPois()
        updatePoisFromCache()
    }

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

    private fun onLocationChanged(location: Location) {
        val newTile = TileCoordinate.fromLocation(location)

        if (currentTile != newTile) {
            Log.d("TileCache", "═══════════════════════════════════════")
            Log.d("TileCache", "▶ USER ENTERED NEW TILE: ${newTile.toKey()}")
            Log.d("TileCache", "  Previous tile: ${currentTile?.toKey() ?: "none"}")
            Log.d("TileCache", "  Location: ${location.lat}, ${location.lon}")
            currentTile = newTile

            val stats = tileCache.getStats()
            Log.d("TileCache", "  Cache state: ${stats.validTiles} valid tiles, ${stats.expiredTiles} expired, ${stats.totalPois} total POIs")

            checkAndFetchTile(newTile)

            Log.d("TileCache", "═══════════════════════════════════════")
        }

        updatePoisFromCache()
    }

    private fun checkAndFetchTile(tile: TileCoordinate) {
        val tileKey = tile.toKey()

        if (tileCache.contains(tile)) {
            Log.d("TileCache", "✓ CACHE HIT: Tile $tileKey found in cache")
            val cachedPois = tileCache.get(tile)
            Log.d("TileCache", "  → Using ${cachedPois?.size ?: 0} cached POIs (no API call needed)")
            return
        }

        if (fetchingTiles.contains(tileKey)) {
            Log.d("TileCache", "⏳ FETCHING IN PROGRESS: Tile $tileKey is already being fetched")
            return
        }

        Log.d("TileCache", "✗ CACHE MISS: Need to fetch tile $tileKey from API")
        fetchTile(tile)
    }

    private fun fetchTile(tile: TileCoordinate) {
        val tileKey = tile.toKey()
        fetchingTiles.add(tileKey)

        Log.d("TileCache", "─────────────────────────────────────")
        Log.d("TileCache", "📡 FETCHING TILE: $tileKey")

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val centerLocation = TileCoordinate.getCenterLocation(tile)
                Log.d("TileCache", "  Tile center: ${centerLocation.lat}, ${centerLocation.lon}")
                Log.d("TileCache", "  API query: 2km radius around center")

                val startTime = System.currentTimeMillis()
                val fetchedPois = locationService.fetchNearbyPOIs(
                    centerLocation.lat,
                    centerLocation.lon,
                    radius = 2000
                )
                val duration = System.currentTimeMillis() - startTime

                tileCache.put(tile, fetchedPois)

                Log.d("TileCache", "✓ API CALL COMPLETED in ${duration}ms")
                Log.d("TileCache", "  → Received ${fetchedPois.size} POIs")
                Log.d("TileCache", "  → Stored in cache with 15min TTL")

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

    private fun updatePoisFromCache() {
        val currentLoc = userLocation
        val cachedPois = if (currentLoc != null) {
            tileCache.getAllPois().filter { distanceTo(currentLoc, it.location) <= 1.0f }
        } else {
            emptyList()
        }

        Log.d("TileCache", "📍 UPDATING DISPLAYED POIs")
        Log.d("TileCache", "  Total POIs in cache: ${cachedPois.size}")

        val merged = (cachedPois + customPois).distinctBy { it.id }
        pois = if (currentLoc != null) {
            merged.sortedBy { distanceTo(currentLoc, it.location) }
        } else {
            customPois
        }

        val cacheStats = tileCache.getStats()
        Log.d("TileCache", "  Loaded tiles: ${cacheStats.validTiles}")
        Log.d("TileCache", "  Display radius: 1.0km")
        Log.d("TileCache", "  POIs within 1km: ${pois.size}")
    }

    fun refreshPOIs() {
        val tile = currentTile ?: return
        val tileKey = tile.toKey()

        Log.d("TileCache", "═══════════════════════════════════════")
        Log.d("TileCache", "🔄 MANUAL REFRESH TRIGGERED")
        Log.d("TileCache", "  Current tile: $tileKey")

        val statsBefore = tileCache.getStats()
        Log.d("TileCache", "  Cache before clear: ${statsBefore.validTiles} tiles, ${statsBefore.totalPois} POIs")

        tileCache.clear()

        Log.d("TileCache", "  ✓ Cache cleared, forcing API call")
        Log.d("TileCache", "═══════════════════════════════════════")

        fetchTile(tile)
    }

    fun getCacheStats(): CacheStats = tileCache.getStats()

    fun cleanExpiredCache() {
        tileCache.cleanExpired()
    }
}
