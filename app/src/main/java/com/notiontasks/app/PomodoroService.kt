package com.notiontasks.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PomodoroService : Service() {

    private val binder = PomodoroBinder()
    private var countDownTimer: CountDownTimer? = null
    
    // Timer States
    var isRunning = false
        private set
    var timeLeftMs: Long = 25 * 60 * 1000L
        private set
    var durationMs: Long = 25 * 60 * 1000L
        private set
    var currentMode = "work" // "work", "shortBreak", "longBreak"
    var associatedTaskId: String? = null
    var associatedTaskTitle: String? = null

    // Callback to update UI when active
    var onTickListener: ((timeLeftMs: Long, formattedTime: String) -> Unit)? = null
    var onFinishedListener: (() -> Unit)? = null
    var onStateChangedListener: ((isRunning: Boolean) -> Unit)? = null

    inner class PomodoroBinder : Binder() {
        fun getService(): PomodoroService = this@PomodoroService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_START_OR_RESUME -> {
                    val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: associatedTaskTitle
                    val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: associatedTaskId
                    val mode = intent.getStringExtra(EXTRA_MODE) ?: currentMode
                    val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, -1)
                    
                    if (durationMinutes > 0 && !isRunning) {
                        durationMs = durationMinutes * 60 * 1000L
                        timeLeftMs = durationMs
                    }
                    
                    associatedTaskTitle = taskTitle
                    associatedTaskId = taskId
                    currentMode = mode
                    
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
        
        isRunning = true
        onStateChangedListener?.invoke(true)
        
        // Android 14+ require immediate startForeground for special use or short service types
        startForeground(NOTIFICATION_ID, createNotification(formatTime(timeLeftMs)))

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
                onStateChangedListener?.invoke(false)
                onFinishedListener?.invoke()
                
                // Show completion notification
                showCompletionNotification()
                stopSelf()
            }
        }.start()
    }

    private fun pauseTimer() {
        if (!isRunning) return
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
        onStateChangedListener?.invoke(false)
        updateNotification("${formatTime(timeLeftMs)} (一時停止中)")
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
        timeLeftMs = durationMs
        onStateChangedListener?.invoke(false)
        stopForeground(true)
    }

    private fun updateNotification(timeStr: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val title = when (currentMode) {
            "work" -> "🎉 集中完了！"
            else -> "☕ 休憩時間終了！"
        }
        
        val message = when (currentMode) {
            "work" -> "素晴らしいですね！ 5分間のリフレッシュ休憩をとりましょう。"
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ポモドーロ常駐タイマー"
            val descriptionText = "ポモドーロの進捗状況をバックグラウンド（通知）で常時表示します"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
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
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    }
}
