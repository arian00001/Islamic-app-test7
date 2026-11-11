package com.islamicapp.noor.Customization

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.net.URL

/**
 * Sets the home and/or lock wallpaper from a PNG the web studio drew on the device.
 * Android needs the SET_WALLPAPER manifest permission only - there is no runtime prompt.
 */
object WallpaperHelper {

    private const val MAX_BYTES = 16 * 1024 * 1024

    fun info(): JSONObject {
        val json = JSONObject()
        json.put("runtimePermission", false)
        json.put("manifestPermission", "android.permission.SET_WALLPAPER")
        json.put("lockScreen", Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        json.put("note", "Granted at install; some manufacturer lock screens keep their own wallpaper.")
        return json
    }

    private fun decode(params: JSONObject): Bitmap {
        val base64 = params.optString("imageBase64", "")
        if (base64.isNotBlank()) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            require(bytes.size <= MAX_BYTES) { "Wallpaper image is too large" }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalArgumentException("Unreadable image data")
        }
        val url = params.optString("url", "")
        require(url.startsWith("https://")) { "Only https wallpaper URLs are allowed" }
        val bytes = URL(url).openStream().use { it.readBytes() }
        require(bytes.size <= MAX_BYTES) { "Wallpaper image is too large" }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Unreadable image data")
    }

    fun apply(context: Context, params: JSONObject): JSONObject {
        val manager = WallpaperManager.getInstance(context)
        val result = JSONObject()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !manager.isWallpaperSupported) {
            return result.put("applied", false).put("reason", "This device does not allow wallpaper changes.")
        }

        val bitmap = decode(params)
        val target = params.optString("target", "home")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && target == "lock") {
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && target == "both") {
            manager.setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            )
        } else {
            manager.setBitmap(bitmap)
        }

        result.put("applied", true)
        result.put("target", target)
        result.put("width", bitmap.width)
        result.put("height", bitmap.height)
        return result
    }
}
