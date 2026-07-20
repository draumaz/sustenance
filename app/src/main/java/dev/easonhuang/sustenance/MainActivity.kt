package dev.easonhuang.sustenance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.easonhuang.sustenance.ui.SustenanceRoot
import dev.easonhuang.sustenance.ui.theme.SustenanceTheme

class MainActivity : ComponentActivity() {

    // Deep-link target from a metric widget tap; consumed once navigation happens.
    private var deepLinkMetric by mutableStateOf<String?>(null)
    private var sharedImages by mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val app = application as SustenanceApp
        setContent {
            val dynamicColor by app.settings.dynamicColor.collectAsState(initial = true)
            SustenanceTheme(dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SustenanceRoot(
                        manager = app.healthConnect,
                        goalsRepo = app.goals,
                        settingsRepo = app.settings,
                        exporter = app.exporter,
                        deepLinkMetric = deepLinkMetric,
                        sharedImageUris = sharedImages,
                        onDeepLinkConsumed = { deepLinkMetric = null },
                        onSharedImagesConsumed = { sharedImages = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        deepLinkMetric = intent.getStringExtra(EXTRA_METRIC)

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let {
                        sharedImages = listOf(it)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    sharedImages = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
            }
        }
    }

    companion object {
        const val EXTRA_METRIC = "dev.easonhuang.sustenance.extra.METRIC"
    }
}
