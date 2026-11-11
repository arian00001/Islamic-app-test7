package com.islamicapp.noor.Utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import com.islamicapp.noor.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

object DeviceManager {

    fun deviceInfo(context: Context): JSONObject {
        val info = JSONObject()
        info.put("manufacturer", Build.MANUFACTURER)
        info.put("model", Build.MODEL)
        info.put("device", Build.DEVICE)
        info.put("sdkInt", Build.VERSION.SDK_INT)
        info.put("androidVersion", Build.VERSION.RELEASE)
        info.put("packageName", context.packageName)
        info.put("appVersion", appVersion(context))
        info.put("timezone", TimeZone.getDefault().id)
        info.put("webAppUrl", BuildConfig.WEB_APP_URL)
        info.put("debug", BuildConfig.DEBUG)
        return info
    }

    fun appVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun installedApps(context: Context): JSONArray {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(mainIntent, 0)
        val seen = HashSet<String>()
        val result = JSONArray()
        for (entry in resolved) {
            val pkg = entry.activityInfo.packageName
            if (pkg == context.packageName || pkg == "com.android.settings") continue
            if (!seen.add(pkg)) continue
            val row = JSONObject()
            row.put("packageName", pkg)
            row.put("label", entry.loadLabel(pm).toString())
            row.put(
                "system",
                (entry.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
            result.put(row)
        }
        return result
    }

    fun share(context: Context, text: String, url: String) {
        val body = if (url.isBlank()) text else text + " " + url
        if (body.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
