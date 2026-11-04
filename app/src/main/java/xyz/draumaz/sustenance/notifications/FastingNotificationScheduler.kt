package xyz.draumaz.sustenance.notifications

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import xyz.draumaz.sustenance.SustenanceApp
import xyz.draumaz.sustenance.data.HealthConnectManager
import java.time.Instant
import java.util.concurrent.TimeUnit

object FastingNotificationScheduler {
    private const val WORK_NAME = "fasting_goal_notification"

    /**
     * One shared decision for "what should be scheduled": used live by the dashboard (in-memory
     * state) and by background re-syncs (live Health Connect reads), so both paths can't drift.
     * A fast is only notified once per period: [lastNotified] records when the current fast
     * already fired.
     */
    fun apply(
        context: Context,
        enabled: Boolean,
        lastLog: Instant?,
        goalHours: Float,
        lastNotifiedEpochSecond: Long,
    ) {
        val alreadyNotifiedForThisFast = lastLog != null && lastLog.epochSecond <= lastNotifiedEpochSecond
        if (enabled && lastLog != null && !alreadyNotifiedForThisFast) {
            schedule(context, lastLog, goalHours)
        } else {
            cancel(context)
        }
    }

    /**
     * Re-derives the schedule from live Health Connect data instead of the dashboard's cached
     * state. Called from background workers because the dashboard's ViewModel isn't alive there:
     * without this, food logged by other apps while we're closed never moved the fast target.
     * Leaves existing work untouched when a decision can't be made (no background-read
     * permission), so a refused read can't cancel a notification that was already set up.
     */
    suspend fun syncWithHealthConnect(context: Context) {
        val app = context.applicationContext as SustenanceApp
        val enabled = app.settings.fastingNotificationsEnabled.first()
        if (!enabled) {
            cancel(context)
            return
        }

        val granted = runCatching { app.healthConnect.grantedPermissions() }.getOrDefault(emptySet())
        if (HealthConnectManager.PERMISSION_READ_IN_BACKGROUND !in granted) return

        val lastLog = app.healthConnect.readLastFoodLogTime(app.settings.fastBreakingCalories.first().toDouble())
            ?: run {
                // No qualifying food log: nothing to fast from, so nothing to notify about.
                cancel(context)
                return
            }

        apply(
            context = context,
            enabled = true,
            lastLog = lastLog,
            goalHours = app.settings.fastingGoalHours.first(),
            lastNotifiedEpochSecond = app.settings.lastFastingNotificationTime.first(),
        )
    }

    fun schedule(context: Context, lastLogTime: Instant, goalHours: Float) {
        val workManager = WorkManager.getInstance(context)

        val goalSeconds = (goalHours * 3600).toLong()
        val targetTime = lastLogTime.plusSeconds(goalSeconds)
        val now = Instant.now()

        val delay = targetTime.epochSecond - now.epochSecond

        Log.d("FastingScheduler", "Scheduling check: lastLog=$lastLogTime, goal=$goalHours, target=$targetTime, now=$now, delay=$delay")

        if (delay <= 0) {
            Log.d("FastingScheduler", "Goal already met, triggering immediate notification")
            val data = Data.Builder()
                .putFloat(FastingNotificationWorker.EXTRA_FASTING_GOAL, goalHours)
                .build()
            val request = OneTimeWorkRequestBuilder<FastingNotificationWorker>()
                .setInputData(data)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            return
        }

        val data = Data.Builder()
            .putFloat(FastingNotificationWorker.EXTRA_FASTING_GOAL, goalHours)
            .build()

        val request = OneTimeWorkRequestBuilder<FastingNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .setInputData(data)
            .build()

        Log.d("FastingScheduler", "Enqueuing unique work with delay $delay seconds")
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
