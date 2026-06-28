package com.notiontasks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.components.EmptyStateView
import com.notiontasks.app.ui.components.TaskItemCard
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState

private data class HomeTasksData(
    val activeTasks: List<TaskModel>,
    val unstartedTasks: List<TaskModel>,
    val inProgressTasks: List<TaskModel>,
    val todayCount: Int
)

@Composable
fun HomeScreen(viewModel: TaskViewModel, statusOptions: List<String>, onEditTask: (TaskModel) -> Unit) {
    val uiState by viewModel.tasksState.collectAsState()

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
                    message = "タスクがありません。右上の更新ボタンを押して同期するか、Notionデータベースを確認してください。",
                    onRefresh = { viewModel.syncWithNotion() }
                )
            }
            is TasksUiState.Success -> {
                var homeFilter by remember { mutableStateOf("all") } // "all" or "today"
                val todayStr = remember {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                }
                val unstartedStatus = remember(statusOptions) { statusOptions.getOrNull(0) ?: "未着手" }
                val inProgressStatus = remember(statusOptions) { statusOptions.getOrNull(1) ?: "進行中" }

                val homeTasksData = remember(state.tasks, homeFilter, statusOptions, todayStr, unstartedStatus, inProgressStatus) {
                    val sortedTasks = state.tasks.sortedWith(
                        compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                            .thenBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.dueDate }
                    )

                    val active = sortedTasks.filter { it.status == unstartedStatus || it.status == inProgressStatus }

                    val filtered = if (homeFilter == "today") {
                        active.filter {
                            (it.scheduledDate != null && it.scheduledDate <= todayStr) ||
                            (it.dueDate != null && it.dueDate <= todayStr)
                        }
                    } else {
                        active
                    }

                    val unstarted = filtered.filter { it.status == unstartedStatus }
                    val inProgress = filtered.filter { it.status == inProgressStatus }

                    val todayCountVal = active.count {
                        (it.scheduledDate != null && it.scheduledDate <= todayStr) ||
                        (it.dueDate != null && it.dueDate <= todayStr)
                    }

                    HomeTasksData(
                        activeTasks = active,
                        unstartedTasks = unstarted,
                        inProgressTasks = inProgress,
                        todayCount = todayCountVal
                    )
                }

                val activeTasks = homeTasksData.activeTasks
                val unstartedTasks = homeTasksData.unstartedTasks
                val inProgressTasks = homeTasksData.inProgressTasks
                val todayCount = homeTasksData.todayCount

                Column(modifier = Modifier.fillMaxSize()) {
                    // Segmented control row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // All button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = if (homeFilter == "all") MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { homeFilter = "all" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "すべて (${activeTasks.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (homeFilter == "all") FontWeight.Bold else FontWeight.Normal,
                                color = if (homeFilter == "all") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Today button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = if (homeFilter == "today") MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { homeFilter = "today" },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "今日やるべきこと",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (homeFilter == "today") FontWeight.Bold else FontWeight.Normal,
                                    color = if (homeFilter == "today") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (homeFilter == "today") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(100.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = todayCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (homeFilter == "today") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (homeFilter == "today") {
                        // Banner for context
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📅 対象日: $todayStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "予定日/締切が今日以前",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    if (activeTasks.isEmpty()) {
                        EmptyStateView(
                            message = "未完了および進行中のタスクはありません！",
                            onRefresh = { viewModel.syncWithNotion() }
                        )
                    } else if (unstartedTasks.isEmpty() && inProgressTasks.isEmpty()) {
                        // Empty today screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "完了",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "今日やるべきタスクはありません",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "今日予定されている、または締め切りの未完了タスクはありません。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { homeFilter = "all" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text("すべてのタスクを表示")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (inProgressTasks.isNotEmpty()) {
                                item {
                                    Text(
                                        text = inProgressStatus,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(inProgressTasks) { task ->
                                    TaskItemCard(
                                        task = task,
                                        statusOptions = statusOptions,
                                        onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                        onEditClick = { onEditTask(task) }
                                    )
                                }
                            }

                            if (unstartedTasks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = unstartedStatus,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(unstartedTasks) { task ->
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
