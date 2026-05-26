package com.notiontasks.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "ホーム", Icons.Default.Home)
    object Category : Screen("category", "種類別", Icons.AutoMirrored.Filled.List)
    object Completed : Screen("completed", "完了済み", Icons.Default.CheckCircle)
    object Settings : Screen("settings", "設定", Icons.Default.Settings)
}
