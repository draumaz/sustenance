package xyz.draumaz.sustenance.ui.components

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.GoalCatalog
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.util.FoodNutrients
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FoodReviewDialog(
    nutrients: FoodNutrients,
    onDismiss: () -> Unit,
    onLog: (FoodNutrients, Double, Instant) -> Unit,
    judgementalMode: Boolean = false,
    gramIncrement: Int = 1,
    currentTotals: Map<Metric, Float> = emptyMap(),
    goals: Map<Metric, Float> = emptyMap(),
) {
    val view = LocalView.current
    var foodItem by remember { mutableStateOf(nutrients.foodItem) }
    // Extract only the numeric part for the editable state. Favor numbers followed by "g".
    var servingSize by remember(nutrients) {
        val s = nutrients.servingSize
        val gMatch = "(\\d+(?:[.,]\\d+)?)\\s*g".toRegex(RegexOption.IGNORE_CASE).find(s)
        val result = gMatch?.groupValues?.get(1) ?: "(\\d+(?:[.,]\\d+)?)".toRegex().find(s)?.groupValues?.get(1) ?: s
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

                Spacer(Modifier.height(8.dp))

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
                            val minusInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = {},
                                interactionSource = minusInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .repeatingClickable(
                                        interactionSource = minusInteractionSource,
                                        onAdjust = { multiplier ->
                                            if (currentGrams > 1.0) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                val next = (currentGrams - (gramIncrement * multiplier)).coerceAtLeast(1.0)
                                                scaleNutrients(next)
                                                currentGrams = next
                                            }
                                        }
                                    )
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

                            val plusInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = {},
                                interactionSource = plusInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .repeatingClickable(
                                        interactionSource = plusInteractionSource,
                                        onAdjust = { multiplier ->
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            val next = currentGrams + (gramIncrement * multiplier)
                                            scaleNutrients(next)
                                            currentGrams = next
                                        }
                                    )
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

                // Nutrition Facts Label
                val ratio = if (baseGrams > 0) currentGrams / baseGrams else 1.0
                val updateBase = { base: MutableDoubleState, newValue: String ->
                    safeParse(newValue)?.let { num ->
                        if (ratio > 0) base.doubleValue = num / ratio else base.doubleValue = num
                    }
                    Unit
                }

                val getGoal = { m: Metric -> goals[m] ?: GoalCatalog.defaults[m] ?: 0f }

                NutritionFactsLabel(
                    currentGrams = currentGrams,
                    cal = cal,
                    onCalChange = { s -> cal = s; updateBase(calBase, s) },
                    fat = fat,
                    onFatChange = { s -> fat = s; updateBase(fatBase, s) },
                    fatPercentage = formatTargetPercentage(safeParse(fat) ?: 0.0, getGoal(Metric.FAT)),
                    satFat = satFat,
                    onSatFatChange = { s -> satFat = s; updateBase(satFatBase, s) },
                    satFatPercentage = formatTargetPercentage(safeParse(satFat) ?: 0.0, getGoal(Metric.SATURATED_FAT)),
                    carb = carb,
                    onCarbChange = { s -> carb = s; updateBase(carbBase, s) },
                    carbPercentage = formatTargetPercentage(safeParse(carb) ?: 0.0, getGoal(Metric.CARBS)),
                    fiber = fiber,
                    onFiberChange = { s -> fiber = s; updateBase(fiberBase, s) },
                    fiberPercentage = formatTargetPercentage(safeParse(fiber) ?: 0.0, getGoal(Metric.FIBER)),
                    sugar = sugar,
                    onSugarChange = { s -> sugar = s; updateBase(sugarBase, s) },
                    sugarPercentage = formatTargetPercentage(safeParse(sugar) ?: 0.0, getGoal(Metric.SUGAR)),
                    prot = prot,
                    onProtChange = { s -> prot = s; updateBase(protBase, s) },
                    protPercentage = formatTargetPercentage(safeParse(prot) ?: 0.0, getGoal(Metric.PROTEIN)),
                    sodium = sodium,
                    onSodiumChange = { s -> sodium = s; updateBase(sodiumBase, s) },
                    sodiumPercentage = formatTargetPercentage(safeParse(sodium) ?: 0.0, getGoal(Metric.SODIUM)),
                    modifier = Modifier.fillMaxWidth()
                )
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
private fun NutritionFactsLabel(
    currentGrams: Double,
    cal: String,
    onCalChange: (String) -> Unit,
    fat: String,
    onFatChange: (String) -> Unit,
    fatPercentage: String?,
    satFat: String,
    onSatFatChange: (String) -> Unit,
    satFatPercentage: String?,
    carb: String,
    onCarbChange: (String) -> Unit,
    carbPercentage: String?,
    fiber: String,
    onFiberChange: (String) -> Unit,
    fiberPercentage: String?,
    sugar: String,
    onSugarChange: (String) -> Unit,
    sugarPercentage: String?,
    prot: String,
    onProtChange: (String) -> Unit,
    protPercentage: String?,
    sodium: String,
    onSodiumChange: (String) -> Unit,
    sodiumPercentage: String?,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, contentColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.nutrition_facts),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = contentColor
                )
                Text(
                    text = stringResource(R.string.per_serving, currentGrams.roundToInt()),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = contentColor
                )
            }

            HorizontalDivider(thickness = 7.dp, color = contentColor)

            // Calories Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.nutrition_calories),
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = contentColor
                    )
                    Spacer(Modifier.width(6.dp))
                    EditableNutrientValue(
                        value = cal,
                        onValueChange = onCalChange,
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = contentColor
                    )
                }

                Text(
                    text = stringResource(R.string.daily_value_header),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = contentColor,
                    textAlign = TextAlign.End
                )
            }

            HorizontalDivider(thickness = 3.dp, color = contentColor)

            // Fat Section
            NutritionFactsRow(
                label = stringResource(R.string.nutrition_fat),
                value = fat,
                onValueChange = onFatChange,
                unit = stringResource(R.string.unit_g),
                percentage = fatPercentage,
                isBold = true,
                color = contentColor
            )
            NutritionFactsRow(
                label = stringResource(R.string.nutrition_saturated),
                value = satFat,
                onValueChange = onSatFatChange,
                unit = stringResource(R.string.unit_g),
                percentage = satFatPercentage,
                isBold = false,
                isIndented = true,
                color = contentColor
            )

            HorizontalDivider(thickness = 1.dp, color = contentColor)

            // Sodium Section
            NutritionFactsRow(
                label = stringResource(R.string.nutrition_sodium),
                value = sodium,
                onValueChange = onSodiumChange,
                unit = stringResource(R.string.unit_mg),
                percentage = sodiumPercentage,
                isBold = true,
                color = contentColor
            )

            HorizontalDivider(thickness = 1.dp, color = contentColor)

            // Carbohydrate Section
            NutritionFactsRow(
                label = stringResource(R.string.nutrition_carbohydrate),
                value = carb,
                onValueChange = onCarbChange,
                unit = stringResource(R.string.unit_g),
                percentage = carbPercentage,
                isBold = true,
                color = contentColor
            )
            NutritionFactsRow(
                label = stringResource(R.string.nutrition_fibre),
                value = fiber,
                onValueChange = onFiberChange,
                unit = stringResource(R.string.unit_g),
                percentage = fiberPercentage,
                isBold = false,
                isIndented = true,
                color = contentColor
            )
            NutritionFactsRow(
                label = stringResource(R.string.nutrition_sugars),
                value = sugar,
                onValueChange = onSugarChange,
                unit = stringResource(R.string.unit_g),
                percentage = sugarPercentage,
                isBold = false,
                isIndented = true,
                color = contentColor
            )

            HorizontalDivider(thickness = 1.dp, color = contentColor)

            // Protein Section
            NutritionFactsRow(
                label = stringResource(R.string.nutrition_protein),
                value = prot,
                onValueChange = onProtChange,
                unit = stringResource(R.string.unit_g),
                percentage = protPercentage,
                isBold = true,
                color = contentColor
            )

            HorizontalDivider(thickness = 3.dp, color = contentColor)

            // Footnote
            val footnoteText = buildAnnotatedString {
                append(stringResource(R.string.dv_footnote_part1))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.dv_footnote_a_little))
                }
                append(stringResource(R.string.dv_footnote_part2))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.dv_footnote_a_lot))
                }
            }

            Text(
                text = footnoteText,
                style = TextStyle(
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp
                ),
                color = contentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun NutritionFactsRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    percentage: String?,
    isBold: Boolean = false,
    isIndented: Boolean = false,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isIndented) 22.dp else 10.dp,
                end = 10.dp,
                top = 2.dp,
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                ),
                color = color
            )
            Spacer(Modifier.width(4.dp))
            EditableNutrientValue(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                ),
                color = color
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                ),
                color = color
            )
        }

        if (percentage != null) {
            Text(
                text = "$percentage %",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = color,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun EditableNutrientValue(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    color: Color
) {
    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            val sanitized = newValue.replace(',', '.')
            if (newValue.isEmpty() || sanitized.toDoubleOrNull() != null || newValue == "." || newValue == ",") {
                onValueChange(newValue)
            }
        },
        textStyle = textStyle.copy(
            color = color,
            textAlign = TextAlign.Start
        ),
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .widthIn(min = 20.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(color.copy(alpha = 0.6f)),
        singleLine = true
    )
}

private fun Modifier.repeatingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onAdjust: (stepMultiplier: Int) -> Unit
): Modifier = this.pointerInput(enabled, interactionSource) {
    if (!enabled) return@pointerInput
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val pressInteraction = PressInteraction.Press(down.position)

            var repeatJob: Job? = null
            try {
                launch {
                    interactionSource.emit(pressInteraction)
                }
                repeatJob = launch {
                    onAdjust(1)
                    delay(350L)

                    var tickCount = 0
                    while (isActive) {
                        tickCount++
                        val (delayMs, stepMultiplier) = when {
                            tickCount < 5 -> 120L to 1
                            else -> 90L to 2
                        }
                        onAdjust(stepMultiplier)
                        delay(delayMs)
                    }
                }

                val up = waitForUpOrCancellation()
                repeatJob.cancel()
                launch {
                    if (up != null) {
                        interactionSource.emit(PressInteraction.Release(pressInteraction))
                    } else {
                        interactionSource.emit(PressInteraction.Cancel(pressInteraction))
                    }
                }
            } catch (_: CancellationException) {
                repeatJob?.cancel()
                launch {
                    interactionSource.emit(PressInteraction.Cancel(pressInteraction))
                }
            }
        }
    }
}

@Preview
@Composable
private fun FoodReviewDialogPreview() {
    MaterialTheme {
        FoodReviewDialog(
            nutrients = FoodNutrients(
                foodItem = "Apple",
                servingSize = "100g",
                calories = 52.0,
                protein = 0.3,
                carbs = 14.0,
                fat = 0.2,
                saturatedFat = 0.0,
                fiber = 2.4,
                sugar = 10.0,
                sodium = 1.0
            ),
            onDismiss = {},
            onLog = { _, _, _ -> },
            judgementalMode = true
        )
    }
}

