package com.notiontasks.app.ui.screens

import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.components.getNotionCategoryColors
import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduleScreen(
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val timeBlocks by viewModel.timeBlocks.collectAsState()
    val lifeActivities by viewModel.lifeActivities.collectAsState()
    val tasksState by viewModel.tasksState.collectAsState()
    val statusOptions by viewModel.statusOptions.collectAsState()

    val unstartedStatus = remember(statusOptions) { statusOptions.getOrNull(0)?.name ?: "未着手" }
    val inProgressStatus = remember(statusOptions) { statusOptions.getOrNull(1)?.name ?: "進行中" }

    // 日付セレクターの状態
    val calendar = remember { Calendar.getInstance() }
    var selectedDateStr by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time))
    }

    LaunchedEffect(selectedDateStr) {
        viewModel.autoInitializeDefaultLifeActivities(context, selectedDateStr)
    }
    var displayName by remember(selectedDateStr) {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateStr) ?: Date()
        mutableStateOf(SimpleDateFormat("yyyy年MM月dd日 (E)", Locale.JAPAN).format(parsed))
    }

    // 選択された日付の有効なタスクをロードする
    val todayTasks = remember(tasksState, selectedDateStr, statusOptions, unstartedStatus, inProgressStatus) {
        when (val state = tasksState) {
            is TasksUiState.Success -> {
                state.tasks.filter { task ->
                    val isActive = task.status == unstartedStatus || task.status == inProgressStatus
                    task.scheduledDate == selectedDateStr ||
                    (isActive && (task.scheduledDate != null && task.scheduledDate < selectedDateStr)) ||
                    (isActive && (task.dueDate != null && task.dueDate < selectedDateStr))
                }
                .sortedWith(
                    compareBy<TaskModel> { task ->
                        val isOverdueDue = task.dueDate != null && task.dueDate < selectedDateStr
                        val isOverdueScheduled = task.scheduledDate != null && task.scheduledDate < selectedDateStr
                        when {
                            isOverdueDue -> 0
                            isOverdueScheduled -> 1
                            else -> 2
                        }
                    }
                    .thenBy(nullsLast(naturalOrder())) { it.dueDate }
                    .thenBy(nullsLast(naturalOrder())) { it.scheduledDate }
                    .thenBy { it.id }
                )
            }
            else -> emptyList()
        }
    }

    // 選択された日付のタイムブロックをフィルタリングする
    val dayBlocks = remember(timeBlocks, selectedDateStr) {
        timeBlocks.filter { it.date == selectedDateStr }.sortedBy { it.startTime }
    }

    // ブロックの作成/編集用のダイアログ状態
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var selectedPresetTask by remember { mutableStateOf<TaskModel?>(null) }
    var selectedPresetActivity by remember { mutableStateOf<LifeActivity?>(null) }
    var clickedTimeMinutes by remember { mutableStateOf<Int?>(null) }

    // 有効（未着手・進行中）なタスク一覧を取得
    val activeTasks = remember(tasksState, unstartedStatus, inProgressStatus, editingBlock) {
        val allTasks = when (val state = tasksState) {
            is TasksUiState.Success -> state.tasks
            else -> emptyList()
        }
        val baseList = allTasks.filter { it.status == unstartedStatus || it.status == inProgressStatus }.toMutableList()
        
        // 編集中のタイムブロックがあり、紐付けられているタスクが baseList にない場合、追加して選択可能にする
        val currentAssociatedId = editingBlock?.associatedId
        if (currentAssociatedId != null && baseList.none { it.id == currentAssociatedId }) {
            allTasks.find { it.id == currentAssociatedId }?.let {
                baseList.add(it)
            }
        }
        baseList
    }

    // フローティング/トレイのタブ選択 ("tasks" または "life")
    var trayTab by remember { mutableStateOf("tasks") }

    // ドラッグ＆ドロップの状態
    var draggedTask by remember { mutableStateOf<TaskModel?>(null) }
    var draggedActivity by remember { mutableStateOf<LifeActivity?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartScreenPos by remember { mutableStateOf(Offset.Zero) }
    var timetableBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current

    // ボトムトレイ UI 用のトレイ拡張可能状態 (Approach C)
    var isTrayExpanded by remember { mutableStateOf(false) }
    val trayHeight by animateDpAsState(
        targetValue = if (isTrayExpanded && !isDragging) 380.dp else 72.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TrayHeight"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 毎日の日付ナビゲーションヘッダー
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateStr) ?: Date()
                    calendar.time = parsed
                    calendar.add(Calendar.DATE, -1)
                    selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                }) {
                    Icon(Icons.Default.ChevronLeft, "前日")
                }

                val parsedDateForPicker = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateStr) ?: Date()
                val dateCalendar = Calendar.getInstance().apply { time = parsedDateForPicker }

                Row(
                    modifier = Modifier
                        .clickable {
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(newCal.time)
                                },
                                dateCalendar.get(Calendar.YEAR),
                                dateCalendar.get(Calendar.MONTH),
                                dateCalendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "日付選択",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateStr) ?: Date()
                    calendar.time = parsed
                    calendar.add(Calendar.DATE, 1)
                    selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                }) {
                    Icon(Icons.Default.ChevronRight, "翌日")
                }
            }
        }

        // メインレイアウト：ボトム拡張可能トレイを備えたタイムテーブル (Approach C)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 24時間表示のスクロール可能なスケジュール列（全幅）
            val scrollState = rememberScrollState()
            
            // 初期ロード時に午前7時に自動スクロールして、使い心地を良くする
            LaunchedEffect(Unit) {
                scrollState.scrollTo(420) // 7 * 60 = 420dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { layoutCoordinates ->
                        timetableBounds = layoutCoordinates.boundsInWindow()
                    }
            ) {
                // 背景のグリッド線 (1分あたり 1.dp -> 合計の高さ = 1440.dp)
                // ボトムトレイでタイムテーブルが隠れないように、下部に余分な高さ (80.dp) を追加
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1520.dp)
                ) {
                    for (hour in 0..23) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                        ) {
                            // 時間線（実線）
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                                thickness = 1.dp,
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                            
                            // 15分刻みの補助線（細かい破線/透明な線）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(15.dp)
                                    .align(Alignment.TopStart)
                                    .clickable {
                                        clickedTimeMinutes = hour * 60
                                        editingBlock = null
                                        selectedPresetTask = null
                                        selectedPresetActivity = null
                                        showAddDialog = true
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(15.dp)
                                    .offset(y = 15.dp)
                                    .clickable {
                                        clickedTimeMinutes = hour * 60 + 15
                                        editingBlock = null
                                        selectedPresetTask = null
                                        selectedPresetActivity = null
                                        showAddDialog = true
                                    }
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                            }
                            
                            // 30分刻みの線（中程度の線）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(15.dp)
                                    .offset(y = 30.dp)
                                    .clickable {
                                        clickedTimeMinutes = hour * 60 + 30
                                        editingBlock = null
                                        selectedPresetTask = null
                                        selectedPresetActivity = null
                                        showAddDialog = true
                                    }
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    thickness = 0.75.dp,
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(15.dp)
                                    .offset(y = 45.dp)
                                    .clickable {
                                        clickedTimeMinutes = hour * 60 + 45
                                        editingBlock = null
                                        selectedPresetTask = null
                                        selectedPresetActivity = null
                                        showAddDialog = true
                                    }
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                            }

                            // 左マージンの時間ラベル（幅: 56.dp）
                            Text(
                                text = String.format(Locale.US, "%02d:00", hour),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .padding(start = 8.dp, top = 2.dp)
                                    .width(48.dp)
                            )
                        }
                    }
                    // スクロールビューの下部にあるスペーサー。コンテンツが 72dp の折りたたまれたトレイの後ろに隠れないようにします。
                    Spacer(modifier = Modifier.height(80.dp))
                }

                // ラベルとスケジュールボックスを分ける垂直タイムラインの境界線
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .offset(x = 56.dp)
                )

                // フローティングスケジュールブロックアイテムをレンダリングする
                dayBlocks.forEach { block ->
                    val blockColor = try {
                        Color(block.color.toColorInt())
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.primaryContainer
                    }

                    Card(
                        modifier = Modifier
                            .padding(start = 64.dp, end = 8.dp)
                            .offset(y = block.startTime.dp)
                            .height((block.endTime - block.startTime).dp)
                            .fillMaxWidth()
                            .clickable {
                                editingBlock = block
                                clickedTimeMinutes = block.startTime
                                selectedPresetTask = null
                                selectedPresetActivity = null
                                showAddDialog = true
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = blockColor.copy(alpha = 0.85f),
                            contentColor = if (blockColor.luminance() > 0.5f) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, blockColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = block.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (block.endTime - block.startTime >= 30) {
                                Text(
                                    text = "${formatMinutes(block.startTime)}〜${formatMinutes(block.endTime)}",
                                    fontSize = 10.sp,
                                    color = if (blockColor.luminance() > 0.5f) Color.DarkGray else Color.LightGray
                                )
                            }
                        }
                    }
                }
            }

            // ボトム拡張可能トレイパネル (Approach C)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(trayHeight),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 12.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ヘッダーハンドル行（常に表示、高さ: 72.dp）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clickable { isTrayExpanded = !isTrayExpanded }
                            .padding(top = 8.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 小さなハンドルノブの視覚的インジケーター
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(100.dp)
                                )
                                .align(Alignment.TopCenter)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .align(Alignment.Center),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (trayTab == "tasks") Icons.AutoMirrored.Filled.Assignment else Icons.AutoMirrored.Filled.DirectionsRun,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (trayTab == "tasks") "今日やるべきこと" else "生活習慣プリセット",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (trayTab == "tasks") {
                                            "未割当のタスク: ${todayTasks.size}件"
                                        } else {
                                            "登録可能な習慣: ${lifeActivities.size}件"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // タップインジケーターの矢印
                                Icon(
                                    imageVector = if (isTrayExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isTrayExpanded) "折りたたむ" else "展開する",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // コンテンツエリア（展開時のみ表示、または完全にインタラクティブ）
                    if (isTrayExpanded) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 1.dp
                        )

                        // トレイタブ（セグメントコントロールスタイル）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(
                                        if (trayTab == "tasks") MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { trayTab = "tasks" },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                                        contentDescription = null,
                                        tint = if (trayTab == "tasks") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "今日やるべきこと",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (trayTab == "tasks") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(
                                        if (trayTab == "life") MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { trayTab = "life" },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                        contentDescription = null,
                                        tint = if (trayTab == "life") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "生活習慣",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (trayTab == "life") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // トレイリストのコンテンツ（展開時）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        ) {
                            if (trayTab == "tasks") {
                                if (todayTasks.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircleOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = "今日のタスクはすべて登録済みです！",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Text(
                                                text = "すべてのタスクが時間割に組み込まれました。\n素晴らしい1日を過ごしましょう！",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        todayTasks.forEach { task ->
                                            var cardScreenPos by remember { mutableStateOf(Offset.Zero) }
                                            Card(
                                                modifier = Modifier
                                                    .onGloballyPositioned { layoutCoordinates ->
                                                        if (!isDragging) {
                                                            cardScreenPos = layoutCoordinates.positionInWindow()
                                                        }
                                                    }
                                                    .pointerInput(task) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = { _ ->
                                                                draggedTask = task
                                                                draggedActivity = null
                                                                isDragging = true
                                                                dragOffset = Offset.Zero
                                                                dragStartScreenPos = cardScreenPos
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragOffset += dragAmount
                                                            },
                                                            onDragEnd = {
                                                                timetableBounds?.let { bounds ->
                                                                    val dropX = dragStartScreenPos.x + dragOffset.x
                                                                    val dropY = dragStartScreenPos.y + dragOffset.y
                                                                    val isInside = dropX >= bounds.left && dropX <= bounds.right &&
                                                                                   dropY >= bounds.top && dropY <= bounds.bottom
                                                                    if (isInside) {
                                                                        val relativeYPx = dropY - bounds.top
                                                                        val totalYPx = relativeYPx + scrollState.value
                                                                        val totalYDp = with(density) { totalYPx.toDp().value }
                                                                        var minutes = totalYDp.toInt()
                                                                        minutes = (minutes / 15) * 15
                                                                        if (minutes < 0) minutes = 0
                                                                        if (minutes > 1425) minutes = 1425
                                                                        
                                                                        clickedTimeMinutes = minutes
                                                                        selectedPresetTask = task
                                                                        selectedPresetActivity = null
                                                                        editingBlock = null
                                                                        showAddDialog = true
                                                                    }
                                                                }
                                                                isDragging = false
                                                                draggedTask = null
                                                            },
                                                            onDragCancel = {
                                                                isDragging = false
                                                                draggedTask = null
                                                            }
                                                        )
                                                    }
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedPresetTask = task
                                                        selectedPresetActivity = null
                                                        clickedTimeMinutes = null
                                                        editingBlock = null
                                                        showAddDialog = true
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (task.categoryColor != null) {
                                                        getNotionCategoryColors(task.categoryColor, isSystemInDarkTheme()).first
                                                    } else {
                                                        getNotionCategoryColors("default", isSystemInDarkTheme()).first
                                                    }
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (task.categoryColor != null) {
                                                        getNotionCategoryColors(task.categoryColor, isSystemInDarkTheme()).second.copy(alpha = 0.2f)
                                                    } else {
                                                        getNotionCategoryColors("default", isSystemInDarkTheme()).second.copy(alpha = 0.2f)
                                                    }
                                                ),
                                                elevation = CardDefaults.cardElevation(2.dp)
                                            ) {
                                                val isSystemDark = isSystemInDarkTheme()
                                                val categoryColors = if (task.categoryColor != null) {
                                                    getNotionCategoryColors(task.categoryColor, isSystemDark)
                                                } else {
                                                    getNotionCategoryColors("default", isSystemDark)
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        // タスクカテゴリのアクセントバー
                                                        Box(
                                                            modifier = Modifier
                                                                .width(4.dp)
                                                                .height(36.dp)
                                                                .background(
                                                                    color = categoryColors.second,
                                                                    shape = RoundedCornerShape(2.dp)
                                                                )
                                                        )

                                                        Column(
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                text = task.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = task.category,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = categoryColors.second,
                                                                    fontWeight = FontWeight.Medium
                                                                )

                                                                val isOverdueDue = task.dueDate != null && task.dueDate < selectedDateStr
                                                                val isOverdueScheduled = task.scheduledDate != null && task.scheduledDate < selectedDateStr

                                                                if (isOverdueDue) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(
                                                                                color = Color(0xFFFFEBEE),
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            )
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            )
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                        }
                                                    }


                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 生活習慣プリセットリスト
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        lifeActivities.forEach { activity ->
                                            var cardScreenPos by remember { mutableStateOf(Offset.Zero) }
                                            val colorParsed = try {
                                                Color(activity.color.toColorInt())
                                            } catch (_: Exception) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .onGloballyPositioned { layoutCoordinates ->
                                                        if (!isDragging) {
                                                            cardScreenPos = layoutCoordinates.positionInWindow()
                                                        }
                                                    }
                                                    .pointerInput(activity) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = { _ ->
                                                                draggedActivity = activity
                                                                draggedTask = null
                                                                isDragging = true
                                                                dragOffset = Offset.Zero
                                                                dragStartScreenPos = cardScreenPos
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragOffset += dragAmount
                                                            },
                                                            onDragEnd = {
                                                                timetableBounds?.let { bounds ->
                                                                    val dropX = dragStartScreenPos.x + dragOffset.x
                                                                    val dropY = dragStartScreenPos.y + dragOffset.y
                                                                    val isInside = dropX >= bounds.left && dropX <= bounds.right &&
                                                                                   dropY >= bounds.top && dropY <= bounds.bottom
                                                                    if (isInside) {
                                                                        val relativeYPx = dropY - bounds.top
                                                                        val totalYPx = relativeYPx + scrollState.value
                                                                        val totalYDp = with(density) { totalYPx.toDp().value }
                                                                        var minutes = totalYDp.toInt()
                                                                        minutes = (minutes / 15) * 15
                                                                        if (minutes < 0) minutes = 0
                                                                        if (minutes > 1425) minutes = 1425
                                                                        
                                                                        clickedTimeMinutes = minutes
                                                                        selectedPresetActivity = activity
                                                                        selectedPresetTask = null
                                                                        editingBlock = null
                                                                        showAddDialog = true
                                                                    }
                                                                }
                                                                isDragging = false
                                                                draggedActivity = null
                                                            },
                                                            onDragCancel = {
                                                                isDragging = false
                                                                draggedActivity = null
                                                            }
                                                        )
                                                    }
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedPresetActivity = activity
                                                        selectedPresetTask = null
                                                        clickedTimeMinutes = null
                                                        editingBlock = null
                                                        showAddDialog = true
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = colorParsed.copy(alpha = 0.08f)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, colorParsed.copy(alpha = 0.2f))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        // 習慣固有のカスタムカラーバー
                                                        Box(
                                                            modifier = Modifier
                                                                .width(4.dp)
                                                                .height(36.dp)
                                                                .background(
                                                                    color = colorParsed,
                                                                    shape = RoundedCornerShape(2.dp)
                                                                )
                                                        )

                                                        Column {
                                                            Text(
                                                                text = activity.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.AccessTime,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Text(
                                                                    text = "${activity.durationMinutes}分",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
                        }
                    }
                }
            }

            // 3. フローティングドラッグゴーストオーバーレイカード (Approach C の洗練)
            if (isDragging && (draggedTask != null || draggedActivity != null)) {
                timetableBounds?.let { bounds ->
                    val relativeX = dragStartScreenPos.x + dragOffset.x - bounds.left
                    val relativeY = dragStartScreenPos.y + dragOffset.y - bounds.top
                    
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = relativeX
                                translationY = relativeY
                                scaleX = 1.05f
                                scaleY = 1.05f
                                alpha = 0.85f
                            }
                            .width(240.dp)
                    ) {
                        if (draggedTask != null) {
                            val isSystemDark = isSystemInDarkTheme()
                            val categoryColors = if (draggedTask!!.categoryColor != null) {
                                getNotionCategoryColors(draggedTask!!.categoryColor, isSystemDark)
                            } else {
                                getNotionCategoryColors("default", isSystemDark)
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = categoryColors.first
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, categoryColors.second.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(24.dp)
                                            .background(
                                                color = categoryColors.second,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Column {
                                        Text(
                                            text = draggedTask!!.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = draggedTask!!.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = categoryColors.second,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else if (draggedActivity != null) {
                            val colorParsed = try {
                                Color(draggedActivity!!.color.toColorInt())
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = colorParsed.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colorParsed.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(24.dp)
                                            .background(
                                                color = colorParsed,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Column {
                                        Text(
                                            text = draggedActivity!!.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${draggedActivity!!.durationMinutes}分",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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

    // --- ダイアログ ---

    // 1. スケジュールブロックの追加/編集ダイアログ
    if (showAddDialog) {
        var blockTitle by remember {
            mutableStateOf(
                editingBlock?.title 
                ?: selectedPresetTask?.title 
                ?: selectedPresetActivity?.name 
                ?: ""
            )
        }
        var blockType by remember {
            mutableStateOf(
                editingBlock?.type 
                ?: if (selectedPresetTask != null) "task" else "life"
            )
        }
        var associatedId by remember {
            mutableStateOf(
                editingBlock?.associatedId 
                ?: selectedPresetTask?.id 
                ?: selectedPresetActivity?.id 
                ?: ""
            )
        }
        var blockColor by remember {
            mutableStateOf(
                editingBlock?.color 
                ?: notionColorToHex(selectedPresetTask?.categoryColor)
                ?: selectedPresetActivity?.color 
                ?: "#2196F3"
            )
        }

        // Clamp times to 15 minute snaps
        val defaultStart = clickedTimeMinutes ?: editingBlock?.startTime ?: 480 // 8:00
        val defaultDuration = selectedPresetActivity?.durationMinutes ?: (editingBlock?.let { it.endTime - it.startTime } ?: 60)
        
        var startHour by remember { mutableIntStateOf(defaultStart / 60) }
        var startMin by remember { mutableIntStateOf((defaultStart % 60 / 15) * 15) }
        var endHour by remember { mutableIntStateOf((defaultStart + defaultDuration) / 60) }
        var endMin by remember { mutableIntStateOf(((defaultStart + defaultDuration) % 60 / 15) * 15) }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingBlock = null
            },
            title = {
                Text(
                    text = if (editingBlock != null) "予定を変更" else "予定を登録",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // タイプインジケーター
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "予定の種類:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ElevatedFilterChip(
                                selected = blockType == "task",
                                onClick = { blockType = "task" },
                                label = { Text("タスク") }
                            )
                            ElevatedFilterChip(
                                selected = blockType == "life",
                                onClick = { blockType = "life" },
                                label = { Text("生活・習慣") }
                            )
                        }
                    }

                    if (blockType == "task") {
                        // タスク選択用のセレクトボックス
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val selectedTask = activeTasks.find { it.id == associatedId }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "紐付けるタスクを選択 (必須):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { dropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedTask?.title ?: "タスクを選択してください",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selectedTask != null) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selectedTask != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .heightIn(max = 280.dp)
                                ) {
                                    if (activeTasks.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("有効なタスクがありません") },
                                            onClick = { dropdownExpanded = false },
                                            enabled = false
                                        )
                                    } else {
                                        activeTasks.forEach { task ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = task.title,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = task.category,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Text(
                                                                text = "• ${task.status}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    associatedId = task.id
                                                    blockTitle = task.title
                                                    val colorHex = notionColorToHex(task.categoryColor)
                                                    if (colorHex != null) {
                                                        blockColor = colorHex
                                                    }
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 予定名の通常入力（生活・習慣などの場合）
                        OutlinedTextField(
                            value = blockTitle,
                            onValueChange = { blockTitle = it },
                            label = { Text("予定名 (必須)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // 開始時間セレクター
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("開始時間:", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                        
                        Button(
                            onClick = {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        startHour = hour
                                        startMin = (minute / 5) * 5
                                        // 終了時間が開始時間以前にならないよう調整
                                        val startTot = startHour * 60 + startMin
                                        val endTot = endHour * 60 + endMin
                                        if (endTot <= startTot) {
                                            val newEnd = startTot + 60
                                            endHour = (newEnd / 60) % 24
                                            endMin = newEnd % 60
                                        }
                                    },
                                    startHour,
                                    startMin,
                                    true
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(String.format(Locale.US, "%02d:%02d", startHour, startMin))
                        }
                    }

                    // 終了時間セレクター
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("終了時間:", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                        
                        Button(
                            onClick = {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        endHour = hour
                                        endMin = (minute / 5) * 5
                                    },
                                    endHour,
                                    endMin,
                                    true
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(String.format(Locale.US, "%02d:%02d", endHour, endMin))
                        }
                    }

                    // 所要時間のクイック設定
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "所要時間のクイック設定:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val durations = listOf(15, 30, 60, 90, 120)
                            durations.forEach { duration ->
                                SuggestionChip(
                                    onClick = {
                                        val startTot = startHour * 60 + startMin
                                        val endTot = startTot + duration
                                        endHour = (endTot / 60) % 24
                                        endMin = endTot % 60
                                    },
                                    label = { Text("${duration}分") }
                                )
                            }
                        }
                    }

                    // カラーインジケーターピッカー
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("カラー:")
                        val colors = listOf("#EF5350", "#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#00BCD4", "#E91E63", "#78909C")
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(c.toColorInt()), RoundedCornerShape(100.dp))
                                    .border(
                                        width = if (blockColor == c) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .clickable { blockColor = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (blockTitle.isBlank()) return@Button
                        
                        val startTot = startHour * 60 + startMin
                        val endTot = endHour * 60 + endMin
                        if (endTot <= startTot) {
                            Toast.makeText(context, "終了時間は開始時間よりも後に設定してください。", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val block = TimeBlock(
                            id = editingBlock?.id ?: UUID.randomUUID().toString(),
                            title = blockTitle,
                            startTime = startTot,
                            endTime = endTot,
                            date = selectedDateStr,
                            type = blockType,
                            associatedId = associatedId.ifBlank { null },
                            color = blockColor
                        )

                        viewModel.addTimeBlock(context, block)
                        editingBlock = null
                        showAddDialog = false
                    },
                    enabled = blockTitle.isNotBlank() && (blockType != "task" || associatedId.isNotBlank())
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingBlock != null) {
                        TextButton(
                            onClick = {
                                viewModel.deleteTimeBlock(context, editingBlock!!.id)
                                editingBlock = null
                                showAddDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("削除")
                        }
                    }
                    TextButton(onClick = {
                        editingBlock = null
                        showAddDialog = false
                    }) {
                        Text("キャンセル")
                    }
                }
            }
        )
    }
}

// 1日の経過分をフォーマットするためのヘルパー (例: 540 -> "09:00")
private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return String.format(Locale.US, "%02d:%02d", h, m)
}

private fun notionColorToHex(colorName: String?): String? {
    return when (colorName?.lowercase()) {
        "gray" -> "#9E9E9E"
        "brown" -> "#8D6E63"
        "orange" -> "#FF9800"
        "yellow" -> "#FFCA28"
        "green" -> "#4CAF50"
        "blue" -> "#2196F3"
        "purple" -> "#9C27B0"
        "pink" -> "#E91E63"
        "red" -> "#EF5350"
        else -> null
    }
}
