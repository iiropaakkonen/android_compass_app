package com.example.compass_app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle


class CompassWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        context.startForegroundService(Intent(context, CompassWidgetService::class.java))
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
        context.startForegroundService(Intent(context, CompassWidgetService::class.java))
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Widget was resized — ask the service to re-render immediately at the new size.
        context.startForegroundService(
            Intent(context, CompassWidgetService::class.java)
                .setAction(CompassWidgetService.ACTION_FORCE_UPDATE)
        )
    }
}
