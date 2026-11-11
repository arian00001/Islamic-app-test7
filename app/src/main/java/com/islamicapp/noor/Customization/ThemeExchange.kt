package com.islamicapp.noor.Customization

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Writes theme presets into the app's private folder and validates an imported one. */
object ThemeExchange {

    fun export(context: Context, payload: JSONObject): JSONObject {
        val folder = File(context.filesDir, "themes")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "theme-" + System.currentTimeMillis() + ".json")
        file.writeText(payload.toString(2))
        return JSONObject()
            .put("saved", true)
            .put("path", file.absolutePath)
            .put("bytes", file.length())
    }

    fun importTheme(json: String): JSONObject {
        require(json.isNotBlank()) { "Nothing to import" }
        val parsed = JSONObject(json)
        require(parsed.has("themeId") || parsed.has("tokens") || parsed.has("name")) {
            "This is not a Noor theme file"
        }
        return parsed
    }
}
