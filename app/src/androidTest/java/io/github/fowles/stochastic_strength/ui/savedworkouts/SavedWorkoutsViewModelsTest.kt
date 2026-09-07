package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fowles.stochastic_strength.data.AppDatabase
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import io.github.fowles.stochastic_strength.domain.WorkoutRepository
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import io.github.fowles.stochastic_strength.ui.components.DEFAULT_WORKOUT_NAME
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedWorkoutsViewModelsTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private lateinit var app: Application
    private lateinit var bench: Exercise

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        app = context.applicationContext as Application
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = WorkoutRepository(db)
        val benchId = db.exerciseDao().insert(
            Exercise(name = "Bench", primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
        )
        bench = db.exerciseDao().getById(benchId)!!
    }

    @After
    fun tearDown() = db.close()

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun await(what: String, predicate: suspend () -> Boolean) = runBlocking {
        repeat(100) {
            if (predicate()) return@runBlocking
            delay(20)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    private fun savedCount(): Int = runBlocking { db.savedWorkoutDao().getAll().size }

    /** `viewModel()` uses AndroidViewModelFactory, which reflects on an exact (Application) constructor. */
    @Test
    fun savedWorkoutsViewModel_keepsTheApplicationOnlyConstructor() {
        assertNotNull(SavedWorkoutsViewModel::class.java.getConstructor(Application::class.java))
    }

    @Test
    fun createNew_twiceInARow_createsOnlyOneWorkout() {
        val vm = onMain { SavedWorkoutsViewModel(app, repo) }

        onMain { vm.createNew(); vm.createNew() }
        await("created id") { vm.createdId.value != null }
        runBlocking { delay(200) } // let any second create land

        assertEquals(1, savedCount())
    }

    @Test
    fun createNew_afterConsumingCreatedId_createsAnother() {
        val vm = onMain { SavedWorkoutsViewModel(app, repo) }

        onMain { vm.createNew() }
        await("first created id") { vm.createdId.value != null }
        onMain { vm.consumeCreated(); vm.createNew() }
        await("second created id") { vm.createdId.value != null }

        assertEquals(2, savedCount())
    }

    @Test
    fun save_onUntouchedEmptyWorkout_deletesIt() = runBlocking {
        val id = repo.saveWorkout(null, DEFAULT_WORKOUT_NAME, emptyList())
        val vm = onMain { SavedWorkoutEditViewModel(app, id, repo) }
        await("loaded") { vm.state.value.status == LoadStatus.LOADED }

        onMain { vm.save() }
        await("deleted") { repo.getSavedWorkout(id) == null }

        assertNull(repo.getSavedWorkout(id))
    }

    @Test
    fun edit_onDeletedWorkout_reportsMissing() = runBlocking {
        val id = repo.saveWorkout(null, DEFAULT_WORKOUT_NAME, emptyList())
        repo.deleteSavedWorkout(id)

        val vm = onMain { SavedWorkoutEditViewModel(app, id, repo) }

        await("missing") { vm.state.value.status == LoadStatus.MISSING }
    }

    @Test
    fun save_onMissingWorkout_writesNothing() = runBlocking {
        val id = repo.saveWorkout(null, DEFAULT_WORKOUT_NAME, emptyList())
        repo.deleteSavedWorkout(id)
        val vm = onMain { SavedWorkoutEditViewModel(app, id, repo) }
        await("missing") { vm.state.value.status == LoadStatus.MISSING }

        onMain { vm.setName("Push day"); vm.save() }
        runBlocking { delay(200) } // let any write land

        assertNull(repo.getSavedWorkout(id))
        assertEquals(0, savedCount())
    }

    @Test
    fun save_onNamedEmptyWorkout_keepsIt() = runBlocking {
        val id = repo.saveWorkout(null, DEFAULT_WORKOUT_NAME, emptyList())
        val vm = onMain { SavedWorkoutEditViewModel(app, id, repo) }
        await("loaded") { vm.state.value.status == LoadStatus.LOADED }

        onMain { vm.setName("Push day"); vm.save() }
        await("renamed") { repo.getSavedWorkout(id)?.name == "Push day" }

        assertNotNull(repo.getSavedWorkout(id))
    }

    @Test
    fun save_onDefaultNamedWorkoutWithExercises_keepsIt() = runBlocking {
        val id = repo.saveWorkout(null, DEFAULT_WORKOUT_NAME, listOf(SavedWorkoutEntry(bench, 8)))
        val vm = onMain { SavedWorkoutEditViewModel(app, id, repo) }
        await("loaded") { vm.state.value.status == LoadStatus.LOADED }

        onMain { vm.save() }
        runBlocking { delay(200) }

        val detail = repo.getSavedWorkout(id)
        assertNotNull(detail)
        assertEquals(listOf(bench.id), detail!!.entries.map { it.exercise.id })
    }
}
