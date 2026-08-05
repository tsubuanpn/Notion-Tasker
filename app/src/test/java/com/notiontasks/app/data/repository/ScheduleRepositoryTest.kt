package com.notiontasks.app.data.repository

import app.cash.turbine.test
import com.notiontasks.app.data.local.LifeActivityDao
import com.notiontasks.app.data.local.LifeActivityEntity
import com.notiontasks.app.data.local.ScheduleBlockDao
import com.notiontasks.app.data.local.ScheduleBlockEntity
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TimeBlock
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
class ScheduleRepositoryTest {

    private val scheduleBlockDao: ScheduleBlockDao = mockk(relaxed = true)
    private val lifeActivityDao: LifeActivityDao = mockk(relaxed = true)
    private lateinit var repository: ScheduleRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // repository will be created in tests
    }

    private fun createRepository() = ScheduleRepository(scheduleBlockDao, lifeActivityDao)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun allTimeBlocks_whenDataExists_emitsConvertedDomainModels() = runTest {
        // Arrange
        val entity = ScheduleBlockEntity(
            id = "1",
            type = "task",
            title = "Task 1",
            associatedId = "task_1",
            startTime = 60,
            endTime = 120,
            color = "#FFFFFF",
            date = "2026-08-02",
        )
        every { scheduleBlockDao.getAllBlocksFlow() } returns flowOf(listOf(entity))
        repository = createRepository()

        // Act & Assert
        repository.allTimeBlocks.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Task 1", result[0].title)
            assertEquals("task", result[0].type)
            awaitComplete()
        }
    }

    @Test
    fun saveTimeBlock_whenCalled_convertsToEntityAndCallsDao() = runTest {
        // Arrange
        repository = createRepository()
        val block = TimeBlock(
            id = "1",
            type = "life",
            title = "Sleep",
            associatedId = null,
            startTime = 0,
            endTime = 480,
            color = "#000000",
            date = "2026-08-02",
        )

        // Act
        repository.saveTimeBlock(block)

        // Assert
        coVerify { 
            scheduleBlockDao.insertBlock(
                match { 
                    (it.id == "1") && (it.title == "Sleep") && (it.type == "life")
                },
            )
        }
    }

    @Test
    fun deleteTimeBlockById_whenCalled_callsDaoDelete() = runTest {
        // Arrange
        repository = createRepository()
        val id = "target_id"

        // Act
        repository.deleteTimeBlockById(id)

        // Assert
        coVerify { scheduleBlockDao.deleteBlockById(id) }
    }

    @Test
    fun allLifeActivities_whenDataExists_emitsConvertedDomainModels() = runTest {
        // Arrange
        val entity = LifeActivityEntity(
            id = "la_1",
            name = "Meal",
            durationMinutes = 60,
            color = "#FF9800",
            defaultStartTime = 720,
            defaultEndTime = 780,
            sortOrder = 1,
        )
        every { lifeActivityDao.getAllActivitiesFlow() } returns flowOf(listOf(entity))
        repository = createRepository()

        // Act & Assert
        repository.allLifeActivities.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Meal", result[0].name)
            assertEquals(60, result[0].durationMinutes)
            awaitComplete()
        }
    }

    @Test
    fun saveLifeActivity_whenCalled_convertsToEntityAndCallsDao() = runTest {
        // Arrange
        repository = createRepository()
        val activity = LifeActivity(
            id = "la_1",
            name = "Exercise",
            durationMinutes = 60,
            color = "#E91E63",
            sortOrder = 5,
        )

        // Act
        repository.saveLifeActivity(activity)

        // Assert
        coVerify { 
            lifeActivityDao.insertActivity(
                match { 
                    (it.id == "la_1") && (it.name == "Exercise") && (it.sortOrder == 5)
                },
            )
        }
    }

    @Test
    fun deleteLifeActivityById_whenCalled_callsDaoDelete() = runTest {
        // Arrange
        repository = createRepository()
        val id = "la_id"

        // Act
        repository.deleteLifeActivityById(id)

        // Assert
        coVerify { lifeActivityDao.deleteActivityById(id) }
    }

}
