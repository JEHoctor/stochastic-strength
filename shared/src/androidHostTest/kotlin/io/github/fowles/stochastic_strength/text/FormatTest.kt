package io.github.fowles.stochastic_strength.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The iOS printf subset must agree with java.lang.String.format for every pattern the app uses. */
class FormatTest {

    private fun viaJava(pattern: String, vararg args: Any?): String = java.lang.String.format(Locale.US, pattern, *args)

    private val patterns = listOf("%.0f", "%.1f", "%.2f", "%.1f kg", "%.0f lbs", "~%.0f%%")

    @Test
    fun subsetMatchesJavaAcrossASweepOfWeights() {
        var v = 0.0
        while (v <= 500.0) {
            for (p in patterns) assertEquals("pattern=$p value=$v", viaJava(p, v), formatPrintfSubset(p, arrayOf(v)))
            v += 0.05
        }
    }

    @Test
    fun subsetMatchesJavaOnTies() {
        for (v in listOf(0.5, 1.5, 2.5, 0.25, 0.35, 1.15, 1.25, 1.35, 2.675, 99.95, 0.05, 0.005))
            for (p in listOf("%.0f", "%.1f", "%.2f"))
                assertEquals("pattern=$p value=$v", viaJava(p, v), formatPrintfSubset(p, arrayOf(v)))
    }

    @Test
    fun subsetHandlesFloatsIntsAndStrings() {
        assertEquals(viaJava("%.1f", 72.5f), formatPrintfSubset("%.1f", arrayOf(72.5f)))
        assertEquals(viaJava("%d sets", 3), formatPrintfSubset("%d sets", arrayOf(3)))
        assertEquals(viaJava("%d", 1234567890123L), formatPrintfSubset("%d", arrayOf(1234567890123L)))
        assertEquals(viaJava("%s and %s", "a", "b"), formatPrintfSubset("%s and %s", arrayOf("a", "b")))
        assertEquals(viaJava("100%%"), formatPrintfSubset("100%%", arrayOf()))
        assertEquals(viaJava("%.2f", -1.005), formatPrintfSubset("%.2f", arrayOf(-1.005)))
    }

    @Test
    fun androidActualIsJavaFormat() {
        assertEquals(viaJava("%.1f kg", 72.5), "%.1f kg".format(72.5))
        assertEquals(viaJava("%.0f", 2.5), "%.0f".format(2.5))
    }
}
