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
import java.util.GregorianCalendar
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
            // Tabular Hijri conversion: no ICU dependency, identical on every device.
            val calendar = GregorianCalendar(zone, Locale.US)
            calendar.time = Date(System.currentTimeMillis())
            var year = calendar.get(Calendar.YEAR)
            var month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            if (month < 3) {
                year -= 1
                month += 12
            }
            val a = year / 100
            val b = 2 - a + a / 4
            val julianDay = (365.25 * (year + 4716)).toInt() +
                (30.6001 * (month + 1)).toInt() + day + b - 1524
            val shift = julianDay - 1948440 + 10632
            val cycle = (shift - 1) / 10631
            val rest = shift - 10631 * cycle + 354
            val jump = ((10985 - rest) / 5316) * (50 * rest / 17719) +
                (rest / 5670) * (43 * rest / 15238)
            val adjusted = rest - ((30 - jump) / 15) * (17719 * jump / 50) -
                (jump / 16) * (15238 * jump / 43) + 29
            val hijriMonth = 24 * adjusted / 709
            val hijriDay = adjusted - 709 * hijriMonth / 24
            val hijriYear = 30 * cycle + jump - 30
            hijriDay.toString() + "/" + hijriMonth + "/" + hijriYear + " AH"
        } catch (e: Exception) {
            "Hijri estimate"
        }
    }

    companion object {
        const val WIDGET = "prayer"
    }
}
