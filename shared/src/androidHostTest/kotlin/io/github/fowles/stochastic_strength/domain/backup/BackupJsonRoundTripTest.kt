package io.github.fowles.stochastic_strength.domain.backup

import io.github.fowles.stochastic_strength.data.model.BaselineChangeReason
import io.github.fowles.stochastic_strength.data.model.BaselineOverride
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.ExerciseHurtState
import io.github.fowles.stochastic_strength.data.model.KnownLocation
import io.github.fowles.stochastic_strength.data.model.LocationExcludedExercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import io.github.fowles.stochastic_strength.data.model.SavedWorkout
import io.github.fowles.stochastic_strength.data.model.SavedWorkoutExercise
import io.github.fowles.stochastic_strength.data.model.SetFeedback
import io.github.fowles.stochastic_strength.data.model.Sex
import io.github.fowles.stochastic_strength.data.model.StrengthLevel
import io.github.fowles.stochastic_strength.data.model.UserProfile
import io.github.fowles.stochastic_strength.data.model.WeightUnit
import io.github.fowles.stochastic_strength.data.model.WorkoutSession
import io.github.fowles.stochastic_strength.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** A full backup survives build → parse, and the output is readable by the real org.json. */
class BackupJsonRoundTripTest {

    private val backup = WorkoutBackup(
        formatVersion = WorkoutBackup.FORMAT_VERSION,
        dbVersion = WorkoutBackup.DB_VERSION,
        exportedAt = 1_757_500_000_000L,
        exercises = listOf(
            Exercise(id = 1, name = "Bench Press", primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = listOf(MuscleGroup.CHEST), equipment = Equipment.BARBELL,
                isDisliked = false, isUnilateral = false, isAsymmetric = true, isTimed = false),
            Exercise(id = 2, name = "Plank", primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = emptyList(), equipment = Equipment.BARBELL, isTimed = true),
        ),
        knownLocations = listOf(KnownLocation(id = 1, name = "Gym", latitude = 42.36, longitude = -71.06)),
        locationExcludedExercises = listOf(LocationExcludedExercise(locationId = 1, exerciseId = 2)),
        workoutSessions = listOf(
            WorkoutSession(id = 1, locationId = 1, startTime = 1_757_000_000_000L, endTime = 1_757_003_600_000L, stravaActivityId = 99L),
            WorkoutSession(id = 2, locationId = null, startTime = 1_757_100_000_000L, endTime = null, stravaActivityId = null),
        ),
        workoutSets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exerciseId = 1, setNumber = 1, targetWeight = 72.5f, targetReps = 8,
                actualReps = 8, feedback = SetFeedback.TOO_HARD, completedAt = 1_757_000_500_000L, durationSeconds = null),
            WorkoutSet(id = 2, sessionId = 1, exerciseId = 2, setNumber = 1, targetWeight = 0f, targetReps = 1,
                actualReps = null, feedback = null, completedAt = null, durationSeconds = 60),
        ),
        userProfile = listOf(UserProfile(id = 1, sex = Sex.FEMALE, strengthLevel = StrengthLevel.MEDIUM,
            weightUnit = WeightUnit.KG, preferredExerciseCount = 6, preferredRepMin = null, preferredRepMax = 12)),
        baselineOverrides = listOf(BaselineOverride(id = 1, sessionId = null, muscleGroup = MuscleGroup.CHEST,
            baselineWeight = 60.3f, asOf = 1_756_000_000_000L, reason = BaselineChangeReason.INITIAL)), // not exactly representable in binary: exercises number formatting
        exerciseHurtState = listOf(ExerciseHurtState(exerciseId = 1, isHurt = true, asOf = 1_757_000_000_000L)),
        savedWorkouts = listOf(SavedWorkout(id = 1, name = "", createdAt = 1_757_200_000_000L)),
        savedWorkoutExercises = listOf(SavedWorkoutExercise(id = 1, workoutId = 1, exerciseId = 1, position = 0, reps = null)),
    )

    @Test
    fun buildThenParseIsIdentity() {
        val json = BackupJsonBuilder.build(backup)
        assertEquals(backup, BackupJsonParser.parse(json))
    }

    @Test
    fun outputIsReadableByOrgJson() {
        val root = org.json.JSONObject(BackupJsonBuilder.build(backup))
        assertEquals(WorkoutBackup.FORMAT, root.getString("format"))
        assertEquals(WorkoutBackup.DB_VERSION, root.getInt("dbVersion"))
        val sets = root.getJSONObject("tables").getJSONArray("workoutSets")
        assertEquals(72.5, sets.getJSONObject(0).getDouble("targetWeight"), 0.0)
        assertEquals(true, sets.getJSONObject(1).isNull("feedback"))
    }

    @Test
    fun orgJsonProducedBackupParses() {
        // Simulate a backup written by the previous org.json-based builder: same keys, org.json's number formatting.
        val viaShim = org.json.JSONObject(BackupJsonBuilder.build(backup)).toString(2)
        assertEquals(backup, BackupJsonParser.parse(viaShim))
    }
}
