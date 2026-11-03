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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.notiontasks.app.ui.viewmodel.SyncUiState
import com.notiontasks.app.ui.viewmodel.TaskViewModel
import com.notiontasks.app.utils.SecurityUtils

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    syncState: SyncUiState,
    onSync: () -> Unit,
) {
    val devModeEnabled by settingsViewModel.devModeEnabled.collectAsState()
    var showSyncFailureDetails by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NotionConnectionStatusCard(
            syncState = syncState,
            onOpenConnection = { navController.navigate(Screen.SettingsNotion.route) },
            onStartSetup = { navController.navigate(Screen.Setup.route) },
            onSync = onSync,
            onShowFailureDetails = { showSyncFailureDetails = true },
        )

        SettingsGroup("日常の設定") {
            SettingsMenuItem(
                title = "通知",
                subtitle = "リマインダーとお知らせを設定します",
                icon = Icons.Default.Notifications
            ) { navController.navigate(Screen.SettingsNotifications.route) }

            SettingsMenuItem(
                title = "集中タイマー",
                subtitle = "ポモドーロ・休憩・アラームを設定します",
                icon = Icons.Default.Timer
            ) { navController.navigate(Screen.SettingsPomodoro.route) }

            SettingsMenuItem(
                title = "外観",
                subtitle = "テーマと表示をカスタマイズします",
                icon = Icons.Default.Palette
            ) { navController.navigate(Screen.SettingsTheme.route) }
        }

        SettingsGroup("Notion と同期") {
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

        SettingsGroup("アプリ管理") {
            SettingsMenuItem(
                title = stringResource(R.string.settings_life_activity_title),
                subtitle = stringResource(R.string.settings_life_activity_subtitle),
                icon = Icons.Default.Favorite
            ) { navController.navigate(Screen.SettingsLifeActivity.route) }

            SettingsMenuItem(
                title = "データ管理",
                subtitle = stringResource(R.string.settings_stats_subtitle),
                icon = Icons.Default.BarChart
            ) { navController.navigate(Screen.SettingsStats.route) }
        }

        SettingsGroup("高度な設定") {
            if (devModeEnabled) {
                SettingsMenuItem(
                    title = stringResource(R.string.settings_developer_title),
                    subtitle = stringResource(R.string.settings_developer_subtitle),
                    icon = Icons.Default.Code
                ) { navController.navigate(Screen.SettingsDeveloper.route) }
            }

            SettingsMenuItem(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle),
                icon = Icons.Default.Info
            ) { navController.navigate(Screen.SettingsAbout.route) }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showSyncFailureDetails && syncState is SyncUiState.Failed) {
        AlertDialog(
            onDismissRequest = { showSyncFailureDetails = false },
            title = { Text("同期エラーの詳細") },
            text = { Text(syncState.message) },
            confirmButton = {
                Button(onClick = {
                    showSyncFailureDetails = false
                    onSync()
                }) { Text("再試行") }
            },
            dismissButton = { TextButton(onClick = { showSyncFailureDetails = false }) { Text("閉じる") } },
        )
    }
}

