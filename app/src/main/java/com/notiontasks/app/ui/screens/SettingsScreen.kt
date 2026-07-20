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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.remote.dto.NotionOptionInfo
import com.notiontasks.app.ui.viewmodel.TaskViewModel

/**
 * 設定画面のサブページを定義するSealed Class
 */
sealed class SettingsSubPage(val title: String, val subtitle: String) {
    data object Main : SettingsSubPage("設定", "")
    data object Notion : SettingsSubPage("Notion 接続設定", "アカウントのシークレットトークンとデータベース連携")
    data object Mapping : SettingsSubPage("プロパティマッピング", "Notionデータベース側のカラム名の紐付け設定")
    data object Notifications : SettingsSubPage("通知スケジュール設定", "Android端末でのバックグラウンドタスク動作")
    data object Theme : SettingsSubPage("外観テーマ設定", "アプリを美しく表示する外観モードの変更")
    data object Alarm : SettingsSubPage("アラーム音設定", "ポモドーロ完了時に鳴らす音を選択")
    data object Pomodoro : SettingsSubPage("ポモドーロタイマー設定", "集中・休憩セッションの時間のカスタマイズ")
    data object LifeActivitySettings : SettingsSubPage("生活習慣設定", "時間割に自動表示するデフォルト時刻やプリセットの編集")
    data object Tabs : SettingsSubPage("表示タブ設定", "ナビゲーションバーに表示するタブの管理")
    data object Info : SettingsSubPage("通知 / プロパティについて", "アプリ仕様と通知機能に関する補足説明")
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
    initialCategoryTabEnabled: Boolean,
    initialCalendarTabEnabled: Boolean,
    initialScheduleTabEnabled: Boolean,
    initialPomodoroTabEnabled: Boolean,
    initialAchievementsTabEnabled: Boolean,
    onTabToggle: (String, Boolean) -> Unit,
    initialCategoryOptions: List<NotionOptionInfo>,
    initialStatusOptions: List<NotionOptionInfo>,
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
        mCatOptions: List<NotionOptionInfo>,
        mStatOptions: List<NotionOptionInfo>,
    ) -> Unit
) {
    // --- 内部状態 ---
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

    var currentSubPage by remember { mutableStateOf<SettingsSubPage>(SettingsSubPage.Main) }
    var isLoadingSchema by remember { mutableStateOf(value = false) }
    var loadedMetadata by remember { mutableStateOf<NotionDatabaseResponse?>(null) }

    val context = LocalContext.current

    // --- ロジック ---
    LaunchedEffect(Unit) {
        if (initialToken.isNotBlank() && initialDbId.isNotBlank()) {
            isLoadingSchema = true
            viewModel.fetchDatabaseProperties(
                token = initialToken,
                dbId = initialDbId,
                onSuccess = { meta ->
                    loadedMetadata = meta
                    isLoadingSchema = false
                }
            ) { isLoadingSchema = false }
        }
    }

    val handleSave: () -> Unit = {
        if (listOf(propTitle, propStatus, propCategory, propScheduled, propDue).any { (it.isBlank() || it == "未選択") }) {
            Toast.makeText(context, "未選択のマッピング項目があります。すべてのプロパティを選択してください。", Toast.LENGTH_LONG).show()
        } else {
            // 動的なオプションの同期
            val chosenCatProp = loadedMetadata?.properties?.get(propCategory)
            val catOptions = chosenCatProp?.select?.options?.filter { it.name.trim().isNotBlank() }
                ?.ifEmpty { initialCategoryOptions }
                ?: initialCategoryOptions

            val chosenStatProp = loadedMetadata?.properties?.get(propStatus)
            val statOptions = if (propStatusType == "status") {
                chosenStatProp?.status?.options?.filter { it.name.trim().isNotBlank() }
                    ?.ifEmpty { initialStatusOptions }
                    ?: initialStatusOptions
            } else {
                chosenStatProp?.select?.options?.filter { it.name.trim().isNotBlank() }
                    ?.ifEmpty { initialStatusOptions }
                    ?: initialStatusOptions
            }

            onSave(
                token, dbId, morningTime, eveningTime, morningEnabled, eveningEnabled, themeMode,
                propTitle, propStatus, propStatusType, propCategory, propScheduled, propDue,
                catOptions, statOptions
            )
            Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
            currentSubPage = SettingsSubPage.Main
        }
    }

    // --- UI レイアウト ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (currentSubPage == SettingsSubPage.Main) {
            MainSettingsMenu(onNavigate = { currentSubPage = it })
        } else {
            SettingsSubHeader(
                page = currentSubPage,
                onBack = { currentSubPage = SettingsSubPage.Main }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            when (currentSubPage) {
                is SettingsSubPage.Notion -> NotionSettingsSection(
                    token = token,
                    onTokenChange = { token = it },
                    dbId = dbId,
                    onDbIdChange = { dbId = it },
                    isLoading = isLoadingSchema,
                    onFetch = {
                        isLoadingSchema = true
                        viewModel.fetchDatabaseProperties(
                            token,
                            dbId,
                            onSuccess = { meta ->
                                loadedMetadata = meta
                                isLoadingSchema = false
                                Toast.makeText(context, "DB構造のロードに成功しました！", Toast.LENGTH_SHORT).show()
                                
                                // ViewModelのロジックを使用して自動検知
                                val detected = viewModel.autoDetectMapping(meta)
                                detected["title"]?.let { propTitle = it }
                                detected["status"]?.let { propStatus = it }
                                detected["statusType"]?.let { propStatusType = it }
                                detected["category"]?.let { propCategory = it }
                                detected["scheduled"]?.let { propScheduled = it }
                                detected["due"]?.let { propDue = it }
                            }
                        ) { err ->
                            isLoadingSchema = false
                            Toast.makeText(context, "取得エラー: $err", Toast.LENGTH_LONG).show()
                        }
                    },
                    onSave = handleSave
                )
                is SettingsSubPage.Mapping -> MappingSettingsSection(
                    metadata = loadedMetadata,
                    propTitle = propTitle, onTitleChange = { propTitle = it },
                    propStatus = propStatus, onStatusChange = { propStatus = it },
                    propStatusType = propStatusType, onStatusTypeChange = { propStatusType = it },
                    propCategory = propCategory, onCategoryChange = { propCategory = it },
                    propScheduled = propScheduled, onScheduledChange = { propScheduled = it },
                    propDue = propDue, onDueChange = { propDue = it },
                    onSave = handleSave
                )
                is SettingsSubPage.Notifications -> NotificationsSettingsSection(
                    morningEnabled = morningEnabled, onMorningToggle = { morningEnabled = it },
                    morningTime = morningTime, onMorningTimeChange = { morningTime = it },
                    eveningEnabled = eveningEnabled, onEveningToggle = { eveningEnabled = it },
                    eveningTime = eveningTime, onEveningTimeChange = { eveningTime = it },
                    onSave = handleSave
                )
                is SettingsSubPage.Theme -> ThemeSettingsSection(
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it },
                    onSave = handleSave
                )
                is SettingsSubPage.Alarm -> AlarmSettingsSection(onBack = { currentSubPage = SettingsSubPage.Main })
                is SettingsSubPage.Pomodoro -> PomodoroSettingsSection(onBack = { currentSubPage = SettingsSubPage.Main })
                is SettingsSubPage.LifeActivitySettings -> LifeActivitySettingsSection(
                    viewModel = viewModel
                )
                is SettingsSubPage.Tabs -> TabsSettingsSection(
                    catEnabled = initialCategoryTabEnabled,
                    calEnabled = initialCalendarTabEnabled,
                    schEnabled = initialScheduleTabEnabled,
                    pomEnabled = initialPomodoroTabEnabled,
                    achEnabled = initialAchievementsTabEnabled,
                    onTabToggle = onTabToggle,
                    onBack = { currentSubPage = SettingsSubPage.Main }
                )
                is SettingsSubPage.Info -> InfoSection(onBack = { currentSubPage = SettingsSubPage.Main })
                else -> {}
            }
        }
    }
}

