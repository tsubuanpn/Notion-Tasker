package com.notiontasks.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.content.IntentCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.notiontasks.app.R
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.navigation.Screen
import com.notiontasks.app.ui.theme.AppThemePalettes
import com.notiontasks.app.ui.viewmodel.SettingsViewModel
import com.notiontasks.app.ui.viewmodel.TaskViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController
) {
    val devModeEnabled by settingsViewModel.devModeEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SettingsGroup(stringResource(R.string.settings_group_account)) {
            SettingsMenuItem(
                title = stringResource(R.string.settings_notion_title),
                subtitle = stringResource(R.string.settings_notion_subtitle),
                icon = Icons.Default.Cloud
            ) { navController.navigate(Screen.SettingsNotion.route) }
            
            SettingsMenuItem(
                title = stringResource(R.string.settings_mapping_title),
                subtitle = stringResource(R.string.settings_mapping_subtitle),
                icon = Icons.Default.Layers
            ) { navController.navigate(Screen.SettingsMapping.route) }
        }

        SettingsGroup(stringResource(R.string.settings_group_app)) {
            SettingsMenuItem(
                title = stringResource(R.string.settings_notifications_title),
                subtitle = stringResource(R.string.settings_notifications_subtitle),
                icon = Icons.Default.Notifications
            ) { navController.navigate(Screen.SettingsNotifications.route) }

            SettingsMenuItem(
                title = stringResource(R.string.settings_stats_title),
                subtitle = stringResource(R.string.settings_stats_subtitle),
                icon = Icons.Default.BarChart
            ) { navController.navigate(Screen.SettingsStats.route) }

            SettingsMenuItem(
                title = stringResource(R.string.settings_life_activity_title),
                subtitle = stringResource(R.string.settings_life_activity_subtitle),
                icon = Icons.Default.Favorite
            ) { navController.navigate(Screen.SettingsLifeActivity.route) }

            SettingsMenuItem(
                title = stringResource(R.string.settings_pomodoro_title),
                subtitle = stringResource(R.string.settings_pomodoro_subtitle),
                icon = Icons.Default.Timer
            ) { navController.navigate(Screen.SettingsPomodoro.route) }

            SettingsMenuItem(
                title = stringResource(R.string.settings_theme_title),
                subtitle = stringResource(R.string.settings_theme_subtitle),
                icon = Icons.Default.Palette
            ) { navController.navigate(Screen.SettingsTheme.route) }

            SettingsMenuItem(
                title = stringResource(R.string.settings_tabs_title),
                subtitle = stringResource(R.string.settings_tabs_subtitle),
                icon = Icons.Default.Menu
            ) { navController.navigate(Screen.SettingsTabs.route) }
        }

        if (devModeEnabled) {
            SettingsGroup(stringResource(R.string.settings_group_dev)) {
                SettingsMenuItem(
                    title = stringResource(R.string.settings_developer_title),
                    subtitle = stringResource(R.string.settings_developer_subtitle),
                    icon = Icons.Default.Code
                ) { navController.navigate(Screen.SettingsDeveloper.route) }
            }
        }

        SettingsGroup(stringResource(R.string.settings_group_other)) {
            SettingsMenuItem(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle),
                icon = Icons.Default.Info
            ) { navController.navigate(Screen.SettingsAbout.route) }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// --- Common Components ---

@Composable
fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                content()
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
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubPageScaffold(
    title: String,
    navController: NavController,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content(innerPadding)
        }
    }
}

// --- Sub-page Screens ---

