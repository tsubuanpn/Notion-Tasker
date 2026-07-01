package com.notiontasks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.components.EmptyStateView
import com.notiontasks.app.ui.components.TaskItemCard
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HomeTasksData(
    val totalActiveCount: Int,
    val unstartedTasks: List<TaskModel>,
    val inProgressTasks: List<TaskModel>,
    val allCount: Int,
    val todayCount: Int
)

@Composable
fun HomeScreen(
    viewModel: TaskViewModel,
    statusOptions: List<NotionOptionInfo>,
    onEditTask: (TaskModel) -> Unit,
    isSearchActive: Boolean = false
) {
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
                var searchQuery by remember { mutableStateOf("") }

                LaunchedEffect(isSearchActive) {
                    if (!isSearchActive) {
                        searchQuery = ""
                    }
                }
                val todayStr = remember {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                }
                val unstartedStatus = remember(statusOptions) { statusOptions.getOrNull(0)?.name ?: "未着手" }
                val inProgressStatus = remember(statusOptions) { statusOptions.getOrNull(1)?.name ?: "進行中" }

                val homeTasksData = remember(state.tasks, homeFilter, searchQuery, statusOptions, todayStr, unstartedStatus, inProgressStatus) {
                    val sortedTasks = state.tasks.sortedWith(
                        compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                            .thenBy(nullsLast(naturalOrder())) { it.dueDate }
                    )

                    val active = sortedTasks.filter { it.status == unstartedStatus || it.status == inProgressStatus }

                    fun filterBySearch(tasks: List<TaskModel>): List<TaskModel> {
                        if (searchQuery.isBlank()) return tasks
                        return tasks.filter { task ->
                            task.title.contains(searchQuery, ignoreCase = true) ||
                            task.category.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    val todayActive = active.filter {
                        it.scheduledDate == todayStr ||
                        (it.scheduledDate != null && it.scheduledDate < todayStr) ||
                        (it.dueDate != null && it.dueDate < todayStr)
                    }

                    val allFiltered = filterBySearch(active)
                    val todayFiltered = filterBySearch(todayActive)

                    val filtered = if (homeFilter == "today") todayFiltered else allFiltered

                    val unstarted = filtered.filter { it.status == unstartedStatus }
                    val inProgress = filtered.filter { it.status == inProgressStatus }

                    HomeTasksData(
                        totalActiveCount = active.size,
                        unstartedTasks = unstarted,
                        inProgressTasks = inProgress,
                        allCount = allFiltered.size,
                        todayCount = todayFiltered.size
                    )
                }

                val totalActiveCount = homeTasksData.totalActiveCount
                val unstartedTasks = homeTasksData.unstartedTasks
                val inProgressTasks = homeTasksData.inProgressTasks
                val allCount = homeTasksData.allCount
                val todayCount = homeTasksData.todayCount

                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("タスク名やカテゴリで検索...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "検索",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "クリア",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
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
                                text = "すべて ($allCount)",
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

                    if (totalActiveCount == 0) {
                        EmptyStateView(
                            message = "未完了および進行中のタスクはありません！",
                            onRefresh = { viewModel.syncWithNotion() }
                        )
                    } else if (unstartedTasks.isEmpty() && inProgressTasks.isEmpty()) {
                        if (searchQuery.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "検索結果なし",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "検索結果が見つかりません",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "「$searchQuery」に一致するタスクはありません。別のキーワードをお試しください。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { searchQuery = "" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Text("検索条件をクリア")
                                }
                            }
                        } else {
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
