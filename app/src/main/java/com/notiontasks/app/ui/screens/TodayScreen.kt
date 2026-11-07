package com.notiontasks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.components.rememberJapanToday
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import com.notiontasks.app.utils.JapanTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val japanTodayHeaderFormatter = DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPAN)

/** Renders the Home date from the app-wide JST calendar, never the device default zone. */
internal fun formatJapanTodayHeader(date: LocalDate = JapanTime.today()): String =
    date.format(japanTodayHeaderFormatter)

/** 毎日同じ三つの情報枠で、次の行動を判断するホーム画面。 */
@Composable
fun TodayScreen(
    viewModel: TaskViewModel,
    statusOptions: List<NotionOptionInfo>,
    onOpenTasks: (Set<String>) -> Unit,
    onStartFocus: (TaskModel) -> Unit,
) {
    val tasksState by viewModel.tasksState.collectAsState()
    val unstartedStatus by viewModel.statusUnstarted.collectAsState()
    val inProgressStatus by viewModel.statusInProgress.collectAsState()
    val completedStatus by viewModel.statusCompleted.collectAsState()
    val today = rememberJapanToday()

    when (val state = tasksState) {
        is TasksUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is TasksUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("タスクを読み込めませんでした", color = MaterialTheme.colorScheme.error) }
        else -> {
            val tasks = (state as? TasksUiState.Success)?.tasks.orEmpty()
            val activeTasks = remember(tasks, unstartedStatus, inProgressStatus) {
                tasks.filter { it.status.trim() == unstartedStatus.trim() || it.status.trim() == inProgressStatus.trim() }
            }
            val inProgressTask = remember(activeTasks, inProgressStatus) {
                representativeInProgressTask(activeTasks, inProgressStatus)
            }
            val todayTasks = remember(tasks, completedStatus, today) {
                filterOpenTasksByPeriod(tasks, completedStatus, setOf(TaskPeriod.TODAY), today)
                    .sortedBy(::taskPeriodSortKey)
            }
            val unassignedTasks = remember(tasks, completedStatus, today) {
                filterOpenTasksByPeriod(tasks, completedStatus, setOf(TaskPeriod.UNASSIGNED), today)
                    .sortedBy(::taskPeriodSortKey)
            }

            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(formatJapanTodayHeader(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                InProgressSection(
                    task = inProgressTask,
                    totalCount = activeTasks.count { it.status.trim() == inProgressStatus.trim() },
                    onComplete = inProgressTask?.let { { viewModel.cycleTaskStatus(it, statusOptions) } },
                    onStartFocus = inProgressTask?.let { { onStartFocus(it) } },
                )
                TodayTasksSection(todayTasks, onOpenTasks)
                UnassignedSection(unassignedTasks, onOpenTasks)
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun InProgressSection(task: TaskModel?, totalCount: Int, onComplete: (() -> Unit)?, onStartFocus: (() -> Unit)?) {
    BriefingSectionCard("進行中のタスク（${totalCount}件）", Icons.Default.PlayArrow) {
        if (task == null) EmptyBriefingRow("進行中のタスクはありません") else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.PlayArrow, "進行中", Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(9.dp), MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(task.category.ifBlank { "進行中" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.RadioButtonUnchecked, "完了にする", Modifier.size(28.dp).clickable { onComplete?.invoke() }, MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).clickable { onStartFocus?.invoke() }.padding(vertical = 12.dp), Arrangement.Center, Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                Text("集中する", Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TodayTasksSection(tasks: List<TaskModel>, onOpenTasks: (Set<String>) -> Unit) {
    BriefingSectionCard("今日のタスク", Icons.Default.TaskAlt) {
        if (tasks.isEmpty()) EmptyBriefingRow("今日のタスクはありません") else tasks.take(2).forEach { task ->
            TaskBriefingRow(task, onClick = { onOpenTasks(setOf(TaskPeriod.TODAY)) })
        }
        if (tasks.size >= 2) {
            MoreTasksButton(onClick = { onOpenTasks(setOf(TaskPeriod.TODAY)) })
        }
    }
}

@Composable
private fun UnassignedSection(tasks: List<TaskModel>, onOpenTasks: (Set<String>) -> Unit) {
    BriefingSectionCard("未割当のタスク", Icons.Default.TaskAlt) {
        if (tasks.isEmpty()) EmptyBriefingRow("未割当のタスクはありません") else tasks.take(2).forEach { task ->
            TaskBriefingRow(task, onClick = { onOpenTasks(setOf(TaskPeriod.UNASSIGNED)) })
        }
        if (tasks.size >= 2) {
            MoreTasksButton(onClick = { onOpenTasks(setOf(TaskPeriod.UNASSIGNED)) })
        }
    }
}

@Composable
private fun BriefingSectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable private fun EmptyBriefingRow(message: String) = Text(message, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun TaskBriefingRow(task: TaskModel, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.category.isNotBlank()) Text(task.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, "タスクを開く", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoreTasksButton(onClick: () -> Unit) {
    Text(
        "さらに表示",
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
