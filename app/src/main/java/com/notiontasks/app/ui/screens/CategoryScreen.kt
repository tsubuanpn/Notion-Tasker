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
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.components.TaskItemCard
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    viewModel: TaskViewModel,
    categoryOptions: List<NotionOptionInfo>,
    statusOptions: List<NotionOptionInfo>,
    onEditTask: (TaskModel) -> Unit,
    onReorderCategories: (List<NotionOptionInfo>) -> Unit
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val uiState by viewModel.tasksState.collectAsState()

    val categories = remember(uiState, categoryOptions) {
        val allTasks = (uiState as? TasksUiState.Success)?.tasks ?: emptyList()
        val activeTaskCategories = allTasks.map { it.category.trim() }.distinct().filter { it.isNotBlank() }
        
        // 名前が一致する場合は有用な NotionOptionInfo、そうでない場合は一時的なものを作成します
        val activeOptions = activeTaskCategories.map { name ->
            categoryOptions.find { it.name == name } ?: NotionOptionInfo(name = name)
        }
        
        val combinedCategories = (categoryOptions + activeOptions).distinctBy { it.name }.filter { it.name.isNotBlank() }
        combinedCategories.ifEmpty { listOf(NotionOptionInfo(name = "未選択")) }
    }

    val tasksByCategoryAndStatus = remember(uiState, categories, statusOptions) {
        val rawTasks = (uiState as? TasksUiState.Success)?.tasks ?: emptyList()
        val sortedTasks = rawTasks.sortedWith(
            compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                .thenBy(nullsLast(naturalOrder())) { it.dueDate }
        )
        val unstartedStatus = statusOptions.getOrNull(0)?.name ?: "未着手"
        val inProgressStatus = statusOptions.getOrNull(1)?.name ?: "進行中"
        val completedStatus = statusOptions.getOrNull(2)?.name ?: "完了"

        categories.associateWith { cat ->
            val categoryTasks = sortedTasks.filter { it.category == cat.name && it.status != completedStatus }
            val unstartedTasks = categoryTasks.filter { it.status == unstartedStatus }
            val inProgressTasks = categoryTasks.filter { it.status == inProgressStatus }
            Triple(categoryTasks, unstartedTasks, inProgressTasks)
        }
    }

    val initialPage = categories.indexOfFirst { it.name == selectedCategory }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { categories.size }
    val coroutineScope = rememberCoroutineScope()

    val showReorderDialog = remember { mutableStateOf(false) }

    // selectedCategory を同期して、有効なカテゴリであることを確認します
    LaunchedEffect(categories, selectedCategory) {
        if (categories.isNotEmpty() && categories.none { it.name == selectedCategory }) {
            viewModel.selectCategory(categories.first().name)
        }
    }

    // selectedCategory から pagerState を同期します（ユーザーがタブをクリックしたときなど）
    LaunchedEffect(selectedCategory) {
        val index = categories.indexOfFirst { it.name == selectedCategory }
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    // pagerState から selectedCategory を同期します（ユーザーがページをスワイプしたときなど）
    LaunchedEffect(pagerState.settledPage) {
        val targetCategory = categories.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (selectedCategory != targetCategory.name) {
            viewModel.selectCategory(targetCategory.name)
        }
    }

    if (showReorderDialog.value) {
        var tempCategories by remember { mutableStateOf(categories) }
        AlertDialog(
            onDismissRequest = { showReorderDialog.value = false },
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
                                    text = category.name,
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
                        showReorderDialog.value = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReorderDialog.value = false }) {
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
        // 個別のタイプを表すタブ行と並び替えボタン
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PrimaryScrollableTabRow(
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
                            text = { Text(category.name) }
                        )
                    }
                }
            }
            IconButton(
                onClick = { showReorderDialog.value = true },
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
            val pageCategory = categories.getOrNull(page)
            val unstartedStatus = statusOptions.getOrNull(0)?.name ?: "未着手"
            val inProgressStatus = statusOptions.getOrNull(1)?.name ?: "進行中"

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
                        text = "${pageCategory?.name ?: ""} のタスクはありません",
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
