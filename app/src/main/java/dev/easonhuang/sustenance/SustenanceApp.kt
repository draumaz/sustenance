package dev.easonhuang.sustenance

import android.app.Application
import dev.easonhuang.sustenance.data.ExportManager
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.SettingsRepository
import dev.easonhuang.sustenance.widget.WidgetUpdateScheduler

class SustenanceApp : Application() {
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(this) }
    val goals: GoalsRepository by lazy { GoalsRepository(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val exporter: ExportManager by lazy { ExportManager(this, healthConnect) }

    override fun onCreate() {
        super.onCreate()
        WidgetUpdateScheduler.ensureScheduled(this)
    }
}
