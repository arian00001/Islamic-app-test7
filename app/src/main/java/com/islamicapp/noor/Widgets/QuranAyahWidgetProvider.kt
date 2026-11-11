package com.islamicapp.noor.Widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.widget.RemoteViews
import com.islamicapp.noor.MainActivity
import com.islamicapp.noor.R
import org.json.JSONArray

/**
 * Horizontal Quran ayah widget. The ayah list, translation, size and background all
 * come from the Widget Studio; previous/next work without opening the app.
 */
class QuranAyahWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
        WidgetUpdater.ensureScheduled(context)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_NEXT -> move(context, 1)
            ACTION_PREV -> move(context, -1)
        }
        super.onReceive(context, intent)
    }

    private fun move(context: Context, delta: Int) {
        val config = WidgetPrefs.get(context, WIDGET)
        val ayahs = config.optJSONArray("ayahs") ?: JSONArray()
        if (ayahs.length() == 0) return
        val current = config.optInt("index", 0)
        var next = (current + delta) % ayahs.length()
        if (next < 0) next += ayahs.length()
        config.put("index", next)
        WidgetPrefs.save(context, WIDGET, config)
        WidgetUpdater.refreshAll(context)
    }

    private fun build(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quran_ayah)
        val config = WidgetPrefs.get(context, WIDGET)
        val ayahs = config.optJSONArray("ayahs") ?: JSONArray()

        var index = 0
        if (ayahs.length() > 0) {
            index = config.optInt("index", 0) % ayahs.length()
            if (index < 0) index += ayahs.length()
        }
        val ayah = if (ayahs.length() > 0) ayahs.optJSONObject(index) else null

        views.setTextViewText(
            R.id.widgetArabic,
            ayah?.optString("arabic", "")?.takeIf { it.isNotBlank() }
                ?: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
        )
        views.setTextViewText(
            R.id.widgetTranslation,
            ayah?.optString("translation", "")?.takeIf { it.isNotBlank() }
                ?: "Open Iman Sangi -> Customize -> Widget Studio"
        )
        views.setTextViewText(R.id.widgetReference, ayah?.optString("reference", "") ?: "")

        views.setTextViewTextSize(
            R.id.widgetArabic,
            TypedValue.COMPLEX_UNIT_SP,
            config.optInt("fontSize", 22).coerceIn(14, 40).toFloat()
        )
        views.setInt(R.id.widgetRoot, "setBackgroundColor", background(config.optString("background", "emerald")))

        views.setOnClickPendingIntent(R.id.widgetPrev, broadcast(context, ACTION_PREV, 11))
        views.setOnClickPendingIntent(R.id.widgetNext, broadcast(context, ACTION_NEXT, 12))
        views.setOnClickPendingIntent(R.id.widgetRoot, openStudio(context, WIDGET, 13))
        return views
    }

    private fun background(name: String): Int = when (name) {
        "cream" -> Color.parseColor("#F6F1E4")
        "gold" -> Color.parseColor("#EBD9A6")
        "glass" -> Color.parseColor("#F7FAF4")
        else -> Color.parseColor("#0B3D2E")
    }

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, QuranAyahWidgetProvider::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val WIDGET = "quran_ayah"
        const val ACTION_NEXT = "com.islamicapp.noor.widget.QURAN_NEXT"
        const val ACTION_PREV = "com.islamicapp.noor.widget.QURAN_PREV"

        fun openStudio(context: Context, widget: String, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra(MainActivity.EXTRA_ROUTE, "/customize/widgets?widget=" + widget)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