@Composable
private fun NotionConnectionStatusCard(
    syncState: SyncUiState,
    onOpenConnection: () -> Unit,
    onStartSetup: () -> Unit,
    onSync: () -> Unit,
    onShowFailureDetails: () -> Unit,
) {
    val presentation = when (syncState) {
        is SyncUiState.Synced -> ConnectionCardPresentation(
            title = "Notion 接続確認済み",
            subtitle = "接続設定とプロパティを確認",
            icon = Icons.Default.CloudDone,
            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            iconTint = MaterialTheme.colorScheme.primary,
            actionLabel = "接続設定",
        )
        SyncUiState.Syncing -> ConnectionCardPresentation(
            title = "Notion に接続中",
            subtitle = "同期状態を確認しています",
            icon = Icons.Default.Sync,
            container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            iconTint = MaterialTheme.colorScheme.secondary,
            actionLabel = null,
        )
        is SyncUiState.PendingChanges -> ConnectionCardPresentation(
            title = "Notion へ未同期の変更があります",
            subtitle = "${syncState.count}件の変更を次回同期で反映します",
            icon = Icons.Default.CloudUpload,
            container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            iconTint = MaterialTheme.colorScheme.secondary,
            actionLabel = "今すぐ同期",
        )
        is SyncUiState.Failed -> ConnectionCardPresentation(
            title = "Notion との同期に失敗しました",
            subtitle = "通信や接続設定を確認して、もう一度同期してください",
            icon = Icons.Default.CloudOff,
            container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.50f),
            content = MaterialTheme.colorScheme.onErrorContainer,
            iconTint = MaterialTheme.colorScheme.error,
            actionLabel = "再試行",
        )
        SyncUiState.NotConfigured -> ConnectionCardPresentation(
            title = "Notion を接続する",
            subtitle = "タスクを同期するには接続が必要です",
            icon = Icons.Default.CloudOff,
            container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            content = MaterialTheme.colorScheme.onErrorContainer,
            iconTint = MaterialTheme.colorScheme.error,
            actionLabel = "接続を開始",
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = presentation.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    tint = presentation.iconTint,
                    modifier = Modifier.size(32.dp),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(presentation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = presentation.content)
                    Text(presentation.subtitle, style = MaterialTheme.typography.bodySmall, color = presentation.content.copy(alpha = 0.76f))
                }
            }
            if (syncState == SyncUiState.Syncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = presentation.iconTint)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (syncState is SyncUiState.Failed) {
                        OutlinedButton(onClick = onShowFailureDetails) { Text("詳細") }
                    }
                    presentation.actionLabel?.let { label ->
                        Button(
                            onClick = when (syncState) {
                                is SyncUiState.Synced -> onOpenConnection
                                is SyncUiState.PendingChanges, is SyncUiState.Failed -> onSync
                                SyncUiState.NotConfigured -> onStartSetup
                                SyncUiState.Syncing -> ({})
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}

private data class ConnectionCardPresentation(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val container: Color,
    val content: Color,
    val iconTint: Color,
    val actionLabel: String?,
)

// --- Common Components ---

@Composable
fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
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

/** 初回だけ表示する、通常設定とは独立した安全な接続フロー。 */
@Composable
fun NotionSetupScreen(
    viewModel: TaskViewModel,
    onConnectionVerified: (token: String, databaseId: String) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var databaseId by remember { mutableStateOf("") }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text("Notion Tasker をはじめる", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "最初にNotionのタスクデータベースへ安全に接続します。接続確認とプロパティ設定が完了するまで、タスクの同期は開始しません。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SetupStepChip("1", "接続", selected = true)
                HorizontalDivider(modifier = Modifier.width(24.dp))
                SetupStepChip("2", "確認", selected = false)
                HorizontalDivider(modifier = Modifier.width(24.dp))
                SetupStepChip("3", "同期", selected = false)
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it; errorMessage = null },
                label = { Text("Notion インテグレーション トークン") },
                supportingText = { Text("Notionで作成したIntegrationのシークレットを入力します") },
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (tokenVisible) "トークンを隠す" else "トークンを表示する",
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = databaseId,
                onValueChange = { databaseId = it; errorMessage = null },
                label = { Text("タスクデータベース ID") },
                supportingText = { Text("共有済みのNotionタスクデータベースのIDを入力します") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    viewModel.fetchDatabaseProperties(
                        token = token.trim(),
                        dbId = databaseId.trim(),
                        onSuccess = {
                            isLoading = false
                            onConnectionVerified(token.trim(), databaseId.trim())
                        },
                        onFailure = {
                            isLoading = false
                            errorMessage = it
                        },
                    )
                },
                enabled = token.isNotBlank() && databaseId.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("接続を確認中")
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("接続を確認して次へ", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SetupStepChip(number: String, label: String, selected: Boolean) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            "$number  $label",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NotionSettingsScreen(
    viewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
) {
    val token by settingsViewModel.notionToken.collectAsState()
    val dbId by settingsViewModel.databaseId.collectAsState()
    var draftToken by remember(token) { mutableStateOf(token) }
    var draftDbId by remember(dbId) { mutableStateOf(dbId) }
    var isLoadingSchema by remember { mutableStateOf(false) }
    var isTokenVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isEditing by rememberSaveable { mutableStateOf(token.isBlank() || dbId.isBlank()) }

    SettingsSubPageScaffold(stringResource(R.string.settings_notion_title), navController) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!isEditing && token.isNotBlank() && dbId.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Notion は接続済みです", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("接続先のタスクデータベース: 設定済み", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { navController.navigate(Screen.SettingsMapping.route) }) { Text("プロパティを確認") }
                            TextButton(onClick = { isEditing = true }) { Text("接続先を変更") }
                        }
                    }
                }
                return@Column
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (token.isBlank()) "Notionへ接続します" else "接続先を変更します", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("1. IntegrationのトークンとタスクDBのIDを入力", style = MaterialTheme.typography.bodyMedium)
                    Text("2. 「データベース構成を取得」をタップ", style = MaterialTheme.typography.bodyMedium)
                    Text("3. 次の画面でプロパティを確認して保存", style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(
                value = draftToken,
                onValueChange = { draftToken = it; errorMessage = null },
                label = { Text(stringResource(R.string.settings_notion_token_label)) },
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                        Icon(
                            imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isTokenVisible) "トークンを隠す" else "トークンを表示する",
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = draftDbId,
                onValueChange = {
                    draftDbId = it
                    errorMessage = null
                },
                label = { Text(stringResource(R.string.settings_db_id_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            errorMessage?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Button(
                onClick = {
                    isLoadingSchema = true
                    viewModel.fetchDatabaseProperties(
                        draftToken,
                        draftDbId,
                        onSuccess = {
                            isLoadingSchema = false
                            settingsViewModel.updateNotionToken(draftToken)
                            settingsViewModel.updateDatabaseId(draftDbId)
                            settingsViewModel.markSetupIncomplete()
                            navController.navigate(Screen.SettingsMapping.route)
                        },
                        onFailure = {
                            isLoadingSchema = false
                            errorMessage = it
                        }
                    )
                },
                enabled = draftToken.isNotBlank() && draftDbId.isNotBlank() && !isLoadingSchema,
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
    ) -> Unit,
    isSetupFlow: Boolean = false,
    onSetupCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val sharedPrefs = remember { SecurityUtils.getSecurePreferences(context) }
    
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
    var isMetadataLoading by remember { mutableStateOf(false) }
    var metadataError by remember { mutableStateOf<String?>(null) }

    val loadMetadata = {
        val token = settingsViewModel.notionToken.value
        val dbId = settingsViewModel.databaseId.value
        if (token.isNotBlank() && dbId.isNotBlank()) {
            isMetadataLoading = true
            metadataError = null
            viewModel.fetchDatabaseProperties(
                token = token,
                dbId = dbId,
                onSuccess = {
                    metadata = it
                    isMetadataLoading = false
                },
                onFailure = {
                    isMetadataLoading = false
                    metadataError = "データベース構成を取得できませんでした。接続情報とNotion側の共有設定を確認して、再試行してください。"
                },
            )
        } else {
            metadataError = "接続情報が未設定です。Notion 接続設定を確認してください。"
        }
    }

    LaunchedEffect(Unit) { loadMetadata() }

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
        if (isSetupFlow) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "ステップ 2 / 3　Notionのプロパティを確認します。タスク管理と計画に必要な項目をすべて割り当てます。",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (isMetadataLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("データベース構成を取得中")
            }
        }
        if (metadataError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(metadataError!!, color = MaterialTheme.colorScheme.onErrorContainer)
                    OutlinedButton(onClick = loadMetadata) { Text("再試行") }
                }
            }
        }

        PropertyDropdown("名前 (タスクタイトル)", propTitle, metadata?.properties?.filter { it.value.title != null }?.keys?.toList() ?: emptyList()) { propTitle = it }

        PropertyDropdown("状態 (ステータス)", propStatus, metadata?.properties?.filter { (it.value.status != null) || (it.value.select != null) }?.keys?.toList() ?: emptyList()) { selected ->
            propStatus = selected
            propStatusType = if (metadata?.properties?.get(selected)?.status != null) "status" else "select"
            propStatusUnstarted = ""
            propStatusInProgress = ""
            propStatusCompleted = ""
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

        Text("タスク表示と計画に使うプロパティ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        val mappedStatusValues = listOf(propStatusUnstarted, propStatusInProgress, propStatusCompleted)
        val areStatusValuesValid = mappedStatusValues.all { it.isNotBlank() && it in statusOptions } &&
            mappedStatusValues.distinct().size == mappedStatusValues.size
        val isAllMapped = metadata != null && propTitle.isNotBlank() && propStatus.isNotBlank() &&
            propCategory.isNotBlank() && propScheduled.isNotBlank() && propDue.isNotBlank() && areStatusValuesValid

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
                if (isSetupFlow) {
                    onSetupCompleted()
                } else {
                    navController.popBackStack()
                }
            },
            enabled = isAllMapped,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (isSetupFlow) "設定を完了して初回同期へ" else stringResource(R.string.settings_save), fontWeight = FontWeight.Bold)
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
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "時間設定は保存後、次に開始するセッションから反映されます。実行中のタイマーは変更されません。",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
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
                
                OutlinedButton(onClick = {
                    try {
                        preview?.stop()
                        val u = if (uriStr.isNotBlank()) uriStr.toUri() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        preview = RingtoneManager.getRingtone(context, u).apply {
                            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                            play()
                        }
                    } catch (_: Exception) {}
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("試聴")
                }
                
                TextButton(onClick = { preview?.stop() }) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("停止")
                }
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
fun StatsManagementScreen(viewModel: TaskViewModel, settingsViewModel: SettingsViewModel, navController: NavController) {
    SettingsSubPageScaffold(stringResource(R.string.settings_stats_title), navController) {
        DataManagementSection(viewModel, settingsViewModel)
    }
}

@Composable
fun AboutScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController,
) {
    val devModeEnabled by settingsViewModel.devModeEnabled.collectAsState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val version = com.notiontasks.app.BuildConfig.VERSION_NAME

    SettingsSubPageScaffold(stringResource(R.string.settings_about_title), navController) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NotionTasker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_about_version, version), style = MaterialTheme.typography.bodyLarge)
                
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
            IconButton(
                onClick = { if (value > 1) onValueChange(value - 1) },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50)),
            ) { Icon(Icons.Default.Remove, contentDescription = "$label を1${unit}減らす") }
            OutlinedTextField(
                value = text,
                onValueChange = { v -> v.filter { it.isDigit() }.let { if (it.length <= 3) { text = it; it.toIntOrNull()?.let { n -> onValueChange(n.coerceIn(1, 999)) } } } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = color),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.width(72.dp).height(52.dp)
            )
            IconButton(
                onClick = { if (value < 999) onValueChange(value + 1) },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50)),
            ) { Icon(Icons.Default.Add, contentDescription = "$label を1${unit}増やす") }
            Text(unit, Modifier.width(20.dp))
        }
    }
}

