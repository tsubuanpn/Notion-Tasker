package com.notiontasks.app.data

import com.notiontasks.app.elapsedSinceFocusSnapshot
import com.notiontasks.app.checkpointStopwatchSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroHelperTest {
    @Test
    fun `focus session count always rounds and displays one decimal place`() {
        assertEquals("1.0", formatFocusSessionCount(minutes = 25, standardDurationMinutes = 25))
        assertEquals("1.3", formatFocusSessionCount(minutes = 32, standardDurationMinutes = 25))
        // 1.25 sessions is exactly halfway at the second decimal place and must
        // round up to one decimal place under the user-facing counting contract.
        assertEquals("1.3", formatFocusSessionCount(minutes = 25, standardDurationMinutes = 20))
    }

    @Test
    fun `focus restoration counts only monotonic time from the same boot`() {
        assertEquals(2_500L, elapsedSinceFocusSnapshot(10_000L, 12_500L))
        assertEquals(0L, elapsedSinceFocusSnapshot(10_000L, 9_000L))
        assertEquals(0L, elapsedSinceFocusSnapshot(0L, 12_500L))
    }

    @Test
    fun `stopwatch checkpoint folds the active segment into the persisted accumulator`() {
        assertEquals(
            95_000L,
            checkpointStopwatchSegment(
                accumulatedMs = 60_000L,
                segmentStartedAtMs = 10_000L,
                currentElapsedRealtimeMs = 45_000L,
            ),
        )
    }
}
