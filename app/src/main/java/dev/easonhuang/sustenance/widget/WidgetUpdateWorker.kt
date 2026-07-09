package dev.easonhuang.sustenance.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Periodically re-renders the home-screen widget(s) with fresh Health Connect data. */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            SustenanceWidget().updateAll(applicationContext)
            // Recompute each metric widget's data into its Glance state (reactively recomposes).
            val manager = GlanceAppWidgetManager(applicationContext)
            manager.getGlanceIds(MetricWidget::class.java).forEach { id ->
                MetricWidget.refreshData(applicationContext, id)
            }
            MetricWidget().updateAll(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sustenance_widget_refresh_now",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
