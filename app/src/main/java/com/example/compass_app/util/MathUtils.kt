package com.example.compass_app

import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.PI

//Distance to POI's
fun distanceTo(location1: Location, location2: Location): Float {
    val latMid = ((location1.lat + location2.lat) / 2.0) * (PI / 180.0)

    // Meters per degree
    val mPerDegLat = 111132.954 - 559.822 * cos(2.0 * latMid) + 1.175 * cos(4.0 * latMid)
    val mPerDegLon = 111412.84 * cos(latMid) - 93.5 * cos(3.0 * latMid)

    val dx = (location1.lat - location2.lat) * mPerDegLat
    val dy = (location1.lon - location2.lon) * mPerDegLon

    return (sqrt(dx * dx + dy * dy) / 1000.0).toFloat()
}

// bearing to location
fun bearingToFloat(from: Location, to: Location): Float {
    val lat1 = from.lat * (PI / 180.0)
    val lat2 = to.lat * (PI / 180.0)
    val dLon = (to.lon - from.lon) * (PI / 180.0)

    val x = sin(dLon) * cos(lat2)
    val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((atan2(x, y) * (180.0 / PI) + 360.0) % 360.0).toFloat()
}

//filter to avoid clutter
fun applySmartFilter(
    pois: List<PointOfInterest>,
    userLocation: Location?,
    enabled: Boolean,
    favorites: Set<Long> = emptySet()
): List<PointOfInterest> {
    val sorted = if (userLocation != null)
        pois.sortedBy { distanceTo(userLocation, it.location) }
    else
        pois

    if (!enabled) return sorted

    // Show only the nearest 10 POIs per category; favourites are never culled
    val countByCategory = mutableMapOf<PoiCategory, Int>()
    return sorted.filter { poi ->
        val isFav = poi.id in favorites
        val count = countByCategory.getOrDefault(poi.category, 0)
        val allow = isFav || count < 10
        if (!isFav && allow) countByCategory[poi.category] = count + 1
        allow
    }
}

//returns a direction as string from the previous float
fun bearingTo(from: Location, to: Location): String {
    return when (val b = bearingToFloat(from, to)) {
        in 0f..22.5f   -> "N"
        in 22.5f..67.5f  -> "NE"
        in 67.5f..112.5f -> "E"
        in 112.5f..157.5f -> "SE"
        in 157.5f..202.5f -> "S"
        in 202.5f..247.5f -> "SW"
        in 247.5f..292.5f -> "W"
        in 292.5f..337.5f -> "NW"
        else -> "N"
    }
}