package com.islamicapp.noor.Storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_prefs")

data class PrefsState(
    val blockedApps: List<String> = emptyList(),
    val requirementMinutes: Int = 30,
    val continuousMode: Boolean = false,
    val listenSeconds: Long = 0L,
    val listenDay: String = "",
    val sessionActive: Boolean = false,
    val sessionStartedAt: Long = 0L,
    val ringtoneUri: String = "",
    val vibration: Boolean = true,
    val focusStartedAt: Long = 0L,
    val focusUntil: Long = 0L,
    val focusRemainingMs: Long = 0L,
    val focusRunning: Boolean = false,
    val protectionEnabled: Boolean = false,
    val prayerMethod: String = "karachi",
    val asrHanafi: Boolean = true,
    val latitude: String = "",
    val longitude: String = "",
    val raw: Map<String, String> = emptyMap()
)

object AppPreferences {

    private lateinit var appContext: Context
    private var initialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PrefsState())
    val state: StateFlow<PrefsState> = _state

    private object Keys {
        val BLOCKED = stringPreferencesKey("blocked_apps")
        val REQUIREMENT = intPreferencesKey("requirement_minutes")
        val CONTINUOUS = booleanPreferencesKey("continuous_mode")
        val LISTEN_SECONDS = longPreferencesKey("listen_seconds")
        val LISTEN_DAY = stringPreferencesKey("listen_day")
        val SESSION_ACTIVE = booleanPreferencesKey("session_active")
        val SESSION_STARTED = longPreferencesKey("session_started")
        val RINGTONE = stringPreferencesKey("ringtone_uri")
        val VIBRATE = booleanPreferencesKey("vibration")
        val FOCUS_STARTED = longPreferencesKey("focus_started")
        val FOCUS_UNTIL = longPreferencesKey("focus_until")
        val FOCUS_REMAINING = longPreferencesKey("focus_remaining")
        val FOCUS_RUNNING = booleanPreferencesKey("focus_running")
        val PROTECTION = booleanPreferencesKey("protection_enabled")
        val METHOD = stringPreferencesKey("prayer_method")
        val ASR_HANAFI = booleanPreferencesKey("asr_hanafi")
        val LAT = stringPreferencesKey("latitude")
        val LON = stringPreferencesKey("longitude")
        val RAW = stringPreferencesKey("raw_kv")
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        scope.launch {
            appContext.dataStore.data.collect { prefs -> _state.value = map(prefs) }
        }
    }

    fun current(): PrefsState = _state.value

    private fun map(prefs: Preferences): PrefsState {
        val blocked = try {
            val array = JSONArray(prefs[Keys.BLOCKED] ?: "[]")
            val list = ArrayList<String>()
            for (i in 0 until array.length()) list.add(array.getString(i))
            list
        } catch (e: Exception) {
            emptyList()
        }
        val raw = try {
            val obj = JSONObject(prefs[Keys.RAW] ?: "{}")
            val map = HashMap<String, String>()
            val iterator = obj.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                map[key] = obj.optString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
        return PrefsState(
            blockedApps = blocked,
            requirementMinutes = prefs[Keys.REQUIREMENT] ?: 30,
            continuousMode = prefs[Keys.CONTINUOUS] ?: false,
            listenSeconds = prefs[Keys.LISTEN_SECONDS] ?: 0L,
            listenDay = prefs[Keys.LISTEN_DAY] ?: "",
            sessionActive = prefs[Keys.SESSION_ACTIVE] ?: false,
            sessionStartedAt = prefs[Keys.SESSION_STARTED] ?: 0L,
            ringtoneUri = prefs[Keys.RINGTONE] ?: "",
            vibration = prefs[Keys.VIBRATE] ?: true,
            focusStartedAt = prefs[Keys.FOCUS_STARTED] ?: 0L,
            focusUntil = prefs[Keys.FOCUS_UNTIL] ?: 0L,
            focusRemainingMs = prefs[Keys.FOCUS_REMAINING] ?: 0L,
            focusRunning = prefs[Keys.FOCUS_RUNNING] ?: false,
            protectionEnabled = prefs[Keys.PROTECTION] ?: false,
            prayerMethod = prefs[Keys.METHOD] ?: "karachi",
            asrHanafi = prefs[Keys.ASR_HANAFI] ?: true,
            latitude = prefs[Keys.LAT] ?: "",
            longitude = prefs[Keys.LON] ?: "",
            raw = raw
        )
    }

    suspend fun setBlockedApps(packages: List<String>) {
        val array = JSONArray()
        packages.distinct().forEach { array.put(it) }
        appContext.dataStore.edit { it[Keys.BLOCKED] = array.toString() }
    }

    suspend fun setRequirement(minutes: Int, continuous: Boolean) {
        appContext.dataStore.edit {
            it[Keys.REQUIREMENT] = minutes.coerceIn(1, 600)
            it[Keys.CONTINUOUS] = continuous
        }
    }

    suspend fun setListening(seconds: Long, day: String) {
        appContext.dataStore.edit {
            it[Keys.LISTEN_SECONDS] = seconds
            it[Keys.LISTEN_DAY] = day
        }
    }

    suspend fun setSession(active: Boolean, startedAt: Long) {
        appContext.dataStore.edit {
            it[Keys.SESSION_ACTIVE] = active
            it[Keys.SESSION_STARTED] = startedAt
        }
    }

    suspend fun setRingtone(uri: String) {
        appContext.dataStore.edit { it[Keys.RINGTONE] = uri }
    }

    suspend fun setVibration(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.VIBRATE] = enabled }
    }

    suspend fun setFocus(startedAt: Long, until: Long, remainingMs: Long, running: Boolean) {
        appContext.dataStore.edit {
            it[Keys.FOCUS_STARTED] = startedAt
            it[Keys.FOCUS_UNTIL] = until
            it[Keys.FOCUS_REMAINING] = remainingMs
            it[Keys.FOCUS_RUNNING] = running
        }
    }

    suspend fun setProtection(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.PROTECTION] = enabled }
    }

    suspend fun setPrayerLocation(method: String, asrHanafi: Boolean, lat: String, lon: String) {
        appContext.dataStore.edit {
            it[Keys.METHOD] = method
            it[Keys.ASR_HANAFI] = asrHanafi
            it[Keys.LAT] = lat
            it[Keys.LON] = lon
        }
    }

    suspend fun putRaw(key: String, value: String) {
        val map = HashMap(current().raw)
        map[key] = value
        val obj = JSONObject()
        map.forEach { entry -> obj.put(entry.key, entry.value) }
        appContext.dataStore.edit { it[Keys.RAW] = obj.toString() }
    }

    fun getRaw(key: String): String? = current().raw[key]

    suspend fun removeRaw(key: String) {
        val map = HashMap(current().raw)
        map.remove(key)
        val obj = JSONObject()
        map.forEach { entry -> obj.put(entry.key, entry.value) }
        appContext.dataStore.edit { it[Keys.RAW] = obj.toString() }
    }
}
