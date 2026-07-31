@file:Suppress("DEPRECATION")
package com.notiontasks.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.utils.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.notiontasks.app.data.model.TimeBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if ((intent.action == Intent.ACTION_BOOT_COMPLETED) || 
            (intent.action == "android.intent.action.BOOT_COMPLETED") || 
            (intent.action == "android.intent.action.QUICKBOOT_POWERON")) {
            rescheduleAlarms(context)
            return
        }

        val type = intent.getStringExtra("NOTIF_TYPE") ?: return
        
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                createNotificationChannel(context)
                
                val database = TaskDatabase.getInstance(context.applicationContext)
                val allTasks = database.taskDao.getAllTasksFlow().first()
                
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val completedStatus = getStatusOptions(context).getOrNull(2)?.name ?: "完了"
                
                when (type) {
                    "MORNING" -> {
                        val todayTasks = allTasks.filter { it.scheduledDate == todayStr }
                        if (todayTasks.isNotEmpty()) {
                            showNotification(
                                context = context,
                                notifId = 2001,
                                title = "【朝の確認】本日のタスクが${todayTasks.size}件あります。",
                                tasksList = todayTasks.map { it.title },
                            )
                        }
                        rescheduleAlarmForType(context, "MORNING")
                    }
                    "EVENING" -> {
                        val unfinishedTasks = allTasks.filter {
                            (it.scheduledDate == todayStr) && (it.status != completedStatus)
                        }
                        if (unfinishedTasks.isNotEmpty()) {
                            showNotification(
                                context = context,
                                notifId = 2002,
                                title = "【夜の確認】本日のタスクが${unfinishedTasks.size}件残っています。",
                                tasksList = unfinishedTasks.map { it.title },
                            )
                        }
                        rescheduleAlarmForType(context, "EVENING")
                    }
                    "BLOCK_START" -> {
                        val blockTitle = intent.getStringExtra("BLOCK_TITLE") ?: "時間割のアラート"
                        val blockType = intent.getStringExtra("BLOCK_TYPE") ?: "life"
                        val blockId = intent.getStringExtra("BLOCK_ID") ?: ""
                        val associatedId = intent.getStringExtra("ASSOCIATED_ID")
                        showBlockNotification(
                            context = context,
                            notifId = 3000 + blockId.hashCode(),
                            title = blockTitle,
                            blockType = blockType,
                            associatedId = associatedId,
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        notifId: Int,
        title: String,
        tasksList: List<String>,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = tasksList.joinToString("\n") { "・$it" }
        val contentText = tasksList.joinToString(", ")

        val notification = NotificationCompat.Builder(context, "notion_tasks_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notifId, notification)
    }

    private fun showBlockNotification(
        context: Context,
        notifId: Int,
        title: String,
        blockType: String,
        associatedId: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if ((blockType == "task") && (!associatedId.isNullOrBlank())) {
                putExtra("DESTINATION", "pomodoro")
                putExtra("FOCUS_TASK_ID", associatedId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val textBody = if (blockType == "task") "タスク「$title」を開始する時間割です。タップして集中を開始しましょう！" else "「$title」の予定時間になりました。"

        val notification = NotificationCompat.Builder(context, "notion_tasks_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("時間割: $title")
            .setContentText(textBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(textBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notifId, notification)
    }

    companion object {
        fun createNotificationChannel(context: Context) {
            val name = "Notionタスク通知"
            val descriptionText = "朝と夜のタスク通知用チャネル"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("notion_tasks_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        fun getSecurePreferences(context: Context): SharedPreferences {
            return SecurityUtils.getSecurePreferences(context)
        }

        fun getStatusOptions(context: Context): List<NotionOptionInfo> {
            val json = getSecurePreferences(context).getString(
                "status_options_v2",
                null
            )
            if (json == null) return emptyList()
            
            return try {
                Json.decodeFromString<List<NotionOptionInfo>>(json)
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun rescheduleAlarms(context: Context) {
            val prefs = getSecurePreferences(context)
            val morningEnabled = prefs.getBoolean("morning_notif_enabled", true)
            val eveningEnabled = prefs.getBoolean("evening_notif_enabled", true)
            
            val morningTime = prefs.getString("morning_notif_time", "08:00") ?: "08:00"
            val eveningTime = prefs.getString("evening_notif_time", "20:00") ?: "20:00"

            if (morningEnabled) {
                scheduleNotification(context, "MORNING", morningTime)
            } else {
                cancelNotification(context, "MORNING")
            }

            if (eveningEnabled) {
                scheduleNotification(context, "EVENING", eveningTime)
            } else {
                cancelNotification(context, "EVENING")
            }
        }

        fun rescheduleAlarmForType(context: Context, type: String) {
            val prefs = getSecurePreferences(context)
            val enabledKey = if (type == "MORNING") "morning_notif_enabled" else "evening_notif_enabled"
            val enabled = prefs.getBoolean(enabledKey, true)
            if (!enabled) {
                cancelNotification(context, type)
                return
            }

            val key = if (type == "MORNING") "morning_notif_time" else "evening_notif_time"
            val defaultVal = if (type == "MORNING") "08:00" else "20:00"
            val time = prefs.getString(key, defaultVal) ?: defaultVal
            scheduleNotification(context, type, time)
        }

        fun cancelNotification(context: Context, type: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
                putExtra("NOTIF_TYPE", type)
            }
            val requestCode = if (type == "MORNING") 1001 else 1002
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
        }

        fun scheduleNotification(context: Context, type: String, timeStr: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
                putExtra("NOTIF_TYPE", type)
            }
            
            val requestCode = if (type == "MORNING") 1001 else 1002
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (timeStr.isBlank()) {
                alarmManager.cancel(pendingIntent)
                return
            }

            val parts = timeStr.split(":")
            if (parts.size != 2) return
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return

            var scheduledTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
            if (scheduledTime.isBefore(LocalDateTime.now())) {
                scheduledTime = scheduledTime.plusDays(1)
            }

            val millis = scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                millis,
                pendingIntent
            )
        }

        fun scheduleBlockAlarm(context: Context, block: TimeBlock) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
                putExtra("NOTIF_TYPE", "BLOCK_START")
                putExtra("BLOCK_ID", block.id)
                putExtra("BLOCK_TITLE", block.title)
                putExtra("BLOCK_TYPE", block.type)
                putExtra("ASSOCIATED_ID", block.associatedId)
            }
            
            val requestCode = 3000 + block.id.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 日付と開始時間を変換する
            val date = try { LocalDate.parse(block.date) } catch (_: Exception) { return }

            val hour = block.startTime / 60
            val minute = block.startTime % 60

            val scheduledTime = LocalDateTime.of(date, LocalTime.of(hour, minute))

            if (scheduledTime.isBefore(LocalDateTime.now())) {
                return // すでに過去の時間です
            }

            val millis = scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                millis,
                pendingIntent
            )
        }

        fun cancelBlockAlarm(context: Context, blockId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TaskNotificationReceiver::class.java)
            val requestCode = 3000 + blockId.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}