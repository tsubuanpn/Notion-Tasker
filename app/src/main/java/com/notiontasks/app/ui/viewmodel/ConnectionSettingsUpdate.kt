package com.notiontasks.app.ui.viewmodel

import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A validated, typed representation of the Notion connection form. */
data class TaskConnectionConfig(
    val token: String,
    val databaseId: String,
    val title: String,
    val status: String,
    val statusType: String,
    val statusUnstarted: String,
    val statusInProgress: String,
    val statusCompleted: String,
    val category: String,
    val scheduledDate: String,
    val dueDate: String,
)

/**
 * The one-way UI event emitted when the mapping form is saved.  Keeping the
 * form data together prevents screen callbacks from becoming a second API.
 */
data class ConnectionSettingsUpdate(
    val connection: TaskConnectionConfig,
    val categoryOptions: List<NotionOptionInfo>,
    val statusOptions: List<NotionOptionInfo>,
) {
    fun validationError(): String? = when {
        connection.token.isBlank() || connection.databaseId.isBlank() ->
            "Notion の接続情報を確認してください。"
        listOf(
            connection.title,
            connection.status,
            connection.category,
            connection.scheduledDate,
            connection.dueDate,
        ).any(String::isBlank) -> "必須のプロパティ対応をすべて設定してください。"
        listOf(
            connection.statusUnstarted,
            connection.statusInProgress,
            connection.statusCompleted,
        ).map(String::trim).let { values -> values.any(String::isBlank) || values.distinct().size != values.size } ->
            "状態値は空白でなく、重複しない3つの値を設定してください。"
        else -> null
    }

    fun mappingPreferences(json: Json): Map<String, String> = buildMap {
        put("mapping_prop_title", connection.title)
        put("mapping_prop_status", connection.status)
        put("mapping_prop_status_type", connection.statusType)
        put("mapping_status_unstarted", connection.statusUnstarted.trim())
        put("mapping_status_in_progress", connection.statusInProgress.trim())
        put("mapping_status_completed", connection.statusCompleted.trim())
        put("mapping_prop_category", connection.category)
        put("mapping_prop_scheduled_date", connection.scheduledDate)
        put("mapping_prop_due_date", connection.dueDate)
        categoryOptions.takeIf { it.isNotEmpty() }?.let {
            put("category_options_v2", json.encodeToString(it))
        }
        statusOptions.takeIf { it.isNotEmpty() }?.let {
            put("status_options_v2", json.encodeToString(it))
        }
    }
}
