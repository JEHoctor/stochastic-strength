package io.github.fowles.stochastic_strength.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SetFeedbackTest {
    @Test
    fun displayLabelWithActualRepsAnnotatesOnlyTooHard() {
        assertEquals("Too Heavy (4)", SetFeedback.TOO_HARD.displayLabel(weighted = true, actualReps = 4))
        assertEquals("Too Heavy", SetFeedback.TOO_HARD.displayLabel(weighted = true))
        assertEquals("Hurt", SetFeedback.HURT.displayLabel(weighted = true, actualReps = 4))
        assertEquals("0–1 more", SetFeedback.RIR_0_1.displayLabel(weighted = true, actualReps = 8))
        assertEquals("2–4 more", SetFeedback.RIR_2_4.displayLabel(weighted = true))
        assertEquals("5+ more", SetFeedback.RIR_5_PLUS.displayLabel(weighted = true, actualReps = 10))
    }

    @Test
    fun unweightedSetsSayTooHardInsteadOfTooHeavy() {
        assertEquals("Too Hard", SetFeedback.TOO_HARD.displayLabel(weighted = false))
        assertEquals("Too Hard (4)", SetFeedback.TOO_HARD.displayLabel(weighted = false, actualReps = 4))
        assertEquals("Hurt", SetFeedback.HURT.displayLabel(weighted = false))
        assertEquals("0–1 more", SetFeedback.RIR_0_1.displayLabel(weighted = false))
    }
}
