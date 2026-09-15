package io.github.fowles.stochastic_strength.collectionsshadow

import io.github.fowles.stochastic_strength.collections.merge
import org.junit.Assert.assertEquals
import org.junit.Test

/** From another package, the explicit import is required to resolve `merge` at all. */
class MergeTest {
    @Test
    fun absentKeyStoresAndReturnsValue() {
        val map = mutableMapOf<String, Int>()
        val result = map.merge("a", 1, Int::plus)
        assertEquals(1, result)
        assertEquals(1, map["a"])
    }

    @Test
    fun presentKeyStoresAndReturnsRemapOfOldAndNew() {
        val map = mutableMapOf("a" to 1)
        val result = map.merge("a", 2, Int::plus)
        assertEquals(3, result)
        assertEquals(3, map["a"])
    }
}
