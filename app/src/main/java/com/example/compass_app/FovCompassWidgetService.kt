package com.example.compass_app

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.widget.RemoteViews

class FovCompassWidgetService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var orientationSensor: Sensor? = null

    private val bitmapWidth = 800
    private val bitmapHeight = 200

    override fun onCreate() {
        super.onCreate()
        WidgetNotificationManager.attach(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        WidgetNotificationManager.detach(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        pushWidgetUpdate(event.values[0])
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun pushWidgetUpdate(heading: Float) {
        val manager = AppWidgetManager.getInstance(this)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(this, FovCompassWidget::class.java)
        )
        if (widgetIds.isEmpty()) return

        val bitmap = FovCompassWidgetRenderer.render(this, bitmapWidth, bitmapHeight, heading)

        val views = RemoteViews(packageName, R.layout.widget_fov_compass)
        views.setImageViewBitmap(R.id.widget_fov_compass_image, bitmap)

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_fov_root, pendingIntent)

        manager.updateAppWidget(widgetIds, views)
    }
}
