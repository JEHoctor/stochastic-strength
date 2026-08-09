package io.github.fowles.stochastic_strength.ui

import io.github.fowles.stochastic_strength.data.AppDatabase
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.SetFeedback
import io.github.fowles.stochastic_strength.data.model.WeightUnit
import io.github.fowles.stochastic_strength.data.model.WorkoutSet

data class SummarySet(
    val setNumber: Int,
    val targetWeight: Float,
    val targetReps: Int,
    val actualReps: Int?,
    val feedback: SetFeedback?,
    val isTimed: Boolean = false,
    val isBodyweight: Boolean = false,
) {
    /** Mirrors [io.github.fowles.stochastic_strength.domain.model.PlannedExercise.isWeighted]. */
    val isWeighted: Boolean get() = !isTimed && !isBodyweight && targetWeight > 0f
}

fun WorkoutSet.toSummarySet(isTimed: Boolean, isBodyweight: Boolean) =
    SummarySet(setNumber, targetWeight, targetReps, actualReps, feedback, isTimed, isBodyweight)

/** Reps-in-reserve feedback is meaningless for timed sets (auto-recorded), so it is hidden. */
fun SummarySet.summaryFeedbackLabel(): String? {
    val fb = feedback?.takeUnless { isTimed && it.isRepsInReserve } ?: return null
    return fb.displayLabel(weighted = isWeighted, actualReps = actualReps.takeUnless { isTimed })
}

data class SummaryExercise(val name: String, val exerciseId: Long, val sets: List<SummarySet>)

data class WorkoutSummaryData(
    val startTime: Long,
    val durationSeconds: Long,
    val exercises: List<SummaryExercise>,
    val weightUnit: WeightUnit,
)

suspend fun loadWorkoutSummary(db: AppDatabase, sessionId: Long): WorkoutSummaryData {
    val weightUnit = db.userProfileDao().getProfile()?.weightUnit ?: WeightUnit.KG
    val session = db.workoutSessionDao().getById(sessionId)
    val sets = db.workoutSetDao().getSetsForSession(sessionId)
    val exerciseIds = sets.map { it.exerciseId }.distinct()
    val exerciseById = db.exerciseDao().getByIds(exerciseIds).associateBy { it.id }
    val setsByExercise = sets.groupBy { it.exerciseId }
    val exercises = exerciseIds.map { id ->
        val exercise = exerciseById[id]
        SummaryExercise(
            name = exercise?.name ?: "Unknown",
            exerciseId = id,
            sets = (setsByExercise[id] ?: emptyList()).sortedBy { it.setNumber }
                .map {
                    it.toSummarySet(
                        isTimed = exercise?.isTimed ?: false,
                        isBodyweight = exercise?.equipment == Equipment.BODYWEIGHT,
                    )
                },
        )
    }
    val duration = if (session != null && session.endTime != null) {
        (session.endTime - session.startTime) / 1000
    } else 0L
    return WorkoutSummaryData(
        startTime = session?.startTime ?: 0L,
        durationSeconds = duration,
        exercises = exercises,
        weightUnit = weightUnit,
    )
}
