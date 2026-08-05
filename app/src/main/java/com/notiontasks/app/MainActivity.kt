@file:Suppress("DEPRECATION")
package com.notiontasks.app

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
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return TaskViewModel(
                            repository = taskRepository,
                            scheduleRepository = scheduleRepository,
                            pomodoroRepository = pomodoroRepository,
                            sharedPrefs = sharedPreferences
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        )[TaskViewModel::class.java]

        // 設定された認証情報およびプロパティマッピングを読み込む
        viewModel.loadCredentialsAndMappings()

        // チャンネルを初期化し、アラームを設定する
        TaskNotificationReceiver.createNotificationChannel(this)
        TaskNotificationReceiver.rescheduleAlarms(this)

        // 初回起動時（またはアップデート後）に通知権限を求める
        val hasRequestedLaunch = sharedPreferences.getBoolean("has_req_notif_launch_v2", false)
        if (!hasRequestedLaunch) {
            checkAndRequestNotificationPermission()
            sharedPreferences.edit { putBoolean("has_req_notif_launch_v2", true) }
        }

        setContent {
            val morningTime = remember { mutableStateOf(sharedPreferences.getString("morning_notif_time", "08:00") ?: "08:00") }
            val eveningTime = remember { mutableStateOf(sharedPreferences.getString("evening_notif_time", "20:00") ?: "20:00") }
            val morningEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("morning_notif_enabled", true)) }
            val eveningEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("evening_notif_enabled", true)) }
            val themeMode = remember { mutableStateOf(sharedPreferences.getString("theme_mode", "system") ?: "system") }
            val themeColorName = remember { mutableStateOf(sharedPreferences.getString("theme_color_name", "blue") ?: "blue") }
            val dynamicColorEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("dynamic_color_enabled", true)) }

            val categoryTabEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("tab_category_enabled", true)) }
            val calendarTabEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("tab_calendar_enabled", true)) }
            val scheduleTabEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("tab_schedule_enabled", true)) }
            val pomodoroTabEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("tab_pomodoro_enabled", true)) }
            val achievementsTabEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("tab_achievements_enabled", true)) }

            val propTitle = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_title", "") ?: "") }
            val propStatus = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_status", "") ?: "") }
            val propStatusType = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_status_type", "status") ?: "status") }
            val propCategory = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_category", "") ?: "") }
            val propScheduled = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_scheduled_date", "") ?: "") }
            val propDue = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_due_date", "") ?: "") }

            val categoryOptions by viewModel.categoryOptions.collectAsState()
            val statusOptions by viewModel.statusOptions.collectAsState()

            // 初期化時または更新時にプロパティマッピングを同期する
            LaunchedEffect(propTitle.value, propStatus.value, propStatusType.value, propCategory.value, propScheduled.value, propDue.value) {
                val currentToken = sharedPreferences.getString("notion_token", "") ?: ""
                val currentDbId = sharedPreferences.getString("database_id", "") ?: ""
                viewModel.updateCredentials(
                    token = currentToken,
                    dbId = currentDbId,
                    title = propTitle.value,
                    status = propStatus.value,
                    statusType = propStatusType.value,
                    category = propCategory.value,
                    scheduledDate = propScheduled.value,
                    dueDate = propDue.value
                )
            }

            val darkTheme = when (themeMode.value) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            NotionTaskerTheme(
                darkTheme = darkTheme,
                themeColorName = themeColorName.value,
                dynamicColor = dynamicColorEnabled.value
            ) {
                MainAppScreen(
                    viewModel = viewModel,
                    initialMorningTime = morningTime.value,
                    initialEveningTime = eveningTime.value,
                    initialMorningEnabled = morningEnabled.value,
                    initialEveningEnabled = eveningEnabled.value,
                    initialThemeMode = themeMode.value,
                    initialThemeColor = themeColorName.value,
                    initialDynamicColorEnabled = dynamicColorEnabled.value,
                    initialPropTitle = propTitle.value,
                    initialPropStatus = propStatus.value,
                    initialPropStatusType = propStatusType.value,
                    initialPropCategory = propCategory.value,
                    initialPropScheduled = propScheduled.value,
                    initialPropDue = propDue.value,
                    initialCategoryTabEnabled = categoryTabEnabled.value,
                    initialCalendarTabEnabled = calendarTabEnabled.value,
                    initialScheduleTabEnabled = scheduleTabEnabled.value,
                    initialPomodoroTabEnabled = pomodoroTabEnabled.value,
                    initialAchievementsTabEnabled = achievementsTabEnabled.value,
                    onTabToggle = { tabKey, isEnabled ->
                        sharedPreferences.edit { putBoolean("tab_${tabKey}_enabled", isEnabled) }
                        when (tabKey) {
                            "category" -> categoryTabEnabled.value = isEnabled
                            "calendar" -> calendarTabEnabled.value = isEnabled
                            "schedule" -> scheduleTabEnabled.value = isEnabled
                            "pomodoro" -> pomodoroTabEnabled.value = isEnabled
                            "achievements" -> achievementsTabEnabled.value = isEnabled
                        }
                    },
                    categoryOptions = categoryOptions,
                    statusOptions = statusOptions,
                    onUpdateCategoryOptions = { newOrder ->
                        val catJson = try { json.encodeToString<List<NotionOptionInfo>>(newOrder) } catch(_: Exception) { "" }
                        if (catJson.isNotBlank()) {
                            sharedPreferences.edit { putString("category_options_v2", catJson) }
                        }
                        viewModel.updateCategoryOptions(newOrder)
                    }
                ) { token, dbId, morning, evening, mEnabled, eEnabled, theme, mTitle, mStatus, mStatusType, mCategory, mScheduled, mDue, mCatOptions, mStatOptions, themeColor, dynamicColor ->
                    // オプションを自動的に文字列化して SharedPrefs に保存する
                    val catJson = try { json.encodeToString<List<NotionOptionInfo>>(mCatOptions) } catch(_: Exception) { "" }
                    val statJson = try { json.encodeToString<List<NotionOptionInfo>>(mStatOptions) } catch(_: Exception) { "" }

                    sharedPreferences.edit {
                        putString("notion_token", token)
                        putString("database_id", dbId)
                        putString("morning_notif_time", morning)
                        putString("evening_notif_time", evening)
                        putBoolean("morning_notif_enabled", mEnabled)
                        putBoolean("evening_notif_enabled", eEnabled)
                        putString("theme_mode", theme)
                        putString("theme_color_name", themeColor)
                        putBoolean("dynamic_color_enabled", dynamicColor)
                        putString("mapping_prop_title", mTitle)
                        putString("mapping_prop_status", mStatus)
                        putString("mapping_prop_status_type", mStatusType)
                        putString("mapping_prop_category", mCategory)
                        putString("mapping_prop_scheduled_date", mScheduled)
                        putString("mapping_prop_due_date", mDue)
                        if (catJson.isNotBlank()) {
                            putString("category_options_v2", catJson)
                        }
                        if (statJson.isNotBlank()) {
                            putString("status_options_v2", statJson)
                        }
                    }

                    propTitle.value = mTitle
                    propStatus.value = mStatus
                    propStatusType.value = mStatusType
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
                        category = mCategory,
                        scheduledDate = mScheduled,
                        dueDate = mDue
                    )

                    morningTime.value = morning
                    eveningTime.value = evening
                    morningEnabled.value = mEnabled
                    eveningEnabled.value = eEnabled
                    themeMode.value = theme
                    themeColorName.value = themeColor
                    dynamicColorEnabled.value = dynamicColor

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
