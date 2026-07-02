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
    val categoryColor: String? = null
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
    val timestamp: Long
)

// TaskDao は、コンパイル時の SQL 検証とクエリを処理します
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status")
    fun getTasksByStatus(status: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

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

    @Query("DELETE FROM pomodoro_logs")
    suspend fun clearAllLogs()
}

// SQL データソースを表す Room データベース
@Database(entities = [TaskEntity::class, PomodoroLogEntity::class], version = 3, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract val taskDao: TaskDao
    abstract val pomodoroLogDao: PomodoroLogDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "notion_tasks_cache.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
