package com.islamicapp.noor

import android.app.Application
import android.content.Context
import com.islamicapp.noor.AppBlocker.AppBlockingManager
import com.islamicapp.noor.Focus.FocusTimerManager
import com.islamicapp.noor.Notifications.AppNotifications
import com.islamicapp.noor.Prayer.AdhanAlarmManager
import com.islamicapp.noor.Protection.AdultProtectionManager
import com.islamicapp.noor.Quran.ListeningTracker
import com.islamicapp.noor.Quran.QuranPlaybackManager
import com.islamicapp.noor.Storage.AppPreferences

class NoorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        init(this)
    }

    companion object {
        @Volatile
        private var initialized = false

        fun init(context: Context) {
            if (initialized) return
            initialized = true
            val app = context.applicationContext
            AppPreferences.init(app)
            AppNotifications.createChannels(app)
            QuranPlaybackManager.init(app)
            ListeningTracker.init(app)
            AppBlockingManager.init(app) { state ->
                EventBus.emit("blocking", state)
            }
            FocusTimerManager.init(app)
            AdultProtectionManager.init(app)
            AdhanAlarmManager.init(app)
        }
    }
}
