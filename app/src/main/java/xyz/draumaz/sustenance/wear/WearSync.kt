package xyz.draumaz.sustenance.wear

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import xyz.draumaz.sustenance.SustenanceApp
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.MetricSummary
import xyz.draumaz.sustenance.util.runCatchingCancellable
import java.time.LocalDate

/**
 * Mirrors today's dashboard to the Wear OS companion. Health Connect keeps a separate store on
 * each device and doesn't sync between them, so the watch can't read the phone's nutrition
 * itself: the phone publishes a small summary as a Data Layer item, which Play services
 * replicates to every paired watch (immediately if one is connected, otherwise on reconnect).
 *
 * The payload contract is shared with `:wear` (`WearDashboard.kt` there): keep the two in step.
 */
object WearSync {
    private const val TAG = "WearSync"

    const val PATH_DASHBOARD = "/dashboard"
    const val KEY_PAYLOAD = "payload"

    /** Message the watch sends when it opens with no (or stale) data. */
    const val PATH_REFRESH_REQUEST = "/refresh"

    /** Reads today's dashboard the way the home-screen widget does and pushes it to the watch. */
    suspend fun pushToday(context: Context) {
        val app = context.applicationContext as SustenanceApp
        val manager = app.healthConnect
        if (!manager.isAvailable) return
        val goals = app.goals.goals.first()
        val isKeto = app.settings.ketoMode.first()
        // Same derived food goal the dashboard and widget show when a balance goal is set. Unlike
        // readDashboard this read throws (e.g. SecurityException without the background
        // permission), and an exception here would fail the whole widget worker into retries.
        val foodGoal = runCatchingCancellable {
            manager.foodGoalForBalance(goals[Metric.CALORIC_BALANCE] ?: 0f, 0)
        }.getOrNull()
        val finalGoals = if (foodGoal != null) goals + (Metric.FOOD to foodGoal) else goals
        push(context, manager.readDashboard(goals = finalGoals, isKeto = isKeto))
    }

    suspend fun push(context: Context, summaries: List<MetricSummary>) {
        // readDashboard swallows per-metric errors, so a background read without the background
        // permission comes back as all "no data". Publishing that would zero the watch out, while
        // the last good snapshot is still right (and the watch zeroes itself at midnight anyway).
        if (summaries.none { it.hasData }) return

        val locales = context.resources.configuration.locales
        val metrics = JSONArray()
        for (s in summaries) {
            metrics.put(
                JSONObject()
                    .put("key", s.metric.key)
                    // Titles and units are resolved here so the watch matches the phone exactly
                    // (Net carbs in keto mode, regional "Fibre") without duplicating that logic.
                    .put("title", s.titleOverride ?: context.getString(s.metric.titleResFor(locales)))
                    .put("unit", context.getString(s.metric.unitRes))
                    .put("display", s.value)
                    .put("today", (s.spark.lastOrNull() ?: 0f).toDouble())
                    .put("goal", s.goal?.toDouble() ?: JSONObject.NULL)
                    .put("hasData", s.hasData)
                    .put("granted", s.granted)
                    .put("moreIsBetter", s.metric.moreIsBetter)
                    .put("accent", s.metric.accent.toArgb()),
            )
        }
        val payload = JSONObject()
            .put("date", LocalDate.now().toString())
            .put("updatedAt", System.currentTimeMillis())
            .put("metrics", metrics)
            .toString()

        val request = PutDataMapRequest.create(PATH_DASHBOARD).apply {
            dataMap.putString(KEY_PAYLOAD, payload)
        }.asPutDataRequest().setUrgent()

        try {
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Play services or Wearable API on this phone: the watch simply stays as it was.
            Log.w(TAG, "Couldn't publish the dashboard to Wear OS", e)
        }
    }
}
