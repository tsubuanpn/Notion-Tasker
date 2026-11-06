package com.notiontasks.app.ui.screens

import com.notiontasks.app.data.model.TaskModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTaskRankingTest {
    @Test
    fun `chooses in-progress task with earliest scheduled date`() {
        val tasks = listOf(
            task(id = "late", status = "進行中", scheduled = "2026-11-10", due = "2026-11-11"),
            task(id = "early", status = "進行中", scheduled = "2026-11-08", due = "2026-11-30"),
            task(id = "unstarted", status = "未着手", scheduled = "2026-11-01", due = null),
        )

        assertEquals("early", representativeInProgressTask(tasks, "進行中")?.id)
    }

    @Test
    fun `uses due date when an in-progress task has no scheduled date`() {
        val tasks = listOf(
            task(id = "due-later", status = "進行中", scheduled = null, due = "2026-11-12"),
            task(id = "due-earlier", status = "進行中", scheduled = null, due = "2026-11-09"),
        )

        assertEquals("due-earlier", representativeInProgressTask(tasks, "進行中")?.id)
    }

    private fun task(id: String, status: String, scheduled: String?, due: String?) = TaskModel(
        id = id,
        title = id,
        status = status,
        category = "",
        dueDate = due,
        scheduledDate = scheduled,
    )
}
