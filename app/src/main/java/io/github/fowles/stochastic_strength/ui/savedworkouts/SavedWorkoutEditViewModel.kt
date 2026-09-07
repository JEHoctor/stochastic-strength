package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.fowles.stochastic_strength.StochasticStrengthApp
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedWorkoutEditState(
    val loaded: Boolean = false,
    val name: String = "",
    val entries: List<SavedWorkoutEntry> = emptyList(),
)

class SavedWorkoutEditViewModel(
    application: Application,
    private val workoutId: Long,
) : AndroidViewModel(application) {
    private val repository = (application as StochasticStrengthApp).workoutRepository

    private val _state = MutableStateFlow(SavedWorkoutEditState())
    val state: StateFlow<SavedWorkoutEditState> = _state.asStateFlow()

    val allExercises: StateFlow<List<Exercise>> = repository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val detail = repository.getSavedWorkout(workoutId) ?: return@launch
            _state.value = SavedWorkoutEditState(loaded = true, name = detail.name, entries = detail.entries)
        }
    }

    fun setName(name: String) { _state.value = _state.value.copy(name = name) }

    fun addExercise(exerciseId: Long) {
        val exercise = allExercises.value.firstOrNull { it.id == exerciseId } ?: return
        if (_state.value.entries.any { it.exercise.id == exerciseId }) return
        _state.value = _state.value.copy(entries = _state.value.entries + SavedWorkoutEntry(exercise, null))
    }

    fun removeExercise(exerciseId: Long) {
        _state.value = _state.value.copy(entries = _state.value.entries.filterNot { it.exercise.id == exerciseId })
    }

    fun setReps(exerciseId: Long, reps: Int?) {
        _state.value = _state.value.copy(entries = _state.value.entries.map {
            if (it.exercise.id == exerciseId) it.copy(reps = reps) else it
        })
    }

    fun move(from: Int, to: Int) {
        val list = _state.value.entries.toMutableList()
        list.add(to, list.removeAt(from))
        _state.value = _state.value.copy(entries = list)
    }

    private var saveJob: Job? = null

    private suspend fun performSave() {
        val s = _state.value
        if (!s.loaded) return
        repository.saveWorkout(workoutId, s.name.trim().ifEmpty { "Untitled workout" }, s.entries)
    }

    /** Persist on leaving the screen. Safe to call more than once. */
    fun save() {
        saveJob = viewModelScope.launch {
            performSave()
        }
    }

    override fun onCleared() {
        val hasPendingSave = saveJob?.isActive == true
        super.onCleared() // cancels viewModelScope
        if (hasPendingSave) {
            CoroutineScope(Dispatchers.IO).launch { performSave() }
        }
    }

    companion object {
        fun factory(workoutId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] ?: error("No application")
                return SavedWorkoutEditViewModel(app, workoutId) as T
            }
        }
    }
}
