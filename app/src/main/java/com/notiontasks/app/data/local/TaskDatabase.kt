package com.notiontasks.app.data.local

import android.content.Context
import androidx.room.*
import com.notiontasks.app.data.model.TaskCategory
import com.notiontasks.app.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow

// TaskEntity represents a row in the local SQLite table for caching
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,      // Raw enum string or value
    val category: String,    // Raw Select option label
    val dueDate: String?,
    val scheduledDate: String?
)

// TaskDao handles compile-time SQL verification and queries
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

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatusLocal(id: String, status: String)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

// Room Database representing the SQL data source
@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract val taskDao: TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "notion_tasks_cache.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
