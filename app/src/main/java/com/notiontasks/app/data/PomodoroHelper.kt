package com.notiontasks.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.repository.PomodoroRepository

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

private fun getPomodoroRepository(context: Context): PomodoroRepository {
    val database = TaskDatabase.getInstance(context.applicationContext)
    return PomodoroRepository(database.pomodoroLogDao)
}

suspend fun loadPomodoroLogs(context: Context): List<PomodoroLog> {
    return getPomodoroRepository(context).loadPomodoroLogs()
}

suspend fun loadPomodoroLogsAsync(context: Context): List<PomodoroLog> = loadPomodoroLogs(context)

suspend fun savePomodoroLogs(context: Context, logs: List<PomodoroLog>) {
    getPomodoroRepository(context).savePomodoroLogs(logs)
}
