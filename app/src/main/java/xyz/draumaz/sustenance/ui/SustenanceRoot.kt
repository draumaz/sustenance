package xyz.draumaz.sustenance.ui

import android.content.Intent
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import xyz.draumaz.sustenance.ui.components.ExpressiveSprings
import xyz.draumaz.sustenance.ui.components.PaneMargin
import xyz.draumaz.sustenance.ui.components.LocalFlyInDestination
import xyz.draumaz.sustenance.ui.components.LocalFlyInScope
import xyz.draumaz.sustenance.ui.components.flyDetailEnter
import xyz.draumaz.sustenance.ui.components.flyDetailExit
import xyz.draumaz.sustenance.ui.components.flyListEnter
import xyz.draumaz.sustenance.ui.components.flyListExit
import xyz.draumaz.sustenance.ui.components.isClosingMetric
import xyz.draumaz.sustenance.ui.components.isOpeningMetric
import xyz.draumaz.sustenance.ui.components.metricFlyIn
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.shape.RoundedCornerShape
import xyz.draumaz.sustenance.ui.components.Panes
import xyz.draumaz.sustenance.ui.components.rememberPaneSplit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.animation.AnimatedContent
import xyz.draumaz.sustenance.ui.components.ScreenCard
import xyz.draumaz.sustenance.ui.components.isOpeningCard
import xyz.draumaz.sustenance.ui.components.pushEnterCard
import xyz.draumaz.sustenance.ui.components.pushExitUnder
import xyz.draumaz.sustenance.ui.components.popEnterBack
import xyz.draumaz.sustenance.ui.components.popExitBack
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.time.Instant
import androidx.health.connect.client.PermissionController
import androidx.activity.compose.PredictiveBackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.GoalsRepository
import xyz.draumaz.sustenance.data.HealthConnectManager
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.SettingsRepository
import xyz.draumaz.sustenance.ui.components.BINGE_CHECK_WINDOW
import xyz.draumaz.sustenance.ui.components.BingeCheckDialog
import xyz.draumaz.sustenance.ui.components.ExpressiveNavigationBar
import xyz.draumaz.sustenance.ui.components.CameraPreview
import xyz.draumaz.sustenance.ui.components.PredictiveBackState
import xyz.draumaz.sustenance.ui.components.ScallopedLoadingAnimation
import xyz.draumaz.sustenance.ui.dashboard.DashboardScreen
import xyz.draumaz.sustenance.ui.detail.DetailScreen
import xyz.draumaz.sustenance.ui.onboarding.GoalSetupScreen
import xyz.draumaz.sustenance.ui.onboarding.LoadingScreen
import xyz.draumaz.sustenance.ui.onboarding.OnboardingScreen
import xyz.draumaz.sustenance.ui.onboarding.UnavailableScreen
import xyz.draumaz.sustenance.ui.settings.SettingsScreen
import xyz.draumaz.sustenance.ui.summary.InsightsScreen
import xyz.draumaz.sustenance.ui.components.FoodReviewDialog
import xyz.draumaz.sustenance.ui.history.HistoryScreen
import xyz.draumaz.sustenance.util.FoodNutrients
import xyz.draumaz.sustenance.util.GeminiQuotaException
import xyz.draumaz.sustenance.util.GeminiManager
import xyz.draumaz.sustenance.widget.WidgetUpdateWorker

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

private fun decodeDownsampledBitmap(context: android.content.Context, uri: Uri, maxDim: Int = 1024): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            
            var inSampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize >= maxDim) && (halfWidth / inSampleSize >= maxDim)) {
                    inSampleSize *= 2
                }
            }
            
            context.contentResolver.openInputStream(uri)?.use { finalInput ->
                val finalOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                BitmapFactory.decodeStream(finalInput, null, finalOptions)
            }
        }
    } catch (e: Exception) {
        Log.e("SustenanceRoot", "Failed to decode downsampled bitmap", e)
        null
    }
}

enum class Dest(val route: String, @param:StringRes val labelRes: Int, val icon: ImageVector) {
    TODAY("today", R.string.today_label, Icons.Rounded.Today),
    INSIGHTS("insights", R.string.summary_title, Icons.Rounded.Insights),
    SETTINGS("settings", R.string.settings_title, Icons.Rounded.Settings),
}

