package com.islamicapp.noor.Prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.islamicapp.noor.Storage.AppPreferences
import org.json.JSONObject

object AdhanAlarmManager {

    private val REQUEST_CODES = mapOf(
        "fajr" to 7101,
        "dhuhr" to 7102,
        "asr" to 7103,
        "maghrib" to 7104,
        "isha" to 7105
    )

    fun init(context: Context) {
        // Channels are created by AppNotifications during app start.
    }

    fun schedule(
        context: Context,
        prayer: String,
        triggerAtMillis: Long,
        soundUri: String,
        vibrate: Boolean
    ): JSONObject {
        val json = JSONObject()
        if (triggerAtMillis <= System.currentTimeMillis()) {
            json.put("scheduled", false)
            json.put("reason", "trigger time is in the past")
            return json
        }

        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, prayer, triggerAtMillis, soundUri, vibrate)
        var exact = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                exact = false
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            exact = false
            manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }

        json.put("scheduled", true)
        json.put("exact", exact)
        json.put("prayer", prayer)
        json.put("triggerAtMillis", triggerAtMillis)
        if (!exact) {
            json.put("note", "Exact alarms are not allowed. Grant the alarm permission for on-time adhan.")
        }
        return json
    }

    fun cancel(context: Context, prayer: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, prayer, 0L, "", true))
    }

    fun rescheduleAll(context: Context) {
        // The web layer asks for today's times on start; nothing is cached here.
    }

    private fun pendingIntent(
        context: Context,
        prayer: String,
        triggerAtMillis: Long,
        soundUri: String,
        vibrate: Boolean
    ): PendingIntent {
        val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
            action = "com.islamicapp.noor.ADHAN_" + prayer
            putExtra("prayer", prayer)
            putExtra("soundUri", soundUri)
            putExtra("vibrate", vibrate)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODES[prayer] ?: 7109,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun storedRingtone(): String = AppPreferences.current().ringtoneUri
}