@Composable
fun NotionSettingsScreen(
    viewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onSave: (
        token: String, dbId: String, morning: String, evening: String, mEnabled: Boolean, eEnabled: Boolean, theme: String,
        mTitle: String, mStatus: String, mStatusType: String, mStatusUnstarted: String, mStatusInProgress: String, mStatusCompleted: String,
        mCategory: String, mScheduled: String, mDue: String, mCatOptions: List<NotionOptionInfo>, mStatOptions: List<NotionOptionInfo>,
        themeColor: String, dynamicColor: Boolean, devMode: Boolean, devCompleteButton: Boolean,
    ) -> Unit
) {
    val token by settingsViewModel.notionToken.collectAsState()
    val dbId by settingsViewModel.databaseId.collectAsState()
    val context = LocalContext.current
    var isLoadingSchema by remember { mutableStateOf(false) }

    val successMsg = stringResource(R.string.settings_fetch_success)
    val errorMsgPrefix = stringResource(R.string.settings_fetch_error, "")

    SettingsSubPageScaffold(stringResource(R.string.settings_notion_title), navController) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = token,
                onValueChange = { settingsViewModel.updateNotionToken(it) },
                label = { Text(stringResource(R.string.settings_notion_token_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = dbId,
                onValueChange = { settingsViewModel.updateDatabaseId(it) },
                label = { Text(stringResource(R.string.settings_db_id_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            Button(
                onClick = {
                    isLoadingSchema = true
                    viewModel.fetchDatabaseProperties(
                        token,
                        dbId,
                        onSuccess = { meta ->
                            isLoadingSchema = false
                            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                            navController.navigate(Screen.SettingsMapping.route)
                        },
                        onFailure = { err ->
                            isLoadingSchema = false
                            Toast.makeText(context, errorMsgPrefix + err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                enabled = token.isNotBlank() && dbId.isNotBlank() && !isLoadingSchema,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoadingSchema) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_fetching))
                } else {
                    Text(stringResource(R.string.settings_fetch_schema), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MappingSettingsScreen(
    viewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onSave: (
        token: String, dbId: String, morning: String, evening: String, mEnabled: Boolean, eEnabled: Boolean, theme: String,
        mTitle: String, mStatus: String, mStatusType: String, mStatusUnstarted: String, mStatusInProgress: String, mStatusCompleted: String,
        mCategory: String, mScheduled: String, mDue: String, mCatOptions: List<NotionOptionInfo>, mStatOptions: List<NotionOptionInfo>,
        themeColor: String, dynamicColor: Boolean, devMode: Boolean, devCompleteButton: Boolean,
    ) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("notion_tasks_secure_prefs", Context.MODE_PRIVATE) }
    
    var propTitle by remember { mutableStateOf(sharedPrefs.getString("mapping_prop_title", "") ?: "") }
    var propStatus by remember { mutableStateOf(sharedPrefs.getString("mapping_prop_status", "") ?: "") }
    var propStatusType by remember { mutableStateOf(sharedPrefs.getString("mapping_prop_status_type", "status") ?: "status") }
    var propStatusUnstarted by remember { mutableStateOf(sharedPrefs.getString("mapping_status_unstarted", "未着手") ?: "未着手") }
    var propStatusInProgress by remember { mutableStateOf(sharedPrefs.getString("mapping_status_in_progress", "進行中") ?: "進行中") }
    var propStatusCompleted by remember { mutableStateOf(sharedPrefs.getString("mapping_status_completed", "完了") ?: "完了") }
    var propCategory by remember { mutableStateOf(sharedPrefs.getString("mapping_prop_category", "") ?: "") }
    var propScheduled by remember { mutableStateOf(sharedPrefs.getString("mapping_prop_scheduled_date", "") ?: "") }
    var propDue by remember { mutableStateOf(sharedPrefs.getString("mapping_prop_due_date", "") ?: "") }

    var metadata by remember { mutableStateOf<NotionDatabaseResponse?>(null) }
    
    LaunchedEffect(Unit) {
        val token = settingsViewModel.notionToken.value
        val dbId = settingsViewModel.databaseId.value
        if (token.isNotBlank() && dbId.isNotBlank()) {
            viewModel.fetchDatabaseProperties(token, dbId, onSuccess = { metadata = it }, onFailure = {})
        }
    }
    
    // 初回かつ未設定の場合に自動検知を試行
    LaunchedEffect(metadata) {
        if (metadata != null && propTitle.isBlank() && propStatus.isBlank()) {
            val detected = viewModel.autoDetectMapping(metadata!!)
            detected["title"]?.let { if (it.isNotBlank()) propTitle = it }
            detected["status"]?.let { if (it.isNotBlank()) propStatus = it }
            detected["statusType"]?.let { if (it.isNotBlank()) propStatusType = it }
            detected["statusUnstarted"]?.let { if (it.isNotBlank()) propStatusUnstarted = it }
            detected["statusInProgress"]?.let { if (it.isNotBlank()) propStatusInProgress = it }
            detected["statusCompleted"]?.let { if (it.isNotBlank()) propStatusCompleted = it }
            detected["category"]?.let { if (it.isNotBlank()) propCategory = it }
            detected["scheduled"]?.let { if (it.isNotBlank()) propScheduled = it }
            detected["due"]?.let { if (it.isNotBlank()) propDue = it }
        }
    }

    SettingsSubPageScaffold(stringResource(R.string.settings_mapping_title), navController) {
        PropertyDropdown("名前 (タスクタイトル)", propTitle, metadata?.properties?.filter { it.value.title != null }?.keys?.toList() ?: emptyList()) { propTitle = it }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1.8f)) {
                PropertyDropdown("状態 (ステータス)", propStatus, metadata?.properties?.filter { (it.value.status != null) || (it.value.select != null) }?.keys?.toList() ?: emptyList()) { propStatus = it }
            }
            Box(Modifier.weight(1.2f)) {
                TypeDropdown("タイプ", propStatusType) { propStatusType = it }
            }
        }

        val statusOptions = if (propStatusType == "status") {
            metadata?.properties?.get(propStatus)?.status?.options?.map { it.name } ?: emptyList()
        } else {
            metadata?.properties?.get(propStatus)?.select?.options?.map { it.name } ?: emptyList()
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ステータス値のマッピング", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                PropertyDropdown("「未着手」とみなす値", propStatusUnstarted, statusOptions) { propStatusUnstarted = it }
                PropertyDropdown("「進行中」とみなす値", propStatusInProgress, statusOptions) { propStatusInProgress = it }
                PropertyDropdown("「完了」とみなす値", propStatusCompleted, statusOptions) { propStatusCompleted = it }
            }
        }

        PropertyDropdown("種類 (カテゴリ)", propCategory, metadata?.properties?.filter { it.value.select != null }?.keys?.toList() ?: emptyList()) { propCategory = it }
        PropertyDropdown("予定日", propScheduled, metadata?.properties?.filter { it.value.date != null }?.keys?.toList() ?: emptyList()) { propScheduled = it }
        PropertyDropdown("締め切り", propDue, metadata?.properties?.filter { it.value.date != null }?.keys?.toList() ?: emptyList()) { propDue = it }

        Spacer(Modifier.height(16.dp))

        if (metadata != null) {
            Button(
                onClick = {
                    metadata?.let { meta ->
                        val detected = viewModel.autoDetectMapping(meta)
                        detected["title"]?.let { if (it.isNotBlank()) propTitle = it }
                        detected["status"]?.let { if (it.isNotBlank()) propStatus = it }
                        detected["statusType"]?.let { if (it.isNotBlank()) propStatusType = it }
                        detected["statusUnstarted"]?.let { if (it.isNotBlank()) propStatusUnstarted = it }
                        detected["statusInProgress"]?.let { if (it.isNotBlank()) propStatusInProgress = it }
                        detected["statusCompleted"]?.let { if (it.isNotBlank()) propStatusCompleted = it }
                        detected["category"]?.let { if (it.isNotBlank()) propCategory = it }
                        detected["scheduled"]?.let { if (it.isNotBlank()) propScheduled = it }
                        detected["due"]?.let { if (it.isNotBlank()) propDue = it }
                        Toast.makeText(context, "プロパティを自動検知しました", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, null)
                Spacer(Modifier.width(8.dp))
                Text("プロパティを自動設定する", fontWeight = FontWeight.Bold)
            }
        }

        val isAllMapped = propTitle.isNotBlank() && 
                          propStatus.isNotBlank() && 
                          propStatusUnstarted.isNotBlank() && 
                          propStatusInProgress.isNotBlank() && 
                          propStatusCompleted.isNotBlank() && 
                          propCategory.isNotBlank() && 
                          propScheduled.isNotBlank() && 
                          propDue.isNotBlank()

        Button(
            onClick = {
                val chosenCatProp = metadata?.properties?.get(propCategory)
                val catOptions = chosenCatProp?.select?.options?.filter { it.name.trim().isNotBlank() } ?: emptyList()

                val chosenStatProp = metadata?.properties?.get(propStatus)
                val statOptions = if (propStatusType == "status") {
                    chosenStatProp?.status?.options?.filter { it.name.trim().isNotBlank() } ?: emptyList()
                } else {
                    chosenStatProp?.select?.options?.filter { it.name.trim().isNotBlank() } ?: emptyList()
                }

                onSave(
                    settingsViewModel.notionToken.value,
                    settingsViewModel.databaseId.value,
                    settingsViewModel.morningNotifTime.value,
                    settingsViewModel.eveningNotifTime.value,
                    settingsViewModel.morningNotifEnabled.value,
                    settingsViewModel.eveningNotifEnabled.value,
                    settingsViewModel.themeMode.value,
                    propTitle, propStatus, propStatusType, propStatusUnstarted, propStatusInProgress, propStatusCompleted,
                    propCategory, propScheduled, propDue, catOptions, statOptions,
                    settingsViewModel.themeColorName.value,
                    settingsViewModel.dynamicColorEnabled.value,
                    settingsViewModel.devModeEnabled.value,
                    settingsViewModel.devCompleteButtonEnabled.value
                )
                navController.popBackStack()
            },
            enabled = isAllMapped,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.settings_save), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NotificationsSettingsScreen(settingsViewModel: SettingsViewModel, navController: NavController) {
    val morningEnabled by settingsViewModel.morningNotifEnabled.collectAsState()
    val morningTime by settingsViewModel.morningNotifTime.collectAsState()
    val eveningEnabled by settingsViewModel.eveningNotifEnabled.collectAsState()
    val eveningTime by settingsViewModel.eveningNotifTime.collectAsState()
    val context = LocalContext.current

    val showPicker = { currentTime: String, onUpdate: (String) -> Unit ->
        val parts = currentTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, hour, min ->
            onUpdate(String.format(java.util.Locale.US, "%02d:%02d", hour, min))
        }, h, m, true).show()
    }

    SettingsSubPageScaffold(stringResource(R.string.settings_notifications_title), navController) {
        NotificationItem(
            title = stringResource(R.string.settings_notif_morning),
            subtitle = stringResource(R.string.settings_notif_morning_desc),
            icon = Icons.Default.WbSunny,
            enabled = morningEnabled,
            time = morningTime,
            onToggle = { settingsViewModel.toggleMorningNotif(it) },
            onTimeClick = { showPicker(morningTime) { settingsViewModel.updateMorningTime(it) } }
        )

        NotificationItem(
            title = stringResource(R.string.settings_notif_evening),
            subtitle = stringResource(R.string.settings_notif_evening_desc),
            icon = Icons.Default.NightsStay,
            enabled = eveningEnabled,
            time = eveningTime,
            onToggle = { settingsViewModel.toggleEveningNotif(it) },
            onTimeClick = { showPicker(eveningTime) { settingsViewModel.updateEveningTime(it) } }
        )
    }
}

@Composable
fun NotificationItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    time: String,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                OutlinedButton(
                    onClick = onTimeClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_notif_time, time), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ThemeSettingsScreen(settingsViewModel: SettingsViewModel, navController: NavController) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val themeColor by settingsViewModel.themeColorName.collectAsState()
    val dynamicColorEnabled by settingsViewModel.dynamicColorEnabled.collectAsState()

    val modes = listOf(
        Triple("system", stringResource(R.string.settings_theme_system), Icons.Default.Layers),
        Triple("light", stringResource(R.string.settings_theme_light), Icons.Default.WbSunny),
        Triple("dark", stringResource(R.string.settings_theme_dark), Icons.Default.NightsStay)
    )

    SettingsSubPageScaffold(stringResource(R.string.settings_theme_title), navController) {
        Text(
            stringResource(R.string.settings_theme_mode),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (key, label, icon) ->
                val selected = themeMode == key
                Surface(
                    onClick = { settingsViewModel.updateThemeMode(key) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (selected) null else borderStroke(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                        RadioButton(selected = selected, onClick = { settingsViewModel.updateThemeMode(key) })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.settings_theme_color),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_theme_dynamic), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.settings_theme_dynamic_desc), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = dynamicColorEnabled, onCheckedChange = { settingsViewModel.toggleDynamicColor(it) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                val isColorEnabled = !dynamicColorEnabled || (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S)
                Text(
                    if (isColorEnabled) stringResource(R.string.settings_theme_preset) else stringResource(R.string.settings_theme_dynamic_priority),
                    style = MaterialTheme.typography.labelMedium
                )

                // Layout implementation (Simplified for compatibility)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemePalettes.take(5).forEach { palette ->
                        ThemeColorItem(palette, themeColor == palette.name, isColorEnabled) {
                            settingsViewModel.updateThemeColor(it)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemePalettes.drop(5).forEach { palette ->
                        ThemeColorItem(palette, themeColor == palette.name, isColorEnabled) {
                            settingsViewModel.updateThemeColor(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeColorItem(palette: com.notiontasks.app.ui.theme.AppThemePalette, isSelected: Boolean, enabled: Boolean, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.seed)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .clickable(enabled = enabled) { onClick(palette.name) },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PomodoroSettingsScreen(settingsViewModel: SettingsViewModel, navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
    var work by remember { mutableIntStateOf(prefs.getInt("work_duration_min", 25)) }
    var sBreak by remember { mutableIntStateOf(prefs.getInt("short_break_duration_min", 5)) }
    var lBreak by remember { mutableIntStateOf(prefs.getInt("long_break_duration_min", 15)) }

    var uriStr by remember { mutableStateOf(prefs.getString("alarm_uri", "") ?: "") }
    var preview by remember { mutableStateOf<Ringtone?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val picked = res.data?.let { intent ->
                IntentCompat.getParcelableExtra(intent, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            uriStr = picked?.toString() ?: ""
            prefs.edit { putString("alarm_uri", uriStr) }
        }
    }

    DisposableEffect(Unit) { onDispose { preview?.stop() } }

    SettingsSubPageScaffold(stringResource(R.string.settings_pomodoro_title), navController) {
        SettingsGroup("タイマー時間") {
            DurationSettingRow(stringResource(R.string.settings_pomodoro_work), work, { work = it }, stringResource(R.string.settings_pomodoro_unit), MaterialTheme.colorScheme.primary)
            DurationSettingRow(stringResource(R.string.settings_pomodoro_short_break), sBreak, { sBreak = it }, stringResource(R.string.settings_pomodoro_unit), MaterialTheme.colorScheme.secondary)
            DurationSettingRow(stringResource(R.string.settings_pomodoro_long_break), lBreak, { lBreak = it }, stringResource(R.string.settings_pomodoro_unit), MaterialTheme.colorScheme.tertiary)
        }

        SettingsGroup(stringResource(R.string.settings_alarm_title)) {
            val alarmTitle = try {
                val u = if (uriStr.isNotBlank()) uriStr.toUri() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                RingtoneManager.getRingtone(context, u)?.getTitle(context) ?: "未設定"
            } catch (_: Exception) { "未設定" }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_alarm_desc)) },
                supportingContent = { Text(stringResource(R.string.settings_alarm_current, alarmTitle)) },
                leadingContent = { Icon(Icons.Default.MusicNote, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        if (uriStr.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uriStr.toUri())
                    }
                    launcher.launch(intent)
                }) { Text(stringResource(R.string.settings_alarm_pick)) }
                
                FilledTonalIconButton(onClick = {
                    try {
                        preview?.stop()
                        val u = if (uriStr.isNotBlank()) uriStr.toUri() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        preview = RingtoneManager.getRingtone(context, u).apply {
                            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                            play()
                        }
                    } catch (_: Exception) {}
                }) { Icon(Icons.Default.PlayArrow, null) }
                
                IconButton(onClick = { preview?.stop() }) { Icon(Icons.Default.Stop, null) }
            }
        }

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { work = 25; sBreak = 5; lBreak = 15 }, Modifier.weight(1f)) { Text(stringResource(R.string.settings_pomodoro_default)) }
            Button(
                onClick = {
                    prefs.edit { putInt("work_duration_min", work); putInt("short_break_duration_min", sBreak); putInt("long_break_duration_min", lBreak) }
                    navController.popBackStack()
                },
                Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_save)) }
        }
    }
}

@Composable
fun LifeActivitySettingsScreen(viewModel: TaskViewModel, navController: NavController) {
    SettingsSubPageScaffold(stringResource(R.string.settings_life_activity_title), navController) {
        LifeActivitySettingsSection(viewModel)
    }
}

@Composable
fun TabsSettingsScreen(settingsViewModel: SettingsViewModel, navController: NavController) {
    val cat by settingsViewModel.categoryTabEnabled.collectAsState()
    val cal by settingsViewModel.calendarTabEnabled.collectAsState()
    val sch by settingsViewModel.scheduleTabEnabled.collectAsState()
    val pom by settingsViewModel.pomodoroTabEnabled.collectAsState()
    val ach by settingsViewModel.achievementsTabEnabled.collectAsState()

    SettingsSubPageScaffold(stringResource(R.string.settings_tabs_title), navController) {
        TabSettingRow(stringResource(R.string.settings_tab_category), stringResource(R.string.settings_tab_category_desc), Icons.AutoMirrored.Filled.List, cat) { settingsViewModel.toggleTab("category", it) }
        TabSettingRow(stringResource(R.string.settings_tab_calendar), stringResource(R.string.settings_tab_calendar_desc), Icons.Default.DateRange, cal) { settingsViewModel.toggleTab("calendar", it) }
        TabSettingRow(stringResource(R.string.settings_tab_schedule), stringResource(R.string.settings_tab_schedule_desc), Icons.Default.Schedule, sch) { settingsViewModel.toggleTab("schedule", it) }
        TabSettingRow(stringResource(R.string.settings_tab_pomodoro), stringResource(R.string.settings_tab_pomodoro_desc), Icons.Default.Timer, pom) { settingsViewModel.toggleTab("pomodoro", it) }
        TabSettingRow(stringResource(R.string.settings_tab_achievements), stringResource(R.string.settings_tab_achievements_desc), Icons.Default.Star, ach) { settingsViewModel.toggleTab("achievements", it) }
    }
}

@Composable
fun StatsManagementScreen(viewModel: TaskViewModel, settingsViewModel: SettingsViewModel, navController: NavController) {
    SettingsSubPageScaffold(stringResource(R.string.settings_stats_title), navController) {
        DataManagementSection(viewModel, settingsViewModel)
    }
}

@Composable
fun AboutScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onSave: (token: String, dbId: String, morning: String, evening: String, mEnabled: Boolean, eEnabled: Boolean, theme: String, mTitle: String, mStatus: String, mStatusType: String, mStatusUnstarted: String, mStatusInProgress: String, mStatusCompleted: String, mCategory: String, mScheduled: String, mDue: String, mCatOptions: List<NotionOptionInfo>, mStatOptions: List<NotionOptionInfo>, themeColor: String, dynamicColor: Boolean, devMode: Boolean, devCompleteButton: Boolean) -> Unit
) {
    val context = LocalContext.current
    val devModeEnabled by settingsViewModel.devModeEnabled.collectAsState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) { "1.3.2" }

    SettingsSubPageScaffold(stringResource(R.string.settings_about_title), navController) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NotionTasker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_about_version, version ?: "1.3.2"), style = MaterialTheme.typography.bodyLarge)
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/tsubuanpn/Notion-Tasker") },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_about_github))
                }
            }
        }

        SettingsGroup(stringResource(R.string.settings_about_dev_info)) {
            ListItem(
                headlineContent = { Text("tsubuanpn", fontWeight = FontWeight.Bold) },
                leadingContent = {
                    Image(
                        painter = painterResource(id = R.drawable.dev_icon),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        SettingsGroup("詳細設定") {
            Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_about_dev_mode), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_about_dev_mode_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = devModeEnabled, onCheckedChange = { settingsViewModel.toggleDevMode(it) })
            }
        }
    }
}

