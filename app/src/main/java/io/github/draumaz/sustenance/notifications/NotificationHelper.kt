package io.github.draumaz.sustenance.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import io.github.draumaz.sustenance.R

object NotificationHelper {
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("NotificationHelper", "Creating notification channels")
            val name = context.getString(R.string.notification_channel_fasting)
            val descriptionText = context.getString(R.string.fasting_notifications_summary)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(FastingNotificationWorker.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
