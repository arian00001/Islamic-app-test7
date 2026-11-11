package com.islamicapp.noor.Quran

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the single ExoPlayer instance and the MediaSession, so playback keeps
 * running with a media notification, lock screen controls and audio focus.
 */
class QuranPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        val attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                publish()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                publish()
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
        ListeningTracker.attach(this, player)
        publish()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMediaId(intent.getIntExtra(EXTRA_SURAH, 1).toString() + ":" +
                        intent.getIntExtra(EXTRA_AYAH, 1).toString())
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                publish()
            }
            ACTION_PAUSE -> player.pause()
            ACTION_RESUME -> player.play()
            ACTION_STOP -> {
                player.stop()
                publish()
            }
            ACTION_SEEK -> player.seekTo(intent.getLongExtra(EXTRA_POSITION, 0L))
            ACTION_SPEED -> player.setPlaybackSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
            ACTION_REPEAT -> player.repeatMode = intent.getIntExtra(EXTRA_REPEAT, Player.REPEAT_MODE_OFF)
        }
        return START_STICKY
    }

    private fun publish() {
        QuranPlaybackManager.updateState(
            PlaybackState(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition,
                durationMs = if (player.duration > 0) player.duration else 0L,
                speed = player.playbackSpeed,
                repeatMode = player.repeatMode
            )
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        ListeningTracker.detach()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.islamicapp.noor.quran.PLAY"
        const val ACTION_PAUSE = "com.islamicapp.noor.quran.PAUSE"
        const val ACTION_RESUME = "com.islamicapp.noor.quran.RESUME"
        const val ACTION_STOP = "com.islamicapp.noor.quran.STOP"
        const val ACTION_SEEK = "com.islamicapp.noor.quran.SEEK"
        const val ACTION_SPEED = "com.islamicapp.noor.quran.SPEED"
        const val ACTION_REPEAT = "com.islamicapp.noor.quran.REPEAT"

        const val EXTRA_URL = "url"
        const val EXTRA_SURAH = "surah"
        const val EXTRA_AYAH = "ayah"
        const val EXTRA_POSITION = "position"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_REPEAT = "repeat"
    }
}
