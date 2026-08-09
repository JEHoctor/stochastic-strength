package io.github.fowles.stochastic_strength.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedSetTest {

    @Test
    fun untimedExerciseHasNoDuration() {
        assertNull(TimedSet.elapsedSeconds(isTimed = false, secondsRemaining = 40))
    }

    @Test
    fun timedSetEndedEarlyRecordsTimeActuallyHeld() {
        assertEquals(20, TimedSet.elapsedSeconds(isTimed = true, secondsRemaining = 40))
    }

    @Test
    fun timerRunToZeroRecordsFullDuration() {
        assertEquals(TimedSet.DURATION_SECONDS, TimedSet.elapsedSeconds(isTimed = true, secondsRemaining = 0))
    }

    @Test
    fun elapsedIsMeasuredAgainstTheSetsOwnLength() {
        assertEquals(7, TimedSet.elapsedSeconds(isTimed = true, secondsRemaining = 3, fullSeconds = 10))
    }

    @Test
    fun timedSetNeverStartedRecordsZero() {
        assertEquals(0, TimedSet.elapsedSeconds(isTimed = true, secondsRemaining = null))
    }
}
