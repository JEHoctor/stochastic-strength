package io.github.fowles.stochastic_strength.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup

/** Two chip rows (muscle, equipment) shared by the exercise library and the exercise picker. */
@Composable
fun ExerciseFilterChips(
    selectedMuscle: MuscleGroup?,
    selectedEquipment: Equipment?,
    onMuscle: (MuscleGroup?) -> Unit,
    onEquipment: (Equipment?) -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column {
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = selectedMuscle == null, onClick = { onMuscle(null) }, label = { Text("All") }) }
                items(MuscleGroup.entries) { muscle ->
                    FilterChip(selected = selectedMuscle == muscle, onClick = { onMuscle(muscle) }, label = { Text(muscle.displayName()) })
                }
            }
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = selectedEquipment == null, onClick = { onEquipment(null) }, label = { Text("All") }) }
                items(Equipment.entries) { equipment ->
                    FilterChip(selected = selectedEquipment == equipment, onClick = { onEquipment(equipment) }, label = { Text(equipment.displayName()) })
                }
            }
        }
    }
}

/** Pure filter used by both callers so the picker and the library agree. */
fun filterExercises(exercises: List<Exercise>, muscle: MuscleGroup?, equipment: Equipment?, query: String = ""): List<Exercise> =
    exercises
        .filter { muscle == null || it.primaryMuscle == muscle }
        .filter { equipment == null || it.equipment == equipment }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
