package com.notiontasks.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.notiontasks.app.R
import com.notiontasks.app.ui.navigation.Screen

/** Navigation-only portion of the settings home; connection status stays with [SettingsScreen]. */
@Composable
internal fun SettingsNavigationSections(
    navController: NavController,
    developerOptionsVisible: Boolean,
) {
    SettingsGroup("日常の設定") {
        SettingsMenuItem("通知", "リマインダーとお知らせを設定します", Icons.Default.Notifications) {
            navController.navigate(Screen.SettingsNotifications.route)
        }
        SettingsMenuItem("集中タイマー", "ポモドーロ・休憩・アラームを設定します", Icons.Default.Timer) {
            navController.navigate(Screen.SettingsPomodoro.route)
        }
        SettingsMenuItem("外観", "テーマと表示をカスタマイズします", Icons.Default.Palette) {
            navController.navigate(Screen.SettingsTheme.route)
        }
    }
    SettingsGroup("Notion と同期") {
        SettingsMenuItem(stringResource(R.string.settings_notion_title), stringResource(R.string.settings_notion_subtitle), Icons.Default.Cloud) {
            navController.navigate(Screen.SettingsNotion.route)
        }
        SettingsMenuItem(stringResource(R.string.settings_mapping_title), stringResource(R.string.settings_mapping_subtitle), Icons.Default.Layers) {
            navController.navigate(Screen.SettingsMapping.route)
        }
    }
    SettingsGroup("アプリ管理") {
        SettingsMenuItem(stringResource(R.string.settings_life_activity_title), stringResource(R.string.settings_life_activity_subtitle), Icons.Default.Favorite) {
            navController.navigate(Screen.SettingsLifeActivity.route)
        }
        SettingsMenuItem("データ管理", stringResource(R.string.settings_stats_subtitle), Icons.Default.BarChart) {
            navController.navigate(Screen.SettingsStats.route)
        }
    }
    SettingsGroup("高度な設定") {
        if (developerOptionsVisible) {
            SettingsMenuItem(stringResource(R.string.settings_developer_title), stringResource(R.string.settings_developer_subtitle), Icons.Default.Code) {
                navController.navigate(Screen.SettingsDeveloper.route)
            }
        }
        SettingsMenuItem(stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_subtitle), Icons.Default.Info) {
            navController.navigate(Screen.SettingsAbout.route)
        }
    }
}
