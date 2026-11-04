package com.notiontasks.app.ui.screens

import com.notiontasks.app.data.model.TaskModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewTaskSummaryTest {
    private val completedStatus = "完了"

    @Test
    fun periodTasks_includesScheduledTasksAndOutstandingCarryover() {
        val tasks = listOf(
            task(id = "planned", status = "未着手", scheduledDate = "2026-11-03"),
            task(id = "carryover", status = "進行中", dueDate = "2026-10-30"),
            task(id = "completed-old", status = completedStatus, dueDate = "2026-10-30"),
            task(id = "future", status = "未着手", scheduledDate = "2026-12-01"),
        )

        val result = reviewPeriodTasks(tasks, completedStatus, "2026-11-02", "2026-11-08")

        assertEquals(listOf("planned", "carryover"), result.map { it.id })
    }

    @Test
    fun carryoverCount_excludesCompletedTasksAndTasksWithinThePeriod() {
        val tasks = listOf(
            task(id = "carryover", status = "未着手", scheduledDate = "2026-10-31"),
            task(id = "today", status = "未着手", scheduledDate = "2026-11-02"),
            task(id = "completed", status = completedStatus, dueDate = "2026-10-31"),
        )

        assertEquals(1, carryoverTaskCount(tasks, completedStatus, "2026-11-02"))
    }

    private fun task(
        id: String,
        status: String,
        scheduledDate: String? = null,
        dueDate: String? = null,
    ) = TaskModel(
        id = id,
        title = id,
        status = status,
        category = "",
        scheduledDate = scheduledDate,
        dueDate = dueDate,
    )
}
