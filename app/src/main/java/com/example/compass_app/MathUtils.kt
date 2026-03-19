package com.example.compass_app

import kotlin.math.sqrt


fun Distance(location1: Location, location2: Location): Float
{
    val dx = location1.lat - location2.lat
    val dy = location1.lon - location2.lon
    return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

