package com.notiontasks.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import android.app.DatePickerDialog
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Check
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.net.Uri
import android.media.RingtoneManager
import android.media.Ringtone
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.notiontasks.app.data.local.TaskDatabase
import com.notiontasks.app.data.model.TaskCategory
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.model.TaskStatus
import com.notiontasks.app.data.remote.NotionApi
import com.notiontasks.app.data.repository.TaskRepository
import com.notiontasks.app.ui.navigation.Screen
import com.notiontasks.app.ui.theme.NotionTaskerTheme
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.ui.viewmodel.TasksUiState
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import okhttp3.MediaType.Companion.toMediaType
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.comparisons.compareBy
import kotlin.comparisons.nullsLast
import kotlin.comparisons.naturalOrder
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.IBinder
import android.os.Build

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
                AchievementsScreen(viewModel = viewModel, statusOptions = statusOptionsState.value, onEditTask = { editingTask = it })
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

@Composable
fun HomeScreen(viewModel: TaskViewModel, statusOptions: List<String>, onEditTask: (TaskModel) -> Unit) {
    val uiState by viewModel.tasksState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is TasksUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TasksUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "エラーが発生しました: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is TasksUiState.Idle -> {
                EmptyStateView(
                    message = "タスクがありません。右上の更新ボタンを押して同期するか、Notionデータベースを確認してください。",
                    onRefresh = { viewModel.syncWithNotion() }
                )
            }
            is TasksUiState.Success -> {
                var homeFilter by remember { mutableStateOf("all") } // "all" or "today"
                val todayStr = remember {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                }

                // Sort tasks: scheduledDate (ascending, nullsLast) -> dueDate (ascending, nullsLast)
                val sortedTasks = state.tasks.sortedWith(
                    compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                        .thenBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.dueDate }
                )
                // Categorize tasks between mapped Unstarted and In Progress
                val unstartedStatus = statusOptions.getOrNull(0) ?: "未着手"
                val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"

                // Uncompleted tasks
                val activeTasks = sortedTasks.filter { it.status == unstartedStatus || it.status == inProgressStatus }

                // Filter active tasks based on option
                val filteredTasks = if (homeFilter == "today") {
                    activeTasks.filter {
                        (it.scheduledDate != null && it.scheduledDate <= todayStr) ||
                        (it.dueDate != null && it.dueDate <= todayStr)
                    }
                } else {
                    activeTasks
                }

                val unstartedTasks = filteredTasks.filter { it.status == unstartedStatus }
                val inProgressTasks = filteredTasks.filter { it.status == inProgressStatus }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Segmented control row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // All button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = if (homeFilter == "all") MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { homeFilter = "all" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "すべて (${activeTasks.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (homeFilter == "all") FontWeight.Bold else FontWeight.Normal,
                                color = if (homeFilter == "all") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Today button
                        val todayCount = activeTasks.count {
                            (it.scheduledDate != null && it.scheduledDate <= todayStr) ||
                            (it.dueDate != null && it.dueDate <= todayStr)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = if (homeFilter == "today") MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { homeFilter = "today" },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "今日やるべきこと",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (homeFilter == "today") FontWeight.Bold else FontWeight.Normal,
                                    color = if (homeFilter == "today") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (homeFilter == "today") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(100.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = todayCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (homeFilter == "today") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (homeFilter == "today") {
                        // Banner for context
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📅 対象日: $todayStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "予定日/締切が今日以前",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    if (activeTasks.isEmpty()) {
                        EmptyStateView(
                            message = "未完了および進行中のタスクはありません！",
                            onRefresh = { viewModel.syncWithNotion() }
                        )
                    } else if (unstartedTasks.isEmpty() && inProgressTasks.isEmpty()) {
                        // Empty today screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "完了",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "今日やるべきタスクはありません",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "今日予定されている、または締め切りの未完了タスクはありません。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { homeFilter = "all" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text("すべてのタスクを表示")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (inProgressTasks.isNotEmpty()) {
                                item {
                                    Text(
                                        text = inProgressStatus,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(inProgressTasks) { task ->
                                    TaskItemCard(
                                        task = task,
                                        statusOptions = statusOptions,
                                        onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                        onEditClick = { onEditTask(task) }
                                    )
                                }
                            }

                            if (unstartedTasks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = unstartedStatus,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(unstartedTasks) { task ->
                                    TaskItemCard(
                                        task = task,
                                        statusOptions = statusOptions,
                                        onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                        onEditClick = { onEditTask(task) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    viewModel: TaskViewModel,
    categoryOptions: List<String>,
    statusOptions: List<String>,
    onEditTask: (TaskModel) -> Unit,
    onReorderCategories: (List<String>) -> Unit
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val uiState by viewModel.tasksState.collectAsState()

    val allTasks = (uiState as? TasksUiState.Success)?.tasks ?: emptyList()
    val activeTaskCategories = allTasks.map { it.category.trim() }.distinct().filter { it.isNotBlank() }
    val combinedCategories = (categoryOptions.map { it.trim() } + activeTaskCategories).distinct().filter { it.isNotBlank() }
    val categories = combinedCategories.ifEmpty { listOf("課題", "学習", "作業", "趣味", "他") }

    val initialPage = categories.indexOf(selectedCategory).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { categories.size }
    val coroutineScope = rememberCoroutineScope()

    var showReorderDialog by remember { mutableStateOf(false) }

    // Sync selectedCategory to ensure it's a valid category
    LaunchedEffect(categories, selectedCategory) {
        if (categories.isNotEmpty() && !categories.contains(selectedCategory)) {
            viewModel.selectCategory(categories.first())
        }
    }

    // Sync pagerState from selectedCategory (e.g. when user clicks a tab)
    LaunchedEffect(selectedCategory) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    // Sync selectedCategory from pagerState (e.g. when user swipe pages)
    LaunchedEffect(pagerState.settledPage) {
        val targetCategory = categories.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (selectedCategory != targetCategory) {
            viewModel.selectCategory(targetCategory)
        }
    }

    if (showReorderDialog) {
        var tempCategories by remember { mutableStateOf(categories) }
        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            title = { Text("種別の並び替え") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("表示する種別の順番を設定します。")
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(tempCategories.size) { index ->
                            val category = tempCategories[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val newList = tempCategories.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index - 1, item)
                                                tempCategories = newList
                                            }
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "上へ"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < tempCategories.size - 1) {
                                                val newList = tempCategories.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index + 1, item)
                                                tempCategories = newList
                                            }
                                        },
                                        enabled = index < tempCategories.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "下へ"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReorderCategories(tempCategories)
                        showReorderDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReorderDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab row representing distinct types along with a sort button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEachIndexed { index, category ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(category) }
                        )
                    }
                }
            }
            IconButton(
                onClick = { showReorderDialog = true },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "並び替え"
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { page ->
            val pageCategory = categories.getOrNull(page) ?: ""
            val rawTasks = (uiState as? TasksUiState.Success)?.tasks ?: emptyList()
            // Sort tasks: scheduledDate (ascending, nullsLast) -> dueDate (ascending, nullsLast)
            val sortedTasks = rawTasks.sortedWith(
                compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                    .thenBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.dueDate }
            )

            val unstartedStatus = statusOptions.getOrNull(0) ?: "未着手"
            val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"
            val completedStatus = statusOptions.getOrNull(2) ?: "完了"

            val categoryTasks = sortedTasks.filter { it.category == pageCategory && it.status != completedStatus }
            val unstartedTasks = categoryTasks.filter { it.status == unstartedStatus }
            val inProgressTasks = categoryTasks.filter { it.status == inProgressStatus }

            if (categoryTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$pageCategory のタスクはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (inProgressTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = inProgressStatus,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(inProgressTasks) { task ->
                            TaskItemCard(
                                task = task,
                                statusOptions = statusOptions,
                                onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                onEditClick = { onEditTask(task) }
                            )
                        }
                    }

                    if (unstartedTasks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = unstartedStatus,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(unstartedTasks) { task ->
                            TaskItemCard(
                                task = task,
                                statusOptions = statusOptions,
                                onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                onEditClick = { onEditTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    initialToken: String,
    initialDbId: String,
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
    initialCategoryOptions: List<String>,
    initialStatusOptions: List<String>,
    onSave: (
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
    var token by remember { mutableStateOf(initialToken) }
    var dbId by remember { mutableStateOf(initialDbId) }
    var morningTime by remember { mutableStateOf(initialMorningTime) }
    var eveningTime by remember { mutableStateOf(initialEveningTime) }
    var morningEnabled by remember { mutableStateOf(initialMorningEnabled) }
    var eveningEnabled by remember { mutableStateOf(initialEveningEnabled) }
    var themeMode by remember { mutableStateOf(initialThemeMode) }

    var propTitle by remember { mutableStateOf(initialPropTitle) }
    var propStatus by remember { mutableStateOf(initialPropStatus) }
    var propStatusType by remember { mutableStateOf(initialPropStatusType) }
    var propCategory by remember { mutableStateOf(initialPropCategory) }
    var propScheduled by remember { mutableStateOf(initialPropScheduled) }
    var propDue by remember { mutableStateOf(initialPropDue) }

    val context = LocalContext.current
    
    val showMorningTimePicker = {
        val parts = morningTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
            morningTime = String.format("%02d:%02d", hourOfDay, minute)
        }, h, m, true).show()
    }

    val showEveningTimePicker = {
        val parts = eveningTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
            eveningTime = String.format("%02d:%02d", hourOfDay, minute)
        }, h, m, true).show()
    }

    // Dynamic schema metadata states
    var isLoadingSchema by remember { mutableStateOf(false) }
    var loadedMetadata by remember { mutableStateOf<NotionDatabaseResponse?>(null) }

    LaunchedEffect(Unit) {
        if (initialToken.isNotBlank() && initialDbId.isNotBlank()) {
            isLoadingSchema = true
            viewModel.fetchDatabaseProperties(
                token = initialToken,
                dbId = initialDbId,
                onSuccess = { meta ->
                    loadedMetadata = meta
                    isLoadingSchema = false
                },
                onFailure = { _ ->
                    isLoadingSchema = false
                }
            )
        }
    }

    // Dropdowns visibility controls
    var showTitleDropdown by remember { mutableStateOf(false) }
    var showStatusDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showScheduledDropdown by remember { mutableStateOf(false) }
    var showDueDropdown by remember { mutableStateOf(false) }
    var currentSubPage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (currentSubPage == null) {
            // Main Settings Top Menu
            Text(
                text = "設定",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Group: アカウント・連携
            Text(
                text = "アカウント・連携",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    SettingsMenuItem(
                        title = "Notion 接続設定",
                        subtitle = "APIトークンとデータベースIDを連携します",
                        icon = Icons.Default.Cloud,
                        onClick = { currentSubPage = "notion" }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsMenuItem(
                        title = "プロパティマッピング",
                        subtitle = "Notion側のカラム名と同期属性を定義します",
                        icon = Icons.Default.Layers,
                        onClick = { currentSubPage = "mapping" }
                    )
                }
            }

            // Group: アプリ設定
            Text(
                text = "アプリ設定",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    SettingsMenuItem(
                        title = "通知スケジュール設定",
                        subtitle = "今日期限タスクを知らせる朝・夕の通知タイマー",
                        icon = Icons.Default.Notifications,
                        onClick = { currentSubPage = "notifications" }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsMenuItem(
                        title = "アラーム音設定",
                        subtitle = "ポモドーロ完了時に鳴らす音を選択",
                        icon = Icons.Default.Notifications,
                        onClick = { currentSubPage = "alarm" }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsMenuItem(
                        title = "外観テーマ設定",
                        subtitle = "ダークモードやライトモードの切り替え設定",
                        icon = Icons.Default.WbSunny,
                        onClick = { currentSubPage = "theme" }
                    )
                }
            }

            // Group: ヘルプ・情報
            Text(
                text = "その他",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                SettingsMenuItem(
                    title = "通知 / プロパティ等について",
                    subtitle = "通知の仕組みやプロパティの自動マッピング機能の説明",
                    icon = Icons.Default.Info,
                    onClick = { currentSubPage = "info" }
                )
            }
        } else {
            val handleSave: () -> Unit = {
                if (propTitle.isBlank() || propTitle == "未選択" ||
                    propStatus.isBlank() || propStatus == "未選択" ||
                    propCategory.isBlank() || propCategory == "未選択" ||
                    propScheduled.isBlank() || propScheduled == "未選択" ||
                    propDue.isBlank() || propDue == "未選択"
                ) {
                    Toast.makeText(context, "未選択のマッピング項目があります。すべてのプロパティを選択してください。", Toast.LENGTH_LONG).show()
                } else {
                    // Dynamically sync Options onSave
                    val chosenCatProp = loadedMetadata?.properties?.get(propCategory)
                    val catOptions = chosenCatProp?.select?.options?.map { it.name.trim() }?.filter { it.isNotBlank() }?.distinct()
                        ?.ifEmpty { initialCategoryOptions.map { it.trim() }.distinct() }
                        ?: initialCategoryOptions.map { it.trim() }.distinct()

                    val chosenStatProp = loadedMetadata?.properties?.get(propStatus)
                    val statOptions = if (propStatusType == "status") {
                        chosenStatProp?.status?.options?.map { it.name.trim() }?.filter { it.isNotBlank() }?.distinct()
                            ?.ifEmpty { initialStatusOptions.map { it.trim() }.distinct() }
                            ?: initialStatusOptions.map { it.trim() }.distinct()
                    } else {
                        chosenStatProp?.select?.options?.map { it.name.trim() }?.filter { it.isNotBlank() }?.distinct()
                            ?.ifEmpty { initialStatusOptions.map { it.trim() }.distinct() }
                            ?: initialStatusOptions.map { it.trim() }.distinct()
                    }

                    onSave(
                        token,
                        dbId,
                        morningTime,
                        eveningTime,
                        morningEnabled,
                        eveningEnabled,
                        themeMode,
                        propTitle,
                        propStatus,
                        propStatusType,
                        propCategory,
                        propScheduled,
                        propDue,
                        catOptions,
                        statOptions
                    )
                    Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
                    currentSubPage = null
                }
            }

            // Drilldown header with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { currentSubPage = null },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "戻る",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column {
                    val title = when (currentSubPage) {
                        "notion" -> "Notion 接続設定"
                        "mapping" -> "プロパティマッピング"
                        "notifications" -> "通知スケジュール設定"
                        "theme" -> "外観テーマ設定"
                        "info" -> "通知 / プロパティについて"
                        else -> ""
                    }
                    val subtitle = when (currentSubPage) {
                        "notion" -> "アカウントのシークレットトークンとデータベース連携"
                        "mapping" -> "Notionデータベース側のカラム名の紐付け設定"
                        "notifications" -> "Android端末でのバックグラウンドタスク動作"
                        "theme" -> "アプリを美しく表示する外観モードの変更"
                        "info" -> "アプリ仕様と通知機能に関する補足説明"
                        else -> ""
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // Subpage body contents
            when (currentSubPage) {
                "notion" -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Notion Integration Token") },
                        placeholder = { Text("secret_...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = dbId,
                        onValueChange = { dbId = it },
                        label = { Text("Database ID") },
                        placeholder = { Text("f87...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            isLoadingSchema = true
                            viewModel.fetchDatabaseProperties(
                                token = token,
                                dbId = dbId,
                                onSuccess = { meta ->
                                    loadedMetadata = meta
                                    isLoadingSchema = false
                                    Toast.makeText(context, "DB構造のロードに成功しました！", Toast.LENGTH_SHORT).show()

                                    // Intelligently auto-detect property matches
                                    meta.properties.forEach { (pName, pVal) ->
                                        when {
                                            pVal.title != null -> propTitle = pName
                                            pVal.status != null -> {
                                                propStatus = pName
                                                propStatusType = "status"
                                            }
                                            pVal.select != null && (pName.contains("状態") || pName.lowercase().contains("status")) -> {
                                                propStatus = pName
                                                propStatusType = "select"
                                            }
                                            pVal.select != null && (pName.contains("種類") || pName.contains("カテゴリ") || pName.lowercase().contains("category") || pName.lowercase().contains("type")) -> {
                                                propCategory = pName
                                            }
                                            pVal.date != null && (pName.contains("予定") || pName.lowercase().contains("scheduled") || pName.lowercase().contains("plan")) -> {
                                                propScheduled = pName
                                            }
                                            pVal.date != null && (pName.contains("締切") || pName.contains("締め切り") || pName.lowercase().contains("due") || pName.lowercase().contains("deadline")) -> {
                                                propDue = pName
                                            }
                                        }
                                    }
                                },
                                onFailure = { err ->
                                    isLoadingSchema = false
                                    Toast.makeText(context, "取得エラー: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = token.isNotBlank() && dbId.isNotBlank() && !isLoadingSchema,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoadingSchema) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("構造を取得中...")
                        } else {
                            Text("データベース構造を自動取得", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = handleSave,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("設定を保存して戻る", fontWeight = FontWeight.Bold)
                    }
                }
                "alarm" -> {
                    val prefsAlarm = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
                    var alarmUriString by remember { mutableStateOf(prefsAlarm.getString("alarm_uri", "") ?: "") }
                    var isPlayingPreview by remember { mutableStateOf(false) }
                    var previewRingtone by remember { mutableStateOf<Ringtone?>(null) }

                    // Launcher for ringtone picker
                    val ringtonePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                            if (picked != null) {
                                alarmUriString = picked.toString()
                                prefsAlarm.edit().putString("alarm_uri", alarmUriString).apply()
                            } else {
                                // cleared selection -> remove
                                alarmUriString = ""
                                prefsAlarm.edit().remove("alarm_uri").apply()
                            }
                        }
                    }

                    DisposableEffect(currentSubPage) {
                        onDispose {
                            previewRingtone?.stop()
                            previewRingtone = null
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "ポモドーロ完了時に鳴らす音を選択します", style = MaterialTheme.typography.bodyMedium)

                        val currentTitle = remember(alarmUriString) {
                            try {
                                val uri = if (alarmUriString.isNotBlank()) Uri.parse(alarmUriString) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "未設定"
                            } catch (e: Exception) {
                                "未設定"
                            }
                        }

                        Text(text = "現在の選択: $currentTitle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "アラーム音を選択")
                                    if (alarmUriString.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmUriString))
                                }
                                ringtonePickerLauncher.launch(intent)
                            }, shape = RoundedCornerShape(12.dp)) {
                                Text("音を選択")
                            }

                            Button(onClick = {
                                // Play preview (use selected or default)
                                try {
                                    previewRingtone?.stop()
                                    val uri = if (alarmUriString.isNotBlank()) Uri.parse(alarmUriString) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                    previewRingtone = RingtoneManager.getRingtone(context, uri)
                                    previewRingtone?.play()
                                    isPlayingPreview = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "再生に失敗しました", Toast.LENGTH_SHORT).show()
                                }
                            }, shape = RoundedCornerShape(12.dp)) {
                                Text("プレビュー再生")
                            }

                            Button(onClick = {
                                previewRingtone?.stop()
                                previewRingtone = null
                                isPlayingPreview = false
                            }, shape = RoundedCornerShape(12.dp)) {
                                Text("停止")
                            }
                        }

                        Text(text = "備考: 設定しない場合は端末のデフォルトアラーム音を使用します。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(onClick = { currentSubPage = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("保存して戻る", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                "mapping" -> {
                    // Title property field
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val titleProps = (loadedMetadata?.properties?.filter { it.value.title != null }?.keys?.toList() ?: emptyList())
                            .ifEmpty { if (propTitle.isNotBlank()) listOf(propTitle) else emptyList() }
                        OutlinedTextField(
                            value = if (propTitle.isBlank()) "未選択" else propTitle,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("名前 (タスクタイトル) プロパティ", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showTitleDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showTitleDropdown,
                            onDismissRequest = { showTitleDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("未選択") },
                                onClick = {
                                    propTitle = ""
                                    showTitleDropdown = false
                                }
                            )
                            titleProps.forEach { name ->
                                if (name.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            propTitle = name
                                            showTitleDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Status property & Type field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status property selector
                        Box(modifier = Modifier.weight(1.8f)) {
                            val statusProps = (loadedMetadata?.properties?.filter { it.value.status != null || it.value.select != null }?.keys?.toList() ?: emptyList())
                                .ifEmpty { if (propStatus.isNotBlank()) listOf(propStatus) else emptyList() }
                            OutlinedTextField(
                                value = if (propStatus.isBlank()) "未選択" else propStatus,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("状態 (ステータス) プロパティ", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showStatusDropdown = true }
                            )
                            DropdownMenu(
                                expanded = showStatusDropdown,
                                onDismissRequest = { showStatusDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("未選択") },
                                    onClick = {
                                        propStatus = ""
                                        showStatusDropdown = false
                                    }
                                )
                                statusProps.forEach { name ->
                                    if (name.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                propStatus = name
                                                val propVal = loadedMetadata?.properties?.get(name)
                                                if (propVal?.status != null) {
                                                    propStatusType = "status"
                                                } else if (propVal?.select != null) {
                                                    propStatusType = "select"
                                                }
                                                showStatusDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Status Type selector (status or select)
                        Box(modifier = Modifier.weight(1.2f)) {
                            var showTypeDropdown by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = if (propStatusType == "status") "Status型" else "Select型",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("タイプ", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showTypeDropdown = true }
                            )
                            DropdownMenu(
                                expanded = showTypeDropdown,
                                onDismissRequest = { showTypeDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Status型") },
                                    onClick = {
                                        propStatusType = "status"
                                        showTypeDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select型") },
                                    onClick = {
                                        propStatusType = "select"
                                        showTypeDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Category property field
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectProps = (loadedMetadata?.properties?.filter { it.value.select != null }?.keys?.toList() ?: emptyList())
                            .ifEmpty { if (propCategory.isNotBlank()) listOf(propCategory) else emptyList() }
                        OutlinedTextField(
                            value = if (propCategory.isBlank()) "未選択" else propCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("種類 (カテゴリ) プロパティ", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showCategoryDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("未選択") },
                                onClick = {
                                    propCategory = ""
                                    showCategoryDropdown = false
                                }
                            )
                            selectProps.forEach { name ->
                                if (name.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            propCategory = name
                                            showCategoryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Scheduled Date property field
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val dateProps = (loadedMetadata?.properties?.filter { it.value.date != null }?.keys?.toList() ?: emptyList())
                            .ifEmpty { if (propScheduled.isNotBlank()) listOf(propScheduled) else emptyList() }
                        OutlinedTextField(
                            value = if (propScheduled.isBlank()) "未選択" else propScheduled,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("予定日 プロパティ", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showScheduledDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showScheduledDropdown,
                            onDismissRequest = { showScheduledDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("未選択") },
                                onClick = {
                                    propScheduled = ""
                                    showScheduledDropdown = false
                                }
                            )
                            dateProps.forEach { name ->
                                if (name.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            propScheduled = name
                                            showScheduledDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Due Date property field
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val dateProps = (loadedMetadata?.properties?.filter { it.value.date != null }?.keys?.toList() ?: emptyList())
                            .ifEmpty { if (propDue.isNotBlank()) listOf(propDue) else emptyList() }
                        OutlinedTextField(
                            value = if (propDue.isBlank()) "未選択" else propDue,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("締め切り プロパティ", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showDueDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showDueDropdown,
                            onDismissRequest = { showDueDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("未選択") },
                                onClick = {
                                    propDue = ""
                                    showDueDropdown = false
                                }
                            )
                            dateProps.forEach { name ->
                                if (name.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            propDue = name
                                            showDueDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = handleSave,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("マッピング設定を保存", fontWeight = FontWeight.Bold)
                    }
                }
                "notifications" -> {
                    // Morning Notification Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "朝の通知 (予定タスク)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "当日の予定タスクを朝確認します",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = morningEnabled,
                                    onCheckedChange = { morningEnabled = it }
                                )
                            }

                            if (morningEnabled) {
                                Button(
                                    onClick = { showMorningTimePicker() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("通知時間: $morningTime", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Evening Notification Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NightsStay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "夜の通知 (未完了タスク)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "当日のやり残したタスクを夜に確認します",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = eveningEnabled,
                                    onCheckedChange = { eveningEnabled = it }
                                )
                            }

                            if (eveningEnabled) {
                                Button(
                                    onClick = { showEveningTimePicker() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("通知時間: $eveningTime", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = handleSave,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("通知設定を保存", fontWeight = FontWeight.Bold)
                    }
                }
                "theme" -> {
                    // Theme modes
                    val modes = listOf(
                        Triple("system", "システム設定", Icons.Default.Layers),
                        Triple("light", "ライトモード", Icons.Default.WbSunny),
                        Triple("dark", "ダークモード", Icons.Default.NightsStay)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        modes.forEach { (modeKey, label, icon) ->
                            val isSelected = themeMode == modeKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { themeMode = modeKey }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { themeMode = modeKey }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = handleSave,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("テーマ設定を保存", fontWeight = FontWeight.Bold)
                    }
                }
                "info" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "通知 / プロパティ等について",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "・設定した朝と夜の時間になると、その日が「予定日」となっているタスクの情報がプッシュ通知されます。\n\n・「データベース構造を自動取得」ボタンを押すと、Notion内の定義に合わせて、アプリのプロパティ項目や、カテゴリ選択欄・選択肢、進行状態などを完全に最適化して自動バインドします。",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { currentSubPage = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("戻る", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMenuItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "詳細",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

fun getNotionCategoryColors(colorName: String?, isDark: Boolean): Pair<Color, Color> {
    return when (colorName) {
        "gray" -> if (isDark) Pair(Color(0x339E9E9E), Color(0xFFE0E0E0)) else Pair(Color(0xFFF5F5F5), Color(0xFF616161))
        "brown" -> if (isDark) Pair(Color(0x268D6E63), Color(0xFFD7CCC8)) else Pair(Color(0xFFEFEBE9), Color(0xFF4E342E))
        "orange" -> if (isDark) Pair(Color(0x26FF9800), Color(0xFFFFCC80)) else Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "yellow" -> if (isDark) Pair(Color(0x26FFEB3B), Color(0xFFFFF59D)) else Pair(Color(0xFFFFFDE7), Color(0xFFF57F17))
        "green" -> if (isDark) Pair(Color(0x264CAF50), Color(0xFFA5D6A7)) else Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "blue" -> if (isDark) Pair(Color(0x262196F3), Color(0xFF90CAF9)) else Pair(Color(0xFFE3F2FD), Color(0xFF0D47A1))
        "purple" -> if (isDark) Pair(Color(0x269C27B0), Color(0xFFE1BEE7)) else Pair(Color(0xFFF3E5F5), Color(0xFF4A148C))
        "pink" -> if (isDark) Pair(Color(0x26E91E63), Color(0xFFF8BBD0)) else Pair(Color(0xFFFCE4EC), Color(0xFF880E4F))
        "red" -> if (isDark) Pair(Color(0x26F44336), Color(0xFFEF9A9A)) else Pair(Color(0xFFFFEBEE), Color(0xFFB71C1C))
        else -> if (isDark) Pair(Color(0x2678909C), Color(0xFFECEFF1)) else Pair(Color(0xFFECEFF1), Color(0xFF37474F))
    }
}

fun getNotionStatusColors(colorName: String?, isDark: Boolean): Pair<Color, Color> {
    return getNotionCategoryColors(colorName, isDark)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskItemCard(
    task: TaskModel,
    statusOptions: List<String>,
    onStatusClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val unstartedStatus = statusOptions.getOrNull(0) ?: "未着手"
    val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"
    val completedStatus = statusOptions.getOrNull(2) ?: "完了"

    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val isCompleted = task.status == completedStatus
    val isInProgress = task.status == inProgressStatus
    val isUnstarted = !isCompleted && !isInProgress

    val isOverdueDue = !isCompleted && task.dueDate != null && task.dueDate < todayStr
    val isOverdueScheduled = !isCompleted && task.scheduledDate != null && task.scheduledDate < todayStr

    val isSystemDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val cardBorder = if (isCompleted) {
        val color = if (isSystemDark) Color(0xFF0D3D2A) else Color(0xFFBBF7D0)
        androidx.compose.foundation.BorderStroke(1.dp, color)
    } else if (isInProgress) {
        val color = if (isSystemDark) Color(0xFF0D4D7A) else Color(0xFFBAE6FD)
        androidx.compose.foundation.BorderStroke(1.dp, color)
    } else {
        val color = if (isSystemDark) Color(0xFF3F3F46) else Color(0xFFCCCCCC)
        androidx.compose.foundation.BorderStroke(1.dp, color)
    }

    val cardBgColor = if (isCompleted) {
        if (isSystemDark) Color(0xFF0F261D) else Color(0xFFF0FDF4)
    } else if (isInProgress) {
        if (isSystemDark) Color(0xFF0D2533) else Color(0xFFF0F9FF)
    } else {
        if (isSystemDark) Color(0xFF18181B) else Color.White
    }

    val categoryColors = if (task.categoryColor != null) {
        getNotionCategoryColors(task.categoryColor, isSystemDark)
    } else {
        when (task.category) {
            "課題" -> getNotionCategoryColors("blue", isSystemDark)
            "学習" -> getNotionCategoryColors("purple", isSystemDark)
            "作業" -> getNotionCategoryColors("yellow", isSystemDark)
            "趣味" -> getNotionCategoryColors("green", isSystemDark)
            else -> getNotionCategoryColors("default", isSystemDark)
        }
    }

    val statusColors = if (isUnstarted) {
        if (isSystemDark) Pair(Color(0xFF27272A), Color(0xFFD4D4D8)) else Pair(Color(0xFFF4F4F5), Color(0xFF3F3F46))
    } else if (task.statusColor != null) {
        getNotionStatusColors(task.statusColor, isSystemDark)
    } else {
        when (task.status) {
            inProgressStatus -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
            completedStatus -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
            else -> Pair(Color(0xFFECEFF1), Color(0xFF37474F))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category & Alert Badge Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Chip Badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = categoryColors.first,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = categoryColors.second,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Alert Badges
                    if (isOverdueDue) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ 期限切れ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isOverdueScheduled) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ 持ち越し",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Dynamic Actionable status button
                Box(
                    modifier = Modifier
                        .background(color = statusColors.first, shape = RoundedCornerShape(8.dp))
                        .clickable { onStatusClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = task.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColors.second,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Task Header
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Date targets footer
            if (task.dueDate != null || task.scheduledDate != null) {
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.dueDate != null) {
                        val isDueOver = task.status != completedStatus && task.dueDate < todayStr
                        Text(
                            text = if (isDueOver) "⚠️ 締切: ${task.dueDate}" else "締切: ${task.dueDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDueOver) Color(0xFFC62828) else Color.Red.copy(alpha = 0.8f),
                            fontWeight = if (isDueOver) FontWeight.Bold else FontWeight.Normal
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (task.scheduledDate != null) {
                        val isSchedOver = task.status != completedStatus && task.scheduledDate < todayStr
                        Text(
                            text = if (isSchedOver) "持ち越し: ${task.scheduledDate}" else "予定: ${task.scheduledDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSchedOver) Color(0xFFE65100) else Color.Gray,
                            fontWeight = if (isSchedOver) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    message: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            lineHeight = 24.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("データを同期する")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    initialCategory: String,
    categoryOptions: List<String>,
    initialScheduledDate: String = "",
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, dueDate: String?, scheduledDate: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory) }
    var dueDate by remember { mutableStateOf("") }
    var scheduledDate by remember { mutableStateOf(initialScheduledDate) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val calendar = Calendar.getInstance()

    val dueDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format("%02d", month + 1)
            val formattedDay = String.format("%02d", dayOfMonth)
            dueDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val scheduledDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format("%02d", month + 1)
            val formattedDay = String.format("%02d", dayOfMonth)
            scheduledDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいタスクを追加", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タスク名 (必須)") },
                    placeholder = { Text("例: 課題の提出") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Category Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("種類 (カテゴリ)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isCategoryDropdownExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "種類選択")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false }
                    ) {
                        categoryOptions.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("締め切り (任意)") },
                    placeholder = { Text("タップして日付を選択") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                dueDatePickerDialog.show()
                                focusManager.clearFocus()
                            }
                        },
                    singleLine = true
                )

                OutlinedTextField(
                    value = scheduledDate,
                    onValueChange = { scheduledDate = it },
                    label = { Text("予定日 (任意)") },
                    placeholder = { Text("タップして日付を選択") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                scheduledDatePickerDialog.show()
                                focusManager.clearFocus()
                            }
                        },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, category, dueDate.ifBlank { null }, scheduledDate.ifBlank { null })
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskModel,
    categoryOptions: List<String>,
    statusOptions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, status: String, dueDate: String?, scheduledDate: String?) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var category by remember { mutableStateOf(task.category) }
    var status by remember { mutableStateOf(task.status) }
    var dueDate by remember { mutableStateOf(task.dueDate ?: "") }
    var scheduledDate by remember { mutableStateOf(task.scheduledDate ?: "") }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var isStatusDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val calendar = Calendar.getInstance()

    val dueDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format("%02d", month + 1)
            val formattedDay = String.format("%02d", dayOfMonth)
            dueDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val scheduledDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format("%02d", month + 1)
            val formattedDay = String.format("%02d", dayOfMonth)
            scheduledDate = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タスクを編集", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タスク名 (必須)") },
                    placeholder = { Text("例: 課題の提出") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Category Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("種類 (カテゴリ)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isCategoryDropdownExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "種類選択")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false }
                    ) {
                        categoryOptions.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Status Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("状態") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isStatusDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "状態選択")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = isStatusDropdownExpanded,
                        onDismissRequest = { isStatusDropdownExpanded = false }
                    ) {
                        statusOptions.forEach { stat ->
                            DropdownMenuItem(
                                text = { Text(stat) },
                                onClick = {
                                    status = stat
                                    isStatusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("締め切り (任意)") },
                        placeholder = { Text("タップして選択") },
                        readOnly = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    dueDatePickerDialog.show()
                                    focusManager.clearFocus()
                                }
                            },
                        singleLine = true
                    )
                    if (dueDate.isNotBlank()) {
                        IconButton(onClick = { dueDate = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = scheduledDate,
                        onValueChange = { scheduledDate = it },
                        label = { Text("予定日 (任意)") },
                        placeholder = { Text("タップして選択") },
                        readOnly = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scheduledDatePickerDialog.show()
                                    focusManager.clearFocus()
                                }
                            },
                        singleLine = true
                    )
                    if (scheduledDate.isNotBlank()) {
                        IconButton(onClick = { scheduledDate = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, category, status, dueDate.ifBlank { null }, scheduledDate.ifBlank { null })
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun AchievementsScreen(
    viewModel: TaskViewModel,
    statusOptions: List<String>,
    onEditTask: (TaskModel) -> Unit
) {
    val uiState by viewModel.tasksState.collectAsState()
    var subPage by remember { mutableStateOf("main") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is TasksUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TasksUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "エラーが発生しました: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is TasksUiState.Idle -> {
                EmptyStateView(
                    message = "タスクデータがありません。同期して実績を確認してください。",
                    onRefresh = { viewModel.syncWithNotion() }
                )
            }
            is TasksUiState.Success -> {
                val todayStr = remember {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                }
                val completedStatus = statusOptions.getOrNull(2) ?: "完了"

                // 1. Overdue & Carried Over calculation
                val overdueCount = state.tasks.count { it.status != completedStatus && it.dueDate != null && it.dueDate < todayStr }
                val carriedOverCount = state.tasks.count { it.status != completedStatus && it.scheduledDate != null && it.scheduledDate < todayStr }

                // 2. Week performance
                val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
                val startOfWeekCal = remember {
                    Calendar.getInstance().apply {
                        firstDayOfWeek = Calendar.MONDAY
                        val day = get(Calendar.DAY_OF_WEEK)
                        val diff = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
                        add(Calendar.DAY_OF_MONTH, diff)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }
                val endOfWeekCal = remember(startOfWeekCal) {
                    Calendar.getInstance().apply {
                        timeInMillis = startOfWeekCal.timeInMillis
                        add(Calendar.DAY_OF_MONTH, 6)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                }
                val startOfWeekStr = remember(startOfWeekCal) { sdf.format(startOfWeekCal.time) }
                val endOfWeekStr = remember(endOfWeekCal) { sdf.format(endOfWeekCal.time) }

                val weekTasks = state.tasks.filter {
                    val sched = it.scheduledDate
                    val due = it.dueDate
                    (sched != null && sched >= startOfWeekStr && sched <= endOfWeekStr) ||
                    (due != null && due >= startOfWeekStr && due <= endOfWeekStr)
                }
                val completedWeekCount = weekTasks.count { it.status == completedStatus }
                val weekRate = if (weekTasks.isNotEmpty()) (completedWeekCount * 100) / weekTasks.size else 0

                // 3. Month performance
                val startOfMonthCal = remember {
                    Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }
                val endOfMonthCal = remember {
                    Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                }
                val startOfMonthStr = remember(startOfMonthCal) { sdf.format(startOfMonthCal.time) }
                val endOfMonthStr = remember(endOfMonthCal) { sdf.format(endOfMonthCal.time) }

                val monthTasks = state.tasks.filter {
                    val sched = it.scheduledDate
                    val due = it.dueDate
                    (sched != null && sched >= startOfMonthStr && sched <= endOfMonthStr) ||
                    (due != null && due >= startOfMonthStr && due <= endOfMonthStr)
                }
                val completedMonthCount = monthTasks.count { it.status == completedStatus }
                val monthRate = if (monthTasks.isNotEmpty()) (completedMonthCount * 100) / monthTasks.size else 0

                // Warning / attention tasks: active (not Completed) and (dueDate < today or scheduledDate < today)
                val warningTasks = state.tasks.filter {
                    it.status != completedStatus && (
                        (it.dueDate != null && it.dueDate < todayStr) ||
                        (it.scheduledDate != null && it.scheduledDate < todayStr)
                    )
                }.sortedWith(
                    compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                        .thenBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.dueDate }
                        .thenBy { it.id }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header title removed as requested
                    
                    // Tab Selector
                    val completedTasksCount = state.tasks.count { it.status == completedStatus }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF1F1F1),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(
                            "main" to "🏆 パフォーマンス実績",
                            "completed" to "✅ 完了済み (${completedTasksCount}件)"
                        ).forEach { (page, label) ->
                            val isSelected = subPage == page
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) {
                                            if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { subPage = page }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        if (page == "main") MaterialTheme.colorScheme.primary else Color(0xFF2E7D32)
                                    } else {
                                        if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color(0xFF616161)
                                    }
                                )
                            }
                        }
                    }

                    if (subPage == "main") {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Counters Row (Overdue & Carrying over)
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Overdue Card
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "⚠️ 期限切れタスク",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "$overdueCount 件",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "締め切り超過",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // Carried Over Card
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFFFF8E1).copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "⏳ 持ち越しタスク",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD84315)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "$carriedOverCount 件",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFD84315)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "予定日超過",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Week Achievement rate
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📅 今週のタスク達成率",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "$weekRate%",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = { weekRate / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )

                                        Text(
                                            text = "進捗: $completedWeekCount / ${weekTasks.size} 件完了",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )

                                        val motivationWeek = when {
                                            weekRate == 100 && weekTasks.isNotEmpty() -> "素晴らしい！今週のタスク完全クリアです！ 🎉"
                                            weekRate >= 75 -> "目標達成まであと一息！素晴らしいペースです🔥"
                                            weekRate >= 50 -> "半分達成！この調子で後半も進めましょう🚀"
                                            weekRate > 0 -> "一歩ずつ確実に進んでいます。頑張りましょう💪"
                                            else -> "タスクを設定して開始しましょう！ 📅"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "💬 $motivationWeek",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Month Achievement rate
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "✨ 今月のタスク達成率",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "$monthRate%",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = { monthRate / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.secondary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )

                                        Text(
                                            text = "進捗: $completedMonthCount / ${monthTasks.size} 件完了",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )

                                        val motivationMonth = when {
                                            monthRate == 100 && monthTasks.isNotEmpty() -> "信じられない快挙！今月パーフェクト達成！ 🏆"
                                            monthRate >= 75 -> "極めて順調です。圧倒的な推進力です！ ✨"
                                            monthRate >= 50 -> "目標の半分を消化。素晴らしい自己管理能力です！ 🌟"
                                            monthRate > 0 -> "良いスタートです。着実な一歩を重ねています。 🍀"
                                            else -> "今月はこれから！コツコツ積み上げましょう📈"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "💬 $motivationMonth",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Attention tasks
                            item {
                                Text(
                                    text = "対応推奨タスク (${warningTasks.size}件)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            if (warningTasks.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "なし",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "期限遅れのタスクはありません！",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "素晴らしい自己管理計画です。この調子を維持しましょう。✨",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(warningTasks) { task ->
                                    TaskItemCard(
                                        task = task,
                                        statusOptions = statusOptions,
                                        onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                        onEditClick = { onEditTask(task) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Completed tasks list view sub-page
                        val completedTasks = state.tasks.filter { it.status == completedStatus }

                        if (completedTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "なし",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "完了済みのタスクはありません",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Text(
                                        text = "完了済み (${completedTasks.size}件)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                items(completedTasks) { task ->
                                    TaskItemCard(
                                        task = task,
                                        statusOptions = statusOptions,
                                        onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                        onEditClick = { onEditTask(task) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(
    viewModel: TaskViewModel,
    statusOptions: List<String>,
    selectedCalendarDate: MutableState<String?>,
    onEditTask: (TaskModel) -> Unit
) {
    val uiState by viewModel.tasksState.collectAsState()

    var focusYear by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var focusMonth by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) } // 0-indexed

    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    // Initialize selected date to today if it's currently null
    LaunchedEffect(Unit) {
        if (selectedCalendarDate.value == null) {
            selectedCalendarDate.value = todayStr
        }
    }

    val selectedDate = selectedCalendarDate.value ?: todayStr
    val isSystemDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"
    val completedStatus = statusOptions.getOrNull(2) ?: "完了"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is TasksUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TasksUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "エラーが発生しました: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is TasksUiState.Idle, is TasksUiState.Success -> {
                val tasks = when (state) {
                    is TasksUiState.Success -> state.tasks
                    else -> emptyList()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Month Navigation Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (focusMonth == 0) {
                                focusMonth = 11
                                focusYear -= 1
                            } else {
                                focusMonth -= 1
                            }
                        }) {
                            Text("<", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Text(
                            text = "${focusYear}年 ${focusMonth + 1}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        IconButton(onClick = {
                            if (focusMonth == 11) {
                                focusMonth = 0
                                focusYear += 1
                            } else {
                                focusMonth += 1
                            }
                        }) {
                            Text(">", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Days of week row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val daysOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")
                        daysOfWeek.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (day == "日") Color.Red else (if (day == "土") Color.Blue else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    // Calendar Grid Layout
                    val calendarHelper = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.YEAR, focusYear)
                        set(java.util.Calendar.MONTH, focusMonth)
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                    val firstDayDayOfWeek = calendarHelper.get(java.util.Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, ...
                    val totalDaysInMonth = calendarHelper.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    val startOffset = firstDayDayOfWeek - 1 // Sunday starts, so offset is simple

                    val totalDaysToDisplay = startOffset + totalDaysInMonth
                    val rowsCount = (totalDaysToDisplay + 6) / 7

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until rowsCount) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val cellIndex = row * 7 + col
                                    val dayNumber = cellIndex - startOffset + 1

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (dayNumber in 1..totalDaysInMonth) {
                                            val dateStr = String.format("%04d-%02d-%02d", focusYear, focusMonth + 1, dayNumber)
                                            val isSelected = dateStr == selectedDate
                                            val isToday = dateStr == todayStr

                                            // Get tasks for this date
                                            val cellTasks = tasks.filter {
                                                it.dueDate == dateStr || it.scheduledDate == dateStr
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier.background(MaterialTheme.colorScheme.primary)
                                                        } else if (isToday) {
                                                            Modifier
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .clickable {
                                                        selectedCalendarDate.value = dateStr
                                                    }
                                                    .padding(2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = dayNumber.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else if (isToday) MaterialTheme.colorScheme.primary
                                                    else if (col == 0) Color.Red
                                                    else if (col == 6) Color.Blue
                                                    else MaterialTheme.colorScheme.onSurface
                                                )

                                                // Draw dots below date number
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    cellTasks.take(3).forEach { task ->
                                                        // Determine dot color
                                                        val dotColor = if (task.status == completedStatus) {
                                                            // Completed is gray-ish
                                                            if (isSystemDark) Color(0xFF616161) else Color(0xFFD6D6D6)
                                                        } else if (task.categoryColor != null) {
                                                            // Use category color
                                                            getNotionCategoryColorRaw(task.categoryColor, isSystemDark)
                                                        } else if (task.status == inProgressStatus) {
                                                            Color(0xFF29B6F6)
                                                        } else {
                                                            when (task.category) {
                                                                "課題" -> Color(0xFF42A5F5)
                                                                "学習" -> Color(0xFFAB47BC)
                                                                "作業" -> Color(0xFFFFCA28)
                                                                "趣味" -> Color(0xFF66BB6A)
                                                                else -> Color(0xFF78909C)
                                                            }
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .padding(horizontal = 0.5.dp)
                                                                .clip(RoundedCornerShape(50))
                                                                .background(dotColor)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                             }
                         }

                         HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                         // Selected Tasks List Header
                         val splitSelectedDate = selectedDate.split("-")
                         val displaySelectedDate = if (splitSelectedDate.size == 3) {
                             "${splitSelectedDate[1]}月${splitSelectedDate[2]}日"
                         } else {
                             selectedDate
                         }

                         Text(
                             text = "$displaySelectedDate のタスク",
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             color = MaterialTheme.colorScheme.onBackground
                         )

                         val selectedTasks = tasks.filter {
                             it.dueDate == selectedDate || it.scheduledDate == selectedDate
                         }

                         if (selectedTasks.isEmpty()) {
                             Box(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .padding(vertical = 32.dp),
                                 contentAlignment = Alignment.Center
                             ) {
                                 Text(
                                     text = "予定はありません",
                                     style = MaterialTheme.typography.bodyMedium,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                 )
                             }
                         } else {
                             Column(
                                 verticalArrangement = Arrangement.spacedBy(16.dp),
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                 selectedTasks.forEach { task ->
                                     TaskItemCard(
                                         task = task,
                                         statusOptions = statusOptions,
                                         onStatusClick = { viewModel.cycleTaskStatus(task, statusOptions) },
                                         onEditClick = { onEditTask(task) }
                                     )
                                 }
                             }
                         }
                     }
                 }
             }
         }
     }
 }

 // Simple color helper for getting base colors of dots
 fun getNotionCategoryColorRaw(colorName: String?, isDark: Boolean): Color {
     return when (colorName) {
         "gray" -> Color(0xFF9E9E9E)
         "brown" -> Color(0xFF8D6E63)
         "orange" -> Color(0xFFFF9800)
         "yellow" -> Color(0xFFFFCA28)
         "green" -> Color(0xFF66BB6A)
         "blue" -> Color(0xFF42A5F5)
         "purple" -> Color(0xFFAB47BC)
         "pink" -> Color(0xFFEC407A)
         "red" -> Color(0xFFEF5350)
         else -> Color(0xFF78909C)
     }
 }

// Pomodoro duration constants (single source of truth, in seconds)
private const val POMODORO_WORK_SEC = 25 * 60
private const val POMODORO_SHORT_BREAK_SEC = 5 * 60
private const val POMODORO_LONG_BREAK_SEC = 15 * 60

// After this many completed work sessions, take a long break instead of a short one
private const val POMODOROS_BEFORE_LONG_BREAK = 4

private fun pomodoroDurationSecondsFor(mode: String): Int = when (mode) {
    "work" -> POMODORO_WORK_SEC
    "shortBreak" -> POMODORO_SHORT_BREAK_SEC
    else -> POMODORO_LONG_BREAK_SEC
}

 @OptIn(ExperimentalMaterial3Api::class)
 @Composable
 fun PomodoroScreen(
    viewModel: TaskViewModel,
    statusOptions: List<String>
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val savedCompletedCountDate = remember { prefs.getString("completed_count_date", "") ?: "" }
    val initialCompletedCount = remember(todayStr, savedCompletedCountDate) {
        if (savedCompletedCountDate == todayStr) prefs.getInt("completed_count", 0) else 0
    }
    
    var timeLeft by remember { mutableStateOf(POMODORO_WORK_SEC) }
    var isRunning by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("work") } // "work", "shortBreak", "longBreak"
    var pomodoroCompletedCount by remember { mutableStateOf(initialCompletedCount) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    val inProgressStatus = statusOptions.getOrNull(1) ?: "進行中"
    val completedStatus = statusOptions.getOrNull(2) ?: "完了"

    LaunchedEffect(todayStr, savedCompletedCountDate) {
        if (savedCompletedCountDate != todayStr) {
            prefs.edit()
                .putInt("completed_count", 0)
                .putString("completed_count_date", todayStr)
                .apply()
        }
    }
    
    val tasksState by viewModel.tasksState.collectAsState()
    val uncompletedTasks = remember(tasksState, completedStatus) {
        when (val state = tasksState) {
            is TasksUiState.Success -> {
                state.tasks.filter { it.status != completedStatus }.sortedWith(
                    compareBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.scheduledDate }
                        .thenBy<TaskModel, String?>(nullsLast(naturalOrder())) { it.dueDate }
                        .thenBy { it.id }
                )
            }
            else -> emptyList()
        }
    }
    
    val activeFocusTask = remember(selectedTaskId, tasksState) {
        when (val state = tasksState) {
            is TasksUiState.Success -> state.tasks.find { it.id == selectedTaskId }
            else -> null
        }
    }

    // Service binding setup
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

    fun triggerServiceAction(action: String, durationMinutes: Int = -1) {
        val intent = Intent(context, PomodoroService::class.java).apply {
            this.action = action
            putExtra(PomodoroService.EXTRA_TASK_ID, selectedTaskId)
            putExtra(PomodoroService.EXTRA_TASK_TITLE, activeFocusTask?.title)
            putExtra(PomodoroService.EXTRA_MODE, mode)
            if (durationMinutes > 0) {
                putExtra(PomodoroService.EXTRA_DURATION_MINUTES, durationMinutes)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    // Update state based on service
    var isAlarmPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(boundService) {
        boundService?.let { service ->
            timeLeft = (service.timeLeftMs / 1000).toInt()
            isRunning = service.isRunning
            mode = service.currentMode
            selectedTaskId = service.associatedTaskId

            isAlarmPlaying = service.isRingtonePlaying

            service.onTickListener = { ms, _ ->
                timeLeft = (ms / 1000).toInt()
            }
            service.onFinishedListener = {
                timeLeft = 0
                isRunning = false
                if (mode == "work") {
                    pomodoroCompletedCount++
                    prefs.edit()
                        .putInt("completed_count", pomodoroCompletedCount)
                        .putString("completed_count_date", todayStr)
                        .apply()
                    if (pomodoroCompletedCount % POMODOROS_BEFORE_LONG_BREAK == 0) {
                        Toast.makeText(context, "集中セッション${POMODOROS_BEFORE_LONG_BREAK}回お疲れさまでした！長めの休憩をとりましょう。", Toast.LENGTH_LONG).show()
                        mode = "longBreak"
                    } else {
                        Toast.makeText(context, "集中セッション完了！素晴らしいです！短い休憩をとりましょう。", Toast.LENGTH_LONG).show()
                        mode = "shortBreak"
                    }
                    timeLeft = pomodoroDurationSecondsFor(mode)
                } else {
                    Toast.makeText(context, "休憩終了！次の集中セッションを始めましょう。", Toast.LENGTH_LONG).show()
                    mode = "work"
                    timeLeft = pomodoroDurationSecondsFor(mode)
                }
            }
            service.onStateChangedListener = { running ->
                isRunning = running
            }
            service.onRingtoneStateChangedListener = { playing ->
                isAlarmPlaying = playing
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 2. Mode Selector Segmented Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF1F1F1),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(
                Triple("work", "集中", Color(0xFFEF5350)),
                Triple("shortBreak", "休憩", Color(0xFF2E7D32)),
                Triple("longBreak", "長い休憩", Color(0xFF1976D2))
            ).forEach { (m, label, activeColor) ->
                val isSelected = mode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) {
                                if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            mode = m
                            timeLeft = pomodoroDurationSecondsFor(m)
                            triggerServiceAction(PomodoroService.ACTION_STOP)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isSelected) {
                            activeColor
                        } else {
                            if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color(0xFF616161)
                        }
                    )
                }
            }
        }

        // 3. Timer Display Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF141414) else Color.White
            ),
            border = BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFEEEEEE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Faint large timer icon in background
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = (when (mode) {
                        "work" -> Color(0xFFEF5350)
                        "shortBreak" -> Color(0xFF2E7D32)
                        else -> Color(0xFF1976D2)
                    }).copy(alpha = 0.03f),
                    modifier = Modifier.size(160.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Timer digits text
                    val minutes = timeLeft / 60
                    val seconds = timeLeft % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    
                    Text(
                        text = timeStr,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (isSystemInDarkTheme()) Color.White else Color(0xFF212121),
                        letterSpacing = (-1).sp
                    )

                    // Play / Pause / Reset Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reset Button
                        IconButton(
                            onClick = {
                                triggerServiceAction(PomodoroService.ACTION_STOP)
                                isRunning = false
                                timeLeft = pomodoroDurationSecondsFor(mode)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White,
                                    shape = RoundedCornerShape(50)
                                )
                                .border(
                                    BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF424242) else Color(0xFFE0E0E0)),
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "リセット",
                                tint = if (isSystemInDarkTheme()) Color.White else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Start/Pause Button
                        val modeColor = when (mode) {
                            "work" -> Color(0xFFEF5350)
                            "shortBreak" -> Color(0xFF2E7D32)
                            else -> Color(0xFF1976D2)
                        }
                        
                        IconButton(
                            onClick = {
                                if (isRunning) {
                                    triggerServiceAction(PomodoroService.ACTION_PAUSE)
                                } else {
                                    val durationMinutes = pomodoroDurationSecondsFor(mode) / 60
                                    triggerServiceAction(PomodoroService.ACTION_START_OR_RESUME, durationMinutes)
                                    if (mode == "work" && activeFocusTask != null) {
                                        if (activeFocusTask.status != inProgressStatus && activeFocusTask.status != completedStatus) {
                                            viewModel.updateTask(
                                                id = activeFocusTask.id,
                                                title = activeFocusTask.title,
                                                status = inProgressStatus,
                                                category = activeFocusTask.category,
                                                dueDate = activeFocusTask.dueDate,
                                                scheduledDate = activeFocusTask.scheduledDate
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = modeColor,
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "一時停止" else "開始",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        // Alarm Stop Button (表示条件: アラーム再生中でタイマーは停止)
                        if (!isRunning && isAlarmPlaying) {
                            IconButton(
                                onClick = {
                                    boundService?.stopRingtonePlayback()
                                    isAlarmPlaying = false
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color.White,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .border(
                                        BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF424242) else Color(0xFFE0E0E0)),
                                        shape = RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsOff,
                                    contentDescription = "アラーム停止",
                                    tint = if (isSystemInDarkTheme()) Color.White else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Stats Indicator
                    HorizontalDivider(color = if (isSystemInDarkTheme()) Color(0xFF2C2C2C) else Color(0xFFEEEEEE), thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日の完了数:",
                            fontSize = 10.sp,
                            color = if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pomodoroCompletedCount}回",
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (pomodoroCompletedCount > 0) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                repeat(minOf(pomodoroCompletedCount, 8)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFFEF5350), shape = RoundedCornerShape(50))
                                    )
                                }
                                if (pomodoroCompletedCount > 8) {
                                    Text(
                                        text = "+",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSystemInDarkTheme()) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Task Association Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "集中するタスクを紐付ける",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSystemInDarkTheme()) Color.White else Color(0xFF424242)
                )
            }

            // Custom Simple Dropdown
            var dropdownExpanded by remember { mutableStateOf(false) }
            val selectedTaskTitle = activeFocusTask?.let { "[${it.category}] ${it.title}" } ?: "-- タスクを選択しない (一般作業) --"

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedTaskTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "選択")
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("-- タスクを選択しない (一般作業) --", fontWeight = FontWeight.Medium, fontSize = 12.sp) },
                        onClick = {
                            selectedTaskId = null
                            dropdownExpanded = false
                        }
                    )
                    uncompletedTasks.forEach { task ->
                        DropdownMenuItem(
                            text = { Text("[${task.category}] ${task.title}", fontWeight = FontWeight.Medium, fontSize = 12.sp) },
                            onClick = {
                                selectedTaskId = task.id
                                dropdownExpanded = false
                                if (isRunning && mode == "work") {
                                    if (task.status != inProgressStatus && task.status != completedStatus) {
                                        viewModel.updateTask(
                                             id = task.id,
                                             title = task.title,
                                             status = inProgressStatus,
                                             category = task.category,
                                             dueDate = task.dueDate,
                                             scheduledDate = task.scheduledDate
                                         )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // 5. Focusing Task Panel Card
            activeFocusTask?.let { task ->
                val badgeBgColor = if (isSystemInDarkTheme()) Color(0xFF3E1F21) else Color(0xFFFFEBEE)
                val badgeTextColor = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)
                val panelBgColor = Color(0xFFEF5350).copy(alpha = 0.05f)
                val panelBorderColor = Color(0xFFEF5350).copy(alpha = 0.15f)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = panelBgColor
                    ),
                    border = BorderStroke(1.dp, panelBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(badgeBgColor, shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "現在フォーカス中",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeTextColor
                                )
                            }
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSystemInDarkTheme()) Color.White else Color(0xFF212121)
                            )
                            Text(
                                text = "分類: ${task.category}  •  状態: ${task.status}",
                                fontSize = 9.sp,
                                color = if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color.Gray
                            )
                        }

                        val buttonBgColor = if (isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                        val buttonIconTint = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF2E7D32)

                        IconButton(
                            onClick = {
                                viewModel.updateTask(
                                    id = task.id,
                                    title = task.title,
                                    status = completedStatus,
                                    category = task.category,
                                    dueDate = task.dueDate,
                                    scheduledDate = task.scheduledDate
                                )
                                selectedTaskId = null
                                Toast.makeText(context, "タスクを完了しました！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .background(buttonBgColor, shape = RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "完了", tint = buttonIconTint)
                        }
                    }
                }
            }


        }
    }
}