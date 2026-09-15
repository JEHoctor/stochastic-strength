package io.github.fowles.stochastic_strength.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedWorkoutNamingTest {
    @Test fun defaultName_emptyIsUntitled() {
        assertEquals(SavedWorkoutNaming.UNTITLED, SavedWorkoutNaming.defaultName(emptyList()))
    }

    @Test fun defaultName_listsUpToThree() {
        assertEquals("Squat", SavedWorkoutNaming.defaultName(listOf("Squat")))
        assertEquals(
            "Squat, Bench Press, Row",
            SavedWorkoutNaming.defaultName(listOf("Squat", "Bench Press", "Row")),
        )
    }

    @Test fun defaultName_countsTheRest() {
        assertEquals(
            "Squat, Bench Press, Row +2",
            SavedWorkoutNaming.defaultName(listOf("Squat", "Bench Press", "Row", "Curl", "Dip")),
        )
    }

    @Test fun isPlaceholder_blankOrUntitled() {
        assertTrue(SavedWorkoutNaming.isPlaceholder(""))
        assertTrue(SavedWorkoutNaming.isPlaceholder("  "))
        assertTrue(SavedWorkoutNaming.isPlaceholder(" Untitled workout "))
        assertFalse(SavedWorkoutNaming.isPlaceholder("Leg day"))
    }
}
