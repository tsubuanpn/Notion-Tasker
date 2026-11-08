package com.notiontasks.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanOverviewDateTest {
    @Test
    fun `formats selected Plan date without the device default time zone`() {
        assertEquals("11月7日（土）", formatSelectedDate("2026-11-07"))
    }

    @Test
    fun `keeps an invalid selected Plan date readable`() {
        assertEquals("invalid-date", formatSelectedDate("invalid-date"))
    }
}
