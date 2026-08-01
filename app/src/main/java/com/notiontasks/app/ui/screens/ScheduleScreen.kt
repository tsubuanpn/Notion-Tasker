package com.notiontasks.app.ui.screens

import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.BorderStroke
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.ui.components.getNotionCategoryColors
import com.notiontasks.app.ui.theme.*
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
    var selectedDateStr by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(selectedDateStr) {
        viewModel.autoInitializeDefaultLifeActivities(context, selectedDateStr)
    }
    val displayName = remember(selectedDateStr) {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateStr) ?: Date()
        SimpleDateFormat("yyyy年MM月dd日 (E)", Locale.JAPAN).format(parsed)
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

    // タイムブロック自体のドラッグ（移動）用状態
    var draggingBlockId by remember { mutableStateOf<String?>(null) }
    var dragBlockDeltaYPx by remember { mutableFloatStateOf(0f) }
    var dragBlockStartMinutes by remember { mutableIntStateOf(0) }
    var dragBlockDuration by remember { mutableIntStateOf(0) }

    val hourHeightDp = 80.dp
    val hourHeightPx = with(density) { hourHeightDp.toPx() }

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
                    val cal = Calendar.getInstance().apply { 
                        time = parsed
                        add(Calendar.DATE, -1)
                    }
                    selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
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
                    val cal = Calendar.getInstance().apply { 
                        time = parsed
                        add(Calendar.DATE, 1)
                    }
                    selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
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
                scrollState.scrollTo(with(density) { (7 * 80).dp.roundToPx() })
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { layoutCoordinates ->
                        timetableBounds = layoutCoordinates.boundsInWindow()
                    }
            ) {
                // 背景のグリッド線 (1時間あたり 80.dp -> 24時間 = 1920.dp)
                // ボトムトレイでタイムテーブルが隠れないように、下部に余分な高さ (120.dp) を追加
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2040.dp)
                ) {
                    for (hour in 0..23) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(hourHeightDp)
                        ) {
                            // 時間線（実線）
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                                thickness = 1.dp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 56.dp)
                            )
                            
                            // 15分刻みの補助線
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
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
                                    .height(20.dp)
                                    .offset(y = 20.dp)
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
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 56.dp)
                                )
                            }
                            
                            // 30分刻みの線（中程度の線）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .offset(y = 40.dp)
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
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 56.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .offset(y = 60.dp)
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
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 56.dp)
                                )
                            }

                            // 左マージンの時間ラベル（幅: 56.dp, グリッド実線と完全に位置を同期）
                            Text(
                                text = String.format(Locale.US, "%02d:00", hour),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .padding(start = 8.dp)
                                    .width(48.dp)
                            )
                        }
                    }
                    // スクロールビューの下部にあるスペーサー
                    Spacer(modifier = Modifier.height(120.dp))
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
                    val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val blockColors = remember(block.color, isSystemDark) {
                        getColorPairFromHex(block.color, isSystemDark)
                    }
                    val bgColor = blockColors.first
                    val accentColor = blockColors.second

                    val isThisBlockDragging = (draggingBlockId == block.id)
                    val durationMinutes = block.endTime - block.startTime

                    val displayStartMinutes = if (isThisBlockDragging) {
                        val deltaMinutes = ((dragBlockDeltaYPx / hourHeightPx) * 60).toInt()
                        val rawMinutes = dragBlockStartMinutes + deltaMinutes
                        val snapped = ((rawMinutes + 7) / 15) * 15
                        snapped.coerceIn(0, 1440 - dragBlockDuration)
                    } else {
                        block.startTime
                    }

                    val displayEndMinutes = if (isThisBlockDragging) {
                        displayStartMinutes + dragBlockDuration
                    } else {
                        block.endTime
                    }

                    val topDp = 80.dp * (displayStartMinutes / 60f)
                    val blockHeightDp = 80.dp * ((displayEndMinutes - displayStartMinutes) / 60f)

                    Card(
                        modifier = Modifier
                            .padding(start = 64.dp, end = 12.dp)
                            .offset(y = topDp)
                            .height(blockHeightDp)
                            .fillMaxWidth()
                            .pointerInput(block) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { _ ->
                                        draggingBlockId = block.id
                                        dragBlockStartMinutes = block.startTime
                                        dragBlockDuration = block.endTime - block.startTime
                                        dragBlockDeltaYPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragBlockDeltaYPx += dragAmount.y
                                    },
                                    onDragEnd = {
                                        draggingBlockId?.let { _ ->
                                            val deltaMinutes = ((dragBlockDeltaYPx / hourHeightPx) * 60).toInt()
                                            val rawMinutes = dragBlockStartMinutes + deltaMinutes
                                            var newStart = ((rawMinutes + 7) / 15) * 15
                                            newStart = newStart.coerceIn(0, 1440 - dragBlockDuration)
                                            val newEnd = newStart + dragBlockDuration

                                            if (newStart != block.startTime || newEnd != block.endTime) {
                                                val updated = block.copy(startTime = newStart, endTime = newEnd)
                                                viewModel.addTimeBlock(context, updated)
                                            }
                                        }
                                        draggingBlockId = null
                                    },
                                    onDragCancel = {
                                        draggingBlockId = null
                                    }
                                )
                            }
                            .clickable {
                                if (draggingBlockId == null) {
                                    editingBlock = block
                                    clickedTimeMinutes = block.startTime
                                    selectedPresetTask = null
                                    selectedPresetActivity = null
                                    showAddDialog = true
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = bgColor.copy(alpha = 0.95f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(if (isThisBlockDragging) 8.dp else 0.dp),
                        border = if (isThisBlockDragging) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize()
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // アクセントバー (リストのスタイルに合わせて動的な高さ、角丸)
                            Box(
                                modifier = Modifier
                                    .padding(vertical = if (durationMinutes < 30) 4.dp else 8.dp)
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        color = accentColor,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 2.dp, bottom = 2.dp, end = 10.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = block.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (isThisBlockDragging) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${formatMinutes(displayStartMinutes)}〜${formatMinutes(displayEndMinutes)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (!isThisBlockDragging && durationMinutes >= 20) {
                                    Text(
                                        text = "${formatMinutes(displayStartMinutes)}〜${formatMinutes(displayEndMinutes)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                            val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                                            val categoryColors = remember(task.categoryColor, isSystemDark) {
                                                if (task.categoryColor != null) {
                                                    getNotionCategoryColors(task.categoryColor, isSystemDark)
                                                } else {
                                                    getNotionCategoryColors("default", isSystemDark)
                                                }
                                            }
                                            var cardScreenPos by remember(task.id) { mutableStateOf(Offset.Zero) }

                                            Card(
                                                modifier = Modifier
                                                    .onGloballyPositioned { layoutCoordinates ->
                                                        if (!isDragging) {
                                                            cardScreenPos = layoutCoordinates.positionInWindow()
                                                        }
                                                    }
                                                    .pointerInput(task.id) {
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
                                                                        var minutes = ((totalYDp / 80f) * 60).toInt()
                                                                        minutes = ((minutes + 7) / 15) * 15
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
                                                    containerColor = categoryColors.first
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    categoryColors.second.copy(alpha = 0.3f)
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min)
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    // タスクカテゴリのアクセントバー (Dynamic height)
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(vertical = 4.dp)
                                                            .width(4.dp)
                                                            .fillMaxHeight()
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
                                                                Surface(
                                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "⚠️ 期限切れ",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            } else if (isOverdueScheduled) {
                                                                Surface(
                                                                    color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) WarningOrangeContainerDark else WarningOrangeContainerLight,
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "⚠️ 持ち越し",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) WarningOrangeOnContainerDark else WarningOrangeOnContainerLight,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                            val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                                            var cardScreenPos by remember(activity.id) { mutableStateOf(Offset.Zero) }
                                            val activityColors = remember(activity.color, isSystemDark) {
                                                getColorPairFromHex(activity.color, isSystemDark)
                                            }
                                            val bgColor = activityColors.first
                                            val accentColor = activityColors.second

                                            Card(
                                                modifier = Modifier
                                                    .onGloballyPositioned { layoutCoordinates ->
                                                        if (!isDragging) {
                                                            cardScreenPos = layoutCoordinates.positionInWindow()
                                                        }
                                                    }
                                                    .pointerInput(activity.id) {
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
                                                                        var minutes = ((totalYDp / 80f) * 60).toInt()
                                                                        minutes = ((minutes + 7) / 15) * 15
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
                                                    containerColor = bgColor
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min)
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 習慣固有のカスタムカラーバー (Dynamic height)
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(vertical = 4.dp)
                                                            .width(4.dp)
                                                            .fillMaxHeight()
                                                            .background(
                                                                color = accentColor,
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
                            val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
                                border = BorderStroke(1.dp, categoryColors.second.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(IntrinsicSize.Min)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(vertical = 4.dp)
                                            .width(4.dp)
                                            .fillMaxHeight()
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
                            val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                            val activityColors = remember(draggedActivity!!.color, isSystemDark) {
                                getColorPairFromHex(draggedActivity!!.color, isSystemDark)
                            }
                            val bgColor = activityColors.first
                            val accentColor = activityColors.second

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = bgColor
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(IntrinsicSize.Min)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(vertical = 4.dp)
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(
                                                color = accentColor,
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

        // 時間を 15 分刻みにスナップ（固定）
        val defaultStart = clickedTimeMinutes ?: editingBlock?.startTime ?: 480 // 8:00
        val defaultDuration = selectedPresetActivity?.durationMinutes ?: (editingBlock?.let { it.endTime - it.startTime } ?: 60)
        
        var startHour by remember { mutableIntStateOf(defaultStart / 60) }
        var startMin by remember { mutableIntStateOf(defaultStart % 60) }
        var endHour by remember { mutableIntStateOf((defaultStart + defaultDuration) / 60) }
        var endMin by remember { mutableIntStateOf((defaultStart + defaultDuration) % 60) }

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
                                        startMin = minute
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
                                        endMin = minute
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
                        val colors = listOf("#EF5350", "#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#E91E63", "#78909C")
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

private fun getColorPairFromHex(hex: String, isDark: Boolean): Pair<Color, Color> {
    val colorName = when (hex.uppercase()) {
        "#9E9E9E", "#78909C" -> "gray"
        "#8D6E63" -> "brown"
        "#FF9800" -> "orange"
        "#FFCA28", "#FFEB3B" -> "yellow"
        "#4CAF50" -> "green"
        "#2196F3" -> "blue"
        "#9C27B0" -> "purple"
        "#E91E63" -> "pink"
        "#EF5350", "#F44336" -> "red"
        else -> null
    }
    return if (colorName != null) {
        getNotionCategoryColors(colorName, isDark)
    } else {
        val baseColor = try { Color(hex.toColorInt()) } catch (_: Exception) { Color(0xFF78909C) }
        if (isDark) Pair(baseColor.copy(alpha = 0.2f), baseColor) 
        else Pair(baseColor.copy(alpha = 0.12f), baseColor)
    }
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
