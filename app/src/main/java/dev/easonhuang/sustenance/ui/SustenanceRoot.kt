package dev.easonhuang.sustenance.ui

import android.content.Intent
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.data.ExportManager
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.SettingsRepository
import dev.easonhuang.sustenance.ui.components.ExpressiveNavigationBar
import dev.easonhuang.sustenance.ui.components.CameraPreview
import dev.easonhuang.sustenance.ui.components.PredictiveBackState
import dev.easonhuang.sustenance.ui.components.ScallopedLoadingAnimation
import dev.easonhuang.sustenance.ui.dashboard.DashboardScreen
import dev.easonhuang.sustenance.ui.detail.DetailScreen
import dev.easonhuang.sustenance.ui.onboarding.LoadingScreen
import dev.easonhuang.sustenance.ui.onboarding.OnboardingScreen
import dev.easonhuang.sustenance.ui.onboarding.UnavailableScreen
import dev.easonhuang.sustenance.ui.settings.SettingsScreen
import dev.easonhuang.sustenance.ui.summary.SummaryScreen
import dev.easonhuang.sustenance.ui.summary.SummaryViewModel
import dev.easonhuang.sustenance.ui.components.FoodReviewDialog
import dev.easonhuang.sustenance.ui.history.HistoryScreen
import dev.easonhuang.sustenance.util.FoodNutrients
import dev.easonhuang.sustenance.util.GeminiManager
import dev.easonhuang.sustenance.widget.WidgetUpdateWorker

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val ACTION_HC_SETTINGS = "androidx.health.connect.action.HEALTH_CONNECT_SETTINGS"
private const val ACTION_MANAGE_HEALTH_PERMISSIONS = "androidx.health.connect.action.MANAGE_HEALTH_PERMISSIONS"

