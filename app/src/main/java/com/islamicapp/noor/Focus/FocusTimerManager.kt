package com.islamicapp.noor.Focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.islamicapp.noor.EventBus
import com.islamicapp.noor.Notifications.AppNotifications
import com.islamicapp.noor.Storage.AppDatabase
import com.islamicapp.noor.Storage.AppPreferences
import com.islamicapp.noor.Storage.FocusSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object FocusTimerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val REQUEST_CODE = 4102

    fun init(context: Context) {
        // Nothing to warm up: state lives in DataStore and an exact alarm.
    }

    fun start(context: Context, minutes: Int): JSONObject {
        val span = minutes.coerceIn(1, 240).toLong() * 60_000L
        val now = System.currentTimeMillis()
        val until = now + span
        scope.launch { AppPreferences.setFocus(now, until, span, true) }
        scheduleAlarm(context, until)
        AppNotifications.focusStarted(context, minutes)
        val payload = status(context)
        EventBus.emitJson("focus", payload)
        return payload
    }

    fun pause(context: Context): JSONObject {
        val prefs = AppPreferences.current()
        val remaining = (prefs.focusUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        scope.launch { AppPreferences.setFocus(prefs.focusStartedAt, 0L, remaining, false) }
        cancelAlarm(context)
        val payload = status(context)
        EventBus.emitJson("focus", payload)
        return payload
    }

    fun resume(context: Context): JSONObject {
        val prefs = AppPreferences.current()
        val remaining = prefs.focusRemainingMs.coerceAtLeast(60_000L)
        val until = System.currentTimeMillis() + remaining
        scope.launch { AppPreferences.setFocus(prefs.focusStartedAt, until, remaining, true) }
        scheduleAlarm(context, until)
        val payload = status(context)
        EventBus.emitJson("focus", payload)
        return payload
    }

    fun status(context: Context): JSONObject {
        val prefs = AppPreferences.current()
        val remaining = if (prefs.focusRunning) {
            (prefs.focusUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            prefs.focusRemainingMs
        }
        val json = JSONObject()
        json.put("running", prefs.focusRunning)
        json.put("remainingSeconds", remaining / 1000L)
        json.put("startedAt", prefs.focusStartedAt)
        return json
    }

    suspend fun history(context: Context): JSONArray {
        val array = JSONArray()
        val rows = AppDatabase.get(context).focusDao().recent(30)
        for (row in rows) {
            val json = JSONObject()
            json.put("startedAt", row.startedAt)
            json.put("minutes", row.minutes)
            json.put("completed", row.completed)
            array.put(json)
        }
        return array
    }

    fun onAlarm(context: Context) {
        val prefs = AppPreferences.current()
        if (!prefs.focusRunning) return
        scope.launch {
            AppPreferences.setFocus(prefs.focusStartedAt, 0L, 0L, false)
            val minutes = ((System.currentTimeMillis() - prefs.focusStartedAt) / 60_000L).toInt()
            AppDatabase.get(context).focusDao().insert(
                FocusSession(
                    startedAt = prefs.focusStartedAt,
                    endedAt = System.currentTimeMillis(),
                    minutes = minutes,
                    completed = true
                )
            )
        }
        AppNotifications.show(context, "Focus session complete", "Well done. Your focus time is saved.")
        EventBus.emitJson("focus", status(context))
    }

    fun restoreAfterReboot(context: Context) {
        val prefs = AppPreferences.current()
        if (!prefs.focusRunning) return
        if (prefs.focusUntil > System.currentTimeMillis()) {
            scheduleAlarm(context, prefs.focusUntil)
        } else {
            onAlarm(context)
        }
    }

    private fun scheduleAlarm(context: Context, triggerAt: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FocusAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancelAlarm(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FocusAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
    }
}