@Composable
fun SustenanceRoot(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: SettingsRepository,
    deepLinkMetric: String? = null,
    sharedImageUris: List<Uri>? = null,
    launchLog: Boolean = false,
    onDeepLinkConsumed: () -> Unit = {},
    onSharedImagesConsumed: () -> Unit = {},
    onLogConsumed: () -> Unit = {},
) {
    val currentContext = LocalContext.current

    if (!manager.isAvailable) {
        UnavailableScreen {
            runCatching {
                currentContext.startActivity(
                    Intent(Intent.ACTION_VIEW, "market://details?id=$HEALTH_CONNECT_PACKAGE".toUri())
                        .setPackage("com.android.vending")
                )
            }
        }
        return
    }

    var granted by remember { mutableStateOf<Set<String>?>(null) }
    // True while the initial setup is chaining its permission requests (data → background).
    var inSetup by remember { mutableStateOf(value = false) }
    var requestedExtras by remember { mutableStateOf(value = false) }
    // Bumped once per permission result during setup. Declining leaves `granted` unchanged, so
    // the setup step can't key on it alone or it would never advance past the spinner.
    var setupResults by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        scope.launch {
            granted = manager.grantedPermissions()
            if (inSetup) setupResults++
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

    // Null until DataStore answers, so a pending goal step never flashes the dashboard first.
    val goalSetupPending by settingsRepo.goalSetupPending.collectAsState<Boolean, Boolean?>(initial = null)
    val currentGoals by goalsRepo.goals.collectAsState(initial = null)
    val goalsToEdit = currentGoals

    // Setup is one continuous flow: after data is granted, immediately chain the background +
    // history prompt (HC won't allow it in the same request), so widgets work straight away.
    LaunchedEffect(setupResults) {
        if (!inSetup || setupResults == 0) return@LaunchedEffect
        when {
            !hasData -> inSetup = false              // user declined data; back to onboarding
            !hasBackground && !requestedExtras -> {  // data in, now ask for background once
                requestedExtras = true
                permissionLauncher.launch(manager.extraPermissions)
            }
            else -> {                                // background resolved → enter the app
                settingsRepo.setGoalSetupPending(true)
                // Wait for the flag to reach the UI so the dashboard doesn't flash before goals.
                snapshotFlow { goalSetupPending }.first { it == true }
                inSetup = false
            }
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
                val candidates = listOfNotNull(
                    HealthConnectManager.managePermissionsIntent(currentContext.packageName),
                    HealthConnectManager.settingsIntent(),
                )
                for (intent in candidates) {
                    if (runCatching { currentContext.startActivity(intent) }.isSuccess) return
                }
                permissionLauncher.launch(manager.permissions)
            }
        }
    }

    when {
        granted == null || inSetup || goalSetupPending == null -> LoadingScreen()
        !hasData -> OnboardingScreen(onConnect = ::startSetup)
        // Last step of setup: set goals before the dashboard ever loads.
        goalSetupPending == true -> if (goalsToEdit == null) LoadingScreen() else GoalSetupScreen(
            initial = goalsToEdit,
            netCarbMode = settingsRepo.ketoMode.collectAsState(initial = false).value,
            onSave = { values ->
                scope.launch {
                    goalsRepo.setGoals(values)
                    settingsRepo.finishGoalSetup(deferred = false)
                }
            },
            onSkip = { scope.launch { settingsRepo.finishGoalSetup(deferred = true) } },
        )
        else -> {
            MainNav(
                manager, goalsRepo, settingsRepo,
                granted = granted ?: emptySet(),
                onManagePermissions = ::manageAccess,
                deepLinkMetric = deepLinkMetric,
                sharedImageUris = sharedImageUris,
                launchLog = launchLog,
                onDeepLinkConsumed = onDeepLinkConsumed,
                onSharedImagesConsumed = onSharedImagesConsumed,
                onLogConsumed = onLogConsumed
            )
        }
    }
}

/** A log held back while the binge check-in is showing. */
private data class BingeCheck(
    val nutrients: FoodNutrients,
    val servingCount: Double,
    val timestamp: Instant,
    val priorLogs: Int,
)

