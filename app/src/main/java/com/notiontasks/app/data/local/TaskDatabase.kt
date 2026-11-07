package com.notiontasks.app.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// TaskEntity は、キャッシュ用のローカル SQLite テーブルの行を表します
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,      // 生の列挙型文字列または値
    val category: String,    // 生の選択オプションラベル
    val dueDate: String?,
    val scheduledDate: String?,
    val statusColor: String? = null,
    val categoryColor: String? = null,
    val lastEditedTime: Long = 0L,
    /** Database ID that last verified this row. Null means legacy/unknown and is never sent. */
    val connectionScope: String? = null,
)

@Entity(tableName = "pomodoro_logs")
data class PomodoroLogEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val taskTitle: String?,
    val category: String,
    val categoryColor: String?,
    val date: String,
    val minutes: Int,
    val timestamp: Long,
    val sessionType: String = "pomodoro",
)

// スケジュールのタイムブロック用エンティティ
@Entity(tableName = "schedule_blocks", indices = [Index(value = ["date"])])
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    val type: String, // "task" または "life"
    val title: String,
    val associatedId: String? = null,
    val startTime: Int, // 深夜零時からの経過分
    val endTime: Int,
    val color: String,
    val date: String, // "yyyy-MM-dd"
)

// 生活習慣マスタ用エンティティ
@Entity(tableName = "life_activities")
data class LifeActivityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val durationMinutes: Int,
    val color: String,
    val defaultStartTime: Int? = null,
    val defaultEndTime: Int? = null,
    val sortOrder: Int = 0,
)

// オフライン時・リトライ用の未同期アクションキュー用エンティティ
@Entity(tableName = "pending_sync_actions")
data class PendingSyncActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // "UPDATE_STATUS", "UPDATE_TASK", "CREATE_TASK" 等
    val taskId: String,
    val payloadJson: String,
    val timestamp: Long,
    /** Database ID that owned this operation when it was queued. */
    val connectionScope: String? = null,
    /** Stable user-intent identity; legacy rows are assigned during migration. */
    val operationId: String = "",
    /** Comma-separated fields intentionally changed by this operation; "legacy" is unknown. */
    val changedFields: String = "legacy",
    /** Monotonic per task and connection while the app is running. */
    val generation: Long = 0L,
    /** PENDING, RETRYABLE_FAILURE, or PERMANENT_FAILURE. */
    val state: String = "PENDING",
    val lastError: String? = null,
)

/** Resolves a locally generated task ID to its Notion page ID within one connection. */
@Entity(
    tableName = "task_id_aliases",
    primaryKeys = ["localTaskId", "connectionScope"],
    indices = [Index(value = ["remoteTaskId", "connectionScope"])],
)
data class TaskIdAliasEntity(
    val localTaskId: String,
    val remoteTaskId: String,
    val connectionScope: String,
)

