package com.notiontasks.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

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
