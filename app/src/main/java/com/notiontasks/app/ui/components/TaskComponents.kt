package com.notiontasks.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.theme.*
import java.util.Calendar

fun getNotionCategoryColors(colorName: String?, isDark: Boolean): Pair<Color, Color> {
    return when (colorName) {
        "gray" -> if (isDark) Pair(Color(0xFF2C2C2E), Color(0xFFEBEBF5)) else Pair(Color(0xFFF2F2F7), Color(0xFF8E8E93))
        "brown" -> if (isDark) Pair(Color(0xFF3C3028), Color(0xFFD2B48C)) else Pair(Color(0xFFEFEBE9), Color(0xFF5D4037))
        "orange" -> if (isDark) Pair(Color(0xFF4D2B00), Color(0xFFFFB347)) else Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "yellow" -> if (isDark) Pair(Color(0xFF4D4400), Color(0xFFFDFD96)) else Pair(Color(0xFFFFFDE7), Color(0xFFF57F17))
        "green" -> if (isDark) Pair(Color(0xFF003D1B), Color(0xFF77DD77)) else Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "blue" -> if (isDark) Pair(Color(0xFF002D4D), Color(0xFF89CFF0)) else Pair(Color(0xFFE3F2FD), Color(0xFF0D47A1))
        "purple" -> if (isDark) Pair(Color(0xFF2D004D), Color(0xFFB39EB5)) else Pair(Color(0xFFF3E5F5), Color(0xFF6A1B9A))
        "pink" -> if (isDark) Pair(Color(0xFF4D002D), Color(0xFFFFB7C5)) else Pair(Color(0xFFFCE4EC), Color(0xFFAD1457))
        "red" -> if (isDark) Pair(Color(0xFF4D0000), Color(0xFFFF6961)) else Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> if (isDark) Pair(Color(0xFF3A3A3C), Color(0xFFD1D1D6)) else Pair(Color(0xFFF2F2F7), Color(0xFF636366))
    }
}

fun getNotionStatusColors(colorName: String?, isDark: Boolean): Pair<Color, Color> {
    return getNotionCategoryColors(colorName, isDark)
}

@Composable
fun TaskItemCard(
    task: TaskModel,
    inProgressStatus: String,
    completedStatus: String,
    onStatusClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val isCompleted = task.status == completedStatus
    val isInProgress = task.status == inProgressStatus

    val isOverdueDue = !isCompleted && task.dueDate != null && task.dueDate < todayStr
    val isOverdueScheduled = !isCompleted && task.scheduledDate != null && task.scheduledDate < todayStr

    val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val cardBorder = if (isCompleted) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else if (isInProgress) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    val cardBgColor = when {
        isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isInProgress -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }

    val categoryColors = getNotionCategoryColors(task.categoryColor, isSystemDark)
    val statusColors = getNotionStatusColors(task.statusColor, isSystemDark)

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
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "⚠️ 期限切れ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (isOverdueScheduled) {
                        Surface(
                            color = if (isSystemDark) WarningOrangeContainerDark else WarningOrangeContainerLight,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "⚠️ 持ち越し",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSystemDark) WarningOrangeOnContainerDark else WarningOrangeOnContainerLight,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // アクション可能な動的ステータスボタン
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

            // タスク名（タイトル）
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 期日/予定日のフッター
            if (task.dueDate != null || task.scheduledDate != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            color = if (isDueOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                            color = if (isSchedOver) {
                                if (isSystemDark) WarningOrangeOnContainerDark else WarningOrangeOnContainerLight
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    statusOptions: List<NotionOptionInfo>,
    initialScheduledDate: String = "",
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, status: String?, dueDate: String?, scheduledDate: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory) }
    var status by remember { mutableStateOf(statusOptions.firstOrNull()?.name.orEmpty()) }
    var dueDate by remember { mutableStateOf("") }
    var scheduledDate by remember { mutableStateOf(initialScheduledDate) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var isStatusDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("タスクを追加", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Clear, contentDescription = "閉じる") }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("タスク名を入力") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    QuickAddChoice("状態", status.ifBlank { "未着手" }, Modifier.fillMaxWidth()) { isStatusDropdownExpanded = true }
                    DropdownMenu(expanded = isStatusDropdownExpanded, onDismissRequest = { isStatusDropdownExpanded = false }) {
                        statusOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = { status = option.name; isStatusDropdownExpanded = false })
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    QuickAddChoice("種類", category.ifBlank { "未設定" }, Modifier.fillMaxWidth()) { isCategoryDropdownExpanded = true }
                    DropdownMenu(expanded = isCategoryDropdownExpanded, onDismissRequest = { isCategoryDropdownExpanded = false }) {
                        categoryOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = { category = option.name; isCategoryDropdownExpanded = false })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAddChoice(
                    label = "予定",
                    value = scheduledDate.ifBlank { "予定なし" },
                    modifier = Modifier.weight(1f),
                    onClick = { scheduledDatePickerDialog.show() },
                )
                QuickAddChoice(
                    label = "締切",
                    value = dueDate.ifBlank { "未設定" },
                    modifier = Modifier.weight(1f),
                    onClick = { dueDatePickerDialog.show() },
                )
            }
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title, category, status.ifBlank { null }, dueDate.ifBlank { null }, scheduledDate.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("追加する", fontWeight = FontWeight.Bold) }
            Text("保存後にNotionへ同期されます", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAddChoice(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(56.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (onClear != null) {
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "$label を解除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = "$label を変更", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Clear, contentDescription = "閉じる") }
                    Text("タスクを編集", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { if (title.isNotBlank()) onConfirm(title, category, status, dueDate.ifBlank { null }, scheduledDate.ifBlank { null }) },
                        enabled = title.isNotBlank(),
                    ) { Text("保存", fontWeight = FontWeight.Bold) }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("タスク名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(16.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        QuickAddChoice("状態", status, Modifier.fillMaxWidth()) { isStatusDropdownExpanded = true }
                        DropdownMenu(expanded = isStatusDropdownExpanded, onDismissRequest = { isStatusDropdownExpanded = false }) {
                            statusOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.name) }, onClick = { status = option.name; isStatusDropdownExpanded = false })
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        QuickAddChoice("種類", category.ifBlank { "未設定" }, Modifier.fillMaxWidth()) { isCategoryDropdownExpanded = true }
                        DropdownMenu(expanded = isCategoryDropdownExpanded, onDismissRequest = { isCategoryDropdownExpanded = false }) {
                            categoryOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.name) }, onClick = { category = option.name; isCategoryDropdownExpanded = false })
                            }
                        }
                    }
                }
                Text("日付", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickAddChoice(
                        "予定",
                        scheduledDate.ifBlank { "予定なし" },
                        Modifier.weight(1f),
                        onClear = scheduledDate.takeIf { it.isNotBlank() }?.let { { scheduledDate = "" } },
                    ) { scheduledDatePickerDialog.show() }
                    QuickAddChoice(
                        "締切",
                        dueDate.ifBlank { "未設定" },
                        Modifier.weight(1f),
                        onClear = dueDate.takeIf { it.isNotBlank() }?.let { { dueDate = "" } },
                    ) { dueDatePickerDialog.show() }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("保存後にNotionへ反映します。オフライン時は次回同期します", modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
