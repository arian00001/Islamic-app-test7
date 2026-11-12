package com.islamicapp.noor.Customization

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Backs the launcher home screen: installed apps with icons, launching them, and
 * the system request that makes Noor the default home app.
 */
object LauncherManager {

    private const val ICON_PX = 48

    fun status(context: Context): JSONObject {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val current = resolved?.activityInfo?.packageName.orEmpty()
        return JSONObject()
            .put("isDefault", current == context.packageName)
            .put("currentHome", current)
            .put("packageName", context.packageName)
            .put("canRequest", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    fun apps(context: Context): JSONArray {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val result = JSONArray()
        for (entry in resolved) {
            val info = entry.activityInfo ?: continue
            val pkg = info.packageName
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val row = JSONObject()
            row.put("packageName", pkg)
            row.put("label", entry.loadLabel(pm).toString())
            row.put("component", info.name)
            row.put(
                "system",
                (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
            row.put("icon", icon(context, entry.loadIcon(pm)))
            result.put(row)
        }
        return result
    }

    fun launch(context: Context, packageName: String): JSONObject {
        if (packageName.isBlank()) throw IllegalArgumentException("packageName is required")
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return JSONObject().put("launched", false).put("packageName", packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return JSONObject().put("launched", true).put("packageName", packageName)
    }

    fun requestDefault(context: Context): JSONObject {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                context.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return status(context).put("requested", true)
            }
        }
        context.startActivity(
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return status(context).put("requested", true)
    }

    fun openHome(context: Context): JSONObject {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return JSONObject().put("opened", true)
    }

    private fun icon(context: Context, drawable: Drawable): String {
        return try {
            val size = (ICON_PX * context.resources.displayMetrics.density).toInt()
                .coerceIn(ICON_PX, ICON_PX * 3)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            bitmap.recycle()
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }
}
