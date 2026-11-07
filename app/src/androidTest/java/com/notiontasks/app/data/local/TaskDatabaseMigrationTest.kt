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
    fun migratesV1ToV9_keepsOriginalTaskAndCreatesNewTables() = runBlocking {
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacyDatabase ->
            legacyDatabase.execSQL(
                "CREATE TABLE tasks (id TEXT NOT NULL, title TEXT NOT NULL, status TEXT NOT NULL, category TEXT NOT NULL, dueDate TEXT, scheduledDate TEXT, PRIMARY KEY(id))",
            )
            legacyDatabase.execSQL(
                "INSERT INTO tasks (id, title, status, category, dueDate, scheduledDate) VALUES ('legacy-task', '旧タスク', '未着手', '学習', '2026-11-10', '2026-11-03')",
            )
            legacyDatabase.execSQL("PRAGMA user_version = 1")
        }

        val migratedDatabase = TaskDatabase.createForTesting(context, databaseName)
        try {
            val task = migratedDatabase.taskDao.getTaskById("legacy-task")
            assertEquals("旧タスク", task?.title)
            assertEquals(null, task?.statusColor)
            assertEquals(null, task?.connectionScope)
            assertEquals(0, migratedDatabase.pomodoroLogDao.getAllLogs().size)
            assertEquals(0, migratedDatabase.pendingSyncActionDao.getAllPendingActions().size)
        } finally {
            migratedDatabase.close()
        }
    }

    @Test
    fun migratesV8ToV9_keepsCachedDataAndMarksItUnowned() = runBlocking {
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacyDatabase ->
            legacyDatabase.execSQL("CREATE TABLE tasks (id TEXT NOT NULL, title TEXT NOT NULL, status TEXT NOT NULL, category TEXT NOT NULL, dueDate TEXT, scheduledDate TEXT, statusColor TEXT, categoryColor TEXT, lastEditedTime INTEGER NOT NULL, PRIMARY KEY(id))")
            legacyDatabase.execSQL("CREATE TABLE pomodoro_logs (id TEXT NOT NULL, taskId TEXT, taskTitle TEXT, category TEXT NOT NULL, categoryColor TEXT, date TEXT NOT NULL, minutes INTEGER NOT NULL, timestamp INTEGER NOT NULL, sessionType TEXT NOT NULL, PRIMARY KEY(id))")
            legacyDatabase.execSQL("CREATE TABLE schedule_blocks (id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, associatedId TEXT, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, color TEXT NOT NULL, date TEXT NOT NULL, PRIMARY KEY(id))")
            legacyDatabase.execSQL("CREATE INDEX index_schedule_blocks_date ON schedule_blocks (date)")
            legacyDatabase.execSQL("CREATE TABLE life_activities (id TEXT NOT NULL, name TEXT NOT NULL, durationMinutes INTEGER NOT NULL, color TEXT NOT NULL, defaultStartTime INTEGER, defaultEndTime INTEGER, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))")
            legacyDatabase.execSQL("CREATE TABLE pending_sync_actions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, actionType TEXT NOT NULL, taskId TEXT NOT NULL, payloadJson TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            legacyDatabase.execSQL("CREATE TABLE sync_metadata (`key` TEXT NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(`key`))")
            legacyDatabase.execSQL("INSERT INTO tasks VALUES ('task-before-migration', '移行前タスク', '未着手', '学習', '2026-11-10', '2026-11-03', NULL, NULL, 1234)")
            legacyDatabase.execSQL("INSERT INTO pending_sync_actions (actionType, taskId, payloadJson, timestamp) VALUES ('UPDATE_TASK', 'task-before-migration', '{\"title\":\"移行前タスク\"}', 5678)")
            legacyDatabase.execSQL("PRAGMA user_version = 8")
        }

        val migratedDatabase = TaskDatabase.createForTesting(context, databaseName)
        try {
            val task = migratedDatabase.taskDao.getTaskById("task-before-migration")
            assertEquals("移行前タスク", task?.title)
            assertEquals(1234L, task?.lastEditedTime)
            assertEquals(null, task?.connectionScope)
            assertEquals(1, migratedDatabase.pendingSyncActionDao.getAllPendingActions().size)

            migratedDatabase.syncMetadataDao.put(SyncMetadataEntity("remote_cursor", 9999L))
            assertEquals(9999L, migratedDatabase.syncMetadataDao.getTimestamp("remote_cursor"))
        } finally {
            migratedDatabase.close()
        }
    }
}
