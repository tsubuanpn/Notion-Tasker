package com.notiontasks.app.data

import com.notiontasks.app.elapsedSinceFocusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroHelperTest {
    @Test
    fun `focus session count always rounds and displays one decimal place`() {
        assertEquals("1.0", formatFocusSessionCount(minutes = 25, standardDurationMinutes = 25))
        assertEquals("1.3", formatFocusSessionCount(minutes = 32, standardDurationMinutes = 25))
    }

    @Test
    fun `focus restoration counts only monotonic time from the same boot`() {
        assertEquals(2_500L, elapsedSinceFocusSnapshot(10_000L, 12_500L))
        assertEquals(0L, elapsedSinceFocusSnapshot(10_000L, 9_000L))
        assertEquals(0L, elapsedSinceFocusSnapshot(0L, 12_500L))
    }
}