enum class Dest(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    TODAY("today", R.string.today_label, Icons.Rounded.Today),
    SUMMARY("summary", R.string.summary_title, Icons.Rounded.Insights),
    SETTINGS("settings", R.string.settings_title, Icons.Rounded.Settings),
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
    val currentContext = LocalContext.current

    if (!manager.isAvailable) {
        UnavailableScreen(onInstall = {
            runCatching {
                currentContext.startActivity(
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
    ) { _ ->
        scope.launch {
            granted = manager.grantedPermissions()
            WidgetUpdateWorker.enqueue(currentContext)
        }
    }

    // Re-read grants every time we return to the app (e.g. after toggling in HC settings).
    LifecycleResumeEffect(Unit) {
        scope.launch {
            val next = manager.grantedPermissions()
            if (next != granted) {
                granted = next
                WidgetUpdateWorker.enqueue(currentContext)
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
        permissionLauncher.launch(manager.metricPermissions + manager.writePermissions)
    }

    fun manageAccess() {
        val g = granted ?: emptySet()
        val allNeeded = manager.metricPermissions + manager.writePermissions
        val missing = allNeeded.filter { it !in g }
        
        when {
            // If any core data or write permissions are missing, request the full set.
            // Health Connect will only show toggles for things not yet granted.
            missing.isNotEmpty() -> permissionLauncher.launch(allNeeded)
            
            // Data granted but background (for widgets) missing → add it now.
            HealthConnectManager.PERMISSION_READ_IN_BACKGROUND !in g ->
                permissionLauncher.launch(manager.extraPermissions)

            // Everything granted → open Health Connect's per-app screen (then its home) to review.
            else -> {
                val candidates = listOf(
                    Intent(ACTION_MANAGE_HEALTH_PERMISSIONS).putExtra(Intent.EXTRA_PACKAGE_NAME, currentContext.packageName),
                    Intent(ACTION_HC_SETTINGS),
                )
                for (intent in candidates) {
                    if (runCatching { currentContext.startActivity(intent) }.isSuccess) return
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
    val currentContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = remember { Dest.entries.toList() }
    val showBar =
        currentRoute in topLevel.map { it.route } || currentRoute?.startsWith("detail/") == true

    val pbState = remember { PredictiveBackState() }
    var todayClickCount by remember { mutableIntStateOf(0) }
    var dashboardDateOffset by remember { mutableIntStateOf(0) }
    var isCameraActive by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var isBatchMode by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isHistoryActive by remember { mutableStateOf(false) }
    var capturedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var batchInfoText by remember { mutableStateOf("") }
    var pendingNutrients by remember { mutableStateOf<FoodNutrients?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            uris.forEach { uri ->
                runCatching {
                    currentContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            capturedBitmaps = capturedBitmaps + bitmap
                        }
                    }
                }
            }
            if (uris.isNotEmpty()) {
                isBatchMode = true
                //Toast.makeText(currentContext, "Added ${uris.size} photos to batch", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearCapture() {
        isCameraActive = false
        isTorchOn = false
        isCapturing = false
        isBatchMode = false
        batchInfoText = ""
        capturedBitmaps.forEach { it.recycle() }
        capturedBitmaps = emptyList()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCameraActive = true
        }
    }

    if (isCameraActive) {
        PredictiveBackHandler(enabled = !isHistoryActive) { progress ->
            try {
                progress.collect { }
            } finally {
                clearCapture()
            }
        }
    }

    BackHandler(enabled = isHistoryActive) {
        isHistoryActive = false
    }

    val apiKeyEnabled by settingsRepo.apiKeyEnabled.collectAsStateWithLifecycle(initialValue = false)
    val apiKey by settingsRepo.apiKey.collectAsStateWithLifecycle(initialValue = "")
    val hasApiKey = apiKeyEnabled && apiKey.isNotEmpty()

    val bottomBarHeight = 120.dp
    val bottomBarHeightPx = with(LocalDensity.current) { bottomBarHeight.roundToPx().toFloat() }
    val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentRoute) {
        bottomBarOffsetHeightPx.floatValue = 0f
    }

    val nestedScrollConnection = remember(isCameraActive) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isCameraActive) return Offset.Zero
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

    val blurRadius by animateDpAsState(
        targetValue = if (pendingNutrients != null || isAnalyzing) 16.dp else 0.dp,
        label = "blur_radius"
    )

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
                        dateOffset = dashboardDateOffset,
                        hasApiKey = hasApiKey,
                        isCameraMode = isCameraActive && !isAnalyzing,
                        capturedBitmaps = capturedBitmaps,
                        batchInfoText = batchInfoText,
                        onBatchInfoTextChange = { batchInfoText = it },
                        onSelectGallery = { galleryLauncher.launch("image/*") },
                        onToggleTorch = { isTorchOn = !isTorchOn },
                        onCapture = {
                            isTorchOn = false
                            isBatchMode = false
                            isCapturing = true
                        },
                        onCaptureBatch = {
                            isBatchMode = true
                            isCapturing = true
                        },
                        onFinishBatch = {
                            isTorchOn = false
                            scope.launch {
                                val trimmedKey = apiKey.trim()
                                if (trimmedKey.isBlank()) {
                                    Toast.makeText(
                                        currentContext,
                                        R.string.api_key_missing,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                isAnalyzing = true
                                val result = GeminiManager(trimmedKey).analyzeFoodImages(
                                    capturedBitmaps,
                                    batchInfoText
                                )
                                isAnalyzing = false
                                if (result.isSuccess) {
                                    pendingNutrients = result.getOrNull()
                                    clearCapture()
                                } else {
                                    Toast.makeText(
                                        currentContext,
                                        currentContext.getString(R.string.analysis_failed, result.exceptionOrNull()?.message ?: ""),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        onLogClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        isHistorySelected = isHistoryActive,
                        onHistoryClick = {
                            isHistoryActive = !isHistoryActive
                        },
                        onNavigate = { dest ->
                            if (isCameraActive) clearCapture()
                            isHistoryActive = false
                            if (dest == Dest.TODAY) {
                                if (currentRoute == Dest.TODAY.route) {
                                    todayClickCount++
                                    bottomBarOffsetHeightPx.floatValue = 0f
                                } else if (currentRoute?.startsWith("detail/") == true) {
                                    navController.popBackStack(Dest.TODAY.route, inclusive = false)
                                } else {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            } else {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Dest.TODAY.route,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius),
            ) {
                composable(
                    Dest.TODAY.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    DashboardScreen(
                        manager = manager,
                        goalsRepo = goalsRepo,
                        settingsRepo = settingsRepo,
                        granted = granted,
                        bottomInset = inner.calculateBottomPadding(),
                        todayClickCount = todayClickCount,
                        onOpenMetric = { metric, _ ->
                            navController.navigate("detail/${metric.key}")
                        },
                        onManagePermissions = onManagePermissions,
                        onDateChanged = { 
                            dashboardDateOffset = it
                            bottomBarOffsetHeightPx.floatValue = 0f
                        }
                    )
                }

                composable(
                    Dest.SUMMARY.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    SummaryScreen(
                        manager = manager,
                        goalsRepo = goalsRepo,
                        bottomInset = inner.calculateBottomPadding(),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    Dest.SETTINGS.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    SettingsScreen(
                        manager = manager,
                        exporter = exporter,
                        settingsRepo = settingsRepo,
                        bottomInset = inner.calculateBottomPadding(),
                        onManagePermissions = onManagePermissions,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    "detail/{metricKey}",
                    arguments = listOf(navArgument("metricKey") { type = NavType.StringType }),
                    enterTransition = {
                        scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(250)
                        ) + fadeIn(tween(200))
                    },
                    exitTransition = {
                        scaleOut(
                            targetScale = 0.9f,
                            animationSpec = tween(250)
                        ) + fadeOut(tween(200))
                    }
                ) { entry ->
                    val key = entry.arguments?.getString("metricKey") ?: ""
                    val metric = Metric.fromKey(key) ?: Metric.TOTAL_CALORIES
                    DetailScreen(
                        manager = manager,
                        goalsRepo = goalsRepo,
                        metric = metric,
                        dateOffset = dashboardDateOffset,
                        pbState = pbState,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            if (isCameraActive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAnalyzing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        CameraPreview(
                            modifier = Modifier.blur(blurRadius),
                            isCapturing = isCapturing,
                            isBatchMode = isBatchMode,
                            isTorchOn = isTorchOn,
                            onImageCaptured = { imageProxy ->
                                scope.launch {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val bitmap = imageProxy.toBitmap()
                                    imageProxy.close()

                                    val rotatedBitmap = if (rotation != 0) {
                                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                        val rotated = Bitmap.createBitmap(
                                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                        )
                                        if (rotated != bitmap) bitmap.recycle()
                                        rotated
                                    } else {
                                        bitmap
                                    }

                                    if (isBatchMode) {
                                        capturedBitmaps = capturedBitmaps + rotatedBitmap
                                        isCapturing = false
                                    } else {
                                        val trimmedKey = apiKey.trim()
                                        if (trimmedKey.isBlank()) {
                                            Toast.makeText(
                                                currentContext,
                                                R.string.api_key_invalid,
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isCapturing = false
                                            isCameraActive = false
                                            return@launch
                                        }

                                        isAnalyzing = true
                                        val result = GeminiManager(trimmedKey).analyzeFoodImage(
                                            rotatedBitmap,
                                            batchInfoText
                                        )
                                        isAnalyzing = false

                                        if (result.isSuccess) {
                                            pendingNutrients = result.getOrNull()
                                            clearCapture()
                                        } else {
                                            val errorMsg =
                                                result.exceptionOrNull()?.localizedMessage
                                                    ?: ""
                                            Toast.makeText(
                                                currentContext,
                                                currentContext.getString(R.string.analysis_failed, errorMsg),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isCapturing = false
                                        }
                                    }
                                }
                            }
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isAnalyzing,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f)
                    ) {
                        ScallopedLoadingAnimation(
                            size = androidx.compose.ui.unit.DpSize(
                                150.dp,
                                150.dp
                            )
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isHistoryActive,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                HistoryScreen(
                    manager = manager,
                    onItemSelected = { item ->
                        pendingNutrients = item.nutrients
                    },
                    onBack = { isHistoryActive = false }
                )
            }

            pendingNutrients?.let { nutrients ->
                FoodReviewDialog(
                    nutrients = nutrients,
                    onDismiss = { pendingNutrients = null },
                    onLog = { nuts, count, timestamp ->
                        scope.launch {
                            try {
                                manager.writeNutrition(nuts, count, timestamp)
                                pendingNutrients = null
                                //Toast.makeText(currentContext, "Food logged successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                if (e.message?.contains("permission", ignoreCase = true) == true ||
                                    e.cause?.message?.contains(
                                        "permission",
                                        ignoreCase = true
                                    ) == true
                                ) {
                                    Log.d(
                                        "MainNav",
                                        "Write failed due to permission. Requesting..."
                                    )
                                    Toast.makeText(
                                        currentContext,
                                        R.string.write_permission_required,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onManagePermissions()
                                } else {
                                    Log.e("MainNav", "Failed to log food", e)
                                    Toast.makeText(
                                        currentContext,
                                        R.string.failed_to_log_food,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
