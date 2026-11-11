package com.islamicapp.noor.Prayer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.islamicapp.noor.EventBus
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object QiblaManager {

    private const val KAABA_LAT = 21.4224779
    private const val KAABA_LON = 39.8251832

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null

    fun bearingToKaaba(latitude: Double, longitude: Double): JSONObject {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(KAABA_LAT)
        val dLon = Math.toRadians(KAABA_LON - longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) bearing += 360.0

        val json = JSONObject()
        json.put("bearing", bearing)
        json.put("distanceKm", haversine(latitude, longitude))
        json.put("available", true)
        return json
    }

    /** Rotates device heading into compass events consumed by the WebView. */
    fun startCompass(context: Context): JSONObject {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        val json = JSONObject()
        if (sensor == null) {
            json.put("available", false)
            json.put("reason", "This device has no rotation or compass sensor")
            return json
        }

        stopCompass()
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)

        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                } else {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                }
                SensorManager.getOrientation(rotation, orientation)
                var degrees = Math.toDegrees(orientation[0].toDouble())
                if (degrees < 0) degrees += 360.0
                val payload = JSONObject()
                payload.put("heading", degrees)
                EventBus.emitJson("compass", payload)
            }

            override fun onAccuracyChanged(changed: Sensor?, accuracy: Int) {
                val payload = JSONObject()
                payload.put("accuracy", accuracy)
                EventBus.emitJson("compassAccuracy", payload)
            }
        }

        manager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager = manager
        listener = newListener

        json.put("available", true)
        json.put("sensor", sensor.name)
        return json
    }

    fun stopCompass() {
        val manager = sensorManager
        val active = listener
        if (manager != null && active != null) manager.unregisterListener(active)
        sensorManager = null
        listener = null
    }

    private fun haversine(latitude: Double, longitude: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(KAABA_LAT - latitude)
        val dLon = Math.toRadians(KAABA_LON - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(KAABA_LAT)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
    }
}
