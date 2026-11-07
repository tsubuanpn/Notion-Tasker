package com.notiontasks.app

import android.app.Application
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.remote.NotionApi
import com.notiontasks.app.data.repository.PomodoroRepository
import com.notiontasks.app.data.repository.ScheduleRepository
import com.notiontasks.app.data.repository.TaskRepository
import com.notiontasks.app.utils.SecurityUtils
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val networkJson = Json { ignoreUnknownKeys = true }

/** Application スコープで共有する、依存関係の最小コンテナ。 */
class NotionTaskerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val securePreferences = SecurityUtils.getSecurePreferences(application)

    private val database = TaskDatabase.getInstance(application)
    private val notionApi = Retrofit.Builder()
        .baseUrl("https://api.notion.com/")
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NotionApi::class.java)

    val taskRepository = TaskRepository(
        notionApi = notionApi,
        taskDao = database.taskDao,
        pendingSyncActionDao = database.pendingSyncActionDao,
        syncMetadataDao = database.syncMetadataDao,
        taskIdAliasDao = database.taskIdAliasDao,
        taskFieldGenerationDao = database.taskFieldGenerationDao,
    )
    val scheduleRepository = ScheduleRepository(
        scheduleBlockDao = database.scheduleBlockDao,
        lifeActivityDao = database.lifeActivityDao,
    )
    val pomodoroRepository = PomodoroRepository(database.pomodoroLogDao)
}
