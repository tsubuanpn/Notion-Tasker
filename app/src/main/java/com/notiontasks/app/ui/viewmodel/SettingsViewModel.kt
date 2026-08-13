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

    // --- Notion Settings ---
    private val _notionToken = MutableStateFlow(sharedPrefs.getString("notion_token", "") ?: "")
    val notionToken: StateFlow<String> = _notionToken.asStateFlow()

    private val _databaseId = MutableStateFlow(sharedPrefs.getString("database_id", "") ?: "")
    val databaseId: StateFlow<String> = _databaseId.asStateFlow()

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

    // --- Tab Settings ---
    private val _categoryTabEnabled = MutableStateFlow(sharedPrefs.getBoolean("tab_category_enabled", true))
    val categoryTabEnabled = _categoryTabEnabled.asStateFlow()

    private val _calendarTabEnabled = MutableStateFlow(sharedPrefs.getBoolean("tab_calendar_enabled", true))
    val calendarTabEnabled = _calendarTabEnabled.asStateFlow()

    private val _scheduleTabEnabled = MutableStateFlow(sharedPrefs.getBoolean("tab_schedule_enabled", true))
    val scheduleTabEnabled = _scheduleTabEnabled.asStateFlow()

    private val _pomodoroTabEnabled = MutableStateFlow(sharedPrefs.getBoolean("tab_pomodoro_enabled", true))
    val pomodoroTabEnabled = _pomodoroTabEnabled.asStateFlow()

    private val _achievementsTabEnabled = MutableStateFlow(sharedPrefs.getBoolean("tab_achievements_enabled", true))
    val achievementsTabEnabled = _achievementsTabEnabled.asStateFlow()

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

    fun toggleTab(tabKey: String, isEnabled: Boolean) {
        sharedPrefs.edit { putBoolean("tab_${tabKey}_enabled", isEnabled) }
        when (tabKey) {
            "category" -> _categoryTabEnabled.value = isEnabled
            "calendar" -> _calendarTabEnabled.value = isEnabled
            "schedule" -> _scheduleTabEnabled.value = isEnabled
            "pomodoro" -> _pomodoroTabEnabled.value = isEnabled
            "achievements" -> _achievementsTabEnabled.value = isEnabled
        }
    }

    fun toggleDevMode(enabled: Boolean) {
        _devModeEnabled.value = enabled
        sharedPrefs.edit { putBoolean("dev_mode_enabled", enabled) }
    }

    fun toggleDevCompleteButton(enabled: Boolean) {
        _devCompleteButtonEnabled.value = enabled
        sharedPrefs.edit { putBoolean("dev_complete_button_enabled", enabled) }
    }
}
