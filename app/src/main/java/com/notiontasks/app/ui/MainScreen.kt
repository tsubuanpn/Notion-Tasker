package com.notiontasks.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notiontasks.app.PomodoroService
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.components.AddTaskDialog
import com.notiontasks.app.ui.components.EditTaskDialog
import com.notiontasks.app.ui.navigation.Screen
import com.notiontasks.app.ui.screens.*
import com.notiontasks.app.ui.viewmodel.SettingsViewModel
import com.notiontasks.app.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
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
        mStatusUnstarted: String,
        mStatusInProgress: String,
        mStatusCompleted: String,
        mCategory: String,
        mScheduled: String,
        mDue: String,
        mCatOptions: List<NotionOptionInfo>,
        mStatOptions: List<NotionOptionInfo>,
        themeColor: String,
        dynamicColor: Boolean,
        devMode: Boolean,
        devCompleteButton: Boolean,
    ) -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSettingsRoute = currentRoute?.startsWith("settings") == true

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

    val showAddDialogState = remember { mutableStateOf(value = false) }
    val editingTaskState = remember { mutableStateOf<TaskModel?>(null) }
    val selectedCalendarDate = remember { mutableStateOf<String?>(null) }
    var isSearchActive by remember { mutableStateOf(value = false) }

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

    val categoryTabEnabled by settingsViewModel.categoryTabEnabled.collectAsState()
    val calendarTabEnabled by settingsViewModel.calendarTabEnabled.collectAsState()
    val scheduleTabEnabled by settingsViewModel.scheduleTabEnabled.collectAsState()
    val pomodoroTabEnabled by settingsViewModel.pomodoroTabEnabled.collectAsState()
    val achievementsTabEnabled by settingsViewModel.achievementsTabEnabled.collectAsState()
    val devModeEnabled by settingsViewModel.devModeEnabled.collectAsState()
    val devCompleteButtonEnabled by settingsViewModel.devCompleteButtonEnabled.collectAsState()

    LaunchedEffect(categoryTabEnabled, calendarTabEnabled, scheduleTabEnabled, pomodoroTabEnabled, achievementsTabEnabled, currentRoute) {
        val isCurrentRouteDisabled = when (currentRoute) {
            Screen.Category.route -> !categoryTabEnabled
            Screen.Calendar.route -> !calendarTabEnabled
            Screen.Schedule.route -> !scheduleTabEnabled
            Screen.Pomodoro.route -> !pomodoroTabEnabled
            Screen.Achievements.route -> !achievementsTabEnabled
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
            if (currentRoute == Screen.Settings.route || !isSettingsRoute) {
                TopAppBar(
                    title = { Text("NotionTasker", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (isSettingsRoute && notionToken.isNotBlank() && databaseId.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    navController.popBackStack()
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
                        if ((currentRoute == Screen.Home.route) || (currentRoute == Screen.Category.route) || (currentRoute == Screen.Calendar.route) || (currentRoute == Screen.Schedule.route) || (currentRoute == Screen.Achievements.route)) {
                            IconButton(
                                onClick = { viewModel.syncWithNotion() },
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "同期")
                            }
                        }
                        if (!isSettingsRoute) {
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
            }
        },
        bottomBar = {
            if (!isSettingsRoute) {
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
                            is Screen.Category -> categoryTabEnabled
                            is Screen.Calendar -> calendarTabEnabled
                            is Screen.Schedule -> scheduleTabEnabled
                            is Screen.Pomodoro -> pomodoroTabEnabled
                            is Screen.Achievements -> achievementsTabEnabled
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
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
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
            if (!isSettingsRoute && (currentRoute != Screen.Achievements.route)) {
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
                ) { newOrder ->
                    onUpdateCategoryOptions(newOrder)
                }
            }
            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    viewModel = viewModel
                )
            }
            composable(Screen.Pomodoro.route) {
                PomodoroScreen(
                    viewModel = viewModel,
                    boundService = boundService,
                    devModeEnabled = devModeEnabled,
                    devCompleteButtonEnabled = devCompleteButtonEnabled
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
                    settingsViewModel = settingsViewModel,
                    navController = navController
                )
            }
            composable(Screen.SettingsNotion.route) {
                NotionSettingsScreen(viewModel, settingsViewModel, navController, onSaveCredentials)
            }
            composable(Screen.SettingsMapping.route) {
                MappingSettingsScreen(viewModel, settingsViewModel, navController, onSaveCredentials)
            }
            composable(Screen.SettingsNotifications.route) {
                NotificationsSettingsScreen(settingsViewModel, navController)
            }
            composable(Screen.SettingsTheme.route) {
                ThemeSettingsScreen(settingsViewModel, navController)
            }
            composable(Screen.SettingsPomodoro.route) {
                PomodoroSettingsScreen(settingsViewModel, navController)
            }
            composable(Screen.SettingsLifeActivity.route) {
                LifeActivitySettingsScreen(viewModel, navController)
            }
            composable(Screen.SettingsTabs.route) {
                TabsSettingsScreen(settingsViewModel, navController)
            }
            composable(Screen.SettingsStats.route) {
                StatsManagementScreen(viewModel, navController)
            }
            composable(Screen.SettingsAbout.route) {
                AboutScreen(settingsViewModel, navController, onSaveCredentials)
            }
            composable(Screen.SettingsDeveloper.route) {
                DeveloperSettingsScreen(settingsViewModel, navController, onSaveCredentials)
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
                    }
                ) { errorMsg ->
                    Toast.makeText(context, "エラー: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    editingTaskState.value?.let { task ->
        EditTaskDialog(
            task = task,
            categoryOptions = categoryOptions,
            statusOptions = statusOptions,
            onDismiss = { editingTaskState.value = null },
        ) { title, cat, stat, due, sched ->
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
                }
            ) { errorMsg ->
                Toast.makeText(context, "エラー: $errorMsg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
