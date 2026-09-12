package xyz.draumaz.sustenance

import android.app.Application
import android.util.Log
import xyz.draumaz.sustenance.data.ExportManager
import xyz.draumaz.sustenance.data.GoalsRepository
import xyz.draumaz.sustenance.data.HealthConnectManager
import xyz.draumaz.sustenance.data.SettingsRepository
import xyz.draumaz.sustenance.notifications.NotificationHelper
import xyz.draumaz.sustenance.widget.WidgetUpdateScheduler

class SustenanceApp : Application() {
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(this) }
    val goals: GoalsRepository by lazy { GoalsRepository(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val exporter: ExportManager by lazy { ExportManager(this, healthConnect) }

    override fun onCreate() {
        super.onCreate()
        Log.d("SustenanceApp", "onCreate")
        WidgetUpdateScheduler.ensureScheduled(this)
        NotificationHelper.createNotificationChannels(this)
    }
}
