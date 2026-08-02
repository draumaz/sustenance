package io.github.draumaz.sustenance.notifications

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.util.concurrent.TimeUnit

object FastingNotificationScheduler {
    private const val WORK_NAME = "fasting_goal_notification"

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
