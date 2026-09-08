package io.github.fowles.stochastic_strength.ui.savedworkouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
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

    var showDiscard by rememberSaveable { mutableStateOf(false) }

    // Only Done saves. Back leaves without saving, asking first if that would lose edits.
    val leave = { if (viewModel.hasUnsavedChanges()) showDiscard = true else onBack() }
    BackHandler(onBack = leave)

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Tap Done to keep them.") },
            confirmButton = { TextButton(onClick = { showDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } },
        )
    }

    val title = if (workoutId == SavedWorkoutEditViewModel.NEW_WORKOUT_ID) "New workout" else "Edit workout"
    Scaffold(topBar = { BackTopAppBar(title = title, onBack = leave) }) { paddingValues ->
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
                // No label: M3 hides the placeholder behind an in-box label until first focus, and the
                // derived name must be visible (and track exercise edits) from the start.
                placeholder = { Text(SavedWorkoutNaming.defaultName(state.entries.map { it.exercise.name })) },
                supportingText = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Drag to reorder · swipe left to remove · − / + sets reps (0 = session default)",
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
                                onRepsChange = { reps -> viewModel.setReps(entry.exercise.id, reps) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Add exercise")
            }
            Button(
                onClick = { viewModel.save(); onBack() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Done")
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
}

@Composable
private fun EntryRow(
    entry: SavedWorkoutEntry,
    dragHandleModifier: Modifier,
    onRemove: () -> Unit,
    onRepsChange: (Int?) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }
            .collect { if (it == SwipeToDismissBoxValue.EndToStart) onRemove() }
    }
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
                .padding(vertical = 4.dp),
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier.padding(start = 4.dp, end = 8.dp).size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium)
                // The stepper already shows a chosen count; 0 is the one value that needs a word.
                if (entry.reps == null) Text(
                    "Session default reps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RepsStepper(reps = entry.reps, onRepsChange = onRepsChange)
        }
    }
}

/** Inline reps control. 0 is not a rep count but "leave it to the session", i.e. a null override. */
@Composable
private fun RepsStepper(reps: Int?, onRepsChange: (Int?) -> Unit) {
    val value = reps ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onRepsChange((value - 1).takeIf { it > 0 }) },
            enabled = value > 0,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "One rep fewer")
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            // At 0 there is nothing to count, so match the disabled "−" rather than the live number.
            color = MaterialTheme.colorScheme.onSurface
                .copy(alpha = if (value == 0) DISABLED_ALPHA else 1f),
            modifier = Modifier.widthIn(min = 24.dp),
        )
        IconButton(
            onClick = { onRepsChange((value + 1).coerceAtMost(MAX_REPS)) },
            enabled = value < MAX_REPS,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "One rep more")
        }
    }
}

private const val MAX_REPS = 50

/** Material 3's disabled-content alpha, so the dimmed 0 matches the disabled "−" beside it. */
private const val DISABLED_ALPHA = 0.38f
