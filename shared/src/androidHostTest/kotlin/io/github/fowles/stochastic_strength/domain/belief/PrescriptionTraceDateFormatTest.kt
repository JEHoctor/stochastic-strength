package io.github.fowles.stochastic_strength.domain.belief

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pins [PrescriptionTraceBuilder.formatMonthDay] against the JVM formatter it's documented to match. */
class PrescriptionTraceDateFormatTest {
    @Test
    fun formatMonthDay_matchesSimpleDateFormat() {
        val expected = SimpleDateFormat("MMM d", Locale.US)
        for (epochMs in listOf(0L, 1_757_500_000_000L, 1_700_000_000_000L)) {
            assertEquals(expected.format(Date(epochMs)), PrescriptionTraceBuilder.formatMonthDay(epochMs))
        }
    }
}
