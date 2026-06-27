package com.notiontasks.app.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiontasks.app.PomodoroService
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState

private const val POMODORO_WORK_SEC = 25 * 60
private const val POMODORO_SHORT_BREAK_SEC = 5 * 60
private const val POMODORO_LONG_BREAK_SEC = 15 * 60
private const val POMODOROS_BEFORE_LONG_BREAK = 4

private fun pomodoroDurationSecondsFor(mode: String): Int = when (mode) {
    "work" -> POMODORO_WORK_SEC
    "shortBreak" -> POMODORO_SHORT_BREAK_SEC
    else -> POMODORO_LONG_BREAK_SEC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    viewModel: TaskViewModel,
    statusOptions: List<String>
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val savedCompletedCountDate = remember { prefs.getString("completed_count_date", "") ?: "" }
    val initialCompletedCount = remember(todayStr, savedCompletedCountDate) {
        if (savedCompletedCountDate == todayStr) prefs.getInt("completed_count", 0) else 0
    }
    
    var timeLeft by remember { mutableStateOf(POMODORO_WORK_SEC) }
    var isRunning by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("work") } // "work", "shortBreak", "longBreak"
    var pomodoroCompletedCount by remember { mutableStateOf(initialCompletedCount) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var isInitialSyncDone by remember { mutableStateOf(false) }
    val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"
    val completedStatus = statusOptions.getOrNull(2) ?: "完了"

    LaunchedEffect(todayStr, savedCompletedCountDate) {
        if (savedCompletedCountDate != todayStr) {
            prefs.edit()
                .putInt("completed_count", 0)
                .putString("completed_count_date", todayStr)
                .apply()
        }
    }
    
    val tasksState by viewModel.tasksState.collectAsState()
    val uncompletedTasks = remember(tasksState, completedStatus) {
        when (val state = tasksState) {
            is TasksUiState.Success -> {
                state.tasks.filter { it.status != completedStatus }.sortedWith(
                    compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                        .thenBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.dueDate }
                        .thenBy { it.id }
                )
            }
            else -> emptyList()
        }
    }
    
    val activeFocusTask = remember(selectedTaskId, tasksState) {
        when (val state = tasksState) {
            is TasksUiState.Success -> state.tasks.find { it.id == selectedTaskId }
            else -> null
        }
    }

    // Service binding setup
    var boundService by remember { mutableStateOf<PomodoroService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val pomodoroBinder = binder as? PomodoroService.PomodoroBinder
                boundService = pomodoroBinder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
                isInitialSyncDone = false
            }
        }
    }

    DisposableEffect(context) {
        val intent = Intent(context, PomodoroService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(serviceConnection)
            isInitialSyncDone = false
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    // Sync selected task state to the bound PomodoroService
    LaunchedEffect(selectedTaskId, activeFocusTask, boundService, isInitialSyncDone) {
        if (!isInitialSyncDone) return@LaunchedEffect
        
        boundService?.let { service ->
            val task = activeFocusTask
            // もしタスクが選択されているが、activeFocusTaskがまだロード中の場合は同期を待つ（nullで上書きしないため）
            if (selectedTaskId != null && task == null) {
                return@LaunchedEffect
            }
            
            // サービス側のタスク情報が、現在の選択状態（ID、タイトル、カテゴリなど）と異なる場合にのみ更新する
            val isSame = service.associatedTaskId == selectedTaskId &&
                         service.associatedTaskTitle == task?.title &&
                         service.associatedTaskCategory == task?.category
            
            if (!isSame) {
                service.updateFocusedTask(
                    taskId = selectedTaskId,
                    taskTitle = task?.title,
                    category = task?.category,
                    categoryColor = task?.categoryColor
                )
            }
        }
    }

    // Update state based on service
    var isAlarmPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(boundService) {
        boundService?.let { service ->
            timeLeft = (service.timeLeftMs / 1000).toInt()
            isRunning = service.isRunning
            mode = service.currentMode
            selectedTaskId = service.associatedTaskId
            isInitialSyncDone = true

            isAlarmPlaying = service.isRingtonePlaying

            service.onTickListener = { ms, _ ->
                timeLeft = (ms / 1000).toInt()
            }
            service.onFinishedListener = {
                timeLeft = 0
                isRunning = false
                if (mode == "work") {
                    pomodoroCompletedCount++
                    prefs.edit()
                        .putInt("completed_count", pomodoroCompletedCount)
                        .putString("completed_count_date", todayStr)
                        .apply()

                    if (pomodoroCompletedCount % POMODOROS_BEFORE_LONG_BREAK == 0) {
                        Toast.makeText(context, "集中セッション${POMODOROS_BEFORE_LONG_BREAK}回お疲れさまでした！長めの休憩をとりましょう。", Toast.LENGTH_LONG).show()
                        mode = "longBreak"
                    } else {
                        Toast.makeText(context, "集中セッション完了！素晴らしいです！短い休憩をとりましょう。", Toast.LENGTH_LONG).show()
                        mode = "shortBreak"
                    }
                    timeLeft = pomodoroDurationSecondsFor(mode)
                } else {
                    Toast.makeText(context, "休憩終了！次の集中セッションを始めましょう。", Toast.LENGTH_LONG).show()
                    mode = "work"
                    timeLeft = pomodoroDurationSecondsFor(mode)
                }
            }
            service.onStateChangedListener = { running ->
                isRunning = running
            }
            service.onRingtoneStateChangedListener = { playing ->
                isAlarmPlaying = playing
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 2. Mode Selector Segmented Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF1F1F1),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(
                Triple("work", "集中", Color(0xFFEF5350)),
                Triple("shortBreak", "休憩", Color(0xFF2E7D32)),
                Triple("longBreak", "長い休憩", Color(0xFF1976D2))
            ).forEach { (m, label, activeColor) ->
                val isSelected = mode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) {
                                if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            mode = m
                            timeLeft = pomodoroDurationSecondsFor(m)
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
                            if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color(0xFF616161)
                        }
                    )
                }
            }
        }

        // 3. Timer Display Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF141414) else Color.White
            ),
            border = BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFEEEEEE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Faint large timer icon in background
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = (when (mode) {
                        "work" -> Color(0xFFEF5350)
                        "shortBreak" -> Color(0xFF2E7D32)
                        else -> Color(0xFF1976D2)
                    }).copy(alpha = 0.03f),
                    modifier = Modifier.size(160.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Timer digits text
                    val minutes = timeLeft / 60
                    val seconds = timeLeft % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    
                    Text(
                        text = timeStr,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (isSystemInDarkTheme()) Color.White else Color(0xFF212121),
                        letterSpacing = (-1).sp
                    )

                    // Play / Pause / Reset Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reset Button
                        IconButton(
                            onClick = {
                                triggerServiceAction(PomodoroService.ACTION_STOP)
                                isRunning = false
                                timeLeft = pomodoroDurationSecondsFor(mode)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White,
                                    shape = RoundedCornerShape(50)
                                )
                                .border(
                                    BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF424242) else Color(0xFFE0E0E0)),
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "リセット",
                                tint = if (isSystemInDarkTheme()) Color.White else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Start/Pause Button
                        val modeColor = when (mode) {
                            "work" -> Color(0xFFEF5350)
                            "shortBreak" -> Color(0xFF2E7D32)
                            else -> Color(0xFF1976D2)
                        }
                        
                        IconButton(
                            onClick = {
                                if (isRunning) {
                                    triggerServiceAction(PomodoroService.ACTION_PAUSE)
                                } else {
                                    val durationMinutes = pomodoroDurationSecondsFor(mode) / 60
                                    triggerServiceAction(PomodoroService.ACTION_START_OR_RESUME, durationMinutes)
                                    if (mode == "work" && activeFocusTask != null) {
                                        if (activeFocusTask.status != inProgressStatus && activeFocusTask.status != completedStatus) {
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
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Alarm Stop Button (表示条件: アラーム再生中でタイマーは停止)
                        if (!isRunning && isAlarmPlaying) {
                            IconButton(
                                onClick = {
                                    boundService?.stopRingtonePlayback()
                                    isAlarmPlaying = false
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .border(
                                        BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF424242) else Color(0xFFE0E0E0)),
                                        shape = RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsOff,
                                    contentDescription = "アラーム停止",
                                    tint = if (isSystemInDarkTheme()) Color.White else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Stats Indicator
                    HorizontalDivider(color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFEEEEEE), thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日の完了数:",
                            fontSize = 10.sp,
                            color = if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pomodoroCompletedCount}回",
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFFEF5350),
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
                                            .background(Color(0xFFEF5350), shape = RoundedCornerShape(50))
                                    )
                                }
                                if (pomodoroCompletedCount > 8) {
                                    Text(
                                        text = "+",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSystemInDarkTheme()) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Task Association Section
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
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "集中するタスクを紐付ける",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSystemInDarkTheme()) Color.White else Color(0xFF424242)
                )
            }

            // Custom Simple Dropdown
            var dropdownExpanded by remember { mutableStateOf(false) }
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
                        DropdownMenuItem(
                            text = { Text("[${task.category}] ${task.title}", fontWeight = FontWeight.Medium, fontSize = 12.sp) },
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
                                    if (task.status != inProgressStatus && task.status != completedStatus) {
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

        // 5. Focusing Task Panel Card
        activeFocusTask?.let { task ->
            val badgeBgColor = if (isSystemInDarkTheme()) Color(0xFF3E1F21) else Color(0xFFFFEBEE)
            val badgeTextColor = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)
            val panelBgColor = Color(0xFFEF5350).copy(alpha = 0.05f)
            val panelBorderColor = Color(0xFFEF5350).copy(alpha = 0.15f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = panelBgColor
                ),
                border = BorderStroke(1.dp, panelBorderColor)
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
                        Box(
                            modifier = Modifier
                                .background(badgeBgColor, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "現在フォーカス中",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSystemInDarkTheme()) Color.White else Color(0xFF212121)
                        )
                        Text(
                            text = "分類: ${task.category}  •  状態: ${task.status}",
                            fontSize = 9.sp,
                            color = if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color.Gray
                        )
                    }

                    val buttonBgColor = if (isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                    val buttonIconTint = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF2E7D32)

                    IconButton(
                        onClick = {
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
                            .background(buttonBgColor, shape = RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "完了", tint = buttonIconTint)
                    }
                }
            }
        }
    }
}
