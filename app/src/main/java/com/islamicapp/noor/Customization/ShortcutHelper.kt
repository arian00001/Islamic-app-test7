package com.islamicapp.noor.Customization

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Base64
import org.json.JSONObject

/**
 * Android does not allow one app to replace another app's launcher icon in place.
 * The supported mechanism is a pinned shortcut: the user confirms once and the chosen
 * icon appears on the home screen. Everything else is honestly reported as unsupported.
 */
object ShortcutHelper {

    private const val MAX_ICON_BYTES = 1024 * 1024

    fun pin(context: Context, params: JSONObject): JSONObject {
        val result = JSONObject()
        val packageName = params.optString("packageName", "")
        require(packageName.isNotBlank()) { "packageName is required" }

        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return result.put("pinned", false)
                .put("reason", "That app is not installed or exposes no launch intent.")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return result.put("pinned", false).put("reason", "Pinned shortcuts need Android 8.0 or newer.")
        }

        val manager = context.getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            return result.put("pinned", false)
                .put("reason", "This launcher does not support pinned shortcuts.")
        }

        val label = params.optString("label", packageName).take(40)
        val builder = ShortcutInfo.Builder(context, "noor_" + packageName.hashCode().toString())
            .setShortLabel(label.take(20))
            .setLongLabel(label)
            .setIntent(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val bitmap = decodeIcon(params.optString("iconBase64", ""))
        if (bitmap != null) builder.setIcon(Icon.createWithBitmap(bitmap))

        val accepted = manager.requestPinShortcut(builder.build(), null)
        result.put("pinned", accepted)
        result.put("reason", if (accepted) "" else "The launcher refused the pin request.")
        return result
    }

    private fun decodeIcon(payload: String): Bitmap? {
        if (payload.isBlank()) return null
        return try {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            if (bytes.size > MAX_ICON_BYTES) return null
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            Bitmap.createScaledBitmap(source, 192, 192, true)
        } catch (e: Exception) {
            null
        }
    }
}
