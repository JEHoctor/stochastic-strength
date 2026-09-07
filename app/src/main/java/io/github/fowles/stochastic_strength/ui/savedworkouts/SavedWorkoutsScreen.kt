package io.github.fowles.stochastic_strength.ui.savedworkouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fowles.stochastic_strength.ui.components.BackTopAppBar
import io.github.fowles.stochastic_strength.ui.components.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedWorkoutsScreen(
    onWorkoutTap: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SavedWorkoutsViewModel = viewModel(),
) {
    val workouts by viewModel.workouts.collectAsState()
    val createdId by viewModel.createdId.collectAsState()

    LaunchedEffect(createdId) {
        createdId?.let { viewModel.consumeCreated(); onWorkoutTap(it) }
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Workouts", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::createNew) {
                Icon(Icons.Default.Add, contentDescription = "New workout")
            }
        },
    ) { paddingValues ->
        val list = workouts
        when {
            list == null -> LoadingBox(contentPadding = paddingValues)
            list.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No saved workouts yet.\nTap + to build one, or use \"Save as workout...\" on a plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { w ->
                    Card(onClick = { onWorkoutTap(w.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${w.entries.size} exercises",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.delete(w.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${w.name}")
                            }
                        }
                    }
                }
            }
        }
    }
}
