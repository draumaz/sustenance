package dev.easonhuang.sustenance.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.easonhuang.sustenance.data.ExportManager
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.ui.components.ExpressiveNavigationBar
import dev.easonhuang.sustenance.ui.components.PredictiveBackState
import dev.easonhuang.sustenance.ui.dashboard.DashboardScreen
import dev.easonhuang.sustenance.ui.detail.DetailScreen
import dev.easonhuang.sustenance.ui.onboarding.LoadingScreen
import dev.easonhuang.sustenance.ui.onboarding.OnboardingScreen
import dev.easonhuang.sustenance.ui.onboarding.UnavailableScreen
import dev.easonhuang.sustenance.ui.settings.SettingsScreen
import dev.easonhuang.sustenance.ui.summary.SummaryScreen
import dev.easonhuang.sustenance.widget.WidgetUpdateWorker

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val ACTION_HC_SETTINGS = "androidx.health.connect.action.HEALTH_CONNECT_SETTINGS"
private const val ACTION_MANAGE_HEALTH_PERMISSIONS = "androidx.health.connect.action.MANAGE_HEALTH_PERMISSIONS"

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.Today),
    SUMMARY("summary", "Summary", Icons.Rounded.Insights),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
fun SustenanceRoot(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    if (!manager.isAvailable) {
        UnavailableScreen(onInstall = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                        .setPackage("com.android.vending")
                )
            }
        })
        return
    }

    var granted by remember { mutableStateOf<Set<String>?>(null) }
    // True while the initial setup is chaining its permission requests (data → background).
    var inSetup by remember { mutableStateOf(false) }
    var requestedExtras by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val predictiveBackState = remember { PredictiveBackState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result ->
        granted = result
        WidgetUpdateWorker.enqueue(context)
    }

    // Re-read grants every time we return to the app (e.g. after toggling in HC settings).
    LifecycleResumeEffect(Unit) {
        scope.launch {
            val next = manager.grantedPermissions()
            if (next != granted) {
                granted = next
                WidgetUpdateWorker.enqueue(context)
            }
        }
        onPauseOrDispose { }
    }

    val hasData = (granted ?: emptySet()).any { it in manager.metricPermissions }
    val hasBackground = granted?.contains(HealthConnectManager.PERMISSION_READ_IN_BACKGROUND) == true

    // Setup is one continuous flow: after data is granted, immediately chain the background +
    // history prompt (HC won't allow it in the same request), so widgets work straight away.
    LaunchedEffect(inSetup, granted) {
        if (!inSetup || granted == null) return@LaunchedEffect
        when {
            !hasData -> inSetup = false              // user declined data; back to onboarding
            !hasBackground && !requestedExtras -> {  // data in, now ask for background once
                requestedExtras = true
                permissionLauncher.launch(manager.extraPermissions)
            }
            else -> inSetup = false                  // background resolved → enter the app
        }
    }

    fun startSetup() {
        requestedExtras = false
        inSetup = true
        permissionLauncher.launch(manager.metricPermissions)
    }

    fun manageAccess() {
        val g = granted ?: emptySet()
        val missingMetrics = manager.metricPermissions.filter { it !in g }
        when {
            // New metrics added to the app won't be in the existing grant set.
            missingMetrics.isNotEmpty() -> permissionLauncher.launch(missingMetrics.toSet())
            // Data granted but background (for widgets) missing → add it now.
            HealthConnectManager.PERMISSION_READ_IN_BACKGROUND !in g ->
                permissionLauncher.launch(manager.extraPermissions)
            // Everything granted → open Health Connect's per-app screen (then its home) to review.
            else -> {
                val candidates = listOf(
                    Intent(ACTION_MANAGE_HEALTH_PERMISSIONS).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
                    Intent(ACTION_HC_SETTINGS),
                )
                for (intent in candidates) {
                    if (runCatching { context.startActivity(intent) }.isSuccess) return
                }
                permissionLauncher.launch(manager.permissions)
            }
        }
    }

    when {
        granted == null || inSetup -> LoadingScreen()
        !hasData -> OnboardingScreen(onConnect = ::startSetup)
        else -> {
            MainNav(
                manager, goalsRepo, exporter,
                granted = granted ?: emptySet(),
                onManagePermissions = ::manageAccess,
                deepLinkMetric = deepLinkMetric,
                onDeepLinkConsumed = onDeepLinkConsumed,
                predictiveBackState = predictiveBackState,
            )
        }
    }
}

@Composable
private fun MainNav(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    granted: Set<String>,
    onManagePermissions: () -> Unit,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    predictiveBackState: PredictiveBackState,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = remember { Dest.entries.toList() }
    val showBar = currentRoute in topLevel.map { it.route } || currentRoute?.startsWith("detail/") == true

    // Open a metric's detail directly when launched from its widget.
    LaunchedEffect(deepLinkMetric) {
        val metric = deepLinkMetric?.let { Metric.fromKey(it) }
        if (metric != null) {
            val isGranted = manager.permissionFor(metric) in granted
            if (isGranted) {
                navController.navigate("detail/${metric.key}")
            } else {
                onManagePermissions()
            }
            onDeepLinkConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBar) {
                ExpressiveNavigationBar(
                    navController = navController,
                    destinations = topLevel,
                    predictiveBackState = predictiveBackState,
                    onNavigate = { dest ->
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
    ) { inner ->
        val bottomInset = inner.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Dest.TODAY.route) {
                DashboardScreen(
                    manager = manager,
                    goalsRepo = goalsRepo,
                    granted = granted,
                    bottomInset = bottomInset,
                    onOpenMetric = { metric -> navController.navigate("detail/${metric.key}") },
                    onManagePermissions = onManagePermissions,
                )
            }
            composable(Dest.SUMMARY.route) {
                SummaryScreen(
                    manager = manager,
                    goalsRepo = goalsRepo,
                    bottomInset = bottomInset,
                    predictiveBackState = predictiveBackState,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Dest.SETTINGS.route) {
                SettingsScreen(
                    manager = manager,
                    exporter = exporter,
                    bottomInset = bottomInset,
                    onManagePermissions = onManagePermissions,
                    predictiveBackState = predictiveBackState,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("detail/{key}") { entry ->
                val metric = entry.arguments?.getString("key")?.let { Metric.fromKey(it) }
                if (metric == null) {
                    navController.popBackStack()
                } else {
                    DetailScreen(
                        manager = manager,
                        metric = metric,
                        predictiveBackState = predictiveBackState,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
