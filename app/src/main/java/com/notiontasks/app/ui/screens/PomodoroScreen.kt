package com.notiontasks.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.notiontasks.app.PomodoroService
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.theme.*
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState

private const val POMODOROS_BEFORE_LONG_BREAK = 4

private fun pomodoroDurationSecondsFor(mode: String, prefs: SharedPreferences): Int = when (mode) {
    "work" -> prefs.getInt("work_duration_min", 25) * 60
    "shortBreak" -> prefs.getInt("short_break_duration_min", 5) * 60
    else -> prefs.getInt("long_break_duration_min", 15) * 60
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    viewModel: TaskViewModel,
    boundService: PomodoroService?,
    devModeEnabled: Boolean = false,
    devCompleteButtonEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val savedCompletedCountDate = remember { prefs.getString("completed_count_date", "") ?: "" }
    val initialCompletedCount = if (savedCompletedCountDate == todayStr) prefs.getInt("completed_count", 0) else 0
    
    var timeLeft by remember { mutableIntStateOf(prefs.getInt("work_duration_min", 25) * 60) }
    var isRunning by remember { mutableStateOf(value = false) }
    var mode by remember { mutableStateOf("work") } // "work" (作業), "shortBreak" (短い休憩), "longBreak" (長い休憩)
    var pomodoroCompletedCount by remember { mutableIntStateOf(initialCompletedCount) }
    val initialTaskId = remember { prefs.getString("selected_task_id", null) }
    var selectedTaskId by remember { mutableStateOf(initialTaskId) }
    var isInitialSyncDone by remember { mutableStateOf(value = false) }

    val tasksState by viewModel.tasksState.collectAsState()
    val inProgressStatus by viewModel.statusInProgress.collectAsState()
    val completedStatus by viewModel.statusCompleted.collectAsState()

    val activeFocusTask = remember(selectedTaskId, tasksState) {
        when (val state = tasksState) {
            is TasksUiState.Success -> state.tasks.find { it.id == selectedTaskId }
            else -> null
        }
    }

    fun triggerServiceAction(action: String, durationMinutes: Int = -1) {
        val intent = Intent(context, PomodoroService::class.java).apply {
            this.action = action
            putExtra(PomodoroService.EXTRA_TASK_ID, selectedTaskId)
            putExtra(PomodoroService.EXTRA_TASK_TITLE, activeFocusTask?.title)
            putExtra(PomodoroService.EXTRA_TASK_CATEGORY, activeFocusTask?.category)
            putExtra(PomodoroService.EXTRA_TASK_CATEGORY_COLOR, activeFocusTask?.categoryColor)
            putExtra(PomodoroService.EXTRA_MODE, mode)
            if (durationMinutes > 0) {
                putExtra(PomodoroService.EXTRA_DURATION_MINUTES, durationMinutes)
            }
        }
        context.startForegroundService(intent)
    }

    LaunchedEffect(selectedTaskId) {
        prefs.edit { putString("selected_task_id", selectedTaskId) }
    }

    LaunchedEffect(tasksState) {
        if (tasksState is TasksUiState.Success) {
            val successState = tasksState as TasksUiState.Success
            val selectedTask = successState.tasks.find { it.id == selectedTaskId }
            // 選択中のタスクが存在しない、または既に「完了」ステータスの場合は選択を解除する
            if (selectedTaskId != null && (selectedTask == null || selectedTask.status.trim() == completedStatus.trim())) {
                // 自動解除の前に、タイマーが動いていれば一時停止命令をサービスに送る（レースコンディション回避）
                if (isRunning) {
                    triggerServiceAction(PomodoroService.ACTION_PAUSE)
                }
                selectedTaskId = null
            }
        }
    }

    LaunchedEffect(todayStr, savedCompletedCountDate) {
        if (savedCompletedCountDate != todayStr) {
            prefs.edit {
                putInt("completed_count", 0)
                putString("completed_count_date", todayStr)
            }
        }
    }
    
    val uncompletedTasks = remember(tasksState, completedStatus, todayStr) {
        val completedTrimmed = completedStatus.trim()
        when (val state = tasksState) {
            is TasksUiState.Success -> {
                state.tasks.asSequence().filter { task ->
                    val isUncompleted = task.status.trim() != completedTrimmed
                    isUncompleted && (
                        (task.scheduledDate == todayStr) ||
                        ((task.scheduledDate != null) && (task.scheduledDate < todayStr)) ||
                        ((task.dueDate != null) && (task.dueDate < todayStr))
                    )
                }.sortedWith(
                    compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                        .thenBy(nullsLast(naturalOrder())) { it.dueDate }
                        .thenBy { it.id },
                ).toList()
            }
            else -> emptyList()
        }
    }

    // 選択されたタスクの状態をバインドされた PomodoroService に同期します
    LaunchedEffect(selectedTaskId, activeFocusTask, boundService, isInitialSyncDone) {
        if (!isInitialSyncDone) return@LaunchedEffect
        
        boundService?.let { service ->
            // もしタスクが選択されているが、activeFocusTaskがまだロード中の場合は同期を待つ（nullで上書きしないため）
            if (selectedTaskId != null && activeFocusTask == null) {
                return@LaunchedEffect
            }
            
            // サービス側のタスク情報が、現在の選択状態（ID、タイトル、カテゴリなど）と異なる場合にのみ更新する
            val isSame = service.associatedTaskId == selectedTaskId &&
                         service.associatedTaskTitle == activeFocusTask?.title &&
                         service.associatedTaskCategory == activeFocusTask?.category
            
            if (!isSame) {
                service.updateFocusedTask(
                    taskId = selectedTaskId,
                    taskTitle = activeFocusTask?.title,
                    category = activeFocusTask?.category,
                    categoryColor = activeFocusTask?.categoryColor,
                )
            }
        }
    }

    // サービスに基づいて状態を更新します
    var isAlarmPlaying by remember { mutableStateOf(value = false) }
    DisposableEffect(boundService) {
        if (boundService != null) {
            if (!boundService.isRunning) {
                boundService.updateModeAndDuration(boundService.currentMode)
            }
            timeLeft = (boundService.timeLeftMs / 1000).toInt()
            isRunning = boundService.isRunning
            mode = boundService.currentMode
            pomodoroCompletedCount = boundService.getCompletedCountToday()
            selectedTaskId = if (boundService.isRunning) {
                boundService.associatedTaskId
            } else {
                prefs.getString("selected_task_id", null)
            }
            isInitialSyncDone = true

            isAlarmPlaying = boundService.isRingtonePlaying

            boundService.onTickListener = { ms, _ ->
                timeLeft = (ms / 1000).toInt()
            }
            boundService.onFinishedListener = {
                val nextMode = boundService.currentMode
                val nextTimeLeftSec = (boundService.timeLeftMs / 1000).toInt()
                val nextCompletedCount = boundService.getCompletedCountToday()
                
                when (nextMode) {
                    "work" -> {
                        Toast.makeText(context, "休憩終了！次の集中セッションを始めましょう。", Toast.LENGTH_LONG).show()
                    }
                    "shortBreak" -> {
                        Toast.makeText(context, "集中セッション完了！素晴らしいです！短い休憩をとりましょう。", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(context, "集中セッション${POMODOROS_BEFORE_LONG_BREAK}回お疲れさました！長めの休憩をとりましょう。", Toast.LENGTH_LONG).show()
                    }
                }
                
                mode = nextMode
                timeLeft = nextTimeLeftSec
                pomodoroCompletedCount = nextCompletedCount
                isRunning = false
            }
            boundService.onStateChangedListener = { running ->
                isRunning = running
            }
            boundService.onRingtoneStateChangedListener = { playing ->
                isAlarmPlaying = playing
            }
            boundService.onSessionTransitionListener = { nextMode, ms ->
                mode = nextMode
                timeLeft = (ms / 1000).toInt()
                pomodoroCompletedCount = boundService.getCompletedCountToday()
            }
        }
        onDispose {
            boundService?.onTickListener = null
            boundService?.onFinishedListener = null
            boundService?.onStateChangedListener = null
            boundService?.onRingtoneStateChangedListener = null
            boundService?.onSessionTransitionListener = null
            isInitialSyncDone = false
        }
    }

    LaunchedEffect(Unit) {
        if ((boundService == null) && (!isRunning)) {
            timeLeft = pomodoroDurationSecondsFor(mode, prefs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 2. モード選択セグメントコントロール
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(
                Triple("work", "集中", MaterialTheme.colorScheme.primary),
                Triple("shortBreak", "休憩", MaterialTheme.colorScheme.secondary),
                Triple("longBreak", "長い休憩", MaterialTheme.colorScheme.tertiary)
            ).forEach { (m, label, activeColor) ->
                val isSelected = mode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            mode = m
                            timeLeft = pomodoroDurationSecondsFor(m, prefs)
                            triggerServiceAction(PomodoroService.ACTION_STOP)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isSelected) {
                            activeColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        // 3. タイマー表示カード
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // 背景に薄く大きなタイマーアイコンを表示
                val modeColor = when (mode) {
                    "work" -> MaterialTheme.colorScheme.primary
                    "shortBreak" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }

                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = modeColor.copy(alpha = 0.05f),
                    modifier = Modifier.size(160.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // タイマーの数字テキスト
                    val minutes = timeLeft / 60
                    val seconds = timeLeft % 60
                    val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                    
                    Text(
                        text = timeStr,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-1).sp
                    )

                    // 再生 / 一時停止 / リセットボタン
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // リセットボタン
                        IconButton(
                            onClick = {
                                triggerServiceAction(PomodoroService.ACTION_STOP)
                                isRunning = false
                                timeLeft = pomodoroDurationSecondsFor(mode, prefs)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(50)
                                )
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "リセット",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                if (isRunning) {
                                    triggerServiceAction(PomodoroService.ACTION_PAUSE)
                                } else {
                                    val durationMinutes = pomodoroDurationSecondsFor(mode, prefs) / 60
                                    triggerServiceAction(PomodoroService.ACTION_START_OR_RESUME, durationMinutes)
                                    if (mode == "work" && activeFocusTask != null) {
                                        val stTrimmed = activeFocusTask.status.trim()
                                        if (stTrimmed != inProgressStatus.trim() && stTrimmed != completedStatus.trim()) {
                                            viewModel.updateTask(
                                                id = activeFocusTask.id,
                                                title = activeFocusTask.title,
                                                status = inProgressStatus,
                                                category = activeFocusTask.category,
                                                dueDate = activeFocusTask.dueDate,
                                                scheduledDate = activeFocusTask.scheduledDate
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = modeColor,
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "一時停止" else "開始",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 開発者モード用: 強制完了ボタン
                        if (devModeEnabled && devCompleteButtonEnabled && mode == "work") {
                            IconButton(
                                onClick = {
                                    triggerServiceAction(PomodoroService.ACTION_COMPLETE)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "強制完了 (Dev)",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // アラーム停止ボタン (表示条件: アラーム再生中でタイマーは停止)
                        if (!isRunning && isAlarmPlaying) {
                            IconButton(
                                onClick = {
                                    boundService?.stopRingtonePlayback()
                                    isAlarmPlaying = false
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsOff,
                                    contentDescription = "アラーム停止",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 統計インジケーター
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日の完了数:",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pomodoroCompletedCount}回",
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (pomodoroCompletedCount > 0) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                repeat(minOf(pomodoroCompletedCount, 8)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                                    )
                                }
                                if (pomodoroCompletedCount > 8) {
                                    Text(
                                        text = "+",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. タスクの紐付けセクション
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "集中するタスクを紐付ける",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // カスタムのシンプルなドロップダウン
            var dropdownExpanded by remember { mutableStateOf(value = false) }
            val selectedTaskTitle = activeFocusTask?.let { "[${it.category}] ${it.title}" } ?: "-- タスクを選択しない (一般作業) --"

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedTaskTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("-- タスクを選択しない (一般作業) --", fontWeight = FontWeight.Medium, fontSize = 12.sp) },
                        onClick = {
                            selectedTaskId = null
                            dropdownExpanded = false
                            boundService?.updateFocusedTask(
                                taskId = null,
                                taskTitle = null,
                                category = null,
                                categoryColor = null
                            )
                        }
                    )
                    uncompletedTasks.forEach { task ->
                        val isOverdueDue = task.dueDate != null && task.dueDate < todayStr
                        val isOverdueScheduled = task.scheduledDate != null && task.scheduledDate < todayStr
                        val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "[${task.category}] ${task.title}",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                if (isOverdueDue) {
                                    Text(
                                        text = "⚠️ 期限",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isOverdueScheduled) {
                                    val orangeColor = if (isSystemDark) WarningOrangeOnContainerDark else WarningOrangeOnContainerLight
                                    Text(
                                        text = "⚠️ 持ち越し",
                                        color = orangeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            onClick = {
                                selectedTaskId = task.id
                                dropdownExpanded = false
                                boundService?.updateFocusedTask(
                                    taskId = task.id,
                                    taskTitle = task.title,
                                    category = task.category,
                                    categoryColor = task.categoryColor
                                )
                                if (isRunning && mode == "work") {
                                    val stTrimmed = task.status.trim()
                                    if (stTrimmed != inProgressStatus.trim() && stTrimmed != completedStatus.trim()) {
                                        viewModel.updateTask(
                                             id = task.id,
                                             title = task.title,
                                             status = inProgressStatus,
                                             category = task.category,
                                             dueDate = task.dueDate,
                                             scheduledDate = task.scheduledDate
                                         )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // 5. フォーカス中のタスクパネルカード
        activeFocusTask?.let { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "現在フォーカス中",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "分類: ${task.category}  •  状態: ${task.status}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            // 明示的にサービスへ一時停止命令を送る
                            if (isRunning) {
                                triggerServiceAction(PomodoroService.ACTION_PAUSE)
                            }
                            
                            viewModel.updateTask(
                                id = task.id,
                                title = task.title,
                                status = completedStatus,
                                category = task.category,
                                dueDate = task.dueDate,
                                scheduledDate = task.scheduledDate
                            )
                            selectedTaskId = null
                            Toast.makeText(context, "タスクを完了しました！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "完了",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
