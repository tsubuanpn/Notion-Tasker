package com.notiontasks.app.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.notiontasks.app.data.local.PendingSyncActionDao
import com.notiontasks.app.data.local.PendingSyncActionEntity
import com.notiontasks.app.data.local.SyncMetadataDao
import com.notiontasks.app.data.local.SyncMetadataEntity
import com.notiontasks.app.data.local.TaskDao
import com.notiontasks.app.data.local.TaskEntity
import com.notiontasks.app.data.local.TaskIdAliasDao
import com.notiontasks.app.data.local.TaskFieldGenerationDao
import com.notiontasks.app.data.local.TaskFieldGenerationEntity
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.data.remote.NotionApi
import com.notiontasks.app.data.remote.dto.NotionUpdateRequest
import com.notiontasks.app.data.remote.dto.NotionCreateRequest
import com.notiontasks.app.data.remote.dto.DatabaseParent
import com.notiontasks.app.data.remote.dto.PropertyUpdate
import com.notiontasks.app.data.remote.dto.RichTextObject
import com.notiontasks.app.data.remote.dto.TextContent
import com.notiontasks.app.data.remote.dto.StatusValue
import com.notiontasks.app.data.remote.dto.SelectValue
import com.notiontasks.app.data.remote.dto.DateValue
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.remote.dto.NotionQueryRequest
import com.notiontasks.app.data.remote.dto.NotionSort
import com.notiontasks.app.data.remote.dto.NotionDataSourceUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.UUID
import retrofit2.HttpException

private const val FULL_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val REMOTE_SYNC_CURSOR_KEY = "notion_remote_sync_cursor"
private const val CLIENT_IDENTIFIER_PROPERTY = "Notion Tasker ID"

/**
 * Whether a task change reached Notion now or has only been retained locally
 * for the automatic retry queue. Non-retryable failures continue to throw.
 */
sealed interface TaskSaveResult {
    data object Confirmed : TaskSaveResult
    data object Pending : TaskSaveResult
}

private class PendingSyncActionException(message: String, cause: Throwable) : IOException(message, cause)

private fun remoteSyncCursorKey(databaseId: String): String =
    "$REMOTE_SYNC_CURSOR_KEY:${databaseId.trim()}"

