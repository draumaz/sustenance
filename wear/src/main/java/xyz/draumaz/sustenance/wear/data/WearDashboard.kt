package xyz.draumaz.sustenance.wear.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** One metric as the phone last published it. */
data class WearMetric(
    val key: String,
    val title: String,
    val unit: String,
    /** The phone's own headline, e.g. "1,234kcal (766 left)". */
    val display: String,
    val today: Float,
    val goal: Float?,
    val hasData: Boolean,
    val granted: Boolean,
    val moreIsBetter: Boolean,
    val accent: Int,
) {
    val progress: Float get() = if (goal != null && goal > 0f) today / goal else 0f

    /** Over a limit (red), as opposed to past a target you want to exceed. */
    val isOverLimit: Boolean get() = !moreIsBetter && goal != null && goal > 0f && today > goal

    /** The headline number without its unit, as the phone widget shows it. */
    val number: String get() = display.substringBefore(unit).trim().ifEmpty { "0" }

    /** Every metric is a daily total, so a new day starts from zero until the phone reports. */
    fun zeroed(): WearMetric = copy(display = "0$unit", today = 0f, hasData = false)
}

data class WearDashboard(
    val date: LocalDate,
    val updatedAt: Instant,
    val metrics: List<WearMetric>,
) {
    val isToday: Boolean get() = date == LocalDate.now()

    /** What to show right now: yesterday's snapshot rolls over to zeros at local midnight. */
    fun current(): List<WearMetric> = if (isToday) metrics else metrics.map { it.zeroed() }
}

/**
 * Reads the phone's snapshot from the Data Layer. Play services keeps the last replicated item
 * on the watch, so this works offline and needs no Health Connect access of its own (the
 * watch's Health Connect store is separate and never holds the phone's nutrition).
 *
 * Contract shared with the phone's `WearSync.kt`: keep the paths and JSON keys in step.
 */
object DashboardStore {
    private const val TAG = "DashboardStore"
    const val PATH_DASHBOARD = "/dashboard"
    private const val KEY_PAYLOAD = "payload"
    private const val PATH_REFRESH_REQUEST = "/refresh"

    private val dashboardUri: Uri = "wear://*$PATH_DASHBOARD".toUri()

    suspend fun load(context: Context): WearDashboard? = try {
        val buffer = Wearable.getDataClient(context).getDataItems(dashboardUri).await()
        try {
            // One item per phone; the newest wins if the watch was ever paired to two.
            buffer.mapNotNull { item ->
                DataMapItem.fromDataItem(item).dataMap.getString(KEY_PAYLOAD)?.let(::parse)
            }.maxByOrNull { it.updatedAt }
        } finally {
            buffer.release()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the dashboard from the Data Layer", e)
        null
    }

    /** Emits the current snapshot, then again whenever the phone publishes a new one. */
    fun observe(context: Context): Flow<WearDashboard?> = callbackFlow {
        val client = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { events ->
            val relevant = events.any {
                it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == PATH_DASHBOARD
            }
            events.release()
            if (relevant) launch { send(load(context)) }
        }
        client.addListener(listener)
        send(load(context))
        awaitClose { client.removeListener(listener) }
    }

    /**
     * Asks the phone to re-read Health Connect and publish. Best effort: if the phone is out of
     * range, the periodic refresh there catches up and the Data Layer delivers on reconnect.
     */
    suspend fun requestRefresh(context: Context) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val messages = Wearable.getMessageClient(context)
            nodes.forEach { messages.sendMessage(it.id, PATH_REFRESH_REQUEST, null).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't ask the phone for a refresh", e)
        }
    }

    private fun parse(json: String): WearDashboard? = try {
        val root = JSONObject(json)
        val array = root.getJSONArray("metrics")
        val metrics = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            WearMetric(
                key = o.getString("key"),
                title = o.getString("title"),
                unit = o.getString("unit"),
                display = o.getString("display"),
                today = o.getDouble("today").toFloat(),
                goal = if (o.isNull("goal")) null else o.getDouble("goal").toFloat(),
                hasData = o.getBoolean("hasData"),
                granted = o.getBoolean("granted"),
                moreIsBetter = o.optBoolean("moreIsBetter"),
                accent = o.getInt("accent"),
            )
        }
        WearDashboard(
            date = LocalDate.parse(root.getString("date")),
            updatedAt = Instant.ofEpochMilli(root.getLong("updatedAt")),
            metrics = metrics,
        )
    } catch (e: Exception) {
        // A payload from a newer/older phone build that doesn't parse: show nothing rather than crash.
        Log.w(TAG, "Unreadable dashboard payload", e)
        null
    }
}
