package com.notiontasks.app.utils

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JapanTimeTest {
    @Test
    fun `date-only Notion value keeps its stated date`() {
        assertEquals("2026-11-07", JapanTime.projectedDateKey("2026-11-07"))
    }

    @Test
    fun `offset timestamp is projected to Japan date`() {
        assertEquals("2026-11-08", JapanTime.projectedDateKey("2026-11-07T16:30:00Z"))
        assertEquals("2026-11-07", JapanTime.projectedDateKey("2026-11-07T00:30:00+09:00"))
    }

    @Test
    fun `invalid Notion date is not silently truncated`() {
        assertNull(JapanTime.projectedDateKey("not-a-date"))
    }

    @Test
    fun `week starts Monday in Japan`() {
        val sundayInJapan = Clock.fixed(Instant.parse("2026-11-08T15:30:00Z"), ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 11, 9), JapanTime.today(sundayInJapan))
        assertEquals(LocalDate.of(2026, 11, 9), JapanTime.startOfWeek(JapanTime.today(sundayInJapan)))
        assertEquals(LocalDate.of(2026, 11, 15), JapanTime.endOfWeek(JapanTime.today(sundayInJapan)))
    }
}
