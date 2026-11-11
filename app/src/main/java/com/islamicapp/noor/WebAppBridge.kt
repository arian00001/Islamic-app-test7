package com.islamicapp.noor

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.islamicapp.noor.AppBlocker.AppBlockingManager
import com.islamicapp.noor.AppBlocker.AppUsageStats
import com.islamicapp.noor.Customization.DeviceExtras
import com.islamicapp.noor.Customization.ShortcutHelper
import com.islamicapp.noor.Customization.ThemeExchange
import com.islamicapp.noor.Customization.WallpaperHelper
import com.islamicapp.noor.Focus.FocusTimerManager
import com.islamicapp.noor.Notifications.AppNotifications
import com.islamicapp.noor.Prayer.AdhanAlarmManager
import com.islamicapp.noor.Prayer.PrayerTimeManager
import com.islamicapp.noor.Prayer.QiblaManager
import com.islamicapp.noor.Protection.AdultProtectionManager
import com.islamicapp.noor.Quran.ListeningTracker
import com.islamicapp.noor.Quran.QuranPlaybackManager
import com.islamicapp.noor.Storage.AppPreferences
import com.islamicapp.noor.Utils.DeviceManager
import com.islamicapp.noor.Utils.PermissionManager
import com.islamicapp.noor.Widgets.WidgetPrefs
import com.islamicapp.noor.Widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * window.AndroidBridge.call(method, paramsJson, requestId)
 *
 * Every method answers through window.NoorNative._onBridge(jsonString) with the
 * BridgeResponse envelope. Native state changes arrive on window.NoorNative._onEvent.
 */
class WebAppBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JavascriptInterface
    fun call(method: String, paramsJson: String, requestId: String) {
        val params = try {
            if (paramsJson.isBlank()) JSONObject() else JSONObject(paramsJson)
        } catch (e: Exception) {
            reply(BridgeResponse.error(requestId, "Invalid JSON parameters"))
            return
        }
        scope.launch {
            try {
                val data = route(method, params)
                reply(BridgeResponse.ok(requestId, data))
            } catch (e: PermissionRequired) {
                reply(BridgeResponse.error(requestId, e.message ?: "Permission required", e.kind))
            } catch (e: Exception) {
                reply(BridgeResponse.error(requestId, e.message ?: "Bridge call failed"))
            }
        }
    }

    @JavascriptInterface
    fun version(): String = DeviceManager.appVersion(activity)

    private suspend fun route(method: String, p: JSONObject): Any? {
        return when (method) {
            // ---------- DEVICE ----------
            "getDeviceInfo" -> DeviceManager.deviceInfo(activity)
            "getAndroidVersion" -> DeviceManager.deviceInfo(activity).getString("androidVersion")
            "getAppList" -> DeviceManager.installedApps(activity)
            "getPermissionsStatus" -> PermissionManager.status(activity)
            "getAppVersion" -> DeviceManager.appVersion(activity)

            // ---------- APP LOCK ----------
            "startAppBlocking" -> AppBlockingManager.startSession(
                activity,
                requirementMinutes = p.optInt("requirementMinutes", 30),
                continuous = p.optBoolean("continuous", false)
            )
            "stopAppBlocking" -> AppBlockingManager.stopSession(activity, p.optBoolean("emergency", false))
            "getBlockingStatus" -> AppBlockingManager.snapshot(activity)
            "getBlockedApps" -> AppBlockingManager.blockedApps(activity)
            "selectBlockedApps" -> {
                val array = p.optJSONArray("packages")
                val list = ArrayList<String>()
                if (array != null) {
                    for (i in 0 until array.length()) list.add(array.getString(i))
                }
                AppBlockingManager.setBlockedApps(activity, list)
                JSONObject().put("count", list.size)
            }
            "getUsageStats" -> AppUsageStats.today(activity)

            // ---------- QURAN AUDIO ----------
            "startQuranPlayback" -> QuranPlaybackManager.play(
                activity,
                url = p.getString("url"),
                surah = p.optInt("surah", 1),
                ayah = p.optInt("ayah", 1),
                title = p.optString("title", ""),
                reciter = p.optString("reciter", "")
            )
            "pauseQuranPlayback" -> {
                QuranPlaybackManager.pause()
                QuranPlaybackManager.status()
            }
            "resumeQuranPlayback" -> {
                QuranPlaybackManager.resume()
                QuranPlaybackManager.status()
            }
            "stopQuranPlayback" -> {
                QuranPlaybackManager.stop()
                QuranPlaybackManager.status()
            }
            "seekQuranPlayback" -> {
                QuranPlaybackManager.seekTo(p.optLong("positionMs", 0L))
                QuranPlaybackManager.status()
            }
            "setPlaybackSpeed" -> {
                QuranPlaybackManager.setSpeed(p.optDouble("speed", 1.0).toFloat())
                QuranPlaybackManager.status()
            }
            "getPlaybackStatus" -> QuranPlaybackManager.status()
            "getListeningTime" -> ListeningTracker.snapshot(activity)
            "setListeningRequirement" -> ListeningTracker.setRequirement(
                activity,
                p.optInt("requirementMinutes", 30),
                p.optBoolean("continuous", false)
            )

            // ---------- FOCUS ----------
            "startFocusTimer" -> FocusTimerManager.start(activity, p.optInt("minutes", 25))
            "pauseFocusTimer" -> FocusTimerManager.pause(activity)
            "resumeFocusTimer" -> FocusTimerManager.resume(activity)
            "getFocusTimerStatus" -> FocusTimerManager.status(activity)
            "getFocusHistory" -> FocusTimerManager.history(activity)

            // ---------- PRAYER ----------
            "getPrayerTimes" -> PrayerTimeManager.timesJson(
                latitude = p.optDouble("latitude", 23.8103),
                longitude = p.optDouble("longitude", 90.4125),
                timeZoneId = p.optString("timeZone", java.util.TimeZone.getDefault().id),
                method = p.optString("method", "karachi"),
                asrHanafi = p.optBoolean("asrHanafi", true)
            )
            "scheduleAdhanAlarm" -> AdhanAlarmManager.schedule(
                activity,
                prayer = p.optString("prayer", "fajr"),
                triggerAtMillis = p.optLong("triggerAtMillis", 0L),
                soundUri = p.optString("soundUri", ""),
                vibrate = p.optBoolean("vibrate", true)
            )
            "cancelAdhanAlarm" -> AdhanAlarmManager.cancel(activity, p.optString("prayer", "fajr"))
            "getQiblaDirection" -> QiblaManager.bearingToKaaba(
                p.optDouble("latitude", 23.8103),
                p.optDouble("longitude", 90.4125)
            )
            "startCompass" -> QiblaManager.startCompass(activity)
            "stopCompass" -> {
                QiblaManager.stopCompass()
                JSONObject().put("stopped", true)
            }

            // ---------- SOUND ----------
            "setRingtone" -> {
                AppPreferences.setRingtone(p.optString("soundUri", ""))
                JSONObject().put("saved", true)
            }
            "previewSound" -> JSONObject().put("played", AppNotifications.preview(activity, p.optString("soundUri", "")))
            "setVibration" -> {
                AppPreferences.setVibration(p.optBoolean("enabled", true))
                JSONObject().put("saved", true)
            }
            "showNotification" -> {
                AppNotifications.show(
                    activity,
                    title = p.optString("title", "Iman Sangi"),
                    body = p.optString("body", ""),
                    channelId = p.optString("channelId", AppNotifications.CHANNEL_GENERAL)
                )
                JSONObject().put("shown", true)
            }

            // ---------- PROTECTION ----------
            "enableAdultProtection" -> AdultProtectionManager.enable(activity)
            "disableAdultProtection" -> AdultProtectionManager.disable(activity)
            "getProtectionStatus" -> AdultProtectionManager.status(activity)
            "openPrivateDnsSettings" -> {
                PermissionManager.openSettings(activity, "privateDns")
                PermissionManager.status(activity)
            }

            // ---------- STORAGE ----------
            "saveLocalData" -> {
                AppPreferences.putRaw(p.getString("key"), p.optString("value", ""))
                JSONObject().put("saved", true)
            }
            "getLocalData" -> AppPreferences.getRaw(p.getString("key"))
            "deleteLocalData" -> {
                AppPreferences.removeRaw(p.getString("key"))
                JSONObject().put("saved", true)
            }

            // ---------- CUSTOMIZATION STUDIO ----------
            "requestWallpaperPermission" -> WallpaperHelper.info()
            "setWallpaper" -> WallpaperHelper.apply(activity, p)
            "createAppShortcut" -> ShortcutHelper.pin(activity, p)
            "addHomeScreenWidget" -> WidgetUpdater.requestPin(activity, p.optString("widget", "quran_ayah"))
            "updateWidgetConfig" -> {
                val name = p.optString("widget", "quran_ayah")
                WidgetPrefs.save(activity, name, p.optJSONObject("config") ?: JSONObject())
                WidgetUpdater.notifyConfigChanged(activity, name)
                JSONObject().put("saved", true).put("instances", WidgetUpdater.count(activity, name))
            }
            "getWidgetStatus" -> {
                val name = p.optString("widget", "quran_ayah")
                JSONObject().put("widget", name).put("instances", WidgetUpdater.count(activity, name))
            }
            "refreshWidgets" -> {
                WidgetUpdater.refreshAll(activity)
                JSONObject().put("refreshed", true)
            }
            "getCurrentLocation" -> DeviceExtras.location(activity)
            "requestLocationPermission" -> {
                activity.requestPermission("location")
                PermissionManager.status(activity).put("requested", "location")
            }
            "vibrateDevice" -> DeviceExtras.vibrate(activity, p.optLong("ms", 30L))
            "schedulePrayerNotification" -> AdhanAlarmManager.schedule(
                activity,
                prayer = p.optString("prayer", "fajr"),
                triggerAtMillis = p.optLong("triggerAtMillis", 0L),
                soundUri = p.optString("soundUri", ""),
                vibrate = p.optBoolean("vibrate", true)
            )
            "cancelPrayerNotification" -> AdhanAlarmManager.cancel(activity, p.optString("prayer", "fajr"))
            "exportTheme" -> ThemeExchange.export(activity, p.optJSONObject("payload") ?: JSONObject())
            "importTheme" -> ThemeExchange.importTheme(p.optString("json", ""))

            // ---------- SYSTEM ----------
            "openAndroidSettings" -> PermissionManager.openSettings(activity, p.optString("target", "app"))
            "requestPermission" -> {
                val kind = p.optString("permission", "notifications")
                activity.requestPermission(kind)
                PermissionManager.status(activity).put("requested", kind)
            }
            "openSystemSettings" -> PermissionManager.openSettings(activity, p.optString("target", "app"))
            "shareContent" -> DeviceManager.share(activity, p.optString("text", ""), p.optString("url", ""))

            else -> throw IllegalArgumentException("Unknown bridge method: " + method)
        }
    }

    fun onPageReady() {
        emitEvent("ready", JSONObject().put("version", DeviceManager.appVersion(activity)))
    }

    fun emitEvent(type: String, payload: Any?) {
        val envelope = JSONObject()
        envelope.put("type", type)
        envelope.put("payload", BridgeResponse.ok("event", payload).opt("data"))
        evaluate("window.NoorNative && window.NoorNative._onEvent(" + JSONObject.quote(envelope.toString()) + ");")
    }

    private fun reply(json: JSONObject) {
        evaluate(
            "window.NoorNative && window.NoorNative._onBridge(" +
                JSONObject.quote(json.toString()) + ");"
        )
    }

    private fun evaluate(script: String) {
        activity.runOnUiThread {
            try {
                webView.evaluateJavascript(script, null)
            } catch (ignored: Exception) {
                // WebView may be destroyed while a call is in flight.
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    class PermissionRequired(val kind: String, message: String) : Exception(message)
}
