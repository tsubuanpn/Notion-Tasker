package com.notiontasks.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),      // Material Blue
    secondary = Color(0xFFA5D6A7),    // Green Accent
    tertiary = Color(0xFFFFCC80),     // Orange / Yellow
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF0D47A1),
    onSecondary = Color(0xFF1B5E20),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFEEEEEE)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E88E5),     // Blue
    secondary = Color(0xFF43A047),   // Green
    tertiary = Color(0xFFFB8C00),    // Orange
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121)
)

@Composable
fun NotionTaskerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
