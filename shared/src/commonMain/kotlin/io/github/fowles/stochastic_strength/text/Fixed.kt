package io.github.fowles.stochastic_strength.text
import kotlin.math.abs

/**
 * Fixed-point decimal formatting for user-visible numbers, the common-code replacement for
 * `"%.Nf".format(x)`. Rounds half-up on the value's shortest decimal representation, which is
 * what `java.util.Formatter` does, so output matches what Android printed before; always uses
 * `.` as the decimal separator regardless of locale.
 */
fun Double.fixed(decimals: Int): String = formatFixed(this, decimals)

/** See [Double.fixed]; the float widens to double exactly as `String.format` widened it. */
fun Float.fixed(decimals: Int): String = formatFixed(this.toDouble(), decimals)

private fun formatFixed(value: Double, decimals: Int): String {
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
