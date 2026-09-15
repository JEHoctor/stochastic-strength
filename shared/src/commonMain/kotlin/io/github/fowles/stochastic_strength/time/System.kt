package io.github.fowles.stochastic_strength.time

import kotlin.time.Clock

/**
 * Explicit-import stand-in for `java.lang.System`, which does not exist in common code.
 * Importing this in a file shadows the JVM default import, so `System.currentTimeMillis()`
 * call sites are unchanged. Deliberately exposes only what the app uses: a future
 * `System.nanoTime()` in shared code fails loudly here instead of silently on iOS.
 */
object System {
    fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
