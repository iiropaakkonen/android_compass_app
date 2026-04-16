package com.example.compass_app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent


class CompassWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        context.startService(Intent(context, CompassWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, CompassWidgetService::class.java))
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Ensure the service is running after a reboot or if Android killed it.
        context.startService(Intent(context, CompassWidgetService::class.java))
    }
}
