package dev.easonhuang.sustenance.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.util.FoodNutrients

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodReviewDialog(
    nutrients: FoodNutrients,
    onDismiss: () -> Unit,
    onLog: (FoodNutrients, Double) -> Unit,
) {
    var foodItem by remember { mutableStateOf(nutrients.foodItem) }
    // Extract only the numeric part for the editable state to avoid "100g g"
    var servingSize by remember {
        mutableStateOf("(\\d+)".toRegex().find(nutrients.servingSize)?.groupValues?.get(1) ?: nutrients.servingSize)
    }

    val baseGrams = remember(servingSize) {
        servingSize.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0
    }

    fun format(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else String.format("%.1f", d)

    var currentGrams by remember { mutableDoubleStateOf(baseGrams) }
    var quantityText by remember { mutableStateOf(currentGrams.toInt().toString()) }

    // Keep quantityText in sync when currentGrams is changed via buttons
    LaunchedEffect(currentGrams) {
        val expected = currentGrams.toInt().toString()
        if (quantityText != expected) {
            quantityText = expected
        }
    }
    
    var cal by remember { mutableStateOf(format(nutrients.calories)) }
    var prot by remember { mutableStateOf(format(nutrients.protein)) }
    var carb by remember { mutableStateOf(format(nutrients.carbs)) }
    var fat by remember { mutableStateOf(format(nutrients.fat)) }
    var satFat by remember { mutableStateOf(format(nutrients.saturatedFat)) }
    var fiber by remember { mutableStateOf(format(nutrients.fiber)) }
    var sugar by remember { mutableStateOf(format(nutrients.sugar)) }
    var sodium by remember { mutableStateOf(format(nutrients.sodium)) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.Center,
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Text(
//                        text = stringResource(R.string.base_serving),
//                        style = MaterialTheme.typography.bodySmall,
//                        textAlign = TextAlign.Center,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                        modifier = Modifier.offset(x = 30.dp)
//                    )
//                    BasicTextField(
//                        value = servingSize,
//                        modifier = Modifier.offset(x = 30.dp),
//                        onValueChange = { servingSize = it },
//                        textStyle = MaterialTheme.typography.bodySmall.copy(
//                            color = MaterialTheme.colorScheme.onSurfaceVariant,
//                            textAlign = TextAlign.Start
//                        ),
//                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant)
//                    )
//                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
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
                            onClick = { if (currentGrams > 1) currentGrams -= 1 },
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
                                    quantityText = newValue
                                    val num = newValue.replace(',', '.').toDoubleOrNull()
                                    if (num != null && num >= 0) {
                                        currentGrams = num
                                    }
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
                            onClick = { currentGrams += 1 },
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val m = if (baseGrams > 0) currentGrams / baseGrams else 1.0
                    
                    val items = listOf(
                        Triple(stringResource(R.string.grams_label), servingSize to { s: String -> servingSize = s }, stringResource(R.string.unit_g) to MaterialTheme.colorScheme.primaryContainer),
                        Triple(stringResource(R.string.metric_total_calories), cal to { s: String -> cal = s }, stringResource(R.string.unit_kcal) to MaterialTheme.colorScheme.primaryContainer),
                        Triple(stringResource(R.string.metric_protein), prot to { s: String -> prot = s }, stringResource(R.string.unit_g) to Color(0xFFE3F2FD)),
                        Triple(stringResource(R.string.metric_carbs), carb to { s: String -> carb = s }, stringResource(R.string.unit_g) to Color(0xFFFFF3E0)),
                        Triple(stringResource(R.string.metric_fat), fat to { s: String -> fat = s }, stringResource(R.string.unit_g) to Color(0xFFFBE9E7)),
                        Triple(stringResource(R.string.metric_saturated_fat), satFat to { s: String -> satFat = s }, stringResource(R.string.unit_g) to Color(0xFFE0F2F1)),
                        Triple(stringResource(R.string.metric_fiber), fiber to { s: String -> fiber = s }, stringResource(R.string.unit_g) to Color(0xFFE8F5E9)),
                        Triple(stringResource(R.string.metric_sugar), sugar to { s: String -> sugar = s }, stringResource(R.string.unit_g) to Color(0xFFF3E5F5)),
                        Triple(stringResource(R.string.metric_sodium), sodium to { s: String -> sodium = s }, stringResource(R.string.unit_mg) to Color(0xFFEEEEEE))
                    )

                    items.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { (label, state, meta) ->
                                EditableNutrientChip(
                                    label = label,
                                    value = state.first,
                                    onValueChange = state.second,
                                    multiplier = m,
                                    unit = meta.first,
                                    containerColor = meta.second,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val edited = FoodNutrients(
                        foodItem = foodItem,
                        servingSize = servingSize,
                        calories = cal.toDoubleOrNull() ?: 0.0,
                        protein = prot.toDoubleOrNull() ?: 0.0,
                        carbs = carb.toDoubleOrNull() ?: 0.0,
                        fat = fat.toDoubleOrNull() ?: 0.0,
                        saturatedFat = satFat.toDoubleOrNull() ?: 0.0,
                        fiber = fiber.toDoubleOrNull() ?: 0.0,
                        sugar = sugar.toDoubleOrNull() ?: 0.0,
                        sodium = sodium.toDoubleOrNull() ?: 0.0
                    )
                    onLog(edited, currentGrams / baseGrams)
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
    multiplier: Double,
    unit: String,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    val displayValue = (value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.').toDoubleOrNull() ?: 0.0) * multiplier
    
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Value Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
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
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.Black.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(Color.Black.copy(alpha = 0.4f)),
                    singleLine = true
                )
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
            
            // Total Preview (Smaller)
            if (multiplier != 1.0) {
                val formattedTotal = if (displayValue >= 100) String.format("%.0f", displayValue) else String.format("%.1f", displayValue)
                Text(
                    text = stringResource(R.string.total_preview, formattedTotal),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color.Black.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }

            // Bottom Label
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.Black.copy(alpha = 0.6f),
                maxLines = 1,
                textAlign = TextAlign.Left
            )
        }
    }
}
