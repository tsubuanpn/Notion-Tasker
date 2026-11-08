package com.notiontasks.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.components.getNotionCategoryColors
import com.notiontasks.app.ui.components.getNotionStatusColors
import com.notiontasks.app.ui.components.rememberJapanToday
import com.notiontasks.app.ui.theme.WarningOrangeOnContainerDark
import com.notiontasks.app.ui.theme.WarningOrangeOnContainerLight
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import com.notiontasks.app.utils.JapanTime
import java.util.Date
import java.util.Locale

@Composable
fun TasksScreen(
    viewModel: TaskViewModel,
    statusOptions: List<NotionOptionInfo>,
    categoryOptions: List<NotionOptionInfo>,
    onEditTask: (TaskModel) -> Unit,
    searchActive: Boolean,
    periods: Set<String>,
    onPeriodsChange: (Set<String>) -> Unit,
) {
    val tasksState by viewModel.tasksState.collectAsState()
    val completed by viewModel.statusCompleted.collectAsState()
    val inProgress by viewModel.statusInProgress.collectAsState()
    var query by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf(emptySet<String>()) }
    var statuses by remember { mutableStateOf(emptySet<String>()) }
    var periodExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    val today = rememberJapanToday()
    val source = (tasksState as? TasksUiState.Success)?.tasks.orEmpty()
    val visible = filterOpenTasksByPeriod(source, completed, periods, today).filter { task ->
        (categories.isEmpty() || task.category in categories) && (statuses.isEmpty() || task.status in statuses) &&
            (query.isBlank() || task.title.contains(query, true) || task.category.contains(query, true))
    }.sortedBy(::taskPeriodSortKey)
    val hasActiveConditions = query.isNotBlank() || periods.isNotEmpty() || categories.isNotEmpty() || statuses.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (searchActive) OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(18.dp), leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, placeholder = { Text("タスクを検索") },
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box { FilterChip(selected = periods.isNotEmpty(), onClick = { periodExpanded = true }, label = { Text("期間${if (periods.isEmpty()) "▼" else "：${periods.size}"}") }); DropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) { TaskPeriod.all.forEach { value -> DropdownMenuItem(text = { Text(if (value in periods) "✓ $value" else value) }, onClick = { onPeriodsChange(periods.toggle(value)) }) } } }
            Box { FilterChip(selected = categories.isNotEmpty(), onClick = { categoryExpanded = true }, label = { Text("種類${if (categories.isEmpty()) "▼" else "：${categories.size}"}") }); DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) { categoryOptions.forEach { option -> DropdownMenuItem(text = { Text(if (option.name in categories) "✓ ${option.name}" else option.name) }, onClick = { categories = categories.toggle(option.name) }) } } }
            Box { FilterChip(selected = statuses.isNotEmpty(), onClick = { statusExpanded = true }, label = { Text("状態${if (statuses.isEmpty()) "▼" else "：${statuses.size}"}") }); DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) { statusOptions.forEach { option -> DropdownMenuItem(text = { Text(if (option.name in statuses) "✓ ${option.name}" else option.name) }, onClick = { statuses = statuses.toggle(option.name) }) } } }
        }
        if (hasActiveConditions) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!searchActive && query.isNotBlank()) {
                    FilterChip(
                        selected = true,
                        onClick = { query = "" },
                        label = { Text("検索: $query ×") },
                    )
                }
                periods.sorted().forEach { value ->
                    FilterChip(selected = true, onClick = { onPeriodsChange(periods - value) }, label = { Text("期間: $value ×") })
                }
                categories.sorted().forEach { value ->
                    FilterChip(selected = true, onClick = { categories = categories - value }, label = { Text("種類: $value ×") })
                }
                statuses.sorted().forEach { value ->
                    FilterChip(selected = true, onClick = { statuses = statuses - value }, label = { Text("状態: $value ×") })
                }
                TextButton(
                    onClick = {
                        query = ""
                        onPeriodsChange(emptySet())
                        categories = emptySet()
                        statuses = emptySet()
                    },
                ) {
                    Text("すべて解除")
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(visible, key = { it.id }) { task ->
                TaskListCard(
                    task = task,
                    isCompleted = task.status.trim() == completed.trim(),
                    isInProgress = task.status.trim() == inProgress.trim(),
                    onEdit = { onEditTask(task) },
                    onCycle = { viewModel.cycleTaskStatus(task, statusOptions) },
                )
            }
            if (visible.isEmpty()) {
                item {
                    val message = when (tasksState) {
                        TasksUiState.Loading -> "タスクを読み込んでいます"
                        is TasksUiState.Error -> "タスクを読み込めませんでした。端末のデータを確認してください。"
                        TasksUiState.Idle -> "タスクはまだありません"
                        is TasksUiState.Success -> "条件に一致するタスクはありません"
                    }
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 32.dp))
                }
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

@Composable
internal fun TaskListCard(
    task: TaskModel,
    isCompleted: Boolean,
    isInProgress: Boolean = false,
    onEdit: () -> Unit,
    onCycle: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val categoryColors = getNotionCategoryColors(task.categoryColor, isDark)
    val statusColors = getNotionStatusColors(task.statusColor, isDark)
    val scheduledLabel = task.scheduledDate?.let { "予定 $it" } ?: "予定なし"
    val dueLabel = task.dueDate?.let { "締切 $it" } ?: "締切なし"
    val today = rememberJapanToday()
    val scheduledDate = JapanTime.projectedDateKey(task.scheduledDate)
    val dueDate = JapanTime.projectedDateKey(task.dueDate)
    val scheduledDateColor = when {
        !isCompleted && scheduledDate != null && scheduledDate < today -> MaterialTheme.colorScheme.error
        !isCompleted && scheduledDate == today -> if (isDark) WarningOrangeOnContainerDark else WarningOrangeOnContainerLight
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }
    val dueDateColor = when {
        !isCompleted && dueDate != null && dueDate < today -> MaterialTheme.colorScheme.error
        !isCompleted && dueDate == today -> if (isDark) WarningOrangeOnContainerDark else WarningOrangeOnContainerLight
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onCycle,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = when {
                        isCompleted -> Icons.Default.CheckCircle
                        isInProgress -> Icons.Default.PlayCircle
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = "${task.title}を次の状態へ変更",
                    modifier = Modifier.size(26.dp),
                    tint = statusColors.second,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (task.category.isNotBlank()) {
                    Surface(
                        color = categoryColors.first,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColors.second,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = scheduledDateColor,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = scheduledLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheduledDateColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = dueDateColor,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = dueLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = dueDateColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
