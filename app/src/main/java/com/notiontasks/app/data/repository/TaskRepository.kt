package com.notiontasks.app.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.notiontasks.app.data.local.PendingSyncActionDao
import com.notiontasks.app.data.local.PendingSyncActionEntity
import com.notiontasks.app.data.local.TaskDao
import com.notiontasks.app.data.local.TaskEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.IOException

// 動的な JSON 解析用の拡張機能
fun Map<String, JsonElement>.getTitleText(propertyName: String): String? {
    val element = this[propertyName] ?: return null
    return try {
        val titleArray = element.jsonObject["title"]?.jsonArray
        titleArray?.firstOrNull()?.jsonObject?.get("plain_text")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.getStatusText(propertyName: String): String? {
    val element = this[propertyName] ?: return null
    return try {
        element.jsonObject["status"]?.jsonObject?.get("name")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.getSelectText(propertyName: String): String? {
    val element = this[propertyName] ?: return null
    return try {
        element.jsonObject["select"]?.jsonObject?.get("name")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.getDateValue(propertyName: String): String? {
    val element = this[propertyName] ?: return null
    return try {
        element.jsonObject["date"]?.jsonObject?.get("start")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.getStatusColor(propertyName: String): String? {
    val element = this[propertyName] ?: return null
    return try {
        element.jsonObject["status"]?.jsonObject?.get("color")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.getSelectColor(propertyName: String): String? {
    val element = this[propertyName] ?: return null
    return try {
        element.jsonObject["select"]?.jsonObject?.get("color")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

class TaskRepository(
    private val notionApi: NotionApi,
    private val taskDao: TaskDao,
    private val pendingSyncActionDao: PendingSyncActionDao,
) {

    // 非同期処理およびリレーショナル整合性のための Mutex 排他制御
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    // 設定画面から構成された動的なマッピングキー
    var propTitleName: String = "名前"
    var propStatusName: String = "状態"
    var propStatusType: String = "status" // "status" (ステータス) または "select" (セレクト)
    var propCategoryName: String = "種類"
    var propScheduledDateName: String = "予定日"
    var propDueDateName: String = "締め切り"

    fun updatePropertyMappings(
        title: String,
        status: String,
        statusType: String,
        category: String,
        scheduledDate: String,
        dueDate: String,
    ) {
        propTitleName = title.ifBlank { "名前" }
        propStatusName = status.ifBlank { "状態" }
        propStatusType = if (statusType == "select") "select" else "status"
        propCategoryName = category.ifBlank { "種類" }
        propScheduledDateName = scheduledDate.ifBlank { "予定日" }
        propDueDateName = dueDate.ifBlank { "締め切り" }
    }

    // 信頼できる唯一の情報源（SSOT）としてのメインのローカルフロー。Entity をドメインモデルに変換します
    val allTasks: Flow<List<TaskModel>> = taskDao.getAllTasksFlow()
        .map { entities ->
            entities.map { entity ->
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
        }
        .flowOn(Dispatchers.Default)

    suspend fun getDatabaseMetadata(token: String, databaseId: String): NotionDatabaseResponse {
        val authHeader = "Bearer $token"
        val meta = notionApi.getDatabase(token = authHeader, databaseId = databaseId)
        
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
    suspend fun syncTasks(token: String, databaseId: String) = syncMutex.withLock {
        val authHeader = "Bearer $token"
        
        // 1. まずオフライン中の変更があれば順次処理を試みる
        try {
            processPendingSyncActionsInternal(token, databaseId)
        } catch (e: Exception) {
            // Pending sync actions failure is logged but we still try to sync tasks
            e.printStackTrace()
        }

        // 2. リモートから最新データを取得
        try {
            val response = notionApi.queryDatabase(token = authHeader, databaseId = databaseId)
            
            val activeEntities = response.results.mapNotNull { page ->
                val title = page.properties.getTitleText(propTitleName) ?: return@mapNotNull null
                
                val statusValue = page.properties.getStatusText(propStatusName)
                    ?: page.properties.getSelectText(propStatusName)
                    ?: "未着手"
                    
                val categoryValue = (page.properties.getSelectText(propCategoryName) ?: "他").trim()
                
                val statusColorVal = page.properties.getStatusColor(propStatusName)
                    ?: page.properties.getSelectColor(propStatusName)
                val categoryColorVal = page.properties.getSelectColor(propCategoryName)

                TaskEntity(
                    id = page.id,
                    title = title,
                    status = statusValue,
                    category = categoryValue,
                    dueDate = page.properties.getDateValue(propDueDateName),
                    scheduledDate = page.properties.getDateValue(propScheduledDateName),
                    statusColor = statusColorVal,
                    categoryColor = categoryColorVal,
                )
            }

            // 3. トランザクションを用いて差分更新（Upsert + 不要データの削除）
            taskDao.syncTasksTransactionally(activeEntities)
        } catch (e: Exception) {
            throw IOException("Network synchronization failed: ${e.message}", e)
        }
    }



    // 直接的なローカル更新を伴う PATCH リクエストによる高性能なステータス遷移の更新
    suspend fun updateTaskStatus(
        token: String,
        pageId: String,
        newStatus: String,
    ) = syncMutex.withLock {
        // 楽観的な UI/ローカルの更新
        val statusColor = taskDao.getStatusColorForStatus(newStatus)
        taskDao.updateTaskStatusLocal(pageId, newStatus, statusColor)

        // リモートの Patch 呼び出しを実行する
        val authHeader = "Bearer $token"
        
        try {
            val request = NotionUpdateRequest(
                properties = mapOf(
                    propStatusName to buildJsonObject {
                        put(
                            propStatusType,
                            buildJsonObject {
                                put("name", newStatus)
                            }
                        )
                    },
                )
            )
            notionApi.updatePage(token = authHeader, pageId = pageId, request = request)
        } catch (_: Exception) {
            // 自己修復セーフティネット：最初の更新が失敗した場合、別の型を試してフォールバックする
            val alternateType = if (propStatusType == "select") "status" else "select"
            try {
                val retryRequest = NotionUpdateRequest(
                    properties = mapOf(
                        propStatusName to buildJsonObject {
                            put(
                                alternateType,
                                buildJsonObject {
                                    put("name", newStatus)
                                },
                            )
                        },
                    ),
                )
                notionApi.updatePage(token = authHeader, pageId = pageId, request = retryRequest)
                // 再試行が成功した場合は、構成をシームレスに更新します
                propStatusType = alternateType
            } catch (_: Exception) {
                // オフラインまたは通信エラー時はキューに保存して次回自動リトライする
                val actionPayload = buildJsonObject {
                    put("newStatus", newStatus)
                }.toString()
                pendingSyncActionDao.insertPendingAction(
                    PendingSyncActionEntity(
                        actionType = "UPDATE_STATUS",
                        taskId = pageId,
                        payloadJson = actionPayload,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun updateTask(
        token: String,
        pageId: String,
        title: String,
        status: String,
        category: String,
        dueDate: String?,
        scheduledDate: String?,
    ) = syncMutex.withLock {
        // 楽観的な UI/ローカルの更新
        val currentLocalTask = taskDao.getTaskById(pageId)
        val statusColor = if (currentLocalTask?.status == status) {
            currentLocalTask.statusColor
        } else {
            taskDao.getStatusColorForStatus(status)
        }
        val categoryColor = if (currentLocalTask?.category == category) {
            currentLocalTask.categoryColor
        } else {
            taskDao.getCategoryColorForCategory(category)
        }
        val updatedLocalRef = TaskEntity(
            id = pageId,
            title = title,
            status = status,
            category = category,
            dueDate = dueDate,
            scheduledDate = scheduledDate,
            statusColor = statusColor,
            categoryColor = categoryColor,
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
                        put("name", status)
                    },
                )
            }
            
            properties[propCategoryName] = buildJsonObject {
                put(
                    "select",
                    buildJsonObject {
                        put("name", category)
                    },
                )
            }
            
            properties[propDueDateName] = buildJsonObject {
                if (dueDate.isNullOrBlank()) {
                    put("date", JsonNull)
                } else {
                    put(
                        "date",
                        buildJsonObject {
                            put("start", dueDate)
                        },
                    )
                }
            }
            
            properties[propScheduledDateName] = buildJsonObject {
                if (scheduledDate.isNullOrBlank()) {
                    put("date", JsonNull)
                } else {
                    put(
                        "date",
                        buildJsonObject {
                            put("start", scheduledDate)
                        },
                    )
                }
            }
            return properties
        }

        // フォールバックメカニズムを使用したリモートの Patch 呼び出しの実行
        val authHeader = "Bearer $token"
        val payload = try {
            buildPropertiesPayload(propStatusType)
        } catch (_: Exception) {
            val alternateType = if (propStatusType == "select") "status" else "select"
            propStatusType = alternateType
            buildPropertiesPayload(alternateType)
        }
        try {
            notionApi.updatePage(token = authHeader, pageId = pageId, request = NotionUpdateRequest(properties = payload))
        } catch (_: Exception) {
            val actionPayload = buildJsonObject {
                put("title", title)
                put("status", status)
                put("category", category)
                dueDate?.let { put("dueDate", it) }
                scheduledDate?.let { put("scheduledDate", it) }
            }.toString()
            pendingSyncActionDao.insertPendingAction(
                PendingSyncActionEntity(
                    actionType = "UPDATE_TASK",
                    taskId = pageId,
                    payloadJson = actionPayload,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createTask(
        token: String,
        databaseId: String,
        title: String,
        status: String,
        category: String,
        dueDate: String?,
        scheduledDate: String?,
    ) = syncMutex.withLock {
        val authHeader = "Bearer $token"
        
        fun buildCreatePayload(sType: String): NotionCreateRequest {
            val properties = mutableMapOf<String, PropertyUpdate>()
            properties[propTitleName] = PropertyUpdate(title = listOf(RichTextObject(text = TextContent(content = title))))
            properties[propStatusName] = if (sType == "select") {
                PropertyUpdate(select = SelectValue(name = status))
            } else {
                PropertyUpdate(status = StatusValue(name = status))
            }
            properties[propCategoryName] = PropertyUpdate(select = SelectValue(name = category))
            
            if (!dueDate.isNullOrBlank()) {
                properties[propDueDateName] = PropertyUpdate(date = DateValue(start = dueDate))
            }
            if (!scheduledDate.isNullOrBlank()) {
                properties[propScheduledDateName] = PropertyUpdate(date = DateValue(start = scheduledDate))
            }

            return NotionCreateRequest(
                parent = DatabaseParent(databaseId = databaseId),
                properties = properties
            )
        }

        try {
            val request = buildCreatePayload(propStatusType)
            val createdPage = notionApi.createPage(token = authHeader, request = request)
            
            val localEntity = TaskEntity(
                id = createdPage.id,
                title = title,
                status = status,
                category = category,
                dueDate = dueDate,
                scheduledDate = scheduledDate
            )
            taskDao.upsertTasks(listOf(localEntity))
        } catch (_: Exception) {
            val alternateType = if (propStatusType == "select") "status" else "select"
            try {
                val request = buildCreatePayload(alternateType)
                val createdPage = notionApi.createPage(token = authHeader, request = request)
                propStatusType = alternateType
                
                val localEntity = TaskEntity(
                    id = createdPage.id,
                    title = title,
                    status = status,
                    category = category,
                    dueDate = dueDate,
                    scheduledDate = scheduledDate
                )
                taskDao.upsertTasks(listOf(localEntity))
            } catch (_: Exception) {
                // オフライン時等：ローカルダミーIDでとりあえず保存し、キューに入れる
                val tempId = "pending_" + System.currentTimeMillis()
                val localEntity = TaskEntity(
                    id = tempId,
                    title = title,
                    status = status,
                    category = category,
                    dueDate = dueDate,
                    scheduledDate = scheduledDate
                )
                taskDao.upsertTasks(listOf(localEntity))

                val actionPayload = buildJsonObject {
                    put("title", title)
                    put("status", status)
                    put("category", category)
                    dueDate?.let { put("dueDate", it) }
                    scheduledDate?.let { put("scheduledDate", it) }
                }.toString()

                pendingSyncActionDao.insertPendingAction(
                    PendingSyncActionEntity(
                        actionType = "CREATE_TASK",
                        taskId = tempId,
                        payloadJson = actionPayload,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // 保留中のオフライン同期アクションを順次処理
    private suspend fun processPendingSyncActionsInternal(token: String, databaseId: String) {
        val pendingActions = pendingSyncActionDao.getAllPendingActions()
        if (pendingActions.isEmpty()) return

        val authHeader = "Bearer $token"
        // 仮IDから本番IDへのマッピング
        val idMapping = mutableMapOf<String, String>()

        for (action in pendingActions) {
            try {
                // 各アクションを独立して処理し、1つのエラーが全体を止めないようにする
                val taskId = idMapping[action.taskId] ?: action.taskId
                val payloadJson = action.payloadJson
                val payloadObj = json.parseToJsonElement(payloadJson).jsonObject
                
                when (action.actionType) {
                    "UPDATE_STATUS" -> {
                        val newStatus = payloadObj["newStatus"]?.jsonPrimitive?.content ?: continue
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
                        properties[propTitleName] = buildJsonObject {
                            put(
                                "title",
                                buildJsonArray {
                                    add(buildJsonObject { put("text", buildJsonObject { put("content", title) }) })
                                },
                            )
                        }
                        properties[propStatusName] = buildJsonObject {
                            put(propStatusType, buildJsonObject { put("name", status) })
                        }
                        properties[propCategoryName] = buildJsonObject {
                            put("select", buildJsonObject { put("name", category) })
                        }
                        properties[propDueDateName] = buildJsonObject {
                            if (dueDate.isNullOrBlank()) put("date", JsonNull) else put("date", buildJsonObject { put("start", dueDate) })
                        }
                        properties[propScheduledDateName] = buildJsonObject {
                            if (scheduledDate.isNullOrBlank()) put("date", JsonNull) else put("date", buildJsonObject { put("start", scheduledDate) })
                        }

                        notionApi.updatePage(token = authHeader, pageId = taskId, request = NotionUpdateRequest(properties = properties))
                    }
                    "CREATE_TASK" -> {
                        val title = payloadObj["title"]?.jsonPrimitive?.content ?: ""
                        val status = payloadObj["status"]?.jsonPrimitive?.content ?: ""
                        val category = payloadObj["category"]?.jsonPrimitive?.content ?: ""
                        val dueDate = payloadObj["dueDate"]?.jsonPrimitive?.contentOrNull
                        val scheduledDate = payloadObj["scheduledDate"]?.jsonPrimitive?.contentOrNull

                        val propMap = mutableMapOf<String, PropertyUpdate>()
                        propMap[propTitleName] = PropertyUpdate(title = listOf(RichTextObject(text = TextContent(content = title))))
                        propMap[propStatusName] = if (propStatusType == "select") PropertyUpdate(select = SelectValue(name = status)) else PropertyUpdate(status = StatusValue(name = status))
                        propMap[propCategoryName] = PropertyUpdate(select = SelectValue(name = category))
                        if (!dueDate.isNullOrBlank()) propMap[propDueDateName] = PropertyUpdate(date = DateValue(start = dueDate))
                        if (!scheduledDate.isNullOrBlank()) propMap[propScheduledDateName] = PropertyUpdate(date = DateValue(start = scheduledDate))

                        val request = NotionCreateRequest(
                            parent = DatabaseParent(databaseId = databaseId),
                            properties = propMap
                        )
                        val created = notionApi.createPage(token = authHeader, request = request)
                        
                        // IDマッピングを記録
                        idMapping[action.taskId] = created.id

                        // 仮タスクを正式な ID で入れ替え
                        taskDao.getTaskById(action.taskId)?.let { tempTask ->
                            taskDao.deleteTask(tempTask)
                        }
                        taskDao.upsertTasks(listOf(
                            TaskEntity(
                                id = created.id,
                                title = title,
                                status = status,
                                category = category,
                                dueDate = dueDate,
                                scheduledDate = scheduledDate
                            )
                        ))
                    }
                }
                // 成功した場合のみアクションを削除
                pendingSyncActionDao.deletePendingActionById(action.id)
            } catch (e: Exception) {
                // 通信エラー等はログ出力して次へ。ただし、深刻なネットワークエラーの場合はループを抜ける
                e.printStackTrace()
                if (e is IOException) break 
            }
        }
    }

    // SharedPreferences から Room への古い JSON マイグレーション
    suspend fun migrateLegacyPreferencesToRoom(
        sharedPreferences: SharedPreferences,
        scheduleRepository: ScheduleRepository,
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
