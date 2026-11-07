package com.notiontasks.app

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notiontasks.app.data.local.TaskDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PomodoroServiceCheckpointTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
    private val logId = "instrumented-pending-focus-log"

    @After
    fun cleanUp() {
        runBlocking {
            context.stopService(android.content.Intent(context, PomodoroService::class.java))
            TaskDatabase.getInstance(context).pomodoroLogDao.deleteLogById(logId)
            prefs.edit()
                .remove("pending_focus_log_generation")
                .remove("pending_focus_log_id")
                .remove("pending_focus_log_task_id")
                .remove("pending_focus_log_title")
                .remove("pending_focus_log_category")
                .remove("pending_focus_log_color")
                .remove("pending_focus_log_date")
                .remove("pending_focus_log_minutes")
                .remove("pending_focus_log_timestamp")
                .remove("pending_focus_log_type")
                .commit()
        }
    }

    @Test
    fun replaysDurableFocusLogCheckpointOnceOnServiceCreation() = runBlocking {
        context.stopService(android.content.Intent(context, PomodoroService::class.java))
        TaskDatabase.getInstance(context).pomodoroLogDao.deleteLogById(logId)

        assertTrue(
            prefs.edit()
                .putLong("pending_focus_log_generation", 41L)
                .putString("pending_focus_log_id", logId)
                .putString("pending_focus_log_task_id", null)
                .putString("pending_focus_log_title", "instrumentation checkpoint")
                .putString("pending_focus_log_category", "general")
                .putString("pending_focus_log_color", "default")
                .putString("pending_focus_log_date", "2026-11-07")
                .putInt("pending_focus_log_minutes", 3)
                .putLong("pending_focus_log_timestamp", 1_700_000_000_000L)
                .putString("pending_focus_log_type", PomodoroService.FOCUS_TYPE_TIMER)
                .commit(),
        )

        ContextCompat.startForegroundService(
            context,
            android.content.Intent(context, PomodoroService::class.java)
                .setAction(PomodoroService.ACTION_RESTORE),
        )

        withTimeout(5_000L) {
            while (TaskDatabase.getInstance(context).pomodoroLogDao.getAllLogs().none { it.id == logId }) {
                delay(50L)
            }
        }

        val restored = TaskDatabase.getInstance(context).pomodoroLogDao.getAllLogs().single { it.id == logId }
        assertEquals(3, restored.minutes)
        assertEquals("2026-11-07", restored.date)
        assertEquals("instrumentation checkpoint", restored.taskTitle)
        assertEquals(PomodoroService.FOCUS_TYPE_TIMER, restored.sessionType)
        assertFalse(prefs.contains("pending_focus_log_id"))
    }
}
