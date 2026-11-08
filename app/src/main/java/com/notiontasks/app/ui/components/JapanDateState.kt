package com.notiontasks.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.notiontasks.app.utils.JapanTime
import kotlinx.coroutines.delay

/** Keeps screen-level "today" values current across the Japan-time date boundary. */
@Composable
fun rememberJapanToday(): String {
    var today by remember { mutableStateOf(JapanTime.todayIso()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            today = JapanTime.todayIso()
        }
    }
    return today
}
