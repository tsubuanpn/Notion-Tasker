package com.notiontasks.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import java.util.Calendar

fun getNotionCategoryColors(colorName: String?, isDark: Boolean): Pair<Color, Color> {
    return when (colorName) {
        "gray" -> if (isDark) Pair(Color(0x339E9E9E), Color(0xFFE0E0E0)) else Pair(Color(0xFFF5F5F5), Color(0xFF616161))
        "brown" -> if (isDark) Pair(Color(0x268D6E63), Color(0xFFD7CCC8)) else Pair(Color(0xFFEFEBE9), Color(0xFF4E342E))
        "orange" -> if (isDark) Pair(Color(0x26FF9800), Color(0xFFFFCC80)) else Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "yellow" -> if (isDark) Pair(Color(0x26FFEB3B), Color(0xFFFFF59D)) else Pair(Color(0xFFFFFDE7), Color(0xFFF57F17))
        "green" -> if (isDark) Pair(Color(0x264CAF50), Color(0xFFA5D6A7)) else Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "blue" -> if (isDark) Pair(Color(0x262196F3), Color(0xFF90CAF9)) else Pair(Color(0xFFE3F2FD), Color(0xFF0D47A1))
        "purple" -> if (isDark) Pair(Color(0x269C27B0), Color(0xFFE1BEE7)) else Pair(Color(0xFFF3E5F5), Color(0xFF4A148C))
        "pink" -> if (isDark) Pair(Color(0x26E91E63), Color(0xFFF8BBD0)) else Pair(Color(0xFFFCE4EC), Color(0xFF880E4F))
        "red" -> if (isDark) Pair(Color(0x26F44336), Color(0xFFEF9A9A)) else Pair(Color(0xFFFFEBEE), Color(0xFFB71C1C))
        else -> if (isDark) Pair(Color(0x2678909C), Color(0xFFECEFF1)) else Pair(Color(0xFFECEFF1), Color(0xFF37474F))
    }
}

fun getNotionStatusColors(colorName: String?, isDark: Boolean): Pair<Color, Color> {
    return getNotionCategoryColors(colorName, isDark)
}