@Composable
private fun MainNav(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: SettingsRepository,
    granted: Set<String>,
    onManagePermissions: () -> Unit,
    deepLinkMetric: String? = null,
    sharedImageUris: List<Uri>? = null,
    launchLog: Boolean = false,
    onDeepLinkConsumed: () -> Unit = {},
    onSharedImagesConsumed: () -> Unit = {},
    onLogConsumed: () -> Unit = {},
) {
    val currentContext = LocalContext.current
    val appContext = currentContext.applicationContext
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = remember { Dest.entries.toList() }
    val showBar =
        currentRoute == Dest.TODAY.route || 
        currentRoute == Dest.INSIGHTS.route || 
        currentRoute?.startsWith(Dest.SETTINGS.route) == true || 
        currentRoute?.startsWith("detail/") == true

    // Wide windows and unfolded foldables put a metric's detail in a pane beside Today and
    // Insights, instead of on a screen of its own: tapping a tile picks what the pane shows.
    val split = rememberPaneSplit()
    // Side by side, the bar and camera keep to the list's pane. Stacked across a hinge they
    // stay full width, over the bottom (detail) pane, clear of the crease either way.
    val sideBySide = split?.takeIf { !it.stacked }
    val onSplitRoute = currentRoute == Dest.TODAY.route || currentRoute == Dest.INSIGHTS.route
    var selectedMetricKey by rememberSaveable { mutableStateOf(Metric.FOOD.key) }
    val selectedMetric = Metric.fromKey(selectedMetricKey) ?: Metric.FOOD
    // Which pane the last touch landed in. Scrolling the detail pane shouldn't tuck away the
    // bar, which sits under the other pane and isn't in the way.
    var detailPaneTouched by remember { mutableStateOf(false) }

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
    var historyQuery by remember { mutableStateOf("") }
    var pendingNutrients by remember { mutableStateOf<FoodNutrients?>(null) }

    val goals by goalsRepo.goals.collectAsState(initial = emptyMap())
    val judgementalMode by settingsRepo.judgementalMode.collectAsState(initial = false)
    val bingeCheckEnabled by settingsRepo.bingeCheckEnabled.collectAsState(initial = false)
    val alwaysBatch by settingsRepo.alwaysBatch.collectAsState(initial = true)
    var bingeCheck by remember { mutableStateOf<BingeCheck?>(null) }
    val gramIncrementEnabled by settingsRepo.gramIncrementEnabled.collectAsState(initial = false)
    val gramIncrement by settingsRepo.gramIncrement.collectAsState(initial = null)
    val ketoMode by settingsRepo.ketoMode.collectAsState(initial = false)
    val currentTotals by produceState(initialValue = emptyMap<Metric, Float>(), goals, ketoMode, manager) {
        suspend fun update() {
            val dashboard = manager.readDashboard(goals, ketoMode, 0)
            value = dashboard.associateBy({ it.metric }, { it.spark.lastOrNull() ?: 0f })
        }
        update()
        manager.changes.collect { update() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            capturedBitmaps += withContext(Dispatchers.IO) {
                uris.mapNotNull { decodeDownsampledBitmap(currentContext, it) }
            }
            if (uris.isNotEmpty()) {
                isBatchMode = true
            }
        }
    }

    var analysisJob by remember { mutableStateOf<Job?>(null) }

    /** Closes the camera and discards any in-progress capture or analysis. */
    fun resetCamera() {
        analysisJob?.cancel()
        analysisJob = null
        isAnalyzing = false
        isCameraActive = false
        isTorchOn = false
        isCapturing = false
        isBatchMode = false
        batchInfoText = ""
        capturedBitmaps.forEach { it.recycle() }
        capturedBitmaps = emptyList()
    }

    /** Closes the camera and history overlays and returns to Today, discarding any capture. */
    fun clearCapture() {
        isHistoryActive = false
        historyQuery = ""
        resetCamera()
        pendingNutrients = null
        navController.popBackStack(Dest.TODAY.route, inclusive = false)
    }

    suspend fun logFood(nutrients: FoodNutrients, servingCount: Double, timestamp: Instant) {
        try {
            manager.writeNutrition(nutrients, servingCount, timestamp)
            pendingNutrients = null
            // A re-log from History is done once it's written: show it landing on Today rather
            // than leaving the user in the list. Only on success, so a failed write can be retried.
            if (isHistoryActive) clearCapture()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MainNav", "Failed to log food", e)
            reportHealthWriteFailure(
                currentContext, e, R.string.failed_to_log_food, onManagePermissions
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCameraActive = true
            // Only the mode it opens in: the single-shot button still takes a lone photo.
            if (alwaysBatch) isBatchMode = true
        }
    }

    // Back closes the camera (and the history overlay opened from it) straight to Today,
    // previewing via pbState. Batch mode included: it never stops at single-shot capture on the
    // way out. clearCapture cancels any analysis before recycling the batch Gemini was reading.
    PredictiveBackHandler(enabled = isCameraActive || isHistoryActive) { progress ->
        pbState.isSwipeActive = true
        try {
            progress.collect { event -> pbState.progress = event.progress }
            clearCapture()
        } catch (_: CancellationException) {
            // Gesture cancelled; stay where we are.
        } finally {
            pbState.isSwipeActive = false
            pbState.progress = 0f
        }
    }

    val apiKeyEnabled by settingsRepo.apiKeyEnabled.collectAsStateWithLifecycle(initialValue = false)
    val apiKey by settingsRepo.apiKey.collectAsStateWithLifecycle(initialValue = "")
    val geminiModel by settingsRepo.geminiModel.collectAsStateWithLifecycle(initialValue = GeminiManager.DEFAULT_MODEL)
    val hasApiKey = apiKeyEnabled && apiKey.isNotEmpty()

    /**
     * Sends [photos] (plus any typed context) to Gemini. On success the camera closes and the review
     * dialog opens; on failure the camera stays open so the user can retry.
     */
    suspend fun analyzePhotos(photos: List<Bitmap>) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            Toast.makeText(currentContext, R.string.api_key_missing, Toast.LENGTH_SHORT).show()
            isAnalyzing = false
            isCapturing = false
            analysisJob = null
            return
        }
        isAnalyzing = true
        val result = GeminiManager(key, GeminiManager.modelName(geminiModel))
            .analyzeFoodImages(photos, batchInfoText)
        isAnalyzing = false
        isCapturing = false
        analysisJob = null
        result
            .onSuccess { nutrients ->
                resetCamera()
                pendingNutrients = nutrients
            }
            .onFailure { error ->
                val message = error.localizedMessage.orEmpty()
                val toastMsg = if (error is GeminiQuotaException) {
                    appContext.getString(R.string.gemini_quota_reached)
                } else if (message.isBlank() || message.contains("No response", ignoreCase = true)) {
                    appContext.getString(R.string.no_response_from_gemini)
                } else {
                    appContext.getString(R.string.analysis_failed, message)
                }
                Toast.makeText(currentContext, toastMsg, Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Opens [metric]'s detail: in the pane beside the list when there is one, else on its own
     * screen. [offset] pins the day (Insights always shows today); null follows the dashboard's.
     */
    fun openMetric(metric: Metric, offset: Int? = null) {
        if (split != null) {
            selectedMetricKey = metric.key
        } else {
            navController.navigate("detail/${metric.key}" + (offset?.let { "?offset=$it" } ?: ""))
        }
    }

    // Unfolding (or widening the window) with a detail open: fold it back into the pane beside
    // the list it was opened from, so the screen doesn't stay a lone detail with room to spare.
    LaunchedEffect(split != null, currentRoute) {
        if (split == null || currentRoute?.startsWith("detail/") != true) return@LaunchedEffect
        backStack?.arguments?.getString("metricKey")?.let { selectedMetricKey = it }
        navController.popBackStack()
    }

    val bottomBarHeight = 120.dp
    val bottomBarHeightPx = with(LocalDensity.current) { bottomBarHeight.roundToPx().toFloat() }
    val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentRoute, isHistoryActive) {
        bottomBarOffsetHeightPx.floatValue = 0f
    }

    val nestedScrollConnection = remember(isCameraActive, isHistoryActive, detailPaneTouched) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isCameraActive && !isHistoryActive) return Offset.Zero
                if (detailPaneTouched) return Offset.Zero
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx.floatValue + delta
                bottomBarOffsetHeightPx.floatValue = newOffset.coerceIn(-bottomBarHeightPx, 0f)
                return super.onPreScroll(available, source)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isCameraActive && !isHistoryActive) return super.onPostFling(consumed, available)
                bottomBarOffsetHeightPx.floatValue = 0f
                return super.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(sharedImageUris) {
        sharedImageUris?.let { uris ->
            isCameraActive = true
            isBatchMode = true
            capturedBitmaps += withContext(Dispatchers.IO) {
                uris.mapNotNull { decodeDownsampledBitmap(currentContext, it) }
            }
            onSharedImagesConsumed()
        }
    }

    // Open a metric's detail directly when launched from its widget.
    LaunchedEffect(deepLinkMetric) {
        val metric = deepLinkMetric?.let { Metric.fromKey(it) }
        if (metric != null) {
            val isGranted = manager.permissionFor(metric) in granted
            if (isGranted) {
                if (split != null) navController.popBackStack(Dest.TODAY.route, inclusive = false)
                openMetric(metric)
            } else {
                onManagePermissions()
            }
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(launchLog) {
        if (launchLog) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            onLogConsumed()
        }
    }

    val rootBlur by animateDpAsState(
        targetValue = if (isCameraActive || isAnalyzing || pendingNutrients != null) 16.dp else 0.dp,
        animationSpec = if (!isCameraActive && !isAnalyzing && pendingNutrients == null) tween(0) else spring(),
        label = "root_blur"
    )

    val cameraBlur by animateDpAsState(
        targetValue = if (isAnalyzing || pendingNutrients != null) 16.dp else 0.dp,
        animationSpec = if (!isAnalyzing && pendingNutrients == null) tween(0) else spring(),
        label = "camera_blur"
    )

    // The share of the window the bar (and the camera and History it opens into) spans: all of
    // it, or the list's pane on a split screen. Animated, so switching to Settings, which has
    // no split, glides the bar back to the middle rather than jumping it there.
    val barFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (sideBySide != null && onSplitRoute) {
            sideBySide.start / (sideBySide.start + sideBySide.gap + sideBySide.end)
        } else 1f,
        animationSpec = ExpressiveSprings.defaultSpatial(),
        label = "bar_fraction",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        bottomBar = {
            if (showBar) {
                val animatedOffset by animateIntAsState(
                    targetValue = bottomBarOffsetHeightPx.floatValue.roundToInt(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "bottom_bar_offset"
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = -animatedOffset) }
                        .fillMaxWidth(barFraction)
                ) {
                    ExpressiveNavigationBar(
                        navController = navController,
                        destinations = topLevel,
                        predictiveBackState = pbState,
                        dateOffset = dashboardDateOffset,
                        hasApiKey = hasApiKey,
                        isCameraMode = isCameraActive && !isAnalyzing,
                        isBatchMode = isBatchMode,
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
                            if (!isBatchMode) {
                                isBatchMode = true
                            } else {
                                isCapturing = true
                            }
                        },
                        onFinishBatch = {
                            isTorchOn = false
                            // The analysis spinner lives in the camera overlay, which History hides.
                            isHistoryActive = false
                            analysisJob = scope.launch { analyzePhotos(capturedBitmaps) }
                        },
                        onLogClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        isHistorySelected = isHistoryActive,
                        historyQuery = historyQuery,
                        onHistoryQueryChange = { historyQuery = it },
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
                                } else {
                                    // Use popBackStack for consistent return to the root 'Today' screen.
                                    // This handles detail screens and settings regardless of parameters.
                                    navController.popBackStack(Dest.TODAY.route, inclusive = false)
                                }
                            } else if (
                                currentRoute?.startsWith("detail/") == true &&
                                navController.previousBackStackEntry?.destination?.route == dest.route
                            ) {
                                // A detail opened from this tab: go back to it rather than
                                // restoring a saved stack that still has the detail on top.
                                navController.popBackStack()
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
          // Tiles fly open into their details on phones (see MetricFlyIn.kt). The blur sits on
          // this layout rather than the NavHost so a tile mid-flight, drawn in its overlay, blurs too.
          SharedTransitionLayout(Modifier.fillMaxSize().blur(rootBlur)) {
           CompositionLocalProvider(LocalFlyInScope provides this.takeIf { split == null }) {
            NavHost(
                navController = navController,
                startDestination = Dest.TODAY.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(
                    Dest.TODAY.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { listExit() },
                    popEnterTransition = { listPopEnter() },
                    popExitTransition = popExitBack,
                ) {
                  CompositionLocalProvider(LocalFlyInDestination provides this) {
                    ScreenCard {
                      SplitWithDetail(
                        split = split,
                        onTouch = { detailPaneTouched = it },
                        detail = {
                            MetricDetailPane(
                                manager, goalsRepo, selectedMetric, dashboardDateOffset,
                                onReLog = { pendingNutrients = it },
                                onManagePermissions = onManagePermissions,
                            )
                        },
                      ) {
                        DashboardScreen(
                            manager = manager,
                            goalsRepo = goalsRepo,
                            settingsRepo = settingsRepo,
                            granted = granted,
                            // Stacked, the bar floats over the bottom (detail) pane, not this one.
                            bottomInset = if (split?.stacked == true) 0.dp else inner.calculateBottomPadding(),
                            todayClickCount = todayClickCount,
                            selectedMetric = selectedMetric.takeIf { split != null },
                            roomy = split != null,
                            onOpenMetric = { metric, _ -> openMetric(metric) },
                            onTimerClick = {
                                navController.navigate("settings?scrollTo=fasting")
                            },
                            onManagePermissions = onManagePermissions,
                            onDateChanged = { 
                                dashboardDateOffset = it
                                bottomBarOffsetHeightPx.floatValue = 0f
                            }
                        )
                      }
                    }
                  }
                }

                composable(
                    Dest.INSIGHTS.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { listExit() },
                    popEnterTransition = { listPopEnter() },
                    popExitTransition = popExitBack,
                ) {
                  CompositionLocalProvider(LocalFlyInDestination provides this) {
                    ScreenCard {
                      SplitWithDetail(
                        split = split,
                        onTouch = { detailPaneTouched = it },
                        detail = {
                            MetricDetailPane(
                                manager, goalsRepo, selectedMetric, dateOffset = 0,
                                onReLog = { pendingNutrients = it },
                                onManagePermissions = onManagePermissions,
                            )
                        },
                      ) {
                        InsightsScreen(
                            manager = manager,
                            goalsRepo = goalsRepo,
                            settingsRepo = settingsRepo,
                            bottomInset = if (split?.stacked == true) 0.dp else inner.calculateBottomPadding(),
                            // Insights always reflects today, so pin the detail view to today
                            // rather than whichever day the Dashboard was last showing.
                            onOpenMetric = { metric -> openMetric(metric, offset = 0) },
                            onBack = { navController.popBackStack() },
                            selectedMetric = selectedMetric.takeIf { split != null },
                        )
                      }
                    }
                  }
                }

                composable(
                    Dest.SETTINGS.route + "?scrollTo={scrollTo}",
                    arguments = listOf(navArgument("scrollTo") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }),
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { if (isOpeningCard()) pushExitUnder() else fadeOut(tween(200)) },
                    popEnterTransition = popEnterBack,
                    popExitTransition = popExitBack,
                ) { entry ->
                    ScreenCard {
                        SettingsScreen(
                            settingsRepo = settingsRepo,
                            bottomInset = inner.calculateBottomPadding(),
                            onManagePermissions = onManagePermissions,
                            onEditGoals = { navController.navigate("goals") },
                            onBack = { navController.popBackStack() },
                            scrollTo = entry.arguments?.getString("scrollTo")
                        )
                    }
                }

                composable(
                    "goals",
                    enterTransition = pushEnterCard,
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = popEnterBack,
                    popExitTransition = popExitBack,
                ) {
                    ScreenCard {
                        val goals by goalsRepo.goals.collectAsState(initial = null)
                        goals?.let { initial ->
                            GoalSetupScreen(
                                initial = initial,
                                editing = true,
                                netCarbMode = ketoMode,
                                onSave = { values ->
                                    scope.launch {
                                        goalsRepo.setGoals(values)
                                        // Also clears the "using default targets" prompt if setup was skipped.
                                        settingsRepo.finishGoalSetup(deferred = false)
                                    }
                                    navController.popBackStack()
                                },
                                onSkip = { navController.popBackStack() },
                            )
                        }
                    }
                }

                composable(
                    "detail/{metricKey}?offset={offset}",
                    arguments = listOf(
                        navArgument("metricKey") { type = NavType.StringType },
                        // Optional day override; absent means follow the Dashboard's selected day.
                        navArgument("offset") { type = NavType.IntType; defaultValue = -1 },
                    ),
                    enterTransition = { if (split == null) flyDetailEnter() else pushEnterCard() },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = popEnterBack,
                    popExitTransition = { if (split == null) flyDetailExit() else popExitBack() },
                ) { entry ->
                    val key = entry.arguments?.getString("metricKey") ?: ""
                    val metric = Metric.fromKey(key) ?: Metric.TOTAL_CALORIES
                  CompositionLocalProvider(LocalFlyInDestination provides this) {
                   // Opaque, so the list behind doesn't show through while it flies open.
                   Box(Modifier.fillMaxSize().metricFlyIn(metric, RoundedCornerShape(16.dp))) {
                    ScreenCard {
                        val offsetArg = entry.arguments?.getInt("offset") ?: -1
                        DetailScreen(
                            manager = manager,
                            goalsRepo = goalsRepo,
                            metric = metric,
                            dateOffset = if (offsetArg >= 0) offsetArg else dashboardDateOffset,
                            onReLog = { pendingNutrients = it },
                            onManagePermissions = onManagePermissions,
                            onBack = { navController.popBackStack() }
                        )
                    }
                   }
                  }
                }
            }
           }
          }

            // Interaction Shield for blurred background
            if (rootBlur > 0.dp) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { /* Block interactions */ }
                        }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isCameraActive && !isHistoryActive && pendingNutrients == null,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { (it * 0.4f).toInt() } +
                        scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0f, transformOrigin = TransformOrigin(0.5f, 0.9f)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { (it * 0.4f).toInt() } +
                        scaleOut(spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0f, transformOrigin = TransformOrigin(0.5f, 0.9f))
            ) {
                // resetCamera() drops batch mode the moment the camera closes, while this overlay
                // is still animating out. Hold the last open value so the gallery scallop leaves
                // with the flashlight instead of vanishing before the exit plays.
                val exiting = transition.targetState == EnterExitState.PostExit
                var heldBatchMode by remember { mutableStateOf(isBatchMode) }
                SideEffect { if (!exiting) heldBatchMode = isBatchMode }
                val showGallery = if (exiting) heldBatchMode else isBatchMode
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barFraction)
                        .padding(
                            top = inner.calculateTopPadding(),
                            // Track the bar's real height so the viewfinder moves up with it
                            // when the keyboard lifts the bar in batch mode.
                            bottom = maxOf(bottomBarHeight, inner.calculateBottomPadding())
                        )
                        .graphicsLayer {
                            alpha = 1f - pbState.progress
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isAnalyzing,
                        transitionSpec = {
                            if (targetState) {
                                (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                 scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)))
                                    .togetherWith(
                                        fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                        scaleOut(targetScale = 0.4f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                                    )
                                    .using(SizeTransform(clip = false) { _, _ ->
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                    })
                            } else {
                                (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                 scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)))
                                    .togetherWith(
                                        fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                        scaleOut(targetScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                    )
                                    .using(SizeTransform(clip = false) { _, _ ->
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                    })
                            }
                        },
                        label = "camera_to_analyzing"
                    ) { analyzing ->
                        if (analyzing) {
                            ScallopedLoadingAnimation(
                                size = DpSize(
                                    200.dp,
                                    200.dp
                                )
                            )
                        } else {
                            Box {
                                CameraPreview(
                                    modifier = Modifier.blur(cameraBlur),
                                    isCapturing = isCapturing,
                                    isBatchMode = isBatchMode,
                                    isTorchOn = isTorchOn,
                                    onToggleTorch = { isTorchOn = !isTorchOn },
                                    // Batch mode's toolbar slot went to History; the gallery
                                    // moved into the viewfinder, beside the flashlight.
                                    showGallery = showGallery,
                                    onSelectGallery = { galleryLauncher.launch("image/*") },
                                    onImageCaptured = { imageProxy ->
                                        // Snapshot the mode: closing the camera while this shot
                                        // processes must drop it, not analyze it as a single.
                                        val batchShot = isBatchMode
                                        if (!batchShot) {
                                            isAnalyzing = true
                                        }
                                        analysisJob = scope.launch(Dispatchers.Default) {
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

                                            withContext(Dispatchers.Main) {
                                                if (batchShot) {
                                                    if (isBatchMode) capturedBitmaps += rotatedBitmap
                                                    else rotatedBitmap.recycle()
                                                    isCapturing = false
                                                } else {
                                                    analyzePhotos(listOf(rotatedBitmap))
                                                }
                                            }
                                        }
                                    }
                                )
                                if (cameraBlur > 0.dp) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .pointerInput(Unit) {
                                                detectTapGestures { /* Block interactions */ }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isHistoryActive,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(barFraction),
            ) {
                HistoryScreen(
                    manager = manager,
                    settingsRepo = settingsRepo,
                    bottomInset = inner.calculateBottomPadding(),
                    query = historyQuery,
                    predictiveBackProgress = if (isHistoryActive) pbState.progress else 0f,
                    onItemSelected = { item ->
                        pendingNutrients = item.nutrients
                    },
                    onBack = { clearCapture() }
                )
            }

            pendingNutrients?.let { nutrients ->
                FoodReviewDialog(
                    nutrients = nutrients,
                    onDismiss = { pendingNutrients = null },
                    judgementalMode = judgementalMode,
                    gramIncrementEnabled = gramIncrementEnabled,
                    gramIncrement = gramIncrement,
                    currentTotals = currentTotals,
                    goals = goals,
                    onLog = { nuts, count, timestamp ->
                        scope.launch {
                            val priorLogs = if (bingeCheckEnabled) {
                                manager.countLogsOf(nuts.foodItem, timestamp, BINGE_CHECK_WINDOW)
                            } else 0
                            if (priorLogs > 0) {
                                bingeCheck = BingeCheck(nuts, count, timestamp, priorLogs)
                            } else {
                                logFood(nuts, count, timestamp)
                            }
                        }
                    }
                )
            }

            bingeCheck?.let { check ->
                BingeCheckDialog(
                    foodName = check.nutrients.foodItem,
                    priorLogs = check.priorLogs,
                    onLog = {
                        bingeCheck = null
                        scope.launch { logFood(check.nutrients, check.servingCount, check.timestamp) }
                    },
                    onHoldOff = {
                        bingeCheck = null
                        pendingNutrients = null
                    },
                    onDismiss = { bingeCheck = null }
                )
            }
        }
    }
}
}