@Composable
fun borderStroke() = androidx.compose.foundation.BorderStroke(
    1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)

@Composable
fun DataManagementSection(viewModel: TaskViewModel, settingsViewModel: SettingsViewModel) {
    val statsDuration by viewModel.statsStorageDuration.collectAsState()
    val pomodoroLogs by viewModel.pomodoroLogs.collectAsState()
    val tasksState by viewModel.tasksState.collectAsState()
    val keepType by settingsViewModel.completedTaskKeepType.collectAsState()
    val keepMonths by settingsViewModel.completedTaskKeepDateMonths.collectAsState()
    val keepCount by settingsViewModel.completedTaskKeepCount.collectAsState()

    var showDeletePeriodDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteTargetMonths by remember { mutableIntStateOf(0) }
    var showStatsRetentionConfirm by remember { mutableStateOf(false) }
    var requestedStatsDuration by remember { mutableIntStateOf(0) }
    var showFullSyncConfirm by remember { mutableStateOf(false) }
    var showCompletedRetentionConfirm by remember { mutableStateOf(false) }
    var requestedKeepType by remember { mutableStateOf(keepType) }
    var requestedKeepMonths by remember { mutableIntStateOf(keepMonths) }
    var requestedKeepCount by remember { mutableIntStateOf(keepCount) }

    val durationOptions = listOf(1 to "1ヶ月", 3 to "3ヶ月", 6 to "6ヶ月", 12 to "1年", 0 to "無制限")
    val cachedTaskCount = (tasksState as? com.notiontasks.app.ui.viewmodel.TasksUiState.Success)?.tasks?.size ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { Text("端末内タスク", style = MaterialTheme.typography.labelMedium); Text("${cachedTaskCount}件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                Column { Text("集中記録", style = MaterialTheme.typography.labelMedium); Text("${pomodoroLogs.size}件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            }
        }
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
                        onClick = {
                            requestedKeepType = "date"
                            requestedKeepMonths = keepMonths
                            requestedKeepCount = keepCount
                            showCompletedRetentionConfirm = true
                        },
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
                        onClick = {
                            requestedKeepType = "count"
                            requestedKeepMonths = keepMonths
                            requestedKeepCount = keepCount
                            showCompletedRetentionConfirm = true
                        },
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
                                    .clickable {
                                        requestedKeepType = "date"
                                        requestedKeepMonths = months
                                        requestedKeepCount = keepCount
                                        showCompletedRetentionConfirm = true
                                    },
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
                                    it.toIntOrNull()?.let { n ->
                                        requestedKeepType = "count"
                                        requestedKeepCount = n
                                    }
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_data_keep_count_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = { showCompletedRetentionConfirm = true },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("保持設定を適用") }
                }
                Text(
                    "この設定は次回の同期後、端末内の完了タスクに適用されます。Notion側のタスクは削除されません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                .clickable {
                                    requestedStatsDuration = months
                                    val isShortening = months > 0 && (statsDuration == 0 || months < statsDuration)
                                    if (isShortening) showStatsRetentionConfirm = true else viewModel.setStatsStorageDuration(months)
                                },
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
                onClick = { showFullSyncConfirm = true },
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

    if (showStatsRetentionConfirm) {
        val label = durationOptions.firstOrNull { it.first == requestedStatsDuration }?.second ?: "選択した期間"
        AlertDialog(
            onDismissRequest = { showStatsRetentionConfirm = false },
            title = { Text("集中記録の保持期間を変更") },
            text = { Text("保持期間を$label に短縮すると、それより前の端末内の集中記録を削除します。Notionのタスクは削除されず、この操作は取り消せません。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.setStatsStorageDurationAndCleanup(requestedStatsDuration)
                    showStatsRetentionConfirm = false
                }) { Text("削除して適用") }
            },
            dismissButton = { TextButton(onClick = { showStatsRetentionConfirm = false }) { Text("キャンセル") } },
        )
    }

    if (showCompletedRetentionConfirm) {
        val detail = if (requestedKeepType == "date") {
            "${durationOptions.firstOrNull { it.first == requestedKeepMonths }?.second ?: "指定期間"}"
        } else {
            "最新 ${requestedKeepCount.coerceAtLeast(1)} 件"
        }
        AlertDialog(
            onDismissRequest = { showCompletedRetentionConfirm = false },
            title = { Text("完了タスクの保持設定を変更") },
            text = { Text("端末内の完了タスクを$detail に制限します。削除は次回同期後に行われ、Notion側のタスクは削除されません。") },
            confirmButton = {
                Button(onClick = {
                    settingsViewModel.updateCompletedTaskKeepType(requestedKeepType)
                    settingsViewModel.updateCompletedTaskKeepDateMonths(requestedKeepMonths)
                    settingsViewModel.updateCompletedTaskKeepCount(requestedKeepCount.coerceAtLeast(1))
                    showCompletedRetentionConfirm = false
                }) { Text("適用") }
            },
            dismissButton = { TextButton(onClick = { showCompletedRetentionConfirm = false }) { Text("キャンセル") } },
        )
    }

    if (showFullSyncConfirm) {
        AlertDialog(
            onDismissRequest = { showFullSyncConfirm = false },
            title = { Text("全データを再同期") },
            text = { Text("Notionからタスクを全件取り直し、端末キャッシュを更新します。設定済みの完了タスク保持ルールも同期後に適用されます。Notion側のタスクは削除しません。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.syncWithNotion(incremental = false)
                    showFullSyncConfirm = false
                }) { Text("再同期する") }
            },
            dismissButton = { TextButton(onClick = { showFullSyncConfirm = false }) { Text("キャンセル") } },
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
    val initializedDates by viewModel.initializedDates.collectAsState()
    
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
    var pendingDeleteActivity by remember { mutableStateOf<com.notiontasks.app.data.model.LifeActivity?>(null) }
    var pendingScheduleUpdate by remember { mutableStateOf<com.notiontasks.app.data.model.LifeActivity?>(null) }

    val saveActivity: (com.notiontasks.app.data.model.LifeActivity) -> Unit = { activity ->
        if (editingActivity != null) {
            val updated = lifeActivities.map { if (it.id == editingActivity!!.id) activity else it }
            viewModel.saveLifeActivities(context, updated)
        } else {
            viewModel.addLifeActivity(context, activity)
        }
        showDialog = false
    }

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
                "プリセット",
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
            "名前・標準時間・色は、時間割で使うプリセットです。自動配置をオンにした項目だけ、指定時刻に予定へ配置されます。",
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
                                        modifier = Modifier.size(48.dp)
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
                                        modifier = Modifier.size(48.dp),
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
                                    onClick = { pendingDeleteActivity = act },
                                    modifier = Modifier.size(48.dp)
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
            val durationValue = actDuration.toIntOrNull()
            val isDurationValid = durationValue != null && durationValue in 1..600
            val startTotal = (defaultStartHour * 60) + defaultStartMin
            val endTotal = (defaultEndHour * 60) + defaultEndMin
            val isTimeRangeValid = !hasDefaultTime || endTotal > startTotal
            val hasAutoPlacementConflict = hasDefaultTime && isTimeRangeValid && lifeActivities.any { existing ->
                existing.id != editingActivity?.id &&
                    existing.defaultStartTime != null && existing.defaultEndTime != null &&
                    startTotal < existing.defaultEndTime && endTotal > existing.defaultStartTime
            }
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
                        if (!isDurationValid) {
                            Text("標準時間は1〜600分で入力してください", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }

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
                                                val newEnd = startTot + (durationValue ?: 30)
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
                            if (!isTimeRangeValid) {
                                Text("終了時間は開始時間より後にしてください", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text("自動配置ではこの開始・終了時刻を使います。標準時間はプリセット選択時の目安です。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (hasAutoPlacementConflict) {
                                Text("他の自動配置の時間帯と重なっています。時間割では予定が重なるため、時刻を確認してください。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (actName.isBlank()) return@Button
                            val duration = durationValue ?: return@Button
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

                            val changesExistingSchedule = editingActivity != null && (
                                editingActivity!!.defaultStartTime != newAct.defaultStartTime ||
                                    editingActivity!!.defaultEndTime != newAct.defaultEndTime ||
                                    editingActivity!!.name != newAct.name ||
                                    editingActivity!!.color != newAct.color
                                )
                            if (changesExistingSchedule && (newAct.defaultStartTime != null)) {
                                pendingScheduleUpdate = newAct
                            } else {
                                saveActivity(newAct)
                            }
                        },
                        enabled = actName.isNotBlank() && isDurationValid && isTimeRangeValid
                    ) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("キャンセル") } }
            )
        }

        pendingDeleteActivity?.let { activity ->
            AlertDialog(
                onDismissRequest = { pendingDeleteActivity = null },
                title = { Text("生活習慣を削除") },
                text = { Text("「${activity.name}」のプリセットを削除します。今日以降に自動配置された未開始の予定も削除されます。") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteLifeActivity(context, activity.id)
                            pendingDeleteActivity = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("削除") }
                },
                dismissButton = { TextButton(onClick = { pendingDeleteActivity = null }) { Text("キャンセル") } },
            )
        }

        pendingScheduleUpdate?.let { activity ->
            AlertDialog(
                onDismissRequest = { pendingScheduleUpdate = null },
                title = { Text("既存の時間割へ反映") },
                text = { Text("この変更は、今日の未経過分と初期化済みの未来 ${initializedDates.size} 日分の時間割に反映されます。手動で調整した同じ生活習慣の予定も更新されます。") },
                confirmButton = {
                    Button(onClick = {
                        saveActivity(activity)
                        pendingScheduleUpdate = null
                    }) { Text("反映して保存") }
                },
                dismissButton = { TextButton(onClick = { pendingScheduleUpdate = null }) { Text("キャンセル") } },
            )
        }
    }
}
