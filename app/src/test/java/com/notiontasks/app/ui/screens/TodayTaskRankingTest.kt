package com.notiontasks.app.ui.screens

import com.notiontasks.app.data.model.TaskModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun `ranks offset schedule dates by their Japan calendar day`() {
        val tasks = listOf(
            // The lexical source date is November 8, but this is November 9 in JST.
            task(id = "later-in-japan", status = "進行中", scheduled = "2026-11-08T23:30:00-02:00", due = null),
            // The lexical source date is November 9, but this is November 8 in JST.
            task(id = "earlier-in-japan", status = "進行中", scheduled = "2026-11-09T00:30:00+14:00", due = null),
        )

        assertEquals("earlier-in-japan", representativeInProgressTask(tasks, "進行中")?.id)
    }

    @Test
    fun `formats the Today header from a Japan calendar date`() {
        assertEquals("11月7日（土）", formatJapanTodayHeader(LocalDate.of(2026, 11, 7)))
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
