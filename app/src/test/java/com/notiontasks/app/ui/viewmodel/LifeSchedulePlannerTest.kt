package com.notiontasks.app.ui.viewmodel

import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.data.model.TimeBlockOrigin
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeSchedulePlannerTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `removes only future managed preset blocks`() {
        assertTrue(LifeSchedulePlanner.canRemoveForDeletedPreset(block("preset", "2026-11-09", 0), "sleep", today, 600))
        assertTrue(LifeSchedulePlanner.canRemoveForDeletedPreset(block("preset", "2026-11-08", 700), "sleep", today, 600))
        assertFalse(LifeSchedulePlanner.canRemoveForDeletedPreset(block("preset", "2026-11-08", 500), "sleep", today, 600))
        assertFalse(LifeSchedulePlanner.canRemoveForDeletedPreset(block("preset", "2026-11-07", 0), "sleep", today, 600))
    }

    @Test
    fun `never removes manual legacy malformed or another preset blocks`() {
        assertFalse(LifeSchedulePlanner.canRemoveForDeletedPreset(block("manual", "2026-11-09", 0), "sleep", today, 0))
        assertFalse(LifeSchedulePlanner.canRemoveForDeletedPreset(block("legacy", "2026-11-09", 0), "sleep", today, 0))
        assertFalse(LifeSchedulePlanner.canRemoveForDeletedPreset(block("preset", "not-a-date", 0), "sleep", today, 0))
        assertFalse(LifeSchedulePlanner.canRemoveForDeletedPreset(block("preset", "2026-11-09", 0, presetId = "meal"), "sleep", today, 0))
    }

    private fun block(origin: String, date: String, start: Int, presetId: String = "sleep") = TimeBlock(
        id = "block", type = "life", title = "Sleep", associatedId = presetId,
        startTime = start, endTime = start + 30, color = "#000000", date = date,
        origin = origin, presetId = presetId,
    )
}
