package com.notiontasks.app.ui.components

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskComponentsTest {
    @Test
    fun datePickerInitialCalendar_usesSelectedDateWhenPresent() {
        val result = datePickerInitialCalendar("2026-02-03")

        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
        assertEquals(3, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun datePickerInitialCalendar_usesFallbackForBlankOrInvalidDate() {
        val fallback = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 4) }

        assertEquals(fallback, datePickerInitialCalendar("", fallback))
        assertEquals(fallback, datePickerInitialCalendar("2026-02-30", fallback))
    }
}
