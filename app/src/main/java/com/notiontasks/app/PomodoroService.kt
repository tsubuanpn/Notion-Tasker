package com.notiontasks.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.AudioAttributes
import androidx.core.content.edit
import androidx.core.net.toUri
import android.content.pm.ServiceInfo
import kotlin.math.ceil
import com.notiontasks.app.data.PomodoroLog
import com.notiontasks.app.data.loadPomodoroLogs
import com.notiontasks.app.data.savePomodoroLogs

class PomodoroService : Service() {

    private val binder = PomodoroBinder()
    private var countDownTimer: CountDownTimer? = null
    
    // Timer States
    var isRunning = false
        private set
    var isPaused = false
        private set
    var timeLeftMs: Long = 25 * 60 * 1000L
        private set
    var durationMs: Long = 25 * 60 * 1000L
        private set
    var currentMode = "work" // "work", "shortBreak", "longBreak"
    var associatedTaskId: String? = null
    var associatedTaskTitle: String? = null
    var associatedTaskCategory: String? = null
    var associatedTaskCategoryColor: String? = null
    var focusStartLeftMs: Long = 0L
    var currentSessionId: String? = null

    private fun getDurationMsForMode(mode: String): Long {
        val prefs = getSharedPreferences("pomodoro_prefs", MODE_PRIVATE)
        val minutes = when (mode) {
            "work" -> prefs.getInt("work_duration_min", 25)
            "shortBreak" -> prefs.getInt("short_break_duration_min", 5)
            else -> prefs.getInt("long_break_duration_min", 15)
        }
        return minutes * 60 * 1000L
    }

    fun updateModeAndDuration(mode: String) {
        currentMode = mode
        durationMs = getDurationMsForMode(mode)
        if (!isRunning && !isPaused) {
            timeLeftMs = durationMs
        }
    }

    fun getCompletedCountToday(): Int {
        val prefs = getSharedPreferences("pomodoro_prefs", MODE_PRIVATE)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val savedCompletedCountDate = prefs.getString("completed_count_date", "") ?: ""
        return if (savedCompletedCountDate == todayStr) prefs.getInt("completed_count", 0) else 0
    }

    private fun transitionToNextMode() {
        val prefs = getSharedPreferences("pomodoro_prefs", MODE_PRIVATE)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        
        val savedCompletedCountDate = prefs.getString("completed_count_date", "") ?: ""
        var pomodoroCompletedCount = if (savedCompletedCountDate == todayStr) {
            prefs.getInt("completed_count", 0)
        } else {
            0
        }

        if (currentMode == "work") {
            pomodoroCompletedCount++
            prefs.edit {
                putInt("completed_count", pomodoroCompletedCount)
                putString("completed_count_date", todayStr)
            }
        }

        currentMode = if (currentMode == "work") {
            if (pomodoroCompletedCount % 4 == 0) "longBreak" else "shortBreak"
        } else {
            "work"
        }

        durationMs = getDurationMsForMode(currentMode)
        timeLeftMs = durationMs
    }

    // Callback to update UI when active
    var onTickListener: ((timeLeftMs: Long, formattedTime: String) -> Unit)? = null
    var onFinishedListener: (() -> Unit)? = null
    var onStateChangedListener: ((isRunning: Boolean) -> Unit)? = null
    private var ringtone: Ringtone? = null
    var isRingtonePlaying: Boolean = false
        private set
    var onRingtoneStateChangedListener: ((isPlaying: Boolean) -> Unit)? = null

