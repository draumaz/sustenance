package xyz.draumaz.sustenance.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import xyz.draumaz.sustenance.wear.ui.DashboardScreen
import xyz.draumaz.sustenance.wear.ui.MetricFocus

class MainActivity : ComponentActivity() {
    /** A metric the tile asked to show, pending until the list has scrolled to it. */
    private val focus = mutableStateOf<MetricFocus?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch carries a tap; after recreation the user has scrolled on their own.
        if (savedInstanceState == null) consume(intent, animate = false)
        setContent {
            DashboardScreen(focus = focus.value, onFocusHandled = { focus.value = null })
        }
    }

    // singleTop: a tile tap while the app is already open arrives here instead of a new screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent, animate = true)
    }

    private fun consume(intent: Intent, animate: Boolean) {
        val key = intent.getStringExtra(EXTRA_METRIC) ?: return
        // A new object each time, so tapping the same metric twice scrolls back to it again.
        focus.value = MetricFocus(key, animate)
        intent.removeExtra(EXTRA_METRIC)
    }

    companion object {
        /** Metric key (e.g. "protein") to scroll to; sent by the tile's cells. */
        const val EXTRA_METRIC = "xyz.draumaz.sustenance.wear.extra.METRIC"
    }
}
