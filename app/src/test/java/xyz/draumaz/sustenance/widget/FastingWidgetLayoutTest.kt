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
import java.time.Duration
import java.time.Instant

/**
 * Renders the fasting widget's content at the sizes launchers and the lock-screen hub hand out
 * and checks what each layout shows. A plain Application stands in for SustenanceApp, whose
 * onCreate schedules WorkManager work that Robolectric hasn't initialised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS")
class FastingWidgetLayoutTest {

    private val now = Instant.parse("2026-11-21T20:50:00Z")
    private fun fastingFor(hours: Long) = FastState(now.minus(Duration.ofHours(hours)), goalHours = 16f, now = now)

    private fun render(width: Int, height: Int, state: FastState, checks: AssertScope.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(DpSize(width.dp, height.dp))
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { FastingContent(state) }
            AssertScope(
                exists = { text, substring -> onNode(hasText(text, substring)).assertExists() },
                absent = { text, substring -> onNode(hasText(text, substring)).assertDoesNotExist() },
            ).checks()
        }

    /** Keeps the assertions readable without leaking the Glance test scope's type. */
    class AssertScope(
        val exists: (String, Boolean) -> Unit,
        val absent: (String, Boolean) -> Unit,
    )

    @Test
    fun narrowCompact_showsOnlyTheClock() = render(110, 40, fastingFor(8)) {
        absent("Time fasting", false)
        absent("Goal", true)
    }

    @Test
    fun wideCompact_addsTheGoal() = render(250, 50, fastingFor(8)) {
        exists("Goal 16h", false)
        absent("Time fasting", false)
        absent("since", true)
    }

    @Test
    fun medium_showsLabelAndFooterButNoPercentage() = render(180, 100, fastingFor(8)) {
        exists("Time fasting", false)
        exists("Goal 16h by ", true)
        absent("since", true)
        absent("50%", false)
    }

    @Test
    fun wideMedium_addsWhenTheFastBegan() = render(300, 100, fastingFor(8)) {
        exists("Goal 16h by ", true)
        exists(" · since ", true)
    }

    @Test
    fun mediumGoalReached_hasNoGoalTime() = render(300, 100, fastingFor(20)) {
        exists("Goal reached · since ", true)
        absent(" by ", true)
    }

    @Test
    fun large_addsThePercentage() = render(411, 251, fastingFor(8)) {
        exists("Time fasting", false)
        exists("50% · ", true)
        exists("Goal 16h · since ", true)
    }

    @Test
    fun goalReached_saysSoAndCapsAtFull() = render(411, 251, fastingFor(20)) {
        exists("Goal reached · since ", true)
        exists("100%", false)
        absent("Goal 16h", true)
    }

    @Test
    fun noMealsLogged_saysSoWithoutAPercentage() =
        render(411, 251, FastState(lastLog = null, goalHours = 16f, now = now)) {
            exists("No meals logged yet", false)
            exists("Goal 16h", false)
            absent("%", true)
        }
}
