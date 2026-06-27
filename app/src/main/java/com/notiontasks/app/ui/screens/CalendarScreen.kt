package com.notiontasks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.components.TaskItemCard
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState

@Composable
fun CalendarScreen(
    viewModel: TaskViewModel,
    statusOptions: List<String>,
    selectedCalendarDate: MutableState<String?>,
    onEditTask: (TaskModel) -> Unit
) {
    val uiState by viewModel.tasksState.collectAsState()

    var focusYear by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var focusMonth by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) } // 0-indexed

    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    // Initialize selected date to today if it's currently null
    LaunchedEffect(Unit) {
        if (selectedCalendarDate.value == null) {
            selectedCalendarDate.value = todayStr
        }
    }

    val selectedDate = selectedCalendarDate.value ?: todayStr
    val isSystemDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"
    val completedStatus = statusOptions.getOrNull(2) ?: "完了"

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
            is TasksUiState.Idle, is TasksUiState.Success -> {
                val tasks = when (state) {
                    is TasksUiState.Success -> state.tasks
                    else -> emptyList()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Month Navigation Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (focusMonth == 0) {
                                focusMonth = 11
                                focusYear -= 1
                            } else {
                                focusMonth -= 1
                            }
                        }) {
                            Text("<", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Text(
                            text = "${focusYear}年 ${focusMonth + 1}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        IconButton(onClick = {
                            if (focusMonth == 11) {
                                focusMonth = 0
                                focusYear += 1
                            } else {
                                focusMonth += 1
                            }
                        }) {
                            Text(">", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Days of week row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val daysOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")
                        daysOfWeek.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (day == "日") Color.Red else (if (day == "土") Color.Blue else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    // Calendar Grid Layout
                    val calendarHelper = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.YEAR, focusYear)
                        set(java.util.Calendar.MONTH, focusMonth)
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                    val firstDayDayOfWeek = calendarHelper.get(java.util.Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, ...
                    val totalDaysInMonth = calendarHelper.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    val startOffset = firstDayDayOfWeek - 1 // Sunday starts, so offset is simple

                    val totalDaysToDisplay = startOffset + totalDaysInMonth
                    val rowsCount = (totalDaysToDisplay + 6) / 7

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until rowsCount) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val cellIndex = row * 7 + col
                                    val dayNumber = cellIndex - startOffset + 1

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (dayNumber in 1..totalDaysInMonth) {
                                            val dateStr = String.format("%04d-%02d-%02d", focusYear, focusMonth + 1, dayNumber)
                                            val isSelected = dateStr == selectedDate
                                            val isToday = dateStr == todayStr

                                            // Get tasks for this date
                                            val cellTasks = tasks.filter {
                                                it.dueDate == dateStr || it.scheduledDate == dateStr
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier.background(MaterialTheme.colorScheme.primary)
                                                        } else if (isToday) {
                                                            Modifier
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .clickable {
                                                        selectedCalendarDate.value = dateStr
                                                    }
                                                    .padding(2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = dayNumber.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else if (isToday) MaterialTheme.colorScheme.primary
                                                    else if (col == 0) Color.Red
                                                    else if (col == 6) Color.Blue
                                                    else MaterialTheme.colorScheme.onSurface
                                                )

                                                // Draw dots below date number
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    cellTasks.take(3).forEach { task ->
                                                        // Determine dot color
                                                        val dotColor = if (task.status == completedStatus) {
                                                            // Completed is gray-ish
                                                            if (isSystemDark) Color(0xFF616161) else Color(0xFFD6D6D6)
                                                        } else if (task.categoryColor != null) {
                                                            // Use category color
                                                            getNotionCategoryColorRaw(task.categoryColor, isSystemDark)
                                                        } else if (task.status == inProgressStatus) {
                                                            Color(0xFF29B6F6)
                                                        } else {
                                                            when (task.category) {
                                                                "課題" -> Color(0xFF42A5F5)
                                                                "学習" -> Color(0xFFAB47BC)
                                                                "作業" -> Color(0xFFFFCA28)
                                                                "趣味" -> Color(0xFF66BB6A)
                                                                else -> Color(0xFF78909C)
                                                            }
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .padding(horizontal = 0.5.dp)
                                                                .clip(RoundedCornerShape(50))
                                                                .background(dotColor)
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Selected Tasks List Header
                    val splitSelectedDate = selectedDate.split("-")
                    val displaySelectedDate = if (splitSelectedDate.size == 3) {
                        "${splitSelectedDate[1]}月${splitSelectedDate[2]}日"
                    } else {
                        selectedDate
                    }

                    Text(
                        text = "$displaySelectedDate のタスク",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val selectedTasks = tasks.filter {
                        it.dueDate == selectedDate || it.scheduledDate == selectedDate
                    }

                    if (selectedTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "予定はありません",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            selectedTasks.forEach { task ->
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

// Simple color helper for getting base colors of dots
@Suppress("UNUSED_PARAMETER")
private fun getNotionCategoryColorRaw(colorName: String?, isDark: Boolean): Color {
    return when (colorName) {
        "gray" -> Color(0xFF9E9E9E)
        "brown" -> Color(0xFF8D6E63)
        "orange" -> Color(0xFFFF9800)
        "yellow" -> Color(0xFFFFCA28)
        "green" -> Color(0xFF66BB6A)
        "blue" -> Color(0xFF42A5F5)
        "purple" -> Color(0xFFAB47BC)
        "pink" -> Color(0xFFEC407A)
        "red" -> Color(0xFFEF5350)
        else -> Color(0xFF78909C)
    }
}
