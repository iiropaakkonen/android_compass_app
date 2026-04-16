package com.example.compass_app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class FovCompassWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        context.startService(Intent(context, FovCompassWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, FovCompassWidgetService::class.java))
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        context.startService(Intent(context, FovCompassWidgetService::class.java))
    }
}
