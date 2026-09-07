package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fowles.stochastic_strength.StochasticStrengthApp
import io.github.fowles.stochastic_strength.domain.WorkoutRepository
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutDetail
import io.github.fowles.stochastic_strength.ui.components.DEFAULT_WORKOUT_NAME
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _createdId = MutableStateFlow<Long?>(null)
    val createdId: StateFlow<Long?> = _createdId.asStateFlow()

    private var createJob: Job? = null

    /** No-op while a create is in flight or its id is still waiting to be consumed (FAB double-tap). */
    fun createNew() {
        if (createJob?.isActive == true || _createdId.value != null) return
        createJob = viewModelScope.launch {
            _createdId.value = repository.saveWorkout(null, DEFAULT_WORKOUT_NAME, emptyList())
        }
    }

    fun consumeCreated() { _createdId.value = null }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteSavedWorkout(id) }
    }
}
