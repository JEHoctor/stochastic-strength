package io.github.fowles.stochastic_strength.ui.workout

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fowles.stochastic_strength.data.AppDatabase
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import io.github.fowles.stochastic_strength.data.model.SetFeedback
import io.github.fowles.stochastic_strength.data.model.Sex
import io.github.fowles.stochastic_strength.data.model.StrengthLevel
import io.github.fowles.stochastic_strength.data.model.UserProfile
import io.github.fowles.stochastic_strength.data.model.WeightUnit
import io.github.fowles.stochastic_strength.domain.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A timed set records how long it was actually held, not the prescribed duration. */
@RunWith(AndroidJUnit4::class)
class TimedSetDurationTest {

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var controller: WorkoutSessionController

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.userProfileDao().insert(
                UserProfile(sex = Sex.MALE, strengthLevel = StrengthLevel.MEDIUM, weightUnit = WeightUnit.KG)
            )
            db.exerciseDao().insertAll(listOf(
                Exercise(
                    name = "Plank", primaryMuscle = MuscleGroup.CORE, secondaryMuscles = emptyList(),
                    equipment = Equipment.BODYWEIGHT, isTimed = true,
                ),
            ))
        }
        scope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Starts a one-exercise (Plank) session, parked on its first active set. */
    private suspend fun startSession(timedSetSeconds: Int = 60) {
        controller = WorkoutSessionController(
            db, WorkoutRepository(db), WorkoutSessionBus(), scope,
            timedSetSeconds = timedSetSeconds,
        )
        controller.initializeSession(
            locationId = null, locationName = null,
            preferredExerciseCount = 1, preferredRepMin = 5, preferredRepMax = 10,
            weightUnit = WeightUnit.KG,
        )
        controller.adjustExerciseCount(1)
        awaitStateNotLoading()
        controller.startFirstExercise()
        awaitActiveSet()
    }

    private suspend fun awaitStateNotLoading() {
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline && controller.state.value is WorkoutState.Loading) {
            delay(20)
        }
    }

    private suspend fun awaitActiveSet(): WorkoutState.ActiveSet {
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            val s = controller.state.value
            if (s is WorkoutState.ActiveSet) return s
            delay(20)
        }
        error("State did not become ActiveSet; was ${controller.state.value}")
    }

    private suspend fun awaitResting(timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (controller.state.value is WorkoutState.Resting) return
            delay(20)
        }
        error("State did not become Resting; was ${controller.state.value}")
    }

    @Test
    fun tooHardPartWayThrough_recordsSecondsHeld() = runBlocking {
        startSession()
        controller.startTimedSet()
        delay(2_500)
        controller.recordFeedback(SetFeedback.TOO_HARD)
        awaitResting()
        delay(100)
        val recorded = db.workoutSetDao().getAll().single().durationSeconds
        assertTrue("expected ~2-3s held, got $recorded", recorded != null && recorded in 2..3)
    }

    @Test
    fun tooHardBeforeStartingTimer_recordsZeroSeconds() = runBlocking {
        startSession()
        controller.recordFeedback(SetFeedback.TOO_HARD)
        awaitResting()
        delay(100)
        assertEquals(0, db.workoutSetDao().getAll().single().durationSeconds)
    }

    @Test
    fun timerRunsOut_recordsFullDuration() = runBlocking {
        startSession(timedSetSeconds = 3)
        controller.startTimedSet()
        awaitResting(timeoutMs = 6000)
        delay(100)
        val set = db.workoutSetDao().getAll().single()
        assertEquals(3, set.durationSeconds)
        assertEquals(SetFeedback.RIR_0_1, set.feedback)
    }
}
