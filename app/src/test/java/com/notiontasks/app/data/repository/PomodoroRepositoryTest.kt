package com.notiontasks.app.data.repository

import app.cash.turbine.test
import com.notiontasks.app.data.PomodoroLog
import com.notiontasks.app.data.local.PomodoroLogDao
import com.notiontasks.app.data.local.PomodoroLogEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroRepositoryTest {

    private val pomodoroLogDao: PomodoroLogDao = mockk(relaxed = true)
    private lateinit var repository: PomodoroRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Repository will be initialized in tests after mocks are set up if needed,
        // or here with default mocks.
    }

    private fun createRepository() = PomodoroRepository(pomodoroLogDao)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun allLogsFlow_データが存在する場合_ドメインモデルのリストが放出される() = runTest {
        // Arrange
        val entity = PomodoroLogEntity(
            id = "log_1",
            taskId = "task_1",
            taskTitle = "Work Task",
            category = "Work",
            categoryColor = "#2196F3",
            date = "2026-08-02",
            minutes = 25,
            timestamp = 1722596400000L
        )
        every { pomodoroLogDao.getAllLogsFlow() } returns flowOf(listOf(entity))
        repository = createRepository()

        // Act & Assert
        repository.allLogsFlow.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Work Task", result[0].taskTitle)
            assertEquals(25, result[0].minutes)
            awaitComplete()
        }
    }

    @Test
    fun insertPomodoroLog_有効なデータの場合_DAOのinsertLogが正しい値で呼ばれる() = runTest {
        // Arrange
        repository = createRepository()
        val log = PomodoroLog(
            id = "log_new",
            taskId = "task_2",
            taskTitle = "Study",
            category = "Life",
            categoryColor = "#4CAF50",
            date = "2026-08-02",
            minutes = 50,
            timestamp = 1722596400000L
        )

        // Act
        repository.insertPomodoroLog(log)

        // Assert
        coVerify { 
            pomodoroLogDao.insertLog(match { 
                it.id == "log_new" && it.taskTitle == "Study" && it.minutes == 50
            })
        }
    }

    @Test
    fun loadPomodoroLogs_DAOからデータが返る場合_正しくマッピングされたリストが返る() = runTest {
        // Arrange
        val entities = listOf(
            PomodoroLogEntity(
                id = "1", taskId = null, taskTitle = null, category = "Break", 
                categoryColor = null, date = "2026-08-02", minutes = 5, timestamp = 0L
            )
        )
        coEvery { pomodoroLogDao.getAllLogs() } returns entities
        repository = createRepository()

        // Act
        val result = repository.loadPomodoroLogs()

        // Assert
        assertEquals(1, result.size)
        assertEquals("Break", result[0].category)
        assertEquals(5, result[0].minutes)
    }

    @Test
    fun deletePomodoroLogById_IDを指定した場合_DAOの削除メソッドが呼ばれる() = runTest {
        // Arrange
        repository = createRepository()
        val id = "target_log_id"

        // Act
        repository.deletePomodoroLogById(id)

        // Assert
        coVerify { pomodoroLogDao.deleteLogById(id) }
    }

    @Test
    fun deleteLogsOlderThan_閾値を指定した場合_DAOの期間指定削除が呼ばれる() = runTest {
        // Arrange
        repository = createRepository()
        val threshold = 1722596400000L

        // Act
        repository.deleteLogsOlderThan(threshold)

        // Assert
        coVerify { pomodoroLogDao.deleteLogsOlderThan(threshold) }
    }

    @Test
    fun clearAllLogs_呼び出し時_DAOの全削除が呼ばれる() = runTest {
        repository = createRepository()
        // Act
        repository.clearAllLogs()

        // Assert
        coVerify { pomodoroLogDao.clearAllLogs() }
    }

}
