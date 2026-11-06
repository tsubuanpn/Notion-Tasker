package com.notiontasks.app.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.notiontasks.app.R
import com.notiontasks.app.TaskNotificationReceiver
import com.notiontasks.app.ui.viewmodel.SettingsViewModel
import java.util.Locale

@Composable
fun NotificationsSettingsScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onScheduleChanged: () -> Unit,
) {
    val morningEnabled by settingsViewModel.morningNotifEnabled.collectAsState()
    val morningTime by settingsViewModel.morningNotifTime.collectAsState()
    val eveningEnabled by settingsViewModel.eveningNotifEnabled.collectAsState()
    val eveningTime by settingsViewModel.eveningNotifTime.collectAsState()
    val context = LocalContext.current
    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactAlarmAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } else {
        true
    }

    val showPicker = { currentTime: String, onUpdate: (String) -> Unit ->
        val parts = currentTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(context, { _, selectedHour, selectedMinute ->
            onUpdate(String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute))
        }, hour, minute, true).show()
    }

    SettingsSubPageScaffold(stringResource(R.string.settings_notifications_title), navController) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = if (notificationsAllowed && exactAlarmAllowed) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (notificationsAllowed && exactAlarmAllowed) "通知の準備はできています" else "通知の許可を確認してください",
                    fontWeight = FontWeight.Bold,
                )
                if (!notificationsAllowed) {
                    Text("Androidの通知権限が許可されていないため、朝・夜の通知は届きません。")
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }) { Text("通知を許可する") }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmAllowed) {
                    Text("正確なアラームが許可されていないため、通知時刻が遅れる場合があります。")
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = "package:${context.packageName}".toUri()
                        })
                    }) { Text("正確なアラームを許可する") }
                }
            }
        }
        NotificationItem(
            title = stringResource(R.string.settings_notif_morning),
            subtitle = stringResource(R.string.settings_notif_morning_desc),
            icon = Icons.Default.WbSunny,
            enabled = morningEnabled,
            time = morningTime,
            onToggle = { settingsViewModel.toggleMorningNotif(it); onScheduleChanged() },
            onTimeClick = { showPicker(morningTime) { settingsViewModel.updateMorningTime(it); onScheduleChanged() } },
        )
        NotificationItem(
            title = stringResource(R.string.settings_notif_evening),
            subtitle = stringResource(R.string.settings_notif_evening_desc),
            icon = Icons.Default.NightsStay,
            enabled = eveningEnabled,
            time = eveningTime,
            onToggle = { settingsViewModel.toggleEveningNotif(it); onScheduleChanged() },
            onTimeClick = { showPicker(eveningTime) { settingsViewModel.updateEveningTime(it); onScheduleChanged() } },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("通知を確認", fontWeight = FontWeight.Bold)
                Text("朝・夜の通知とは別に、時間割の開始時刻にも通知されます。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { TaskNotificationReceiver.showSettingsTestNotification(context) },
                    enabled = notificationsAllowed,
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("テスト通知を送る")
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    time: String,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = onToggle,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(checked = enabled, onCheckedChange = null)
            }
            if (enabled) {
                OutlinedButton(
                    onClick = onTimeClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_notif_time, time), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
