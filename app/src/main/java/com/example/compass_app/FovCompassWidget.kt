package com.example.compass_app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class FovCompassWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        context.startForegroundService(Intent(context, FovCompassWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, FovCompassWidgetService::class.java))
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        context.startForegroundService(Intent(context, FovCompassWidgetService::class.java))
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        context.startForegroundService(
            Intent(context, FovCompassWidgetService::class.java)
                .setAction(FovCompassWidgetService.ACTION_FORCE_UPDATE)
        )
    }
}
