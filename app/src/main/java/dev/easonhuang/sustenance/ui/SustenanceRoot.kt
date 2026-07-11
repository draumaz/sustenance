package dev.easonhuang.sustenance.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.health.connect.client.PermissionController
import androidx.activity.compose.PredictiveBackHandler
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.easonhuang.sustenance.data.ExportManager
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.SettingsRepository
import dev.easonhuang.sustenance.ui.components.ExpressiveNavigationBar
import dev.easonhuang.sustenance.ui.components.PredictiveBackState
import dev.easonhuang.sustenance.ui.dashboard.DashboardScreen
import dev.easonhuang.sustenance.ui.detail.DetailScreen
import dev.easonhuang.sustenance.ui.onboarding.LoadingScreen
import dev.easonhuang.sustenance.ui.onboarding.OnboardingScreen
import dev.easonhuang.sustenance.ui.onboarding.UnavailableScreen
import dev.easonhuang.sustenance.ui.settings.SettingsScreen
import dev.easonhuang.sustenance.ui.summary.SummaryScreen
import dev.easonhuang.sustenance.ui.summary.SummaryViewModel
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
    settingsRepo: SettingsRepository,
    exporter: ExportManager,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    if (!manager.isAvailable) {
        UnavailableScreen(onInstall = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "market://details?id=$HEALTH_CONNECT_PACKAGE".toUri())
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
                manager, goalsRepo, settingsRepo, exporter,
                granted = granted ?: emptySet(),
                onManagePermissions = ::manageAccess,
                deepLinkMetric = deepLinkMetric,
                onDeepLinkConsumed = onDeepLinkConsumed,
            )
        }
    }
}

@Composable
private fun MainNav(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: SettingsRepository,
    exporter: ExportManager,
    granted: Set<String>,
    onManagePermissions: () -> Unit,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = remember { Dest.entries.toList() }
    val showBar = currentRoute in topLevel.map { it.route } || currentRoute?.startsWith("detail/") == true

    val pbState = remember { PredictiveBackState() }
    var todayClickCount by remember { mutableIntStateOf(0) }

    val bottomBarHeight = 120.dp
    val bottomBarHeightPx = with(LocalDensity.current) { bottomBarHeight.roundToPx().toFloat() }
    val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx.floatValue + delta
                bottomBarOffsetHeightPx.floatValue = newOffset.coerceIn(-bottomBarHeightPx, 0f)
                return Offset.Zero
            }
        }
    }

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
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        bottomBar = {
            if (showBar) {
                val animatedOffset by animateIntAsState(
                    targetValue = bottomBarOffsetHeightPx.floatValue.roundToInt(),
                    animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
                    label = "bottom_bar_offset"
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = -animatedOffset) }
                ) {
                    ExpressiveNavigationBar(
                        navController = navController,
                        destinations = topLevel,
                        predictiveBackState = pbState,
                        onNavigate = { dest ->
                            if (dest == Dest.TODAY) {
                                if (currentRoute == Dest.TODAY.route) {
                                    todayClickCount++
                                    bottomBarOffsetHeightPx.floatValue = 0f
                                } else if (currentRoute?.startsWith("detail/") == true) {
                                    navController.popBackStack(Dest.TODAY.route, inclusive = false)
                                } else {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            } else {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        },
    ) { inner ->
        val bottomInset = inner.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.92f, animationSpec = tween(300))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.92f, animationSpec = tween(300))
            }
        ) {
            composable(Dest.TODAY.route) {
                DashboardScreen(
                    manager = manager,
                    goalsRepo = goalsRepo,
                    settingsRepo = settingsRepo,
                    granted = granted,
                    bottomInset = bottomInset,
                    todayClickCount = todayClickCount,
                    onOpenMetric = { metric, offset -> navController.navigate("detail/${metric.key}?offset=$offset") },
                    onManagePermissions = onManagePermissions,
                    onDateChanged = { bottomBarOffsetHeightPx.floatValue = 0f }
                )
            }
            composable(Dest.SUMMARY.route) {
                SummaryScreen(
                    manager = manager,
                    goalsRepo = goalsRepo,
                    bottomInset = bottomInset,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Dest.SETTINGS.route) {
                SettingsScreen(
                    manager = manager,
                    exporter = exporter,
                    settingsRepo = settingsRepo,
                    bottomInset = bottomInset,
                    onManagePermissions = onManagePermissions,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "detail/{key}?offset={offset}",
                arguments = listOf(
                    navArgument("key") { type = NavType.StringType },
                    navArgument("offset") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { entry ->
                val metric = entry.arguments?.getString("key")?.let { Metric.fromKey(it) }
                val offset = entry.arguments?.getInt("offset") ?: 0
                if (metric == null) {
                    navController.popBackStack()
                } else {
                    DetailScreen(
                        manager = manager,
                        goalsRepo = goalsRepo,
                        metric = metric,
                        dateOffset = offset,
                        pbState = pbState,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
