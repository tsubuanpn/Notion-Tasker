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

    @Query("DELETE FROM tasks WHERE id NOT IN (:ids)")
    suspend fun deleteTasksNotInList(ids: List<String>)

    @Transaction
    suspend fun syncTasksTransactionally(activeEntities: List<TaskEntity>) {
        upsertTasks(activeEntities)
        deleteTasksNotInList(activeEntities.map { it.id })
    }

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Query("UPDATE tasks SET status = :status, statusColor = :statusColor WHERE id = :id")
    suspend fun updateTaskStatusLocal(id: String, status: String, statusColor: String?)

    @Query("SELECT statusColor FROM tasks WHERE status = :status AND statusColor IS NOT NULL LIMIT 1")
    suspend fun getStatusColorForStatus(status: String): String?

    @Query("SELECT categoryColor FROM tasks WHERE category = :category AND categoryColor IS NOT NULL LIMIT 1")
    suspend fun getCategoryColorForCategory(category: String): String?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

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
    @Query("SELECT * FROM pending_sync_actions ORDER BY timestamp ASC")
    suspend fun getAllPendingActions(): List<PendingSyncActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingAction(action: PendingSyncActionEntity)

    @Query("DELETE FROM pending_sync_actions WHERE id = :id")
    suspend fun deletePendingActionById(id: Long)

    @Query("DELETE FROM pending_sync_actions")
    suspend fun clearAllPendingActions()
}

// SQL データソースを表す Room データベース
@Database(
    entities = [
        TaskEntity::class,
        PomodoroLogEntity::class,
        ScheduleBlockEntity::class,
        LifeActivityEntity::class,
        PendingSyncActionEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class TaskDatabase : RoomDatabase() {
    abstract val taskDao: TaskDao
    abstract val pomodoroLogDao: PomodoroLogDao
    abstract val scheduleBlockDao: ScheduleBlockDao
    abstract val lifeActivityDao: LifeActivityDao
    abstract val pendingSyncActionDao: PendingSyncActionDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE life_activities ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "notion_tasks_cache.db",
                )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
