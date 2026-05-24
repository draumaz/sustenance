package dev.easonhuang.heartwood

import android.app.Application
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.widget.WidgetUpdateScheduler

class HeartwoodApp : Application() {
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(this) }
    val goals: GoalsRepository by lazy { GoalsRepository(this) }
    val exporter: ExportManager by lazy { ExportManager(this, healthConnect) }

    override fun onCreate() {
        super.onCreate()
        WidgetUpdateScheduler.ensureScheduled(this)
    }
}
