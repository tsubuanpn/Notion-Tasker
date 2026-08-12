package com.notiontasks.app.data.repository

import com.notiontasks.app.data.local.PomodoroLogDao
import com.notiontasks.app.data.local.PomodoroLogEntity
import com.notiontasks.app.data.PomodoroLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PomodoroRepository(
    private val pomodoroLogDao: PomodoroLogDao,
) {
    val allLogsFlow: Flow<List<PomodoroLog>> = pomodoroLogDao.getAllLogsFlow()
        .map { entities -> entities.map { it.toDomainModel() } }

    suspend fun loadPomodoroLogs(): List<PomodoroLog> = withContext(Dispatchers.IO) {
        pomodoroLogDao.getAllLogs().map { it.toDomainModel() }
    }

    suspend fun insertPomodoroLog(log: PomodoroLog) = withContext(Dispatchers.IO) {
        pomodoroLogDao.insertLog(log.toEntity())
    }

    suspend fun deletePomodoroLogById(id: String) = withContext(Dispatchers.IO) {
        pomodoroLogDao.deleteLogById(id)
    }

    suspend fun deletePomodoroLogsByIds(ids: List<String>) = withContext(Dispatchers.IO) {
        pomodoroLogDao.deleteLogsByIds(ids)
    }

    suspend fun deleteLogsOlderThan(timestamp: Long) = withContext(Dispatchers.IO) {
        pomodoroLogDao.deleteLogsOlderThan(timestamp)
    }

    @Deprecated("統計データ消失の原因となるため、差分更新（insertPomodoroLog）を使用してください")
    suspend fun savePomodoroLogs(logs: List<PomodoroLog>) = withContext(Dispatchers.IO) {
        pomodoroLogDao.clearAllLogs()
        pomodoroLogDao.insertLogs(logs.map { it.toEntity() })
    }

    suspend fun clearAllLogs() = withContext(Dispatchers.IO) {
        pomodoroLogDao.clearAllLogs()
    }

    private fun PomodoroLogEntity.toDomainModel() = PomodoroLog(
        id = id,
        taskId = taskId,
        taskTitle = taskTitle,
        category = category,
        categoryColor = categoryColor,
        date = date,
        minutes = minutes,
        timestamp = timestamp,
    )

    private fun PomodoroLog.toEntity() = PomodoroLogEntity(
        id = id,
        taskId = taskId,
        taskTitle = taskTitle,
        category = category,
        categoryColor = categoryColor,
        date = date,
        minutes = minutes,
        timestamp = timestamp,
    )
}
