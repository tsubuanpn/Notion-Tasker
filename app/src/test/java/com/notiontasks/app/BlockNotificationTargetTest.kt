package com.notiontasks.app

import com.notiontasks.app.data.local.ScheduleBlockEntity
import com.notiontasks.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockNotificationTargetTest {
    private val block = ScheduleBlockEntity(
        id = "block",
        type = "task",
        title = "current Room title",
        associatedId = "task",
        startTime = 0,
        endTime = 30,
        color = "#000000",
        date = "2026-11-07",
    )

    @Test
    fun `uses the current block values for an active task`() {
        val target = currentBlockNotificationTarget(block, listOf(task("進行中")), "完了")

        assertEquals(BlockNotificationTarget("current Room title", "task", "task"), target)
    }

    @Test
    fun `suppresses completed and orphaned task blocks`() {
        assertNull(currentBlockNotificationTarget(block, listOf(task("完了")), "完了"))
        assertNull(currentBlockNotificationTarget(block, emptyList(), "完了"))
    }

    private fun task(status: String) = TaskEntity(
        id = "task",
        title = "task",
        status = status,
        category = "",
        dueDate = null,
        scheduledDate = null,
        connectionScope = "scope",
    )
}
