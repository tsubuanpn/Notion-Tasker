package com.notiontasks.app

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notiontasks.app.data.local.ScheduleBlockEntity
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.local.TaskEntity
import com.notiontasks.app.utils.JapanTime
import com.notiontasks.app.utils.SecurityUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskNotificationReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = TaskDatabase.getInstance(context)
    private val taskId = "instrumented-notification-task"
    private val blockId = "instrumented-notification-block"
    private val notificationId = 3_000 + blockId.hashCode()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @After
    fun cleanUp() {
        runBlocking {
            database.scheduleBlockDao.deleteBlockById(blockId)
            database.taskDao.deleteTaskById(taskId)
        }
        notificationManager.cancel(notificationId)
    }

    @Test
    fun blockNotificationUsesCurrentTaskStateInsteadOfStaleIntentData() = runBlocking {
        val securePreferences = SecurityUtils.getSecurePreferences(context)
        val scope = securePreferences.getString("database_id", "")?.trim().orEmpty()
        val completedStatus = securePreferences.getString("mapping_status_completed", "完了")
            ?.trim().orEmpty().ifBlank { "完了" }
        assumeTrue("A configured Notion connection is required", scope.isNotBlank())

        val block = ScheduleBlockEntity(
            id = blockId,
            type = "task",
            title = "instrumentation notification block",
            associatedId = taskId,
            startTime = 0,
            endTime = 30,
            color = "#000000",
            date = JapanTime.todayIso(),
        )
        database.scheduleBlockDao.insertBlock(block)
        database.taskDao.upsertTasks(
            listOf(
                TaskEntity(
                    id = taskId,
                    title = "instrumentation notification task",
                    status = completedStatus,
                    category = "",
                    dueDate = null,
                    scheduledDate = JapanTime.todayIso(),
                    connectionScope = scope,
                ),
            ),
        )

        notificationManager.cancel(notificationId)
        dispatchBlockStart()
        delay(700L)
        assertFalse(notificationManager.activeNotifications.any { it.id == notificationId })

        database.taskDao.updateTaskStatusLocal(
            id = taskId,
            status = "instrumentation-active",
            statusColor = null,
            lastEditedTime = System.currentTimeMillis(),
        )
        // Positive target selection is tested without depending on the device's
        // notification-rate limiter; receiver delivery remains covered above for
        // completed, orphaned, and deleted records.
        val activeTask = database.taskDao.getTaskById(taskId)!!
        assertEquals(
            BlockNotificationTarget(
                title = "instrumentation notification block",
                type = "task",
                associatedId = taskId,
            ),
            currentBlockNotificationTarget(block, listOf(activeTask), completedStatus),
        )
    }

    @Test
    fun blockNotificationSuppressesOrphanedAndDeletedBlockTargets() = runBlocking {
        val securePreferences = SecurityUtils.getSecurePreferences(context)
        val scope = securePreferences.getString("database_id", "")?.trim().orEmpty()
        assumeTrue("A configured Notion connection is required", scope.isNotBlank())

        // A task block whose current association cannot be found must not notify. This
        // emulates a task that was deleted after its alarm had already been scheduled.
        database.scheduleBlockDao.insertBlock(
            ScheduleBlockEntity(
                id = blockId,
                type = "task",
                title = "instrumentation orphaned block",
                associatedId = "instrumented-missing-notification-task",
                startTime = 0,
                endTime = 30,
                color = "#000000",
                date = JapanTime.todayIso(),
            ),
        )
        notificationManager.cancel(notificationId)
        dispatchBlockStart()
        delay(700L)
        assertFalse(notificationManager.activeNotifications.any { it.id == notificationId })

        // A stale alarm for a block that has been removed must not recreate a
        // notification from the old Intent payload either.
        database.scheduleBlockDao.deleteBlockById(blockId)
        dispatchBlockStart()
        delay(700L)
        assertFalse(notificationManager.activeNotifications.any { it.id == notificationId })
    }

    private fun dispatchBlockStart() {
        context.sendBroadcast(
            Intent().apply {
                component = ComponentName(context, TaskNotificationReceiver::class.java)
                putExtra("NOTIF_TYPE", "BLOCK_START")
                putExtra("BLOCK_ID", blockId)
                // Deliberately stale values must be ignored by the receiver.
                putExtra("BLOCK_TITLE", "stale title")
                putExtra("BLOCK_TYPE", "life")
            },
        )
    }
}
