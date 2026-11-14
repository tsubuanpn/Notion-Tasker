package com.notiontasks.app.ui.viewmodel

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTaskDateTargetTest {
    @Test
    fun `selected day fills only the chosen fields`() {
        val date = "2026-12-25"
        assertEquals(CalendarTaskDates(dueDate = date), CalendarTaskDateTarget.DUE_DATE.initialDates(date))
        assertEquals(CalendarTaskDates(scheduledDate = date), CalendarTaskDateTarget.SCHEDULED_DATE.initialDates(date))
        assertEquals(CalendarTaskDates(date, date), CalendarTaskDateTarget.BOTH.initialDates(date))
    }

    @Test
    fun `missing calendar context leaves dates empty for every target`() {
        CalendarTaskDateTarget.entries.forEach { target ->
            assertEquals(CalendarTaskDates(), target.initialDates(null))
        }
    }

    @Test
    fun `missing or unknown preference defaults to scheduled date`() {
        assertEquals(CalendarTaskDateTarget.SCHEDULED_DATE, CalendarTaskDateTarget.fromPreference(null))
        assertEquals(CalendarTaskDateTarget.SCHEDULED_DATE, CalendarTaskDateTarget.fromPreference("unknown"))
    }

    @Test
    fun `date target updates immediately and survives recreating the settings view model`() {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        var savedTarget: String? = null
        every { preferences.edit() } returns editor
        every { preferences.getString("calendar_task_date_target", any()) } answers { savedTarget }
        every { editor.putString("calendar_task_date_target", any()) } answers {
            savedTarget = secondArg()
            editor
        }

        val settings = SettingsViewModel(preferences)
        assertEquals(CalendarTaskDateTarget.SCHEDULED_DATE, settings.calendarTaskDateTarget.value)
        CalendarTaskDateTarget.entries.forEach { target ->
            settings.updateCalendarTaskDateTarget(target)
            assertEquals(target, settings.calendarTaskDateTarget.value)
            assertEquals(target, SettingsViewModel(preferences).calendarTaskDateTarget.value)
            verify { editor.putString("calendar_task_date_target", target.preferenceValue) }
        }
    }
}
