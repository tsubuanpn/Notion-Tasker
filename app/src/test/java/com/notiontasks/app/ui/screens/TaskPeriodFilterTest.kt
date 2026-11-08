package com.notiontasks.app.ui.screens

import com.notiontasks.app.data.model.TaskModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPeriodFilterTest {
    private fun task(
        id: String,
        status: String = "未着手",
        scheduled: String? = null,
        due: String? = null,
    ) = TaskModel(id, id, status, "", due, scheduled)

    private val tasks = listOf(
        task("scheduledPast", scheduled = "2026-11-05"),
        task("dueToday", due = "2026-11-07"),
        task("thisWeek", scheduled = "2026-11-08"),
        task("nextWeek", scheduled = "2026-11-09"),
        task("unassigned"),
        task("completedPast", status = "完了", scheduled = "2026-11-01"),
    )

    @Test
    fun `today includes either date on or before today and excludes completed`() {
        val actual = filterOpenTasksByPeriod(tasks, "完了", setOf(TaskPeriod.TODAY), "2026-11-07")

        assertEquals(listOf("scheduledPast", "dueToday"), actual.map { it.id })
    }

    @Test
    fun `this week uses Monday-start Sunday boundary and includes past dates`() {
        val actual = filterOpenTasksByPeriod(tasks, "完了", setOf(TaskPeriod.THIS_WEEK), "2026-11-07")

        assertEquals(listOf("scheduledPast", "dueToday", "thisWeek"), actual.map { it.id })
    }

    @Test
    fun `unassigned requires both scheduled and due dates to be unset`() {
        val actual = filterOpenTasksByPeriod(tasks, "完了", setOf(TaskPeriod.UNASSIGNED), "2026-11-07")

        assertEquals(listOf("unassigned"), actual.map { it.id })
    }

    @Test
    fun `multiple periods are combined as a union`() {
        val actual = filterOpenTasksByPeriod(
            tasks,
            "完了",
            setOf(TaskPeriod.TODAY, TaskPeriod.UNASSIGNED),
            "2026-11-07",
        )

        assertEquals(listOf("scheduledPast", "dueToday", "unassigned"), actual.map { it.id })
    }
}
