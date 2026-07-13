package dev.easonhuang.sustenance.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.easonhuang.sustenance.util.FoodNutrients

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodReviewDialog(
    nutrients: FoodNutrients,
    onDismiss: () -> Unit,
    onLog: (FoodNutrients, Double) -> Unit
) {
    var servingCount by remember { mutableFloatStateOf(1f) }
    
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
                Text(
                    text = nutrients.foodItem,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Base Serving: ${nutrients.servingSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                // Serving Selector
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
                            onClick = { if (servingCount > 0.1f) servingCount -= 0.1f },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.Remove, "Less")
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f", servingCount),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "SERVINGS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { servingCount += 0.1f },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.Add, "More")
                        }
                    }
                }

                // Nutrient Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val m = servingCount.toDouble()
                    NutrientChip("Calories", nutrients.calories * m, "kcal", MaterialTheme.colorScheme.primaryContainer)
                    NutrientChip("Protein", nutrients.protein * m, "g", Color(0xFFE3F2FD))
                    NutrientChip("Carbs", nutrients.carbs * m, "g", Color(0xFFFFF3E0))
                    NutrientChip("Fat", nutrients.fat * m, "g", Color(0xFFFBE9E7))
                    NutrientChip("Saturated Fat", nutrients.saturatedFat * m, "g", Color(0xFFE0F2F1))
                    NutrientChip("Fiber", nutrients.fiber * m, "g", Color(0xFFE8F5E9))
                    NutrientChip("Sugar", nutrients.sugar * m, "g", Color(0xFFF3E5F5))
                    NutrientChip("Sodium", nutrients.sodium * m, "mg", Color(0xFFEEEEEE))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLog(nutrients, servingCount.toDouble()) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Done, null)
                Spacer(Modifier.width(8.dp))
                Text("Log Food")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun NutrientChip(label: String, value: Double, unit: String, containerColor: Color) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format(if (value >= 100) "%.0f" else "%.1f", value) + " " + unit,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.8f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
}
