package io.github.fowles.stochastic_strength.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutDetail

@Composable
fun SavedWorkoutPickerDialog(
    title: String,
    workouts: List<SavedWorkoutDetail>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (workouts.isEmpty()) {
                Text("No saved workouts yet. Create one from Home → Workouts.")
            } else {
                LazyColumn {
                    items(workouts, key = { it.id }) { w ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(w.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(w.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${w.entries.size} exercises",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
