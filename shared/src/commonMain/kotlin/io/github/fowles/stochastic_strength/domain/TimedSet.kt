package io.github.fowles.stochastic_strength.domain

/** Facts about timed (hold-for-time) sets: how long one runs, and how long one actually lasted. */
object TimedSet {
    /** How long a timed set is prescribed for. */
    const val DURATION_SECONDS = 60

    /**
     * How long a timed set was actually held, given the countdown's remaining seconds at the moment
     * the user gave feedback. Null for untimed exercises; zero if the timer was never started.
     */
    fun elapsedSeconds(
        isTimed: Boolean,
        secondsRemaining: Int?,
        fullSeconds: Int = DURATION_SECONDS,
    ): Int? = if (isTimed) fullSeconds - (secondsRemaining ?: fullSeconds) else null
}
