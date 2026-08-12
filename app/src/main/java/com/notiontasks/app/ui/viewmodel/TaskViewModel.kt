package com.notiontasks.app.ui.viewmodel

import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.data.PomodoroLog
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.data.repository.ScheduleRepository
import com.notiontasks.app.data.repository.TaskRepository
import com.notiontasks.app.data.repository.PomodoroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Calendar

// 画面操作のステータス状態
sealed interface TasksUiState {
    object Idle : TasksUiState
    object Loading : TasksUiState
    data class Success(val tasks: List<TaskModel>) : TasksUiState
    data class Error(val message: String) : TasksUiState
}

class TaskViewModel(
    private val repository: TaskRepository,
    private val scheduleRepository: ScheduleRepository,
    private val pomodoroRepository: PomodoroRepository,
    private val sharedPrefs: android.content.SharedPreferences,
) : ViewModel() {

    private val jsonSerializer = Json { ignoreUnknownKeys = true }

    // ポモドーロログの状態 (Room を購読)
    val pomodoroLogs: StateFlow<List<PomodoroLog>> = pomodoroRepository.allLogsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    // スケジュール/タイムブロッキングの状態 (Room SQL を SSOT としてリアクティブに購読)
    val timeBlocks: StateFlow<List<TimeBlock>> = scheduleRepository.allTimeBlocks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val lifeActivities: StateFlow<List<LifeActivity>> = scheduleRepository.allLifeActivities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _initializedDates = MutableStateFlow<Set<String>>(emptySet())

    // 保存された構成値 (MainActivity の暗号化された動的 SharedPrefs から挿入されます)
    private val _notionToken = MutableStateFlow("")
    val notionToken: StateFlow<String> = _notionToken.asStateFlow()

    private val _databaseId = MutableStateFlow("")
    val databaseId: StateFlow<String> = _databaseId.asStateFlow()

    // 統計データの保存期間設定 (0 = 無制限, 1, 3, 6, 12 = ヶ月)
    private val _statsStorageDuration = MutableStateFlow(sharedPrefs.getInt("pomodoro_stats_duration_months", 0))
    val statsStorageDuration: StateFlow<Int> = _statsStorageDuration.asStateFlow()

    // ライブ Room 更新を組み合わせた画面レベルの StateFlow
    val tasksState: StateFlow<TasksUiState> = repository.allTasks
        .map { list ->
            if (list.isEmpty()) {
                TasksUiState.Idle
            } else {
                TasksUiState.Success(list)
            }
        }
        .catch { err -> emit(TasksUiState.Error(err.message ?: "Unknown Database Error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TasksUiState.Loading,
        )

    // 個別のカテゴリフィルタフロー
    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _statusOptions = MutableStateFlow<List<NotionOptionInfo>>(emptyList())
    val statusOptions: StateFlow<List<NotionOptionInfo>> = _statusOptions.asStateFlow()

    private val _categoryOptions = MutableStateFlow<List<NotionOptionInfo>>(emptyList())
    val categoryOptions: StateFlow<List<NotionOptionInfo>> = _categoryOptions.asStateFlow()

    init {
        viewModelScope.launch {
            // SharedPreferences に残っている過去のデータを Room データベースへマイグレーション
            repository.migrateLegacyPreferencesToRoom(sharedPrefs, scheduleRepository)
            ensureDefaultLifeActivities()
            cleanupOldPomodoroLogs()
        }
        loadCredentialsAndMappings()
        loadInitializedDates()
        loadOptions()
    }

    private suspend fun cleanupOldPomodoroLogs() {
        val months = _statsStorageDuration.value
        if (months > 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -months)
            pomodoroRepository.deleteLogsOlderThan(calendar.timeInMillis)
        }
    }

    fun setStatsStorageDuration(months: Int) {
        _statsStorageDuration.value = months
        sharedPrefs.edit { putInt("pomodoro_stats_duration_months", months) }
        viewModelScope.launch {
            cleanupOldPomodoroLogs()
        }
    }

    fun deletePomodoroLog(id: String) {
        viewModelScope.launch {
            pomodoroRepository.deletePomodoroLogById(id)
        }
    }

    fun deletePomodoroLogs(ids: List<String>) {
        viewModelScope.launch {
            pomodoroRepository.deletePomodoroLogsByIds(ids)
        }
    }

    fun deletePomodoroLogsOlderThan(months: Int) {
        viewModelScope.launch {
            if (months == -1) {
                pomodoroRepository.clearAllLogs()
            } else {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -months)
                pomodoroRepository.deleteLogsOlderThan(calendar.timeInMillis)
            }
        }
    }

    private suspend fun ensureDefaultLifeActivities() {
        val current = scheduleRepository.loadLifeActivities()
        if (current.isEmpty()) {
            val defaults = listOf(
                LifeActivity("la_sleep", "睡眠", 480, "#9C27B0", defaultStartTime = 0, defaultEndTime = 420, sortOrder = 0), // 00:00 - 07:00
                LifeActivity("la_meal", "食事", 60, "#FF9800", defaultStartTime = 720, defaultEndTime = 780, sortOrder = 1), // 12:00 - 13:00
                LifeActivity("la_rest", "休憩", 30, "#4CAF50", sortOrder = 2),
                LifeActivity("la_transit", "移動", 30, "#2196F3", sortOrder = 3),
                LifeActivity("la_bath", "お風呂", 30, "#2196F3", defaultStartTime = 1260, defaultEndTime = 1290, sortOrder = 4), // 21:00 - 21:30
                LifeActivity("la_exercise", "運動", 60, "#E91E63", sortOrder = 5),
            )
            scheduleRepository.saveLifeActivities(defaults)
        }
    }

    private fun loadOptions() {
        val catJson = sharedPrefs.getString("category_options_v2", null)
        if (catJson != null) {
            try {
                val list = jsonSerializer.decodeFromString<List<NotionOptionInfo>>(catJson)
                _categoryOptions.value = list
                if (_selectedCategory.value.isEmpty()) {
                    _selectedCategory.value = list.firstOrNull()?.name ?: ""
                }
            } catch (_: Exception) {}
        }

        val statJson = sharedPrefs.getString("status_options_v2", null)
        if (statJson != null) {
            try {
                val list = jsonSerializer.decodeFromString<List<NotionOptionInfo>>(statJson)
                _statusOptions.value = list
            } catch (_: Exception) {}
        }
    }

    fun saveLifeActivities(context: android.content.Context, list: List<LifeActivity>) {
        viewModelScope.launch {
            scheduleRepository.saveLifeActivities(list)
            // 今後初期化される予定の日付（今日を含む）に反映
            syncAllInitializedFutureDates(context)
        }
    }

    fun addLifeActivity(context: android.content.Context, activity: LifeActivity) {
        viewModelScope.launch {
            // 新規追加時はリストの最後に配置
            val current = scheduleRepository.loadLifeActivities()
            val maxOrder = current.maxOfOrNull { it.sortOrder } ?: -1
            val newAct = activity.copy(sortOrder = maxOrder + 1)
            
            scheduleRepository.saveLifeActivity(newAct)
            // 今後初期化される予定の日付（今日を含む）に反映
            syncAllInitializedFutureDates(context)
        }
    }

    fun moveLifeActivity(id: String, direction: Int) {
        // direction: -1 for up, 1 for down
        viewModelScope.launch {
            val current = scheduleRepository.loadLifeActivities().sortedBy { it.sortOrder }
            val index = current.indexOfFirst { it.id == id }
            if (index == -1) return@launch

            val targetIndex = index + direction
            if ((targetIndex < 0) || (targetIndex >= current.size)) return@launch

            val list = current.toMutableList()
            val item = list.removeAt(index)
            list.add(targetIndex, item)

            // sortOrder を振り直し
            val updatedList = list.mapIndexed { i, act -> act.copy(sortOrder = i) }
            scheduleRepository.saveLifeActivities(updatedList)
        }
    }

    fun deleteLifeActivity(context: android.content.Context, id: String) {
        viewModelScope.launch {
            scheduleRepository.deleteLifeActivityById(id)
            
            // 初期化済みの未来（今日含む）のスケジュールから、この習慣を削除
            val today = java.time.LocalDate.now()
            val nowMinutes = java.time.LocalTime.now().let { (it.hour * 60) + it.minute }
            
            // UIキャッシュではなく、DBから全ブロックを直接取得してスキャンする
            val allBlocks = scheduleRepository.getAllTimeBlocks()
            val blocksToDelete = allBlocks.filter { 
                (it.associatedId == id) && (it.type == "life") && 
                (try {
                    val blockDate = java.time.LocalDate.parse(it.date)
                    !blockDate.isBefore(today)
                } catch(_: Exception) { false })
            }

            blocksToDelete.forEach { block ->
                val isToday = (block.date == today.toString())
                if (!isToday || (block.startTime > nowMinutes)) {
                    scheduleRepository.deleteTimeBlockById(block.id)
                    com.notiontasks.app.TaskNotificationReceiver.cancelBlockAlarm(context, block.id)
                }
            }
        }
    }

    private fun loadInitializedDates() {
        val set = sharedPrefs.getStringSet("initialized_dates", emptySet()) ?: emptySet()
        _initializedDates.value = set
    }

    private fun saveInitializedDates(set: Set<String>) {
        sharedPrefs.edit { putStringSet("initialized_dates", set) }
    }

    /**
     * 初期化済みの全ての日付のうち、今日および未来の日付に対して設定を同期します。
     */
    private fun syncAllInitializedFutureDates(context: android.content.Context) {
        val today = java.time.LocalDate.now()

        // ConcurrentModificationException を避けるためコピーに対して処理
        val dates = _initializedDates.value.toList()

        dates.forEach { dateStr ->
            try {
                val date = java.time.LocalDate.parse(dateStr)
                if (date.isAfter(today)) {
                    // 未来の日付は全件同期
                    syncDefaultLifeActivitiesForDate(context, dateStr, futureOnly = false)
                } else if (date.isEqual(today)) {
                    // 今日は今以降の分のみ同期
                    syncDefaultLifeActivitiesForDate(context, dateStr, futureOnly = true)
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 指定された日付に対して、デフォルトの生活習慣を同期（追加・更新）します。
     * @param context アラーム設定用
     * @param date 同期対象の日付 (yyyy-MM-dd)
     * @param futureOnly true の場合、現在時刻より後の習慣のみを追加・更新します。
     */
    fun syncDefaultLifeActivitiesForDate(
        context: android.content.Context,
        date: String,
        futureOnly: Boolean = false,
    ) {
        viewModelScope.launch {
            syncDefaultLifeActivitiesForDateInternal(context, date, futureOnly)
        }
    }

    private suspend fun syncDefaultLifeActivitiesForDateInternal(
        context: android.content.Context,
        date: String,
        futureOnly: Boolean = false,
    ) {
        val currentLifeActivities = scheduleRepository.loadLifeActivities()
        val defaults = currentLifeActivities.filter {
            (it.defaultStartTime != null) && (it.defaultEndTime != null)
        }

        if (defaults.isEmpty()) return

        val now = java.time.LocalDateTime.now()
        val todayStr = java.time.LocalDate.now().toString()
        val currentMinutes = (now.hour * 60) + now.minute

        val dayBlocks = timeBlocks.value.filter { (it.date == date) && (it.type == "life") }

        defaults.forEach { act ->
            // futureOnly かつ 今日の日付の場合、終了時刻が過ぎているものはスキップ
            if (futureOnly && (date == todayStr) && (act.defaultEndTime!! <= currentMinutes)) {
                return@forEach
            }

            val existingBlock = dayBlocks.find { it.associatedId == act.id }

            if (existingBlock != null) {
                // 既に存在する場合、内容（時間やタイトル、色）を更新
                if ((existingBlock.startTime != act.defaultStartTime) ||
                    (existingBlock.endTime != act.defaultEndTime) ||
                    (existingBlock.title != act.name) ||
                    (existingBlock.color != act.color)
                ) {

                    val updated = existingBlock.copy(
                        title = act.name,
                        startTime = act.defaultStartTime!!,
                        endTime = act.defaultEndTime!!,
                        color = act.color,
                    )
                    scheduleRepository.saveTimeBlock(updated)
                    com.notiontasks.app.TaskNotificationReceiver.scheduleBlockAlarm(context, updated)
                }
            } else {
                // 存在しない場合のみ新規追加
                val block = TimeBlock(
                    id = "tb_" + java.util.UUID.randomUUID().toString().take(8),
                    type = "life",
                    title = act.name,
                    associatedId = act.id,
                    startTime = act.defaultStartTime!!,
                    endTime = act.defaultEndTime!!,
                    color = act.color,
                    date = date,
                )
                scheduleRepository.saveTimeBlock(block)
                com.notiontasks.app.TaskNotificationReceiver.scheduleBlockAlarm(context, block)
            }
        }
    }

    fun autoInitializeDefaultLifeActivities(context: android.content.Context, date: String) {
        if (_initializedDates.value.contains(date)) return

        viewModelScope.launch {
            val currentBlocks = timeBlocks.value
            val hasAnyLifeOnThisDate = currentBlocks.any { (it.date == date) && (it.type == "life") }
            
            // この日付に既に生活アクティビティブロックがある場合は初期化済みとして扱います
            if (hasAnyLifeOnThisDate) {
                val updated = _initializedDates.value + date
                _initializedDates.value = updated
                saveInitializedDates(updated)
                return@launch
            }

            syncDefaultLifeActivitiesForDate(context, date, futureOnly = false)

            val updated = _initializedDates.value + date
            _initializedDates.value = updated
            saveInitializedDates(updated)
        }
    }

    fun addTimeBlock(context: android.content.Context, block: TimeBlock) {
        // 同じ ID の既存のアラームをキャンセルします
        com.notiontasks.app.TaskNotificationReceiver.cancelBlockAlarm(context, block.id)
        
        viewModelScope.launch {
            scheduleRepository.saveTimeBlock(block)
            com.notiontasks.app.TaskNotificationReceiver.scheduleBlockAlarm(context, block)
        }
    }

    fun deleteTimeBlock(context: android.content.Context, id: String) {
        com.notiontasks.app.TaskNotificationReceiver.cancelBlockAlarm(context, id)
        
        viewModelScope.launch {
            scheduleRepository.deleteTimeBlockById(id)
        }
    }

    // データベースプロパティの定義を取得する
    fun fetchDatabaseProperties(
        token: String,
        dbId: String,
        onSuccess: (NotionDatabaseResponse) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                if (token.isBlank() || dbId.isBlank()) {
                    onFailure("トークン、またはデータベースIDが入力されていません")
                    return@launch
                }
                val meta = repository.getDatabaseMetadata(token, dbId)
                onSuccess(meta)
            } catch (e: Exception) {
                onFailure(e.message ?: "データベース構成の取得に失敗しました。認証情報や共有設定を確認してください。")
            }
        }
    }

    fun loadCredentialsAndMappings() {
        val token = sharedPrefs.getString("notion_token", "") ?: ""
        val dbId = sharedPrefs.getString("database_id", "") ?: ""
        val title = sharedPrefs.getString("mapping_prop_title", "")?.ifBlank { "名前" } ?: "名前"
        val status = sharedPrefs.getString("mapping_prop_status", "")?.ifBlank { "状態" } ?: "状態"
        val statusType = sharedPrefs.getString("mapping_prop_status_type", "status") ?: "status"
        val category = sharedPrefs.getString("mapping_prop_category", "")?.ifBlank { "種類" } ?: "種類"
        val scheduledDate = sharedPrefs.getString("mapping_prop_scheduled_date", "")?.ifBlank { "予定日" } ?: "予定日"
        val dueDate = sharedPrefs.getString("mapping_prop_due_date", "")?.ifBlank { "締め切り" } ?: "締め切り"

        _notionToken.value = token
        _databaseId.value = dbId
        repository.updatePropertyMappings(
            title = title,
            status = status,
            statusType = statusType,
            category = category,
            scheduledDate = scheduledDate,
            dueDate = dueDate,
        )
    }

    // アクティビティのロードフック
    fun updateCredentials(
        token: String,
        dbId: String,
        title: String = "名前",
        status: String = "状態",
        statusType: String = "status",
        category: String = "種類",
        scheduledDate: String = "予定日",
        dueDate: String = "締め切り",
    ) {
        _notionToken.value = token
        _databaseId.value = dbId
        repository.updatePropertyMappings(
            title = title,
            status = status,
            statusType = statusType,
            category = category,
            scheduledDate = scheduledDate,
            dueDate = dueDate,
        )
    }

    // 状態更新を伴うメインの同期トリガーコールバック
    fun syncWithNotion() {
        loadCredentialsAndMappings()
        val token = _notionToken.value
        val dbId = _databaseId.value

        if (token.isBlank() || dbId.isBlank()) {
            return
        }

        viewModelScope.launch {
            try {
                repository.syncTasks(token, dbId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 選択されたカテゴリターゲットを設定する
    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateStatusOptions(statusOptions: List<NotionOptionInfo>) {
        if (statusOptions.isNotEmpty()) {
            _statusOptions.value = statusOptions
        }
    }

    fun updateCategoryOptions(categoryOptions: List<NotionOptionInfo>) {
        if (categoryOptions.isNotEmpty()) {
            _categoryOptions.value = categoryOptions
            if (_selectedCategory.value.isEmpty()) {
                _selectedCategory.value = categoryOptions.firstOrNull()?.name ?: ""
            }
        }
    }

    /**
     * NotionのDBメタデータからプロパティのマッピングを自動検知する
     */
    fun autoDetectMapping(meta: NotionDatabaseResponse): Map<String, String> {
        val detected = mutableMapOf<String, String>()
        meta.properties.forEach { (pName, pVal) ->
            when {
                pVal.title != null -> detected["title"] = pName
                pVal.status != null -> {
                    detected["status"] = pName
                    detected["statusType"] = "status"
                }
                (pVal.select != null) && (pName.contains("状態") || pName.lowercase().contains("status")) -> {
                    if (!detected.containsKey("status")) {
                        detected["status"] = pName
                        detected["statusType"] = "select"
                    }
                }
                (pVal.select != null) && (pName.contains("種類") || pName.contains("カテゴリ") || pName.lowercase().contains("category")) -> {
                    detected["category"] = pName
                }
                (pVal.date != null) && (pName.contains("予定") || pName.lowercase().contains("scheduled")) -> {
                    detected["scheduled"] = pName
                }
                (pVal.date != null) && (pName.contains("締切") || pName.contains("期限") || pName.lowercase().contains("due")) -> {
                    detected["due"] = pName
                }
            }
        }
        return detected
    }

    // 定義されたステータスオプションに対する動的なサイクルロジック
    fun cycleTaskStatus(task: TaskModel, stateOptions: List<NotionOptionInfo> = emptyList()) {
        loadCredentialsAndMappings()
        val options = stateOptions.ifEmpty { _statusOptions.value }
        if (options.isEmpty()) return

        val currentIndex = options.indexOfFirst { it.name == task.status }
        
        val nextOption = if (currentIndex == -1) {
            options.first()
        } else {
            options[(currentIndex + 1) % options.size]
        }

        val token = _notionToken.value
        if (token.isBlank()) return

        viewModelScope.launch {
            try {
                repository.updateTaskStatus(
                    token = token,
                    pageId = task.id,
                    newStatus = nextOption.name,
                    newStatusColor = nextOption.color.ifBlank { null }
                )
            } catch (_: Exception) {
                // フォールバックまたはログ出力
            }
        }
    }

    fun updateTask(
        id: String,
        title: String,
        status: String,
        category: String,
        dueDate: String? = null,
        scheduledDate: String? = null,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) {
        loadCredentialsAndMappings()
        val token = _notionToken.value
        if (token.isBlank()) {
            onFailure("Notionの設定が不十分です（トークンが未入力）。")
            return
        }

        val sColor = _statusOptions.value.find { it.name == status }?.color
        val cColor = _categoryOptions.value.find { it.name == category }?.color

        viewModelScope.launch {
            try {
                repository.updateTask(
                    token = token,
                    pageId = id,
                    title = title,
                    status = status,
                    category = category.trim(),
                    dueDate = if (dueDate.isNullOrBlank()) null else dueDate,
                    scheduledDate = if (scheduledDate.isNullOrBlank()) null else scheduledDate,
                    statusColor = sColor?.ifBlank { null },
                    categoryColor = cColor?.ifBlank { null }
                )
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "タスクの更新に失敗しました。")
            }
        }
    }

    fun addTask(
        title: String,
        category: String,
        status: String? = null,
        dueDate: String? = null,
        scheduledDate: String? = null,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) {
        loadCredentialsAndMappings()
        val token = _notionToken.value
        val dbId = _databaseId.value
        if (token.isBlank() || dbId.isBlank()) {
            onFailure("Notionの設定が不十分です（トークンまたはデータベースIDが未入力）。")
            return
        }

        val finalStatus = status ?: _statusOptions.value.firstOrNull()?.name ?: "未着手"
        val sColor = _statusOptions.value.find { it.name == finalStatus }?.color
        val cColor = _categoryOptions.value.find { it.name == category.trim() }?.color

        viewModelScope.launch {
            try {
                repository.createTask(
                    token = token,
                    databaseId = dbId,
                    title = title,
                    status = finalStatus,
                    category = category.trim(),
                    dueDate = if (dueDate.isNullOrBlank()) null else dueDate,
                    scheduledDate = if (scheduledDate.isNullOrBlank()) null else scheduledDate,
                    statusColor = sColor?.ifBlank { null },
                    categoryColor = cColor?.ifBlank { null }
                )
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "タスクの作成に失敗しました。")
            }
        }
    }
}