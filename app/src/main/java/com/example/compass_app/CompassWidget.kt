package com.example.compass_app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle


class CompassWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        startService(context, Intent(context, CompassWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, CompassWidgetService::class.java))
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        startService(context, Intent(context, CompassWidgetService::class.java))
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        startService(
            context,
            Intent(context, CompassWidgetService::class.java)
                .setAction(CompassWidgetService.ACTION_FORCE_UPDATE)
        )
    }

    private fun startService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
