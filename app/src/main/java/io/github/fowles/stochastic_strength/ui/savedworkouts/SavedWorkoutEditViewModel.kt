package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.fowles.stochastic_strength.StochasticStrengthApp
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.domain.WorkoutRepository
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutNaming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the workout row behind the editor has been read yet, and whether it was still there. */
enum class LoadStatus { LOADING, LOADED, MISSING }

data class SavedWorkoutEditState(
    val status: LoadStatus = LoadStatus.LOADING,
    val name: String = "",
    val entries: List<SavedWorkoutEntry> = emptyList(),
)

class SavedWorkoutEditViewModel(
    application: Application,
    private val workoutId: Long,
    private val repository: WorkoutRepository,
) : AndroidViewModel(application) {
    constructor(application: Application, workoutId: Long) :
        this(application, workoutId, (application as StochasticStrengthApp).workoutRepository)

    private val _state = MutableStateFlow(SavedWorkoutEditState())
    val state: StateFlow<SavedWorkoutEditState> = _state.asStateFlow()

    val allExercises: StateFlow<List<Exercise>> = repository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val detail = repository.getSavedWorkout(workoutId)
            _state.value = if (detail == null) {
                // Deleted underneath us (or a stale nav argument): say so instead of spinning forever.
                SavedWorkoutEditState(LoadStatus.MISSING)
            } else {
                // An unnamed workout edits as empty text, with the derived name as placeholder.
                val name = if (SavedWorkoutNaming.isPlaceholder(detail.name)) "" else detail.name
                SavedWorkoutEditState(LoadStatus.LOADED, name, detail.entries)
            }
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
        if (s.status != LoadStatus.LOADED) return
        val name = s.name.trim()
        // A fresh editor the user backed out of without touching anything: leave no row behind.
        if (s.entries.isEmpty() && name.isEmpty()) {
            repository.deleteSavedWorkout(workoutId)
            return
        }
        // Empty stays empty: the list and pickers derive a name from the exercises.
        repository.saveWorkout(workoutId, name, s.entries)
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
            // Detached from viewModelScope: nothing above us can catch a throw (e.g. the workout
            // was deleted underneath us), so it would take the process down.
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { performSave() }
                    .onFailure { Log.w(TAG, "saved-workout save after clear failed", it) }
            }
        }
    }

    companion object {
        private const val TAG = "SavedWorkoutEdit"

        fun factory(workoutId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] ?: error("No application")
                return SavedWorkoutEditViewModel(app, workoutId) as T
            }
        }
    }
}
