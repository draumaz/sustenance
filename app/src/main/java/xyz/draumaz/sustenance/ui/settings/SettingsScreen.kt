package xyz.draumaz.sustenance.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import xyz.draumaz.sustenance.util.GeminiManager
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.VerificationStatus
import xyz.draumaz.sustenance.BuildConfig
import xyz.draumaz.sustenance.data.SettingsRepository
import xyz.draumaz.sustenance.data.parseDecimal
import xyz.draumaz.sustenance.ui.SettingsViewModel
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient

private const val AUTHOR_URL = "https://github.com/draumaz"
private const val REPO_URL = "https://github.com/draumaz/sustenance"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    bottomInset: Dp,
    onManagePermissions: () -> Unit,
    onEditGoals: () -> Unit,
    onBack: () -> Unit = {},
    scrollTo: String? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext as xyz.draumaz.sustenance.SustenanceApp
    val scope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(settingsRepo))
    val dynamicColor by vm.dynamicColor.collectAsState(initial = false)
    val goalSetupDeferred by settingsRepo.goalSetupDeferred.collectAsState(initial = false)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    LaunchedEffect(scrollTo, goalSetupDeferred) {
        if (scrollTo == "fasting") {
            // The diet card sits one item lower while the goal setup prompt is shown.
            listState.animateScrollToItem(if (goalSetupDeferred) 3 else 2)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp),
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Shrink above the keyboard so focused fields can scroll into view (adjustResize).
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 16.dp,
                bottom = bottomInset + 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (goalSetupDeferred) {
                item {
                    // Highlighted until the user finishes the goal step they skipped during setup.
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onEditGoals)
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.TrackChanges, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(9.dp),
                            )
                            Spacer(Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.goal_setup_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(R.string.goal_setup_reminder),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.size(16.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                        }
                    }
                }
            }
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
                    SettingRow(
                        icon = Icons.Rounded.TrackChanges,
                        title = stringResource(R.string.daily_goals),
                        subtitle = stringResource(R.string.daily_goals_summary),
                        onClick = onEditGoals,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

                    val ketoMode by vm.ketoMode.collectAsState(initial = false)
                    SettingRow(
                        icon = Icons.Rounded.Whatshot,
                        title = stringResource(R.string.keto_mode),
                        subtitle = stringResource(R.string.keto_mode_summary),
                        onClick = { vm.setKetoMode(!ketoMode) }
                    ) {
                        Switch(checked = ketoMode, onCheckedChange = null)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

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
                        // Local edits win over the stored value, so a save landing mid-typing can't reset the field.
                        var tempCalories by remember { mutableStateOf<String?>(null) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempCalories ?: fastBreakingCalories.toString(),
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
                        var tempGoal by remember { mutableStateOf<String?>(null) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempGoal ?: if (fastingGoalHours % 1f == 0f) fastingGoalHours.toInt().toString() else fastingGoalHours.toString(),
                                onValueChange = { newValue ->
                                    val parsed = parseDecimal(newValue)
                                    if (newValue.isEmpty() || parsed != null || newValue == "." || newValue == ",") {
                                        tempGoal = newValue
                                        parsed?.let { vm.setFastingGoalHours(it.toFloat()) }
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

                        val fastingNotificationsEnabled by vm.fastingNotificationsEnabled.collectAsState(initial = false)
                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted) {
                                vm.setFastingNotificationsEnabled(true)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

                        SettingRow(
                            icon = Icons.Rounded.Notifications,
                            title = stringResource(R.string.fasting_notifications),
                            subtitle = stringResource(R.string.fasting_notifications_summary),
                            onClick = {
                                if (!fastingNotificationsEnabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        vm.setFastingNotificationsEnabled(true)
                                    }
                                } else {
                                    vm.setFastingNotificationsEnabled(false)
                                }
                            }
                        ) {
                            Switch(checked = fastingNotificationsEnabled, onCheckedChange = null)
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.section_misc)) }
            item {
                SettingsCard {
                    val apiKeyEnabled by vm.apiKeyEnabled.collectAsState(initial = false)
                    val apiKey by vm.apiKey.collectAsState(initial = "")
                    // Local edits win over the stored values, so a save landing mid-typing can't reset the field.
                    var editedApiKey by remember { mutableStateOf<String?>(null) }
                    val tempApiKey = editedApiKey ?: apiKey
                    val apiKeyVerificationStatus by vm.apiKeyVerificationStatus.collectAsState(initial = VerificationStatus.IDLE)
                    var isApiKeyVerifying by remember { mutableStateOf(false) }

                    val geminiModel by vm.geminiModel.collectAsState(initial = GeminiManager.DEFAULT_MODEL)
                    var editedGeminiModel by remember { mutableStateOf<String?>(null) }
                    val tempGeminiModel = editedGeminiModel ?: geminiModel.removePrefix("gemini-")
                    val geminiModelVerificationStatus by vm.geminiModelVerificationStatus.collectAsState(initial = VerificationStatus.IDLE)
                    var isModelVerifying by remember { mutableStateOf(false) }

                    // Both verify buttons make the same test call with the current key + model;
                    // they differ only in which status they update and the failure message.
                    fun verifyGemini(
                        @StringRes failureMessage: Int,
                        setStatus: (VerificationStatus) -> Unit,
                        setVerifying: (Boolean) -> Unit,
                    ) {
                        val key = tempApiKey.trim()
                        if (key.isBlank()) {
                            Toast.makeText(context, R.string.api_key_missing, Toast.LENGTH_SHORT).show()
                            setStatus(VerificationStatus.ERROR)
                            return
                        }
                        setVerifying(true)
                        scope.launch {
                            val result = GeminiManager(key, GeminiManager.modelName(tempGeminiModel)).verifyModel()
                            setVerifying(false)
                            if (result.isSuccess) {
                                setStatus(VerificationStatus.SUCCESS)
                            } else {
                                setStatus(VerificationStatus.ERROR)
                                val base = appContext.getString(failureMessage)
                                val details = result.exceptionOrNull()?.localizedMessage
                                val msg = if (details.isNullOrBlank()) base else "$base ($details)"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    SettingRow(
                        icon = Icons.Rounded.Key,
                        title = stringResource(R.string.enable_food_logging),
                        subtitle = stringResource(R.string.food_logging_summary),
                        onClick = { vm.setApiKeyEnabled(!apiKeyEnabled) }
                    ) {
                        Switch(checked = apiKeyEnabled, onCheckedChange = null)
                    }

                    if (apiKeyEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempGeminiModel,
                                onValueChange = { newValue ->
                                    val cleaned = if (newValue.startsWith("gemini-")) newValue.removePrefix("gemini-") else newValue
                                    editedGeminiModel = cleaned
                                    vm.setGeminiModel(cleaned)
                                    if (geminiModelVerificationStatus != VerificationStatus.IDLE) {
                                        vm.setGeminiModelVerificationStatus(VerificationStatus.IDLE)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                prefix = { Text("gemini-") },
                                placeholder = { Text(GeminiManager.DEFAULT_MODEL, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                singleLine = true,
                                label = { Text(stringResource(R.string.gemini_model)) },
                                colors = verificationFieldColors(geminiModelVerificationStatus),
                                trailingIcon = {
                                    VerifyButton(
                                        status = geminiModelVerificationStatus,
                                        isVerifying = isModelVerifying,
                                        contentDescription = stringResource(R.string.verify_model),
                                        onClick = {
                                            verifyGemini(
                                                R.string.invalid_model_name,
                                                vm::setGeminiModelVerificationStatus,
                                            ) { isModelVerifying = it }
                                        },
                                    )
                                }
                            )
                            Spacer(Modifier.size(8.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var apiKeyVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = tempApiKey,
                                onValueChange = {
                                    editedApiKey = it
                                    vm.setApiKey(it)
                                    if (apiKeyVerificationStatus != VerificationStatus.IDLE) {
                                        vm.setApiKeyVerificationStatus(VerificationStatus.IDLE)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("AQ.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                singleLine = true,
                                label = { Text(stringResource(R.string.api_key)) },
                                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = verificationFieldColors(apiKeyVerificationStatus),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                            Icon(
                                                imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (apiKeyVisible) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key)
                                            )
                                        }
                                        VerifyButton(
                                            status = apiKeyVerificationStatus,
                                            isVerifying = isApiKeyVerifying,
                                            contentDescription = stringResource(R.string.verify_api_key),
                                            onClick = {
                                                verifyGemini(
                                                    R.string.api_key_invalid,
                                                    vm::setApiKeyVerificationStatus,
                                                ) { isApiKeyVerifying = it }
                                            },
                                        )
                                    }
                                }
                            )
                            Spacer(Modifier.size(8.dp))
                        }

                        val gramIncrementEnabled by vm.gramIncrementEnabled.collectAsState(initial = false)
                        val gramIncrement by vm.gramIncrement.collectAsState(initial = 1)
                        var tempIncrement by remember { mutableStateOf<String?>(null) }

                        SettingRow(
                            icon = Icons.Rounded.Scale,
                            title = stringResource(R.string.gram_increment),
                            subtitle = stringResource(R.string.gram_increment_summary),
                            onClick = { vm.setGramIncrementEnabled(!gramIncrementEnabled) }
                        ) {
                            Switch(checked = gramIncrementEnabled, onCheckedChange = null)
                        }

                        if (gramIncrementEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = tempIncrement ?: gramIncrement.toString(),
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                            tempIncrement = newValue
                                            newValue.toIntOrNull()?.takeIf { it > 0 }?.let { vm.setGramIncrement(it) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.gram_increment_amount)) },
                                    placeholder = { Text("1") },
                                    suffix = { Text(stringResource(R.string.unit_g)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

                        val judgementalMode by vm.judgementalMode.collectAsState(initial = false)
                        SettingRow(
                            icon = Icons.Rounded.Psychology,
                            title = stringResource(R.string.judgemental_mode),
                            subtitle = stringResource(R.string.judgemental_mode_summary),
                            onClick = { vm.setJudgementalMode(!judgementalMode) }
                        ) {
                            Switch(checked = judgementalMode, onCheckedChange = null)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

                        val bingeCheckEnabled by vm.bingeCheckEnabled.collectAsState(initial = true)
                        SettingRow(
                            icon = Icons.Rounded.Spa,
                            title = stringResource(R.string.binge_check),
                            subtitle = stringResource(R.string.binge_check_summary),
                            onClick = { vm.setBingeCheckEnabled(!bingeCheckEnabled) }
                        ) {
                            Switch(checked = bingeCheckEnabled, onCheckedChange = null)
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
                        painter = painterResource(R.drawable.draumaz),
                        title = "draumaz",
                        subtitle = stringResource(R.string.about_author),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, AUTHOR_URL.toUri()))
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

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
                        append(stringResource(R.string.about_credit_prefix))
                        withLink(LinkAnnotation.Url("https://github.com/GuyOnWifi/heartwood")) {
                            append(stringResource(R.string.about_credit_heartwood))
                        }
                        append(stringResource(R.string.about_credit_suffix))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(
    painter: Painter,
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
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
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

/** Green used to mark a verified API key / model. */
private val VerifiedColor = Color(0xFF4CAF50)

@Composable
private fun verificationFieldColors(status: VerificationStatus) = when (status) {
    VerificationStatus.SUCCESS -> OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VerifiedColor,
        unfocusedBorderColor = VerifiedColor,
    )
    VerificationStatus.ERROR -> OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.error,
        unfocusedBorderColor = MaterialTheme.colorScheme.error,
    )
    VerificationStatus.IDLE -> OutlinedTextFieldDefaults.colors()
}

/** Check-mark button at the end of a text field, tinted by the last verification result. */
@Composable
private fun VerifyButton(
    status: VerificationStatus,
    isVerifying: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    if (isVerifying) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        return
    }
    val (bgColor, contentColor) = when (status) {
        VerificationStatus.SUCCESS -> VerifiedColor.copy(alpha = 0.15f) to VerifiedColor
        VerificationStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
        VerificationStatus.IDLE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.primary
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(bgColor)
                .padding(6.dp)
        )
    }
}
