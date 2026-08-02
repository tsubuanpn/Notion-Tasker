package com.notiontasks.app.ui.viewmodel

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import app.cash.turbine.test
import com.notiontasks.app.TaskNotificationReceiver
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.repository.PomodoroRepository
import com.notiontasks.app.data.repository.ScheduleRepository
import com.notiontasks.app.data.repository.TaskRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {

    private val repository: TaskRepository = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)
    private val pomodoroRepository: PomodoroRepository = mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // AlarmManager と通知関連のスタティックメソッドをモック
        mockkObject(TaskNotificationReceiver.Companion)
        every { TaskNotificationReceiver.scheduleBlockAlarm(any(), any()) } returns Unit

        val alarmManager: AlarmManager = mockk(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager

        // デフォルトのモック設定（ViewModelの初期化でエラーにならないように）
        every { repository.allTasks } returns flowOf(emptyList())
        every { scheduleRepository.allTimeBlocks } returns flowOf(emptyList())
        every { scheduleRepository.allLifeActivities } returns flowOf(emptyList())
        every { pomodoroRepository.allLogsFlow } returns flowOf(emptyList())

        every { sharedPrefs.getInt(any(), any()) } returns 0
        every { sharedPrefs.getString(any(), any()) } returns ""
        every { sharedPrefs.getStringSet(any(), any()) } returns emptySet()
    }

    @After
    fun tearDown() {
        unmockkObject(TaskNotificationReceiver.Companion)
        Dispatchers.resetMain()
    }

    @Test
    fun tasksState_初期状態_Loadingを放出() = runTest {
        // Arrange
        // repository.allTasks がまだ何も放出していない状態をシミュレート
        every { repository.allTasks } returns flow { /* no-op */ }

        // Act
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)

        // Assert
        viewModel.tasksState.test {
            assertEquals(TasksUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tasksState_データあり_Successを放出() = runTest {
        // Arrange
        val tasks = listOf(
            TaskModel(
                id = "1",
                title = "Test Task",
                status = "Todo",
                category = "Work",
                dueDate = null,
                scheduledDate = null
            )
        )
        every { repository.allTasks } returns flowOf(tasks)

        // Act
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)

        // Assert
        viewModel.tasksState.test {
            // 初期値 Loading の後、Success が放出されることを確認
            assertEquals(TasksUiState.Loading, awaitItem())
            val item = awaitItem()
            assertTrue(item is TasksUiState.Success)
            assertEquals(tasks, (item as TasksUiState.Success).tasks)
        }
    }

    @Test
    fun tasksState_データなし_Idleを放出() = runTest {
        // Arrange
        every { repository.allTasks } returns flowOf(emptyList())

        // Act
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)

        // Assert
        viewModel.tasksState.test {
            assertEquals(TasksUiState.Loading, awaitItem())
            assertEquals(TasksUiState.Idle, awaitItem())
        }
    }

    @Test
    fun tasksState_エラー発生_Errorを放出() = runTest {
        // Arrange
        val errorMessage = "Database Error"
        every { repository.allTasks } returns flow { throw Exception(errorMessage) }

        // Act
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)

        // Assert
        viewModel.tasksState.test {
            assertEquals(TasksUiState.Loading, awaitItem())
            val item = awaitItem()
            assertTrue(item is TasksUiState.Error)
            assertEquals(errorMessage, (item as TasksUiState.Error).message)
        }
    }

    @Test
    fun init_初期化時_マイグレーションとデフォルト設定が実行される() = runTest {
        // Arrange
        every { sharedPrefs.getInt("pomodoro_stats_duration_months", 0) } returns 3

        // Act
        TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)
        advanceUntilIdle()

        // Assert
        coVerify { repository.migrateLegacyPreferencesToRoom(sharedPrefs, scheduleRepository) }
        coVerify { scheduleRepository.loadLifeActivities() }
        coVerify { pomodoroRepository.deleteLogsOlderThan(any()) }
    }

    @Test
    fun syncWithNotion_認証情報あり_リポジトリの同期が呼ばれる() = runTest {
        // Arrange
        every { sharedPrefs.getString("notion_token", "") } returns "valid_token"
        every { sharedPrefs.getString("database_id", "") } returns "valid_db_id"
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)
        
        // Act
        viewModel.syncWithNotion()
        advanceUntilIdle()

        // Assert
        coVerify { repository.syncTasks("valid_token", "valid_db_id") }
    }

    @Test
    fun syncWithNotion_認証情報なし_リポジトリの同期が呼ばれない() = runTest {
        // Arrange
        every { sharedPrefs.getString("notion_token", "") } returns ""
        every { sharedPrefs.getString("database_id", "") } returns ""
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)
        
        // Act
        viewModel.syncWithNotion()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { repository.syncTasks(any(), any()) }
    }

    @Test
    fun autoInitializeDefaultLifeActivities_未初期化の日付_同期処理が実行される() = runTest {
        // Arrange
        val date = "2026-08-02"
        val defaults = listOf(
            LifeActivity("la_sleep", "睡眠", 480, "#9C27B0", defaultStartTime = 0, defaultEndTime = 480, sortOrder = 0)
        )
        coEvery { scheduleRepository.loadLifeActivities() } returns defaults
        
        // Act
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)
        advanceUntilIdle() 
        
        viewModel.autoInitializeDefaultLifeActivities(context, date)
        advanceUntilIdle()

        // Assert
        // 保存処理 (saveTimeBlock) が呼ばれることを確認
        coVerify { scheduleRepository.saveTimeBlock(match { it.associatedId == "la_sleep" }) }
        // 初期化済み日付として保存されることを確認
        verify { sharedPrefs.edit() }
    }

    @Test
    fun autoInitializeDefaultLifeActivities_初期化済みの日付_処理がスキップされる() = runTest {
        // Arrange
        val date = "2026-08-02"
        every { sharedPrefs.getStringSet("initialized_dates", any()) } returns setOf(date)
        val viewModel = TaskViewModel(repository, scheduleRepository, pomodoroRepository, sharedPrefs)
        advanceUntilIdle() // init で loadInitializedDates を完了させる
        
        // Act
        viewModel.autoInitializeDefaultLifeActivities(context, date)
        advanceUntilIdle()

        // Assert
        // 既に初期化済みなので、保存処理は呼ばれない
        coVerify(exactly = 0) { scheduleRepository.saveTimeBlocks(any()) }
    }
}
