package com.notiontasks.app.ui.screens

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PomodoroSettingsPersistenceTest {
    @Test
    fun `saves durations and alarm in one preferences transaction`() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { preferences.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor

        savePomodoroSettings(
            preferences = preferences,
            workMinutes = 30,
            shortBreakMinutes = 7,
            longBreakMinutes = 20,
            alarmUri = "content://test/alarm",
        )

        verify(exactly = 1) { preferences.edit() }
        verify(exactly = 1) { editor.putInt("work_duration_min", 30) }
        verify(exactly = 1) { editor.putInt("short_break_duration_min", 7) }
        verify(exactly = 1) { editor.putInt("long_break_duration_min", 20) }
        verify(exactly = 1) { editor.putString("alarm_uri", "content://test/alarm") }
        verify(exactly = 1) { editor.apply() }
    }
}
