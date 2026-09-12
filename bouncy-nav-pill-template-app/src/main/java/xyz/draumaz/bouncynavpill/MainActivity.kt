package xyz.draumaz.bouncynavpill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.draumaz.bouncynavpill.ui.BouncyNavTheme
import xyz.draumaz.bouncynavpill.ui.ExpressiveNavigationBar
import xyz.draumaz.bouncynavpill.ui.NavDestinationItem
import xyz.draumaz.bouncynavpill.ui.PredictiveBackState
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BouncyNavTheme {
                BouncyNavTemplateApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncyNavTemplateApp() {
    val navController = rememberNavController()
    val pbState = remember { PredictiveBackState() }

    val destinations = listOf(
        NavDestinationItem("home", "Home", Icons.Rounded.Home),
        NavDestinationItem("explore", "Explore", Icons.Rounded.Explore),
        NavDestinationItem("analytics", "Analytics", Icons.Rounded.Analytics),
        NavDestinationItem("settings", "Settings", Icons.Rounded.Settings)
    )

    var isScalloped by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val navBarHeightPx = with(density) { 100.dp.toPx() }
    val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx.floatValue - delta
                bottomBarOffsetHeightPx.floatValue = newOffset.coerceIn(0f, navBarHeightPx)
                return Offset.Zero
            }
        }
    }

    val animatedOffset by animateIntAsState(
        targetValue = bottomBarOffsetHeightPx.floatValue.roundToInt(),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bottom_bar_offset"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bouncy Nav Pill Demo") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            "Scalloped",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = isScalloped,
                            onCheckedChange = { isScalloped = it }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.offset { IntOffset(x = 0, y = animatedOffset) }
            ) {
                ExpressiveNavigationBar(
                    navController = navController,
                    destinations = destinations,
                    predictiveBackState = pbState,
                    isScalloped = isScalloped,
                    onNavigate = { dest ->
                        navController.navigate(dest.route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(nestedScrollConnection)
        ) {
            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
                composable("home") {
                    ScreenContent(
                        title = "Home Screen",
                        subtitle = "Try selecting tabs or holding down for haptic feedback! Scroll up and down to test auto-hiding behavior.",
                        pbState = pbState,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("explore") {
                    ScreenContent(
                        title = "Explore Screen",
                        subtitle = "Discover new interactive components powered by Jetpack Compose & Expressive Navigation.",
                        pbState = pbState,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("analytics") {
                    ScreenContent(
                        title = "Analytics Screen",
                        subtitle = "Fluid spring dynamics and smooth predictive back transition metrics.",
                        pbState = pbState,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    ScreenContent(
                        title = "Settings Screen",
                        subtitle = "Configure theme settings, animation physics, and navigation preferences.",
                        pbState = pbState,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenContent(
    title: String,
    subtitle: String,
    pbState: PredictiveBackState,
    onBack: () -> Unit
) {
    PredictiveBackHandler(enabled = true) { progress ->
        pbState.isSwipeActive = true
        try {
            progress.collect { event -> pbState.progress = event.progress }
            pbState.isSwipeActive = false
            pbState.progress = 0f
            onBack()
        } catch (e: Exception) {
            pbState.isSwipeActive = false
            pbState.progress = 0f
        }
    }

    val progress = pbState.progress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1f - (progress * 0.08f)
                scaleY = 1f - (progress * 0.08f)
                translationX = progress * 400f
                alpha = 1f - (progress * 0.2f)
                shape = RoundedCornerShape((progress * 28).dp)
                clip = true
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            items(15) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Sample Content Item ${index + 1}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Scrollable item demonstrating auto-hide and spring physics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}
