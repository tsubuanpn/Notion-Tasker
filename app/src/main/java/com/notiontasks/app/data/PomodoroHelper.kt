package com.notiontasks.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color

// PomodoroLog 用のデータクラス
data class PomodoroLog(
    val id: String,
    val taskId: String?,
    val taskTitle: String?,
    val category: String,
    val categoryColor: String?,
    val date: String,
    val minutes: Int,
    val timestamp: Long
)

// CategoryStats 用のデータクラス
data class CategoryStats(
    val category: String,
    val color: String?,
    val minutes: Int,
    val hours: Int,
    val minsRemainder: Int,
    val percentage: Int
)

fun getCategoryChartColorInCompose(category: String, colorName: String?): Color {
    return when (colorName?.lowercase()) {
        "gray" -> Color(0xFF9E9E9E)
        "brown" -> Color(0xFF8D6E63)
        "orange" -> Color(0xFFFF9800)
        "yellow" -> Color(0xFFFFCA28)
        "green" -> Color(0xFF10B981)
        "blue" -> Color(0xFF3B82F6)
        "purple" -> Color(0xFF8B5CF6)
        "pink" -> Color(0xFFEC4899)
        "red" -> Color(0xFFEF5350)
        else -> Color(0xFF737373)
    }
}

fun loadPomodoroLogs(context: Context): List<PomodoroLog> {
    val db = com.notiontasks.app.data.local.TaskDatabase.getInstance(context)
    val entities = kotlinx.coroutines.runBlocking {
        db.pomodoroLogDao.getAllLogs()
    }
    return entities.map { entity ->
        PomodoroLog(
            id = entity.id,
            taskId = entity.taskId,
            taskTitle = entity.taskTitle,
            category = entity.category,
            categoryColor = entity.categoryColor,
            date = entity.date,
            minutes = entity.minutes,
            timestamp = entity.timestamp
        )
    }
}

suspend fun loadPomodoroLogsAsync(context: Context): List<PomodoroLog> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = com.notiontasks.app.data.local.TaskDatabase.getInstance(context)
        val entities = db.pomodoroLogDao.getAllLogs()
        entities.map { entity ->
            PomodoroLog(
                id = entity.id,
                taskId = entity.taskId,
                taskTitle = entity.taskTitle,
                category = entity.category,
                categoryColor = entity.categoryColor,
                date = entity.date,
                minutes = entity.minutes,
                timestamp = entity.timestamp
            )
        }
    }
}

fun savePomodoroLogs(context: Context, logs: List<PomodoroLog>) {
    val db = com.notiontasks.app.data.local.TaskDatabase.getInstance(context)
    kotlinx.coroutines.runBlocking {
        db.pomodoroLogDao.clearAllLogs()
        val entities = logs.map { log ->
            com.notiontasks.app.data.local.PomodoroLogEntity(
                id = log.id,
                taskId = log.taskId,
                taskTitle = log.taskTitle,
                category = log.category,
                categoryColor = log.categoryColor,
                date = log.date,
                minutes = log.minutes,
                timestamp = log.timestamp
            )
        }
        db.pomodoroLogDao.insertLogs(entities)
    }
}
