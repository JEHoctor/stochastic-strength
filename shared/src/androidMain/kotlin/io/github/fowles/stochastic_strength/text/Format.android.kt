package io.github.fowles.stochastic_strength.text

actual fun String.format(vararg args: Any?): String = java.lang.String.format(this, *args)
