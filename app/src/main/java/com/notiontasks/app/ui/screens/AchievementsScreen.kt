package com.notiontasks.app.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiontasks.app.data.CategoryStats
import com.notiontasks.app.data.PomodoroLog
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.getCategoryChartColorInCompose
import com.notiontasks.app.data.loadPomodoroLogsAsync
import com.notiontasks.app.ui.components.EmptyStateView
import com.notiontasks.app.ui.components.TaskItemCard
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import java.util.Calendar
import kotlin.math.roundToInt

private data class AchievementsData(
    val overdueCount: Int,
    val carriedOverCount: Int,
    val weekTasks: List<TaskModel>,
    val completedWeekCount: Int,
    val weekRate: Int,
    val monthTasks: List<TaskModel>,
    val completedMonthCount: Int,
    val monthRate: Int,
    val warningTasks: List<TaskModel>
)

@Composable
fun AchievementsScreen(
    viewModel: TaskViewModel,
    statusOptions: List<String>,
    categoryOptions: List<String>,
    onEditTask: (TaskModel) -> Unit
) {
    val uiState by viewModel.tasksState.collectAsState()
    var subPage by remember { mutableStateOf("main") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is TasksUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TasksUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "エラーが発生しました: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is TasksUiState.Idle -> {
                EmptyStateView(
                    message = "タスクデータがありません。同期して実績を確認してください。",
                    onRefresh = { viewModel.syncWithNotion() }
                )
            }
            is TasksUiState.Success -> {
                val todayStr = remember {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                }
                val completedStatus = statusOptions.getOrNull(2) ?: "完了"

                val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
                val startOfWeekCal = remember {
                    Calendar.getInstance().apply {
                        firstDayOfWeek = Calendar.MONDAY
                        val day = get(Calendar.DAY_OF_WEEK)
                        val diff = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
                        add(Calendar.DAY_OF_MONTH, diff)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }
                val endOfWeekCal = remember(startOfWeekCal) {
                    Calendar.getInstance().apply {
                        timeInMillis = startOfWeekCal.timeInMillis
                        add(Calendar.DAY_OF_MONTH, 6)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                }
                val startOfWeekStr = remember(startOfWeekCal) { sdf.format(startOfWeekCal.time) }
                val endOfWeekStr = remember(endOfWeekCal) { sdf.format(endOfWeekCal.time) }

                val startOfMonthCal = remember {
                    Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }
                val endOfMonthCal = remember {
                    Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                }
                val startOfMonthStr = remember(startOfMonthCal) { sdf.format(startOfMonthCal.time) }
                val endOfMonthStr = remember(endOfMonthCal) { sdf.format(endOfMonthCal.time) }

                // 1. Overdue, Carried Over, Week, Month and Warning calculations optimized with remember
                val achievementsData = remember(
                    state.tasks, todayStr, completedStatus,
                    startOfWeekStr, endOfWeekStr, startOfMonthStr, endOfMonthStr
                ) {
                    val overdueCountVal = state.tasks.count { it.status != completedStatus && it.dueDate != null && it.dueDate < todayStr }
                    val carriedOverCountVal = state.tasks.count { it.status != completedStatus && it.scheduledDate != null && it.scheduledDate < todayStr }

                    val weekTasksVal = state.tasks.filter {
                        val sched = it.scheduledDate
                        val due = it.dueDate
                        (sched != null && sched >= startOfWeekStr && sched <= endOfWeekStr) ||
                        (due != null && due >= startOfWeekStr && due <= endOfWeekStr)
                    }
                    val completedWeekCountVal = weekTasksVal.count { it.status == completedStatus }
                    val weekRateVal = if (weekTasksVal.isNotEmpty()) (completedWeekCountVal * 100) / weekTasksVal.size else 0

                    val monthTasksVal = state.tasks.filter {
                        val sched = it.scheduledDate
                        val due = it.dueDate
                        (sched != null && sched >= startOfMonthStr && sched <= endOfMonthStr) ||
                        (due != null && due >= startOfMonthStr && due <= endOfMonthStr)
                    }
                    val completedMonthCountVal = monthTasksVal.count { it.status == completedStatus }
                    val monthRateVal = if (monthTasksVal.isNotEmpty()) (completedMonthCountVal * 100) / monthTasksVal.size else 0

                    val warningTasksVal = state.tasks.filter {
                        it.status != completedStatus && (
                            (it.dueDate != null && it.dueDate < todayStr) ||
                            (it.scheduledDate != null && it.scheduledDate < todayStr)
                        )
                    }.sortedWith(
                        compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                            .thenBy(nullsLast(naturalOrder())) { it.dueDate }
                            .thenBy { it.id }
                    )

                    AchievementsData(
                        overdueCount = overdueCountVal,
                        carriedOverCount = carriedOverCountVal,
                        weekTasks = weekTasksVal,
                        completedWeekCount = completedWeekCountVal,
                        weekRate = weekRateVal,
                        monthTasks = monthTasksVal,
                        completedMonthCount = completedMonthCountVal,
                        monthRate = monthRateVal,
                        warningTasks = warningTasksVal
                    )
                }

                val overdueCount = achievementsData.overdueCount
                val carriedOverCount = achievementsData.carriedOverCount
                val weekTasks = achievementsData.weekTasks
                val completedWeekCount = achievementsData.completedWeekCount
                val weekRate = achievementsData.weekRate
                val monthTasks = achievementsData.monthTasks
                val completedMonthCount = achievementsData.completedMonthCount
                val monthRate = achievementsData.monthRate
                val warningTasks = achievementsData.warningTasks

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tab Selector
                    val completedTasksCount = state.tasks.count { it.status == completedStatus }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF1F1F1),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(
                            "main" to "🏆 目標進捗",
                            "stats" to "📈 作業統計",
                            "completed" to "✅ 完了タスク (${completedTasksCount}件)"
                        ).forEach { (page, label) ->
                            val isSelected = subPage == page
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) {
                                            if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { subPage = page }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        when (page) {
                                            "main" -> MaterialTheme.colorScheme.primary
                                            "stats" -> Color(0xFF0288D1)
                                            else -> Color(0xFF2E7D32)
                                        }
                                    } else {
                                        if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color(0xFF616161)
                                    }
                                )
                            }
                        }
                    }

                    when (subPage) {
                        "main" -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 1. Counters Row (Overdue & Carrying over)
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Overdue Card
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "⚠️ 期限切れタスク",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "$overdueCount 件",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "締め切り超過",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        // Carried Over Card
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFFFF8E1).copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "⏳ 持ち越しタスク",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFD84315)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "$carriedOverCount 件",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFD84315)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "予定日超過",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. Week Achievement rate
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "📅 今週のタスク達成率",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "$weekRate%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            LinearProgressIndicator(
                                                progress = { weekRate / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )

                                            Text(
                                                text = "進捗: $completedWeekCount / ${weekTasks.size} 件完了",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )

                                            val motivationWeek = when {
                                                weekRate == 100 && weekTasks.isNotEmpty() -> "素晴らしい！今週のタスク完全クリアです！ 🎉"
                                                weekRate >= 75 -> "目標達成まであと一息！素晴らしいペースです🔥"
                                                weekRate >= 50 -> "半分達成！この調子で後半も進めましょう🚀"
                                                weekRate > 0 -> "一歩ずつ確実に進んでいます。頑張りましょう💪"
                                                else -> "タスクを設定して開始しましょう！ 📅"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = "💬 $motivationWeek",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Month Achievement rate
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "✨ 今月のタスク達成率",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "$monthRate%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }

                                            LinearProgressIndicator(
                                                progress = { monthRate / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.secondary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )

                                            Text(
                                                text = "進捗: $completedMonthCount / ${monthTasks.size} 件完了",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )

                                            val motivationMonth = when {
                                                monthRate == 100 && monthTasks.isNotEmpty() -> "信じられない快挙！今月パーフェクト達成！ 🏆"
                                                monthRate >= 75 -> "極めて順調です。圧倒的な推進力です！ ✨"
                                                monthRate >= 50 -> "目標の半分を消化。素晴らしい自己管理能力です！ 🌟"
                                                monthRate > 0 -> "良いスタートです。着実な一歩を重ねています。 🍀"
                                                else -> "今月はこれから！コツコツ積み上げましょう📈"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = "💬 $motivationMonth",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // 4. Attention tasks
                                item {
                                    Text(
                                        text = "対応推奨タスク (${warningTasks.size}件)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                if (warningTasks.isEmpty()) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "なし",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "期限遅れのタスクはありません！",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "素晴らしい自己管理計画です。この調子を維持しましょう。✨",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(warningTasks) { task ->
                                        TaskItemCard(
                                            task = task,
                                            statusOptions = statusOptions,
                                            onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                            onEditClick = { onEditTask(task) }
                                        )
                                    }
                                }
                            }
                        }
                        "stats" -> {
                            val categoryColorMap = remember(state.tasks) {
                                state.tasks.filter { it.category.isNotBlank() }.associate { it.category to it.categoryColor }
                            }
                            PomodoroStatsSubPage(
                                context = LocalContext.current,
                                categoryOptions = categoryOptions,
                                categoryColorMap = categoryColorMap
                            )
                        }
                        else -> {
                            // Completed tasks list view sub-page
                            val completedTasks = state.tasks.filter { it.status == completedStatus }

                            if (completedTasks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "なし",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = "完了済みのタスクはありません",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Text(
                                            text = "完了済み (${completedTasks.size}件)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    items(completedTasks) { task ->
                                        TaskItemCard(
                                            task = task,
                                            statusOptions = statusOptions,
                                            onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                            onEditClick = { onEditTask(task) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PomodoroStatsSubPage(
    context: Context,
    categoryOptions: List<String>,
    categoryColorMap: Map<String, String?>
) {
    var pomodoroLogs by remember { mutableStateOf<List<PomodoroLog>>(emptyList()) }
    LaunchedEffect(context) {
        pomodoroLogs = loadPomodoroLogsAsync(context)
    }

    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val currentMonthPrefix = todayStr.substring(0, 7)

    val todayMinutes = remember(pomodoroLogs, todayStr) {
        pomodoroLogs.filter { it.date == todayStr }.sumOf { it.minutes }
    }
    val todayHours = todayMinutes / 60
    val todayMinsRemainder = todayMinutes % 60

    val monthMinutes = remember(pomodoroLogs, currentMonthPrefix) {
        pomodoroLogs.filter { it.date.startsWith(currentMonthPrefix) }.sumOf { it.minutes }
    }
    val monthHoursTotal = monthMinutes / 60

    val allTimeMinutes = remember(pomodoroLogs) {
        pomodoroLogs.sumOf { it.minutes }
    }
    val allTimeHoursTotal = allTimeMinutes / 60

    val thisWeekDays = remember {
        val list = mutableListOf<Pair<String, String>>()
        val sdfLabel = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        val sdfIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val offsetToMonday = when (dayOfWeek) {
            Calendar.SUNDAY -> -6
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> -1
            Calendar.WEDNESDAY -> -2
            Calendar.THURSDAY -> -3
            Calendar.FRIDAY -> -4
            Calendar.SATURDAY -> -5
            else -> 0
        }
        cal.add(Calendar.DAY_OF_MONTH, offsetToMonday)
        
        repeat(7) {
            list.add(Pair(sdfLabel.format(cal.time), sdfIso.format(cal.time)))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        list
    }

    var selectedDate by remember { mutableStateOf<String?>(todayStr) }

    val maxDailyHours = remember(pomodoroLogs, thisWeekDays) {
        var max = 10f
        for (day in thisWeekDays) {
            val dayHours = pomodoroLogs.filter { it.date == day.second }.sumOf { it.minutes } / 60f
            if (dayHours > max) {
                max = dayHours
            }
        }
        max
    }

    val sortedCategories = remember(pomodoroLogs, thisWeekDays) {
        val thisWeekDaysStrings = thisWeekDays.map { it.second }
        val logsInThisWeek = pomodoroLogs.filter { thisWeekDaysStrings.contains(it.date) }
        val totalReportMins = logsInThisWeek.sumOf { it.minutes }
        
        val catGroups = logsInThisWeek.groupBy { it.category }
        val list = catGroups.map { entry ->
            val minutes = entry.value.sumOf { it.minutes }
            val pct = if (totalReportMins > 0) ((minutes.toFloat() / totalReportMins) * 100).roundToInt() else 0
            val firstWithColor = entry.value.firstOrNull { it.categoryColor != null }
            CategoryStats(
                category = entry.key,
                color = firstWithColor?.categoryColor,
                minutes = minutes,
                hours = minutes / 60,
                minsRemainder = minutes % 60,
                percentage = pct
            )
        }.sortedByDescending { it.minutes }
        
        Pair(list, totalReportMins)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFF1F1F1)
                            )
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("今日", "今月", "総計").forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (todayHours > 0) "${todayHours}時間${todayMinsRemainder}分" else "${todayMinsRemainder}分",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))
                        
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = "${monthHoursTotal}時間",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))
                        
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = "${allTimeHoursTotal}時間",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "今週の作業統計",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(text = "${maxDailyHours.roundToInt()}h", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text(text = "${(maxDailyHours / 2).roundToInt()}h", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text(text = "0", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val weekLabels = listOf("月", "火", "水", "木", "金", "土", "日")
                            thisWeekDays.forEachIndexed { index, day ->
                                val dayLogs = pomodoroLogs.filter { it.date == day.second }
                                val categoryMinutes = dayLogs.groupBy { it.category }
                                    .mapValues { entry -> entry.value.sumOf { it.minutes } }
                                val categoryHours = categoryMinutes.mapValues { it.value / 60f }
                                val isSelected = selectedDate == day.second

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .clickable { selectedDate = day.second }
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(90.dp)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFF5F5F5)),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            val categoriesList = (categoryOptions + listOf("一般作業")).distinct()
                                            categoriesList.forEach { cat ->
                                                val hrs = categoryHours[cat] ?: 0f
                                                if (hrs > 0f) {
                                                    val pct = hrs / maxDailyHours
                                                    val colorName = dayLogs.firstOrNull { it.category == cat }?.categoryColor ?: categoryColorMap[cat]
                                                    val color = getCategoryChartColorInCompose(cat, colorName)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height((90 * pct).dp)
                                                            .background(color)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = weekLabels[index],
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = day.first,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedDate?.let { selDate ->
            val dateLogs = pomodoroLogs.filter { it.date == selDate }
            val formattedSelectedDate = try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(selDate)
                if (date != null) {
                    java.text.SimpleDateFormat("M月d日", java.util.Locale.getDefault()).format(date)
                } else {
                    selDate
                }
            } catch (_: Exception) {
                selDate
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎯 $formattedSelectedDate の作業詳細",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val totalMins = dateLogs.sumOf { it.minutes }
                            Text(
                                text = "合計: ${totalMins / 60}時間${totalMins % 60}分 (${dateLogs.size}回)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (dateLogs.isEmpty()) {
                            Text(
                                text = "この日の作業記録はありません。",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                dateLogs.sortedByDescending { log -> log.timestamp }.forEach { log ->
                                    val colorName = log.categoryColor ?: categoryColorMap[log.category]
                                    val catColor = getCategoryChartColorInCompose(log.category, colorName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFFAFAFA),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(catColor)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = log.taskTitle ?: "ポモドーロセッション",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = log.category,
                                                    fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${log.minutes}分",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            val (sortedCats, totalReportMins) = sortedCategories
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "今週の各作業時間",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (totalReportMins > 0) {
                                    var startAngle = -90f
                                    sortedCats.forEach { cat ->
                                        val sweepAngle = (cat.minutes.toFloat() / totalReportMins) * 360f
                                        val colorName = cat.color ?: categoryColorMap[cat.category]
                                        val color = getCategoryChartColorInCompose(cat.category, colorName)
                                        drawArc(
                                            color = color,
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx())
                                        )
                                        startAngle += sweepAngle
                                    }
                                } else {
                                    drawArc(
                                        color = Color.LightGray,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 8.dp.toPx())
                                    )
                                }
                            }
                            Text(
                                text = "Total",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val chunkedCats = sortedCats.take(6).chunked(2)
                            chunkedCats.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { sc ->
                                        val colorName = sc.color ?: categoryColorMap[sc.category]
                                        val color = getCategoryChartColorInCompose(sc.category, colorName)
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(color)
                                                    .align(Alignment.CenterVertically)
                                            )
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = sc.category,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "${sc.percentage}%",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    text = if (sc.hours > 0) "${sc.hours}時間${sc.minsRemainder}分" else "${sc.minsRemainder}分",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
