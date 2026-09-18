package io.github.fowles.stochastic_strength.text

actual fun String.format(vararg args: Any?): String = formatPrintfSubset(this, args)
