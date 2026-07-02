package com.notiontasks.app.ui.viewmodel

import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// 画面操作のステータス状態
sealed interface TasksUiState {
    object Idle : TasksUiState
    object Loading : TasksUiState
    data class Success(val tasks: List<TaskModel>) : TasksUiState
    data class Error(val message: String) : TasksUiState
}

class TaskViewModel(
    private val repository: TaskRepository,
    private val sharedPrefs: android.content.SharedPreferences
) : ViewModel() {

    private val jsonSerializer = Json { ignoreUnknownKeys = true }

    // スケジュール/タイムブロッキングの状態
    private val _timeBlocks = MutableStateFlow<List<TimeBlock>>(emptyList())
    val timeBlocks: StateFlow<List<TimeBlock>> = _timeBlocks.asStateFlow()

    private val _lifeActivities = MutableStateFlow<List<LifeActivity>>(emptyList())
    val lifeActivities: StateFlow<List<LifeActivity>> = _lifeActivities.asStateFlow()

    private val _initializedDates = MutableStateFlow<Set<String>>(emptySet())

    // 保存された構成値 (MainActivity の暗号化された動的 SharedPrefs から挿入されます)
    private val _notionToken = MutableStateFlow("")
    val notionToken: StateFlow<String> = _notionToken.asStateFlow()

    private val _databaseId = MutableStateFlow("")
    val databaseId: StateFlow<String> = _databaseId.asStateFlow()

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
            initialValue = TasksUiState.Loading
        )

    // 個別のカテゴリフィルタフロー
    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _statusOptions = MutableStateFlow<List<NotionOptionInfo>>(emptyList())
    val statusOptions: StateFlow<List<NotionOptionInfo>> = _statusOptions.asStateFlow()

    private val _categoryOptions = MutableStateFlow<List<NotionOptionInfo>>(emptyList())
    val categoryOptions: StateFlow<List<NotionOptionInfo>> = _categoryOptions.asStateFlow()

    init {
        loadCredentialsAndMappings()
        loadLifeActivities()
        loadTimeBlocks()
        loadInitializedDates()
        loadOptions()
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

    private fun loadLifeActivities() {
        val jsonStr = sharedPrefs.getString("saved_life_activities", null)
        val list = if (jsonStr != null) {
            try {
                jsonSerializer.decodeFromString<List<LifeActivity>>(jsonStr)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        
        if (list.isEmpty()) {
            val defaults = listOf(
                LifeActivity("la_sleep", "睡眠", 480, "#9C27B0", defaultStartTime = 0, defaultEndTime = 420), // 00:00 - 07:00
                LifeActivity("la_meal", "食事", 60, "#FF9800", defaultStartTime = 720, defaultEndTime = 780), // 12:00 - 13:00
                LifeActivity("la_rest", "休憩", 30, "#4CAF50"),
                LifeActivity("la_transit", "移動", 30, "#2196F3"),
                LifeActivity("la_bath", "お風呂", 30, "#00BCD4", defaultStartTime = 1260, defaultEndTime = 1290), // 21:00 - 21:30
                LifeActivity("la_exercise", "運動", 60, "#E91E63")
            )
            _lifeActivities.value = defaults
            saveLifeActivitiesInternal(defaults)
        } else {
            _lifeActivities.value = list
        }
    }

    private fun saveLifeActivitiesInternal(list: List<LifeActivity>) {
        try {
            val str = jsonSerializer.encodeToString(list)
            sharedPrefs.edit { putString("saved_life_activities", str) }
        } catch (_: Exception) {}
    }

    fun saveLifeActivities(list: List<LifeActivity>) {
        _lifeActivities.value = list
        saveLifeActivitiesInternal(list)
    }

    fun addLifeActivity(activity: LifeActivity) {
        val updated = _lifeActivities.value + activity
        _lifeActivities.value = updated
        saveLifeActivitiesInternal(updated)
    }

    fun deleteLifeActivity(id: String) {
        val updated = _lifeActivities.value.filter { it.id != id }
        _lifeActivities.value = updated
        saveLifeActivitiesInternal(updated)
    }

    private fun loadInitializedDates() {
        val set = sharedPrefs.getStringSet("initialized_dates", emptySet()) ?: emptySet()
        _initializedDates.value = set
    }

    private fun saveInitializedDates(set: Set<String>) {
        sharedPrefs.edit { putStringSet("initialized_dates", set) }
    }

    fun autoInitializeDefaultLifeActivities(context: android.content.Context, date: String) {
        if (_initializedDates.value.contains(date)) return
        
        val currentBlocks = _timeBlocks.value
        val hasAnyLifeOnThisDate = currentBlocks.any { it.date == date && it.type == "life" }
        
        // この日付に既に生活アクティビティブロックがある場合は、重複を避けるために初期化済みとして扱います
        if (hasAnyLifeOnThisDate) {
            val updated = _initializedDates.value + date
            _initializedDates.value = updated
            saveInitializedDates(updated)
            return
        }

        val defaultsToInsert = _lifeActivities.value.filter {
            it.defaultStartTime != null && it.defaultEndTime != null
        }

        if (defaultsToInsert.isEmpty()) {
            val updated = _initializedDates.value + date
            _initializedDates.value = updated
            saveInitializedDates(updated)
            return
        }

        var updatedBlocks = currentBlocks
        defaultsToInsert.forEach { act ->
            val block = TimeBlock(
                id = "tb_" + java.util.UUID.randomUUID().toString().take(8),
                type = "life",
                title = act.name,
                associatedId = act.id,
                startTime = act.defaultStartTime!!,
                endTime = act.defaultEndTime!!,
                color = act.color,
                date = date
            )
            updatedBlocks = updatedBlocks.filter { it.id != block.id } + block
            com.notiontasks.app.TaskNotificationReceiver.scheduleBlockAlarm(context, block)
        }

        _timeBlocks.value = updatedBlocks
        saveTimeBlocksInternal(updatedBlocks)

        val updated = _initializedDates.value + date
        _initializedDates.value = updated
        saveInitializedDates(updated)
    }

    private fun loadTimeBlocks() {
        val jsonStr = sharedPrefs.getString("saved_time_blocks", null)
        if (jsonStr != null) {
            try {
                val list = jsonSerializer.decodeFromString<List<TimeBlock>>(jsonStr)
                _timeBlocks.value = list
            } catch (_: Exception) {}
        }
    }

    private fun saveTimeBlocksInternal(list: List<TimeBlock>) {
        try {
            val str = jsonSerializer.encodeToString(list)
            sharedPrefs.edit { putString("saved_time_blocks", str) }
        } catch (_: Exception) {}
    }

    fun addTimeBlock(context: android.content.Context, block: TimeBlock) {
        // 念のため、同じ ID の既存のアラームをキャンセルします
        com.notiontasks.app.TaskNotificationReceiver.cancelBlockAlarm(context, block.id)
        
        val updated = _timeBlocks.value.filter { it.id != block.id } + block
        _timeBlocks.value = updated
        saveTimeBlocksInternal(updated)
        
        // 新しいブロックのアラームをスケジュールします
        com.notiontasks.app.TaskNotificationReceiver.scheduleBlockAlarm(context, block)
    }

    fun deleteTimeBlock(context: android.content.Context, id: String) {
        com.notiontasks.app.TaskNotificationReceiver.cancelBlockAlarm(context, id)
        
        val updated = _timeBlocks.value.filter { it.id != id }
        _timeBlocks.value = updated
        saveTimeBlocksInternal(updated)
    }

    // データベースプロパティの定義を取得する
    fun fetchDatabaseProperties(
        token: String,
        dbId: String,
        onSuccess: (NotionDatabaseResponse) -> Unit,
        onFailure: (String) -> Unit
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
            dueDate = dueDate
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
        dueDate: String = "締め切り"
    ) {
        _notionToken.value = token
        _databaseId.value = dbId
        repository.updatePropertyMappings(
            title = title,
            status = status,
            statusType = statusType,
            category = category,
            scheduledDate = scheduledDate,
            dueDate = dueDate
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
            } catch (_: Exception) {
                // Failures logged
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
                pVal.select != null && (pName.contains("状態") || pName.lowercase().contains("status")) -> {
                    if (!detected.containsKey("status")) {
                        detected["status"] = pName
                        detected["statusType"] = "select"
                    }
                }
                pVal.select != null && (pName.contains("種類") || pName.contains("カテゴリ") || pName.lowercase().contains("category")) -> {
                    detected["category"] = pName
                }
                pVal.date != null && (pName.contains("予定") || pName.lowercase().contains("scheduled")) -> {
                    detected["scheduled"] = pName
                }
                pVal.date != null && (pName.contains("締切") || pName.contains("期限") || pName.lowercase().contains("due")) -> {
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
                    newStatus = nextOption.name
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
        onFailure: (String) -> Unit = {}
    ) {
        loadCredentialsAndMappings()
        val token = _notionToken.value
        if (token.isBlank()) {
            onFailure("Notionの設定が不十分です（トークンが未入力）。")
            return
        }

        viewModelScope.launch {
            try {
                repository.updateTask(
                    token = token,
                    pageId = id,
                    title = title,
                    status = status,
                    category = category.trim(),
                    dueDate = if (dueDate.isNullOrBlank()) null else dueDate,
                    scheduledDate = if (scheduledDate.isNullOrBlank()) null else scheduledDate
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
        onFailure: (String) -> Unit = {}
    ) {
        loadCredentialsAndMappings()
        val token = _notionToken.value
        val dbId = _databaseId.value
        if (token.isBlank() || dbId.isBlank()) {
            onFailure("Notionの設定が不十分です（トークンまたはデータベースIDが未入力）。")
            return
        }

        val finalStatus = status ?: _statusOptions.value.firstOrNull()?.name ?: "未着手"

        viewModelScope.launch {
            try {
                repository.createTask(
                    token = token,
                    databaseId = dbId,
                    title = title,
                    status = finalStatus,
                    category = category.trim(),
                    dueDate = if (dueDate.isNullOrBlank()) null else dueDate,
                    scheduledDate = if (scheduledDate.isNullOrBlank()) null else scheduledDate
                )
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "タスクの作成に失敗しました。")
            }
        }
    }
}