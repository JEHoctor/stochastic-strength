package io.github.fowles.stochastic_strength.time

import kotlin.time.Clock

/** Wall-clock time as epoch milliseconds — the common-code equivalent of `System.currentTimeMillis()`. */
fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
