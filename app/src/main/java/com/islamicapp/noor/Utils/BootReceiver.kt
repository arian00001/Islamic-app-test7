package com.islamicapp.noor.Utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.islamicapp.noor.AppBlocker.AppBlockingManager
import com.islamicapp.noor.Focus.FocusTimerManager
import com.islamicapp.noor.NoorApp
import com.islamicapp.noor.Prayer.AdhanAlarmManager

/**
 * Restores what Android allows after a reboot: saved sessions and schedules.
 * The accessibility service itself must be re-enabled by the system, and the
 * VPN needs a fresh user consent, so both are re-checked by the web layer.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NoorApp.init(context)
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppBlockingManager.restoreAfterReboot(context)
        FocusTimerManager.restoreAfterReboot(context)
        AdhanAlarmManager.rescheduleAll(context)
    }
}
