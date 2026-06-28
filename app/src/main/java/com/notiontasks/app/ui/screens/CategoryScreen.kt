package com.notiontasks.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.components.TaskItemCard
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    viewModel: TaskViewModel,
    categoryOptions: List<String>,
    statusOptions: List<String>,
    onEditTask: (TaskModel) -> Unit,
    onReorderCategories: (List<String>) -> Unit
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val uiState by viewModel.tasksState.collectAsState()

    val categories = remember(uiState, categoryOptions) {
        val allTasks = (uiState as? TasksUiState.Success)?.tasks ?: emptyList()
        val activeTaskCategories = allTasks.map { it.category.trim() }.distinct().filter { it.isNotBlank() }
        val combinedCategories = (categoryOptions.map { it.trim() } + activeTaskCategories).distinct().filter { it.isNotBlank() }
        combinedCategories.ifEmpty { listOf("課題", "学習", "作業", "趣味", "他") }
    }

    val tasksByCategoryAndStatus = remember(uiState, categories, statusOptions) {
        val rawTasks = (uiState as? TasksUiState.Success)?.tasks ?: emptyList()
        val sortedTasks = rawTasks.sortedWith(
            compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                .thenBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.dueDate }
        )
        val unstartedStatus = statusOptions.getOrNull(0) ?: "未着手"
        val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"
        val completedStatus = statusOptions.getOrNull(2) ?: "完了"

        categories.associateWith { cat ->
            val categoryTasks = sortedTasks.filter { it.category == cat && it.status != completedStatus }
            val unstartedTasks = categoryTasks.filter { it.status == unstartedStatus }
            val inProgressTasks = categoryTasks.filter { it.status == inProgressStatus }
            Triple(categoryTasks, unstartedTasks, inProgressTasks)
        }
    }

    val initialPage = categories.indexOf(selectedCategory).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { categories.size }
    val coroutineScope = rememberCoroutineScope()

    var showReorderDialog by remember { mutableStateOf(false) }

    // Sync selectedCategory to ensure it's a valid category
    LaunchedEffect(categories, selectedCategory) {
        if (categories.isNotEmpty() && !categories.contains(selectedCategory)) {
            viewModel.selectCategory(categories.first())
        }
    }

    // Sync pagerState from selectedCategory (e.g. when user clicks a tab)
    LaunchedEffect(selectedCategory) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    // Sync selectedCategory from pagerState (e.g. when user swipe pages)
    LaunchedEffect(pagerState.settledPage) {
        val targetCategory = categories.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (selectedCategory != targetCategory) {
            viewModel.selectCategory(targetCategory)
        }
    }

    if (showReorderDialog) {
        var tempCategories by remember { mutableStateOf(categories) }
        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            title = { Text("種別の並び替え") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("表示する種別の順番を設定します。")
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(tempCategories.size) { index ->
                            val category = tempCategories[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val newList = tempCategories.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index - 1, item)
                                                tempCategories = newList
                                            }
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "上へ"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < tempCategories.size - 1) {
                                                val newList = tempCategories.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index + 1, item)
                                                tempCategories = newList
                                            }
                                        },
                                        enabled = index < tempCategories.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "下へ"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReorderCategories(tempCategories)
                        showReorderDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReorderDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab row representing distinct types along with a sort button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEachIndexed { index, category ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(category) }
                        )
                    }
                }
            }
            IconButton(
                onClick = { showReorderDialog = true },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "並び替え"
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { page ->
            val pageCategory = categories.getOrNull(page) ?: ""
            val unstartedStatus = statusOptions.getOrNull(0) ?: "未着手"
            val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"

            val triple = tasksByCategoryAndStatus[pageCategory] ?: Triple(emptyList(), emptyList(), emptyList())
            val categoryTasks = triple.first
            val unstartedTasks = triple.second
            val inProgressTasks = triple.third

            if (categoryTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$pageCategory のタスクはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
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
                        items(inProgressTasks.size) { index ->
                            val task = inProgressTasks[index]
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
                        items(unstartedTasks.size) { index ->
                            val task = unstartedTasks[index]
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