@Composable
fun DeveloperSettingsScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onSave: (token: String, dbId: String, morning: String, evening: String, mEnabled: Boolean, eEnabled: Boolean, theme: String, mTitle: String, mStatus: String, mStatusType: String, mStatusUnstarted: String, mStatusInProgress: String, mStatusCompleted: String, mCategory: String, mScheduled: String, mDue: String, mCatOptions: List<NotionOptionInfo>, mStatOptions: List<NotionOptionInfo>, themeColor: String, dynamicColor: Boolean, devMode: Boolean, devCompleteButton: Boolean) -> Unit
) {
    val devCompleteEnabled by settingsViewModel.devCompleteButtonEnabled.collectAsState()

    SettingsSubPageScaffold(stringResource(R.string.settings_developer_title), navController) {
        SettingsGroup(stringResource(R.string.settings_dev_timer_group)) {
            Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_dev_complete_btn), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_dev_complete_btn_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = devCompleteEnabled, onCheckedChange = { settingsViewModel.toggleDevCompleteButton(it) })
            }
        }
    }
}

@Composable
fun PropertyDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val displayOptions = if (selected.isNotBlank() && (selected !in options)) listOf(selected) + options else options

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.ifBlank { "未選択" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            shape = RoundedCornerShape(12.dp),
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("未選択") }, onClick = { onSelect(""); expanded = false })
            displayOptions.filter { it.isNotBlank() }.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
            }
        }
    }
}

