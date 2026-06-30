package com.notiontasks.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Screen operations status states
sealed interface TasksUiState {
    object Idle : TasksUiState
    object Loading : TasksUiState
    data class Success(val tasks: List<TaskModel>) : TasksUiState
    data class Error(val message: String) : TasksUiState
}

class TaskViewModel(
    private val repository: TaskRepository
) : ViewModel() {

    // Config stored values (injected from encrypted dynamic shared preferences in MainActivity)
    private val _notionToken = MutableStateFlow("")
    val notionToken: StateFlow<String> = _notionToken.asStateFlow()

    private val _databaseId = MutableStateFlow("")
    val databaseId: StateFlow<String> = _databaseId.asStateFlow()

    // Screen-level stateflow combining live Room updates
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

    // Separate category filter flow
    private val _selectedCategory = MutableStateFlow("課題")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _statusOptions = MutableStateFlow(listOf("未着手", "進行中", "完了"))

    // Fetch database properties definition
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

    // Activity load hooks
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

    // Main sync trigger calling back with state updates
    fun syncWithNotion() {
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

    // Set selected category target
    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateStatusOptions(statusOptions: List<String>) {
        if (statusOptions.isNotEmpty()) {
            _statusOptions.value = statusOptions
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

    // Dynamic cycle logic over defined state options
    fun cycleTaskStatus(task: TaskModel, stateOptions: List<String> = emptyList()) {
        val options = stateOptions.ifEmpty { listOf("未着手", "進行中", "完了") }
        val currentIndex = options.indexOf(task.status)
        
        val nextStatus = if (currentIndex == -1) {
            options.firstOrNull() ?: "未着手"
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
                    newStatus = nextStatus
                )
            } catch (_: Exception) {
                // Failback or log
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
        status: String = _statusOptions.value.firstOrNull() ?: "未着手",
        dueDate: String? = null,
        scheduledDate: String? = null,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val token = _notionToken.value
        val dbId = _databaseId.value
        if (token.isBlank() || dbId.isBlank()) {
            onFailure("Notionの設定が不十分です（トークンまたはデータベースIDが未入力）。")
            return
        }

        viewModelScope.launch {
            try {
                repository.createTask(
                    token = token,
                    databaseId = dbId,
                    title = title,
                    status = status,
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