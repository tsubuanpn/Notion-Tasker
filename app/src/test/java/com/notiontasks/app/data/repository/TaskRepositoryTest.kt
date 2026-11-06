package com.notiontasks.app.data.repository

import android.content.SharedPreferences
import com.notiontasks.app.data.local.PendingSyncActionDao
import com.notiontasks.app.data.local.PendingSyncActionEntity
import com.notiontasks.app.data.local.SyncMetadataDao
import com.notiontasks.app.data.local.TaskDao
import com.notiontasks.app.data.local.TaskEntity
import com.notiontasks.app.data.remote.NotionApi
import com.notiontasks.app.data.remote.dto.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryTest {

    private val notionApi: NotionApi = mockk()
    private val taskDao: TaskDao = mockk(relaxed = true)
    private val pendingSyncActionDao: PendingSyncActionDao = mockk(relaxed = true)
    private val syncMetadataDao: SyncMetadataDao = mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)

    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        repository = TaskRepository(notionApi, taskDao, pendingSyncActionDao, syncMetadataDao)
        repository.updateDataSourceId("data_source")
        repository.updateConnectionScope("database")
        coEvery { notionApi.getDataSource(any(), any(), any()) } returns NotionDatabaseResponse(
            id = "data_source",
            properties = mapOf(
                "Notion Tasker ID" to NotionPropertyMeta(
                    id = "client_identifier",
                    type = "rich_text",
                ),
            ),
        )
    }

    @Test
    fun getTitleText_concatenatesAllRichTextFragments() {
        val properties = mapOf(
            "名前" to buildJsonObject {
                put("title", buildJsonArray {
                    add(buildJsonObject { put("plain_text", "前半") })
                    add(buildJsonObject { put("plain_text", "後半") })
                })
            },
        )

        assertEquals("前半後半", properties.getTitleText("名前"))
    }

    @Test
    fun syncTasks_success_reflectsDataToRoom() = runTest {
        // Arrange
        val token = "test_token"
        val databaseId = "test_db"
        val pageId = "page_123"
        
        val properties = mapOf(
            "名前" to buildJsonObject {
                put("title", buildJsonArray {
                    add(buildJsonObject { put("plain_text", "Sample Task") })
                })
            },
            "状態" to buildJsonObject {
                put("status", buildJsonObject { put("name", "In Progress") })
            },
            "種類" to buildJsonObject {
                put("select", buildJsonObject { put("name", "Work") })
            }
        )
        
        val response = NotionQueryResponse(
            results = listOf(NotionPage(id = pageId, properties = properties))
        )
        
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } returns response

        // Act
        repository.syncTasks(token, databaseId)

        // Assert
        coVerify { 
            taskDao.syncTasksTransactionally(eq(databaseId), match { entities ->
                entities.size == 1 &&
                entities[0].id == pageId &&
                entities[0].title == "Sample Task" &&
                entities[0].status == "In Progress" &&
                entities[0].connectionScope == databaseId
            })
        }
    }

    @Test
    fun syncTasks_missingMappedTitle_doesNotReplaceCachedTasks() = runTest {
        val response = NotionQueryResponse(
            results = listOf(
                NotionPage(
                    id = "page_123",
                    properties = mapOf(
                        "別名" to buildJsonObject { put("title", buildJsonArray {}) },
                    ),
                ),
            ),
        )
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } returns response

        try {
            repository.syncTasks("token", "database")
            fail("Expected missing title mapping to fail the sync")
        } catch (_: IOException) {
            // Expected: preserving the existing cache is safer than replacing it with invalid data.
        }

        coVerify(exactly = 0) { taskDao.syncTasksTransactionally(any(), any()) }
    }

    @Test
    fun syncTasks_recordsOnlyRemoteEditedTimeAsTheNextCursor() = runTest {
        val properties = mapOf(
            "名前" to buildJsonObject {
                put("title", buildJsonArray { add(buildJsonObject { put("plain_text", "Task") }) })
            },
        )
        val response = NotionQueryResponse(
            results = listOf(
                NotionPage(
                    id = "page_123",
                    lastEditedTime = "2026-11-03T00:00:00.000Z",
                    properties = properties,
                ),
            ),
        )
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } returns response

        repository.syncTasks("token", "database")

        coVerify {
            syncMetadataDao.put(match { it.key == "notion_remote_sync_cursor" && it.timestamp > 0L })
        }
    }

    @Test
    fun syncTasks_singleDataSource_discoversAndUsesItsId() = runTest {
        repository.updateDataSourceId("")
        val properties = mapOf(
            "名前" to buildJsonObject {
                put("title", buildJsonArray { add(buildJsonObject { put("plain_text", "Task") }) })
            },
        )
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        coEvery { notionApi.getDatabaseDataSources(any(), any(), any()) } returns NotionDatabaseInfo(
            id = "database",
            dataSources = listOf(NotionDataSource(id = "data_source", name = "Tasks")),
        )
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } returns NotionQueryResponse(
            results = listOf(NotionPage(id = "page_123", properties = properties)),
        )

        repository.syncTasks("token", "database")

        coVerify { notionApi.queryDatabase(any(), any(), "data_source", any()) }
    }

    @Test
    fun syncTasks_pagination_mergesMultiplePages() = runTest {
        // Arrange
        val token = "test_token"
        val databaseId = "test_db"
        
        fun createPageProperties(title: String) = mapOf(
            "名前" to buildJsonObject {
                put("title", buildJsonArray {
                    add(buildJsonObject { put("plain_text", title) })
                })
            }
        )
        
        val responsePage1 = NotionQueryResponse(
            results = listOf(NotionPage(id = "page_1", properties = createPageProperties("Task 1"))),
            hasMore = true,
            nextCursor = "cursor_2"
        )
        val responsePage2 = NotionQueryResponse(
            results = listOf(NotionPage(id = "page_2", properties = createPageProperties("Task 2"))),
            hasMore = false,
            nextCursor = null
        )
        
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        // 1回目の呼び出し（cursorなし）と2回目の呼び出し（cursor_2あり）をシミュレート
        coEvery { notionApi.queryDatabase(any(), any(), any(), match { it.startCursor == null }) } returns responsePage1
        coEvery { notionApi.queryDatabase(any(), any(), any(), match { it.startCursor == "cursor_2" }) } returns responsePage2

        // Act
        repository.syncTasks(token, databaseId)

        // Assert
        coVerify(exactly = 2) { notionApi.queryDatabase(any(), any(), any(), any()) }
        coVerify { 
            taskDao.syncTasksTransactionally(eq(databaseId), match { entities ->
                entities.size == 2 &&
                entities.any { it.id == "page_1" && it.title == "Task 1" } &&
                entities.any { it.id == "page_2" && it.title == "Task 2" }
            })
        }
    }

    @Test
    fun syncTasks_sortParameters_sentToApiCorrectly() = runTest {
        // Arrange
        val token = "test_token"
        val databaseId = "test_db"
        
        val response = NotionQueryResponse(results = emptyList(), hasMore = false)
        
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } returns response

        // Act
        repository.syncTasks(token, databaseId)

        // Assert
        coVerify { 
            notionApi.queryDatabase(
                any(),
                any(),
                "data_source",
                match { request ->
                    val sorts = request.sorts
                    sorts?.size == 2 &&
                    sorts[0].property == repository.propScheduledDateName &&
                    sorts[1].property == repository.propDueDateName
                }
            )
        }
    }

    @Test(expected = IOException::class)
    fun syncTasks_networkError_throwsIOException() = runTest {
        // Arrange
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } throws Exception("Network Error")

        // Act
        repository.syncTasks("token", "db")
    }

    @Test
    fun updateTaskStatus_success_updatesRemoteAndLocal() = runTest {
        // Arrange
        val token = "token"
        val pageId = "id"
        val newStatus = "Done"
        
        coEvery { notionApi.updatePage(any(), any(), any(), any()) } returns NotionPage(
            id = pageId,
            properties = emptyMap(),
        )

        // Act
        repository.updateTaskStatus(token, pageId, newStatus)

        // Assert
        coVerify { taskDao.updateTaskStatusLocal(pageId, newStatus, any(), any()) }
        coVerify { notionApi.updatePage(any(), any(), pageId, any()) }
    }

    @Test
    fun updateTaskStatus_offline_queuesPendingAction() = runTest {
        // Arrange
        val token = "token"
        val pageId = "id"
        val newStatus = "Done"
        
        coEvery { notionApi.updatePage(any(), any(), any(), any()) } throws IOException("Offline")

        // Act
        repository.updateTaskStatus(token, pageId, newStatus)

        // Assert
        coVerify { taskDao.updateTaskStatusLocal(pageId, newStatus, any(), any()) }
        coVerify(exactly = 1) { notionApi.updatePage(any(), any(), pageId, any()) }
        coVerify { 
            pendingSyncActionDao.insertPendingAction(match { action ->
                action.actionType == "UPDATE_STATUS" &&
                action.taskId == pageId &&
                action.payloadJson.contains("Done")
            })
        }
        coVerify { pendingSyncActionDao.deletePendingActionsForTask("UPDATE_STATUS", pageId) }
    }

    @Test
    fun createTask_offline_savesWithTempIdAndQueuesAction() = runTest {
        // Arrange
        coEvery { notionApi.createPage(any(), any(), any()) } throws IOException("Offline")

        // Act
        repository.createTask("token", "db", "New Task", "Todo", "Life", null, null)

        // Assert
        coVerify { 
            taskDao.upsertTasks(match { entities ->
                entities.size == 1 && entities[0].id.startsWith("pending_")
            })
        }
        coVerify { 
            pendingSyncActionDao.insertPendingAction(match { action ->
                action.actionType == "CREATE_TASK" && action.payloadJson.contains("New Task")
            })
        }
    }

    @Test
    fun createTask_transientFailure_postsOnceAndQueuesTheOriginalIntent() = runTest {
        coEvery { notionApi.createPage(any(), any(), any()) } throws IOException("response lost")

        repository.createTask("token", "db", "New Task", "Todo", "Life", null, null)

        coVerify(exactly = 1) { notionApi.createPage(any(), any(), any()) }
        coVerify {
            pendingSyncActionDao.insertPendingAction(match { action ->
                action.actionType == "CREATE_TASK" && action.payloadJson.contains("New Task")
            })
        }
    }

    @Test
    fun createTask_writesStableClientIdentifier() = runTest {
        val requestSlot = slot<NotionCreateRequest>()
        coEvery { notionApi.createPage(any(), any(), capture(requestSlot)) } returns NotionPage(
            id = "page_123",
            properties = emptyMap(),
        )

        repository.createTask("token", "db", "New Task", "Todo", "Life", null, null)

        val identifier = requestSlot.captured.properties["Notion Tasker ID"]
            ?.richText
            ?.singleOrNull()
            ?.text
            ?.content
        assertEquals(true, !identifier.isNullOrBlank())
    }

    @Test
    fun pendingCreate_reconcilesExistingPageBeforeRetrying() = runTest {
        val pending = PendingSyncActionEntity(
            id = 1,
            actionType = "CREATE_TASK",
            taskId = "pending_client-123",
            payloadJson = """{"title":"New Task","status":"Todo","category":"Life","clientIdentifier":"client-123"}""",
            timestamp = 1L,
            connectionScope = "db",
        )
        val existing = NotionPage(
            id = "page_123",
            properties = mapOf(
                "名前" to buildJsonObject { put("title", buildJsonArray { add(buildJsonObject { put("plain_text", "New Task") }) }) },
                "状態" to buildJsonObject { put("status", buildJsonObject { put("name", "Todo") }) },
                "種類" to buildJsonObject { put("select", buildJsonObject { put("name", "Life") }) },
            ),
        )
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns listOf(pending) andThen emptyList()
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } returns NotionQueryResponse(results = listOf(existing))
        coEvery { taskDao.getTaskById("pending_client-123") } returns TaskEntity(
            id = "pending_client-123",
            title = "New Task",
            status = "Todo",
            category = "Life",
            dueDate = null,
            scheduledDate = null,
        )

        repository.syncTasks("token", "db")

        coVerify(exactly = 0) { notionApi.createPage(any(), any(), any()) }
        coVerify { pendingSyncActionDao.deletePendingActionById(1) }
        coVerify { taskDao.upsertTasks(match { it.single().id == "page_123" }) }
    }

    @Test
    fun syncTasks_permanentPendingFailure_keepsTheUserAction() = runTest {
        val pending = PendingSyncActionEntity(
            id = 1,
            actionType = "UPDATE_STATUS",
            taskId = "page_123",
            payloadJson = "{\"newStatus\":\"Done\"}",
            timestamp = 1L,
        )
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns listOf(pending)
        coEvery { notionApi.updatePage(any(), any(), any(), any()) } throws IllegalArgumentException("invalid status")

        try {
            repository.syncTasks("token", "database")
            fail("Expected a permanent pending-action failure to be reported")
        } catch (_: IOException) {
            // Expected: the user action remains queued for repair and retry.
        }

        coVerify(exactly = 0) { pendingSyncActionDao.deletePendingActionById(pending.id) }
    }

    @Test
    fun migrateLegacyPreferencesToRoom_notMigrated_migratesDataAndSetsFlag() = runTest {
        // Arrange
        val editor: SharedPreferences.Editor = mockk(relaxed = true)
        val migratedKey = "has_migrated_legacy_prefs_to_room_v1"
        every { sharedPrefs.getBoolean(migratedKey, false) } returns false
        every { sharedPrefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        
        // 旧データのシミュレート (SharedPreferences内のJSON)
        every { sharedPrefs.getString("time_blocks_v2", null) } returns "[]"
        every { sharedPrefs.getString("life_activities_v2", null) } returns "[]"

        // Act
        repository.migrateLegacyPreferencesToRoom(sharedPrefs, scheduleRepository)

        // Assert
        verify { editor.putBoolean(migratedKey, true) }
        verify { editor.apply() }
    }

    @Test
    fun migrateLegacyPreferencesToRoom_alreadyMigrated_doesNothing() = runTest {
        // Arrange
        val migratedKey = "has_migrated_legacy_prefs_to_room_v1"
        every { sharedPrefs.getBoolean(migratedKey, false) } returns true

        // Act
        repository.migrateLegacyPreferencesToRoom(sharedPrefs, scheduleRepository)

        // Assert
        verify(exactly = 0) { sharedPrefs.edit() }
    }
}
