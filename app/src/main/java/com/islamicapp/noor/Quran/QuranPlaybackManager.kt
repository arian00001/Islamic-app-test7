package com.islamicapp.noor.Quran

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.islamicapp.noor.EventBus
import org.json.JSONObject

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val repeatMode: Int = 0
)

/**
 * Commands go to the service as intents; state flows back through updateState().
 * The WebView can only ask, never assert, what the player is doing.
 */
object QuranPlaybackManager {

    private var appContext: Context? = null
    private var state = PlaybackState()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun updateState(next: PlaybackState) {
        state = next
        val payload = JSONObject()
        payload.put("isPlaying", next.isPlaying)
        payload.put("isBuffering", next.isBuffering)
        payload.put("positionMs", next.positionMs)
        payload.put("durationMs", next.durationMs)
        payload.put("speed", next.speed.toDouble())
        EventBus.emitJson("audioState", payload)
    }

    fun currentState(): PlaybackState = state

    fun play(context: Context, url: String, surah: Int, ayah: Int, title: String, reciter: String): JSONObject {
        val intent = Intent(context, QuranPlayerService::class.java).apply {
            action = QuranPlayerService.ACTION_PLAY
            putExtra(QuranPlayerService.EXTRA_URL, url)
            putExtra(QuranPlayerService.EXTRA_SURAH, surah)
            putExtra(QuranPlayerService.EXTRA_AYAH, ayah)
        }
        ContextCompat.startForegroundService(context, intent)
        ListeningTracker.beginSession(context)
        val result = status()
        result.put("title", title)
        result.put("reciter", reciter)
        result.put("surah", surah)
        result.put("ayah", ayah)
        return result
    }

    fun pause() = send(QuranPlayerService.ACTION_PAUSE)

    fun resume() = send(QuranPlayerService.ACTION_RESUME)

    fun stop() = send(QuranPlayerService.ACTION_STOP)

    fun seekTo(positionMs: Long) {
        appContext?.let { context ->
            val intent = Intent(context, QuranPlayerService::class.java).apply {
                action = QuranPlayerService.ACTION_SEEK
                putExtra(QuranPlayerService.EXTRA_POSITION, positionMs.coerceAtLeast(0L))
            }
            context.startService(intent)
        }
    }

    fun setSpeed(speed: Float) {
        appContext?.let { context ->
            val intent = Intent(context, QuranPlayerService::class.java).apply {
                action = QuranPlayerService.ACTION_SPEED
                putExtra(QuranPlayerService.EXTRA_SPEED, speed.coerceIn(0.5f, 2f))
            }
            context.startService(intent)
        }
    }

    fun status(): JSONObject {
        val json = JSONObject()
        json.put("isPlaying", state.isPlaying)
        json.put("isBuffering", state.isBuffering)
        json.put("positionMs", state.positionMs)
        json.put("durationMs", state.durationMs)
        json.put("speed", state.speed.toDouble())
        json.put("repeatMode", state.repeatMode)
        return json
    }

    private fun send(action: String) {
        val context = appContext ?: return
        val intent = Intent(context, QuranPlayerService::class.java).apply { this.action = action }
        try {
            context.startService(intent)
        } catch (ignored: Exception) {
            // Service not running - nothing to control.
        }
    }
}
