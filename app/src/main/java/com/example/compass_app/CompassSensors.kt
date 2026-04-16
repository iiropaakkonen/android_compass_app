package com.example.compass_app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CompassSensor(private val sensorManager: SensorManager) : SensorEventListener {

    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    // Where phone is pointed in degrees 0-360
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading

    fun start() {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        //Most important function
        var degree = Math.round(event.values[0])
        _heading.value = degree.toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}