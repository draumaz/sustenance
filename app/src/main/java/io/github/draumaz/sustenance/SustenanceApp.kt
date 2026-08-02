package io.github.draumaz.sustenance

import android.app.Application
import io.github.draumaz.sustenance.data.ExportManager
import io.github.draumaz.sustenance.data.GoalsRepository
import io.github.draumaz.sustenance.data.HealthConnectManager
import io.github.draumaz.sustenance.data.SettingsRepository
import io.github.draumaz.sustenance.widget.WidgetUpdateScheduler

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
