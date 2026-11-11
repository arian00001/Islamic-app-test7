package com.islamicapp.noor.Widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject

/** Refreshes every placed widget and keeps a 15 minute rotation alarm alive. */
object WidgetUpdater {

    private const val INTERVAL_MS = 15 * 60 * 1000L
    private const val REQUEST_CODE = 4200

    private val PROVIDERS = listOf(
        QuranAyahWidgetProvider::class.java,
        PrayerWidgetProvider::class.java,
        TasbihWidgetProvider::class.java
    )

    fun providerFor(widget: String): Class<*>? = when (widget) {
        "quran_ayah" -> QuranAyahWidgetProvider::class.java
        "prayer" -> PrayerWidgetProvider::class.java
        "tasbih" -> TasbihWidgetProvider::class.java
        else -> null
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        PROVIDERS.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids == null || ids.isEmpty()) return@forEach
            val intent = Intent(context, provider)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    fun ensureScheduled(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + INTERVAL_MS,
                INTERVAL_MS,
                pending
            )
        } catch (ignored: Exception) {
            // Rotation is a convenience; the widget still updates when the app opens.
        }
    }

    fun count(context: Context, widget: String): Int {
        val provider = providerFor(widget) ?: return 0
        val manager = AppWidgetManager.getInstance(context) ?: return 0
        return manager.getAppWidgetIds(ComponentName(context, provider))?.size ?: 0
    }

    fun requestPin(context: Context, widget: String): JSONObject {
        val result = JSONObject()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return result.put("requested", false)
                .put("reason", "Android 8.0 or newer can place widgets from an app.")
        }
        val provider = providerFor(widget)
            ?: return result.put("requested", false).put("reason", "Unknown widget: " + widget)

        val manager = AppWidgetManager.getInstance(context)
        if (manager == null || !manager.isRequestPinAppWidgetSupported) {
            return result.put("requested", false)
                .put("reason", "This launcher does not let apps place widgets. Add it from the widget picker.")
        }

        val accepted = manager.requestPinAppWidget(ComponentName(context, provider), null, null)
        ensureScheduled(context)
        refreshAll(context)
        return result.put("requested", accepted)
            .put("reason", if (accepted) "" else "The launcher refused the request.")
    }

    fun notifyConfigChanged(context: Context, widget: String) {
        refreshAll(context)
        ensureScheduled(context)
    }
}
