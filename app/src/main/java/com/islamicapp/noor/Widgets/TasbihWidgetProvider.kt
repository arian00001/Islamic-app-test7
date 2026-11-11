package com.islamicapp.noor.Widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.islamicapp.noor.R

/** Tappable dhikr counter - every tap is handled by the provider, no app process needed. */
class TasbihWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_COUNT) {
            WidgetPrefs.bumpTasbih(context)
            WidgetUpdater.refreshAll(context)
        }
        if (intent?.action == ACTION_RESET) {
            val config = WidgetPrefs.get(context, WIDGET)
            config.put("count", 0)
            WidgetPrefs.save(context, WIDGET, config)
            WidgetUpdater.refreshAll(context)
        }
        super.onReceive(context, intent)
    }

    private fun build(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tasbih)
        val config = WidgetPrefs.get(context, WIDGET)
        val count = config.optInt("count", 0)
        val target = config.optInt("target", 33).coerceAtLeast(1)

        views.setTextViewText(R.id.tasbihCount, count.toString())
        views.setTextViewText(R.id.tasbihLabel, config.optString("label", "সুবহানাল্লাহ"))
        views.setTextViewText(R.id.tasbihGoal, count.toString() + " / " + target)
        views.setOnClickPendingIntent(R.id.tasbihRoot, broadcast(context, ACTION_COUNT, 31))
        views.setOnClickPendingIntent(R.id.tasbihReset, broadcast(context, ACTION_RESET, 32))
        return views
    }

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, TasbihWidgetProvider::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val WIDGET = "tasbih"
        const val ACTION_COUNT = "com.islamicapp.noor.widget.TASBIH_COUNT"
        const val ACTION_RESET = "com.islamicapp.noor.widget.TASBIH_RESET"
    }
}
