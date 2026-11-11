package com.islamicapp.noor.Customization

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.islamicapp.noor.Utils.PermissionManager
import com.islamicapp.noor.WebAppBridge
import org.json.JSONObject

/** Vibration feedback and the device's last known location. */
object DeviceExtras {

    @Suppress("DEPRECATION")
    fun vibrate(context: Context, millis: Long): JSONObject {
        val duration = millis.coerceIn(10L, 2000L)
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            return JSONObject().put("vibrated", false)
        }
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        return JSONObject().put("vibrated", true).put("ms", duration)
    }

    fun location(context: Context): JSONObject {
        if (!PermissionManager.locationGranted(context)) {
            throw WebAppBridge.PermissionRequired(
                "location",
                "Location permission is needed for prayer times and the Qibla."
            )
        }
        val json = JSONObject()
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) return json.put("available", false).put("reason", "No location service.")

        var best: android.location.Location? = null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            val last = try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
            if (last != null && (best == null || last.time > best.time)) best = last
        }

        if (best == null) {
            return json.put("available", false)
                .put("reason", "No location fix yet. Turn GPS on, then try again.")
        }

        json.put("available", true)
        json.put("latitude", best.latitude)
        json.put("longitude", best.longitude)
        json.put("accuracy", best.accuracy.toDouble())
        json.put("ageMs", System.currentTimeMillis() - best.time)
        return json
    }
}
