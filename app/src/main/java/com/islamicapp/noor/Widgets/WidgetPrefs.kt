package com.islamicapp.noor.Widgets

import android.content.Context
import org.json.JSONObject

/** Shared storage between the WebView (configuration) and the App Widget providers. */
object WidgetPrefs {

    private const val NAME = "noor_widgets"

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun get(context: Context, widget: String): JSONObject {
        val raw = prefs(context).getString(widget, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun save(context: Context, widget: String, config: JSONObject) {
        prefs(context).edit().putString(widget, config.toString()).apply()
    }

    fun bumpTasbih(context: Context): Int {
        val config = get(context, "tasbih")
        val next = config.optInt("count", 0) + 1
        config.put("count", next)
        save(context, "tasbih", config)
        return next
    }
}
