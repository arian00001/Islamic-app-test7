package com.islamicapp.noor.AppBlocker

import android.app.usage.UsageStatsManager
import android.content.Context
import com.islamicapp.noor.Utils.PermissionManager
import org.json.JSONArray

object AppUsageStats {

    /**
     * Returns today's foreground time per app. Requires the Usage Access
     * permission; without it Android returns an empty list and the caller is
     * told which permission is missing.
     */
    fun today(context: Context): JSONArray {
        val result = JSONArray()
        if (!PermissionManager.usageAccessGranted(context)) return result

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val midnight = now - (now % 86_400_000L)
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnight, now) ?: return result

        val merged = HashMap<String, Long>()
        for (entry in stats) {
            if (entry.totalTimeInForeground <= 0) continue
            merged[entry.packageName] = (merged[entry.packageName] ?: 0L) + entry.totalTimeInForeground
        }

        val rows = merged.entries.sortedByDescending { it.value }.take(40)
        for (row in rows) {
            val json = org.json.JSONObject()
            json.put("packageName", row.key)
            json.put("foregroundMs", row.value)
            json.put("label", label(context, row.key))
            result.put(json)
        }
        return result
    }

    private fun label(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
