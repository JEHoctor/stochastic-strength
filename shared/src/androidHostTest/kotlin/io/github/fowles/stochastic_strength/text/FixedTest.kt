package io.github.fowles.stochastic_strength.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** `fixed(n)` must agree with `String.format(Locale.US, "%.nf", …)` — what the app printed before. */
class FixedTest {

    private fun viaJava(decimals: Int, v: Double): String = java.lang.String.format(Locale.US, "%.${decimals}f", v)

    @Test
    fun matchesJavaAcrossASweepOfWeights() {
        var v = 0.0
        while (v <= 500.0) {
            for (d in 0..2) assertEquals("decimals=$d value=$v", viaJava(d, v), v.fixed(d))
            v += 0.05
        }
    }

    @Test
    fun matchesJavaOnTies() {
        for (v in listOf(0.5, 1.5, 2.5, 0.25, 0.35, 1.15, 1.25, 1.35, 2.675, 99.95, 0.05, 0.005, 9.95, 0.95))
            for (d in 0..2) assertEquals("decimals=$d value=$v", viaJava(d, v), v.fixed(d))
    }

    @Test
    fun floatsWidenLikeJava() {
        assertEquals(viaJava(1, 72.5f.toDouble()), 72.5f.fixed(1))
        assertEquals(viaJava(0, 62.3f.toDouble()), 62.3f.fixed(0))
        assertEquals(viaJava(2, -1.005), (-1.005).fixed(2))
        assertEquals(viaJava(3, 0.5), 0.5.fixed(3))
        assertEquals(viaJava(1, 1.0E10), 1.0E10.fixed(1))
        assertEquals(viaJava(1, 1.0E-5), 1.0E-5.fixed(1))
    }
}
