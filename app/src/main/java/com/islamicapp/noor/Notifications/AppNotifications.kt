package com.islamicapp.noor.Notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.islamicapp.noor.MainActivity
import com.islamicapp.noor.R

object AppNotifications {

    const val CHANNEL_GENERAL = "general"
    const val CHANNEL_ADHAN = "adhan"
    const val CHANNEL_FOCUS = "focus"
    const val CHANNEL_PROTECTION = "protection"

    private var nextId = 1000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val general = NotificationChannel(
            CHANNEL_GENERAL,
            context.getString(R.string.channel_quran),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val focus = NotificationChannel(
            CHANNEL_FOCUS,
            context.getString(R.string.channel_focus),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val protection = NotificationChannel(
            CHANNEL_PROTECTION,
            context.getString(R.string.channel_protection),
            NotificationManager.IMPORTANCE_LOW
        )
        val adhan = NotificationChannel(
            CHANNEL_ADHAN,
            context.getString(R.string.channel_adhan),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        }

        manager.createNotificationChannel(general)
        manager.createNotificationChannel(focus)
        manager.createNotificationChannel(protection)
        manager.createNotificationChannel(adhan)
    }

    fun show(context: Context, title: String, body: String, channelId: String = CHANNEL_GENERAL) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(nextId++, notification)
        } catch (ignored: SecurityException) {
            // Notification permission not granted yet.
        }
    }

    fun adhan(context: Context, prayer: String, soundUri: String, vibrate: Boolean) {
        val channelId = CHANNEL_ADHAN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val uri = if (soundUri.isBlank()) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                Uri.parse(soundUri)
            }
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(channelId, "Adhan", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(uri, attributes)
                enableVibration(vibrate)
            }
            manager?.createNotificationChannel(channel)
        }
        show(context, "It is time for " + prayer.replaceFirstChar { it.uppercase() }, "Tap to open Noor", channelId)
    }

    fun blockingStarted(context: Context, minutes: Int) {
        show(
            context,
            "Focus started",
            "Selected apps stay blocked until you listen to " + minutes + " minutes of Quran.",
            CHANNEL_FOCUS
        )
    }

    fun listeningComplete(context: Context) {
        show(context, "Listening goal complete", "Your blocked apps are unlocked.", CHANNEL_FOCUS)
    }

    fun focusStarted(context: Context, minutes: Int) {
        show(context, "Focus session started", minutes.toString() + " minutes of focus. Stay with it.", CHANNEL_FOCUS)
    }

    fun preview(context: Context, soundUri: String): Boolean {
        val uri = if (soundUri.isBlank()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            Uri.parse(soundUri)
        }
        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
            true
        } catch (e: Exception) {
            false
        }
    }
}
