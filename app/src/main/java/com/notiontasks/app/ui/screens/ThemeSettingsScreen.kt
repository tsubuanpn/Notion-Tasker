package com.notiontasks.app.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.notiontasks.app.R
import com.notiontasks.app.ui.theme.AppThemePalette
import com.notiontasks.app.ui.theme.AppThemePalettes
import com.notiontasks.app.ui.viewmodel.SettingsViewModel

@Composable
fun ThemeSettingsScreen(settingsViewModel: SettingsViewModel, navController: NavController) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val themeColor by settingsViewModel.themeColorName.collectAsState()
    val dynamicColorEnabled by settingsViewModel.dynamicColorEnabled.collectAsState()
    val modes = listOf(
        Triple("system", stringResource(R.string.settings_theme_system), Icons.Default.Layers),
        Triple("light", stringResource(R.string.settings_theme_light), Icons.Default.WbSunny),
        Triple("dark", stringResource(R.string.settings_theme_dark), Icons.Default.NightsStay),
    )

    SettingsSubPageScaffold(stringResource(R.string.settings_theme_title), navController) {
        Text(
            stringResource(R.string.settings_theme_mode),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modes.forEach { (key, label, icon) ->
                val selected = themeMode == key
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (selected) null else borderStroke(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { settingsViewModel.updateThemeMode(key) },
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_theme_color),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_theme_dynamic), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.settings_theme_dynamic_desc), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = dynamicColorEnabled, onCheckedChange = settingsViewModel::toggleDynamicColor)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                val isColorEnabled = !dynamicColorEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                Text(
                    if (isColorEnabled) stringResource(R.string.settings_theme_preset) else stringResource(R.string.settings_theme_dynamic_priority),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (!isColorEnabled) {
                    Text("壁紙の色を使用中です。プリセット色は動的カラーをオフにすると選べます。", style = MaterialTheme.typography.bodySmall)
                } else {
                    AppThemePalettes.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { palette ->
                                ThemeColorItem(palette, themeColor == palette.name, settingsViewModel::updateThemeColor)
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {}
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("現在のテーマ", fontWeight = FontWeight.Bold)
                    Text(
                        if (dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "壁紙の動的カラー" else AppThemePalettes.firstOrNull { it.name == themeColor }?.label.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorItem(palette: AppThemePalette, isSelected: Boolean, onClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.width(160.dp).heightIn(min = 48.dp),
        onClick = { onClick(palette.name) },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) palette.seed.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) palette.seed else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = palette.seed, modifier = Modifier.size(20.dp)) {
                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(3.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(palette.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
