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
import androidx.compose.runtime.MutableState
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.PathEffect
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
import com.notiontasks.app.utils.JapanTime
import com.notiontasks.app.utils.ScheduleExporter
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.*

private const val TIME_SNAP_MINUTES = 5
private const val GRID_SNAP_MINUTES = 15

@Composable
fun ScheduleScreen(
    viewModel: TaskViewModel,
    selectedCalendarDate: MutableState<String?>,
    onOpenCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val timeBlocks by viewModel.timeBlocks.collectAsState()
    val lifeActivities by viewModel.lifeActivities.collectAsState()
    val tasksState by viewModel.tasksState.collectAsState()
    val unstartedStatus by viewModel.statusUnstarted.collectAsState()
    val inProgressStatus by viewModel.statusInProgress.collectAsState()

    // 日付セレクターの状態
    var selectedDateStr by remember {
        mutableStateOf(
            selectedCalendarDate.value
                ?: JapanTime.todayIso(),
        )
    }

    // 現在時刻の状態管理（1分ごとに更新）
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val isToday = remember(selectedDateStr, currentTime) {
        val todayStr = JapanTime.todayIso()
        selectedDateStr == todayStr
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentTime = now
            // 次の00秒まで待機して1分間隔を維持
            val secondsToNextMinute = 60 - now.get(Calendar.SECOND)
            kotlinx.coroutines.delay(secondsToNextMinute * 1000L)
        }
    }

    LaunchedEffect(selectedDateStr) {
        selectedCalendarDate.value = selectedDateStr
        viewModel.autoInitializeDefaultLifeActivities(context, selectedDateStr)
    }
    val displayName = remember(selectedDateStr) {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateStr) ?: Date()
        SimpleDateFormat("yyyy年MM月dd日 (E)", Locale.JAPAN).format(parsed)
    }

    // 選択された日付の有効なタスクをロードする
    val todayTasks = remember(tasksState, selectedDateStr, unstartedStatus, inProgressStatus) {
        val unstartedTrimmed = unstartedStatus.trim()
        val inProgressTrimmed = inProgressStatus.trim()
        when (val state = tasksState) {
            is TasksUiState.Success -> {
                state.tasks.filter { task ->
                    val statusTrimmed = task.status.trim()
                    val isActive = statusTrimmed == unstartedTrimmed || statusTrimmed == inProgressTrimmed
                    JapanTime.projectedDateKey(task.scheduledDate) == selectedDateStr ||
                    (isActive && (JapanTime.projectedDateKey(task.scheduledDate)?.let { it < selectedDateStr } == true)) ||
                    (isActive && (JapanTime.projectedDateKey(task.dueDate)?.let { it < selectedDateStr } == true))
                }
                .sortedWith(
                    compareBy<TaskModel> { task ->
                        val isOverdueDue = JapanTime.projectedDateKey(task.dueDate)?.let { it < selectedDateStr } == true
                        val isOverdueScheduled = JapanTime.projectedDateKey(task.scheduledDate)?.let { it < selectedDateStr } == true
                        when {
                            isOverdueDue -> 0
                            isOverdueScheduled -> 1
                            else -> 2
                        }
                    }
                    .thenBy(nullsLast(naturalOrder())) { JapanTime.projectedDateKey(it.dueDate) }
                    .thenBy(nullsLast(naturalOrder())) { JapanTime.projectedDateKey(it.scheduledDate) }
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
    val unassignedTasks = remember(todayTasks, dayBlocks) {
        val assignedTaskIds = dayBlocks.mapNotNull { it.associatedId }.toSet()
        todayTasks.filterNot { it.id in assignedTaskIds }
    }

    // ブロックの作成/編集用のダイアログ状態
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var deleteConfirmationBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var selectedPresetTask by remember { mutableStateOf<TaskModel?>(null) }
    var selectedPresetActivity by remember { mutableStateOf<LifeActivity?>(null) }
    var clickedTimeMinutes by remember { mutableStateOf<Int?>(null) }

    val allTasks = remember(tasksState) {
        when (val state = tasksState) {
            is TasksUiState.Success -> state.tasks
            else -> emptyList()
        }
    }
    val taskById = remember(allTasks) { allTasks.associateBy { it.id } }

    // 有効（未着手・進行中）なタスク一覧を取得
    val activeTasks = remember(allTasks, unstartedStatus, inProgressStatus, editingBlock) {
        val unstartedTrimmed = unstartedStatus.trim()
        val inProgressTrimmed = inProgressStatus.trim()
        val baseList = allTasks.filter { 
            val st = it.status.trim()
            st == unstartedTrimmed || st == inProgressTrimmed 
        }.toMutableList()
        
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
        targetValue = if (isTrayExpanded && !isDragging) 344.dp else 76.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TrayHeight"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PlanModeTabs(
            calendarSelected = false,
            onOpenCalendar = onOpenCalendar,
            onOpenSchedule = {},
        )

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

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
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
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    IconButton(onClick = {
                        val text = ScheduleExporter.exportToText(displayName, dayBlocks)
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "共有",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
            
            // 初期ロード時または「今日」選択時に現在時刻または午前7時に自動スクロール
            LaunchedEffect(isToday) {
                val scrollTargetMinutes = if (isToday) {
                    val now = Calendar.getInstance()
                    // 現在時刻が画面中央に来るようにオフセット（例：3時間分引く）
                    (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) - 180).coerceAtLeast(0)
                } else {
                    7 * 60
                }
                scrollState.scrollTo(with(density) { (scrollTargetMinutes * (80f / 60f)).dp.roundToPx() })
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
                                        clickedTimeMinutes = hour * 60 + GRID_SNAP_MINUTES
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
                                        clickedTimeMinutes = hour * 60 + (GRID_SNAP_MINUTES * 2)
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
                                        clickedTimeMinutes = hour * 60 + (GRID_SNAP_MINUTES * 3)
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
                    val linkedTask = block.associatedId?.let { taskById[it] }
                    val categoryLabel = linkedTask?.category?.takeIf { it.isNotBlank() }
                        ?: if (block.type == "life") "生活習慣" else "タスク"
                    val categoryColors = remember(linkedTask?.categoryColor, isSystemDark) {
                        getNotionCategoryColors(linkedTask?.categoryColor, isSystemDark)
                    }
                    val bgColor = blockColors.first
                    val accentColor = blockColors.second

                    val isThisBlockDragging = (draggingBlockId == block.id)
                    val durationMinutes = block.endTime - block.startTime

                    val displayStartMinutes = if (isThisBlockDragging) {
                        val deltaMinutes = ((dragBlockDeltaYPx / hourHeightPx) * 60).toInt()
                        val rawMinutes = dragBlockStartMinutes + deltaMinutes
                        val snapped = ((rawMinutes + (TIME_SNAP_MINUTES / 2)) / TIME_SNAP_MINUTES) * TIME_SNAP_MINUTES
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
                                            var newStart = ((rawMinutes + (TIME_SNAP_MINUTES / 2)) / TIME_SNAP_MINUTES) * TIME_SNAP_MINUTES
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
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(if (isThisBlockDragging) 8.dp else 0.dp),
                        border = if (isThisBlockDragging) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 左端の色帯で、スクロール中も予定を追いやすくする。
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(5.dp)
                                    .background(
                                        color = accentColor,
                                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                                    )
                            )
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp, vertical = if (durationMinutes < 30) 5.dp else 10.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = block.title,
                                    fontSize = if (durationMinutes < 30) 13.sp else 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                if (durationMinutes >= 20) {
                                    Text(
                                        text = "${formatMinutes(displayStartMinutes)} – ${formatMinutes(displayEndMinutes)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = accentColor,
                                    )
                                }

                                if (durationMinutes >= 45) {
                                    Spacer(Modifier.height(5.dp))
                                    Surface(
                                        color = categoryColors.first,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = categoryLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = categoryColors.second,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 現在時刻インジケーター（今日の日付の時のみ表示 - スケジュールブロックの上に重ねる）
                if (isToday) {
                    val now = currentTime
                    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                    val indicatorTopDp = 80.dp * (currentMinutes / 60f)
                    val indicatorColor = MaterialTheme.colorScheme.error
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = indicatorTopDp - 4.dp) // ドットの中心を線に合わせる
                    ) {
                        // 左側のドット（不透明）
                        Box(
                            modifier = Modifier
                                .padding(start = 52.dp)
                                .size(8.dp)
                                .background(indicatorColor, RoundedCornerShape(50))
                        )
                        
                        // 現在時刻の線（破線 + 透過）
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(start = 56.dp)
                                .align(Alignment.CenterStart)
                        ) {
                            val strokeWidth = 1.dp.toPx()
                            val dashWidth = 4.dp.toPx()
                            val dashGap = 4.dp.toPx()
                            
                            drawLine(
                                color = indicatorColor.copy(alpha = 0.5f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = strokeWidth,
                                pathEffect = PathEffect.dashPathEffect(
                                    intervals = floatArrayOf(dashWidth, dashGap),
                                    phase = 0f
                                )
                            )
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
                    // ヘッダーハンドル行（常に表示）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
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
                                        text = if (trayTab == "tasks") "未割当タスク ${unassignedTasks.size}件" else "生活習慣 ${lifeActivities.size}件",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (trayTab == "tasks") {
                                            "タップで時間を決める ・ 長押しでドラッグ"
                                        } else {
                                            "タップで時間を決める ・ 長押しでドラッグ"
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
                                        text = "タスク",
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
                                        text = "生活",
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
                                if (unassignedTasks.isEmpty()) {
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
                                                text = "未割当タスクはありません",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Text(
                                                text = "必要なタスクはすべて時間割へ配置されています。",
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
                                        unassignedTasks.forEach { task ->
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
                                                                        minutes = ((minutes + (TIME_SNAP_MINUTES / 2)) / TIME_SNAP_MINUTES) * TIME_SNAP_MINUTES
                                                                        if (minutes < 0) minutes = 0
                                                                        if (minutes > 1440 - 15) minutes = 1440 - 15 // デフォルト15分枠を考慮
                                                                        
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

                                                            val isOverdueDue = JapanTime.projectedDateKey(task.dueDate)?.let { it < selectedDateStr } == true
                                                            val isOverdueScheduled = JapanTime.projectedDateKey(task.scheduledDate)?.let { it < selectedDateStr } == true

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

                                                    IconButton(
                                                        onClick = {
                                                            selectedPresetTask = task
                                                            selectedPresetActivity = null
                                                            clickedTimeMinutes = null
                                                            editingBlock = null
                                                            showAddDialog = true
                                                        },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AddCircle,
                                                            contentDescription = "${task.title}を時間割に追加",
                                                            tint = categoryColors.second,
                                                            modifier = Modifier.size(22.dp)
                                                        )
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
                                                                        minutes = ((minutes + (TIME_SNAP_MINUTES / 2)) / TIME_SNAP_MINUTES) * TIME_SNAP_MINUTES
                                                                        if (minutes < 0) minutes = 0
                                                                        if (minutes > 1440 - activity.durationMinutes) {
                                                                            minutes = 1440 - activity.durationMinutes
                                                                        }
                                                                        
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

        // 選択された時間（スナップ済み）を初期値として使用
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
                                            val newEnd = (startTot + 60).coerceAtMost(1440)
                                            endHour = newEnd / 60
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
                                    endHour.coerceAtMost(23),
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
                        TextButton(
                            onClick = {
                                endHour = 24
                                endMin = 0
                            },
                        ) {
                            Text("24:00にする")
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
                                        val endTot = (startTot + duration).coerceAtMost(1440)
                                        endHour = endTot / 60
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
                                deleteConfirmationBlock = editingBlock
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

    deleteConfirmationBlock?.let { block ->
        AlertDialog(
            onDismissRequest = { deleteConfirmationBlock = null },
            title = { Text("予定を削除しますか？") },
            text = {
                Text(
                    "「${block.title}」\n${block.date} ${String.format(Locale.US, "%02d:%02d", block.startTime / 60, block.startTime % 60)}〜${String.format(Locale.US, "%02d:%02d", block.endTime / 60, block.endTime % 60)}\n\nアプリ内の予定と通知だけを削除します。Notion のタスクは削除されません。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTimeBlock(context, block.id)
                        deleteConfirmationBlock = null
                        editingBlock = null
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationBlock = null }) {
                    Text("キャンセル")
                }
            },
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
