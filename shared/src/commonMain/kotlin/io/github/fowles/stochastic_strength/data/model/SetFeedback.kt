package io.github.fowles.stochastic_strength.data.model

enum class SetFeedback {
    TOO_HARD, HURT, RIR_0_1, RIR_2_4, RIR_5_PLUS;

    /**
     * [weighted] is false for bodyweight, timed and otherwise unloaded sets — there is no weight
     * there to call heavy, so TOO_HARD reads "Too Hard" instead of "Too Heavy".
     */
    fun displayLabel(weighted: Boolean, actualReps: Int? = null): String = when (this) {
        TOO_HARD -> {
            val base = if (weighted) "Too Heavy" else "Too Hard"
            if (actualReps != null) "$base ($actualReps)" else base
        }
        HURT       -> "Hurt"
        RIR_0_1    -> "0–1 more"
        RIR_2_4    -> "2–4 more"
        RIR_5_PLUS -> "5+ more"
    }

    val isRepsInReserve: Boolean get() = this == RIR_0_1 || this == RIR_2_4 || this == RIR_5_PLUS
}
