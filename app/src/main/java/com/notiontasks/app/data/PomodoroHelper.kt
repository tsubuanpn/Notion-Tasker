package com.notiontasks.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.notiontasks.app.data.local.PomodoroLogEntity
import com.notiontasks.app.data.local.TaskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// PomodoroLog 用のデータクラス
data class PomodoroLog(
    val id: String,
    val taskId: String?,
    val taskTitle: String?,
    val category: String,
    val categoryColor: String?,
    val date: String,
    val minutes: Int,
    val timestamp: Long,
)

// CategoryStats 用のデータクラス
data class CategoryStats(
    val category: String,
    val color: String?,
    val minutes: Int,
    val hours: Int,
    val minsRemainder: Int,
    val percentage: Int,
)

fun getCategoryChartColorInCompose(colorName: String?): Color {
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

suspend fun loadPomodoroLogs(context: Context): List<PomodoroLog> = withContext(Dispatchers.IO) {
    val db = TaskDatabase.getInstance(context)
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
            timestamp = entity.timestamp,
        )
    }
}

// レガシーな命名との互換性のために残す（中身は async と同じ）
suspend fun loadPomodoroLogsAsync(context: Context): List<PomodoroLog> = loadPomodoroLogs(context)

suspend fun savePomodoroLogs(context: Context, logs: List<PomodoroLog>) = withContext(Dispatchers.IO) {
    val db = TaskDatabase.getInstance(context)
    db.pomodoroLogDao.clearAllLogs()
    val entities = logs.map { log ->
        PomodoroLogEntity(
            id = log.id,
            taskId = log.taskId,
            taskTitle = log.taskTitle,
            category = log.category,
            categoryColor = log.categoryColor,
            date = log.date,
            minutes = log.minutes,
            timestamp = log.timestamp,
        )
    }
    db.pomodoroLogDao.insertLogs(entities)
}

/**
 * 特定のログを追加または更新する（テーブル全体をクリアしない効率的な方法）
 */
