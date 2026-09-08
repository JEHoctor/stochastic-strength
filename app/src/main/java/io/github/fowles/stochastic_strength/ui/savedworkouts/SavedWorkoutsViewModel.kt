package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fowles.stochastic_strength.StochasticStrengthApp
import io.github.fowles.stochastic_strength.domain.WorkoutRepository
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutDetail
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedWorkoutsViewModel(
    application: Application,
    private val repository: WorkoutRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) :
        this(application, (application as StochasticStrengthApp).workoutRepository)

    val workouts: StateFlow<List<SavedWorkoutDetail>?> = repository.observeSavedWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // A new workout is not a row until the editor has something to save: see
    // SavedWorkoutEditViewModel.NEW_WORKOUT_ID.

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteSavedWorkout(id) }
    }
}
