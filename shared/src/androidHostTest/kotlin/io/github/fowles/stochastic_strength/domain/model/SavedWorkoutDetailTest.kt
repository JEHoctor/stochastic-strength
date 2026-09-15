package io.github.fowles.stochastic_strength.domain.model

import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedWorkoutDetailTest {
    private fun entry(name: String) =
        SavedWorkoutEntry(Exercise(name = name, primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL), null)

    @Test fun displayName_usesTypedNameWhenPresent() {
        val d = SavedWorkoutDetail(1, "Push day", listOf(entry("Bench Press")))
        assertEquals("Push day", d.displayName)
    }

    @Test fun displayName_derivesFromExercisesWhenUnnamed() {
        val d = SavedWorkoutDetail(1, "", listOf(entry("Bench Press"), entry("Dip")))
        assertEquals("Bench Press, Dip", d.displayName)
    }

    @Test fun displayName_untitledWhenUnnamedAndEmpty() {
        assertEquals(SavedWorkoutNaming.UNTITLED, SavedWorkoutDetail(1, "  ", emptyList()).displayName)
    }
}
