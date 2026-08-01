package com.notiontasks.app.data.repository

import com.notiontasks.app.data.local.PomodoroLogDao
import com.notiontasks.app.data.local.PomodoroLogEntity
import com.notiontasks.app.data.PomodoroLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PomodoroRepository(
    private val pomodoroLogDao: PomodoroLogDao,
) {
    suspend fun loadPomodoroLogs(): List<PomodoroLog> = withContext(Dispatchers.IO) {
        pomodoroLogDao.getAllLogs().map { it.toDomainModel() }
    }

    suspend fun savePomodoroLogs(logs: List<PomodoroLog>) = withContext(Dispatchers.IO) {
        pomodoroLogDao.clearAllLogs()
        pomodoroLogDao.insertLogs(logs.map { it.toEntity() })
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
