package com.islamicapp.noor.Prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.islamicapp.noor.EventBus
import com.islamicapp.noor.Notifications.AppNotifications
import org.json.JSONObject

class AdhanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra("prayer") ?: "prayer"
        val soundUri = intent.getStringExtra("soundUri") ?: AdhanAlarmManager.storedRingtone()
        val vibrate = intent.getBooleanExtra("vibrate", true)

        AppNotifications.adhan(context, prayer, soundUri, vibrate)

        val payload = JSONObject()
        payload.put("prayer", prayer)
        payload.put("at", System.currentTimeMillis())
        EventBus.emitJson("prayerAlarm", payload)
    }
}
