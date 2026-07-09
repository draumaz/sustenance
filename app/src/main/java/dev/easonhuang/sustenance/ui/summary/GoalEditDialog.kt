package dev.easonhuang.sustenance.ui.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.WeeklyStat

@Composable
fun GoalEditDialog(
    stat: WeeklyStat,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
) {
    var text by remember {
        mutableStateOf(
            if (stat.goal % 1f == 0f) stat.goal.toInt().toString() else stat.goal.toString()
        )
    }
    val title = if (stat.metric == Metric.CALORIC_BALANCE) "Caloric balance offset" else "${stat.metric.title} goal"
    val prompt = if (stat.metric == Metric.CALORIC_BALANCE) "Daily offset in ${stat.metric.unit}" else "Daily target in ${stat.metric.unit}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(prompt)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(stat.metric.unit) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toFloatOrNull()?.let { onSave(it) } ?: onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