class TaskRepository(
    private val notionApi: NotionApi,
    private val taskDao: TaskDao,
    private val pendingSyncActionDao: PendingSyncActionDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val taskIdAliasDao: TaskIdAliasDao,
    private val taskFieldGenerationDao: TaskFieldGenerationDao,
) {

    // 非同期処理およびリレーショナル整合性のための Mutex 排他制御
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var lastFullSyncAt = 0L
    private var verifiedClientIdentifierDataSourceId: String? = null
    private val connectionScope = MutableStateFlow<String?>(null)

    private fun Throwable.isRetryableSyncFailure(): Boolean = when (this) {
        is PendingSyncActionException -> false
        is IOException -> true
        is HttpException -> code() == 429 || code() >= 500
        else -> false
    }

    private suspend fun enqueuePendingAction(
        actionType: String,
        taskId: String,
        payloadJson: String,
        changedFields: String = "legacy",
        generation: Long? = null,
    ) {
        val scope = requireNotNull(connectionScope.value) {
            "接続先が未確認のため、保留中の変更として保存できません。Notionの接続設定を確認してください。"
        }
        // 同じ操作は最新の状態だけを残し、オフライン中の連打でキューを肥大化させない。
        pendingSyncActionDao.deletePendingActionsForTask(actionType, taskId, scope)
        val resolvedGeneration = generation ?: maxOf(
            pendingSyncActionDao.getLatestGeneration(taskId, scope) ?: 0L,
            taskFieldGenerationDao.getLatestGeneration(taskId, scope) ?: 0L,
        ) + 1L
        val fields = changedFields.split(',').map { it.trim() }.filter { it.isNotBlank() && it != "legacy" }
        taskFieldGenerationDao.upsertGenerations(fields.map { TaskFieldGenerationEntity(taskId, scope, it, resolvedGeneration) })
        pendingSyncActionDao.insertPendingAction(
            PendingSyncActionEntity(
                actionType = actionType,
                taskId = taskId,
                payloadJson = payloadJson,
                timestamp = System.currentTimeMillis(),
                connectionScope = scope,
                operationId = UUID.randomUUID().toString(),
                changedFields = changedFields,
                generation = resolvedGeneration,
            ),
        )
    }

    private suspend fun advanceFieldGeneration(taskId: String, changedFields: String): Long {
        val scope = requireNotNull(connectionScope.value)
        val generation = maxOf(
            pendingSyncActionDao.getLatestGeneration(taskId, scope) ?: 0L,
            taskFieldGenerationDao.getLatestGeneration(taskId, scope) ?: 0L,
        ) + 1L
        val fields = changedFields.split(',').map { it.trim() }.filter { it.isNotBlank() && it != "legacy" }
        taskFieldGenerationDao.upsertGenerations(fields.map { TaskFieldGenerationEntity(taskId, scope, it, generation) })
        return generation
    }

    private suspend fun isSuperseded(action: PendingSyncActionEntity, field: String, scope: String): Boolean =
        action.changedFields != "legacy" && (taskFieldGenerationDao.getFieldGeneration(action.taskId, scope, field) ?: action.generation) > action.generation

    /**
     * Keeps only fields with an unresolved local intent while still applying fresh remote
     * values for every other field. Legacy actions have no field metadata, so they retain
     * the entire local row until a user resolves them.
     */
    private suspend fun mergeRemoteWithPendingLocalFields(
        remote: TaskEntity,
        pendingActions: List<PendingSyncActionEntity>,
    ): TaskEntity {
        val taskActions = pendingActions.filter { it.taskId == remote.id }
        if (taskActions.isEmpty()) return remote

        val local = taskDao.getTaskById(remote.id)
            ?.takeIf { it.connectionScope == remote.connectionScope }
            ?: return remote
        if (taskActions.any { it.changedFields == "legacy" }) return local

        val protectedFields = taskActions
            .flatMap { action -> action.changedFields.split(',') }
            .map { it.trim() }
            .toSet()
        if (protectedFields.isEmpty()) return remote

        return remote.copy(
            title = if ("title" in protectedFields) local.title else remote.title,
            status = if ("status" in protectedFields) local.status else remote.status,
            category = if ("category" in protectedFields) local.category else remote.category,
            dueDate = if ("dueDate" in protectedFields) local.dueDate else remote.dueDate,
            scheduledDate = if ("scheduledDate" in protectedFields) local.scheduledDate else remote.scheduledDate,
            statusColor = if ("status" in protectedFields) local.statusColor else remote.statusColor,
            categoryColor = if ("category" in protectedFields) local.categoryColor else remote.categoryColor,
            lastEditedTime = maxOf(local.lastEditedTime, remote.lastEditedTime),
        )
    }

    // 設定画面から構成された動的なマッピングキー
    var propTitleName: String = "名前"
    var propStatusName: String = "状態"
    var propStatusType: String = "status" // "status" (ステータス) または "select" (セレクト)
    var propStatusUnstartedName: String = "未着手"
    var propStatusInProgressName: String = "進行中"
    var propStatusCompletedName: String = "完了"
    var propCategoryName: String = "種類"
    var propScheduledDateName: String = "予定日"
    var propDueDateName: String = "締め切り"
    private var configuredDataSourceId: String = ""

    fun updateDataSourceId(dataSourceId: String) {
        configuredDataSourceId = dataSourceId.trim()
        verifiedClientIdentifierDataSourceId = null
    }

    /** Switches the visible and sendable cache to one explicitly configured database. */
    fun updateConnectionScope(databaseId: String) {
        connectionScope.value = databaseId.trim().ifBlank { null }
    }

    /** Returns the Notion page ID for a locally generated ID, if this connection has resolved it. */
    suspend fun resolveTaskId(taskId: String): String {
        val scope = connectionScope.value ?: return taskId
        return taskIdAliasDao.getRemoteTaskId(taskId, scope) ?: taskId
    }

    /** Adds the identifier column once per connected data source without altering other properties. */
    private suspend fun ensureClientIdentifierProperty(token: String, dataSourceId: String) {
        if (verifiedClientIdentifierDataSourceId == dataSourceId) return

        val metadata = notionApi.getDataSource(token = "Bearer $token", dataSourceId = dataSourceId)
        val existing = metadata.properties[CLIENT_IDENTIFIER_PROPERTY]
        when {
            existing == null -> notionApi.updateDataSource(
                token = "Bearer $token",
                dataSourceId = dataSourceId,
                request = NotionDataSourceUpdateRequest(
                    properties = mapOf(
                        CLIENT_IDENTIFIER_PROPERTY to buildJsonObject {
                            put("rich_text", buildJsonObject {})
                        },
                    ),
                ),
            )
            existing.type != "rich_text" -> throw IllegalArgumentException(
                "Notionの「$CLIENT_IDENTIFIER_PROPERTY」プロパティはリッチテキスト型である必要があります。型を変更せず、別名のプロパティを作成してください。",
            )
        }
        verifiedClientIdentifierDataSourceId = dataSourceId
    }

    private suspend fun findCreatedPageByClientIdentifier(
        token: String,
        dataSourceId: String,
        clientIdentifier: String,
    ): com.notiontasks.app.data.remote.dto.NotionPage? {
        val response = notionApi.queryDatabase(
            token = "Bearer $token",
            dataSourceId = dataSourceId,
            request = NotionQueryRequest(
                filter = buildJsonObject {
                    put("property", CLIENT_IDENTIFIER_PROPERTY)
                    put("rich_text", buildJsonObject { put("equals", clientIdentifier) })
                },
                pageSize = 2,
            ),
        )
        if (response.results.size > 1) {
            throw IllegalStateException("同じNotion Tasker IDを持つタスクが複数あります。自動再送を停止しました。")
        }
        return response.results.singleOrNull()
    }

    private suspend fun resolveDataSourceId(token: String, databaseId: String): String {
        if (configuredDataSourceId.isNotBlank()) return configuredDataSourceId

        val sources = notionApi.getDatabaseDataSources(token = "Bearer $token", databaseId = databaseId).dataSources
        return when (sources.size) {
            1 -> sources.single().id.also { configuredDataSourceId = it }
            0 -> throw IllegalArgumentException("このデータベースには利用可能なデータソースがありません。Notion側の共有設定を確認してください。")
            else -> throw IllegalArgumentException("このデータベースには複数のデータソースがあります。設定画面でデータソース ID を指定してください。")
        }
    }

    fun updatePropertyMappings(
        title: String,
        status: String,
        statusType: String,
        statusUnstarted: String,
        statusInProgress: String,
        statusCompleted: String,
        category: String,
        scheduledDate: String,
        dueDate: String,
    ) {
        propTitleName = title.ifBlank { "名前" }
        propStatusName = status.ifBlank { "状態" }
        propStatusType = if (statusType == "select") "select" else "status"
        propStatusUnstartedName = statusUnstarted.ifBlank { "未着手" }
        propStatusInProgressName = statusInProgress.ifBlank { "進行中" }
        propStatusCompletedName = statusCompleted.ifBlank { "完了" }
        // 種類・予定日・締め切りは任意マッピング。空ならNotion APIの
        // プロパティ操作から完全に除外する。
        propCategoryName = category.trim()
        propScheduledDateName = scheduledDate.trim()
        propDueDateName = dueDate.trim()
    }

    // 信頼できる唯一の情報源（SSOT）としてのメインのローカルフロー。Entity をドメインモデルに変換します
    val allTasks: Flow<List<TaskModel>> = combine(taskDao.getAllTasksFlow(), connectionScope) { entities, scope ->
            entities.asSequence()
                // When connection settings are unavailable after restore, retain only legacy/
                // unowned cache rows for read-only recovery.  Once a connection is selected,
                // never mix those rows into a verified connection's cache.
                .filter { entity ->
                    if (scope == null) entity.connectionScope == null else entity.connectionScope == scope
                }
                .map { entity ->
                TaskModel(
                    id = entity.id,
                    title = entity.title,
                    status = entity.status,
                    category = entity.category,
                    dueDate = entity.dueDate,
                    scheduledDate = entity.scheduledDate,
                    statusColor = entity.statusColor,
                    categoryColor = entity.categoryColor,
                )
            }
                .toList()
        }
        .flowOn(Dispatchers.Default)

    val pendingActionCount: Flow<Int> = combine(pendingSyncActionDao.getAllPendingActionsFlow(), connectionScope) { actions, scope ->
        actions.count { scope != null && it.connectionScope == scope }
    }
        .flowOn(Dispatchers.Default)

    /** Failed actions remain visible and require an explicit retry or discard decision. */
    val permanentFailures: Flow<List<PendingSyncActionEntity>> = combine(
        pendingSyncActionDao.getPermanentFailuresFlow(),
        connectionScope,
    ) { actions, scope -> actions.filter { scope != null && it.connectionScope == scope } }
        .flowOn(Dispatchers.Default)

    suspend fun retryPermanentFailure(id: Long): Boolean {
        val scope = connectionScope.value ?: return false
        val action = pendingSyncActionDao.getAllPendingActions().firstOrNull { it.id == id }
            ?: return false
        if (action.state != "PERMANENT_FAILURE" || action.connectionScope != scope) return false
        return pendingSyncActionDao.retryPermanentFailure(id, scope) > 0
    }

    suspend fun discardPermanentFailure(id: Long): Boolean {
        val action = pendingSyncActionDao.getAllPendingActions().firstOrNull { it.id == id }
            ?: return false
        if (action.state != "PERMANENT_FAILURE" || action.connectionScope != connectionScope.value) return false
        pendingSyncActionDao.deletePendingActionById(id)
        return true
    }

    fun getTaskFlowById(id: String): Flow<TaskModel?> = combine(taskDao.getTaskFlowById(id), connectionScope) { entity, scope ->
            entity?.takeIf { scope != null && it.connectionScope == scope }?.let {
                TaskModel(
                    id = it.id,
                    title = it.title,
                    status = it.status,
                    category = it.category,
                    dueDate = it.dueDate,
                    scheduledDate = it.scheduledDate,
                    statusColor = it.statusColor,
                    categoryColor = it.categoryColor,
                )
            }
        }
        .flowOn(Dispatchers.Default)

    suspend fun getDatabaseMetadata(token: String, databaseId: String): NotionDatabaseResponse {
        val authHeader = "Bearer $token"
        val dataSourceId = resolveDataSourceId(token, databaseId)
        val meta = notionApi.getDataSource(token = authHeader, dataSourceId = dataSourceId)
        
        // 自己修復：実際のデータベースプロパティの型を自動的に検査し、select と status の不一致を解消します
        meta.properties[propStatusName]?.let { statusProp ->
            if (statusProp.type == "select") {
                propStatusType = "select"
            } else if (statusProp.type == "status") {
                propStatusType = "status"
            }
        }
        return meta
    }

    // リモートの Notion DB からタスクのキャッシュを更新し、保留中のオフラインアクションを同期する
    suspend fun syncTasks(token: String, databaseId: String, incremental: Boolean = true) = syncMutex.withLock {
        updateConnectionScope(databaseId)
        val authHeader = "Bearer $token"
        val dataSourceId = resolveDataSourceId(token, databaseId)
        
        // 1. まずオフライン中の変更があれば順次処理を試みる
        processPendingSyncActionsInternal(token, dataSourceId)
        // A retryable local operation is newer than any fetched remote snapshot. Keep its
        // task row unchanged until that operation is confirmed, including during a full sync.
        val pendingActions = pendingSyncActionDao.getAllPendingActions()
            .filter { it.connectionScope == databaseId }
        val protectedTaskIds = pendingActions
            .map { it.taskId }
            .toSet()

        // 2. リモートから最新データを取得
        try {
            val allFetchedEntities = mutableListOf<TaskEntity>()
            var hasMore = true
            var nextCursor: String? = null
            
            // 起動直後と6時間ごとには全件同期し、Notion側で削除・アーカイブされた
            // タスクもローカルキャッシュから確実に除去する。
            val now = System.currentTimeMillis()
            val performFullSync = !incremental || now - lastFullSyncAt >= FULL_SYNC_INTERVAL_MS
            // 楽観的な端末更新時刻は Notion の時刻ではないため、差分取得の基準には使わない。
            val cursorKey = remoteSyncCursorKey(databaseId)
            val lastSync = if (performFullSync) 0L else syncMetadataDao.getTimestamp(cursorKey) ?: 0L
            var latestRemoteEditTime = lastSync

            while (hasMore) {
                // 差分同期用のフィルタ構築
                val filter = if (lastSync > 0) {
                    buildJsonObject {
                        put("timestamp", "last_edited_time")
                        put("last_edited_time", buildJsonObject {
                            put("after", java.time.Instant.ofEpochMilli(lastSync).toString())
                        })
                    }
                } else null

            val sorts = buildList {
                if (propScheduledDateName.isNotBlank()) add(NotionSort(property = propScheduledDateName, direction = "ascending"))
                if (propDueDateName.isNotBlank()) add(NotionSort(property = propDueDateName, direction = "ascending"))
            }

                val request = NotionQueryRequest(
                    filter = filter,
                    sorts = sorts,
                    startCursor = nextCursor,
                    pageSize = 100
                )
                val response = notionApi.queryDatabase(token = authHeader, dataSourceId = dataSourceId, request = request)
                
                val pageEntities = response.results.map { page ->
                    require(page.properties.containsKey(propTitleName)) {
                        "タイトルのプロパティ「$propTitleName」がNotionデータベースに見つかりません。プロパティマッピングを確認してください。"
                    }
                    val title = page.properties.getTitleText(propTitleName)
                        ?: throw IllegalArgumentException("タイトルのプロパティ「$propTitleName」を読み取れません。")
                    
                    val statusValue = (page.properties.getStatusText(propStatusName)
                        ?: page.properties.getSelectText(propStatusName)
                        ?: "未着手").trim()
                        
                    val categoryValue = (page.properties.getSelectText(propCategoryName) ?: "他").trim()
                    
                    val statusColorVal = page.properties.getStatusColor(propStatusName)
                        ?: page.properties.getSelectColor(propStatusName)
                    val categoryColorVal = page.properties.getSelectColor(propCategoryName)

                    val editedTime = try {
                        page.lastEditedTime?.let { java.time.Instant.parse(it).toEpochMilli() } ?: 0L
                    } catch (_: Exception) { 0L }
                    latestRemoteEditTime = maxOf(latestRemoteEditTime, editedTime)

                    TaskEntity(
                        id = page.id,
                        title = title,
                        status = statusValue,
                        category = categoryValue,
                        dueDate = page.properties.getDateValue(propDueDateName),
                        scheduledDate = page.properties.getDateValue(propScheduledDateName),
                        statusColor = statusColorVal,
                        categoryColor = categoryColorVal,
                        lastEditedTime = editedTime,
                        connectionScope = databaseId,
                    )
                }
                allFetchedEntities.addAll(
                    pageEntities.map { remote ->
                        mergeRemoteWithPendingLocalFields(remote, pendingActions)
                    },
                )
                
                hasMore = response.hasMore
                nextCursor = response.nextCursor

                // レート制限 (3回/秒) を考慮したディレイ挿入
                if (hasMore) {
                    kotlinx.coroutines.delay(350)
                }
            }

            // 3. トランザクションを用いて更新
            // 差分同期の場合は upsert のみを行い、全件同期の場合のみ不要データを削除する
            if (!performFullSync && lastSync > 0) {
                taskDao.upsertTasks(allFetchedEntities)
            } else {
                taskDao.syncTasksTransactionally(
                    connectionScope = databaseId,
                    activeEntities = allFetchedEntities,
                    protectedTaskIds = protectedTaskIds.toList(),
                )
                lastFullSyncAt = now
            }
            if (latestRemoteEditTime > 0L) {
                syncMetadataDao.put(SyncMetadataEntity(cursorKey, latestRemoteEditTime))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Network synchronization failed: ${e.message}", e)
        }
    }

    /**
     * ローカルの完了済みタスクをクリーンアップします。
     * @param keepType "date" または "count"
     * @param keepValue 期間(月) または 最大保持件数
     */
    suspend fun cleanupCompletedTasks(keepType: String, keepValue: Int) {
        val completed = propStatusCompletedName
        val scope = connectionScope.value ?: return
        val protectedIds = pendingSyncActionDao.getAllPendingActions()
            .filter { it.connectionScope == scope }
            .map { it.taskId }
        when (keepType) {
            "date" -> {
                if (keepValue > 0) {
                    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(com.notiontasks.app.utils.JapanTime.zoneId.id))
                    cal.add(java.util.Calendar.MONTH, -keepValue)
                    taskDao.deleteCompletedTasksBefore(scope, completed, cal.timeInMillis, protectedIds)
                }
            }
            "count" -> {
                if (keepValue > 0) {
                    taskDao.keepOnlyLatestCompletedTasks(scope, completed, keepValue, protectedIds)
                }
            }
        }
    }



    // 直接的なローカル更新を伴う PATCH リクエストによる高性能なステータス遷移の更新
    suspend fun updateTaskStatus(
        token: String,
        pageId: String,
        newStatus: String,
        newStatusColor: String? = null,
    ): TaskSaveResult = syncMutex.withLock {
        val resolvedPageId = resolveTaskId(pageId)
        val actionGeneration = advanceFieldGeneration(resolvedPageId, "status")
        val trimmedStatus = newStatus.trim()
        // 楽観的な UI/ローカルの更新
        val statusColor = newStatusColor ?: taskDao.getStatusColorForStatus(trimmedStatus)
        val localEditTime = System.currentTimeMillis()
        taskDao.updateTaskStatusLocal(resolvedPageId, trimmedStatus, statusColor, localEditTime)

        // リモートの Patch 呼び出しを実行する
        val authHeader = "Bearer $token"
        
        try {
            val request = NotionUpdateRequest(
                properties = mapOf(
                    propStatusName to buildJsonObject {
                        put(
                            propStatusType,
                            buildJsonObject {
                                put("name", trimmedStatus)
                            }
                        )
                    },
                )
            )
            val updatedPage = notionApi.updatePage(token = authHeader, pageId = resolvedPageId, request = request)
            
            // APIからの最新の色情報を反映させる（Notion側での変更を追従）
            val finalStatusColor = updatedPage.properties.getStatusColor(propStatusName)
            if (finalStatusColor != null) {
                taskDao.updateTaskStatusLocal(resolvedPageId, trimmedStatus, finalStatusColor, localEditTime)
            }
        } catch (firstFailure: Exception) {
            if (firstFailure is CancellationException) throw firstFailure
            if (firstFailure.isRetryableSyncFailure()) {
                // 接続断・429・サーバー障害では最初の PATCH が到達したか判別できない。
                // 型を替えた再送は同じ意図を二度適用し得るため、元の操作だけを保留する。
                val actionPayload = buildJsonObject {
                    put("newStatus", trimmedStatus)
                }.toString()
                enqueuePendingAction("UPDATE_STATUS", resolvedPageId, actionPayload, changedFields = "status", generation = actionGeneration)
                return@withLock TaskSaveResult.Pending
            }
            // 自己修復セーフティネット：最初の更新が失敗した場合、別の型を試してフォールバックする
            val alternateType = if (propStatusType == "select") "status" else "select"
            try {
                val retryRequest = NotionUpdateRequest(
                    properties = mapOf(
                        propStatusName to buildJsonObject {
                            put(
                                alternateType,
                                buildJsonObject {
                                    put("name", trimmedStatus)
                                },
                            )
                        },
                    ),
                )
                val updatedPage = notionApi.updatePage(token = authHeader, pageId = resolvedPageId, request = retryRequest)
                // 再試行が成功した場合は、構成をシームレスに更新します
                propStatusType = alternateType
                
                val finalStatusColor = if (alternateType == "status") {
                    updatedPage.properties.getStatusColor(propStatusName)
                } else {
                    updatedPage.properties.getSelectColor(propStatusName)
                }
                if (finalStatusColor != null) {
                    taskDao.updateTaskStatusLocal(resolvedPageId, trimmedStatus, finalStatusColor, localEditTime)
                }
            } catch (retryFailure: Exception) {
                if (retryFailure is CancellationException) throw retryFailure
                if (!retryFailure.isRetryableSyncFailure()) throw retryFailure

                // 一時的な通信・サーバー障害だけをキューに保存して次回自動リトライする。
                val actionPayload = buildJsonObject {
                    put("newStatus", trimmedStatus)
                }.toString()
                enqueuePendingAction("UPDATE_STATUS", resolvedPageId, actionPayload, changedFields = "status", generation = actionGeneration)
                return@withLock TaskSaveResult.Pending
            }
        }
        TaskSaveResult.Confirmed
    }

    suspend fun updateTask(
        token: String,
        pageId: String,
        title: String,
        status: String,
        category: String,
        dueDate: String?,
        scheduledDate: String?,
        statusColor: String? = null,
        categoryColor: String? = null,
    ): TaskSaveResult = syncMutex.withLock {
        val resolvedPageId = resolveTaskId(pageId)
        val actionGeneration = advanceFieldGeneration(resolvedPageId, "title,status,category,dueDate,scheduledDate")
        val trimmedStatus = status.trim()
        val trimmedCategory = category.trim()
        // 楽観的な UI/ローカルの更新
        val currentLocalTask = taskDao.getTaskById(resolvedPageId)
        val finalStatusColor = statusColor ?: if (currentLocalTask?.status == trimmedStatus) {
            currentLocalTask.statusColor
        } else {
            taskDao.getStatusColorForStatus(trimmedStatus)
        }
        val finalCategoryColor = categoryColor ?: if (currentLocalTask?.category == trimmedCategory) {
            currentLocalTask.categoryColor
        } else {
            taskDao.getCategoryColorForCategory(trimmedCategory)
        }
        val updatedLocalRef = TaskEntity(
            id = resolvedPageId,
            title = title,
            status = trimmedStatus,
            category = trimmedCategory,
            dueDate = dueDate,
            scheduledDate = scheduledDate,
            statusColor = finalStatusColor,
            categoryColor = finalCategoryColor,
            lastEditedTime = System.currentTimeMillis(),
            connectionScope = connectionScope.value,
        )
        taskDao.upsertTasks(listOf(updatedLocalRef))

        // リクエストプロパティのペイロードを構築するためのヘルパー
        fun buildPropertiesPayload(sType: String): Map<String, JsonElement> {
            val properties = mutableMapOf<String, JsonElement>()
            
            properties[propTitleName] = buildJsonObject {
                put(
                    "title",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "text",
                                    buildJsonObject {
                                        put("content", title)
                                    },
                                )
                            },
                        )
                    },
                )
            }

            properties[propStatusName] = buildJsonObject {
                put(
                    sType,
                    buildJsonObject {
                        put("name", trimmedStatus)
                    },
                )
            }
            
            if (propCategoryName.isNotBlank()) {
                properties[propCategoryName] = buildJsonObject {
                    if (trimmedCategory.isBlank()) {
                        put("select", JsonNull)
                    } else {
                        put(
                            "select",
                            buildJsonObject {
                                put("name", trimmedCategory)
                            },
                        )
                    }
                }
            }
            
            if (propDueDateName.isNotBlank()) {
                properties[propDueDateName] = buildJsonObject {
                    if (dueDate.isNullOrBlank()) {
                        put("date", JsonNull)
                    } else {
                        put("date", buildJsonObject { put("start", dueDate) })
                    }
                }
            }

            if (propScheduledDateName.isNotBlank()) {
                properties[propScheduledDateName] = buildJsonObject {
                    if (scheduledDate.isNullOrBlank()) {
                        put("date", JsonNull)
                    } else {
                        put("date", buildJsonObject { put("start", scheduledDate) })
                    }
                }
            }
            return properties
        }

        // フォールバックメカニズムを使用したリモートの Patch 呼び出しの実行
        val authHeader = "Bearer $token"
        try {
            val payload = try {
                buildPropertiesPayload(propStatusType)
            } catch (_: Exception) {
                val alternateType = if (propStatusType == "select") "status" else "select"
                propStatusType = alternateType
                buildPropertiesPayload(alternateType)
            }
            
            val updatedPage = notionApi.updatePage(token = authHeader, pageId = resolvedPageId, request = NotionUpdateRequest(properties = payload))
            
            // APIからの最新の色情報を反映させる
            val apiStatusColor = if (propStatusType == "status") {
                updatedPage.properties.getStatusColor(propStatusName)
            } else {
                updatedPage.properties.getSelectColor(propStatusName)
            }
            val apiCategoryColor = propCategoryName.takeIf { it.isNotBlank() }?.let { updatedPage.properties.getSelectColor(it) }
            
            if (apiStatusColor != null || apiCategoryColor != null) {
                val refreshedEntity = updatedLocalRef.copy(
                    statusColor = apiStatusColor ?: updatedLocalRef.statusColor,
                    categoryColor = apiCategoryColor ?: updatedLocalRef.categoryColor
                )
                taskDao.upsertTasks(listOf(refreshedEntity))
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (!failure.isRetryableSyncFailure()) throw failure

            val actionPayload = buildJsonObject {
                put("title", title)
                put("status", status)
                put("category", category)
                dueDate?.let { put("dueDate", it) }
                scheduledDate?.let { put("scheduledDate", it) }
            }.toString()
            enqueuePendingAction("UPDATE_TASK", resolvedPageId, actionPayload, changedFields = "title,status,category,dueDate,scheduledDate", generation = actionGeneration)
            return@withLock TaskSaveResult.Pending
        }
        TaskSaveResult.Confirmed
    }

    suspend fun createTask(
        token: String,
        databaseId: String,
        title: String,
        status: String,
        category: String,
        dueDate: String?,
        scheduledDate: String?,
        statusColor: String? = null,
        categoryColor: String? = null,
    ): TaskSaveResult = syncMutex.withLock {
        val trimmedStatus = status.trim()
        val trimmedCategory = category.trim()
        val authHeader = "Bearer $token"
        var dataSourceId = ""
        val clientIdentifier = UUID.randomUUID().toString()
        
        fun buildCreatePayload(sType: String): NotionCreateRequest {
            val properties = mutableMapOf<String, PropertyUpdate>()
            properties[propTitleName] = PropertyUpdate(title = listOf(RichTextObject(text = TextContent(content = title))))
            properties[propStatusName] = if (sType == "select") {
                PropertyUpdate(select = SelectValue(name = trimmedStatus))
            } else {
                PropertyUpdate(status = StatusValue(name = trimmedStatus))
            }
            if (propCategoryName.isNotBlank() && trimmedCategory.isNotBlank()) {
                properties[propCategoryName] = PropertyUpdate(select = SelectValue(name = trimmedCategory))
            }
            
            if (propDueDateName.isNotBlank() && !dueDate.isNullOrBlank()) {
                properties[propDueDateName] = PropertyUpdate(date = DateValue(start = dueDate))
            }
            if (propScheduledDateName.isNotBlank() && !scheduledDate.isNullOrBlank()) {
                properties[propScheduledDateName] = PropertyUpdate(date = DateValue(start = scheduledDate))
            }
            properties[CLIENT_IDENTIFIER_PROPERTY] = PropertyUpdate(
                richText = listOf(RichTextObject(text = TextContent(content = clientIdentifier))),
            )

            return NotionCreateRequest(
                parent = DatabaseParent(dataSourceId = dataSourceId),
                properties = properties
            )
        }

        try {
            dataSourceId = resolveDataSourceId(token, databaseId)
            ensureClientIdentifierProperty(token, dataSourceId)
            val request = buildCreatePayload(propStatusType)
            val createdPage = notionApi.createPage(token = authHeader, request = request)
            
            // APIから返された実際の色情報を取得
            val apiStatusColor = if (propStatusType == "status") {
                createdPage.properties.getStatusColor(propStatusName)
            } else {
                createdPage.properties.getSelectColor(propStatusName)
            }
            val apiCategoryColor = propCategoryName.takeIf { it.isNotBlank() }?.let { createdPage.properties.getSelectColor(it) }

            val localEntity = TaskEntity(
                id = createdPage.id,
                title = title,
                status = trimmedStatus,
                category = trimmedCategory,
                dueDate = dueDate,
                scheduledDate = scheduledDate,
                statusColor = apiStatusColor ?: statusColor,
                categoryColor = apiCategoryColor ?: categoryColor,
                lastEditedTime = System.currentTimeMillis(),
                connectionScope = connectionScope.value,
            )
            taskDao.upsertTasks(listOf(localEntity))
        } catch (firstFailure: Exception) {
            if (firstFailure is CancellationException) throw firstFailure
            if (dataSourceId.isBlank()) {
                if (!firstFailure.isRetryableSyncFailure()) throw firstFailure
                val tempId = "pending_$clientIdentifier"
                taskDao.upsertTasks(listOf(
                    TaskEntity(
                        id = tempId,
                        title = title,
                        status = trimmedStatus,
                        category = trimmedCategory,
                        dueDate = dueDate,
                        scheduledDate = scheduledDate,
                        statusColor = statusColor,
                        categoryColor = categoryColor,
                        lastEditedTime = System.currentTimeMillis(),
                        connectionScope = connectionScope.value,
                    ),
                ))
                val actionPayload = buildJsonObject {
                    put("title", title)
                    put("status", trimmedStatus)
                    put("category", trimmedCategory)
                    put("clientIdentifier", clientIdentifier)
                    dueDate?.let { put("dueDate", it) }
                    scheduledDate?.let { put("scheduledDate", it) }
                }.toString()
                enqueuePendingAction("CREATE_TASK", tempId, actionPayload, changedFields = "title,status,category,dueDate,scheduledDate")
                return@withLock TaskSaveResult.Pending
            }
            if (!firstFailure.isRetryableSyncFailure()) throw firstFailure

            // 応答喪失・429・5xx ではリモート作成済みかを判定できない。別の status/select
            // 型で再POSTすると重複作成し得るため、元の意図を一度だけローカルに保留する。
            val tempId = "pending_$clientIdentifier"
            val localEntity = TaskEntity(
                id = tempId,
                title = title,
                status = trimmedStatus,
                category = trimmedCategory,
                dueDate = dueDate,
                scheduledDate = scheduledDate,
                statusColor = statusColor,
                categoryColor = categoryColor,
                lastEditedTime = System.currentTimeMillis(),
                connectionScope = connectionScope.value,
            )
            taskDao.upsertTasks(listOf(localEntity))

            val actionPayload = buildJsonObject {
                put("title", title)
                put("status", trimmedStatus)
                put("category", trimmedCategory)
                    put("clientIdentifier", clientIdentifier)
                dueDate?.let { put("dueDate", it) }
                scheduledDate?.let { put("scheduledDate", it) }
            }.toString()
            enqueuePendingAction("CREATE_TASK", tempId, actionPayload, changedFields = "title,status,category,dueDate,scheduledDate")
            // A lost create response can already have created the page.  Query by the stable
            // identifier before one automatic retry; network failure simply leaves it queued.
            processPendingSyncActionsInternal(token, dataSourceId)
            return@withLock TaskSaveResult.Pending
        }
        TaskSaveResult.Confirmed
    }

    // 保留中のオフライン同期アクションを順次処理
    private suspend fun processPendingSyncActionsInternal(token: String, dataSourceId: String) {
        val scope = connectionScope.value ?: return
        val pendingActions = pendingSyncActionDao.getAllPendingActions()
            .filter { it.connectionScope == scope }
        if (pendingActions.isEmpty()) return

        val authHeader = "Bearer $token"
        // 仮IDから本番IDへのマッピング
        val idMapping = mutableMapOf<String, String>()
        val blockedTaskIds = mutableSetOf<String>()

        for (action in pendingActions) {
            if (action.taskId in blockedTaskIds) continue
            try {
                // 各アクションを独立して処理し、1つのエラーが全体を止めないようにする
                val taskId = idMapping[action.taskId] ?: resolveTaskId(action.taskId)
                val payloadJson = action.payloadJson
                val payloadObj = json.parseToJsonElement(payloadJson).jsonObject
                
                when (action.actionType) {
                    "UPDATE_STATUS" -> {
                        if (isSuperseded(action, "status", scope)) {
                            pendingSyncActionDao.deletePendingActionById(action.id)
                            continue
                        }
                        val newStatus = payloadObj["newStatus"]?.jsonPrimitive?.content
                            ?: throw IllegalArgumentException("UPDATE_STATUS action is missing newStatus")
                        val request = NotionUpdateRequest(
                            properties = mapOf(
                                propStatusName to buildJsonObject {
                                    put(propStatusType, buildJsonObject { put("name", newStatus) })
                                }
                            )
                        )
                        notionApi.updatePage(token = authHeader, pageId = taskId, request = request)
                    }
                    "UPDATE_TASK" -> {
                        val title = payloadObj["title"]?.jsonPrimitive?.content ?: ""
                        val status = payloadObj["status"]?.jsonPrimitive?.content ?: ""
                        val category = payloadObj["category"]?.jsonPrimitive?.content ?: ""
                        val dueDate = payloadObj["dueDate"]?.jsonPrimitive?.contentOrNull
                        val scheduledDate = payloadObj["scheduledDate"]?.jsonPrimitive?.contentOrNull

                        val properties = mutableMapOf<String, JsonElement>()
                        if (!isSuperseded(action, "title", scope)) properties[propTitleName] = buildJsonObject {
                            put(
                                "title",
                                buildJsonArray {
                                    add(buildJsonObject { put("text", buildJsonObject { put("content", title) }) })
                                },
                            )
                        }
                        if (!isSuperseded(action, "status", scope)) properties[propStatusName] = buildJsonObject {
                            put(propStatusType, buildJsonObject { put("name", status) })
                        }
                        if (propCategoryName.isNotBlank() && !isSuperseded(action, "category", scope)) {
                            properties[propCategoryName] = buildJsonObject {
                                if (category.isBlank()) put("select", JsonNull) else put("select", buildJsonObject { put("name", category) })
                            }
                        }
                        if (propDueDateName.isNotBlank() && !isSuperseded(action, "dueDate", scope)) {
                            properties[propDueDateName] = buildJsonObject {
                                if (dueDate.isNullOrBlank()) put("date", JsonNull) else put("date", buildJsonObject { put("start", dueDate) })
                            }
                        }
                        if (propScheduledDateName.isNotBlank() && !isSuperseded(action, "scheduledDate", scope)) {
                            properties[propScheduledDateName] = buildJsonObject {
                                if (scheduledDate.isNullOrBlank()) put("date", JsonNull) else put("date", buildJsonObject { put("start", scheduledDate) })
                            }
                        }

                        if (properties.isNotEmpty()) notionApi.updatePage(token = authHeader, pageId = taskId, request = NotionUpdateRequest(properties = properties))
                    }
                    "CREATE_TASK" -> {
                        val title = payloadObj["title"]?.jsonPrimitive?.content ?: ""
                        val status = payloadObj["status"]?.jsonPrimitive?.content ?: ""
                        val category = payloadObj["category"]?.jsonPrimitive?.content ?: ""
                        val dueDate = payloadObj["dueDate"]?.jsonPrimitive?.contentOrNull
                        val scheduledDate = payloadObj["scheduledDate"]?.jsonPrimitive?.contentOrNull
                        val clientIdentifier = payloadObj["clientIdentifier"]?.jsonPrimitive?.contentOrNull
                            ?: throw PendingSyncActionException(
                                "識別子のない過去のタスク作成は重複防止のため自動再送しません。保留中の変更を確認してください。",
                                IllegalStateException("CREATE_TASK is missing clientIdentifier"),
                            )

                        ensureClientIdentifierProperty(token, dataSourceId)
                        val existing = findCreatedPageByClientIdentifier(token, dataSourceId, clientIdentifier)

                        val propMap = mutableMapOf<String, PropertyUpdate>()
                        propMap[propTitleName] = PropertyUpdate(title = listOf(RichTextObject(text = TextContent(content = title))))
                        propMap[propStatusName] = if (propStatusType == "select") PropertyUpdate(select = SelectValue(name = status)) else PropertyUpdate(status = StatusValue(name = status))
                        if (propCategoryName.isNotBlank() && category.isNotBlank()) propMap[propCategoryName] = PropertyUpdate(select = SelectValue(name = category))
                        if (propDueDateName.isNotBlank() && !dueDate.isNullOrBlank()) propMap[propDueDateName] = PropertyUpdate(date = DateValue(start = dueDate))
                        if (propScheduledDateName.isNotBlank() && !scheduledDate.isNullOrBlank()) propMap[propScheduledDateName] = PropertyUpdate(date = DateValue(start = scheduledDate))
                        propMap[CLIENT_IDENTIFIER_PROPERTY] = PropertyUpdate(
                            richText = listOf(RichTextObject(text = TextContent(content = clientIdentifier))),
                        )

                        val created = existing ?: notionApi.createPage(
                            token = authHeader,
                            request = NotionCreateRequest(
                                parent = DatabaseParent(dataSourceId = dataSourceId),
                                properties = propMap,
                            ),
                        )
                        
                        // Preserve the latest local edit made before CREATE completed.  Its
                        // queued UPDATE is also rewritten below, so it survives a restart.
                        val latestLocalTask = taskDao.getTaskById(action.taskId)
                        val resolvedTask = TaskEntity(
                            id = created.id,
                            title = latestLocalTask?.title ?: title,
                            status = latestLocalTask?.status ?: status,
                            category = latestLocalTask?.category ?: category,
                            dueDate = latestLocalTask?.dueDate ?: dueDate,
                            scheduledDate = latestLocalTask?.scheduledDate ?: scheduledDate,
                            statusColor = latestLocalTask?.statusColor,
                            categoryColor = latestLocalTask?.categoryColor,
                            lastEditedTime = latestLocalTask?.lastEditedTime ?: System.currentTimeMillis(),
                            connectionScope = scope,
                        )

                        // IDマッピングを記録
                        idMapping[action.taskId] = created.id
                        taskIdAliasDao.resolveCreatedTask(
                            localTaskId = action.taskId,
                            remoteTask = resolvedTask,
                            connectionScope = scope,
                        )
                    }
                    else -> throw IllegalArgumentException("Unknown pending action type: ${action.actionType}")
                }
                // 成功した場合のみアクションを削除
                pendingSyncActionDao.deletePendingActionById(action.id)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure.isRetryableSyncFailure()) {
                    // 古い操作を追い越さないよう、通信・サーバー障害時は次の同期まで待つ。
                    pendingSyncActionDao.updatePendingActionState(
                        action.id,
                        state = "RETRYABLE_FAILURE",
                        lastError = failure.message,
                    )
                    break
                }

                // 認証・プロパティ設定の不整合でユーザーの操作を失わないよう、保留操作は残す。
                // 設定を直した後に同じ順序で再送できるよう、後続の操作もここで止める。
                pendingSyncActionDao.updatePendingActionState(
                    action.id,
                    state = "PERMANENT_FAILURE",
                    lastError = failure.message,
                )
                // Keep dependent operations for this task untouched, while independent tasks
                // and the subsequent remote read continue to make progress.
                blockedTaskIds += action.taskId
                continue
            }
        }
    }

    // SharedPreferences から Room への古い JSON マイグレーション
    suspend fun migrateLegacyPreferencesToRoom(
        sharedPreferences: SharedPreferences,
        scheduleRepository: ScheduleStore,
    ) = withContext(Dispatchers.IO) {
        val migratedKey = "has_migrated_legacy_prefs_to_room_v1"
        if (!sharedPreferences.getBoolean(migratedKey, false)) {
            try {
                // TimeBlocks のマイグレーション
                val rawTimeBlocksJson = sharedPreferences.getString("time_blocks_v2", null)
                if (!rawTimeBlocksJson.isNullOrBlank()) {
                    val blocks: List<TimeBlock> = json.decodeFromString(rawTimeBlocksJson)
                    scheduleRepository.saveTimeBlocks(blocks)
                }

                // LifeActivities のマイグレーション
                val rawLifeActivitiesJson = sharedPreferences.getString("life_activities_v2", null)
                if (!rawLifeActivitiesJson.isNullOrBlank()) {
                    val activities: List<LifeActivity> = json.decodeFromString(rawLifeActivitiesJson)
                    scheduleRepository.saveLifeActivities(activities)
                }

                sharedPreferences.edit { putBoolean(migratedKey, true) }
            } catch (_: Exception) {
                // エラー時はスキップして次回マイグレーションを再トライ
            }
        }
    }
}