/** リモート由来の同期時刻だけを保持するためのメタデータです。 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val timestamp: Long,
)

// TaskDao は、コンパイル時の SQL 検証とクエリを処理します
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status")
    fun getTasksByStatus(status: String): Flow<List<TaskEntity>>

    @Upsert
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE connectionScope = :connectionScope AND id NOT IN (:remoteIds) AND id NOT IN (:protectedIds)")
    suspend fun deleteTasksNotInList(connectionScope: String, remoteIds: List<String>, protectedIds: List<String>)

    @Transaction
    suspend fun syncTasksTransactionally(
        connectionScope: String,
        activeEntities: List<TaskEntity>,
        protectedTaskIds: List<String> = emptyList(),
    ) {
        upsertTasks(activeEntities)
        deleteTasksNotInList(connectionScope, activeEntities.map { it.id }, protectedTaskIds)
    }

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Query("UPDATE tasks SET status = :status, statusColor = :statusColor, lastEditedTime = :lastEditedTime WHERE id = :id")
    suspend fun updateTaskStatusLocal(id: String, status: String, statusColor: String?, lastEditedTime: Long)

    @Query("SELECT statusColor FROM tasks WHERE status = :status AND statusColor IS NOT NULL LIMIT 1")
    suspend fun getStatusColorForStatus(status: String): String?

    @Query("SELECT categoryColor FROM tasks WHERE category = :category AND categoryColor IS NOT NULL LIMIT 1")
    suspend fun getCategoryColorForCategory(category: String): String?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun getTaskFlowById(id: String): Flow<TaskEntity?>

    @Query("DELETE FROM tasks WHERE status = :completedStatus AND lastEditedTime < :cutoffTimestamp")
    suspend fun deleteCompletedTasksBefore(completedStatus: String, cutoffTimestamp: Long)

    @Query("DELETE FROM tasks WHERE status = :completedStatus AND id NOT IN (SELECT id FROM tasks WHERE status = :completedStatus ORDER BY lastEditedTime DESC LIMIT :maxCount)")
    suspend fun keepOnlyLatestCompletedTasks(completedStatus: String, maxCount: Int)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Dao
interface PomodoroLogDao {
    @Query("SELECT * FROM pomodoro_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<PomodoroLogEntity>>

    @Query("SELECT * FROM pomodoro_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<PomodoroLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PomodoroLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<PomodoroLogEntity>)

    @Query("DELETE FROM pomodoro_logs WHERE id = :id")
    suspend fun deleteLogById(id: String)

    @Query("DELETE FROM pomodoro_logs WHERE id IN (:ids)")
    suspend fun deleteLogsByIds(ids: List<String>)

    @Query("DELETE FROM pomodoro_logs WHERE timestamp < :timestamp")
    suspend fun deleteLogsOlderThan(timestamp: Long)

    @Query("DELETE FROM pomodoro_logs")
    suspend fun clearAllLogs()
}

@Dao
interface ScheduleBlockDao {
    @Query("SELECT * FROM schedule_blocks ORDER BY startTime ASC")
    fun getAllBlocksFlow(): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE date = :date ORDER BY startTime ASC")
    fun getBlocksByDateFlow(date: String): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks ORDER BY startTime ASC")
    suspend fun getAllBlocks(): List<ScheduleBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: ScheduleBlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<ScheduleBlockEntity>)

    @Query("DELETE FROM schedule_blocks WHERE id = :id")
    suspend fun deleteBlockById(id: String)

    @Query("DELETE FROM schedule_blocks WHERE date = :date")
    suspend fun deleteBlocksByDate(date: String)

    @Query("DELETE FROM schedule_blocks")
    suspend fun clearAllBlocks()
}

@Dao
interface LifeActivityDao {
    @Query("SELECT * FROM life_activities ORDER BY sortOrder ASC")
    fun getAllActivitiesFlow(): Flow<List<LifeActivityEntity>>

    @Query("SELECT * FROM life_activities ORDER BY sortOrder ASC")
    suspend fun getAllActivities(): List<LifeActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: LifeActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<LifeActivityEntity>)

    @Query("DELETE FROM life_activities WHERE id = :id")
    suspend fun deleteActivityById(id: String)

    @Query("DELETE FROM life_activities")
    suspend fun clearAllActivities()
}

@Dao
interface PendingSyncActionDao {
    @Query("SELECT COUNT(*) FROM pending_sync_actions")
    fun pendingActionCountFlow(): Flow<Int>

    @Query("SELECT * FROM pending_sync_actions ORDER BY timestamp ASC")
    fun getAllPendingActionsFlow(): Flow<List<PendingSyncActionEntity>>

    @Query("SELECT * FROM pending_sync_actions ORDER BY timestamp ASC")
    suspend fun getAllPendingActions(): List<PendingSyncActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingAction(action: PendingSyncActionEntity)

    @Query("SELECT MAX(generation) FROM pending_sync_actions WHERE taskId = :taskId AND connectionScope = :connectionScope")
    suspend fun getLatestGeneration(taskId: String, connectionScope: String): Long?

    @Query("UPDATE pending_sync_actions SET state = :state, lastError = :lastError WHERE id = :id")
    suspend fun updatePendingActionState(id: Long, state: String, lastError: String?)

    @Query("DELETE FROM pending_sync_actions WHERE id = :id")
    suspend fun deletePendingActionById(id: Long)

    @Query("DELETE FROM pending_sync_actions WHERE actionType = :actionType AND taskId = :taskId")
    suspend fun deletePendingActionsForTask(actionType: String, taskId: String)

    @Query("DELETE FROM pending_sync_actions")
    suspend fun clearAllPendingActions()
}

/**
 * Keeps every local reference on a task together when an offline CREATE receives its Notion ID.
 * The alias remains afterwards so a queued action restored after process death still resolves.
 */
@Dao
interface TaskIdAliasDao {
    @Query("SELECT remoteTaskId FROM task_id_aliases WHERE localTaskId = :localTaskId AND connectionScope = :connectionScope LIMIT 1")
    suspend fun getRemoteTaskId(localTaskId: String, connectionScope: String): String?

    @Query("SELECT remoteTaskId FROM task_id_aliases WHERE localTaskId = :localTaskId AND connectionScope = :connectionScope LIMIT 1")
    fun observeRemoteTaskId(localTaskId: String, connectionScope: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: TaskIdAliasEntity)

    @Query("UPDATE pending_sync_actions SET taskId = :remoteTaskId WHERE taskId = :localTaskId AND connectionScope = :connectionScope")
    suspend fun replacePendingTaskReferences(localTaskId: String, remoteTaskId: String, connectionScope: String)

