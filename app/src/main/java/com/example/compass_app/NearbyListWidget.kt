package com.example.compass_app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class NearbyListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_nearby_list)

            // Wire up the ListView to the RemoteViewsService
            val serviceIntent = Intent(context, NearbyListRemoteViewsService::class.java)
            views.setRemoteAdapter(R.id.nearby_list_view, serviceIntent)
            views.setEmptyView(R.id.nearby_list_view, R.id.nearby_list_empty)

            // Template intent — each row tap opens MainActivity.
            // Must be FLAG_MUTABLE so the launcher can merge the per-row fill-in intent.
            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val template = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.nearby_list_view, template)

            // Title bar tap also opens the app
            val immutableOpen = PendingIntent.getActivity(
                context, 1, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.nearby_list_title, immutableOpen)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
