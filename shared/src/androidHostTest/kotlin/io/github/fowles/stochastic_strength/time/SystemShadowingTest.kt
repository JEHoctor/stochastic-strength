package io.github.fowles.stochastic_strength.timeshadow

import io.github.fowles.stochastic_strength.time.System
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** From another package, the explicit import must win over java.lang.System and agree with it. */
class SystemShadowingTest {
    @Test
    fun explicitImportShadowsJavaLangSystemAndAgreesWithIt() {
        val ours = System.currentTimeMillis()
        val jvm = java.lang.System.currentTimeMillis()
        assertTrue("ours=$ours jvm=$jvm", abs(ours - jvm) < 1_000)
    }
}