@Composable
fun TaskItemCard(
    task: TaskModel,
    statusOptions: List<NotionOptionInfo>,
    onStatusClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val inProgressStatus = statusOptions.getOrNull(1)?.name ?: "進行中"
    val completedStatus = statusOptions.getOrNull(2)?.name ?: "完了"

    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val isCompleted = task.status == completedStatus
    val isInProgress = task.status == inProgressStatus
    val isUnstarted = !isCompleted && !isInProgress

    val isOverdueDue = !isCompleted && task.dueDate != null && task.dueDate < todayStr
    val isOverdueScheduled = !isCompleted && task.scheduledDate != null && task.scheduledDate < todayStr

    val isSystemDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val cardBorder = if (isCompleted) {
        val color = if (isSystemDark) Color(0xFF0D3D2A) else Color(0xFFBBF7D0)
        androidx.compose.foundation.BorderStroke(1.dp, color)
    } else if (isInProgress) {
        val color = if (isSystemDark) Color(0xFF0D4D7A) else Color(0xFFBAE6FD)
        androidx.compose.foundation.BorderStroke(1.dp, color)
    } else {
        val color = if (isSystemDark) Color(0xFF3F3F46) else Color(0xFFCCCCCC)
        androidx.compose.foundation.BorderStroke(1.dp, color)
    }

    val cardBgColor = if (isCompleted) {
        if (isSystemDark) Color(0xFF0F261D) else Color(0xFFF0FDF4)
    } else if (isInProgress) {
        if (isSystemDark) Color(0xFF0D2533) else Color(0xFFF0F9FF)
    } else {
        if (isSystemDark) Color(0xFF18181B) else Color.White
    }

    val categoryColors = if (task.categoryColor != null) {
        getNotionCategoryColors(task.categoryColor, isSystemDark)
    } else {
        getNotionCategoryColors("default", isSystemDark)
    }

    val statusColors = if (isUnstarted) {
        if (isSystemDark) Pair(Color(0xFF27272A), Color(0xFFD4D4D8)) else Pair(Color(0xFFF4F4F5), Color(0xFF3F3F46))
    } else if (task.statusColor != null) {
        getNotionStatusColors(task.statusColor, isSystemDark)
    } else {
        getNotionStatusColors("default", isSystemDark)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // カテゴリ & アラートバッジの行
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // カテゴリチップバッジ
                    Box(
                        modifier = Modifier
                            .background(
                                color = categoryColors.first,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = categoryColors.second,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // アラートバッジ
                    if (isOverdueDue) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ 期限切れ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isOverdueScheduled) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ 持ち越し",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 動的でアクション可能なステータスボタン
                Box(
                    modifier = Modifier
                        .background(color = statusColors.first, shape = RoundedCornerShape(8.dp))
                        .clickable { onStatusClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = task.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColors.second,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // タスクヘッダー
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 日付目標フッター
            if (task.dueDate != null || task.scheduledDate != null) {
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.dueDate != null) {
                        val isDueOver = task.status != completedStatus && task.dueDate < todayStr
                        Text(
                            text = if (isDueOver) "⚠️ 締切: ${task.dueDate}" else "締切: ${task.dueDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDueOver) Color(0xFFC62828) else Color.Red.copy(alpha = 0.8f),
                            fontWeight = if (isDueOver) FontWeight.Bold else FontWeight.Normal
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (task.scheduledDate != null) {
                        val isSchedOver = task.status != completedStatus && task.scheduledDate < todayStr
                        Text(
                            text = if (isSchedOver) "持ち越し: ${task.scheduledDate}" else "予定: ${task.scheduledDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSchedOver) Color(0xFFE65100) else Color.Gray,
                            fontWeight = if (isSchedOver) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    message: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            lineHeight = 24.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("データを同期する")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    initialCategory: String,
    categoryOptions: List<NotionOptionInfo>,
    initialScheduledDate: String = "",
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, dueDate: String?, scheduledDate: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory) }
    var dueDate by remember { mutableStateOf("") }
    var scheduledDate by remember { mutableStateOf(initialScheduledDate) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val calendar = Calendar.getInstance()

    val dueDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format(java.util.Locale.US, "%02d", month + 1)
            val formattedDay = String.format(java.util.Locale.US, "%02d", dayOfMonth)
            dueDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val scheduledDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format(java.util.Locale.US, "%02d", month + 1)
            val formattedDay = String.format(java.util.Locale.US, "%02d", dayOfMonth)
            scheduledDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいタスクを追加", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タスク名 (必須)") },
                    placeholder = { Text("例: 課題の提出") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // カテゴリ選択
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("種類 (カテゴリ)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isCategoryDropdownExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "種類選択")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false }
                    ) {
                        categoryOptions.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    category = cat.name
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("締め切り (任意)") },
                    placeholder = { Text("タップして日付を選択") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                dueDatePickerDialog.show()
                                focusManager.clearFocus()
                            }
                        },
                    singleLine = true
                )

                OutlinedTextField(
                    value = scheduledDate,
                    onValueChange = { scheduledDate = it },
                    label = { Text("予定日 (任意)") },
                    placeholder = { Text("タップして日付を選択") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                scheduledDatePickerDialog.show()
                                focusManager.clearFocus()
                            }
                        },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, category, dueDate.ifBlank { null }, scheduledDate.ifBlank { null })
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskModel,
    categoryOptions: List<NotionOptionInfo>,
    statusOptions: List<NotionOptionInfo>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, status: String, dueDate: String?, scheduledDate: String?) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var category by remember { mutableStateOf(task.category) }
    var status by remember { mutableStateOf(task.status) }
    var dueDate by remember { mutableStateOf(task.dueDate ?: "") }
    var scheduledDate by remember { mutableStateOf(task.scheduledDate ?: "") }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var isStatusDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val calendar = Calendar.getInstance()

    val dueDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format(java.util.Locale.US, "%02d", month + 1)
            val formattedDay = String.format(java.util.Locale.US, "%02d", dayOfMonth)
            dueDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val scheduledDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format(java.util.Locale.US, "%02d", month + 1)
            val formattedDay = String.format(java.util.Locale.US, "%02d", dayOfMonth)
            scheduledDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タスクを編集", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タスク名 (必須)") },
                    placeholder = { Text("例: 課題の提出") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // カテゴリ選択
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("種類 (カテゴリ)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isCategoryDropdownExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "種類選択")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false }
                    ) {
                        categoryOptions.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    category = cat.name
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // ステータス選択
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("状態") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isStatusDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "状態選択")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = isStatusDropdownExpanded,
                        onDismissRequest = { isStatusDropdownExpanded = false }
                    ) {
                        statusOptions.forEach { stat ->
                            DropdownMenuItem(
                                text = { Text(stat.name) },
                                onClick = {
                                    status = stat.name
                                    isStatusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("締め切り (任意)") },
                        placeholder = { Text("タップして選択") },
                        readOnly = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    dueDatePickerDialog.show()
                                    focusManager.clearFocus()
                                }
                            },
                        singleLine = true
                    )
                    if (dueDate.isNotBlank()) {
                        IconButton(onClick = { dueDate = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = scheduledDate,
                        onValueChange = { scheduledDate = it },
                        label = { Text("予定日 (任意)") },
                        placeholder = { Text("タップして選択") },
                        readOnly = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scheduledDatePickerDialog.show()
                                    focusManager.clearFocus()
                                }
                            },
                        singleLine = true
                    )
                    if (scheduledDate.isNotBlank()) {
                        IconButton(onClick = { scheduledDate = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, category, status, dueDate.ifBlank { null }, scheduledDate.ifBlank { null })
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
