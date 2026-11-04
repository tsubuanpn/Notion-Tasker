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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
    var focusType by remember { mutableStateOf(PomodoroService.FOCUS_TYPE_POMODORO) }
    var showPomodoroPhaseSheet by remember { mutableStateOf(false) }
    var freeTimerMinutes by remember { mutableIntStateOf(prefs.getInt("free_timer_duration_min", 30)) }
    var showTimerDurationSheet by remember { mutableStateOf(false) }
    var pendingTimerMinutes by remember { mutableIntStateOf(freeTimerMinutes) }
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
    val canEditTimerDuration = !isRunning && boundService?.isPaused != true
    val isSessionActive = isRunning || boundService?.isPaused == true

    fun triggerServiceAction(action: String, durationMinutes: Int = -1) {
        val intent = Intent(context, PomodoroService::class.java).apply {
            this.action = action
            putExtra(PomodoroService.EXTRA_TASK_ID, selectedTaskId)
            putExtra(PomodoroService.EXTRA_TASK_TITLE, activeFocusTask?.title)
            putExtra(PomodoroService.EXTRA_TASK_CATEGORY, activeFocusTask?.category)
            putExtra(PomodoroService.EXTRA_TASK_CATEGORY_COLOR, activeFocusTask?.categoryColor)
            putExtra(PomodoroService.EXTRA_MODE, mode)
            putExtra(PomodoroService.EXTRA_FOCUS_TYPE, focusType)
            if (durationMinutes > 0) {
                putExtra(PomodoroService.EXTRA_DURATION_MINUTES, durationMinutes)
            }
        }
        context.startForegroundService(intent)
    }

    fun durationSecondsFor(type: String, phase: String = mode): Int = when (type) {
        PomodoroService.FOCUS_TYPE_TIMER -> freeTimerMinutes * 60
        PomodoroService.FOCUS_TYPE_STOPWATCH -> 0
        else -> pomodoroDurationSecondsFor(phase, prefs)
    }

    fun selectedDurationSeconds(): Int = durationSecondsFor(focusType)

    fun selectFocusType(type: String) {
        if (focusType != type) {
            focusType = type
            mode = "work"
            timeLeft = durationSecondsFor(type, "work")
            // Do not use ACTION_STOP here.  The old approach could destroy the service while
            // this screen still held its binder, leaving the previous mode selected after a
            // reset or completion.
            boundService?.configureFocusType(type, freeTimerMinutes)
        }
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
            if (!boundService.isRunning && boundService.focusType == PomodoroService.FOCUS_TYPE_POMODORO) {
                boundService.updateModeAndDuration(boundService.currentMode)
            }
            timeLeft = (boundService.timeLeftMs / 1000).toInt()
            isRunning = boundService.isRunning
            mode = boundService.currentMode
            focusType = boundService.focusType
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
                if (boundService.focusType != PomodoroService.FOCUS_TYPE_POMODORO) {
                    Toast.makeText(context, "集中記録を保存しました。", Toast.LENGTH_LONG).show()
                    timeLeft = 0
                    isRunning = false
                } else {
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
            timeLeft = selectedDurationSeconds()
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
        FocusModeTabs(
            selectedType = focusType,
            enabled = !isRunning && boundService?.isPaused != true,
            onSelect = ::selectFocusType,
        )

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
                val modeColor = when (focusType) {
                    PomodoroService.FOCUS_TYPE_TIMER -> MaterialTheme.colorScheme.secondary
                    PomodoroService.FOCUS_TYPE_STOPWATCH -> MaterialTheme.colorScheme.tertiary
                    else -> when (mode) {
                        "work" -> MaterialTheme.colorScheme.primary
                        "shortBreak" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 方式ごとに内容は異なっても、タイマー枠のリズムは一定に保つ。
                    Box(
                        modifier = Modifier.height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (focusType == PomodoroService.FOCUS_TYPE_POMODORO) {
                            PomodoroPhaseChip(
                                mode = mode,
                                enabled = !isRunning && boundService?.isPaused != true,
                                onClick = { showPomodoroPhaseSheet = true },
                            )
                        } else {
                            Text(
                                text = if (focusType == PomodoroService.FOCUS_TYPE_TIMER) "指定時間の集中" else "自由計測",
                                style = MaterialTheme.typography.labelLarge,
                                color = modeColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    val minutes = timeLeft / 60
                    val seconds = timeLeft % 60
                    val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                    val configuredDurationSeconds = selectedDurationSeconds()
                    val progress = if (configuredDurationSeconds > 0) {
                        ((configuredDurationSeconds - timeLeft).toFloat() / configuredDurationSeconds)
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    Box(
                        modifier = Modifier.size(216.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (focusType == PomodoroService.FOCUS_TYPE_STOPWATCH && isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(216.dp),
                                color = modeColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeWidth = 10.dp,
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(216.dp),
                                color = modeColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeWidth = 10.dp,
                            )
                        }
                        Text(
                            text = timeStr,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (focusType == PomodoroService.FOCUS_TYPE_TIMER && canEditTimerDuration) modeColor else MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-1).sp,
                            modifier = Modifier
                                .clickable(
                                    enabled = focusType == PomodoroService.FOCUS_TYPE_TIMER && canEditTimerDuration,
                                ) {
                                    pendingTimerMinutes = freeTimerMinutes
                                    showTimerDurationSheet = true
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

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
                                timeLeft = selectedDurationSeconds()
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
                        
                        Button(
                            onClick = {
                                if (isRunning) {
                                    triggerServiceAction(PomodoroService.ACTION_PAUSE)
                                } else {
                                    val durationMinutes = if (focusType == PomodoroService.FOCUS_TYPE_STOPWATCH) {
                                        -1
                                    } else {
                                        selectedDurationSeconds() / 60
                                    }
                                    triggerServiceAction(PomodoroService.ACTION_START_OR_RESUME, durationMinutes)
                                    if ((focusType != PomodoroService.FOCUS_TYPE_POMODORO || mode == "work") && activeFocusTask != null) {
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
                                .height(52.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = modeColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "一時停止" else "開始",
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRunning) "一時停止" else "開始", fontWeight = FontWeight.Bold)
                        }

                        // 開発者モード用: 強制完了ボタン
                        if (devModeEnabled && devCompleteButtonEnabled && focusType == PomodoroService.FOCUS_TYPE_POMODORO && mode == "work") {
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

                    Text(
                        text = when (focusType) {
                            PomodoroService.FOCUS_TYPE_POMODORO -> "集中と休憩を自動で切り替えます"
                            PomodoroService.FOCUS_TYPE_STOPWATCH -> "経過時間を自由に記録します"
                            else -> "指定した時間を集中ログに記録します"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!isSessionActive) {
            FocusTaskSelector(
                selectedTask = activeFocusTask,
                availableTasks = uncompletedTasks,
                onSelectTask = { task ->
                    selectedTaskId = task?.id
                    boundService?.updateFocusedTask(
                        taskId = task?.id,
                        taskTitle = task?.title,
                        category = task?.category,
                        categoryColor = task?.categoryColor,
                    )
                },
            )
        }

        // 5. フォーカス中のタスクパネルカード
        if (isSessionActive) activeFocusTask?.let { task ->
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

    if (showPomodoroPhaseSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPomodoroPhaseSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("フェーズを変更", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("終了後は、設定されたポモドーロサイクルに従って自動で次のフェーズへ進みます。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    Triple("work", "集中", "${pomodoroDurationSecondsFor("work", prefs) / 60}分"),
                    Triple("shortBreak", "短い休憩", "${pomodoroDurationSecondsFor("shortBreak", prefs) / 60}分"),
                    Triple("longBreak", "長い休憩", "${pomodoroDurationSecondsFor("longBreak", prefs) / 60}分"),
                ).forEach { (targetMode, label, duration) ->
                    val selected = mode == targetMode
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            mode = targetMode
                            timeLeft = pomodoroDurationSecondsFor(targetMode, prefs)
                            triggerServiceAction(PomodoroService.ACTION_STOP)
                            showPomodoroPhaseSheet = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (selected) "選択中" else duration, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showTimerDurationSheet) {
        val durationOptions = remember { (5..180 step 5).toList() }
        val durationWheelState = rememberLazyListState()
        LaunchedEffect(showTimerDurationSheet) {
            if (showTimerDurationSheet) {
                durationWheelState.scrollToItem(
                    (durationOptions.indexOf(pendingTimerMinutes) - 2).coerceAtLeast(0),
                )
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showTimerDurationSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "時間を設定",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "${pendingTimerMinutes}分",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )

                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    state = durationWheelState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(durationOptions) { minutes ->
                        val isSelected = minutes == pendingTimerMinutes
                        Text(
                            text = "${minutes}分",
                            style = if (isSelected) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingTimerMinutes = minutes }
                                .padding(vertical = 10.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        freeTimerMinutes = pendingTimerMinutes
                        prefs.edit { putInt("free_timer_duration_min", freeTimerMinutes) }
                        timeLeft = selectedDurationSeconds()
                        showTimerDurationSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("設定する", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun PomodoroPhaseChip(
    mode: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (label, color) = when (mode) {
        "shortBreak" -> "短い休憩" to MaterialTheme.colorScheme.secondary
        "longBreak" -> "長い休憩" to MaterialTheme.colorScheme.tertiary
        else -> "集中" to MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (enabled) "フェーズを変更" else "集中中はフェーズを変更できません",
                modifier = Modifier.padding(start = 4.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun FocusModeTabs(
    selectedType: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("集中方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!enabled) Text("実行中は変更できません", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                PomodoroService.FOCUS_TYPE_POMODORO to "ポモドーロ",
                PomodoroService.FOCUS_TYPE_TIMER to "タイマー",
                PomodoroService.FOCUS_TYPE_STOPWATCH to "計測",
            ).forEach { (type, label) ->
                val selected = type == selectedType
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable(enabled = enabled) { onSelect(type) }
                        .padding(vertical = 11.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FocusTaskSelector(
    selectedTask: TaskModel?,
    availableTasks: List<TaskModel>,
    onSelectTask: (TaskModel?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("集中するタスク", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedTask?.title ?: "タスクを選択",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedTask == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    selectedTask?.category?.ifBlank { "一般作業として記録します" } ?: "選ばなくても集中を開始できます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("変更", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("集中するタスク", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = {
                        onSelectTask(null)
                        showPicker = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("タスクを選択しない（一般作業）")
                }
                LazyColumn(modifier = Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableTasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTask(task)
                                    showPicker = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (task.id == selectedTask?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(task.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(task.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
