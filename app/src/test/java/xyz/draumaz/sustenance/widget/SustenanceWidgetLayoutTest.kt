package xyz.draumaz.sustenance.widget

import android.app.Application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.MetricSummary

/** Checks the dashboard widget switches to titled tiles only when tiles are big enough for them. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS")
class SustenanceWidgetLayoutTest {

    private val metrics = listOf(
        Metric.TOTAL_CALORIES, Metric.FOOD, Metric.PROTEIN, Metric.CARBS, Metric.FAT, Metric.FIBER,
    )
    private val tiles = metrics.mapIndexed { i, m ->
        MetricSummary(metric = m, value = "${100 + i}", caption = null, hasData = true, granted = true, spark = listOf(50f), goal = 100f)
    }
    private val titles = listOf("Total energy", "Food", "Protein", "Carbs", "Fat", "Fiber")

    private fun render(width: Int, height: Int, expectTitles: Boolean) = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(width.dp, height.dp))
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable { WidgetContent(tiles) }
        tiles.forEach { onNode(hasText(it.value)).assertExists() }
        titles.forEach { title ->
            if (expectTitles) onNode(hasText(title)).assertExists() else onNode(hasText(title)).assertDoesNotExist()
        }
    }

    @Test
    fun lockScreenHubSize_showsMetricTitles() = render(411, 251, expectTitles = true)

    @Test
    fun defaultTwoByOne_keepsCompactTiles() = render(180, 110, expectTitles = false)
}