// --- 分割されたコンポーネント ---

@Composable
fun MainSettingsMenu(onNavigate: (SettingsSubPage) -> Unit) {
    Text("設定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    SettingsGroup("アカウント・連携") {
        SettingsMenuItem("Notion 接続設定", "APIトークンとデータベースIDを連携します", Icons.Default.Cloud) { onNavigate(SettingsSubPage.Notion) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SettingsMenuItem("プロパティマッピング", "Notion側のカラム名と同期属性を定義します", Icons.Default.Layers) { onNavigate(SettingsSubPage.Mapping) }
    }

    SettingsGroup("アプリ設定") {
        SettingsMenuItem("通知スケジュール設定", "今日期限タスクを知らせる朝・夕の通知タイマー", Icons.Default.Notifications) { onNavigate(SettingsSubPage.Notifications) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SettingsMenuItem("生活習慣設定", "時間割に自動表示するデフォルト時刻やプリセットの編集", Icons.Default.Favorite) { onNavigate(SettingsSubPage.LifeActivitySettings) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SettingsMenuItem("アラーム音設定", "ポモドーロ完了時に鳴らす音を選択", Icons.Default.Notifications) { onNavigate(SettingsSubPage.Alarm) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SettingsMenuItem("ポモドーロタイマー設定", "集中・休憩セッションの時間のカスタマイズ", Icons.Default.Timer) { onNavigate(SettingsSubPage.Pomodoro) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SettingsMenuItem("外観テーマ設定", "ダークモードやライトモードの切り替え設定", Icons.Default.WbSunny) { onNavigate(SettingsSubPage.Theme) }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SettingsMenuItem("表示タブ設定", "ナビゲーションバーに表示するタブの管理", Icons.Default.Menu) { onNavigate(SettingsSubPage.Tabs) }
    }

    SettingsGroup("その他") {
        SettingsMenuItem("通知 / プロパティ等について", "通知の仕組みやプロパティの自動マッピング機能の説明", Icons.Default.Info) { onNavigate(SettingsSubPage.Info) }
    }
}

@Composable
fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        content = content
    )
}

@Composable
fun SettingsSubHeader(page: SettingsSubPage, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "戻る")
        }
        Column {
            Text(page.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NotionSettingsSection(
    token: String, onTokenChange: (String) -> Unit,
    dbId: String, onDbIdChange: (String) -> Unit,
    isLoading: Boolean, onFetch: () -> Unit, onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(value = token, onValueChange = onTokenChange, label = { Text("Notion Integration Token") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dbId, onValueChange = onDbIdChange, label = { Text("Database ID") }, modifier = Modifier.fillMaxWidth())
        
        Button(onClick = onFetch, enabled = token.isNotBlank() && dbId.isNotBlank() && !isLoading, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("取得中...")
            } else {
                Text("データベース構造を自動取得", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("設定を保存して戻る", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun MappingSettingsSection(
    metadata: NotionDatabaseResponse?,
    propTitle: String, onTitleChange: (String) -> Unit,
    propStatus: String, onStatusChange: (String) -> Unit,
    propStatusType: String, onStatusTypeChange: (String) -> Unit,
    propCategory: String, onCategoryChange: (String) -> Unit,
    propScheduled: String, onScheduledChange: (String) -> Unit,
    propDue: String, onDueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PropertyDropdown("名前 (タスクタイトル)", propTitle, metadata?.properties?.filter { it.value.title != null }?.keys?.toList() ?: emptyList(), onTitleChange)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1.8f)) {
                PropertyDropdown("状態 (ステータス)", propStatus, metadata?.properties?.filter { it.value.status != null || it.value.select != null }?.keys?.toList() ?: emptyList(), onStatusChange)
            }
            Box(Modifier.weight(1.2f)) {
                TypeDropdown("タイプ", propStatusType, onStatusTypeChange)
            }
        }

        PropertyDropdown("種類 (カテゴリ)", propCategory, metadata?.properties?.filter { it.value.select != null }?.keys?.toList() ?: emptyList(), onCategoryChange)
        PropertyDropdown("予定日", propScheduled, metadata?.properties?.filter { it.value.date != null }?.keys?.toList() ?: emptyList(), onScheduledChange)
        PropertyDropdown("締め切り", propDue, metadata?.properties?.filter { it.value.date != null }?.keys?.toList() ?: emptyList(), onDueChange)

        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("マッピング設定を保存", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun PropertyDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val displayOptions = if (selected.isNotBlank() && selected !in options) listOf(selected) + options else options
    
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.ifBlank { "未選択" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            shape = RoundedCornerShape(12.dp)
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
            shape = RoundedCornerShape(12.dp)
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Status型") }, onClick = { onSelect("status"); expanded = false })
            DropdownMenuItem(text = { Text("Select型") }, onClick = { onSelect("select"); expanded = false })
        }
    }
}

@Composable
fun NotificationsSettingsSection(
    morningEnabled: Boolean, onMorningToggle: (Boolean) -> Unit,
    morningTime: String, onMorningTimeChange: (String) -> Unit,
    eveningEnabled: Boolean, onEveningToggle: (Boolean) -> Unit,
    eveningTime: String, onEveningTimeChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val showPicker = { currentTime: String, onUpdate: (String) -> Unit ->
        val parts = currentTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, hour, min ->
            onUpdate(String.format(java.util.Locale.US, "%02d:%02d", hour, min))
        }, h, m, true).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NotificationCard("朝の通知", "当日の予定を確認", Icons.Default.WbSunny, morningEnabled, onMorningToggle, morningTime) { showPicker(morningTime, onMorningTimeChange) }
        NotificationCard("夜の通知", "未完了タスクを確認", Icons.Default.NightsStay, eveningEnabled, onEveningToggle, eveningTime) { showPicker(eveningTime, onEveningTimeChange) }
        
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("通知設定を保存", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun NotificationCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onToggle: (Boolean) -> Unit, time: String, onTimeClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                OutlinedButton(onClick = onTimeClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("通知時間: $time", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ThemeSettingsSection(themeMode: String, onThemeChange: (String) -> Unit, onSave: () -> Unit) {
    val modes = listOf(Triple("system", "システム設定", Icons.Default.Layers), Triple("light", "ライトモード", Icons.Default.WbSunny), Triple("dark", "ダークモード", Icons.Default.NightsStay))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        modes.forEach { (key, label, icon) ->
            val selected = themeMode == key
            Row(
                Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onThemeChange(key) }.padding(16.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
                RadioButton(selected = selected, onClick = { onThemeChange(key) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("テーマ設定を保存", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun AlarmSettingsSection(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ポモドーロ完了時に鳴らす音を選択します", style = MaterialTheme.typography.bodyMedium)
        val title = remember(uriStr) { 
            try { 
                val u = if (uriStr.isNotBlank()) uriStr.toUri() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                RingtoneManager.getRingtone(context, u)?.getTitle(context) ?: "未設定"
            } catch(_: Exception) { "未設定" }
        }
        Text("現在の選択: $title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    if (uriStr.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uriStr.toUri())
                }
                launcher.launch(intent)
            }) { Text("音を選択") }
            Button(onClick = {
                try {
                    preview?.stop()
                    val u = if (uriStr.isNotBlank()) uriStr.toUri() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    preview = RingtoneManager.getRingtone(context, u).apply {
                        audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                        play()
                    }
                } catch(_: Exception) {}
            }) { Text("再生") }
            Button(onClick = { preview?.stop() }) { Text("停止") }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("戻る") }
    }
}

@Composable
fun PomodoroSettingsSection(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
    var work by remember { mutableIntStateOf(prefs.getInt("work_duration_min", 25)) }
    var sBreak by remember { mutableIntStateOf(prefs.getInt("short_break_duration_min", 5)) }
    var lBreak by remember { mutableIntStateOf(prefs.getInt("long_break_duration_min", 15)) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DurationSettingRow("集中時間", work, { work = it }, "分", Color(0xFFEF5350))
        DurationSettingRow("短い休憩", sBreak, { sBreak = it }, "分", Color(0xFF2E7D32))
        DurationSettingRow("長い休憩", lBreak, { lBreak = it }, "分", Color(0xFF1976D2))
        
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { work = 25; sBreak = 5; lBreak = 15 }, Modifier.weight(1f)) { Text("デフォルト") }
            Button(onClick = {
                prefs.edit { putInt("work_duration_min", work); putInt("short_break_duration_min", sBreak); putInt("long_break_duration_min", lBreak) }
                onBack()
            }, Modifier.weight(1f)) { Text("保存して戻る") }
        }
    }
}

@Composable
fun TabsSettingsSection(catEnabled: Boolean, calEnabled: Boolean, schEnabled: Boolean, pomEnabled: Boolean, achEnabled: Boolean, onTabToggle: (String, Boolean) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TabSettingRow("カテゴリ別", "分類してタスクを表示", Icons.AutoMirrored.Filled.List, catEnabled) { onTabToggle("category", it) }
        TabSettingRow("カレンダー", "日付ごとに確認", Icons.Default.DateRange, calEnabled) { onTabToggle("calendar", it) }
        TabSettingRow("時間割", "1日のタイムブロッキング", Icons.Default.Schedule, schEnabled) { onTabToggle("schedule", it) }
        TabSettingRow("ポモドーロ", "集中タイマー", Icons.Default.Timer, pomEnabled) { onTabToggle("pomodoro", it) }
        TabSettingRow("実績", "完了統計の表示", Icons.Default.Star, achEnabled) { onTabToggle("achievements", it) }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("戻る") }
    }
}

@Composable
fun InfoSection(onBack: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))) {
        Column(Modifier.padding(16.dp)) {
            Text("通知 / プロパティ等について", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("・朝夕の通知は「予定日」が今日のタスクを知らせます。\n・自動取得ボタンはNotionの構造をアプリに同期します。", style = MaterialTheme.typography.bodySmall)
        }
    }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("戻る") }
}

@Composable
fun SettingsMenuItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeActivitySettingsSection(
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val lifeActivities by viewModel.lifeActivities.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<com.notiontasks.app.data.model.LifeActivity?>(null) }
    
    var actName by remember { mutableStateOf("") }
    var actDuration by remember { mutableStateOf("30") }
    var actColor by remember { mutableStateOf("#4CAF50") }
    
    var hasDefaultTime by remember { mutableStateOf(false) }
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
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                    )
                } else {
                    lifeActivities.forEach { act ->
                        val colorParsed = try {
                            Color(android.graphics.Color.parseColor(act.color))
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
                                    hasDefaultTime = act.defaultStartTime != null && act.defaultEndTime != null
                                    
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
                                        if (act.defaultStartTime != null && act.defaultEndTime != null) {
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
                            IconButton(
                                onClick = { viewModel.deleteLifeActivity(act.id) },
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

        Spacer(modifier = Modifier.height(16.dp))

        // 追加/編集用のダイアログ
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

                        // Color options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("カラー:")
                            val colors = listOf("#EF5350", "#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#00BCD4", "#E91E63", "#78909C")
                            colors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(android.graphics.Color.parseColor(c)), RoundedCornerShape(100.dp))
                                        .border(
                                            width = if (actColor == c) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = RoundedCornerShape(100.dp)
                                        )
                                        .clickable { actColor = c }
                                )
                            }
                        }

                        // Switch for Auto scheduling
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
                            // Start Time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("開始時間:", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                                
                                Button(
                                    onClick = {
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                defaultStartHour = hour
                                                defaultStartMin = minute
                                                // 終了時間が開始時間以前にならないよう調整
                                                val startTot = defaultStartHour * 60 + defaultStartMin
                                                val endTot = defaultEndHour * 60 + defaultEndMin
                                                if (endTot <= startTot) {
                                                    val newEnd = startTot + 60
                                                    defaultEndHour = (newEnd / 60) % 24
                                                    defaultEndMin = newEnd % 60
                                                }
                                            },
                                            defaultStartHour,
                                            defaultStartMin,
                                            true
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(String.format(java.util.Locale.US, "%02d:%02d", defaultStartHour, defaultStartMin))
                                }
                            }

                            // End Time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("終了時間:", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                                
                                Button(
                                    onClick = {
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                defaultEndHour = hour
                                                defaultEndMin = minute
                                            },
                                            defaultEndHour,
                                            defaultEndMin,
                                            true
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
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
                            
                            val startTot = if (hasDefaultTime) defaultStartHour * 60 + defaultStartMin else null
                            val endTot = if (hasDefaultTime) defaultEndHour * 60 + defaultEndMin else null

                            val newAct = com.notiontasks.app.data.model.LifeActivity(
                                id = editingActivity?.id ?: ("la_" + java.util.UUID.randomUUID().toString().take(6)),
                                name = actName,
                                durationMinutes = duration,
                                color = actColor,
                                defaultStartTime = startTot,
                                defaultEndTime = endTot
                            )

                            if (editingActivity != null) {
                                val updated = lifeActivities.map {
                                    if (it.id == editingActivity!!.id) newAct else it
                                }
                                viewModel.saveLifeActivities(updated)
                            } else {
                                viewModel.addLifeActivity(newAct)
                            }

                            showDialog = false
                        },
                        enabled = actName.isNotBlank()
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }
    }
}
