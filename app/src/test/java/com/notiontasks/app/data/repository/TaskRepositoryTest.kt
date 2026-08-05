package com.notiontasks.app.data.repository

import android.content.SharedPreferences
import com.notiontasks.app.data.local.PendingSyncActionDao
import com.notiontasks.app.data.local.TaskDao
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryTest {

    private val notionApi: NotionApi = mockk()
    private val taskDao: TaskDao = mockk(relaxed = true)
    private val pendingSyncActionDao: PendingSyncActionDao = mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)

    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        repository = TaskRepository(notionApi, taskDao, pendingSyncActionDao)
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
            taskDao.syncTasksTransactionally(match { entities ->
                entities.size == 1 &&
                entities[0].id == pageId &&
                entities[0].title == "Sample Task" &&
                entities[0].status == "In Progress"
            })
        }
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
            taskDao.syncTasksTransactionally(match { entities ->
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
                databaseId,
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
        
        coEvery { notionApi.updatePage(any(), any(), any(), any()) } returns mockk()

        // Act
        repository.updateTaskStatus(token, pageId, newStatus)

        // Assert
        coVerify { taskDao.updateTaskStatusLocal(pageId, newStatus, any()) }
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
        coVerify { taskDao.updateTaskStatusLocal(pageId, newStatus, any()) }
        coVerify { 
            pendingSyncActionDao.insertPendingAction(match { action ->
                action.actionType == "UPDATE_STATUS" &&
                action.taskId == pageId &&
                action.payloadJson.contains("Done")
            })
        }
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
