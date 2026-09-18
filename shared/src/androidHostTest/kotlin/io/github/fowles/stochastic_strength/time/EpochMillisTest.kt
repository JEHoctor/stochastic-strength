package io.github.fowles.stochastic_strength.time

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EpochMillisTest {
    @Test
    fun agreesWithTheJvmClock() {
        val ours = epochMillis()
        val jvm = System.currentTimeMillis()
        assertTrue("ours=$ours jvm=$jvm", abs(ours - jvm) < 1_000)
    }
}
