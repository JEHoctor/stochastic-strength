package io.github.fowles.stochastic_strength.domain.model

/**
 * Names a saved workout gets when the user never typed one: built from what is in it. Such a
 * workout is stored with an empty name so the derived one keeps tracking its exercises.
 */
object SavedWorkoutNaming {
    const val UNTITLED = "Untitled workout"
    const val MAX_LISTED = 3

    /** "Bench Press, Squat, Row" — or "…, Row +2" past [MAX_LISTED]; [UNTITLED] when empty. */
    fun defaultName(exerciseNames: List<String>): String {
        if (exerciseNames.isEmpty()) return UNTITLED
        val listed = exerciseNames.take(MAX_LISTED).joinToString(", ")
        val rest = exerciseNames.size - MAX_LISTED
        return if (rest > 0) "$listed +$rest" else listed
    }

    /** True for a name the user never chose (empty, or the pre-derivation placeholder). */
    fun isPlaceholder(name: String): Boolean = name.isBlank() || name.trim() == UNTITLED
}
