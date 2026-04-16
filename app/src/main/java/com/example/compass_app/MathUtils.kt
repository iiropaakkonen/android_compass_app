package com.example.compass_app

import kotlin.math.sqrt
import kotlin.math.cos
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