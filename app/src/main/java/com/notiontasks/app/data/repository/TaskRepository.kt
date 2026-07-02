package com.notiontasks.app.data.repository

import com.notiontasks.app.data.local.TaskDao
import com.notiontasks.app.data.local.TaskEntity
import com.notiontasks.app.data.model.TaskModel
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val taskDao: TaskDao
) {

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
        dueDate: String
    ) {
        propTitleName = title.ifBlank { "名前" }
        propStatusName = status.ifBlank { "状態" }
        propStatusType = if (statusType == "select") "select" else "status"
        propCategoryName = category.ifBlank { "種類" }
        propScheduledDateName = scheduledDate.ifBlank { "予定日" }
        propDueDateName = dueDate.ifBlank { "締め切り" }
    }

    // 信頼できる唯一の情報源としてのメインのローカルフロー。Entity をドメインモデルに変換します
    val allTasks: Flow<List<TaskModel>> = taskDao.getAllTasksFlow().map { entities ->
        entities.map { entity ->
            TaskModel(
                id = entity.id,
                title = entity.title,
                status = entity.status,
                category = entity.category,
                dueDate = entity.dueDate,
                scheduledDate = entity.scheduledDate,
                statusColor = entity.statusColor,
                categoryColor = entity.categoryColor
            )
        }
    }

    suspend fun getDatabaseMetadata(token: String, databaseId: String): NotionDatabaseResponse {
        val authHeader = "Bearer $token"
        val meta = notionApi.getDatabase(token = authHeader, databaseId = databaseId)
        
        // 自己修復：実際のデータベースプロパティのタイプを自動的に検査し、select と status の不一致を解消します
        val statusProp = meta.properties[propStatusName]
        if (statusProp != null) {
            if (statusProp.type == "select") {
                propStatusType = "select"
            } else if (statusProp.type == "status") {
                propStatusType = "status"
            }
        }
        return meta
    }

    // リモートの Notion DB からタスクキャッシュを更新する
    suspend fun syncTasks(token: String, databaseId: String) {
        val authHeader = "Bearer $token"
        try {
            // 生のクエリデータベース DTO ノードから一致するすべてのレコードを取得する
            val response = notionApi.queryDatabase(token = authHeader, databaseId = databaseId)
            
            val activeEntities = response.results.mapNotNull { page ->
                val title = page.properties.getTitleText(propTitleName) ?: return@mapNotNull null
                
                // ステータスの解析が空を返す場合、セレクトの解析にフォールバックする
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
                    categoryColor = categoryColorVal
                )
            }

            // ローカルリポジトリデータベースと同期する
            taskDao.clearAllTasks()
            taskDao.insertTasks(activeEntities)
        } catch (e: Exception) {
            // 呼び出し元での HTTP 障害や接続断を処理する
            throw IOException("Network synchronization failed: ${e.message}", e)
        }
    }

    // キャッシュされた要素全体の直接フィルター
    @Suppress("unused")
    fun getTasksByStatus(status: String): Flow<List<TaskModel>> {
        return taskDao.getTasksByStatus(status).map { entities ->
            entities.map { entity ->
                TaskModel(
                    id = entity.id,
                    title = entity.title,
                    status = entity.status,
                    category = entity.category,
                    dueDate = entity.dueDate,
                    scheduledDate = entity.scheduledDate,
                    statusColor = entity.statusColor,
                    categoryColor = entity.categoryColor
                )
            }
        }
    }

    // カテゴリ画面用のローカルフィルター表現
    @Suppress("unused")
    fun getTasksByCategory(category: String): Flow<List<TaskModel>> {
        return allTasks.map { tasks ->
            tasks.filter { it.category == category }
        }
    }

    // 直接的なローカル更新を伴う PATCH リクエストによる高性能なステータス遷移の更新
    suspend fun updateTaskStatus(
        token: String,
        pageId: String,
        newStatus: String
    ) {
        // 楽観的な UI/ローカル更新
        val statusColor = taskDao.getStatusColorForStatus(newStatus)
        taskDao.updateTaskStatusLocal(pageId, newStatus, statusColor)

        // リモートの Patch 呼び出しを実行する
        val authHeader = "Bearer $token"
        
        try {
            val request = NotionUpdateRequest(
                properties = mapOf(
                    propStatusName to buildJsonObject {
                        put(propStatusType, buildJsonObject {
                            put("name", newStatus)
                        })
                    }
                )
            )
            notionApi.updatePage(token = authHeader, pageId = pageId, request = request)
        } catch (e: Exception) {
            // 自己修復セーフティネット：最初の更新が失敗した場合、別のタイプを試してフォールバックする
            val alternateType = if (propStatusType == "select") "status" else "select"
            try {
                val retryRequest = NotionUpdateRequest(
                    properties = mapOf(
                        propStatusName to buildJsonObject {
                            put(alternateType, buildJsonObject {
                                put("name", newStatus)
                            })
                        }
                    )
                )
                notionApi.updatePage(token = authHeader, pageId = pageId, request = retryRequest)
                // 再試行が成功した場合は、シームレスに構成を更新します
                propStatusType = alternateType
            } catch (_: Exception) {
                // 両方が失敗した場合は、エラーを表示します
                throw IOException("Failed to save remote status change with either select or status type: ${e.message}", e)
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
        scheduledDate: String?
    ) {
        // 楽観的な UI/ローカル更新
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
            categoryColor = categoryColor
        )
        taskDao.insertTasks(listOf(updatedLocalRef))

        // リクエストプロパティのペイロードを構築するためのヘルパー
        fun buildPropertiesPayload(sType: String): Map<String, JsonElement> {
            val properties = mutableMapOf<String, JsonElement>()
            
            properties[propTitleName] = buildJsonObject {
                put("title", buildJsonArray {
                    add(buildJsonObject {
                        put("text", buildJsonObject {
                            put("content", title)
                        })
                    })
                })
            }
            
            properties[propStatusName] = buildJsonObject {
                put(sType, buildJsonObject {
                    put("name", status)
                })
            }
            
            properties[propCategoryName] = buildJsonObject {
                put("select", buildJsonObject {
                    put("name", category)
                })
            }
            
            properties[propDueDateName] = buildJsonObject {
                if (dueDate.isNullOrBlank()) {
                    put("date", JsonNull)
                } else {
                    put("date", buildJsonObject {
                        put("start", dueDate)
                    })
                }
            }
            
            properties[propScheduledDateName] = buildJsonObject {
                if (scheduledDate.isNullOrBlank()) {
                    put("date", JsonNull)
                } else {
                    put("date", buildJsonObject {
                        put("start", scheduledDate)
                    })
                }
            }
            return properties
        }

        // フォールバックメカニズムを使用したリモートの Patch 呼び出しの実行
        val authHeader = "Bearer $token"
        try {
            val initialPayload = buildPropertiesPayload(propStatusType)
            notionApi.updatePage(token = authHeader, pageId = pageId, request = NotionUpdateRequest(properties = initialPayload))
        } catch (e: Exception) {
            val alternateType = if (propStatusType == "select") "status" else "select"
            try {
                val fallbackPayload = buildPropertiesPayload(alternateType)
                notionApi.updatePage(token = authHeader, pageId = pageId, request = NotionUpdateRequest(properties = fallbackPayload))
                propStatusType = alternateType
            } catch (_: Exception) {
                throw IOException("Failed to save remote task change: ${e.message}", e)
            }
        }
    }

    suspend fun createTask(
        token: String,
        databaseId: String,
        title: String,
        status: String,
        category: String,
        dueDate: String?,
        scheduledDate: String?
    ) {
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
            taskDao.insertTasks(listOf(localEntity))
        } catch (e: Exception) {
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
                taskDao.insertTasks(listOf(localEntity))
            } catch (_: Exception) {
                throw IOException("Failed to create remote task: ${e.message}", e)
            }
        }
    }
}