    @Query("UPDATE schedule_blocks SET associatedId = :remoteTaskId WHERE type = 'task' AND associatedId = :localTaskId")
    suspend fun replaceScheduleTaskReferences(localTaskId: String, remoteTaskId: String)

    @Query("UPDATE pomodoro_logs SET taskId = :remoteTaskId WHERE taskId = :localTaskId")
    suspend fun replacePomodoroTaskReferences(localTaskId: String, remoteTaskId: String)

    @Query("DELETE FROM tasks WHERE id = :localTaskId")
    suspend fun deleteTemporaryTask(localTaskId: String)

    @Upsert
    suspend fun upsertResolvedTask(task: TaskEntity)

    @Transaction
    suspend fun resolveCreatedTask(
        localTaskId: String,
        remoteTask: TaskEntity,
        connectionScope: String,
    ) {
        upsertAlias(TaskIdAliasEntity(localTaskId, remoteTask.id, connectionScope))
        replacePendingTaskReferences(localTaskId, remoteTask.id, connectionScope)
        replaceScheduleTaskReferences(localTaskId, remoteTask.id)
        replacePomodoroTaskReferences(localTaskId, remoteTask.id)
        deleteTemporaryTask(localTaskId)
        upsertResolvedTask(remoteTask)
    }
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT timestamp FROM sync_metadata WHERE key = :key LIMIT 1")
    suspend fun getTimestamp(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: SyncMetadataEntity)
}

// SQL データソースを表す Room データベース
@Database(
    entities = [
        TaskEntity::class,
        PomodoroLogEntity::class,
        ScheduleBlockEntity::class,
        LifeActivityEntity::class,
        PendingSyncActionEntity::class,
        TaskIdAliasEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class TaskDatabase : RoomDatabase() {
    abstract val taskDao: TaskDao
    abstract val pomodoroLogDao: PomodoroLogDao
    abstract val scheduleBlockDao: ScheduleBlockDao
    abstract val lifeActivityDao: LifeActivityDao
    abstract val pendingSyncActionDao: PendingSyncActionDao
    abstract val taskIdAliasDao: TaskIdAliasDao
    abstract val syncMetadataDao: SyncMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN statusColor TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN categoryColor TEXT")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pomodoro_logs (id TEXT NOT NULL, taskId TEXT, taskTitle TEXT, category TEXT NOT NULL, categoryColor TEXT, date TEXT NOT NULL, minutes INTEGER NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(id))",
                )
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS schedule_blocks (id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, associatedId TEXT, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, color TEXT NOT NULL, date TEXT NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_blocks_date ON schedule_blocks (date)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS life_activities (id TEXT NOT NULL, name TEXT NOT NULL, durationMinutes INTEGER NOT NULL, color TEXT NOT NULL, defaultStartTime INTEGER, defaultEndTime INTEGER, PRIMARY KEY(id))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_sync_actions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, actionType TEXT NOT NULL, taskId TEXT NOT NULL, payloadJson TEXT NOT NULL, timestamp INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE life_activities ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN lastEditedTime INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pomodoro_logs ADD COLUMN sessionType TEXT NOT NULL DEFAULT 'pomodoro'")
            }
        }

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_metadata` (`key` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
            }
        }

        /** Old rows have no trustworthy connection ownership, so retain them as unowned. */
        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN connectionScope TEXT")
                db.execSQL("ALTER TABLE pending_sync_actions ADD COLUMN connectionScope TEXT")
            }
        }

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS task_id_aliases (localTaskId TEXT NOT NULL, remoteTaskId TEXT NOT NULL, connectionScope TEXT NOT NULL, PRIMARY KEY(localTaskId, connectionScope))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_id_aliases_remoteTaskId_connectionScope ON task_id_aliases (remoteTaskId, connectionScope)",
                )
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_sync_actions ADD COLUMN operationId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pending_sync_actions ADD COLUMN changedFields TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL("ALTER TABLE pending_sync_actions ADD COLUMN generation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pending_sync_actions ADD COLUMN state TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE pending_sync_actions ADD COLUMN lastError TEXT")
                db.execSQL("UPDATE pending_sync_actions SET operationId = 'legacy-' || id WHERE operationId = ''")
            }
        }

        private fun createDatabase(context: Context, name: String): TaskDatabase =
            Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java, name)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                )
                .build()

        /** 本番と同じ移行設定を使う、名前を分離したインストルメンテーションテスト用DB。 */
        internal fun createForTesting(context: Context, name: String): TaskDatabase = createDatabase(context, name)

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDatabase(context, "notion_tasks_cache.db").also {
                    INSTANCE = it
                }
            }
        }
    }
}
