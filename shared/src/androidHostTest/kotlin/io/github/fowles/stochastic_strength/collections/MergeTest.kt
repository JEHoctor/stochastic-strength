package io.github.fowles.stochastic_strength.collectionsshadow

import io.github.fowles.stochastic_strength.collections.merge as shimMerge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Calls the shim through an import alias so the common implementation runs on the JVM too; a
 * plain `merge` call here would resolve to `java.util.Map.merge`, which is exactly what
 * production code on Android does.
 */
class MergeTest {
    @Test
    fun absentKeyStoresAndReturnsValue() {
        val map = mutableMapOf<String, Int>()
        val result = map.shimMerge("a", 1, Int::plus)
        assertEquals(1, result)
        assertEquals(1, map["a"])
    }

    @Test
    fun presentKeyStoresAndReturnsRemapOfOldAndNew() {
        val map = mutableMapOf("a" to 1)
        val result = map.shimMerge("a", 2, Int::plus)
        assertEquals(3, result)
        assertEquals(3, map["a"])
    }
}
