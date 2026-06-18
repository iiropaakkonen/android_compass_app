package com.example.compass_app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Uses androids ORIENTATION sensor to find which orientation the phone is at
class CompassSensor(private val sensorManager: SensorManager) : SensorEventListener {

    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading

    var smoothingEnabled = true

    private var smoothedHeading = 0f
    private var initialized = false

    fun start() {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val raw = event.values[0]

        if (!smoothingEnabled) {
            _heading.value = ((raw % 360f) + 360f) % 360f
            return
        }

        if (!initialized) {
            smoothedHeading = raw
            _heading.value = raw
            initialized = true
            return
        }

        //tries to smooth the movement so the application doesnt look janky

        val delta = ((raw - smoothedHeading + 540f) % 360f) - 180f
        smoothedHeading = (smoothedHeading + delta * ALPHA + 360f) % 360f

        val current = _heading.value
        val diff = Math.abs(((smoothedHeading - current + 540f) % 360f) - 180f)
        if (diff >= THRESHOLD) {
            _heading.value = smoothedHeading
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}


    companion object {
        private const val ALPHA = 0.12f   // smoothing strength (lower = smoother but laggier)
        private const val THRESHOLD = 1.0f // minimum change in degrees before the UI updates
    }
}