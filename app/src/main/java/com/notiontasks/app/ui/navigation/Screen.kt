package com.notiontasks.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "ホーム", Icons.Default.Home)
    object Category : Screen("category", "種類別", Icons.AutoMirrored.Filled.List)
    object Calendar : Screen("calendar", "カレンダー", Icons.Default.DateRange)
    object Pomodoro : Screen("pomodoro", "集中", Icons.Default.Timer)
    object Achievements : Screen("achievements", "実績", Icons.Default.EmojiEvents)
    object Settings : Screen("settings", "設定", Icons.Default.Settings)
}
