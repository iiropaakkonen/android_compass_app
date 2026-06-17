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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import kotlin.math.abs
import kotlin.math.min

class FovCompassWidgetService : Service(), SensorEventListener {

    companion object {
        const val ACTION_FORCE_UPDATE = "com.example.compass_app.FOV_FORCE_UPDATE"
    }

    private lateinit var sensorManager: SensorManager
    private var orientationSensor: Sensor? = null

    // Dedicated background thread — rendering stays off the sensor-callback thread
    private lateinit var renderThread: HandlerThread
    private lateinit var renderHandler: Handler

    // Throttle state (accessed only from the sensor callback thread)
    private var lastHeading = Float.NaN
    private var lastRenderMs = 0L

    override fun onCreate() {
        super.onCreate()
        WidgetNotificationManager.attach(this)
        renderThread = HandlerThread("fov-widget-render").also { it.start() }
        renderHandler = Handler(renderThread.looper)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FORCE_UPDATE) {
            val heading = lastHeading
            if (!heading.isNaN()) renderHandler.post { pushWidgetUpdate(heading) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        renderThread.quitSafely()
        WidgetNotificationManager.detach(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        val heading = event.values[0]
        val now     = SystemClock.elapsedRealtime()

        // Angular difference, wrapped to [0, 180]
        val delta = if (lastHeading.isNaN()) Float.MAX_VALUE
                    else { val d = abs(heading - lastHeading) % 360f; min(d, 360f - d) }

        // Skip if heading barely moved AND we rendered recently
        if (delta < 1f && now - lastRenderMs < 100L) return

        lastHeading  = heading
        lastRenderMs = now

        // Drop any queued render (stale heading) and post the latest one
        renderHandler.removeCallbacksAndMessages(null)
        renderHandler.post { pushWidgetUpdate(heading) }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun pushWidgetUpdate(heading: Float) {
        val manager   = AppWidgetManager.getInstance(this)
        val widgetIds = manager.getAppWidgetIds(ComponentName(this, FovCompassWidget::class.java))
        if (widgetIds.isEmpty()) return

        val density   = resources.displayMetrics.density
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        for (id in widgetIds) {
            val options  = manager.getAppWidgetOptions(id)
            val widthPx  = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,  300) * density).toInt().coerceAtLeast(300)
            val heightPx = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,  80) * density).toInt().coerceAtLeast(80)
            val bitmap   = FovCompassWidgetRenderer.render(this, widthPx, heightPx, heading)
            val views    = RemoteViews(packageName, R.layout.widget_fov_compass)
            views.setImageViewBitmap(R.id.widget_fov_compass_image, bitmap)
            views.setOnClickPendingIntent(R.id.widget_fov_root, tapIntent)
            manager.updateAppWidget(id, views)
        }
    }
}