/**
 * [list] alone on a phone, or beside a [detail] pane on a split screen. Reports which pane each
 * touch (or mouse wheel) starts in through [onTouch]: true for the detail.
 */
@Composable
private fun SplitWithDetail(
    split: xyz.draumaz.sustenance.ui.components.PaneSplit?,
    onTouch: (inDetail: Boolean) -> Unit,
    detail: @Composable () -> Unit,
    list: @Composable () -> Unit,
) {
    if (split == null) {
        list()
        return
    }
    Panes(
        split = split,
        // Margins on the window's outer edges (and the top, unless the pane starts below a
        // hinge): the gap between the panes already has each screen's own 16dp on both sides.
        start = {
            Box(
                Modifier.fillMaxSize()
                    .padding(start = PaneMargin, top = PaneMargin, end = if (split.stacked) PaneMargin else 0.dp)
                    .onPointerStart { onTouch(false) }
            ) { list() }
        },
        end = {
            Box(
                Modifier.fillMaxSize()
                    .padding(
                        start = if (split.stacked) PaneMargin else 0.dp,
                        top = if (split.stacked) 0.dp else PaneMargin,
                        end = PaneMargin,
                    )
                    .onPointerStart { onTouch(true) }
            ) { detail() }
        },
    )
}

/** Calls [onStart] as a press or wheel scroll begins anywhere within, without consuming it. */
private fun Modifier.onPointerStart(onStart: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) onStart()
        }
    }
}

