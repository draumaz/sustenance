package xyz.draumaz.sustenance.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import xyz.draumaz.sustenance.MainActivity
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.SustenanceApp
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
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "fasting_notifications"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_FASTING_GOAL = "extra_fasting_goal"
    }
}
