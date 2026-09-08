package io.github.fowles.stochastic_strength.ui.savedworkouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutNaming
import io.github.fowles.stochastic_strength.ui.components.BackTopAppBar
import io.github.fowles.stochastic_strength.ui.components.ExercisePickerSheet
import io.github.fowles.stochastic_strength.ui.components.LoadingBox
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedWorkoutEditScreen(
    workoutId: Long,
    onBack: () -> Unit,
    viewModel: SavedWorkoutEditViewModel = viewModel(factory = SavedWorkoutEditViewModel.factory(workoutId)),
) {
    val state by viewModel.state.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var repsFor by rememberSaveable { mutableStateOf<Long?>(null) }

    val saveAndBack = { viewModel.save(); onBack() }
    BackHandler(onBack = saveAndBack)

    val title = if (workoutId == SavedWorkoutEditViewModel.NEW_WORKOUT_ID) "New workout" else "Edit workout"
    Scaffold(topBar = { BackTopAppBar(title = title, onBack = saveAndBack) }) { paddingValues ->
        when (state.status) {
            LoadStatus.LOADING -> {
                LoadingBox(contentPadding = paddingValues)
                return@Scaffold
            }
            LoadStatus.MISSING -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("This workout no longer exists.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }
            LoadStatus.LOADED -> Unit
        }
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                placeholder = { Text(SavedWorkoutNaming.defaultName(state.entries.map { it.exercise.name })) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Drag to reorder · swipe left to remove · tap to set reps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            HorizontalDivider()
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                viewModel.move(from.index, to.index)
            }
            LazyColumn(state = lazyListState, modifier = Modifier.weight(1f)) {
                items(state.entries, key = { it.exercise.id }) { entry ->
                    ReorderableItem(reorderState, key = entry.exercise.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "dragElevation")
                        Column(modifier = Modifier.animateItem().graphicsLayer { shadowElevation = elevation.toPx() }) {
                            EntryRow(
                                entry = entry,
                                dragHandleModifier = Modifier.draggableHandle(),
                                onRemove = { viewModel.removeExercise(entry.exercise.id) },
                                onTap = { repsFor = entry.exercise.id },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Add exercise")
            }
        }
    }

    if (showPicker) {
        val excludeIds = remember(state.entries) { state.entries.map { it.exercise.id }.toSet() }
        ExercisePickerSheet(
            exercises = allExercises,
            excludeIds = excludeIds,
            onPick = { id -> showPicker = false; viewModel.addExercise(id) },
            onDismiss = { showPicker = false },
        )
    }
    repsFor?.let { id ->
        val entry = state.entries.firstOrNull { it.exercise.id == id }
        if (entry != null) {
            RepsDialog(
                exerciseName = entry.exercise.name,
                initial = entry.reps,
                onConfirm = { reps -> viewModel.setReps(id, reps); repsFor = null },
                onDismiss = { repsFor = null },
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: SavedWorkoutEntry,
    dragHandleModifier: Modifier,
    onRemove: () -> Unit,
    onTap: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { onRemove(); true } else false },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onTap)
                .padding(vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier.padding(start = 4.dp, end = 8.dp).size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.reps?.let { "$it reps" } ?: "Session default reps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RepsDialog(
    exerciseName: String,
    initial: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exerciseName) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(3) },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull()?.takeIf { it > 0 }) }) { Text("Set") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null) }) { Text("Use session default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