    inner class PomodoroBinder : Binder() {
        fun getService(): PomodoroService = this@PomodoroService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        durationMs = getDurationMsForMode(currentMode)
        timeLeftMs = durationMs
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(formatTime(timeLeftMs)), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(formatTime(timeLeftMs)))
        }
        if (intent != null) {
            val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: associatedTaskTitle
            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: associatedTaskId
            val taskCategory = intent.getStringExtra(EXTRA_TASK_CATEGORY) ?: associatedTaskCategory
            val taskCategoryColor = intent.getStringExtra(EXTRA_TASK_CATEGORY_COLOR) ?: associatedTaskCategoryColor
            val mode = intent.getStringExtra(EXTRA_MODE)
            
            associatedTaskTitle = taskTitle
            associatedTaskId = taskId
            associatedTaskCategory = taskCategory
            associatedTaskCategoryColor = taskCategoryColor
            
            if (mode != null) {
                updateModeAndDuration(mode)
            }

            when (intent.action) {
                ACTION_START_OR_RESUME -> {
                    val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, -1)
                    
                    if (durationMinutes > 0 && !isRunning) {
                        if (!isPaused) {
                            durationMs = durationMinutes * 60 * 1000L
                            timeLeftMs = durationMs
                        }
                    }

                    // If an alarm is currently playing from a previous finished timer, stop it when starting a new one
                    try {
                        ringtone?.stop()
                    } catch (_: Exception) { }
                    ringtone = null
                    if (isRingtonePlaying) {
                        isRingtonePlaying = false
                        onRingtoneStateChangedListener?.invoke(false)
                    }

                    startTimer()
                }
                ACTION_PAUSE -> {
                    pauseTimer()
                }
                ACTION_STOP -> {
                    stopTimer()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        if (isRunning) return
        
        if (!isPaused || currentSessionId == null) {
            currentSessionId = System.currentTimeMillis().toString()
        } else {
            currentSessionId?.let { sessionId ->
                val targetId = "pomo_paused_$sessionId"
                val currentLogs = loadPomodoroLogs(this).toMutableList()
                val removed = currentLogs.removeAll { it.id == targetId }
                if (removed) {
                    savePomodoroLogs(this, currentLogs)
                }
            }
        }
        
        isRunning = true
        isPaused = false
        onStateChangedListener?.invoke(true)
        
        if (currentMode == "work") {
            focusStartLeftMs = timeLeftMs
        }
        
        // Android 14+ require immediate startForeground for special use or short service types
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(formatTime(timeLeftMs)), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(formatTime(timeLeftMs)))
        }

        countDownTimer = object : CountDownTimer(timeLeftMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMs = millisUntilFinished
                val timeStr = formatTime(timeLeftMs)
                onTickListener?.invoke(timeLeftMs, timeStr)
                updateNotification(timeStr)
            }

            override fun onFinish() {
                timeLeftMs = 0
                isRunning = false
                isPaused = false
                onStateChangedListener?.invoke(false)
                
                commitFocusSession(isTemporary = false)
                
                transitionToNextMode()
                
                onFinishedListener?.invoke()
                // Play alarm sound: prefer user-selected URI stored in pomodoro_prefs
                val prefs = getSharedPreferences("pomodoro_prefs", MODE_PRIVATE)
                val stored = prefs.getString("alarm_uri", "")
                val alarmUri = if (!stored.isNullOrBlank()) {
                    try { stored.toUri() } catch (_: Exception) { null }
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                try {
                    ringtone = RingtoneManager.getRingtone(this@PomodoroService, alarmUri)
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    ringtone?.audioAttributes = audioAttributes
                    ringtone?.play()
                    isRingtonePlaying = true
                    onRingtoneStateChangedListener?.invoke(true)
                } catch (_: Exception) {
                    // 再生に失敗しても処理は継続
                }

                // Show completion notification
                showCompletionNotification()

                // Keep service briefly active so ringtone can play; stop when appropriate
                stopSelf()
            }
        }.start()
    }

    private fun pauseTimer() {
        if (!isRunning) return
        commitFocusSession(isTemporary = true)
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
        isPaused = true
        onStateChangedListener?.invoke(false)
        // 停止時に再生中のアラームがあれば止める
        ringtone?.stop()
        ringtone = null
        if (isRingtonePlaying) {
            isRingtonePlaying = false
            onRingtoneStateChangedListener?.invoke(false)
        }
        updateNotification("${formatTime(timeLeftMs)} (一時停止中)")
    }

    private fun stopTimer() {
        commitFocusSession(isTemporary = false)
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
        isPaused = false
        timeLeftMs = durationMs
        onStateChangedListener?.invoke(false)
        // 停止時に再生中のアラームがあれば止める
        ringtone?.stop()
        ringtone = null
        if (isRingtonePlaying) {
            isRingtonePlaying = false
            onRingtoneStateChangedListener?.invoke(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // Public API to stop ringtone playback (for UI button)
    fun stopRingtonePlayback() {
        ringtone?.stop()
        ringtone = null
        if (isRingtonePlaying) {
            isRingtonePlaying = false
            onRingtoneStateChangedListener?.invoke(false)
        }
    }

    fun commitFocusSession(isTemporary: Boolean = false) {
        if (currentMode != "work") return
        val elapsedMs = focusStartLeftMs - timeLeftMs
        if (elapsedMs < 1000L) return // 1秒未満の極めて短い時間は記録しない

        // 1秒以上作業していれば、端数を切り上げて（最低1分として）記録。テストや短い作業でも確実にログが残る。
        val elapsedSeconds = elapsedMs / 1000.0
        val elapsedMins = ceil(elapsedSeconds / 60.0).toInt()

        if (elapsedMins > 0) {
            val sdfIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayStr = sdfIso.format(java.util.Date())

            val taskTitleVal = associatedTaskTitle ?: "一般作業の集中セッション"
            val categoryVal = associatedTaskCategory ?: "一般作業"
            val categoryColorVal = if (!associatedTaskCategoryColor.isNullOrBlank() && associatedTaskCategoryColor != "default") {
                associatedTaskCategoryColor!!.lowercase()
            } else {
                when (associatedTaskCategory) {
                    "課題" -> "blue"
                    "学習" -> "purple"
                    "作業" -> "green"
                    "趣味" -> "pink"
                    "他" -> "orange"
                    else -> "default"
                }
            }

            val logId = if (isTemporary && currentSessionId != null) {
                "pomo_paused_$currentSessionId"
            } else {
                "pomo_${System.currentTimeMillis()}"
            }

            val newLog = PomodoroLog(
                id = logId,
                taskId = associatedTaskId,
                taskTitle = taskTitleVal,
                category = categoryVal,
                categoryColor = categoryColorVal,
                date = todayStr,
                minutes = elapsedMins,
                timestamp = System.currentTimeMillis()
            )

            val currentLogs = loadPomodoroLogs(this).toMutableList()
            
            // 本登録するときに、既に同じセッションの一時的なログがあればそれを削除して新しく追加する
            if (!isTemporary && currentSessionId != null) {
                currentLogs.removeAll { it.id == "pomo_paused_$currentSessionId" }
            }

            currentLogs.add(newLog)
            savePomodoroLogs(this, currentLogs)
        }

        // Reset focusStartLeftMs to current timeLeftMs for the next chunk
        focusStartLeftMs = timeLeftMs
    }

    private fun promoteTemporarySession() {
        val sessionId = currentSessionId ?: return
        val targetId = "pomo_paused_$sessionId"
        val currentLogs = loadPomodoroLogs(this).toMutableList()
        val tempLogIndex = currentLogs.indexOfFirst { it.id == targetId }
        if (tempLogIndex != -1) {
            val tempLog = currentLogs[tempLogIndex]
            val promotedLog = tempLog.copy(id = "pomo_${System.currentTimeMillis()}")
            currentLogs[tempLogIndex] = promotedLog
            savePomodoroLogs(this, currentLogs)
        }
        currentSessionId = null
    }

    fun updateFocusedTask(taskId: String?, taskTitle: String?, category: String?, categoryColor: String?) {
        if (currentMode == "work") {
            if (isRunning) {
                commitFocusSession(isTemporary = false)
                currentSessionId = System.currentTimeMillis().toString()
            } else if (isPaused) {
                promoteTemporarySession()
            }
        }

        associatedTaskId = taskId
        associatedTaskTitle = taskTitle
        associatedTaskCategory = category
        associatedTaskCategoryColor = categoryColor

        if (currentMode == "work" && isRunning) {
            focusStartLeftMs = timeLeftMs
        }
    }

    private fun updateNotification(timeStr: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(timeStr))
    }

    private fun createNotification(timeStr: String): Notification {
        val title = when (currentMode) {
            "work" -> "【集中セッション中】"
            "shortBreak" -> "【短い休憩中】"
            "longBreak" -> "【長い休憩中】"
            else -> "【ポモドーロタイマー】"
        }
        
        val contentText = if (associatedTaskTitle != null) {
            "タスク: $associatedTaskTitle | 残り $timeStr"
        } else {
            "一般の集中作業中 | 残り $timeStr"
        }

        // Set up intents for action buttons
        val pauseIntent = Intent(this, PomodoroService::class.java).apply {
            action = if (isRunning) ACTION_PAUSE else ACTION_START_OR_RESUME
        }
        val pendingPauseIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PomodoroService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseActionText = if (isRunning) "一時停止" else "再開"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingMainActivityIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, playPauseActionText, pendingPauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "終了", pendingStopIntent)

        return builder.build()
    }

    private fun showCompletionNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val title = when (currentMode) {
            "work" -> "🎉 集中完了！"
            else -> "☕ 休憩時間終了！"
        }
        
        val message = when (currentMode) {
            "work" -> "素晴らしいですね！ ここでリフレッシュ休憩をとりましょう。"
            else -> "次のフォーカスを始めましょう！素晴らしい一日のために。"
        }

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 3, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentIntent(pendingMainActivityIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val name = "ポモドーロ常駐タイマー"
        val descriptionText = "ポモドーロの進捗状況をバックグラウンド（通知）で常時表示します"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        ringtone?.stop()
        ringtone = null
        if (isRingtonePlaying) {
            isRingtonePlaying = false
            onRingtoneStateChangedListener?.invoke(false)
        }
    }

    companion object {
        const val CHANNEL_ID = "pomodoro_timer_channel"
        const val NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002

        const val ACTION_START_OR_RESUME = "com.notiontasks.app.ACTION_START_OR_RESUME"
        const val ACTION_PAUSE = "com.notiontasks.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.notiontasks.app.ACTION_STOP"

        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_CATEGORY = "extra_task_category"
        const val EXTRA_TASK_CATEGORY_COLOR = "extra_task_category_color"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    }
}
