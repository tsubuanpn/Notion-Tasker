package com.notiontasks.app.ui.viewmodel

import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOptionRecoveryTest {
    private fun task(category: String, color: String? = null) = TaskModel(
        id = category,
        title = category,
        status = "未着手",
        category = category,
        dueDate = null,
        scheduledDate = null,
        categoryColor = color,
    )

    @Test
    fun `saved category metadata survives a task cache refresh`() {
        val saved = listOf(NotionOptionInfo(name = "学習", color = "blue"))

        assertEquals(saved, categoryOptionsForTaskUi(saved, listOf(task("作業"))))
    }

    @Test
    fun `missing legacy metadata is recovered from local task categories`() {
        val actual = categoryOptionsForTaskUi(
            savedOptions = emptyList(),
            cachedTasks = listOf(task("学習", "blue"), task("作業", "green"), task("学習", "red"), task(" ")),
        )

        assertEquals(
            listOf(NotionOptionInfo(name = "学習", color = "blue"), NotionOptionInfo(name = "作業", color = "green")),
            actual,
        )
    }
}
