package com.notiontasks.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
@Serializable
data class TaskModel(
    val id: String,
    val title: String,
    val status: String, // タスクのステータスを表す動的な文字列
    val category: String, // タスクのカテゴリを表す動的な文字列
    val dueDate: String?, // 形式: YYYY-MM-DD
    val scheduledDate: String?, // 形式: YYYY-MM-DD
    val statusColor: String? = null,
    val categoryColor: String? = null
)
