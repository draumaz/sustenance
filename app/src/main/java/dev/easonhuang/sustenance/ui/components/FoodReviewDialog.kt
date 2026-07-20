package dev.easonhuang.sustenance.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.util.FoodNutrients
import java.time.Instant
import java.util.Locale
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FoodReviewDialog(
    nutrients: FoodNutrients,
    onDismiss: () -> Unit,
    onLog: (FoodNutrients, Double, Instant) -> Unit,
) {
    android.util.Log.d("FoodReviewDialog", "Dialog opened with nutrients: $nutrients")
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
    var showTimePicker by remember { mutableStateOf(false) }

    val baseGrams = remember(servingSize) {
        val b = servingSize.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0
        android.util.Log.d("FoodReviewDialog", "baseGrams: $b (from servingSize='$servingSize')")
        b
    }

    fun safeParse(s: String): Double? = s.replace(',', '.').toDoubleOrNull()
    fun format(d: Double): String {
        val res = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
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

    fun scaleNutrients(newGrams: Double) {
        if (baseGrams <= 0) return
        val ratio = newGrams / baseGrams
        android.util.Log.d("FoodReviewDialog", "scaleNutrients: newGrams=$newGrams, baseGrams=$baseGrams, ratio=$ratio")
        val s = { label: String, base: Double -> 
            val scaled = base * ratio
            val formatted = format(scaled)
            android.util.Log.d("FoodReviewDialog", "  $label: base=$base, scaled=$scaled, formatted=$formatted")
            formatted
        }
        cal = s("Calories", calBase.doubleValue)
        prot = s("Protein", protBase.doubleValue)
        carb = s("Carbs", carbBase.doubleValue)
        fat = s("Fat", fatBase.doubleValue)
        satFat = s("SatFat", satFatBase.doubleValue)
        fiber = s("Fiber", fiberBase.doubleValue)
        sugar = s("Sugar", sugarBase.doubleValue)
        sodium = s("Sodium", sodiumBase.doubleValue)
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
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = foodItem.replace("\\s*\\(\\d+g\\)".toRegex(), "").trim(),
                    onValueChange = { foodItem = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

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

                if (showTimePicker) {
                    val timePickerState = rememberTimePickerState(
                        initialHour = selectedTime.hour,
                        initialMinute = selectedTime.minute
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Gram Selector
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
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
                            modifier = Modifier.size(48.dp)
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
                                textStyle = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.widthIn(min = 80.dp)
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
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.more))
                        }
                    }
                }

                // Nutrient Chips
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ratio = if (baseGrams > 0) currentGrams / baseGrams else 1.0
                    val updateBase = { label: String, base: MutableDoubleState, newValue: String ->
                        safeParse(newValue)?.let { num ->
                            val oldBase = base.doubleValue
                            if (ratio > 0) base.doubleValue = num / ratio else base.doubleValue = num
                            android.util.Log.d("FoodReviewDialog", "updateBase $label: newValue=$newValue, num=$num, ratio=$ratio, oldBase=$oldBase, newBase=${base.doubleValue}")
                        }
                        Unit
                    }

                    val items = listOf(
                        Triple(stringResource(R.string.metric_total_calories), cal to { s: String -> cal = s; updateBase("Calories", calBase, s) }, stringResource(R.string.unit_kcal) to MaterialTheme.colorScheme.primaryContainer),
                        Triple(stringResource(R.string.metric_protein), prot to { s: String -> prot = s; updateBase("Protein", protBase, s) }, stringResource(R.string.unit_g) to Color(0xFFE3F2FD)),
                        Triple(stringResource(R.string.metric_carbs), carb to { s: String -> carb = s; updateBase("Carbs", carbBase, s) }, stringResource(R.string.unit_g) to Color(0xFFFFF3E0)),
                        Triple(stringResource(R.string.metric_fat), fat to { s: String -> fat = s; updateBase("Fat", fatBase, s) }, stringResource(R.string.unit_g) to Color(0xFFFBE9E7)),
                        Triple(stringResource(R.string.metric_saturated_fat), satFat to { s: String -> satFat = s; updateBase("SatFat", satFatBase, s) }, stringResource(R.string.unit_g) to Color(0xFFE0F2F1)),
                        Triple(stringResource(R.string.metric_fiber), fiber to { s: String -> fiber = s; updateBase("Fiber", fiberBase, s) }, stringResource(R.string.unit_g) to Color(0xFFE8F5E9)),
                        Triple(stringResource(R.string.metric_sugar), sugar to { s: String -> sugar = s; updateBase("Sugar", sugarBase, s) }, stringResource(R.string.unit_g) to Color(0xFFF3E5F5)),
                        Triple(stringResource(R.string.metric_sodium), sodium to { s: String -> sodium = s; updateBase("Sodium", sodiumBase, s) }, stringResource(R.string.unit_mg) to Color(0xFFEEEEEE))
                    )

                    items.forEach { (label, state, meta) ->
                        EditableNutrientChip(
                            label = label,
                            value = state.first,
                            onValueChange = state.second,
                            unit = meta.first,
                            containerColor = meta.second,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val edited = FoodNutrients(
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
                    val combinedInstant = now.with(selectedTime).atZone(ZoneId.systemDefault()).toInstant()
                    onLog(edited, 1.0, combinedInstant)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
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
}

@Composable
private fun EditableNutrientChip(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor.copy(alpha = 0.8f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            val sanitized = it.replace(',', '.')
                            if (it.isEmpty() || sanitized.toDoubleOrNull() != null || it == "." || it == ",") {
                                onValueChange(it)
                            }
                        },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.End,
                            color = Color.Black
                        ),
                        modifier = Modifier.widthIn(min = 60.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(Color.Black.copy(alpha = 0.4f)),
                        singleLine = true
                    )
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
