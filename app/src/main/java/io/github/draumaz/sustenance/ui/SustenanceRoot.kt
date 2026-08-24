package io.github.draumaz.sustenance.ui

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.github.draumaz.sustenance.R
import io.github.draumaz.sustenance.data.ExportManager
import io.github.draumaz.sustenance.data.GoalsRepository
import io.github.draumaz.sustenance.data.HealthConnectManager
import io.github.draumaz.sustenance.data.Metric
import io.github.draumaz.sustenance.data.SettingsRepository
import io.github.draumaz.sustenance.ui.components.ExpressiveNavigationBar
import io.github.draumaz.sustenance.ui.components.FoodReviewDialog
import io.github.draumaz.sustenance.ui.components.CameraPreview
import io.github.draumaz.sustenance.ui.components.PredictiveBackState
import io.github.draumaz.sustenance.ui.components.ScallopedLoadingAnimation
import io.github.draumaz.sustenance.ui.dashboard.DashboardScreen
import io.github.draumaz.sustenance.ui.detail.DetailScreen
import io.github.draumaz.sustenance.ui.history.HistoryScreen
import io.github.draumaz.sustenance.ui.onboarding.LoadingScreen
import io.github.draumaz.sustenance.ui.onboarding.OnboardingScreen
import io.github.draumaz.sustenance.ui.onboarding.UnavailableScreen
import io.github.draumaz.sustenance.ui.settings.SettingsScreen
import io.github.draumaz.sustenance.ui.summary.InsightsScreen
import io.github.draumaz.sustenance.util.FoodNutrients
import io.github.draumaz.sustenance.util.GeminiManager
import io.github.draumaz.sustenance.widget.WidgetUpdateWorker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val ACTION_HC_SETTINGS = "androidx.health.connect.action.HEALTH_CONNECT_SETTINGS"
private const val ACTION_MANAGE_HEALTH_PERMISSIONS = "androidx.health.connect.action.MANAGE_HEALTH_PERMISSIONS"

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

