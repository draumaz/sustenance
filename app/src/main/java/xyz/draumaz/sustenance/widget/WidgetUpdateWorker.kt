package xyz.draumaz.sustenance.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import xyz.draumaz.sustenance.notifications.FastingNotificationScheduler

/** Periodically re-renders the home-screen widget(s) with fresh Health Connect data. */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SustenanceWidget().updateAll(applicationContext)
            // The dashboard's ViewModel isn't alive here, so nothing else keeps fasting
            // notifications honest with food logged by other apps while we're in the background.
            FastingNotificationScheduler.syncWithHealthConnect(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            // WorkManager stopping us (e.g. REPLACE by a newer refresh) isn't a failure to retry.
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
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
