package com.example.compass_app

import android.annotation.SuppressLint
import android.os.Looper
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
    
    private var hasFetchedPois = false
    private val locationService = LocationService()

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

                if (!hasFetchedPois) {
                    fetchPOIs(newLoc.lat, newLoc.lon)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun fetchPOIs(lat: Float, lon: Float) {
        if (hasFetchedPois) return
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                pois = locationService.fetchNearbyPOIs(lat, lon, radius = 2000)
                hasFetchedPois = true
            } catch (e: Exception) {
                errorMessage = "Failed to fetch data: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshPOIs() {
        val loc = userLocation ?: return
        hasFetchedPois = false
        fetchPOIs(loc.lat, loc.lon)
    }
}
