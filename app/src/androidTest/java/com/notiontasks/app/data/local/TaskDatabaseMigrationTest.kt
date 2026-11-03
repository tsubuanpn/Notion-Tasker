package com.notiontasks.app.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "task-database-migration-test.db"

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesV7ToV8_keepsCachedDataAndCreatesSyncMetadata() = runBlocking {
        context.deleteDatabase(databaseName)
        val currentDatabase = TaskDatabase.createForTesting(context, databaseName)
        try {
            currentDatabase.taskDao.upsertTasks(
                listOf(
                    TaskEntity(
                        id = "task-before-migration",
                        title = "移行前タスク",
                        status = "未着手",
                        category = "学習",
                        dueDate = "2026-11-10",
                        scheduledDate = "2026-11-03",
                        lastEditedTime = 1234L,
                    ),
                ),
            )
            currentDatabase.pendingSyncActionDao.insertPendingAction(
                PendingSyncActionEntity(
                    actionType = "UPDATE_TASK",
                    taskId = "task-before-migration",
                    payloadJson = "{\"title\":\"移行前タスク\"}",
                    timestamp = 5678L,
                ),
            )
        } finally {
            currentDatabase.close()
        }

        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacyDatabase ->
            legacyDatabase.execSQL("DROP TABLE sync_metadata")
            legacyDatabase.execSQL("PRAGMA user_version = 7")
        }

        val migratedDatabase = TaskDatabase.createForTesting(context, databaseName)
        try {
            val task = migratedDatabase.taskDao.getTaskById("task-before-migration")
            assertEquals("移行前タスク", task?.title)
            assertEquals(1234L, task?.lastEditedTime)
            assertEquals(1, migratedDatabase.pendingSyncActionDao.getAllPendingActions().size)

            migratedDatabase.syncMetadataDao.put(SyncMetadataEntity("remote_cursor", 9999L))
            assertEquals(9999L, migratedDatabase.syncMetadataDao.getTimestamp("remote_cursor"))
        } finally {
            migratedDatabase.close()
        }
    }
}