@Composable
fun SustenanceRoot(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: SettingsRepository,
    exporter: ExportManager,
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
                sharedImageUris = sharedImageUris,
                launchLog = launchLog,
                onDeepLinkConsumed = onDeepLinkConsumed,
                onSharedImagesConsumed = onSharedImagesConsumed,
                onLogConsumed = onLogConsumed
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun MainNav(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: SettingsRepository,
    exporter: ExportManager,
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
    
    val backstack = remember { mutableStateListOf<NavKey>(NavKey.Today) }
    val currentKey = backstack.lastOrNull()
    val previousKey = if (backstack.size > 1) backstack[backstack.size - 2] else null
    
    val topLevel = remember { listOf(NavKey.Insights, NavKey.Today, NavKey.Settings()) }
    
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

    val goals by goalsRepo.goals.collectAsState(initial = emptyMap())
    val judgementalMode by settingsRepo.judgementalMode.collectAsState(initial = false)
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
            uris.forEach { uri ->
                decodeDownsampledBitmap(currentContext, uri)?.let { bitmap ->
                    capturedBitmaps += bitmap
                }
            }
            if (uris.isNotEmpty()) {
                isBatchMode = true
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
            pbState.isSwipeActive = true
            try {
                progress.collect { event -> pbState.progress = event.progress }
                clearCapture()
            } catch (e: Exception) {
                // Cancelled
            } finally {
                pbState.isSwipeActive = false
                pbState.progress = 0f
            }
        }
    }

    PredictiveBackHandler(enabled = isHistoryActive) { progress ->
        pbState.isSwipeActive = true
        try {
            progress.collect { event -> pbState.progress = event.progress }
            isHistoryActive = false
            clearCapture()
        } catch (e: Exception) {
            // Cancelled
        } finally {
            pbState.isSwipeActive = false
            pbState.progress = 0f
        }
    }

    val apiKeyEnabled by settingsRepo.apiKeyEnabled.collectAsStateWithLifecycle(initialValue = false)
    val apiKey by settingsRepo.apiKey.collectAsStateWithLifecycle(initialValue = "")
    val hasApiKey = apiKeyEnabled && apiKey.isNotEmpty()

    val bottomBarHeight = 120.dp
    val bottomBarHeightPx = with(LocalDensity.current) { bottomBarHeight.roundToPx().toFloat() }
    val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentKey, isHistoryActive) {
        bottomBarOffsetHeightPx.floatValue = 0f
    }

    val nestedScrollConnection = remember(isCameraActive, isHistoryActive) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isCameraActive && !isHistoryActive) return Offset.Zero
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx.floatValue + delta
                bottomBarOffsetHeightPx.floatValue = newOffset.coerceIn(-bottomBarHeightPx, 0f)
                return super.onPreScroll(available, source)
            }
        }
    }

    LaunchedEffect(sharedImageUris) {
        sharedImageUris?.let { uris ->
            isCameraActive = true
            isBatchMode = true
            uris.forEach { uri ->
                decodeDownsampledBitmap(currentContext, uri)?.let { bitmap ->
                    capturedBitmaps += bitmap
                }
            }
            onSharedImagesConsumed()
        }
    }

    LaunchedEffect(deepLinkMetric) {
        val metric = deepLinkMetric?.let { Metric.fromKey(it) }
        if (metric != null) {
            val isGranted = manager.permissionFor(metric) in granted
            if (isGranted) {
                backstack.add(NavKey.Detail(metric.key))
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

    val dashboardBlur by animateDpAsState(
        targetValue = if (isCameraActive || isAnalyzing || pendingNutrients != null) 16.dp else 0.dp,
        label = "dashboard_blur"
    )

    val cameraBlur by animateDpAsState(
        targetValue = if (isAnalyzing || pendingNutrients != null) 16.dp else 0.dp,
        label = "camera_blur"
    )

    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val isVertical = layoutType != NavigationSuiteType.NavigationBar

    val animatedOffset by animateIntAsState(
        targetValue = bottomBarOffsetHeightPx.floatValue.roundToInt(),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bottom_bar_offset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (isVertical) {
                ExpressiveNavigationBar(
                    currentKey = currentKey,
                    previousKey = previousKey,
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
                                val errorMsg = result.exceptionOrNull()?.message ?: ""
                                Toast.makeText(
                                    currentContext,
                                    appContext.getString(R.string.analysis_failed, errorMsg),
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
                    isVertical = true,
                    onNavigate = { dest ->
                        if (isCameraActive) clearCapture()
                        isHistoryActive = false
                        if (dest == NavKey.Today) {
                            if (currentKey == NavKey.Today) {
                                todayClickCount++
                                bottomBarOffsetHeightPx.floatValue = 0f
                            } else {
                                while (backstack.size > 1) {
                                    backstack.removeAt(backstack.size - 1)
                                }
                            }
                        } else {
                            val isSameTopLevel = when (dest) {
                                is NavKey.Settings -> currentKey is NavKey.Settings
                                else -> currentKey == dest
                            }
                            if (!isSameTopLevel) {
                                while (backstack.size > 1) {
                                    backstack.removeAt(backstack.size - 1)
                                }
                                if (dest != NavKey.Today) {
                                    backstack.add(dest)
                                }
                            }
                        }
                    }
                )
            }

            Box(Modifier.weight(1f)) {
                NavDisplay(
                    backStack = backstack,
                    onBack = { if (backstack.size > 1) backstack.removeAt(backstack.size - 1) },
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(dashboardBlur),
                    sceneStrategy = listDetailStrategy
                ) { key ->
                    when (key) {
                        NavKey.Today -> NavEntry(
                            key = key,
                            metadata = ListDetailSceneStrategy.listPane()
                        ) { _ ->
                            DashboardScreen(
                                manager = manager,
                                goalsRepo = goalsRepo,
                                settingsRepo = settingsRepo,
                                granted = granted,
                                bottomInset = if (isVertical) 0.dp else 120.dp,
                                todayClickCount = todayClickCount,
                                onOpenMetric = { detailKey ->
                                    backstack.add(detailKey)
                                },
                                onTimerClick = {
                                    backstack.add(NavKey.Settings(scrollTo = "fasting"))
                                },
                                onManagePermissions = onManagePermissions,
                                onDateChanged = { 
                                    dashboardDateOffset = it
                                    bottomBarOffsetHeightPx.floatValue = 0f
                                }
                            )
                        }

                        NavKey.Insights -> NavEntry(
                            key = key
                        ) { _ ->
                            InsightsScreen(
                                manager = manager,
                                goalsRepo = goalsRepo,
                                bottomInset = if (isVertical) 0.dp else 120.dp,
                                onBack = { if (backstack.size > 1) backstack.removeAt(backstack.size - 1) }
                            )
                        }

                        is NavKey.Settings -> NavEntry(
                            key = key
                        ) { _ ->
                            SettingsScreen(
                                manager = manager,
                                exporter = exporter,
                                settingsRepo = settingsRepo,
                                bottomInset = if (isVertical) 0.dp else 120.dp,
                                onManagePermissions = onManagePermissions,
                                onBack = { if (backstack.size > 1) backstack.removeAt(backstack.size - 1) },
                                scrollTo = (key as NavKey.Settings).scrollTo
                            )
                        }

                        is NavKey.Detail -> NavEntry(
                            key = key,
                            metadata = ListDetailSceneStrategy.detailPane()
                        ) { entryKey ->
                            val detailKey = (entryKey as? NavKey.Detail) ?: key as NavKey.Detail
                            val metric = Metric.fromKey(detailKey.metricKey) ?: Metric.TOTAL_CALORIES
                            DetailScreen(
                                manager = manager,
                                goalsRepo = goalsRepo,
                                settingsRepo = settingsRepo,
                                metric = metric,
                                dateOffset = dashboardDateOffset,
                                onReLog = { pendingNutrients = it },
                                onBack = { if (backstack.size > 1) backstack.removeAt(backstack.size - 1) }
                            )
                        }
                    }
                }
            }
        }

        if (!isVertical) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(x = 0, y = -animatedOffset) }
            ) {
                ExpressiveNavigationBar(
                    currentKey = currentKey,
                    previousKey = previousKey,
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
                                val errorMsg = result.exceptionOrNull()?.message ?: ""
                                Toast.makeText(
                                    currentContext,
                                    appContext.getString(R.string.analysis_failed, errorMsg),
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
                    isVertical = false,
                    onNavigate = { dest ->
                        if (isCameraActive) clearCapture()
                        isHistoryActive = false
                        if (dest == NavKey.Today) {
                            if (currentKey == NavKey.Today) {
                                todayClickCount++
                                bottomBarOffsetHeightPx.floatValue = 0f
                            } else {
                                while (backstack.size > 1) {
                                    backstack.removeAt(backstack.size - 1)
                                }
                            }
                        } else {
                            val isSameTopLevel = when (dest) {
                                is NavKey.Settings -> currentKey is NavKey.Settings
                                else -> currentKey == dest
                            }
                            if (!isSameTopLevel) {
                                while (backstack.size > 1) {
                                    backstack.removeAt(backstack.size - 1)
                                }
                                if (dest != NavKey.Today) {
                                    backstack.add(dest)
                                }
                            }
                        }
                    }
                )
            }
        }

            // Interaction Shield for blurred background
            if (dashboardBlur > 0.dp) {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = if (isVertical) 0.dp else bottomBarHeight
                        )
                        .graphicsLayer {
                            alpha = 1f - pbState.progress
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAnalyzing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box {
                            CameraPreview(
                                modifier = Modifier.blur(cameraBlur),
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
                                            capturedBitmaps += rotatedBitmap
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
                                                    appContext.getString(R.string.analysis_failed, errorMsg),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                isCapturing = false
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
                    settingsRepo = settingsRepo,
                    bottomInset = if (isVertical) 0.dp else 120.dp,
                    predictiveBackProgress = if (isHistoryActive) pbState.progress else 0f,
                    onItemSelected = { item ->
                        pendingNutrients = item.nutrients
                    },
                    onBack = { 
                        isHistoryActive = false 
                        clearCapture()
                    }
                )
            }

            pendingNutrients?.let { nutrients ->
                FoodReviewDialog(
                    nutrients = nutrients,
                    onDismiss = { pendingNutrients = null },
                    judgementalMode = judgementalMode,
                    currentTotals = currentTotals,
                    goals = goals,
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
