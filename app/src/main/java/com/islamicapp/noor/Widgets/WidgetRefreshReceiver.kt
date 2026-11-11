package com.islamicapp.noor.Widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Rotation tick: repaints the widgets and keeps the next tick scheduled. */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        WidgetUpdater.refreshAll(context)
        WidgetUpdater.ensureScheduled(context)
    }
}
