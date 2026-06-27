package com.notiontasks.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import androidx.navigation.compose.rememberNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.NotionApi
import com.notiontasks.app.data.repository.TaskRepository
import com.notiontasks.app.ui.components.AddTaskDialog
import com.notiontasks.app.ui.components.EditTaskDialog
import com.notiontasks.app.ui.navigation.Screen
import com.notiontasks.app.ui.screens.HomeScreen
import com.notiontasks.app.ui.screens.CategoryScreen
import com.notiontasks.app.ui.screens.CalendarScreen
import com.notiontasks.app.ui.screens.PomodoroScreen
import com.notiontasks.app.ui.screens.AchievementsScreen
import com.notiontasks.app.ui.screens.SettingsScreen
import com.notiontasks.app.ui.theme.NotionTaskerTheme
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {

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

        // Initialize Crypto SharedPreferences for security guidelines
        val mainKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPreferences = EncryptedSharedPreferences.create(
            applicationContext,
            "notion_tasks_secure_prefs",
            mainKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // API Setup
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
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory(contentType))
            .build()

        val notionApi = retrofit.create(NotionApi::class.java)
        val database = TaskDatabase.getInstance(applicationContext)
        val repository = TaskRepository(notionApi, database.taskDao)

        // MVVM Factory
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return TaskViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[TaskViewModel::class.java]

        // Load configured credentials
        val savedToken = sharedPreferences.getString("notion_token", "") ?: ""
        val savedDbId = sharedPreferences.getString("database_id", "") ?: ""
        viewModel.updateCredentials(savedToken, savedDbId)

        // Initialize channel and set up alarms
        TaskNotificationReceiver.createNotificationChannel(this)
        TaskNotificationReceiver.rescheduleAlarms(this)

        // Prompt for notification permission on first launch (or after update)
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
            sharedPreferences.edit().putBoolean("has_req_notif_launch_v2", true).apply()
        }

        setContent {
            val morningTime = remember { mutableStateOf(sharedPreferences.getString("morning_notif_time", "08:00") ?: "08:00") }
            val eveningTime = remember { mutableStateOf(sharedPreferences.getString("evening_notif_time", "20:00") ?: "20:00") }
            val morningEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("morning_notif_enabled", true)) }
            val eveningEnabled = remember { mutableStateOf(sharedPreferences.getBoolean("evening_notif_enabled", true)) }
            val themeMode = remember { mutableStateOf(sharedPreferences.getString("theme_mode", "system") ?: "system") }

            val propTitle = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_title", "") ?: "") }
            val propStatus = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_status", "") ?: "") }
            val propStatusType = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_status_type", "status") ?: "status") }
            val propCategory = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_category", "") ?: "") }
            val propScheduled = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_scheduled_date", "") ?: "") }
            val propDue = remember { mutableStateOf(sharedPreferences.getString("mapping_prop_due_date", "") ?: "") }

            val categoryOptionsJson = sharedPreferences.getString("category_options", "[\"課題\",\"学習\",\"作業\",\"趣味\",\"他\"]") ?: "[\"課題\",\"学習\",\"作業\",\"趣味\",\"他\"]"
            val categoryOptions = remember {
                mutableStateOf(
                    try {
                        Json.decodeFromString<List<String>>(categoryOptionsJson)
                    } catch (e: Exception) {
                        listOf("課題", "学習", "作業", "趣味", "他")
                    }
                )
            }

            val statusOptionsJson = sharedPreferences.getString("status_options", "[\"未着手\",\"進行中\",\"完了\"]") ?: "[\"未着手\",\"進行中\",\"完了\"]"
            val statusOptions = remember {
                mutableStateOf(
                    try {
                        Json.decodeFromString<List<String>>(statusOptionsJson)
                    } catch (e: Exception) {
                        listOf("未着手", "進行中", "完了")
                    }
                )
            }

            // Sync property mappings on initialization or when updated
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

            LaunchedEffect(statusOptions.value) {
                viewModel.updateStatusOptions(statusOptions.value)
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
                    categoryOptionsState = categoryOptions,
                    statusOptionsState = statusOptions,
                    onUpdateCategoryOptions = { newOrder ->
                        val catJson = try { Json.encodeToString<List<String>>(newOrder) } catch(e: Exception) { "" }
                        if (catJson.isNotBlank()) {
                            sharedPreferences.edit().putString("category_options", catJson).apply()
                        }
                    },
                    onSaveCredentials = { token, dbId, morning, evening, mEnabled, eEnabled, theme, mTitle, mStatus, mStatusType, mCategory, mScheduled, mDue, mCatOptions, mStatOptions ->
                        // Automatically stringify Options to SharedPrefs
                        val catJson = try { Json.encodeToString<List<String>>(mCatOptions) } catch(e: Exception) { "" }
                        val statJson = try { Json.encodeToString<List<String>>(mStatOptions) } catch(e: Exception) { "" }

                        sharedPreferences.edit()
                            .putString("notion_token", token)
                            .putString("database_id", dbId)
                            .putString("morning_notif_time", morning)
                            .putString("evening_notif_time", evening)
                            .putBoolean("morning_notif_enabled", mEnabled)
                            .putBoolean("evening_notif_enabled", eEnabled)
                            .putString("theme_mode", theme)
                            .putString("mapping_prop_title", mTitle)
                            .putString("mapping_prop_status", mStatus)
                            .putString("mapping_prop_status_type", mStatusType)
                            .putString("mapping_prop_category", mCategory)
                            .putString("mapping_prop_scheduled_date", mScheduled)
                            .putString("mapping_prop_due_date", mDue)
                            .apply()
                        
                        if (catJson.isNotBlank()) sharedPreferences.edit().putString("category_options", catJson).apply()
                        if (statJson.isNotBlank()) sharedPreferences.edit().putString("status_options", statJson).apply()

                        propTitle.value = mTitle
                        propStatus.value = mStatus
                        propStatusType.value = mStatusType
                        propCategory.value = mCategory
                        propScheduled.value = mScheduled
                        propDue.value = mDue
                        
                        if (mCatOptions.isNotEmpty()) categoryOptions.value = mCatOptions
                        if (mStatOptions.isNotEmpty()) statusOptions.value = mStatOptions

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

                        // Reschedule alarms at new times
                        TaskNotificationReceiver.rescheduleAlarms(this)

                        Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()

                        // Request permission if not already granted
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
    categoryOptionsState: MutableState<List<String>>,
    statusOptionsState: MutableState<List<String>>,
    onUpdateCategoryOptions: (List<String>) -> Unit,
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
        mCatOptions: List<String>,
        mStatOptions: List<String>
    ) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val notionToken by viewModel.notionToken.collectAsState()
    val databaseId by viewModel.databaseId.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    // Automatic synchronizer trigger on launch if token is already defined
    LaunchedEffect(notionToken, databaseId) {
        if (notionToken.isNotBlank() && databaseId.isNotBlank()) {
            viewModel.syncWithNotion()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskModel?>(null) }
    val selectedCalendarDate = remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotionTasker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (currentRoute == Screen.Home.route || currentRoute == Screen.Category.route || currentRoute == Screen.Calendar.route || currentRoute == Screen.Achievements.route) {
                        IconButton(onClick = { viewModel.syncWithNotion() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "同期")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val screens = listOf(Screen.Home, Screen.Category, Screen.Calendar, Screen.Pomodoro, Screen.Achievements, Screen.Settings)
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
        },
        floatingActionButton = {
            if (currentRoute != Screen.Settings.route && currentRoute != Screen.Achievements.route) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
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
                HomeScreen(viewModel = viewModel, statusOptions = statusOptionsState.value, onEditTask = { editingTask = it })
            }
            composable(Screen.Category.route) {
                CategoryScreen(
                    viewModel = viewModel,
                    categoryOptions = categoryOptionsState.value,
                    statusOptions = statusOptionsState.value,
                    onEditTask = { editingTask = it },
                    onReorderCategories = { newOrder ->
                        categoryOptionsState.value = newOrder
                        onUpdateCategoryOptions(newOrder)
                    }
                )
            }
            composable(Screen.Pomodoro.route) {
                PomodoroScreen(viewModel = viewModel, statusOptions = statusOptionsState.value)
            }
            composable(Screen.Achievements.route) {
                AchievementsScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptionsState.value,
                    categoryOptions = categoryOptionsState.value,
                    onEditTask = { editingTask = it }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptionsState.value,
                    selectedCalendarDate = selectedCalendarDate,
                    onEditTask = { editingTask = it }
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
                    initialCategoryOptions = categoryOptionsState.value,
                    initialStatusOptions = statusOptionsState.value,
                    onSave = onSaveCredentials
                )
            }
        }
    }

    if (showAddDialog) {
        val context = LocalContext.current
        val defaultCategory = if (currentRoute == Screen.Category.route) selectedCategory else (categoryOptionsState.value.firstOrNull() ?: "課題")
        val initialScheduled = if (currentRoute == Screen.Calendar.route) (selectedCalendarDate.value ?: "") else ""
        AddTaskDialog(
            initialCategory = defaultCategory,
            categoryOptions = categoryOptionsState.value,
            initialScheduledDate = initialScheduled,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, cat, due, sched ->
                viewModel.addTask(
                    title = title,
                    category = cat,
                    status = statusOptionsState.value.firstOrNull() ?: "未着手",
                    dueDate = due,
                    scheduledDate = sched,
                    onSuccess = {
                        showAddDialog = false
                        Toast.makeText(context, "タスクを追加しました", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { errorMsg ->
                        Toast.makeText(context, "エラー: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    editingTask?.let { task ->
        val context = LocalContext.current
        EditTaskDialog(
            task = task,
            categoryOptions = categoryOptionsState.value,
            statusOptions = statusOptionsState.value,
            onDismiss = { editingTask = null },
            onConfirm = { title, cat, stat, due, sched ->
                viewModel.updateTask(
                    id = task.id,
                    title = title,
                    status = stat,
                    category = cat,
                    dueDate = due,
                    scheduledDate = sched,
                    onSuccess = {
                        editingTask = null
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
