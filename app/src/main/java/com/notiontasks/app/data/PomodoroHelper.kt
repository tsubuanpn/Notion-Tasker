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

// グループ化された PomodoroLog 用のデータクラス
data class GroupedPomodoroLog(
    val taskTitle: String?,
    val category: String,
    val categoryColor: String?,
    val totalMinutes: Int,
    val startTime: Long,
    val endTime: Long,
    val logIds: List<String>,
    val sessionCount: Int
)

fun formatDurationJapanese(minutes: Int): String {
    if (minutes < 60) return "${minutes}分"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}時間" else "${h}時間${m}分"
}

fun formatDecimal(value: Double): String {
    return if (value % 1.0 == 0.0) {
        "${value.toInt()}"
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
}

fun groupPomodoroLogs(logs: List<PomodoroLog>): List<GroupedPomodoroLog> {
    if (logs.isEmpty()) return emptyList()

    // タイムスタンプで昇順ソート（古い順）
    val sortedLogs = logs.sortedBy { it.timestamp }
    val result = mutableListOf<GroupedPomodoroLog>()

    var currentGroupLogs = mutableListOf<PomodoroLog>()

    for (log in sortedLogs) {
        if (currentGroupLogs.isEmpty()) {
            currentGroupLogs.add(log)
            continue
        }

        val lastLog = currentGroupLogs.last()
        val isSameTask = log.taskId == lastLog.taskId &&
                (log.taskId != null || (log.taskTitle == lastLog.taskTitle && log.category == lastLog.category))

        // 前のログの終了時刻
        val lastEndTime = lastLog.timestamp
        // 現在のログの開始時刻
        val currentStartTime = log.timestamp - (log.minutes * 60 * 1000L)

        // 1時間(3600000ms)以内の空きであれば連続とみなす
        val isContinuous = (currentStartTime - lastEndTime) <= 3600000L

        if (isSameTask && isContinuous) {
            currentGroupLogs.add(log)
        } else {
            result.add(createGroupFromLogs(currentGroupLogs))
            currentGroupLogs = mutableListOf(log)
        }
    }

    if (currentGroupLogs.isNotEmpty()) {
        result.add(createGroupFromLogs(currentGroupLogs))
    }

    // 表示は新しい順（終了時刻の降順）
    return result.sortedByDescending { it.endTime }
}

private fun createGroupFromLogs(logs: List<PomodoroLog>): GroupedPomodoroLog {
    val firstLog = logs.first()
    val lastLog = logs.last()
    val totalMinutes = logs.sumOf { it.minutes }
    val startTime = firstLog.timestamp - (firstLog.minutes * 60 * 1000L)
    val endTime = lastLog.timestamp

    return GroupedPomodoroLog(
        taskTitle = firstLog.taskTitle,
        category = firstLog.category,
        categoryColor = firstLog.categoryColor,
        totalMinutes = totalMinutes,
        startTime = startTime,
        endTime = endTime,
        logIds = logs.map { it.id },
        sessionCount = logs.size
    )
}

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

suspend fun insertPomodoroLog(context: Context, log: PomodoroLog) {
    getPomodoroRepository(context).insertPomodoroLog(log)
}

suspend fun deletePomodoroLogById(context: Context, id: String) {
    getPomodoroRepository(context).deletePomodoroLogById(id)
}
