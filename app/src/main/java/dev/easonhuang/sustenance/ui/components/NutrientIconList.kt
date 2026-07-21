package dev.easonhuang.sustenance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.util.FoodNutrients
import dev.easonhuang.sustenance.data.formatValue
import dev.easonhuang.sustenance.R

@Composable
fun NutrientIconList(
    nutrients: FoodNutrients,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NutrientIconItem(Metric.PROTEIN, nutrients.protein)
        NutrientIconItem(Metric.CARBS, nutrients.carbs)
        NutrientIconItem(Metric.FAT, nutrients.fat)
        if (nutrients.sugar > 1.0) NutrientIconItem(Metric.SUGAR, nutrients.sugar)
        if (nutrients.sodium > 50.0) NutrientIconItem(Metric.SODIUM, nutrients.sodium)
    }
}

@Composable
private fun NutrientIconItem(metric: Metric, value: Double) {
    if (value < 0.1) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = metric.icon,
            contentDescription = stringResource(metric.titleRes),
            tint = metric.accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.padding(horizontal = 1.dp))
        Text(
            text = metric.formatValue(value.toFloat()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(metric.unitRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
