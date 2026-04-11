package com.example.compass_app

import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.PI

data class Location(val lat: Float, val lon: Float) {
    /**
     * Calculates an approximate distance in kilometers between two locations.
     * Uses a simplified Euclidean approximation suitable for small distances.
     */
    fun distanceTo(other: Location): Float {
        val latMid = ((this.lat + other.lat) / 2.0) * (PI / 180.0)
        
        // Meters per degree
        val mPerDegLat = 111132.954 - 559.822 * cos(2.0 * latMid) + 1.175 * cos(4.0 * latMid)
        val mPerDegLon = 111412.84 * cos(latMid) - 93.5 * cos(3.0 * latMid)

        val dx = (this.lat - other.lat) * mPerDegLat
        val dy = (this.lon - other.lon) * mPerDegLon
        
        return (sqrt(dx * dx + dy * dy) / 1000.0).toFloat()
    }
}
