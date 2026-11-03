package com.notiontasks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.components.getNotionCategoryColors
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** カレンダーを既定に、時間割を同一階層で切り替える計画画面。 */
@Composable
fun PlanOverviewScreen(
    viewModel: TaskViewModel,
    selectedCalendarDate: MutableState<String?>,
    statusOptions: List<NotionOptionInfo>,
    onOpenSchedule: () -> Unit,
    onEditTask: (TaskModel) -> Unit,
) {
    val tasksState by viewModel.tasksState.collectAsState()
    val completedStatus by viewModel.statusCompleted.collectAsState()
    val inProgressStatus by viewModel.statusInProgress.collectAsState()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val tasks = (tasksState as? TasksUiState.Success)?.tasks.orEmpty()
    val selectedDate = selectedCalendarDate.value ?: today
    var year by remember { mutableIntStateOf(selectedDate.take(4).toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf((selectedDate.substringAfter("-").take(2).toIntOrNull() ?: Calendar.getInstance().get(Calendar.MONTH) + 1) - 1) }
    val tasksByDate = remember(tasks) {
        buildMap<String, MutableList<TaskModel>> {
            tasks.forEach { task ->
                listOfNotNull(task.scheduledDate, task.dueDate).distinct().forEach { date ->
                    getOrPut(date) { mutableListOf() }.add(task)
                }
            }
        }
    }
    val selectedTasks = remember(tasks, selectedDate) {
        tasks.filter { it.scheduledDate == selectedDate || it.dueDate == selectedDate }
            .sortedBy { it.scheduledDate ?: it.dueDate }
    }
    val calendarDays = remember(year, month) { monthCells(year, month) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PlanModeTabs(
                calendarSelected = true,
                onOpenCalendar = {},
                onOpenSchedule = onOpenSchedule,
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${year}年${month + 1}月", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        MonthMoveButton(Icons.AutoMirrored.Filled.ArrowBack, "前月") {
                            if (month == 0) { month = 11; year -= 1 } else month -= 1
                        }
                        MonthMoveButton(Icons.AutoMirrored.Filled.ArrowForward, "翌月") {
                            if (month == 11) { month = 0; year += 1 } else month += 1
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("日", "月", "火", "水", "木", "金", "土").forEachIndexed { index, day ->
                            Text(
                                day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = when (index) {
                                    0 -> MaterialTheme.colorScheme.error
                                    6 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    calendarDays.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEachIndexed { column, day ->
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(0.92f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (day != null) {
                                        val isSelected = day.date == selectedDate
                                        val isToday = day.date == today
                                        val dots = tasksByDate[day.date].orEmpty().take(3)
                                        Column(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                .clickable { selectedCalendarDate.value = day.date },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                day.number.toString(),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday -> MaterialTheme.colorScheme.primary
                                                    column == 0 -> MaterialTheme.colorScheme.error
                                                    column == 6 -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            if (dots.isNotEmpty()) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    dots.forEach { task ->
                                                        Box(
                                                            Modifier.size(4.dp).background(
                                                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                else getNotionCategoryColors(task.categoryColor, isDark).second,
                                                                CircleShape,
                                                            ),
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
            }
        }
        item {
            val title = formatSelectedDate(selectedDate)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${selectedTasks.size}件のタスク", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) }
        selectedTasks.forEach { task ->
            item(key = "task-${task.id}") {
                TaskListCard(
                    task = task,
                    isCompleted = completedStatus.isNotBlank() && task.status.trim() == completedStatus.trim(),
                    isInProgress = inProgressStatus.isNotBlank() && task.status.trim() == inProgressStatus.trim(),
                    onEdit = { onEditTask(task) },
                    onCycle = { viewModel.cycleTaskStatus(task, statusOptions) },
                )
            }
        }
        if (selectedTasks.isEmpty()) {
            item {
                Text(
                    "この日のタスクはありません。",
                    modifier = Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun PlanModeTabs(
    calendarSelected: Boolean,
    onOpenCalendar: () -> Unit,
    onOpenSchedule: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlanTab("カレンダー", Icons.Default.CalendarMonth, selected = calendarSelected, modifier = Modifier.weight(1f), onClick = onOpenCalendar)
        PlanTab("時間割", Icons.Default.Schedule, selected = !calendarSelected, modifier = Modifier.weight(1f), onClick = onOpenSchedule)
    }
}

@Composable
private fun PlanTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthMoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description)
    }
}

private data class PlanCalendarDay(val number: Int, val date: String)

private fun monthCells(year: Int, month: Int): List<PlanCalendarDay?> {
    val calendar = Calendar.getInstance().apply { set(year, month, 1) }
    val offset = calendar.get(Calendar.DAY_OF_WEEK) - 1
    val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = MutableList<PlanCalendarDay?>(offset) { null }
    repeat(days) { index -> cells += PlanCalendarDay(index + 1, String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, index + 1)) }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private fun formatSelectedDate(date: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return date
    SimpleDateFormat("M月d日（E）", Locale.JAPAN).format(parsed)
} catch (_: Exception) { date }
