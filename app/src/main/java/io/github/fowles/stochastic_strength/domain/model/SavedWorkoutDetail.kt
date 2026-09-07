package io.github.fowles.stochastic_strength.domain.model

import io.github.fowles.stochastic_strength.data.model.Exercise

/** One row of a saved workout with its exercise resolved. `reps == null` = session decides. */
data class SavedWorkoutEntry(val exercise: Exercise, val reps: Int?)

data class SavedWorkoutDetail(
    val id: Long,
    val name: String,
    val entries: List<SavedWorkoutEntry>,
)
