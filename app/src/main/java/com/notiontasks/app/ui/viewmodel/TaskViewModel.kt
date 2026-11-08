package com.notiontasks.app.ui.viewmodel

import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.data.model.TimeBlockOrigin
import com.notiontasks.app.data.PomodoroLog
import com.notiontasks.app.data.local.PendingSyncActionEntity
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.data.repository.ScheduleStore
import com.notiontasks.app.data.repository.TaskRepository
import com.notiontasks.app.data.repository.TaskSaveResult
import com.notiontasks.app.data.repository.PomodoroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.TimeZone

// 画面操作のステータス状態
sealed interface TasksUiState {
    object Idle : TasksUiState
    object Loading : TasksUiState
    data class Success(val tasks: List<TaskModel>) : TasksUiState
    data class Error(val message: String) : TasksUiState
}

class TaskViewModel(
    private val repository: TaskRepository,
    private val scheduleRepository: ScheduleStore,
    private val pomodoroRepository: PomodoroRepository,
    private val sharedPrefs: android.content.SharedPreferences,
) : ViewModel() {

    private val taskSaveMutex = Mutex()
    private val lifeScheduleMutex = Mutex()
    private val _activeTaskSaveKeys = MutableStateFlow<Set<String>>(emptySet())
    val activeTaskSaveKeys: StateFlow<Set<String>> = _activeTaskSaveKeys.asStateFlow()
    val isCreatingTask: StateFlow<Boolean> = activeTaskSaveKeys
        .map { CREATE_TASK_SAVE_KEY in it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    private fun launchTaskSaveOnce(key: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            val accepted = taskSaveMutex.withLock {
                if (key in _activeTaskSaveKeys.value) {
                    false
                } else {
                    _activeTaskSaveKeys.value += key
                    true
                }
            }
            if (!accepted) return@launch

            try {
                block()
            } finally {
                taskSaveMutex.withLock {
                    _activeTaskSaveKeys.value -= key
                }
            }
        }
    }

    private val _syncPhase = MutableStateFlow<SyncUiState>(SyncUiState.NotConfigured)
    val syncState: StateFlow<SyncUiState> = combine(
        repository.pendingActionCount,
        _syncPhase,
    ) { pendingCount, phase ->
        when {
            phase is SyncUiState.Syncing -> phase
            phase is SyncUiState.Failed -> phase
            pendingCount > 0 -> SyncUiState.PendingChanges(pendingCount)
            else -> phase
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncUiState.NotConfigured,
    )

    val permanentSyncFailures: StateFlow<List<PendingSyncActionEntity>> = repository.permanentFailures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryPermanentSyncFailure(id: Long) {
        viewModelScope.launch {
            if (repository.retryPermanentFailure(id)) syncWithNotion()
        }
    }

    fun discardPermanentSyncFailure(id: Long) {
        viewModelScope.launch { repository.discardPermanentFailure(id) }
    }

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
    /** 自動配置の更新対象を、設定画面で説明するために公開する読み取り専用状態。 */
    val initializedDates: StateFlow<Set<String>> = _initializedDates.asStateFlow()

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

    private val _statusUnstarted = MutableStateFlow(sharedPrefs.getString("mapping_status_unstarted", "未着手") ?: "未着手")
    val statusUnstarted: StateFlow<String> = _statusUnstarted.asStateFlow()

    private val _statusInProgress = MutableStateFlow(sharedPrefs.getString("mapping_status_in_progress", "進行中") ?: "進行中")
    val statusInProgress: StateFlow<String> = _statusInProgress.asStateFlow()

    private val _statusCompleted = MutableStateFlow(sharedPrefs.getString("mapping_status_completed", "完了") ?: "完了")
    val statusCompleted: StateFlow<String> = _statusCompleted.asStateFlow()

    private val _categoryOptions = MutableStateFlow<List<NotionOptionInfo>>(emptyList())
    val categoryOptions: StateFlow<List<NotionOptionInfo>> = combine(
        _categoryOptions,
        repository.allTasks,
    ) { savedOptions, cachedTasks ->
        categoryOptionsForTaskUi(savedOptions, cachedTasks)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _categoryOptions.value,
    )

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
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(com.notiontasks.app.utils.JapanTime.zoneId.id))
            calendar.add(Calendar.MONTH, -months)
            pomodoroRepository.deleteLogsOlderThan(calendar.timeInMillis)
        }
    }

    fun setStatsStorageDuration(months: Int) {
        _statsStorageDuration.value = months
        sharedPrefs.edit { putInt("pomodoro_stats_duration_months", months) }
    }

    /** 保持期間を短縮するときだけ、ユーザー確認後に呼び出す。 */
    fun setStatsStorageDurationAndCleanup(months: Int) {
        setStatsStorageDuration(months)
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
                val calendar = Calendar.getInstance(TimeZone.getTimeZone(com.notiontasks.app.utils.JapanTime.zoneId.id))
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
            val today = com.notiontasks.app.utils.JapanTime.today()
            val nowMinutes = com.notiontasks.app.utils.JapanTime.now().let { (it.hour * 60) + it.minute }
            
            // UIキャッシュではなく、DBから全ブロックを直接取得してスキャンする
            val allBlocks = scheduleRepository.getAllTimeBlocks()
            allBlocks.filter { block ->
                LifeSchedulePlanner.canRemoveForDeletedPreset(block, id, today, nowMinutes)
            }.forEach { block ->
                scheduleRepository.deleteTimeBlockById(block.id)
                com.notiontasks.app.TaskNotificationReceiver.cancelBlockAlarm(context, block.id)
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
        val today = com.notiontasks.app.utils.JapanTime.today()

        // ConcurrentModificationException を避けるためコピーに対して処理
        val dates = _initializedDates.value.toList()

        dates.forEach { dateStr ->
            when {
                com.notiontasks.app.utils.JapanTime.parseCalendarDate(dateStr)?.isAfter(today) == true -> {
                    // 未来の日付は全件同期
                    syncDefaultLifeActivitiesForDate(context, dateStr, futureOnly = false)
                }
                com.notiontasks.app.utils.JapanTime.parseCalendarDate(dateStr) == today -> {
                    // 今日は今以降の分のみ同期
                    syncDefaultLifeActivitiesForDate(context, dateStr, futureOnly = true)
                }
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
            lifeScheduleMutex.withLock {
                syncDefaultLifeActivitiesForDateInternal(context, date, futureOnly)
            }
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

        val now = com.notiontasks.app.utils.JapanTime.now()
        val todayStr = com.notiontasks.app.utils.JapanTime.todayIso()
        val currentMinutes = (now.hour * 60) + now.minute

        // Use the database, not the asynchronously collected UI cache. This makes repeated
        // opens and quick date changes observe writes made by a preceding generation.
        val dayBlocks = scheduleRepository.getTimeBlocksForDate(date)

        defaults.forEach { act ->
            val defaultStartTime = act.defaultStartTime ?: return@forEach
            val defaultEndTime = act.defaultEndTime ?: return@forEach
            // futureOnly かつ 今日の日付の場合、終了時刻が過ぎているものはスキップ
            if (futureOnly && (date == todayStr) && (defaultEndTime <= currentMinutes)) {
                return@forEach
            }

            val existingBlock = dayBlocks.find {
                it.origin == TimeBlockOrigin.PRESET && it.presetId == act.id
            }

            if (existingBlock != null) {
                // 既に存在する場合、内容（時間やタイトル、色）を更新
                if (LifeSchedulePlanner.needsPresetUpdate(
                        existingBlock,
                        name = act.name,
                        startTime = defaultStartTime,
                        endTime = defaultEndTime,
                        color = act.color,
                    )
                ) {

                    val updated = existingBlock.copy(
                        title = act.name,
                        startTime = defaultStartTime,
                        endTime = defaultEndTime,
                        color = act.color,
                        presetGeneration = DEFAULT_PRESET_GENERATION,
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
                    startTime = defaultStartTime,
                    endTime = defaultEndTime,
                    color = act.color,
                    date = date,
                    origin = TimeBlockOrigin.PRESET,
                    presetId = act.id,
                    presetGeneration = DEFAULT_PRESET_GENERATION,
                )
                scheduleRepository.saveTimeBlock(block)
                com.notiontasks.app.TaskNotificationReceiver.scheduleBlockAlarm(context, block)
            }
        }
    }

    fun autoInitializeDefaultLifeActivities(context: android.content.Context, date: String) {
        if (_initializedDates.value.contains(date)) return

        viewModelScope.launch {
            lifeScheduleMutex.withLock {
                // Mark completion only after every generated block was persisted. A process
                // interruption therefore leaves the date eligible for a safe idempotent retry.
                syncDefaultLifeActivitiesForDateInternal(context, date, futureOnly = false)
                val updated = _initializedDates.value + date
                _initializedDates.value = updated
                saveInitializedDates(updated)
            }
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
                repository.updateDataSourceId("")
                val meta = repository.getDatabaseMetadata(token, dbId)
                onSuccess(meta)
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        val statusUnstarted = sharedPrefs.getString("mapping_status_unstarted", "")?.ifBlank { "未着手" }?.trim() ?: "未着手"
        val statusInProgress = sharedPrefs.getString("mapping_status_in_progress", "")?.ifBlank { "進行中" }?.trim() ?: "進行中"
        val statusCompleted = sharedPrefs.getString("mapping_status_completed", "")?.ifBlank { "完了" }?.trim() ?: "完了"
        val category = sharedPrefs.getString("mapping_prop_category", "") ?: ""
        val scheduledDate = sharedPrefs.getString("mapping_prop_scheduled_date", "") ?: ""
        val dueDate = sharedPrefs.getString("mapping_prop_due_date", "") ?: ""

        _notionToken.value = token
        _databaseId.value = dbId
        if (token.isBlank() || dbId.isBlank()) {
            _syncPhase.value = SyncUiState.NotConfigured
        }
        _statusUnstarted.value = statusUnstarted
        _statusInProgress.value = statusInProgress
        _statusCompleted.value = statusCompleted
        repository.updatePropertyMappings(
            title = title,
            status = status,
            statusType = statusType,
            statusUnstarted = statusUnstarted,
            statusInProgress = statusInProgress,
            statusCompleted = statusCompleted,
            category = category,
            scheduledDate = scheduledDate,
            dueDate = dueDate,
        )
        repository.updateConnectionScope(dbId)
        repository.updateDataSourceId("")
    }

    // アクティビティのロードフック
    fun updateCredentials(config: TaskConnectionConfig) {
        val trimmedUnstarted = config.statusUnstarted.trim()
        val trimmedInProgress = config.statusInProgress.trim()
        val trimmedCompleted = config.statusCompleted.trim()
        
        _notionToken.value = config.token
        _databaseId.value = config.databaseId
        if (config.token.isBlank() || config.databaseId.isBlank()) {
            _syncPhase.value = SyncUiState.NotConfigured
        }
        _statusUnstarted.value = trimmedUnstarted
        _statusInProgress.value = trimmedInProgress
        _statusCompleted.value = trimmedCompleted
        repository.updatePropertyMappings(
            title = config.title,
            status = config.status,
            statusType = config.statusType,
            statusUnstarted = trimmedUnstarted,
            statusInProgress = trimmedInProgress,
            statusCompleted = trimmedCompleted,
            category = config.category,
            scheduledDate = config.scheduledDate,
            dueDate = config.dueDate,
        )
        repository.updateConnectionScope(config.databaseId)
        repository.updateDataSourceId("")
    }

    // 状態更新を伴うメインの同期トリガーコールバック
    fun syncWithNotion(incremental: Boolean = true) {
        loadCredentialsAndMappings()
        val token = _notionToken.value
        val dbId = _databaseId.value

        if (token.isBlank() || dbId.isBlank()) {
            _syncPhase.value = SyncUiState.NotConfigured
            return
        }

        viewModelScope.launch {
            _syncPhase.value = SyncUiState.Syncing
            try {
                repository.syncTasks(token, dbId, incremental = incremental)
                // 同期後に完了済みタスクのクリーンアップを実行
                cleanupData()
                _syncPhase.value = SyncUiState.Synced(System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _syncPhase.value = SyncUiState.Failed(
                    message = e.message ?: "同期に失敗しました",
                    retryable = true,
                )
            }
        }
    }

    /** Keeps persisted UI selections usable after an offline task receives its Notion page ID. */
    suspend fun resolveTaskId(id: String): String = repository.resolveTaskId(id)

    private suspend fun cleanupData() {
        val keepType = sharedPrefs.getString("data_keep_type", "date") ?: "date"
        val keepVal = if (keepType == "date") {
            sharedPrefs.getInt("data_keep_months", 0)
        } else {
            sharedPrefs.getInt("data_keep_count", 1000)
        }
        repository.cleanupCompletedTasks(keepType, keepVal)
        cleanupOldPomodoroLogs()
    }

    fun deletePomodoroLogsByMonths(months: Int) {
        viewModelScope.launch {
            if (months == -1) {
                pomodoroRepository.clearAllLogs()
            } else {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone(com.notiontasks.app.utils.JapanTime.zoneId.id))
                calendar.add(Calendar.MONTH, -months)
                pomodoroRepository.deleteLogsOlderThan(calendar.timeInMillis)
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
        val props = meta.properties

        // 1. タイトル (名前) の検知
        detected["title"] = props.entries.find { it.value.title != null }?.key 
            ?: props.keys.find { it.contains("名前") || it.contains("タスク") || it.contains("Title", ignoreCase = true) || it.contains("Name", ignoreCase = true) }
            ?: ""

        // 2. ステータスの検知 (Status型を最優先)
        val statusProp = props.entries.find { it.value.status != null }
            ?: props.entries.find { it.value.select != null && (it.key.contains("状態") || it.key.contains("ステータス") || it.key.contains("Status", ignoreCase = true) || it.key.contains("Progress", ignoreCase = true)) }
        
        if (statusProp != null) {
            val pName = statusProp.key
            val pVal = statusProp.value
            detected["status"] = pName
            detected["statusType"] = if (pVal.status != null) "status" else "select"
            
            val options = pVal.status?.options ?: pVal.select?.options ?: emptyList()
            
            // ステータス値の推論 (グループ情報がある場合は優先使用)
            if (pVal.status != null) {
                pVal.status.groups.forEach { group ->
                    val firstOption = options.find { it.id == group.optionIds.firstOrNull() }?.name
                    when (group.name.lowercase()) {
                        "to do", "未着手", "todo" -> detected["statusUnstarted"] = firstOption ?: ""
                        "in progress", "進行中", "doing" -> detected["statusInProgress"] = firstOption ?: ""
                        "complete", "完了", "done" -> detected["statusCompleted"] = firstOption ?: ""
                    }
                }
            }
            
            // グループで見つからない、または Select 型の場合のキーワード補完
            if (detected["statusUnstarted"].isNullOrBlank()) {
                detected["statusUnstarted"] = options.find { it.name.contains("未着手") || it.name.contains("待機") || it.name.contains("Todo", ignoreCase = true) || it.name.contains("Backlog", ignoreCase = true) }?.name 
                    ?: options.getOrNull(0)?.name ?: ""
            }
            if (detected["statusInProgress"].isNullOrBlank()) {
                detected["statusInProgress"] = options.find { it.name.contains("進行") || it.name.contains("作業") || it.name.contains("進捗") || it.name.contains("Doing", ignoreCase = true) || it.name.contains("Progress", ignoreCase = true) }?.name 
                    ?: options.getOrNull(1)?.name ?: ""
            }
            if (detected["statusCompleted"].isNullOrBlank()) {
                detected["statusCompleted"] = options.find { it.name.contains("完了") || it.name.contains("済") || it.name.contains("Done", ignoreCase = true) || it.name.contains("Finished", ignoreCase = true) || it.name.contains("Complete", ignoreCase = true) }?.name 
                    ?: options.getOrNull(2)?.name ?: ""
            }
        }

        // 3. カテゴリの検知 (Select型)
        detected["category"] = props.entries.find { it.value.select != null && (it.key.contains("カテゴリ") || it.key.contains("種類") || it.key.contains("分類") || it.key.contains("Category", ignoreCase = true) || it.key.contains("Type", ignoreCase = true) || it.key.contains("Tag", ignoreCase = true)) }?.key 
            ?: ""

        // 4. 予定日の検知 (Date型)
        detected["scheduled"] = props.entries.find { it.value.date != null && (it.key.contains("予定") || it.key.contains("実施") || it.key.contains("Scheduled", ignoreCase = true) || it.key.contains("Start", ignoreCase = true)) }?.key 
            ?: props.entries.find { it.value.date != null }?.key 
            ?: ""

        // 5. 締め切りの検知 (Date型)
        detected["due"] = props.entries.find { it.value.date != null && it.key != detected["scheduled"] && (it.key.contains("締切") || it.key.contains("期限") || it.key.contains("Due", ignoreCase = true) || it.key.contains("End", ignoreCase = true) || it.key.contains("Deadline", ignoreCase = true)) }?.key 
            ?: ""

        return detected
    }

    // 定義されたステータスオプションに対する動的なサイクルロジック
    fun cycleTaskStatus(task: TaskModel, stateOptions: List<NotionOptionInfo> = emptyList()) {
        loadCredentialsAndMappings()
        val options = stateOptions.ifEmpty { _statusOptions.value }
        if (options.isEmpty()) return

        val nextStatus = nextSupportedTaskStatus(
            currentStatus = task.status,
            unstartedStatus = _statusUnstarted.value,
            inProgressStatus = _statusInProgress.value,
            completedStatus = _statusCompleted.value,
        ) ?: return
        val nextOption = options.firstOrNull { it.name.trim() == nextStatus } ?: return

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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The task status remains in the local cache; the next explicit sync may retry it.
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
        onConfirmed: () -> Unit = {},
        onPending: () -> Unit = {},
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

        launchTaskSaveOnce(id) {
            try {
                when (repository.updateTask(
                    token = token,
                    pageId = id,
                    title = title,
                    status = status,
                    category = category.trim(),
                    dueDate = if (dueDate.isNullOrBlank()) null else dueDate,
                    scheduledDate = if (scheduledDate.isNullOrBlank()) null else scheduledDate,
                    statusColor = sColor?.ifBlank { null },
                    categoryColor = cColor?.ifBlank { null }
                )) {
                    TaskSaveResult.Confirmed -> onConfirmed()
                    TaskSaveResult.Pending -> onPending()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        onConfirmed: () -> Unit = {},
        onPending: () -> Unit = {},
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

        launchTaskSaveOnce(CREATE_TASK_SAVE_KEY) {
            try {
                when (repository.createTask(
                    token = token,
                    databaseId = dbId,
                    title = title,
                    status = finalStatus,
                    category = category.trim(),
                    dueDate = if (dueDate.isNullOrBlank()) null else dueDate,
                    scheduledDate = if (scheduledDate.isNullOrBlank()) null else scheduledDate,
                    statusColor = sColor?.ifBlank { null },
                    categoryColor = cColor?.ifBlank { null }
                )) {
                    TaskSaveResult.Confirmed -> onConfirmed()
                    TaskSaveResult.Pending -> onPending()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                onFailure(e.message ?: "タスクの作成に失敗しました。")
            }
        }
    }

    private companion object {
        const val CREATE_TASK_SAVE_KEY = "create-task"
        const val DEFAULT_PRESET_GENERATION = 1L
    }
}
