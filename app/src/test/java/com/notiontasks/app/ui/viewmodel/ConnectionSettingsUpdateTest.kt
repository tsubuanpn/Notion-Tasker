package com.notiontasks.app.ui.viewmodel

import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSettingsUpdateTest {
    @Test
    fun `mapping preferences trims statuses and skips empty option lists`() {
        val update = ConnectionSettingsUpdate(
            connection = TaskConnectionConfig(
                token = " token ", databaseId = " database ", title = "Name", status = "Status",
                statusType = "status", statusUnstarted = " 未着手 ", statusInProgress = " 進行中 ",
                statusCompleted = " 完了 ", category = "Category", scheduledDate = "Scheduled", dueDate = "Due",
            ),
            categoryOptions = emptyList(),
            statusOptions = emptyList(),
        )

        val preferences = update.mappingPreferences(Json)

        assertEquals("未着手", preferences.getValue("mapping_status_unstarted"))
        assertEquals("進行中", preferences.getValue("mapping_status_in_progress"))
        assertEquals("完了", preferences.getValue("mapping_status_completed"))
        assertFalse("category_options_v2" in preferences)
        assertFalse("status_options_v2" in preferences)
    }

    @Test
    fun `mapping preferences serializes selected Notion options`() {
        val update = ConnectionSettingsUpdate(
            connection = TaskConnectionConfig(
                token = "token", databaseId = "database", title = "Name", status = "Status",
                statusType = "select", statusUnstarted = "Todo", statusInProgress = "Doing",
                statusCompleted = "Done", category = "Category", scheduledDate = "Scheduled", dueDate = "Due",
            ),
            categoryOptions = listOf(NotionOptionInfo(id = "category-id", name = "Study", color = "blue")),
            statusOptions = listOf(NotionOptionInfo(id = "status-id", name = "Todo", color = "gray")),
        )

        val preferences = update.mappingPreferences(Json)

        assertTrue(preferences.getValue("category_options_v2").contains("Study"))
        assertTrue(preferences.getValue("status_options_v2").contains("Todo"))
    }

    @Test
    fun `validation explains missing connection and duplicate status values`() {
        val incomplete = update(token = "", unstarted = "Todo", inProgress = "Doing", completed = "Done")
        val duplicateStatus = update(token = "token", unstarted = "Todo", inProgress = "Todo", completed = "Done")

        assertEquals("Notion の接続情報を確認してください。", incomplete.validationError())
        assertEquals("状態値は空白でなく、重複しない3つの値を設定してください。", duplicateStatus.validationError())
    }

    private fun update(token: String, unstarted: String, inProgress: String, completed: String) = ConnectionSettingsUpdate(
        connection = TaskConnectionConfig(
            token = token, databaseId = "database", title = "Name", status = "Status", statusType = "status",
            statusUnstarted = unstarted, statusInProgress = inProgress, statusCompleted = completed,
            category = "Category", scheduledDate = "Scheduled", dueDate = "Due",
        ),
        categoryOptions = emptyList(),
        statusOptions = emptyList(),
    )
}
