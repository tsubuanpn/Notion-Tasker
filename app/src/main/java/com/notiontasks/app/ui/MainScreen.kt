package com.notiontasks.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notiontasks.app.PomodoroService
import com.notiontasks.app.TaskNotificationReceiver
import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.components.AddTaskDialog
import com.notiontasks.app.ui.components.EditTaskDialog
import com.notiontasks.app.ui.navigation.Screen
import com.notiontasks.app.ui.screens.*
import com.notiontasks.app.ui.viewmodel.SettingsViewModel
import com.notiontasks.app.ui.viewmodel.SyncUiState
import com.notiontasks.app.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
    categoryOptions: List<NotionOptionInfo>,
    statusOptions: List<NotionOptionInfo>,
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
    val topBarTitle = when (currentRoute) {
        Screen.Home.route -> "Notion Tasker"
        Screen.Category.route -> Screen.Category.title
        Screen.Plan.route, Screen.Schedule.route -> Screen.Plan.title
        Screen.Pomodoro.route -> Screen.Pomodoro.title
        Screen.Achievements.route -> Screen.Achievements.title
        Screen.Settings.route -> Screen.Settings.title
        else -> "NotionTasker"
    }

    val notionToken by viewModel.notionToken.collectAsState()
    val databaseId by viewModel.databaseId.collectAsState()
    val setupCompleted by settingsViewModel.setupCompleted.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val permanentSyncFailures by viewModel.permanentSyncFailures.collectAsState()
    val isCreatingTask by viewModel.isCreatingTask.collectAsState()
    val activeTaskSaveKeys by viewModel.activeTaskSaveKeys.collectAsState()

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
    LaunchedEffect(notionToken, databaseId, setupCompleted) {
        if (setupCompleted && notionToken.isNotBlank() && databaseId.isNotBlank()) {
            viewModel.syncWithNotion()
        }
    }

    val showAddDialogState = remember { mutableStateOf(value = false) }
    val editingTaskState = remember { mutableStateOf<TaskModel?>(null) }
    val selectedCalendarDate = remember { mutableStateOf<String?>(null) }
    var isTaskSearchActive by rememberSaveable { mutableStateOf(false) }

    val activity = context as? ComponentActivity
    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent
        if (intent != null) {
            val dest = intent.getStringExtra("DESTINATION")
            val focusTaskId = intent.getStringExtra("FOCUS_TASK_ID")
            if (setupCompleted && dest == "pomodoro") {
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

    val devModeEnabled by settingsViewModel.devModeEnabled.collectAsState()
    val devCompleteButtonEnabled by settingsViewModel.devCompleteButtonEnabled.collectAsState()
    val isSetupRoute = currentRoute == Screen.Setup.route

    // ホームは常に既存のホームエントリへ戻す。画面状態の復元で、直前の「計画」を
    // ホームとして再表示してしまうことを避ける。
    val navigateToPrimaryScreen: (Screen) -> Unit = { screen ->
        if (screen == Screen.Home) {
            navController.popBackStack(Screen.Home.route, inclusive = false)
            if (navController.currentDestination?.route != Screen.Home.route) {
                navController.navigate(Screen.Home.route) { launchSingleTop = true }
            }
        } else if (currentRoute != screen.route) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isSetupRoute && (currentRoute == Screen.Settings.route || !isSettingsRoute)) {
                TopAppBar(
                    title = { Text(topBarTitle, fontWeight = FontWeight.Bold) },
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
                        if (currentRoute == Screen.Category.route) {
                            IconButton(onClick = { isTaskSearchActive = !isTaskSearchActive }) {
                                Icon(
                                    imageVector = if (isTaskSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (isTaskSearchActive) "検索を閉じる" else "検索",
                                )
                            }
                        }
                        if ((currentRoute == Screen.Home.route) || (currentRoute == Screen.Category.route) || (currentRoute == Screen.Plan.route) || (currentRoute == Screen.Schedule.route) || (currentRoute == Screen.Pomodoro.route) || (currentRoute == Screen.Achievements.route)) {
                            SyncStatusAction(
                                state = syncState,
                                onClick = {
                                    if (syncState is SyncUiState.NotConfigured) {
                                        navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                                    } else {
                                        viewModel.syncWithNotion()
                                    }
                                },
                            )
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
            if (!isSettingsRoute && !isSetupRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val screens = listOf(
                        Screen.Home,
                        Screen.Category,
                        Screen.Plan,
                        Screen.Pomodoro,
                        Screen.Achievements
                    ).filter { screen ->
                        when (screen) {
                            is Screen.Home -> true
                            is Screen.Category -> true
                            is Screen.Plan -> true
                            is Screen.Pomodoro -> true
                            is Screen.Achievements -> true
                            else -> true
                        }
                    }
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = {
                                Text(
                                    text = screen.title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = when (screen) {
                                is Screen.Plan -> currentRoute in setOf(Screen.Plan.route, Screen.Schedule.route)
                                else -> currentRoute == screen.route
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            onClick = {
                                navigateToPrimaryScreen(screen)
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSetupRoute && currentRoute in setOf(Screen.Home.route, Screen.Category.route, Screen.Plan.route)) {
                FloatingActionButton(
                    onClick = { showAddDialogState.value = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Default.Add, contentDescription = "タスクを追加") }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (setupCompleted) Screen.Home.route else Screen.Setup.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                TodayScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptions,
                    onOpenPlan = {
                        selectedCalendarDate.value = com.notiontasks.app.utils.JapanTime.todayIso()
                        navController.navigate(Screen.Plan.route) { launchSingleTop = true }
                    },
                    onOpenTasks = { navController.navigate(Screen.Category.route) { launchSingleTop = true } },
                    onStartFocus = { task ->
                        context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
                            .edit { putString("selected_task_id", task.id) }
                        navController.navigate(Screen.Pomodoro.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Screen.Category.route) {
                TasksScreen(
                    viewModel = viewModel,
                    statusOptions = statusOptions,
                    categoryOptions = categoryOptions,
                    onEditTask = { editingTaskState.value = it },
                    searchActive = isTaskSearchActive,
                )
            }
            composable(Screen.Plan.route) {
                PlanOverviewScreen(
                    viewModel = viewModel,
                    selectedCalendarDate = selectedCalendarDate,
                    statusOptions = statusOptions,
                    onOpenSchedule = { navController.navigate(Screen.Schedule.route) },
                    onEditTask = { editingTaskState.value = it },
                )
            }
            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    viewModel = viewModel,
                    selectedCalendarDate = selectedCalendarDate,
                    onOpenCalendar = { navController.popBackStack(Screen.Plan.route, inclusive = false) },
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
            composable(Screen.Settings.route) {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    navController = navController,
                    syncState = syncState,
                    onSync = { viewModel.syncWithNotion() },
                    permanentFailures = permanentSyncFailures,
                    onRetryFailure = viewModel::retryPermanentSyncFailure,
                    onDiscardFailure = viewModel::discardPermanentSyncFailure,
                )
            }
            composable(Screen.Setup.route) {
                NotionSetupScreen(
                    viewModel = viewModel,
                    onConnectionVerified = { token, dbId ->
                        settingsViewModel.stageNotionConnection(token, dbId)
                        navController.navigate(Screen.SettingsMapping.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.SettingsNotion.route) {
                NotionSettingsScreen(viewModel, settingsViewModel, navController)
            }
            composable(Screen.SettingsMapping.route) {
                MappingSettingsScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    navController = navController,
                    onSave = onSaveCredentials,
                    isSetupFlow = !setupCompleted,
                    onSetupCompleted = {
                        settingsViewModel.markSetupCompleted()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.SettingsNotifications.route) {
                NotificationsSettingsScreen(
                    settingsViewModel = settingsViewModel,
                    navController = navController,
                    onScheduleChanged = { TaskNotificationReceiver.rescheduleAlarms(context) },
                )
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
            composable(Screen.SettingsStats.route) {
                StatsManagementScreen(viewModel, settingsViewModel, navController)
            }
            composable(Screen.SettingsAbout.route) {
                AboutScreen(settingsViewModel, navController)
            }
            composable(Screen.SettingsDeveloper.route) {
                DeveloperSettingsScreen(settingsViewModel, navController)
            }
        }
    }

    if (showAddDialogState.value) {
        val defaultCategory = if (currentRoute == Screen.Category.route) selectedCategory else (categoryOptions.firstOrNull()?.name ?: "")
        val initialScheduled = ""
        AddTaskDialog(
            initialCategory = defaultCategory,
            categoryOptions = categoryOptions,
            statusOptions = statusOptions,
            initialScheduledDate = initialScheduled,
            isSaving = isCreatingTask,
            onDismiss = { showAddDialogState.value = false },
            onConfirm = { title, cat, status, due, sched ->
                viewModel.addTask(
                    title = title,
                    category = cat,
                    status = status,
                    dueDate = due,
                    scheduledDate = sched,
                    onConfirmed = {
                        showAddDialogState.value = false
                        Toast.makeText(context, "タスクを追加しました", Toast.LENGTH_SHORT).show()
                    },
                    onPending = {
                        showAddDialogState.value = false
                        Toast.makeText(context, "端末に保存しました。同期を再開すると追加されます", Toast.LENGTH_LONG).show()
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
            isSaving = task.id in activeTaskSaveKeys,
            onDismiss = { editingTaskState.value = null },
        ) { title, cat, stat, due, sched ->
            viewModel.updateTask(
                id = task.id,
                title = title,
                status = stat,
                category = cat,
                dueDate = due,
                scheduledDate = sched,
                onConfirmed = {
                    editingTaskState.value = null
                    Toast.makeText(context, "タスクを更新しました", Toast.LENGTH_SHORT).show()
                },
                onPending = {
                    editingTaskState.value = null
                    Toast.makeText(context, "端末に保存しました。同期を再開すると更新されます", Toast.LENGTH_LONG).show()
                }
            ) { errorMsg ->
                Toast.makeText(context, "エラー: $errorMsg", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun SyncStatusAction(state: SyncUiState, onClick: () -> Unit) {
    val (label, icon, containerColor, contentColor) = when (state) {
        is SyncUiState.Synced -> SyncBadge("同期済", Icons.Default.CloudDone, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        SyncUiState.Syncing -> SyncBadge("同期中", Icons.Default.Sync, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        is SyncUiState.PendingChanges -> SyncBadge("同期保留 (${state.count})", Icons.Default.CloudOff, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        is SyncUiState.Failed -> SyncBadge("同期エラー", Icons.Default.CloudOff, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        SyncUiState.NotConfigured -> SyncBadge("未設定", Icons.Default.CloudOff, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = contentColor)
        Spacer(Modifier.size(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
    }
}

private data class SyncBadge(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)
