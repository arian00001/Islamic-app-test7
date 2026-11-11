package com.islamicapp.noor.Focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FocusAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        FocusTimerManager.onAlarm(context)
    }
}
