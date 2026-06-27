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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.notiontasks.app.data.local.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.BOOT_COMPLETED" || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
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
                
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val completedStatus = getStatusOptions(context).getOrNull(2) ?: "完了"
                
                if (type == "MORNING") {
                    val todayTasks = allTasks.filter { it.scheduledDate == todayStr }
                    if (todayTasks.isNotEmpty()) {
                        showNotification(
                            context = context,
                            notifId = 2001,
                            title = "【朝の確認】本日のタスクが${todayTasks.size}件あります。",
                            tasksList = todayTasks.map { it.title }
                        )
                    }
                    rescheduleAlarmForType(context, "MORNING")
                } else if (type == "EVENING") {
                    val unfinishedTasks = allTasks.filter { 
                        it.scheduledDate == todayStr && it.status != completedStatus
                    }
                    if (unfinishedTasks.isNotEmpty()) {
                        showNotification(
                            context = context,
                            notifId = 2002,
                            title = "【夜の確認】本日のタスクが${unfinishedTasks.size}件残っています。",
                            tasksList = unfinishedTasks.map { it.title }
                        )
                    }
                    rescheduleAlarmForType(context, "EVENING")
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
        tasksList: List<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
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

    companion object {
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Notionタスク通知"
                val descriptionText = "朝と夜のタスク通知用チャネル"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel("notion_tasks_channel", name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun getSecurePreferences(context: Context): SharedPreferences {
            val mainKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context.applicationContext,
                "notion_tasks_secure_prefs",
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        fun getStatusOptions(context: Context): List<String> {
            val json = getSecurePreferences(context).getString(
                "status_options",
                "[\"未着手\",\"進行中\",\"完了\"]"
            ) ?: "[\"未着手\",\"進行中\",\"完了\"]"
            return try {
                Json.decodeFromString<List<String>>(json)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf("未着手", "進行中", "完了") }
            } catch (e: Exception) {
                listOf("未着手", "進行中", "完了")
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
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val now = Calendar.getInstance()
            if (calendar.before(now)) {
                calendar.add(Calendar.DATE, 1)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }
}
