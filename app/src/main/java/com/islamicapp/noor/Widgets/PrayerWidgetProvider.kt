package com.islamicapp.noor.Widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.islamicapp.noor.Prayer.PrayerTimeManager
import com.islamicapp.noor.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Five prayers, the next prayer and today's hijri date, computed on the device. */
class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
        WidgetUpdater.ensureScheduled(context)
    }

    private fun build(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer)
        val config = WidgetPrefs.get(context, WIDGET)
        val zone = try {
            TimeZone.getTimeZone(config.optString("timeZone", TimeZone.getDefault().id))
        } catch (e: Exception) {
            TimeZone.getDefault()
        }

        val times = PrayerTimeManager.timesJson(
            config.optDouble("latitude", 23.8103),
            config.optDouble("longitude", 90.4125),
            zone.id,
            config.optString("method", "karachi"),
            config.optBoolean("asrHanafi", true)
        ).optJSONObject("times") ?: JSONObject()

        val now = System.currentTimeMillis()
        val order = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        var nextKey = "fajr"
        var nextAt = 0L
        var best = Long.MAX_VALUE
        order.forEach { key ->
            val millis = times.optLong(key, -1L)
            if (millis > now && millis - now < best) {
                best = millis - now
                nextKey = key
                nextAt = millis
            }
        }

        val pattern = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = zone }

        views.setTextViewText(R.id.prayerNext, nextKey.replaceFirstChar { it.uppercase() })
        views.setTextViewText(
            R.id.prayerNextTime,
            if (nextAt > 0L) pattern.format(Date(nextAt)) else "--:--"
        )
        views.setTextViewText(R.id.prayerFajr, pattern.format(Date(times.optLong("fajr", now))))
        views.setTextViewText(R.id.prayerDhuhr, pattern.format(Date(times.optLong("dhuhr", now))))
        views.setTextViewText(R.id.prayerAsr, pattern.format(Date(times.optLong("asr", now))))
        views.setTextViewText(R.id.prayerMaghrib, pattern.format(Date(times.optLong("maghrib", now))))
        views.setTextViewText(R.id.prayerIsha, pattern.format(Date(times.optLong("isha", now))))
        views.setTextViewText(R.id.prayerHijri, hijriToday(zone))
        views.setOnClickPendingIntent(
            R.id.widgetRoot,
            QuranAyahWidgetProvider.openStudio(context, WIDGET, 21)
        )
        return views
    }

    private fun hijriToday(zone: TimeZone): String {
        return try {
            val calendar = android.icu.util.IslamicCalendar(zone, Locale.US)
            calendar.timeInMillis = System.currentTimeMillis()
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            day.toString() + "/" + month + "/" + year + " AH"
        } catch (e: Exception) {
            "Hijri estimate"
        }
    }

    companion object {
        const val WIDGET = "prayer"
    }
}
