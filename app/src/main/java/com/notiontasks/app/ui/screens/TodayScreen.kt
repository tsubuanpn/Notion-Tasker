package com.notiontasks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.components.rememberJapanToday
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import com.notiontasks.app.utils.JapanTime
import kotlinx.coroutines.delay
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
    onOpenPlan: () -> Unit,
    onOpenTasks: () -> Unit,
    onStartFocus: (TaskModel) -> Unit,
) {
    val tasksState by viewModel.tasksState.collectAsState()
    val timeBlocks by viewModel.timeBlocks.collectAsState()
    val unstartedStatus by viewModel.statusUnstarted.collectAsState()
    val inProgressStatus by viewModel.statusInProgress.collectAsState()
    val today = rememberJapanToday()
    var nowMinutes by remember { mutableIntStateOf(currentMinutes()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L); nowMinutes = currentMinutes() } }

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
            val dayBlocks = remember(timeBlocks, today) { timeBlocks.filter { it.type == "task" && it.date == today }.sortedBy { it.startTime } }
            val taskById = remember(tasks) { tasks.associateBy { it.id } }
            val upcomingBlocks = remember(dayBlocks, nowMinutes) { dayBlocks.filter { it.endTime >= nowMinutes }.take(2) }
            val assignedIds = remember(dayBlocks) { dayBlocks.mapNotNull { it.associatedId }.toSet() }
            val unplannedTasks = remember(activeTasks, assignedIds, inProgressTask) {
                activeTasks.asSequence().filter { it.id !in assignedIds && it.id != inProgressTask?.id }
                    .sortedBy {
                        JapanTime.projectedDateKey(it.scheduledDate)
                            ?: JapanTime.projectedDateKey(it.dueDate)
                            ?: "9999-12-31"
                    }.take(2).toList()
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
                TodayScheduleSection(upcomingBlocks, taskById, onOpenPlan)
                UnplannedSection(unplannedTasks, onOpenTasks)
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
private fun TodayScheduleSection(blocks: List<TimeBlock>, taskById: Map<String, TaskModel>, onOpenPlan: () -> Unit) {
    BriefingSectionCard("これからのタスク予定（最大2件）", Icons.Default.CalendarMonth, onOpenPlan) {
        if (blocks.isEmpty()) EmptyBriefingRow("今日の予定はありません") else blocks.forEachIndexed { index, block ->
            val task = block.associatedId?.let(taskById::get)
            ScheduleBriefingRow(task?.title ?: block.title, "${formatTime(block.startTime)} – ${formatTime(block.endTime)}", task?.category.orEmpty(), index == 0, onOpenPlan)
        }
    }
}

@Composable
private fun UnplannedSection(tasks: List<TaskModel>, onOpenTasks: () -> Unit) {
    BriefingSectionCard("今日に未割当のタスク（最大2件）", Icons.Default.TaskAlt, onOpenTasks) {
        if (tasks.isEmpty()) EmptyBriefingRow("未計画タスクはありません") else tasks.forEach { task ->
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenTasks).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (task.category.isNotBlank()) Text(task.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, "タスクを開く", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BriefingSectionCard(title: String, icon: ImageVector, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (onClick != null) Icon(Icons.Default.ChevronRight, "$title を開く", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
            if (onClick != null) {
                Text("一覧を見る", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable private fun EmptyBriefingRow(message: String) = Text(message, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun ScheduleBriefingRow(title: String, time: String, category: String, isFirst: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).background(if (isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(time, category).filter { it.isNotBlank() }.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, "計画を開く", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun currentMinutes(): Int = com.notiontasks.app.utils.JapanTime.now().let { it.hour * 60 + it.minute }
private fun formatTime(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
