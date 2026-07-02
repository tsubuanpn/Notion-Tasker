@file:Suppress("DEPRECATION")
package com.notiontasks.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.core.content.edit
import androidx.navigation.compose.rememberNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.NotionApi
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.data.repository.TaskRepository
import com.notiontasks.app.ui.components.AddTaskDialog
import com.notiontasks.app.ui.components.EditTaskDialog
import com.notiontasks.app.ui.navigation.Screen
import com.notiontasks.app.ui.screens.HomeScreen
import com.notiontasks.app.ui.screens.CategoryScreen
import com.notiontasks.app.ui.screens.CalendarScreen
import com.notiontasks.app.ui.screens.PomodoroScreen
import com.notiontasks.app.ui.screens.AchievementsScreen
import com.notiontasks.app.ui.screens.ScheduleScreen
import com.notiontasks.app.ui.screens.SettingsScreen
import com.notiontasks.app.ui.theme.NotionTaskerTheme
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var viewModel: TaskViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
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
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // セキュリティガイドラインに従って暗号化された SharedPreferences を初期化する
        val mainKey = MasterKey.Builder(applicationContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPreferences = try {
            EncryptedSharedPreferences.create(
                applicationContext,
                "notion_tasks_secure_prefs",
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                applicationContext.deleteSharedPreferences("notion_tasks_secure_prefs")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            EncryptedSharedPreferences.create(
                applicationContext,
                "notion_tasks_secure_prefs",
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

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
        val repository = TaskRepository(notionApi, database.taskDao)

        // MVVM ファクトリ
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return TaskViewModel(repository, sharedPreferences) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[TaskViewModel::class.java]

        // 設定された認証情報およびプロパティマッピングを読み込む
        viewModel.loadCredentialsAndMappings()

        // チャンネルを初期化し、アラームを設定する
        TaskNotificationReceiver.createNotificationChannel(this)
        TaskNotificationReceiver.rescheduleAlarms(this)

        // 初回起動時（またはアップデート後）に通知権限を求める
        val hasRequestedLaunch = sharedPreferences.getBoolean("has_req_notif_launch_v2", false)
        if (!hasRequestedLaunch) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            sharedPreferences.edit { putBoolean("has_req_notif_launch_v2", true) }
        }

        setContent {
            val morningTime = remember { mutableStateOf(sharedPreferences.getString("morning_notif_time", "08:00") ?: "08:00") }
            val eveningTime = remember { mutableStateOf(sharedPreferences.getString("evening_notif_time", "20:00") ?: "20:00") }
            val morningEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("morning_notif_enabled", true)) }
            val eveningEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("evening_notif_enabled", true)) }
            val themeMode = remember { mutableStateOf(sharedPreferences.getString("theme_mode", "system") ?: "system") }

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

            NotionTaskerTheme(darkTheme = darkTheme) {
                MainAppScreen(
                    viewModel = viewModel,
                    initialMorningTime = morningTime.value,
                    initialEveningTime = eveningTime.value,
                    initialMorningEnabled = morningEnabled.value,
                    initialEveningEnabled = eveningEnabled.value,
                    initialThemeMode = themeMode.value,
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
                    },
                    onSaveCredentials = { token, dbId, morning, evening, mEnabled, eEnabled, theme, mTitle, mStatus, mStatusType, mCategory, mScheduled, mDue, mCatOptions, mStatOptions ->
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

                        // 新しい時間でアラームを再スケジュールする
                        TaskNotificationReceiver.rescheduleAlarms(this)

                        Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()

                        // まだ許可されていない場合は権限をリクエストする
                        checkAndRequestNotificationPermission()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: TaskViewModel,
    initialMorningTime: String,
    initialEveningTime: String,
    initialMorningEnabled: Boolean,
    initialEveningEnabled: Boolean,
    initialThemeMode: String,
    initialPropTitle: String,
    initialPropStatus: String,
    initialPropStatusType: String,
    initialPropCategory: String,
    initialPropScheduled: String,
    initialPropDue: String,
    initialCategoryTabEnabled: Boolean,
    initialCalendarTabEnabled: Boolean,
    initialScheduleTabEnabled: Boolean,
    initialPomodoroTabEnabled: Boolean,
    initialAchievementsTabEnabled: Boolean,
    onTabToggle: (String, Boolean) -> Unit,
    categoryOptions: List<NotionOptionInfo>,
    statusOptions: List<NotionOptionInfo>,
    onUpdateCategoryOptions: (List<NotionOptionInfo>) -> Unit,
    onSaveCredentials: (
        token: String,
        dbId: String,
        morning: String,
        evening: String,
        mEnabled: Boolean,
        eEnabled: Boolean,
        theme: String,
        mTitle: String,
        mStatus: String,
        mStatusType: String,
        mCategory: String,
        mScheduled: String,
        mDue: String,
        mCatOptions: List<NotionOptionInfo>,
        mStatOptions: List<NotionOptionInfo>
    ) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val notionToken by viewModel.notionToken.collectAsState()
    val databaseId by viewModel.databaseId.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val context = LocalContext.current
    var boundService by remember { mutableStateOf<PomodoroService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val pomodoroBinder = binder as? PomodoroService.PomodoroBinder
                boundService = pomodoroBinder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
    }

    DisposableEffect(context) {
        val intent = Intent(context, PomodoroService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    // トークンが既に定義されている場合、起動時に自動同期をトリガーする
    LaunchedEffect(notionToken, databaseId) {
        if (notionToken.isNotBlank() && databaseId.isNotBlank()) {
            viewModel.syncWithNotion()
        }
    }

    val showAddDialogState = remember { mutableStateOf(false) }
    val editingTaskState = remember { mutableStateOf<TaskModel?>(null) }
    val selectedCalendarDate = remember { mutableStateOf<String?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.Home.route) {
            isSearchActive = false
        }
    }

    val activity = context as? ComponentActivity
    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent
        if (intent != null) {
            val dest = intent.getStringExtra("DESTINATION")
            val focusTaskId = intent.getStringExtra("FOCUS_TASK_ID")
            if (dest == "pomodoro") {
                if (focusTaskId != null) {
                    val pPrefs = context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
                    pPrefs.edit { putString("selected_task_id", focusTaskId) }
                }
                navController.navigate(Screen.Pomodoro.route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                intent.removeExtra("DESTINATION")
                intent.removeExtra("FOCUS_TASK_ID")
            }
        }
    }

    LaunchedEffect(initialCategoryTabEnabled, initialCalendarTabEnabled, initialScheduleTabEnabled, initialPomodoroTabEnabled, initialAchievementsTabEnabled, currentRoute) {
        val isCurrentRouteDisabled = when (currentRoute) {
            Screen.Category.route -> !initialCategoryTabEnabled
            Screen.Calendar.route -> !initialCalendarTabEnabled
            Screen.Schedule.route -> !initialScheduleTabEnabled
            Screen.Pomodoro.route -> !initialPomodoroTabEnabled
            Screen.Achievements.route -> !initialAchievementsTabEnabled
            else -> false
        }
        if (isCurrentRouteDisabled) {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotionTasker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (currentRoute == Screen.Settings.route && notionToken.isNotBlank() && databaseId.isNotBlank()) {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (currentRoute == Screen.Home.route) {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "検索を閉じる" else "検索"
                            )
                        }
                    }
                    if (currentRoute == Screen.Home.route || currentRoute == Screen.Category.route || currentRoute == Screen.Calendar.route || currentRoute == Screen.Schedule.route || currentRoute == Screen.Achievements.route) {
                        IconButton(onClick = { viewModel.syncWithNotion() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "同期")
                        }
                    }
                    if (currentRoute != Screen.Settings.route) {
                        IconButton(onClick = {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "設定")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentRoute != Screen.Settings.route) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val screens = listOf(
                        Screen.Home,
                        Screen.Category,
                        Screen.Calendar,
                        Screen.Schedule,
                        Screen.Pomodoro,
                        Screen.Achievements
                    ).filter { screen ->
                        when (screen) {
                            is Screen.Home -> true
                            is Screen.Category -> initialCategoryTabEnabled
                            is Screen.Calendar -> initialCalendarTabEnabled
                            is Screen.Schedule -> initialScheduleTabEnabled
                            is Screen.Pomodoro -> initialPomodoroTabEnabled
                            is Screen.Achievements -> initialAchievementsTabEnabled
                            else -> true
                        }
                    }
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute != Screen.Settings.route && currentRoute != Screen.Achievements.route) {
                FloatingActionButton(
                    onClick = { showAddDialogState.value = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "タスク追加")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (notionToken.isBlank() || databaseId.isBlank()) Screen.Settings.route else Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptions,
                    onEditTask = { editingTaskState.value = it },
                    isSearchActive = isSearchActive
                )
            }
            composable(Screen.Category.route) {
                CategoryScreen(
                    viewModel = viewModel,
                    categoryOptions = categoryOptions,
                    statusOptions = statusOptions,
                    onEditTask = { editingTaskState.value = it },
                    onReorderCategories = { newOrder ->
                        onUpdateCategoryOptions(newOrder)
                    }
                )
            }
            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    viewModel = viewModel
                )
            }
            composable(Screen.Pomodoro.route) {
                PomodoroScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptions,
                    boundService = boundService
                )
            }
            composable(Screen.Achievements.route) {
                AchievementsScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptions,
                    categoryOptions = categoryOptions,
                    onEditTask = { editingTaskState.value = it }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptions,
                    selectedCalendarDate = selectedCalendarDate,
                    onEditTask = { editingTaskState.value = it }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    initialToken = notionToken,
                    initialDbId = databaseId,
                    initialMorningTime = initialMorningTime,
                    initialEveningTime = initialEveningTime,
                    initialMorningEnabled = initialMorningEnabled,
                    initialEveningEnabled = initialEveningEnabled,
                    initialThemeMode = initialThemeMode,
                    initialPropTitle = initialPropTitle,
                    initialPropStatus = initialPropStatus,
                    initialPropStatusType = initialPropStatusType,
                    initialPropCategory = initialPropCategory,
                    initialPropScheduled = initialPropScheduled,
                    initialPropDue = initialPropDue,
                    initialCategoryTabEnabled = initialCategoryTabEnabled,
                    initialCalendarTabEnabled = initialCalendarTabEnabled,
                    initialScheduleTabEnabled = initialScheduleTabEnabled,
                    initialPomodoroTabEnabled = initialPomodoroTabEnabled,
                    initialAchievementsTabEnabled = initialAchievementsTabEnabled,
                    onTabToggle = onTabToggle,
                    initialCategoryOptions = categoryOptions,
                    initialStatusOptions = statusOptions,
                    onSave = onSaveCredentials
                )
            }
        }
    }

    if (showAddDialogState.value) {
        val defaultCategory = if (currentRoute == Screen.Category.route) selectedCategory else (categoryOptions.firstOrNull()?.name ?: "")
        val initialScheduled = if (currentRoute == Screen.Calendar.route) (selectedCalendarDate.value ?: "") else ""
        AddTaskDialog(
            initialCategory = defaultCategory,
            categoryOptions = categoryOptions,
            initialScheduledDate = initialScheduled,
            onDismiss = { showAddDialogState.value = false },
            onConfirm = { title, cat, due, sched ->
                viewModel.addTask(
                    title = title,
                    category = cat,
                    status = statusOptions.firstOrNull()?.name,
                    dueDate = due,
                    scheduledDate = sched,
                    onSuccess = {
                        showAddDialogState.value = false
                        Toast.makeText(context, "タスクを追加しました", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { errorMsg ->
                        Toast.makeText(context, "エラー: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    editingTaskState.value?.let { task ->
        EditTaskDialog(
            task = task,
            categoryOptions = categoryOptions,
            statusOptions = statusOptions,
            onDismiss = { editingTaskState.value = null },
            onConfirm = { title, cat, stat, due, sched ->
                viewModel.updateTask(
                    id = task.id,
                    title = title,
                    status = stat,
                    category = cat,
                    dueDate = due,
                    scheduledDate = sched,
                    onSuccess = {
                        editingTaskState.value = null
                        Toast.makeText(context, "タスクを更新しました", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { errorMsg ->
                        Toast.makeText(context, "エラー: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}
