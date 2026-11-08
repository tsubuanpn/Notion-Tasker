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
    fun migratesV1ToCurrent_keepsOriginalTaskAndCreatesNewTables() = runBlocking {
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
    fun migratesV8ToCurrent_keepsCachedDataAndMarksItUnowned() = runBlocking {
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
            legacyDatabase.execSQL("INSERT INTO schedule_blocks (id, type, title, associatedId, startTime, endTime, color, date) VALUES ('legacy-block', 'life', '旧予定', 'la_old', 60, 120, '#123456', '2026-11-03')")
            legacyDatabase.execSQL("PRAGMA user_version = 8")
        }

        val migratedDatabase = TaskDatabase.createForTesting(context, databaseName)
        try {
            val task = migratedDatabase.taskDao.getTaskById("task-before-migration")
            assertEquals("移行前タスク", task?.title)
            assertEquals(1234L, task?.lastEditedTime)
            assertEquals(null, task?.connectionScope)
            val pending = migratedDatabase.pendingSyncActionDao.getAllPendingActions().single()
            assertEquals("legacy-1", pending.operationId)
            assertEquals("legacy", pending.changedFields)
            assertEquals("PENDING", pending.state)
            val migratedBlock = migratedDatabase.scheduleBlockDao.getAllBlocks().single()
            assertEquals("legacy-block", migratedBlock.id)
            assertEquals("legacy", migratedBlock.origin)
            assertEquals(null, migratedBlock.presetId)
            assertEquals(0L, migratedBlock.presetGeneration)

            migratedDatabase.syncMetadataDao.put(SyncMetadataEntity("remote_cursor", 9999L))
            assertEquals(9999L, migratedDatabase.syncMetadataDao.getTimestamp("remote_cursor"))
            migratedDatabase.taskIdAliasDao.upsertAlias(
                TaskIdAliasEntity("pending_task", "page_123", "database"),
            )
            assertEquals(
                "page_123",
                migratedDatabase.taskIdAliasDao.getRemoteTaskId("pending_task", "database"),
            )
        } finally {
            migratedDatabase.close()
        }
    }

    @Test
    fun migratesV12ToCurrent_preservesOwnedQueueAndMarksExistingBlocksLegacy() = runBlocking {
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL("CREATE TABLE tasks (id TEXT NOT NULL, title TEXT NOT NULL, status TEXT NOT NULL, category TEXT NOT NULL, dueDate TEXT, scheduledDate TEXT, statusColor TEXT, categoryColor TEXT, lastEditedTime INTEGER NOT NULL, connectionScope TEXT, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE pomodoro_logs (id TEXT NOT NULL, taskId TEXT, taskTitle TEXT, category TEXT NOT NULL, categoryColor TEXT, date TEXT NOT NULL, minutes INTEGER NOT NULL, timestamp INTEGER NOT NULL, sessionType TEXT NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE schedule_blocks (id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, associatedId TEXT, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, color TEXT NOT NULL, date TEXT NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE INDEX index_schedule_blocks_date ON schedule_blocks (date)")
            database.execSQL("CREATE TABLE life_activities (id TEXT NOT NULL, name TEXT NOT NULL, durationMinutes INTEGER NOT NULL, color TEXT NOT NULL, defaultStartTime INTEGER, defaultEndTime INTEGER, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE pending_sync_actions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, actionType TEXT NOT NULL, taskId TEXT NOT NULL, payloadJson TEXT NOT NULL, timestamp INTEGER NOT NULL, connectionScope TEXT, operationId TEXT NOT NULL, changedFields TEXT NOT NULL, generation INTEGER NOT NULL, state TEXT NOT NULL, lastError TEXT)")
            database.execSQL("CREATE TABLE task_id_aliases (localTaskId TEXT NOT NULL, remoteTaskId TEXT NOT NULL, connectionScope TEXT NOT NULL, PRIMARY KEY(localTaskId, connectionScope))")
            database.execSQL("CREATE INDEX index_task_id_aliases_remoteTaskId_connectionScope ON task_id_aliases (remoteTaskId, connectionScope)")
            database.execSQL("CREATE TABLE task_field_generations (taskId TEXT NOT NULL, connectionScope TEXT NOT NULL, field TEXT NOT NULL, generation INTEGER NOT NULL, PRIMARY KEY(taskId, connectionScope, field))")
            database.execSQL("CREATE TABLE sync_metadata (`key` TEXT NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(`key`))")
            database.execSQL("INSERT INTO tasks VALUES ('owned-task', '所有タスク', '進行中', '学習', NULL, NULL, NULL, NULL, 55, 'verified-db')")
            database.execSQL("INSERT INTO schedule_blocks VALUES ('old-block', 'life', '既存枠', 'preset', 600, 660, '#123456', '2026-11-08')")
            database.execSQL("INSERT INTO pending_sync_actions (actionType, taskId, payloadJson, timestamp, connectionScope, operationId, changedFields, generation, state, lastError) VALUES ('UPDATE_TASK', 'owned-task', '{}', 1, 'verified-db', 'operation', 'title', 4, 'PERMANENT_FAILURE', 'bad mapping')")
            database.execSQL("INSERT INTO task_id_aliases VALUES ('pending-task', 'page-1', 'verified-db')")
            database.execSQL("INSERT INTO task_field_generations VALUES ('owned-task', 'verified-db', 'title', 4)")
            database.execSQL("PRAGMA user_version = 12")
        }

        val migrated = TaskDatabase.createForTesting(context, databaseName)
        try {
            assertEquals("verified-db", migrated.taskDao.getTaskById("owned-task")?.connectionScope)
            assertEquals("PERMANENT_FAILURE", migrated.pendingSyncActionDao.getAllPendingActions().single().state)
            assertEquals("page-1", migrated.taskIdAliasDao.getRemoteTaskId("pending-task", "verified-db"))
            assertEquals(4L, migrated.taskFieldGenerationDao.getFieldGeneration("owned-task", "verified-db", "title"))
            val block = migrated.scheduleBlockDao.getBlockById("old-block")
            assertEquals("legacy", block?.origin)
            assertEquals(null, block?.presetId)
        } finally {
            migrated.close()
        }
    }
}
