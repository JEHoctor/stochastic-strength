package io.github.fowles.stochastic_strength.text

import kotlin.math.abs

/**
 * Explicit-import stand-in for `kotlin.text.format`, which is JVM-only. Importing this in a
 * file shadows the JVM default import, so `"%.1f".format(x)` call sites are unchanged. Android
 * delegates to `java.lang.String.format`; iOS uses [formatPrintfSubset].
 */
expect fun String.format(vararg args: Any?): String

/**
 * The printf subset the app uses: `%s`, `%d`, `%.Nf` (and `%%`). Fixed-point rounding is
 * half-up on the value's decimal form, matching `java.util.Formatter`. Always uses `.` as
 * the decimal separator. Anything else throws [IllegalArgumentException].
 */
internal fun formatPrintfSubset(pattern: String, args: Array<out Any?>): String {
    val out = StringBuilder()
    var argIndex = 0
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c != '%') { out.append(c); i++; continue }
        i++
        if (i >= pattern.length) throw IllegalArgumentException("Dangling '%' in \"$pattern\"")
        if (pattern[i] == '%') { out.append('%'); i++; continue }
        var precision = -1
        if (pattern[i] == '.') {
            i++
            val start = i
            while (i < pattern.length && pattern[i].isDigit()) i++
            precision = pattern.substring(start, i).toIntOrNull()
                ?: throw IllegalArgumentException("Bad precision in \"$pattern\"")
        }
        if (i >= pattern.length) throw IllegalArgumentException("Dangling conversion in \"$pattern\"")
        val conversion = pattern[i++]
        val arg = if (argIndex < args.size) args[argIndex++]
        else throw IllegalArgumentException("Missing argument for %$conversion in \"$pattern\"")
        when (conversion) {
            's' -> out.append(arg.toString())
            'd' -> out.append((arg as Number).toLong().toString())
            'f' -> out.append(fixed((arg as Number).toDouble(), if (precision < 0) 6 else precision))
            else -> throw IllegalArgumentException("Unsupported conversion %$conversion in \"$pattern\"")
        }
    }
    return out.toString()
}

/**
 * Rounds [value] to [decimals] fractional digits the way `java.util.Formatter`'s `%f` does:
 * half-up on the value's *shortest round-tripping decimal string* (`Double.toString()`), not
 * on the exact binary value and not by scaling with floating-point multiplication (which can
 * itself introduce rounding error — e.g. `0.44999999999999996 * 10.0 == 4.5` exactly, even
 * though the value's decimal spelling is below the `x.5` boundary). Operating on the decimal
 * string's digits directly sidesteps that.
 */
private fun fixed(value: Double, decimals: Int): String {
    val negative = value < 0.0
    val (intPart, fracPartRaw) = toPlainDecimalParts(abs(value))
    val fracPadded = fracPartRaw.padEnd(decimals + 1, '0')
    val keptFrac = StringBuilder(fracPadded.substring(0, decimals))
    val firstDropped = fracPadded[decimals]
    val intDigits = StringBuilder(intPart)

    if (firstDropped >= '5') {
        var carry = true
        var i = keptFrac.length - 1
        while (i >= 0 && carry) {
            if (keptFrac[i] == '9') keptFrac[i] = '0'
            else { keptFrac[i] = keptFrac[i] + 1; carry = false }
            i--
        }
        var j = intDigits.length - 1
        while (j >= 0 && carry) {
            if (intDigits[j] == '9') intDigits[j] = '0'
            else { intDigits[j] = intDigits[j] + 1; carry = false }
            j--
        }
        if (carry) intDigits.insert(0, '1')
    }

    val intResult = intDigits.toString()
    val fracResult = keptFrac.toString()
    val isZero = intResult.all { it == '0' } && fracResult.all { it == '0' }
    val sign = if (negative && !isZero) "-" else ""
    return if (decimals == 0) "$sign$intResult" else "$sign$intResult.$fracResult"
}

/**
 * Splits the non-negative [value]'s shortest round-tripping decimal string (`Double.toString()`,
 * e.g. `"123.45"` or, for very large/small magnitudes, `"1.5E10"`) into its integer and
 * fractional digit strings, expanding any exponent so both are plain decimal digits.
 */
private fun toPlainDecimalParts(value: Double): Pair<String, String> {
    val s = value.toString()
    val eIndex = s.indexOfFirst { it == 'E' || it == 'e' }
    if (eIndex < 0) {
        val dot = s.indexOf('.')
        return if (dot < 0) s to "" else s.substring(0, dot) to s.substring(dot + 1)
    }
    val mantissa = s.substring(0, eIndex)
    val exp = s.substring(eIndex + 1).toInt()
    val dot = mantissa.indexOf('.')
    val digits = if (dot < 0) mantissa else mantissa.substring(0, dot) + mantissa.substring(dot + 1)
    val pointPos = (if (dot < 0) mantissa.length else dot) + exp
    return when {
        pointPos <= 0 -> "0" to ("0".repeat(-pointPos) + digits)
        pointPos >= digits.length -> (digits + "0".repeat(pointPos - digits.length)) to ""
        else -> digits.substring(0, pointPos) to digits.substring(pointPos)
    }
}
