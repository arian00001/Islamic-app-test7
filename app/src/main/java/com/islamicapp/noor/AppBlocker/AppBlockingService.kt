package com.islamicapp.noor.AppBlocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground window changes and raises the block screen for chosen apps.
 *
 * Android does not let an ordinary app create an unbreakable lock: the user can
 * always turn this service off in Settings, and that is intentional. The block
 * screen therefore offers a documented emergency unlock instead of hiding.
 */
class AppBlockingService : AccessibilityService() {

    private var lastPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (packageName == lastPackage) return
        lastPackage = packageName

        if (!AppBlockingManager.isBlocked(packageName)) return

        AppBlockingManager.recordBlock(this, packageName)
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }
}
