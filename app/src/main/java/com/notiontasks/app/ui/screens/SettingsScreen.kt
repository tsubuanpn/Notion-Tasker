package com.notiontasks.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.core.net.toUri
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.ui.viewmodel.TaskViewModel

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
            morningTime = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
        }, h, m, true).show()
    }

    val showEveningTimePicker = {
        val parts = eveningTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
            eveningTime = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
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
                        title = "ポモドーロタイマー設定",
                        subtitle = "集中・休憩セッションの時間のカスタマイズ",
                        icon = Icons.Default.Timer,
                        onClick = { currentSubPage = "pomodoro_settings" }
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
                        "alarm" -> "アラーム音設定"
                        "pomodoro_settings" -> "ポモドーロタイマー設定"
                        "info" -> "通知 / プロパティについて"
                        else -> ""
                    }
                    val subtitle = when (currentSubPage) {
                        "notion" -> "アカウントのシークレットトークンとデータベース連携"
                        "mapping" -> "Notionデータベース側のカラム名の紐付け設定"
                        "notifications" -> "Android端末でのバックグラウンドタスク動作"
                        "theme" -> "アプリを美しく表示する外観モードの変更"
                        "alarm" -> "ポモドーロ完了時に鳴らす音を選択"
                        "pomodoro_settings" -> "集中・休憩セッションの時間のカスタマイズ"
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
                    var previewRingtone by remember { mutableStateOf<Ringtone?>(null) }

                    // Launcher for ringtone picker
                    val ringtonePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            @Suppress("DEPRECATION")
                            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                            if (picked != null) {
                                alarmUriString = picked.toString()
                                prefsAlarm.edit { putString("alarm_uri", alarmUriString) }
                            } else {
                                // cleared selection -> remove
                                alarmUriString = ""
                                prefsAlarm.edit { remove("alarm_uri") }
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
                                val uri = if (alarmUriString.isNotBlank()) alarmUriString.toUri() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "未設定"
                            } catch (_: Exception) {
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
                                    if (alarmUriString.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, alarmUriString.toUri())
                                }
                                ringtonePickerLauncher.launch(intent)
                            }, shape = RoundedCornerShape(12.dp)) {
                                Text("音を選択")
                            }

                            Button(onClick = {
                                // Play preview (use selected or default)
                                try {
                                    previewRingtone?.stop()
                                    val uri = if (alarmUriString.isNotBlank()) alarmUriString.toUri() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                    previewRingtone = RingtoneManager.getRingtone(context, uri)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        val audioAttributes = AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                            .build()
                                        previewRingtone?.audioAttributes = audioAttributes
                                    }
                                    previewRingtone?.play()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "再生に失敗しました", Toast.LENGTH_SHORT).show()
                                }
                            }, shape = RoundedCornerShape(12.dp)) {
                                Text("プレビュー再生")
                            }

                            Button(onClick = {
                                previewRingtone?.stop()
                                previewRingtone = null
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
                "pomodoro_settings" -> {
                    val prefsPomodoro = remember { context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE) }
                    var workMin by remember { mutableIntStateOf(prefsPomodoro.getInt("work_duration_min", 25)) }
                    var shortBreakMin by remember { mutableIntStateOf(prefsPomodoro.getInt("short_break_duration_min", 5)) }
                    var longBreakMin by remember { mutableIntStateOf(prefsPomodoro.getInt("long_break_duration_min", 15)) }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "ポモドーロタイマーの時間を設定します。それぞれの時間の長さを変更できます。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        DurationSettingRow(
                            label = "集中時間",
                            value = workMin,
                            onValueChange = { workMin = it.coerceIn(1, 999) },
                            unit = "分",
                            color = Color(0xFFEF5350)
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        DurationSettingRow(
                            label = "短い休憩",
                            value = shortBreakMin,
                            onValueChange = { shortBreakMin = it.coerceIn(1, 999) },
                            unit = "分",
                            color = Color(0xFF2E7D32)
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        DurationSettingRow(
                            label = "長い休憩",
                            value = longBreakMin,
                            onValueChange = { longBreakMin = it.coerceIn(1, 999) },
                            unit = "分",
                            color = Color(0xFF1976D2)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    workMin = 25
                                    shortBreakMin = 5
                                    longBreakMin = 15
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("デフォルトに戻す", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    prefsPomodoro.edit {
                                        putInt("work_duration_min", workMin)
                                        putInt("short_break_duration_min", shortBreakMin)
                                        putInt("long_break_duration_min", longBreakMin)
                                    }
                                    
                                    Toast.makeText(context, "ポモドーロ設定を保存しました", Toast.LENGTH_SHORT).show()
                                    currentSubPage = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("保存して戻る", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "mapping" -> {
                    // Title property field
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val titleProps = (loadedMetadata?.properties?.filter { it.value.title != null }?.keys?.toList() ?: emptyList())
                            .ifEmpty { if (propTitle.isNotBlank()) listOf(propTitle) else emptyList() }
                        OutlinedTextField(
                            value = propTitle.ifBlank { "未選択" },
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
                                value = propStatus.ifBlank { "未選択" },
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
                            value = propCategory.ifBlank { "未選択" },
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
                            value = propScheduled.ifBlank { "未選択" },
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
                            value = propDue.ifBlank { "未選択" },
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

@Composable
fun DurationSettingRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    unit: String,
    color: Color
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { if (value > 1) onValueChange(value - 1) },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() }
                    if (filtered.length <= 3) {
                        textValue = filtered
                        val parsed = filtered.toIntOrNull()
                        if (parsed != null) {
                            val validated = parsed.coerceIn(1, 999)
                            onValueChange(validated)
                        } else if (filtered.isEmpty()) {
                            onValueChange(1)
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = color
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = color,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(72.dp)
                    .height(52.dp)
            )

            IconButton(
                onClick = { if (value < 999) onValueChange(value + 1) },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
        }
    }
}
