package com.notiontasks.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "今日", Icons.Default.Home)
    object Category : Screen("tasks", "タスク", Icons.AutoMirrored.Filled.List)
    object Plan : Screen("plan", "計画", Icons.Default.DateRange)
    object Schedule : Screen("schedule", "時間割", Icons.Default.Schedule)
    object Pomodoro : Screen("pomodoro", "集中", Icons.Default.Timer)
    object Achievements : Screen("achievements", "振り返り", Icons.Default.EmojiEvents)
    object Settings : Screen("settings", "設定", Icons.Default.Settings)
    object Setup : Screen("setup", "セットアップ", Icons.Default.Settings)

    // Settings Sub-pages
    object SettingsNotion : Screen("settings/notion", "Notion 接続設定", Icons.Default.Settings)
    object SettingsMapping : Screen("settings/mapping", "プロパティマッピング", Icons.Default.Settings)
    object SettingsNotifications : Screen("settings/notifications", "通知スケジュール設定", Icons.Default.Settings)
    object SettingsTheme : Screen("settings/theme", "外観テーマ設定", Icons.Default.Settings)
    object SettingsPomodoro : Screen("settings/pomodoro", "ポモドーロタイマー設定", Icons.Default.Settings)
    object SettingsLifeActivity : Screen("settings/life_activity", "生活習慣設定", Icons.Default.Settings)
    object SettingsStats : Screen("settings/stats", "統計データ管理", Icons.Default.Settings)
    object SettingsAbout : Screen("settings/about", "アプリの概要", Icons.Default.Settings)
    object SettingsDeveloper : Screen("settings/developer", "開発者設定", Icons.Default.Settings)
}
