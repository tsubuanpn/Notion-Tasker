package com.notiontasks.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TimeBlock(
    val id: String,
    val type: String, // "task" or "life"
    val title: String,
    val associatedId: String? = null, // Store Task id if type == "task"
    val startTime: Int, // Minutes from midnight (e.g. 540 = 09:00)
    val endTime: Int, // Minutes from midnight (e.g. 600 = 10:00)
    val color: String, // Hex color string, e.g. "#4CAF50"
    val date: String // "yyyy-MM-dd"
)

@Serializable
data class LifeActivity(
    val id: String,
    val name: String,
    val durationMinutes: Int, // Default duration in minutes
    val color: String, // Hex color string, e.g. "#FF9800"
    val defaultStartTime: Int? = null, // Default start time in minutes from midnight (null if none)
    val defaultEndTime: Int? = null // Default end time in minutes from midnight (null if none)
)
