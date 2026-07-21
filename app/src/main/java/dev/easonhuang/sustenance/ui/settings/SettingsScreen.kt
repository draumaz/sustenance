package dev.easonhuang.sustenance.ui.settings

import android.content.Intent
import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.BuildConfig
import dev.easonhuang.sustenance.data.ExportFormat
import dev.easonhuang.sustenance.data.ExportManager
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.SettingsRepository
import dev.easonhuang.sustenance.ui.components.PredictiveBackState
import dev.easonhuang.sustenance.ui.SettingsViewModel
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient

private const val REPO_URL = "https://github.com/draumaz/sustenance"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    manager: HealthConnectManager,
    exporter: ExportManager,
    settingsRepo: SettingsRepository,
    bottomInset: Dp,
    onManagePermissions: () -> Unit,
    onBack: () -> Unit = {},
    scrollTo: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(settingsRepo))
    val dynamicColor by vm.dynamicColor.collectAsState(initial = true)

    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    var showFormatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scrollTo) {
        if (scrollTo == "fasting") {
            listState.animateScrollToItem(2)
        }
    }

    suspend fun runExport(uri: Uri, format: ExportFormat) {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        val metrics = Metric.entries.filter { manager.permissionFor(it) in granted }
        if (metrics.isEmpty()) {
            snackbar.showSnackbar(context.getString(R.string.export_no_data)); return
        }
        exporter.export(uri, metrics, format).fold(
            onSuccess = { count -> snackbar.showSnackbar(context.getString(R.string.export_success, count, format.label)) },
            onFailure = { snackbar.showSnackbar(context.getString(R.string.export_failed, it.message)) },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.CSV) } } }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.JSON) } } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp), fontWeight = FontWeight.Bold)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        //topBar = { LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionLabel(stringResource(R.string.section_appearance)) }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.dynamic_color),
                        subtitle = stringResource(R.string.dynamic_color_summary),
                        onClick = { vm.setDynamicColor(!dynamicColor) }
                    ) {
                        Switch(checked = dynamicColor, onCheckedChange = null)
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.section_diet)) }
            item {
                SettingsCard {
                    val ketoMode by vm.ketoMode.collectAsState(initial = false)
                    SettingRow(
                        icon = Icons.Rounded.Whatshot,
                        title = stringResource(R.string.keto_mode),
                        subtitle = stringResource(R.string.keto_mode_summary),
                        onClick = { vm.setKetoMode(!ketoMode) }
                    ) {
                        Switch(checked = ketoMode, onCheckedChange = null)
                    }

                    val lastLogTimerEnabled by vm.lastLogTimerEnabled.collectAsState(initial = false)
                    SettingRow(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.show_last_log_timer),
                        subtitle = stringResource(R.string.show_last_log_timer_summary),
                        onClick = { vm.setLastLogTimerEnabled(!lastLogTimerEnabled) }
                    ) {
                        Switch(checked = lastLogTimerEnabled, onCheckedChange = null)
                    }

                    if (lastLogTimerEnabled) {
                        val fastBreakingCalories by vm.fastBreakingCalories.collectAsState(initial = 0)
                        var tempCalories by remember(fastBreakingCalories) { mutableStateOf(fastBreakingCalories.toString()) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempCalories,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                        tempCalories = newValue
                                        newValue.toIntOrNull()?.let { vm.setFastBreakingCalories(it) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(stringResource(R.string.fast_breaking_calories)) },
                                placeholder = { Text("0") },
                                suffix = { Text(stringResource(R.string.unit_kcal)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text(stringResource(R.string.fast_breaking_calories_summary)) }
                            )
                            Spacer(Modifier.size(8.dp))
                        }

                        val fastingGoalHours by vm.fastingGoalHours.collectAsState(initial = 16f)
                        var tempGoal by remember(fastingGoalHours) { mutableStateOf(if (fastingGoalHours % 1f == 0f) fastingGoalHours.toInt().toString() else fastingGoalHours.toString()) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempGoal,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.toDoubleOrNull() != null || newValue == ".") {
                                        tempGoal = newValue
                                        newValue.toFloatOrNull()?.let { vm.setFastingGoalHours(it) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(stringResource(R.string.fasting_goal)) },
                                placeholder = { Text("16") },
                                suffix = { Text(stringResource(R.string.unit_hr)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                supportingText = { Text(stringResource(R.string.fasting_goal_summary)) }
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.section_misc)) }
            item {
                SettingsCard {
                    val apiKeyEnabled by vm.apiKeyEnabled.collectAsState(initial = false)
                    val apiKey by vm.apiKey.collectAsState(initial = "")
                    var tempApiKey by remember(apiKey) { mutableStateOf(apiKey) }

                    SettingRow(
                        icon = Icons.Rounded.Key,
                        title = stringResource(R.string.enable_food_logging),
                        subtitle = stringResource(R.string.food_logging_summary),
                        onClick = { vm.setApiKeyEnabled(!apiKeyEnabled) }
                    ) {
                        Switch(checked = apiKeyEnabled, onCheckedChange = null)
                    }

                    if (apiKeyEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var apiKeyVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = tempApiKey,
                                onValueChange = { tempApiKey = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("AQ.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                singleLine = true,
                                label = { Text(stringResource(R.string.api_key)) },
                                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                        Icon(
                                            imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (apiKeyVisible) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key)
                                        )
                                    }
                                }
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.section_data)) }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = stringResource(R.string.launch_hc),
                        subtitle = stringResource(R.string.launch_hc_summary),
                        onClick = {
                            runCatching {
                                val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.section_about)) }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        title = "Sustenance ${BuildConfig.VERSION_NAME}",
                        subtitle = stringResource(R.string.about_summary),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
                            }
                        }
                    )
                }
            }
            item {
                Text(
                    text = buildAnnotatedString {
                        append("Sustenance is based on ")
                        withLink(LinkAnnotation.Url("https://github.com/GuyOnWifi/heartwood")) {
                            append("Heartwood")
                        }
                        append(", a Health Connect viewer by Eason Huang.\n\nThe majority of new code was written with the help of gemini-3-flash-preview.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text(stringResource(R.string.export_format_title)) },
            text = { Text(stringResource(R.string.export_format_summary)) },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    csvLauncher.launch("sustenance-export.csv")
                }) { Text("CSV") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    jsonLauncher.launch("sustenance-export.json")
                }) { Text("JSON") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(9.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (content != null) {
            Spacer(Modifier.size(16.dp))
            content()
        }
    }
}