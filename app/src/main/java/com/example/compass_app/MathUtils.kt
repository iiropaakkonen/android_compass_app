package com.example.compass_app

import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.PI

fun distanceTo(location1: Location, location2: Location): Float {
    val latMid = ((location1.lat + location2.lat) / 2.0) * (PI / 180.0)

    // Meters per degree
    val mPerDegLat = 111132.954 - 559.822 * cos(2.0 * latMid) + 1.175 * cos(4.0 * latMid)
    val mPerDegLon = 111412.84 * cos(latMid) - 93.5 * cos(3.0 * latMid)

    val dx = (location1.lat - location2.lat) * mPerDegLat
    val dy = (location1.lon - location2.lon) * mPerDegLon

    return (sqrt(dx * dx + dy * dy) / 1000.0).toFloat()
}

fun bearingToFloat(from: Location, to: Location): Float {
    val lat1 = from.lat * (PI / 180.0)
    val lat2 = to.lat * (PI / 180.0)
    val dLon = (to.lon - from.lon) * (PI / 180.0)

    val x = sin(dLon) * cos(lat2)
    val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((atan2(x, y) * (180.0 / PI) + 360.0) % 360.0).toFloat()
}

fun bearingTo(from: Location, to: Location): String {
    val lat1 = from.lat * (PI / 180.0)
    val lat2 = to.lat * (PI / 180.0)
    val dLon = (to.lon - from.lon) * (PI / 180.0)

    val x = sin(dLon) * cos(lat2)
    val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = (atan2(x, y) * (180.0 / PI) + 360.0) % 360.0

    return when {
        bearing < 22.5  -> "N"
        bearing < 67.5  -> "NE"
        bearing < 112.5 -> "E"
        bearing < 157.5 -> "SE"
        bearing < 202.5 -> "S"
        bearing < 247.5 -> "SW"
        bearing < 292.5 -> "W"
        bearing < 337.5 -> "NW"
        else            -> "N"
    }
}