/**
 * The detail pane: [metric]'s detail for the day [dateOffset] days back, crossfading as another
 * tile is picked. No back arrow: the list it belongs to is right beside it.
 */
@Composable
private fun MetricDetailPane(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    metric: Metric,
    dateOffset: Int,
    onReLog: (FoodNutrients) -> Unit,
    onManagePermissions: () -> Unit,
) {
    // Keep the current detail on screen until the tapped one has its data, so the pane goes
    // straight from one filled detail to the next instead of through a spinner and a pop-in.
    // The view model made here is the one DetailScreen gets: same owner, same key.
    val requested = metric to dateOffset
    var shown by remember { mutableStateOf(requested) }
    val incoming: DetailViewModel = viewModel(
        key = "${metric.key}_$dateOffset",
        factory = DetailViewModel.factory(manager, goalsRepo, metric, dateOffset),
    )
    LaunchedEffect(requested) {
        if (requested == shown) return@LaunchedEffect
        // A slow Health Connect read still switches soon, to the spinner, so a tap never
        // feels ignored.
        withTimeoutOrNull(DetailHoldMillis) { incoming.detail.first { it != null } }
        shown = requested
    }
    val slide = with(LocalDensity.current) { DetailSlide.roundToPx() }
    AnimatedContent(
        targetState = shown,
        transitionSpec = {
            // From below for a metric further down the dashboard (or a later day, which comes
            // up as the pull to the previous one comes down), from above for the reverse.
            val (fromMetric, fromDay) = initialState
            val (toMetric, toDay) = targetState
            val down = if (toMetric != fromMetric) toMetric.ordinal > fromMetric.ordinal else toDay < fromDay
            val dir = if (down) 1 else -1
            // Material's fade through: the old detail is gone before the new one starts to
            // appear, so two details' charts and numbers never show misaligned on top of each
            // other, and the new one fades in over its slide so it's opaque as it lands.
            (fadeIn(tween(DetailFadeInMillis, delayMillis = DetailFadeOutMillis, easing = LinearOutSlowInEasing)) +
                slideInVertically(spring(DetailSwapDamping, DetailSwapStiffness)) { dir * slide })
                .togetherWith(fadeOut(tween(DetailFadeOutMillis, easing = FastOutLinearInEasing)))
        },
        label = "detail_pane",
    ) { (shownMetric, shownDay) ->
        DetailScreen(
            manager = manager,
            goalsRepo = goalsRepo,
            metric = shownMetric,
            dateOffset = shownDay,
            onReLog = onReLog,
            onManagePermissions = onManagePermissions,
            onBack = null,
        )
    }
}

/** The longest the pane keeps the previous detail while the next one loads. */
private const val DetailHoldMillis = 600L

/** How far a detail travels as it swaps in: enough to read as direction, not as a page turn. */
private val DetailSlide = 32.dp

/** The incoming detail's slide: no overshoot, settled in about a quarter second. */
private const val DetailSwapDamping = 1f
private const val DetailSwapStiffness = 700f

/** Fade-through timings (Material's 90 out, then 210 in). */
private const val DetailFadeOutMillis = 90
private const val DetailFadeInMillis = 210

/*
 * Today's and Insights' transitions around a metric's detail. On a phone the tapped tile flies
 * open into the detail, so the list only recedes behind it (and returns as the detail shrinks
 * back); the goals editor still pushes in as a card. Split layouts never open a detail route
 * for long (it folds into the pane), so they keep the card motions.
 */
private fun androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.listExit() =
    when {
        isOpeningMetric() -> flyListExit()
        isOpeningCard() -> pushExitUnder()
        else -> fadeOut(tween(200))
    }

private fun androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.listPopEnter() =
    if (isClosingMetric()) flyListEnter() else popEnterBack()
