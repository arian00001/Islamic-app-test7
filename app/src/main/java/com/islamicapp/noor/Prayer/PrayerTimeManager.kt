package com.islamicapp.noor.Prayer

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Local prayer time calculation (PrayTimes solar equations, no network needed).
 * Every instant is returned in epoch milliseconds for the requested time zone.
 */
object PrayerTimeManager {

    private val ANGLES = mapOf(
        "mwl" to doubleArrayOf(18.0, 17.0),
        "isna" to doubleArrayOf(15.0, 15.0),
        "egypt" to doubleArrayOf(19.5, 17.5),
        "karachi" to doubleArrayOf(18.0, 18.0),
        "makkah" to doubleArrayOf(18.5, 18.5),
        "tehran" to doubleArrayOf(17.7, 14.0),
        "jafari" to doubleArrayOf(16.0, 14.0)
    )

    private val KEYS = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")

    fun timesJson(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        method: String,
        asrHanafi: Boolean
    ): JSONObject {
        val zone = try {
            TimeZone.getTimeZone(timeZoneId)
        } catch (e: Exception) {
            TimeZone.getDefault()
        }
        val calendar = Calendar.getInstance(zone)
        val computed = compute(calendar, latitude, longitude, zone, method, asrHanafi)

        val times = JSONObject()
        KEYS.forEachIndexed { index, key -> times.put(key, computed[index]) }

        val json = JSONObject()
        json.put("latitude", latitude)
        json.put("longitude", longitude)
        json.put("timeZone", zone.id)
        json.put("method", method)
        json.put("asrHanafi", asrHanafi)
        json.put(
            "date",
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = zone }
                .format(Date(calendar.timeInMillis))
        )
        json.put("times", times)
        json.put("valid", computed.all { it > 0 })

        val now = System.currentTimeMillis()
        var nextKey = "fajr"
        var nextAt = computed[0]
        for (index in KEYS.indices) {
            if (computed[index] > now) {
                nextKey = KEYS[index]
                nextAt = computed[index]
                break
            }
        }
        val next = JSONObject()
        next.put("key", nextKey)
        next.put("at", nextAt)
        json.put("next", next)
        return json
    }

    fun compute(
        calendar: Calendar,
        latitude: Double,
        longitude: Double,
        zone: TimeZone,
        method: String,
        asrHanafi: Boolean
    ): LongArray {
        val angles = ANGLES[method] ?: ANGLES.getValue("karachi")
        val fajrAngle = angles[0]
        val ishaAngle = angles[1]

        val startOfDay = startOfDay(calendar, zone)
        val zoneOffsetHours = zone.getOffset(startOfDay) / 3_600_000.0
        val dayIndex = Math.floorDiv(startOfDay, 86_400_000L).toDouble() - 10_957.5

        val g = Math.toRadians(357.529 + 0.98560028 * dayIndex)
        val q = 280.459 + 0.98564736 * dayIndex
        val l = Math.toRadians(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = Math.toRadians(23.439 - 0.00000036 * dayIndex)

        val declination = Math.toDegrees(asin(sin(e) * sin(l)))
        var rightAscension = Math.toDegrees(atan2(cos(e) * sin(l), cos(l))) / 15.0
        rightAscension = normalizeHours(rightAscension)
        var equationOfTime = q / 15.0 - rightAscension
        if (equationOfTime > 12) equationOfTime -= 24
        if (equationOfTime < -12) equationOfTime += 24

        val dhuhr = 12.0 + zoneOffsetHours - longitude / 15.0 - equationOfTime
        val latRad = Math.toRadians(latitude)
        val declRad = Math.toRadians(declination)

        val fajr = dhuhr - hourAngle(fajrAngle, latRad, declRad)
        val sunrise = dhuhr - hourAngle(0.833, latRad, declRad)
        val sunset = dhuhr + hourAngle(0.833, latRad, declRad)
        val isha = dhuhr + hourAngle(ishaAngle, latRad, declRad)

        val asrFactor = if (asrHanafi) 2.0 else 1.0
        val asrAngle = Math.toDegrees(
            atan(1.0 / (asrFactor + tan(abs(latRad - declRad))))
        )
        val asr = dhuhr + hourAngle(asrAngle, latRad, declRad)

        return longArrayOf(
            toMillis(startOfDay, fajr),
            toMillis(startOfDay, sunrise),
            toMillis(startOfDay, dhuhr),
            toMillis(startOfDay, asr),
            toMillis(startOfDay, sunset),
            toMillis(startOfDay, isha)
        )
    }

    private fun hourAngle(angleDegrees: Double, latRad: Double, declRad: Double): Double {
        val cosH = (sin(Math.toRadians(-angleDegrees)) - sin(latRad) * sin(declRad)) /
            (cos(latRad) * cos(declRad))
        if (cosH > 1.0 || cosH < -1.0) return Double.NaN
        return Math.toDegrees(acos(cosH)) / 15.0
    }

    private fun toMillis(startOfDay: Long, hours: Double): Long {
        if (hours.isNaN()) return -1L
        return startOfDay + (hours * 3_600_000.0).toLong()
    }

    private fun normalizeHours(value: Double): Double {
        var hours = value % 24.0
        if (hours < 0) hours += 24.0
        return hours
    }

    private fun startOfDay(calendar: Calendar, zone: TimeZone): Long {
        val copy = Calendar.getInstance(zone)
        copy.timeInMillis = calendar.timeInMillis
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }
}
