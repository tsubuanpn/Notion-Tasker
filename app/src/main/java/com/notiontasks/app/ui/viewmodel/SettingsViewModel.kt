package com.notiontasks.app.ui.viewmodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import com.notiontasks.app.ui.theme.AppThemePalettes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val sharedPrefs: SharedPreferences
) : ViewModel() {

    data class NotionConnectionDraft(val token: String, val databaseId: String)
    private var notionConnectionDraft: NotionConnectionDraft? = null

    init {
        // データソースはデータベースから自動検出するため、旧版の手入力値は移行時に破棄する。
        sharedPrefs.edit { remove("notion_data_source_id") }
    }

    private val legacySetupComplete = sharedPrefs.getString("notion_token", "").orEmpty().isNotBlank() &&
        sharedPrefs.getString("database_id", "").orEmpty().isNotBlank() &&
        sharedPrefs.getString("mapping_prop_title", "").orEmpty().isNotBlank() &&
        sharedPrefs.getString("mapping_prop_status", "").orEmpty().isNotBlank()

    private val _setupCompleted = MutableStateFlow(
        if (sharedPrefs.contains("setup_completed")) {
            sharedPrefs.getBoolean("setup_completed", false)
        } else {
            legacySetupComplete
        }
    )
    val setupCompleted: StateFlow<Boolean> = _setupCompleted.asStateFlow()

    // --- Notion Settings ---
    private val _notionToken = MutableStateFlow(sharedPrefs.getString("notion_token", "") ?: "")
    val notionToken: StateFlow<String> = _notionToken.asStateFlow()

    private val _databaseId = MutableStateFlow(sharedPrefs.getString("database_id", "") ?: "")
    val databaseId: StateFlow<String> = _databaseId.asStateFlow()

    private val _calendarTaskDateTarget = MutableStateFlow(
        CalendarTaskDateTarget.fromPreference(sharedPrefs.getString("calendar_task_date_target", null))
    )
    val calendarTaskDateTarget = _calendarTaskDateTarget.asStateFlow()

    fun updateCalendarTaskDateTarget(target: CalendarTaskDateTarget) {
        _calendarTaskDateTarget.value = target
        sharedPrefs.edit { putString("calendar_task_date_target", target.preferenceValue) }
    }

    // --- Notification Settings ---
    private val _morningNotifEnabled = MutableStateFlow(sharedPrefs.getBoolean("morning_notif_enabled", true))
    val morningNotifEnabled = _morningNotifEnabled.asStateFlow()

    private val _morningNotifTime = MutableStateFlow(sharedPrefs.getString("morning_notif_time", "08:00") ?: "08:00")
    val morningNotifTime = _morningNotifTime.asStateFlow()

    private val _eveningNotifEnabled = MutableStateFlow(sharedPrefs.getBoolean("evening_notif_enabled", true))
    val eveningNotifEnabled = _eveningNotifEnabled.asStateFlow()

    private val _eveningNotifTime = MutableStateFlow(sharedPrefs.getString("evening_notif_time", "20:00") ?: "20:00")
    val eveningNotifTime = _eveningNotifTime.asStateFlow()

    // --- Appearance Settings ---
    private val _themeMode = MutableStateFlow(sharedPrefs.getString("theme_mode", "system") ?: "system")
    val themeMode = _themeMode.asStateFlow()

    private val _themeColorName = MutableStateFlow(sharedPrefs.getString("theme_color_name", "blue") ?: "blue")
    val themeColorName = _themeColorName.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(sharedPrefs.getBoolean("dynamic_color_enabled", true))
    val dynamicColorEnabled = _dynamicColorEnabled.asStateFlow()

    // --- Data Management Settings ---
    private val _completedTaskKeepType = MutableStateFlow(sharedPrefs.getString("data_keep_type", "date") ?: "date")
    val completedTaskKeepType = _completedTaskKeepType.asStateFlow()

    private val _completedTaskKeepDateMonths = MutableStateFlow(sharedPrefs.getInt("data_keep_months", 0))
    val completedTaskKeepDateMonths = _completedTaskKeepDateMonths.asStateFlow()

    private val _completedTaskKeepCount = MutableStateFlow(sharedPrefs.getInt("data_keep_count", 1000))
    val completedTaskKeepCount = _completedTaskKeepCount.asStateFlow()

    // --- Developer Settings ---
    private val _devModeEnabled = MutableStateFlow(sharedPrefs.getBoolean("dev_mode_enabled", false))
    val devModeEnabled = _devModeEnabled.asStateFlow()

    private val _devCompleteButtonEnabled = MutableStateFlow(sharedPrefs.getBoolean("dev_complete_button_enabled", false))
    val devCompleteButtonEnabled = _devCompleteButtonEnabled.asStateFlow()

    // --- Actions ---

    fun updateNotionToken(token: String) {
        _notionToken.value = token
        sharedPrefs.edit { putString("notion_token", token) }
    }

    fun updateDatabaseId(dbId: String) {
        _databaseId.value = dbId
        sharedPrefs.edit { putString("database_id", dbId) }
    }

    /** Keeps a candidate connection out of persistent settings until its mapping is confirmed. */
    fun stageNotionConnection(token: String, databaseId: String) {
        notionConnectionDraft = NotionConnectionDraft(token.trim(), databaseId.trim())
    }

    fun connectionForMapping(): NotionConnectionDraft = notionConnectionDraft
        ?: NotionConnectionDraft(_notionToken.value, _databaseId.value)

    fun discardStagedNotionConnection() {
        notionConnectionDraft = null
    }

    /** Commits the verified connection and all required mappings in one SharedPreferences edit. */
    fun commitNotionConnection(
        token: String,
        databaseId: String,
        mappings: Map<String, String>,
    ) {
        val cleanToken = token.trim()
        val cleanDatabaseId = databaseId.trim()
        sharedPrefs.edit {
            putString("notion_token", cleanToken)
            putString("database_id", cleanDatabaseId)
            mappings.forEach { (key, value) -> putString(key, value) }
            putBoolean("setup_completed", true)
        }
        _notionToken.value = cleanToken
        _databaseId.value = cleanDatabaseId
        _setupCompleted.value = true
        notionConnectionDraft = null
    }

    fun markSetupCompleted() {
        _setupCompleted.value = true
        sharedPrefs.edit { putBoolean("setup_completed", true) }
    }

    /** 接続先を変更する間は、完了済みセットアップとして扱わない。 */
    fun markSetupIncomplete() {
        _setupCompleted.value = false
        sharedPrefs.edit { putBoolean("setup_completed", false) }
    }

    fun toggleMorningNotif(enabled: Boolean) {
        _morningNotifEnabled.value = enabled
        sharedPrefs.edit { putBoolean("morning_notif_enabled", enabled) }
    }

    fun updateMorningTime(time: String) {
        _morningNotifTime.value = time
        sharedPrefs.edit { putString("morning_notif_time", time) }
    }

    fun toggleEveningNotif(enabled: Boolean) {
        _eveningNotifEnabled.value = enabled
        sharedPrefs.edit { putBoolean("evening_notif_enabled", enabled) }
    }

    fun updateEveningTime(time: String) {
        _eveningNotifTime.value = time
        sharedPrefs.edit { putString("evening_notif_time", time) }
    }

    fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit { putString("theme_mode", mode) }
    }

    fun updateThemeColor(colorName: String) {
        _themeColorName.value = colorName
        sharedPrefs.edit { putString("theme_color_name", colorName) }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        _dynamicColorEnabled.value = enabled
        sharedPrefs.edit { putBoolean("dynamic_color_enabled", enabled) }
    }

    fun toggleDevMode(enabled: Boolean) {
        _devModeEnabled.value = enabled
        sharedPrefs.edit { putBoolean("dev_mode_enabled", enabled) }
    }

    fun toggleDevCompleteButton(enabled: Boolean) {
        _devCompleteButtonEnabled.value = enabled
        sharedPrefs.edit { putBoolean("dev_complete_button_enabled", enabled) }
    }

    // --- Data Management Actions ---
    fun updateCompletedTaskKeepType(type: String) {
        _completedTaskKeepType.value = type
        sharedPrefs.edit { putString("data_keep_type", type) }
    }

    fun updateCompletedTaskKeepDateMonths(months: Int) {
        _completedTaskKeepDateMonths.value = months
        sharedPrefs.edit { putInt("data_keep_months", months) }
    }

    fun updateCompletedTaskKeepCount(count: Int) {
        _completedTaskKeepCount.value = count
        sharedPrefs.edit { putInt("data_keep_count", count) }
    }
}
