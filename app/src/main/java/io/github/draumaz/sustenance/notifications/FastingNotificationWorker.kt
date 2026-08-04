package io.github.draumaz.sustenance.notifications

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.draumaz.sustenance.R
import io.github.draumaz.sustenance.SustenanceApp
import java.time.Instant

class FastingNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d("FastingWorker", "doWork started")
        val app = applicationContext as SustenanceApp
        val fastingGoal = inputData.getFloat(EXTRA_FASTING_GOAL, 16f)
        showNotification(fastingGoal)
        app.settings.setLastFastingNotificationTime(Instant.now().epochSecond)
        return Result.success()
    }

    private fun showNotification(goal: Float) {
        Log.d("FastingWorker", "Showing notification for goal: $goal")
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val goalText = if (goal % 1f == 0f) goal.toInt().toString() else goal.toString()
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.fasting_goal_met_title))
            .setContentText(applicationContext.getString(R.string.fasting_goal_met_body, goalText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "fasting_notifications"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_FASTING_GOAL = "extra_fasting_goal"
    }
}
