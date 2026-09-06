package io.github.draumaz.sustenance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.draumaz.sustenance.R
import io.github.draumaz.sustenance.data.GoalCatalog
import io.github.draumaz.sustenance.data.Metric
import io.github.draumaz.sustenance.util.FoodNutrients
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun formatTargetPercentage(value: Double, goal: Float?): String? {
    if (goal == null || goal <= 0f) return null
    val pct = (value / goal * 100.0)
    return when {
        pct <= 0.0 -> "0"
        pct < 1.0 -> "<1"
        else -> pct.roundToInt().toString()
    }
}

private data class NutrientChipData(
    val label: String,
    val value: String,
    val onValueChange: (String) -> Unit,
    val unit: String,
    val containerColor: Color,
    val percentage: String?
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FoodReviewDialog(
    nutrients: FoodNutrients,
    onDismiss: () -> Unit,
    onLog: (FoodNutrients, Double, Instant) -> Unit,
    judgementalMode: Boolean = false,
    currentTotals: Map<Metric, Float> = emptyMap(),
    goals: Map<Metric, Float> = emptyMap(),
) {
    var foodItem by remember { mutableStateOf(nutrients.foodItem) }
    // Extract only the numeric part for the editable state. Favor numbers followed by "g".
    var servingSize by remember(nutrients) {
        val s = nutrients.servingSize
        val gMatch = "(\\d+)\\s*g".toRegex(RegexOption.IGNORE_CASE).find(s)
        val result = gMatch?.groupValues?.get(1) ?: "(\\d+)".toRegex().find(s)?.groupValues?.get(1) ?: s
        mutableStateOf(result)
    }

    val now = remember { LocalDateTime.now() }
    var selectedTime by remember { mutableStateOf(now.toLocalTime()) }
    var showTimePicker by remember { mutableStateOf(value = false) }

    val baseGrams = remember(servingSize) {
        servingSize.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0
    }

    fun safeParse(s: String): Double? = s.replace(',', '.').toDoubleOrNull()
    fun format(d: Double): String {
        val res = if ((d % 1.0) == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
        if (res.isEmpty()) android.util.Log.e("FoodReviewDialog", "FORMAT PRODUCED EMPTY STRING FOR $d")
        return res
    }

    var currentGrams by remember(nutrients) { mutableDoubleStateOf(baseGrams) }
    var quantityText by remember(nutrients) { mutableStateOf(format(currentGrams)) }

    val calBase = remember(nutrients) { mutableDoubleStateOf(nutrients.calories) }
    val protBase = remember(nutrients) { mutableDoubleStateOf(nutrients.protein) }
    val carbBase = remember(nutrients) { mutableDoubleStateOf(nutrients.carbs) }
    val fatBase = remember(nutrients) { mutableDoubleStateOf(nutrients.fat) }
    val satFatBase = remember(nutrients) { mutableDoubleStateOf(nutrients.saturatedFat) }
    val fiberBase = remember(nutrients) { mutableDoubleStateOf(nutrients.fiber) }
    val sugarBase = remember(nutrients) { mutableDoubleStateOf(nutrients.sugar) }
    val sodiumBase = remember(nutrients) { mutableDoubleStateOf(nutrients.sodium) }

    var cal by remember(nutrients) { mutableStateOf(format(nutrients.calories)) }
    var prot by remember(nutrients) { mutableStateOf(format(nutrients.protein)) }
    var carb by remember(nutrients) { mutableStateOf(format(nutrients.carbs)) }
    var fat by remember(nutrients) { mutableStateOf(format(nutrients.fat)) }
    var satFat by remember(nutrients) { mutableStateOf(format(nutrients.saturatedFat)) }
    var fiber by remember(nutrients) { mutableStateOf(format(nutrients.fiber)) }
    var sugar by remember(nutrients) { mutableStateOf(format(nutrients.sugar)) }
    var sodium by remember(nutrients) { mutableStateOf(format(nutrients.sodium)) }

    var showConfirmationDialog by remember { mutableStateOf(false) }

    val currentNutrients = FoodNutrients(
        foodItem = foodItem,
        servingSize = "${currentGrams.toInt()}g",
        calories = safeParse(cal) ?: 0.0,
        protein = safeParse(prot) ?: 0.0,
        carbs = safeParse(carb) ?: 0.0,
        fat = safeParse(fat) ?: 0.0,
        saturatedFat = safeParse(satFat) ?: 0.0,
        fiber = safeParse(fiber) ?: 0.0,
        sugar = safeParse(sugar) ?: 0.0,
        sodium = safeParse(sodium) ?: 0.0
    )

    val judgement = remember(currentNutrients, currentTotals, goals) {
        Metric.judgeFoodItem(currentNutrients, currentTotals, goals)
    }

    fun scaleNutrients(newGrams: Double) {
        if (baseGrams <= 0) return
        val ratio = newGrams / baseGrams
        val s = { base: Double -> format(base * ratio) }
        cal = s(calBase.doubleValue)
        prot = s(protBase.doubleValue)
        carb = s(carbBase.doubleValue)
        fat = s(fatBase.doubleValue)
        satFat = s(satFatBase.doubleValue)
        fiber = s(fiberBase.doubleValue)
        sugar = s(sugarBase.doubleValue)
        sodium = s(sodiumBase.doubleValue)
    }

    // Keep quantityText in sync when currentGrams is changed via buttons, but don't stomp on decimal typing.
    LaunchedEffect(currentGrams) {
        val currentTextNum = quantityText.replace(',', '.').toDoubleOrNull()
        if (currentTextNum != currentGrams) {
            quantityText = format(currentGrams)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        icon = {
            Icon(
                Icons.Rounded.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = foodItem.replace("\\s*\\(\\d+g\\)".toRegex(), "").trim(),
                    onValueChange = { foodItem = it },
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
                    AssistChip(
                        onClick = { showTimePicker = true },
                        label = { Text(selectedTime.format(timeFormatter)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = CircleShape,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                }

                if (showTimePicker) {
                    val timePickerState = rememberTimePickerState(
                        initialHour = selectedTime.hour,
                        initialMinute = selectedTime.minute,
                    )
                    Dialog(onDismissRequest = { showTimePicker = false }) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.log_food),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                                )
                                TimePicker(state = timePickerState)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showTimePicker = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                                            showTimePicker = false
                                        }
                                    ) {
                                        Text(stringResource(R.string.save))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Gram Selector & Judgement Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentGrams > 1) {
                                        val next = currentGrams - 1
                                        scaleNutrients(next)
                                        currentGrams = next
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.Remove, stringResource(R.string.less))
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BasicTextField(
                                    value = quantityText,
                                    onValueChange = { newValue ->
                                        val sanitized = newValue.replace(',', '.')
                                        val num = sanitized.toDoubleOrNull()
                                        if (num != null && num >= 0) {
                                            scaleNutrients(num)
                                            currentGrams = num
                                        }
                                        quantityText = newValue
                                    },
                                    textStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.widthIn(min = 60.dp)
                                )
                                Text(
                                    text = stringResource(R.string.grams_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    val next = currentGrams + 1
                                    scaleNutrients(next)
                                    currentGrams = next
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.more))
                            }
                        }
                    }

                    if (judgementalMode) {
                        Spacer(Modifier.width(8.dp))
                        val isNegative = judgement is Metric.Judgement.Negative
                        val color = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                        
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(MorphingScallopedShape(0f, 1f, bumpsCount = 2f))
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isNegative) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Nutrient Chips
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val ratio = if (baseGrams > 0) currentGrams / baseGrams else 1.0
                    val updateBase = { base: MutableDoubleState, newValue: String ->
                        safeParse(newValue)?.let { num ->
                            if (ratio > 0) base.doubleValue = num / ratio else base.doubleValue = num
                        }
                        Unit
                    }

                    val getGoal = { m: Metric -> goals[m] ?: GoalCatalog.defaults[m] ?: 0f }

                    val items = listOf(
                        NutrientChipData(
                            label = stringResource(R.string.metric_total_calories),
                            value = cal,
                            onValueChange = { s: String -> cal = s; updateBase(calBase, s) },
                            unit = stringResource(R.string.unit_kcal),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            percentage = formatTargetPercentage(safeParse(cal) ?: 0.0, getGoal(Metric.FOOD).takeIf { it > 0f } ?: getGoal(Metric.TOTAL_CALORIES))
                        ),
                        NutrientChipData(
                            label = stringResource(R.string.metric_protein),
                            value = prot,
                            onValueChange = { s: String -> prot = s; updateBase(protBase, s) },
                            unit = stringResource(R.string.unit_g),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            percentage = formatTargetPercentage(safeParse(prot) ?: 0.0, getGoal(Metric.PROTEIN))
                        ),
                        NutrientChipData(
                            label = stringResource(R.string.metric_carbs),
                            value = carb,
                            onValueChange = { s: String -> carb = s; updateBase(carbBase, s) },
                            unit = stringResource(R.string.unit_g),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            percentage = formatTargetPercentage(safeParse(carb) ?: 0.0, getGoal(Metric.CARBS))
                        ),
                        NutrientChipData(
                            label = stringResource(R.string.metric_fat),
                            value = fat,
                            onValueChange = { s: String -> fat = s; updateBase(fatBase, s) },
                            unit = stringResource(R.string.unit_g),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            percentage = formatTargetPercentage(safeParse(fat) ?: 0.0, getGoal(Metric.FAT))
                        ),
                        NutrientChipData(
                            label = stringResource(R.string.metric_saturated_fat),
                            value = satFat,
                            onValueChange = { s: String -> satFat = s; updateBase(satFatBase, s) },
                            unit = stringResource(R.string.unit_g),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            percentage = formatTargetPercentage(safeParse(satFat) ?: 0.0, getGoal(Metric.SATURATED_FAT))
                        ),
                        NutrientChipData(
                            label = stringResource(R.string.metric_fiber),
                            value = fiber,
                            onValueChange = { s: String -> fiber = s; updateBase(fiberBase, s) },
                            unit = stringResource(R.string.unit_g),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            percentage = formatTargetPercentage(safeParse(fiber) ?: 0.0, getGoal(Metric.FIBER))
                        ),
                        NutrientChipData(
                            label = stringResource(R.string.metric_sugar),
                            value = sugar,
                            onValueChange = { s: String -> sugar = s; updateBase(sugarBase, s) },
                            unit = stringResource(R.string.unit_g),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            percentage = formatTargetPercentage(safeParse(sugar) ?: 0.0, getGoal(Metric.SUGAR))
                        ),
                        NutrientChipData(
                            label = stringResource(R.string.metric_sodium),
                            value = sodium,
                            onValueChange = { s: String -> sodium = s; updateBase(sodiumBase, s) },
                            unit = stringResource(R.string.unit_mg),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            percentage = formatTargetPercentage(safeParse(sodium) ?: 0.0, getGoal(Metric.SODIUM))
                        )
                    )

                    items.forEach { data ->
                        EditableNutrientChip(
                            label = data.label,
                            value = data.value,
                            onValueChange = data.onValueChange,
                            unit = data.unit,
                            containerColor = data.containerColor,
                            percentage = data.percentage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (judgementalMode && judgement is Metric.Judgement.Negative) {
                        showConfirmationDialog = true
                    } else {
                        val combinedInstant = now.with(selectedTime).atZone(ZoneId.systemDefault()).toInstant()
                        onLog(currentNutrients, 1.0, combinedInstant)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Done, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.log_food))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(stringResource(R.string.are_you_sure)) },
            text = { Text(stringResource(R.string.judgement_negative_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val combinedInstant = now.with(selectedTime).atZone(ZoneId.systemDefault()).toInstant()
                        onLog(currentNutrients, 1.0, combinedInstant)
                        showConfirmationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.log_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EditableNutrientChip(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    percentage: String? = null,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        val contentColor = contentColorFor(containerColor)
        val cleanUnit = unit.trim()
        val isSodiumOrMg = cleanUnit.equals("mg", ignoreCase = true) || cleanUnit.equals("мг", ignoreCase = true)
        val isMultiChar = cleanUnit.length > 1

        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (percentage != null) {
                    Text(
                        text = " ($percentage%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor.copy(alpha = 0.45f),
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = {
                        val sanitized = it.replace(',', '.')
                        if (it.isEmpty() || sanitized.toDoubleOrNull() != null || it == "." || it == ",") {
                            onValueChange(it)
                        }
                    },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.End,
                        color = contentColor
                    ),
                    modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 50.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(contentColor.copy(alpha = 0.4f)),
                    singleLine = true
                )
                Text(
                    text = " $cleanUnit",
                    style = if (isMultiChar) {
                        MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.width(if (isSodiumOrMg) 22.dp else if (isMultiChar) 32.dp else 22.dp)
                )
            }
        }
    }
}
