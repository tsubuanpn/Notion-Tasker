package com.notiontasks.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@Suppress("unused")
@Serializable
enum class TaskStatus(val value: String) {
    UNSTARTED("未着手"),
    IN_PROGRESS("進行中"),
    COMPLETED("完了");
}

@Suppress("unused")
@Serializable
enum class TaskCategory(val value: String) {
    ASSIGNMENT("課題"),
    LEARNING("学習"),
    WORK("作業"),
    HOBBY("趣味"),
    OTHER("他");
}

@OptIn(InternalSerializationApi::class)
@Serializable
data class TaskModel(
    val id: String,
    val title: String,
    val status: String, // Dynamic string representing task status
    val category: String, // Dynamic string representing task category
    val dueDate: String?, // Format: YYYY-MM-DD
    val scheduledDate: String?, // Format: YYYY-MM-DD
    val statusColor: String? = null,
    val categoryColor: String? = null
)
