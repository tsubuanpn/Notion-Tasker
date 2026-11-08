package com.notiontasks.app.data.repository

import com.notiontasks.app.data.local.LifeActivityDao
import com.notiontasks.app.data.local.LifeActivityEntity
import com.notiontasks.app.data.local.ScheduleBlockDao
import com.notiontasks.app.data.local.ScheduleBlockEntity
import com.notiontasks.app.data.model.LifeActivity
import com.notiontasks.app.data.model.TimeBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Contract consumed by view models and legacy migration; Room remains an implementation detail. */
interface ScheduleStore {
    val allTimeBlocks: Flow<List<TimeBlock>>
    val allLifeActivities: Flow<List<LifeActivity>>

    suspend fun saveTimeBlocks(blocks: List<TimeBlock>)
    suspend fun saveTimeBlock(block: TimeBlock)
    suspend fun getAllTimeBlocks(): List<TimeBlock>
    suspend fun getTimeBlocksForDate(date: String): List<TimeBlock>
    suspend fun deleteTimeBlockById(id: String)
    suspend fun loadLifeActivities(): List<LifeActivity>
    suspend fun saveLifeActivities(activities: List<LifeActivity>)
    suspend fun saveLifeActivity(activity: LifeActivity)
    suspend fun deleteLifeActivityById(id: String)
}

class ScheduleRepository(
    private val scheduleBlockDao: ScheduleBlockDao,
    private val lifeActivityDao: LifeActivityDao,
) : ScheduleStore {
    override val allTimeBlocks: Flow<List<TimeBlock>> = scheduleBlockDao.getAllBlocksFlow()
        .map { entities ->
            entities.map { it.toDomainModel() }
        }
        .flowOn(Dispatchers.Default)

    override suspend fun saveTimeBlocks(blocks: List<TimeBlock>) = withContext(Dispatchers.IO) {
        scheduleBlockDao.insertBlocks(blocks.map { it.toEntity() })
    }

    override suspend fun saveTimeBlock(block: TimeBlock) = withContext(Dispatchers.IO) {
        scheduleBlockDao.insertBlock(block.toEntity())
    }

    override suspend fun getAllTimeBlocks(): List<TimeBlock> = withContext(Dispatchers.IO) {
        scheduleBlockDao.getAllBlocks().map { it.toDomainModel() }
    }

    override suspend fun getTimeBlocksForDate(date: String): List<TimeBlock> = withContext(Dispatchers.IO) {
        scheduleBlockDao.getBlocksByDate(date).map { it.toDomainModel() }
    }

    override suspend fun deleteTimeBlockById(id: String) = withContext(Dispatchers.IO) {
        scheduleBlockDao.deleteBlockById(id)
    }

    override val allLifeActivities: Flow<List<LifeActivity>> = lifeActivityDao.getAllActivitiesFlow()
        .map { entities ->
            entities.map { it.toDomainModel() }
        }
        .flowOn(Dispatchers.Default)

    override suspend fun loadLifeActivities(): List<LifeActivity> = withContext(Dispatchers.IO) {
        lifeActivityDao.getAllActivities().map { it.toDomainModel() }
    }

    override suspend fun saveLifeActivities(activities: List<LifeActivity>) = withContext(Dispatchers.IO) {
        lifeActivityDao.insertActivities(activities.map { it.toEntity() })
    }

    override suspend fun saveLifeActivity(activity: LifeActivity) = withContext(Dispatchers.IO) {
        lifeActivityDao.insertActivity(activity.toEntity())
    }

    override suspend fun deleteLifeActivityById(id: String) = withContext(Dispatchers.IO) {
        lifeActivityDao.deleteActivityById(id)
    }

    private fun ScheduleBlockEntity.toDomainModel() = TimeBlock(
        id = id,
        type = type,
        title = title,
        associatedId = associatedId,
        startTime = startTime,
        endTime = endTime,
        color = color,
        date = date,
        origin = origin,
        presetId = presetId,
        presetGeneration = presetGeneration,
    )

    private fun TimeBlock.toEntity() = ScheduleBlockEntity(
        id = id,
        type = type,
        title = title,
        associatedId = associatedId,
        startTime = startTime,
        endTime = endTime,
        color = color,
        date = date,
        origin = origin,
        presetId = presetId,
        presetGeneration = presetGeneration,
    )

    private fun LifeActivityEntity.toDomainModel() = LifeActivity(
        id = id,
        name = name,
        durationMinutes = durationMinutes,
        color = color,
        defaultStartTime = defaultStartTime,
        defaultEndTime = defaultEndTime,
        sortOrder = sortOrder,
    )

    private fun LifeActivity.toEntity() = LifeActivityEntity(
        id = id,
        name = name,
        durationMinutes = durationMinutes,
        color = color,
        defaultStartTime = defaultStartTime,
        defaultEndTime = defaultEndTime,
        sortOrder = sortOrder,
    )
}
