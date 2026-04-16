package com.example.compass_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import androidx.core.app.NotificationCompat

object WidgetNotificationManager {

    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "compass_widget"

    private var activeCount = 0

    @Synchronized
    fun attach(service: Service) {
        if (activeCount == 0) createChannel(service)
        activeCount++
        service.startForeground(NOTIFICATION_ID, buildNotification(service))
    }

    @Synchronized
    fun detach(service: Service) {
        activeCount = maxOf(0, activeCount - 1)
        if (activeCount == 0) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            service.stopForeground(Service.STOP_FOREGROUND_DETACH)
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Compass Widget",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps compass widgets updated in the background"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Compass")
            .setContentText("Widget active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
