package com.islamicapp.noor.AppBlocker

import android.content.Context
import com.islamicapp.noor.EventBus
import com.islamicapp.noor.Notifications.AppNotifications
import com.islamicapp.noor.Quran.ListeningTracker
import com.islamicapp.noor.Storage.AppDatabase
import com.islamicapp.noor.Storage.AppPreferences
import com.islamicapp.noor.Storage.BlockEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds the blocked-app set and the active goal in memory so the accessibility
 * service can answer isBlocked() cheaply for every window change.
 */
object AppBlockingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var blocked: Set<String> = emptySet()
    private var active = false
    private var requirementMinutes = 30
    private var continuous = false

    fun init(context: Context, onState: (JSONObject) -> Unit) {
        appContext = context.applicationContext
        scope.launch {
            AppPreferences.state.collect { prefs ->
                blocked = prefs.blockedApps.toSet()
                active = prefs.sessionActive
                requirementMinutes = prefs.requirementMinutes
                continuous = prefs.continuousMode
                onState(snapshot(context))
            }
        }
    }

    fun isBlocked(packageName: String): Boolean {
        return active && blocked.contains(packageName)
    }

    fun startSession(context: Context, requirementMinutes: Int, continuous: Boolean): JSONObject {
        val minutes = requirementMinutes.coerceIn(1, 600)
        this.requirementMinutes = minutes
        this.continuous = continuous
        active = true
        ListeningTracker.setRequirement(context, minutes, continuous)
        ListeningTracker.resetGoal(context)
        scope.launch {
            AppPreferences.setRequirement(minutes, continuous)
            AppPreferences.setSession(true, System.currentTimeMillis())
        }
        AppNotifications.blockingStarted(context, minutes)
        val payload = snapshot(context)
        EventBus.emitJson("blocking", payload)
        return payload
    }

    fun stopSession(context: Context, emergency: Boolean): JSONObject {
        active = false
        scope.launch { AppPreferences.setSession(false, 0L) }
        if (emergency) {
            AppNotifications.show(
                context,
                "Blocking paused",
                "You used the emergency unlock. The goal is still open."
            )
        }
        val payload = snapshot(context)
        EventBus.emitJson("blocking", payload)
        return payload
    }

    fun completeRequirement(context: Context) {
        active = false
        scope.launch {
            AppPreferences.setSession(false, 0L)
            AppDatabase.get(context).blockDao().insert(
                BlockEvent(
                    packageName = "goal_complete",
                    blockedAt = System.currentTimeMillis(),
                    released = true
                )
            )
        }
        AppNotifications.show(
            context,
            "Goal complete",
            "Your listening goal is finished. Blocked apps are unlocked."
        )
        EventBus.emitJson("blocking", snapshot(context))
    }

    fun setBlockedApps(context: Context, packages: List<String>) {
        blocked = packages.toSet()
        scope.launch { AppPreferences.setBlockedApps(packages) }
    }

    fun blockedApps(context: Context): JSONArray {
        val array = JSONArray()
        blocked.sorted().forEach { array.put(it) }
        return array
    }

    fun recordBlock(context: Context, packageName: String) {
        scope.launch {
            AppDatabase.get(context).blockDao().insert(
                BlockEvent(
                    packageName = packageName,
                    blockedAt = System.currentTimeMillis(),
                    released = false
                )
            )
        }
    }

    fun restoreAfterReboot(context: Context) {
        scope.launch { AppPreferences.setSession(active, System.currentTimeMillis()) }
    }

    fun snapshot(context: Context): JSONObject {
        val listening = ListeningTracker.snapshot(context)
        val json = JSONObject()
        json.put("active", active)
        json.put("blockedApps", blocked.size)
        json.put("requirementMinutes", requirementMinutes)
        json.put("continuous", continuous)
        json.put("remainingSeconds", listening.optLong("remainingSeconds", 0L))
        json.put("listenedSeconds", listening.optLong("listenedSeconds", 0L))
        json.put("goalSeconds", listening.optLong("goalSeconds", 0L))
        return json
    }
}
