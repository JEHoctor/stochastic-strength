package io.github.fowles.stochastic_strength.domain

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fowles.stochastic_strength.data.AppDatabase
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import io.github.fowles.stochastic_strength.data.model.SetFeedback
import io.github.fowles.stochastic_strength.data.model.WorkoutSession
import io.github.fowles.stochastic_strength.data.model.WorkoutSet
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedWorkoutRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private lateinit var bench: Exercise
    private lateinit var squat: Exercise

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = WorkoutRepository(db)
        val benchId = db.exerciseDao().insert(Exercise(name = "Bench", primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val squatId = db.exerciseDao().insert(Exercise(name = "Squat", primaryMuscle = MuscleGroup.QUADS, equipment = Equipment.BARBELL))
        bench = db.exerciseDao().getById(benchId)!!
        squat = db.exerciseDao().getById(squatId)!!
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveWorkout_roundTrips_orderAndReps_andUpdateReplacesRows() = runBlocking {
        val id = repo.saveWorkout(null, "Legs first", listOf(SavedWorkoutEntry(squat, 5), SavedWorkoutEntry(bench, null)))
        val detail = repo.getSavedWorkout(id)!!
        assertEquals("Legs first", detail.name)
        assertEquals(listOf(squat.id, bench.id), detail.entries.map { it.exercise.id })
        assertEquals(listOf(5, null), detail.entries.map { it.reps })

        val sameId = repo.saveWorkout(id, "Bench only", listOf(SavedWorkoutEntry(bench, 8)))
        assertEquals(id, sameId)
        val updated = repo.getSavedWorkout(id)!!
        assertEquals("Bench only", updated.name)
        assertEquals(listOf(bench.id), updated.entries.map { it.exercise.id })
        assertEquals(1, db.savedWorkoutDao().getAllExerciseRows().size)
    }

    @Test
    fun observeSavedWorkouts_dropsRowsWhoseExerciseIsGone() = runBlocking {
        val id = repo.saveWorkout(null, "W", listOf(SavedWorkoutEntry(bench, null), SavedWorkoutEntry(squat, null)))
        db.exerciseDao().deleteAll()
        db.exerciseDao().insert(bench)   // only bench survives, with its original id
        val list = repo.observeSavedWorkouts().first()
        assertEquals(1, list.size)
        assertEquals(id, list[0].id)
        assertEquals(listOf(bench.id), list[0].entries.map { it.exercise.id })
    }

    @Test
    fun deleteSavedWorkout_removesDetailAndRows() = runBlocking {
        val id = repo.saveWorkout(null, "W", listOf(SavedWorkoutEntry(bench, null)))
        repo.deleteSavedWorkout(id)
        assertNull(repo.getSavedWorkout(id))
        assertEquals(0, db.savedWorkoutDao().getAllExerciseRows().size)
    }

    @Test
    fun saveSessionAsWorkout_ordersByFirstSet_andRecordsFirstSetTargetReps() = runBlocking {
        val sid = db.workoutSessionDao().insert(WorkoutSession(startTime = 1000, endTime = 5000))
        db.workoutSetDao().insert(WorkoutSet(sessionId = sid, exerciseId = squat.id, setNumber = 1, targetWeight = 100f, targetReps = 5, feedback = SetFeedback.RIR_2_4, completedAt = 1100))
        db.workoutSetDao().insert(WorkoutSet(sessionId = sid, exerciseId = squat.id, setNumber = 2, targetWeight = 100f, targetReps = 5, feedback = SetFeedback.RIR_2_4, completedAt = 1200))
        db.workoutSetDao().insert(WorkoutSet(sessionId = sid, exerciseId = bench.id, setNumber = 1, targetWeight = 60f, targetReps = 8, feedback = SetFeedback.RIR_2_4, completedAt = 1300))
        val id = repo.saveSessionAsWorkout(sid, "Replay")
        val detail = repo.getSavedWorkout(id)!!
        assertEquals(listOf(squat.id, bench.id), detail.entries.map { it.exercise.id })
        assertEquals(listOf(5, 8), detail.entries.map { it.reps })
    }
}
