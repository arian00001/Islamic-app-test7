package com.islamicapp.noor.Quran

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.islamicapp.noor.AppBlocker.AppBlockingManager
import com.islamicapp.noor.EventBus
import com.islamicapp.noor.Notifications.AppNotifications
import com.islamicapp.noor.Storage.AppDatabase
import com.islamicapp.noor.Storage.AppPreferences
import com.islamicapp.noor.Storage.ListeningDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The trusted counter. It samples the real player position once per second and
 * only adds time while the player is actually playing, so a JavaScript message
 * can never fake completion.
 */
object ListeningTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var appContext: Context? = null
    private var player: ExoPlayer? = null
    private var ticker: Job? = null

    private var lastPosition = 0L
    private var lastPlayingAt = 0L
    private var lastPersistAt = 0L
    private var pendingMs = 0L

    private var listenedSeconds = 0L
    private var goalSeconds = 30L * 60L
    private var continuous = false
    private var sessionActive = false
    private var day = today()
    private var completionHandled = false
    private var hydrated = false

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            AppPreferences.state.collect { prefs ->
                if (!hydrated) {
                    hydrated = true
                    goalSeconds = prefs.requirementMinutes.toLong() * 60L
                    continuous = prefs.continuousMode
                    sessionActive = prefs.sessionActive
                    if (prefs.listenDay == today()) listenedSeconds = prefs.listenSeconds
                } else {
                    goalSeconds = prefs.requirementMinutes.toLong() * 60L
                    continuous = prefs.continuousMode
                    sessionActive = prefs.sessionActive
                }
            }
        }
    }

    fun attach(context: Context, exoPlayer: ExoPlayer) {
        player = exoPlayer
        lastPosition = exoPlayer.currentPosition
        startTicker(context)
    }

    fun detach() {
        ticker?.cancel()
        ticker = null
        player = null
    }

    private fun startTicker(context: Context) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                sample(context)
            }
        }
    }

    private fun sample(context: Context) {
        val exo = player ?: return
        rollDayIfNeeded(context)

        val playing = exo.isPlaying && exo.playbackState == Player.STATE_READY
        val position = exo.currentPosition
        val now = System.currentTimeMillis()

        if (playing) {
            if (lastPosition > 0 && position > lastPosition) {
                val delta = position - lastPosition
                // Ignore seek jumps: only real second-to-second progress counts.
                if (delta in 1..2500) accumulate(delta, context)
            }
            lastPlayingAt = now
        } else if (continuous && sessionActive && now - lastPlayingAt > 30_000L && listenedSeconds > 0) {
            // Continuous mode: a pause longer than 30 seconds restarts the session.
            listenedSeconds = 0
            save(context, force = true)
        }

        lastPosition = position
        publish(context)

        if (now - lastPersistAt > 5_000L) save(context, force = false)
    }

    private fun accumulate(deltaMs: Long, context: Context) {
        pendingMs += deltaMs
        if (pendingMs < 1000L) return
        val whole = pendingMs / 1000L
        pendingMs -= whole * 1000L
        listenedSeconds += whole
        checkGoal(context)
    }

    private fun checkGoal(context: Context) {
        if (completionHandled) return
        if (!sessionActive) return
        if (listenedSeconds < goalSeconds) return

        completionHandled = true
        sessionActive = false
        AppNotifications.listeningComplete(context)
        AppBlockingManager.completeRequirement(context)
        scope.launch {
            AppPreferences.setSession(false, 0L)
        }
        EventBus.emitJson("listeningComplete", snapshotJson())
    }

    private fun rollDayIfNeeded(context: Context) {
        val current = today()
        if (current == day) return
        day = current
        // A new day keeps today's progress but never carries a finished goal forward.
        if (!sessionActive) {
            listenedSeconds = 0
            completionHandled = false
        }
        save(context, force = true)
    }

    private fun save(context: Context, force: Boolean) {
        if (!force && System.currentTimeMillis() - lastPersistAt < 4_000L) return
        lastPersistAt = System.currentTimeMillis()
        val seconds = listenedSeconds
        val currentDay = day
        scope.launch {
            AppPreferences.setListening(seconds, currentDay)
            AppDatabase.get(context).listeningDao().upsert(
                ListeningDay(currentDay, seconds, System.currentTimeMillis())
            )
        }
    }

    private fun publish(context: Context) {
        EventBus.emitJson("listeningTick", snapshotJson())
    }

    fun beginSession(context: Context) {
        if (!sessionActive) {
            sessionActive = true
            completionHandled = listenedSeconds >= goalSeconds
            scope.launch { AppPreferences.setSession(true, System.currentTimeMillis()) }
        }
        if (listenedSeconds >= goalSeconds) {
            completionHandled = true
        }
    }

    fun resetGoal(context: Context) {
        listenedSeconds = 0
        completionHandled = false
        pendingMs = 0
        save(context, force = true)
    }

    fun setRequirement(context: Context, minutes: Int, isContinuous: Boolean) {
        goalSeconds = minutes.coerceIn(1, 600).toLong() * 60L
        continuous = isContinuous
        scope.launch { AppPreferences.setRequirement(minutes, isContinuous) }
    }

    fun snapshot(context: Context): JSONObject = snapshotJson()

    private fun snapshotJson(): JSONObject {
        val json = JSONObject()
        json.put("listenedSeconds", listenedSeconds)
        json.put("goalSeconds", goalSeconds)
        json.put("remainingSeconds", (goalSeconds - listenedSeconds).coerceAtLeast(0L))
        json.put("percent", if (goalSeconds > 0) (listenedSeconds * 100 / goalSeconds) else 0)
        json.put("sessionActive", sessionActive)
        json.put("continuous", continuous)
        json.put("day", day)
        json.put("complete", listenedSeconds >= goalSeconds)
        return json
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
