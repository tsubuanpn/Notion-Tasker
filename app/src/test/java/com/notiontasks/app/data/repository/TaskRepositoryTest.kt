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
    fun syncTasks_正常系_Roomへデータが反映される() = runTest {
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

    @Test(expected = IOException::class)
    fun syncTasks_通信エラー_IOExceptionがスローされる() = runTest {
        // Arrange
        coEvery { pendingSyncActionDao.getAllPendingActions() } returns emptyList()
        coEvery { notionApi.queryDatabase(any(), any(), any(), any()) } throws Exception("Network Error")

        // Act
        repository.syncTasks("token", "db")
    }

    @Test
    fun updateTaskStatus_成功時_リモート更新とローカル更新が両方行われる() = runTest {
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
    fun updateTaskStatus_オフライン時_PendingSyncActionとして保存される() = runTest {
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
    fun createTask_オフライン時_一時IDでRoomに保存されアクションがキューイングされる() = runTest {
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
    fun migrateLegacyPreferencesToRoom_未実施時_データが移行されフラグがセットされる() = runTest {
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
    fun migrateLegacyPreferencesToRoom_実施済み_何も行われない() = runTest {
        // Arrange
        val migratedKey = "has_migrated_legacy_prefs_to_room_v1"
        every { sharedPrefs.getBoolean(migratedKey, false) } returns true

        // Act
        repository.migrateLegacyPreferencesToRoom(sharedPrefs, scheduleRepository)

        // Assert
        verify(exactly = 0) { sharedPrefs.edit() }
    }
}