@Composable
fun TypeDropdown(label: String, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (selected == "status") "Status型" else "Select型",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            shape = RoundedCornerShape(12.dp),
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Status型") }, onClick = { onSelect("status"); expanded = false })
            DropdownMenuItem(text = { Text("Select型") }, onClick = { onSelect("select"); expanded = false })
        }
    }
}

@Composable
fun DurationSettingRow(label: String, value: Int, onValueChange: (Int) -> Unit, unit: String, color: Color) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = { if (value > 1) onValueChange(value - 1) }, Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))) { Text("-") }
            OutlinedTextField(
                value = text,
                onValueChange = { v -> v.filter { it.isDigit() }.let { if (it.length <= 3) { text = it; it.toIntOrNull()?.let { n -> onValueChange(n.coerceIn(1, 999)) } } } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = color),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.width(72.dp).height(52.dp)
            )
            IconButton(onClick = { if (value < 999) onValueChange(value + 1) }, Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))) { Text("+") }
            Text(unit, Modifier.width(20.dp))
        }
    }
}

@Composable
fun TabSettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun borderStroke() = androidx.compose.foundation.BorderStroke(
    1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)

@Composable
fun DataManagementSection(viewModel: TaskViewModel, settingsViewModel: SettingsViewModel) {
    val statsDuration by viewModel.statsStorageDuration.collectAsState()
    val keepType by settingsViewModel.completedTaskKeepType.collectAsState()
    val keepMonths by settingsViewModel.completedTaskKeepDateMonths.collectAsState()
    val keepCount by settingsViewModel.completedTaskKeepCount.collectAsState()

    var showDeletePeriodDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteTargetMonths by remember { mutableIntStateOf(0) }

    val durationOptions = listOf(1 to "1ヶ月", 3 to "3ヶ月", 6 to "6ヶ月", 12 to "1年", 0 to "無制限")

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- 完了済みタスクの保持制限 ---
        Text(
            stringResource(R.string.settings_data_completed_tasks_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 制限タイプ選択
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { settingsViewModel.updateCompletedTaskKeepType("date") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (keepType == "date") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (keepType == "date") null else borderStroke()
                    ) {
                        Row(
                            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_data_keep_type_date), style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Surface(
                        onClick = { settingsViewModel.updateCompletedTaskKeepType("count") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (keepType == "count") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (keepType == "count") null else borderStroke()
                    ) {
                        Row(
                            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Numbers, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_data_keep_type_count), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (keepType == "date") {
                    // 日付制限の選択肢 (バー形式)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        durationOptions.forEach { (months, label) ->
                            val isSelected = keepMonths == months
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { settingsViewModel.updateCompletedTaskKeepDateMonths(months) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                } else {
                    // 件数制限の入力
                    var countText by remember(keepCount) { mutableStateOf(keepCount.toString()) }
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.let {
                                if (it.length <= 5) {
                                    countText = it
                                    it.toIntOrNull()?.let { n -> settingsViewModel.updateCompletedTaskKeepCount(n) }
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_data_keep_count_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // --- 統計データ（ポモドーロ）の保持期間 ---
        Text(
            stringResource(R.string.settings_stats_duration),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    durationOptions.forEach { (months, label) ->
                        val isSelected = statsDuration == months
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setStatsStorageDuration(months) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // --- データ削除・メンテナンス ---
        Text(
            "データのメンテナンス",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showDeletePeriodDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_data_delete_stats_btn))
            }

            OutlinedButton(
                onClick = { viewModel.syncWithNotion(incremental = false) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_data_reset_sync))
            }

            Text(
                stringResource(R.string.settings_data_help_notion_remains),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }

    // --- ダイアログ ---

    // 1. 削除期間選択ダイアログ
    if (showDeletePeriodDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePeriodDialog = false },
            title = { Text(stringResource(R.string.settings_data_delete_select_period)) },
            text = {
                val deleteOptions = listOf(1 to "1ヶ月以上前の記録", 3 to "3ヶ月以上前の記録", 6 to "6ヶ月以上前の記録", 12 to "1年以上前の記録", -1 to "すべての記録")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    deleteOptions.forEach { (months, label) ->
                        TextButton(
                            onClick = {
                                deleteTargetMonths = months
                                showDeletePeriodDialog = false
                                showDeleteConfirmDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDeletePeriodDialog = false }) { Text("キャンセル") }
            }
        )
    }

    // 2. 最終確認ダイアログ
    if (showDeleteConfirmDialog) {
        val targetLabel = when (deleteTargetMonths) {
            -1 -> "すべて"
            else -> "${deleteTargetMonths}ヶ月以上前"
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_data_delete_confirm_title), color = MaterialTheme.colorScheme.error) },
            text = { Text(stringResource(R.string.settings_stats_delete_msg, targetLabel)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePomodoroLogsByMonths(deleteTargetMonths)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_stats_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("キャンセル") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeActivitySettingsSection(
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val lifeActivities by viewModel.lifeActivities.collectAsState()
    
    var showDialog by remember { mutableStateOf(value = false) }
    var editingActivity by remember { mutableStateOf<com.notiontasks.app.data.model.LifeActivity?>(null) }
    
    var actName by remember { mutableStateOf("") }
    var actDuration by remember { mutableStateOf("30") }
    var actColor by remember { mutableStateOf("#4CAF50") }

    var hasDefaultTime by remember { mutableStateOf(value = false) }
    var defaultStartHour by remember { mutableIntStateOf(8) }
    var defaultStartMin by remember { mutableIntStateOf(0) }
    var defaultEndHour by remember { mutableIntStateOf(9) }
    var defaultEndMin by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "生活習慣プリセットの設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    editingActivity = null
                    actName = ""
                    actDuration = "30"
                    actColor = "#4CAF50"
                    hasDefaultTime = false
                    defaultStartHour = 8
                    defaultStartMin = 0
                    defaultEndHour = 9
                    defaultEndMin = 0
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, "追加")
                Spacer(modifier = Modifier.width(4.dp))
                Text("追加")
            }
        }

        Text(
            "ここで設定した生活習慣は、時間割の登録時にプリセットとして使えるだけでなく、デフォルト時間を設定しておくことで、毎日最初から時間割に自動的に配置されます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lifeActivities.isEmpty()) {
                    Text(
                        "プリセットがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                    )
                } else {
                    lifeActivities.forEach { act ->
                        val colorParsed = try {
                            Color(act.color.toColorInt())
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.secondaryContainer
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable {
                                    editingActivity = act
                                    actName = act.name
                                    actDuration = act.durationMinutes.toString()
                                    actColor = act.color
                                    hasDefaultTime = (act.defaultStartTime != null) && (act.defaultEndTime != null)
                                    
                                    val startTot = act.defaultStartTime ?: 480
                                    defaultStartHour = startTot / 60
                                    defaultStartMin = startTot % 60
                                    
                                    val endTot = act.defaultEndTime ?: (startTot + act.durationMinutes)
                                    defaultEndHour = endTot / 60
                                    defaultEndMin = endTot % 60
                                    
                                    showDialog = true
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(colorParsed, RoundedCornerShape(100.dp))
                                )
                                Column {
                                    Text(
                                        text = act.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "標準: ${act.durationMinutes}分",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if ((act.defaultStartTime != null) && (act.defaultEndTime != null)) {
                                            val startH = act.defaultStartTime / 60
                                            val startM = act.defaultStartTime % 60
                                            val endH = act.defaultEndTime / 60
                                            val endM = act.defaultEndTime % 60
                                            Text(
                                                text = String.format(java.util.Locale.US, "• 自動配置: %02d:%02d ~ %02d:%02d", startH, startM, endH, endM),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column {
                                    IconButton(
                                        onClick = { viewModel.moveLifeActivity(act.id, -1) },
                                        enabled = lifeActivities.indexOf(act) > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "上に移動",
                                            tint = if (lifeActivities.indexOf(act) > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveLifeActivity(act.id, 1) },
                                        enabled = (lifeActivities.indexOf(act) < (lifeActivities.size - 1)),
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "下に移動",
                                            tint = if (lifeActivities.indexOf(act) < (lifeActivities.size - 1)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.deleteLifeActivity(context, act.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "削除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        text = if (editingActivity != null) "生活習慣を編集" else "生活習慣を追加",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = actName,
                            onValueChange = { actName = it },
                            label = { Text("生活習慣名 (必須)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = actDuration,
                            onValueChange = { actDuration = it.filter { c -> c.isDigit() } },
                            label = { Text("標準の時間 (分)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("カラー:")
                            val colors = listOf("#EF5350", "#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#E91E63", "#78909C")
                            colors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(c.toColorInt()), RoundedCornerShape(100.dp))
                                        .border(
                                            width = if (actColor == c) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = RoundedCornerShape(100.dp)
                                        )
                                        .clickable { actColor = c }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("デフォルト時間の設定", fontWeight = FontWeight.Bold)
                                Text("毎日この時間に自動で時間割へ配置されます", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = hasDefaultTime,
                                onCheckedChange = { hasDefaultTime = it }
                            )
                        }

                        if (hasDefaultTime) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("開始時間:", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                                Button(
                                    onClick = {
                                        android.app.TimePickerDialog(context, { _, hour, minute ->
                                            defaultStartHour = hour
                                            defaultStartMin = minute
                                            val startTot = (defaultStartHour * 60) + defaultStartMin
                                            val endTot = (defaultEndHour * 60) + defaultEndMin
                                            if (endTot <= startTot) {
                                                val newEnd = startTot + 60
                                                defaultEndHour = (newEnd / 60) % 24
                                                defaultEndMin = newEnd % 60
                                            }
                                        }, defaultStartHour, defaultStartMin, true).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) {
                                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(String.format(java.util.Locale.US, "%02d:%02d", defaultStartHour, defaultStartMin))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("終了時間:", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                                Button(
                                    onClick = {
                                        android.app.TimePickerDialog(context, { _, hour, minute ->
                                            defaultEndHour = hour
                                            defaultEndMin = minute
                                        }, defaultEndHour, defaultEndMin, true).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) {
                                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(String.format(java.util.Locale.US, "%02d:%02d", defaultEndHour, defaultEndMin))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (actName.isBlank()) return@Button
                            val duration = actDuration.toIntOrNull() ?: 30
                            val startTot = if (hasDefaultTime) (defaultStartHour * 60) + defaultStartMin else null
                            val endTot = if (hasDefaultTime) (defaultEndHour * 60) + defaultEndMin else null

                            val newAct = com.notiontasks.app.data.model.LifeActivity(
                                id = editingActivity?.id ?: ("la_" + java.util.UUID.randomUUID().toString().take(6)),
                                name = actName,
                                durationMinutes = duration,
                                color = actColor,
                                defaultStartTime = startTot,
                                defaultEndTime = endTot
                            )

                            if (editingActivity != null) {
                                val updated = lifeActivities.map { if (it.id == editingActivity!!.id) newAct else it }
                                viewModel.saveLifeActivities(context, updated)
                            } else {
                                viewModel.addLifeActivity(context, newAct)
                            }
                            showDialog = false
                        },
                        enabled = actName.isNotBlank()
                    ) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("キャンセル") } }
            )
        }
    }
}
