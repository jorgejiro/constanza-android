package com.jjrapps.constanza.recap

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** weekly-recap: the week's range follows the app's language, never the device default. Runs on a
 *  device because the day-and-month pattern comes from the platform's ICU data. */
@RunWith(AndroidJUnit4::class)
class WeekRangeLabelInstrumentedTest {

    private val spanish = Locale.forLanguageTag("es-ES")

    @Test
    fun spanishWeekWithinOneMonth() {
        assertEquals("12 – 18 de octubre", weekRangeLabel(LocalDate.parse("2026-10-12"), spanish))
    }

    @Test
    fun englishWeekWithinOneMonth() {
        assertEquals("October 12 – 18", weekRangeLabel(LocalDate.parse("2026-10-12"), Locale.US))
    }

    @Test
    fun spanishWeekAcrossTwoMonths() {
        assertEquals("28 de septiembre – 4 de octubre", weekRangeLabel(LocalDate.parse("2026-09-28"), spanish))
    }

    @Test
    fun spanishEvenWhenTheDeviceDefaultIsEnglish() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("12 – 18 de octubre", weekRangeLabel(LocalDate.parse("2026-10-12"), spanish))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
