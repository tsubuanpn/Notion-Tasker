@file:Suppress("DEPRECATION")
package com.notiontasks.app

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.edit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.remote.NotionApi
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.data.repository.ScheduleRepository
import com.notiontasks.app.data.repository.TaskRepository
import com.notiontasks.app.data.repository.PomodoroRepository
import com.notiontasks.app.ui.MainAppScreen
import com.notiontasks.app.ui.theme.NotionTaskerTheme
import com.notiontasks.app.ui.viewmodel.SettingsViewModel
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.utils.SecurityUtils
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var viewModel: TaskViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "通知権限が許可されました", Toast.LENGTH_SHORT).show()
            TaskNotificationReceiver.rescheduleAlarms(this)
        } else {
            Toast.makeText(this, "通知権限が拒否されました。設定から許可してください。", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAndRequestExactAlarmPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                    Toast.makeText(this, "時間割通報の正確性を向上させるため、アラーム権限を許可してください。", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    // 設定画面が開けない場合のフォールバック
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // セキュリティガイドラインに従って暗号化された SharedPreferences を初期化する
        val sharedPreferences = SecurityUtils.getSecurePreferences(this)

        // API のセットアップ
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.notion.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        val notionApi = retrofit.create(NotionApi::class.java)
        val database = TaskDatabase.getInstance(applicationContext)
        
        val taskRepository = TaskRepository(
            notionApi = notionApi,
            taskDao = database.taskDao,
            pendingSyncActionDao = database.pendingSyncActionDao
        )
        val scheduleRepository = ScheduleRepository(
            scheduleBlockDao = database.scheduleBlockDao,
            lifeActivityDao = database.lifeActivityDao
        )
        val pomodoroRepository = PomodoroRepository(
            pomodoroLogDao = database.pomodoroLogDao
        )

        // MVVM ファクトリ
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(TaskViewModel::class.java) -> {
                        @Suppress("UNCHECKED_CAST")
                        TaskViewModel(
                            repository = taskRepository,
                            scheduleRepository = scheduleRepository,
                            pomodoroRepository = pomodoroRepository,
                            sharedPrefs = sharedPreferences
                        ) as T
                    }
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                        @Suppress("UNCHECKED_CAST")
                        SettingsViewModel(sharedPreferences) as T
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }

        viewModel = ViewModelProvider(this, factory)[TaskViewModel::class.java]
        settingsViewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]

        // 設定された認証情報およびプロパティマッピングを読み込む
        viewModel.loadCredentialsAndMappings()

        // チャンネルを初期化し、アラームを設定する
        TaskNotificationReceiver.createNotificationChannel(this)
        TaskNotificationReceiver.rescheduleAlarms(this)

        // 初回起動時（またはアップデート後）に通知権限を求める
        val hasRequestedLaunch = sharedPreferences.getBoolean("has_req_notif_launch_v2", false)
        if (!hasRequestedLaunch) {
            checkAndRequestNotificationPermission()
            checkAndRequestExactAlarmPermission()
            sharedPreferences.edit { putBoolean("has_req_notif_launch_v2", true) }
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val themeColorName by settingsViewModel.themeColorName.collectAsState()
            val dynamicColorEnabled by settingsViewModel.dynamicColorEnabled.collectAsState()

            val propTitle = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_title", "") ?: "") }
            val propStatus = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_status", "") ?: "") }
            val propStatusType = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_status_type", "status") ?: "status") }
            val propStatusUnstarted = remember { mutableStateOf(sharedPreferences.getString("mapping_status_unstarted", "未着手") ?: "未着手") }
            val propStatusInProgress = remember { mutableStateOf(sharedPreferences.getString("mapping_status_in_progress", "進行中") ?: "進行中") }
            val propStatusCompleted = remember { mutableStateOf(sharedPreferences.getString("mapping_status_completed", "完了") ?: "完了") }
            val propCategory = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_category", "") ?: "") }
            val propScheduled = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_scheduled_date", "") ?: "") }
            val propDue = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_due_date", "") ?: "") }

            val categoryOptions by viewModel.categoryOptions.collectAsState()
            val statusOptions by viewModel.statusOptions.collectAsState()

            // 初期化時または更新時にプロパティマッピングを同期する
            LaunchedEffect(propTitle.value, propStatus.value, propStatusType.value, propStatusUnstarted.value, propStatusInProgress.value, propStatusCompleted.value, propCategory.value, propScheduled.value, propDue.value) {
                val currentToken = settingsViewModel.notionToken.value
                val currentDbId = settingsViewModel.databaseId.value
                viewModel.updateCredentials(
                    token = currentToken,
                    dbId = currentDbId,
                    title = propTitle.value,
                    status = propStatus.value,
                    statusType = propStatusType.value,
                    statusUnstarted = propStatusUnstarted.value,
                    statusInProgress = propStatusInProgress.value,
                    statusCompleted = propStatusCompleted.value,
                    category = propCategory.value,
                    scheduledDate = propScheduled.value,
                    dueDate = propDue.value
                )
            }

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            NotionTaskerTheme(
                darkTheme = darkTheme,
                themeColorName = themeColorName,
                dynamicColor = dynamicColorEnabled
            ) {
                MainAppScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    categoryOptions = categoryOptions,
                    statusOptions = statusOptions,
                    onUpdateCategoryOptions = { newOrder ->
                        val catJson = try { json.encodeToString<List<NotionOptionInfo>>(newOrder) } catch(_: Exception) { "" }
                        if (catJson.isNotBlank()) {
                            sharedPreferences.edit { putString("category_options_v2", catJson) }
                        }
                        viewModel.updateCategoryOptions(newOrder)
                    }
                ) { token, dbId, morning, evening, mEnabled, eEnabled, theme, mTitle, mStatus, mStatusType, mStatusUnstarted, mStatusInProgress, mStatusCompleted, mCategory, mScheduled, mDue, mCatOptions, mStatOptions, themeColor, dynamicColor, devMode, devCompleteButton ->
                    // オプションを自動的に文字列化して SharedPrefs に保存する
                    val statJson = try { json.encodeToString<List<NotionOptionInfo>>(mStatOptions) } catch(_: Exception) { "" }

                    settingsViewModel.updateNotionToken(token)
                    settingsViewModel.updateDatabaseId(dbId)
                    settingsViewModel.updateMorningTime(morning)
                    settingsViewModel.updateEveningTime(evening)
                    settingsViewModel.toggleMorningNotif(mEnabled)
                    settingsViewModel.toggleEveningNotif(eEnabled)
                    settingsViewModel.updateThemeMode(theme)
                    settingsViewModel.updateThemeColor(themeColor)
                    settingsViewModel.toggleDynamicColor(dynamicColor)
                    settingsViewModel.toggleDevMode(devMode)
                    settingsViewModel.toggleDevCompleteButton(devCompleteButton)

                    sharedPreferences.edit {
                        putString("mapping_prop_title", mTitle)
                        putString("mapping_prop_status", mStatus)
                        putString("mapping_prop_status_type", mStatusType)
                        putString("mapping_status_unstarted", mStatusUnstarted)
                        putString("mapping_status_in_progress", mStatusInProgress)
                        putString("mapping_status_completed", mStatusCompleted)
                        putString("mapping_prop_category", mCategory)
                        putString("mapping_prop_scheduled_date", mScheduled)
                        putString("mapping_prop_due_date", mDue)
                        if (statJson.isNotBlank()) {
                            putString("status_options_v2", statJson)
                        }
                    }

                    propTitle.value = mTitle
                    propStatus.value = mStatus
                    propStatusType.value = mStatusType
                    propStatusUnstarted.value = mStatusUnstarted.trim()
                    propStatusInProgress.value = mStatusInProgress.trim()
                    propStatusCompleted.value = mStatusCompleted.trim()
                    propCategory.value = mCategory
                    propScheduled.value = mScheduled
                    propDue.value = mDue
                    
                    viewModel.updateCategoryOptions(mCatOptions)
                    viewModel.updateStatusOptions(mStatOptions)

                    viewModel.updateCredentials(
                        token = token,
                        dbId = dbId,
                        title = mTitle,
                        status = mStatus,
                        statusType = mStatusType,
                        statusUnstarted = mStatusUnstarted.trim(),
                        statusInProgress = mStatusInProgress.trim(),
                        statusCompleted = mStatusCompleted.trim(),
                        category = mCategory,
                        scheduledDate = mScheduled,
                        dueDate = mDue
                    )

                    // 新しい時間でアラームを再スケジュールする
                    TaskNotificationReceiver.rescheduleAlarms(this@MainActivity)

                    Toast.makeText(this@MainActivity, "設定を保存しました", Toast.LENGTH_SHORT).show()

                    // まだ許可されていない場合は権限をリクエストする
                    checkAndRequestNotificationPermission()
                }
            }
        }
    }
}
