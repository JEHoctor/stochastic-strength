package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fowles.stochastic_strength.StochasticStrengthApp
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedWorkoutsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StochasticStrengthApp).workoutRepository

    val workouts: StateFlow<List<SavedWorkoutDetail>?> = repository.observeSavedWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _createdId = MutableStateFlow<Long?>(null)
    val createdId: StateFlow<Long?> = _createdId.asStateFlow()

    fun createNew() {
        viewModelScope.launch { _createdId.value = repository.saveWorkout(null, "Untitled workout", emptyList()) }
    }

    fun consumeCreated() { _createdId.value = null }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteSavedWorkout(id) }
    }
}
