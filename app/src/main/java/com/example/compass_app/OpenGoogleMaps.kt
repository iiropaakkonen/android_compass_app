package com.example.compass_app

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openGoogleMaps(context: Context, address: String) {
    val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    context.startActivity(intent)
}

fun openGoogleMapsCoordinates(context: Context, lat: Float, lon: Float, label: String) {
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    context.startActivity(intent)
}