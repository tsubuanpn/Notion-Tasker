package com.notiontasks.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroHelperTest {
    @Test
    fun `focus session count always rounds and displays one decimal place`() {
        assertEquals("1.0", formatFocusSessionCount(minutes = 25, standardDurationMinutes = 25))
        assertEquals("1.3", formatFocusSessionCount(minutes = 32, standardDurationMinutes = 25))
    }
}
