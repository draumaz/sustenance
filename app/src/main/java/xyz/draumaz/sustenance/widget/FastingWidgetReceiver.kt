package xyz.draumaz.sustenance.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Shares the dashboard widget's refresh work: [WidgetUpdateWorker] re-renders both. */
class FastingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FastingWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.ensureScheduled(context)
        WidgetUpdateWorker.enqueue(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateScheduler.ensureScheduled(context)
        WidgetUpdateWorker.enqueue(context)
    }
}
