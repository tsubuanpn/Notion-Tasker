package com.notiontasks.app.ui.theme

import androidx.compose.ui.graphics.Color

// --- Theme Palettes ---

data class AppThemePalette(
    val name: String,
    val label: String,
    val seed: Color,
    val lightPrimary: Color,
    val darkPrimary: Color
)

val AppThemePalettes = listOf(
    AppThemePalette("blue", "ブルー", Color(0xFF0061A4), Color(0xFF0061A4), Color(0xFF9ECAFF)),
    AppThemePalette("green", "グリーン", Color(0xFF006E1C), Color(0xFF006E1C), Color(0xFF77DD77)),
    AppThemePalette("red", "レッド", Color(0xFFBA1A1A), Color(0xFFBA1A1A), Color(0xFFFFB4AB)),
    AppThemePalette("purple", "パープル", Color(0xFF6B5778), Color(0xFF6B5778), Color(0xFFD7BEE4)),
    AppThemePalette("orange", "オレンジ", Color(0xFF8B5000), Color(0xFF8B5000), Color(0xFFFFB870)),
    AppThemePalette("pink", "ピンク", Color(0xFF9C4278), Color(0xFF9C4278), Color(0xFFFFD8E6)),
    AppThemePalette("teal", "ティール", Color(0xFF006A6A), Color(0xFF006A6A), Color(0xFF80D5D4)),
    AppThemePalette("brown", "ブラウン", Color(0xFF7D5800), Color(0xFF7D5800), Color(0xFFFFB952))
)

// Warning / Mochikoshi Colors
val WarningOrangeContainerLight = Color(0xFFFFF3E0)
val WarningOrangeOnContainerLight = Color(0xFFE65100)
val WarningOrangeContainerDark = Color(0xFF4D2B00)
val WarningOrangeOnContainerDark = Color(0xFFFFB347)

// Default (Blue) Colors
val md_theme_light_primary = Color(0xFF0061A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD1E4FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001D36)
val md_theme_light_secondary = Color(0xFF535F70)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFD7E3F7)
val md_theme_light_onSecondaryContainer = Color(0xFF101C2B)
val md_theme_light_tertiary = Color(0xFF6B5778)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFF2DAFF)
val md_theme_light_onTertiaryContainer = Color(0xFF251431)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFDFCFF)
val md_theme_light_onBackground = Color(0xFF1A1C1E)
val md_theme_light_surface = Color(0xFFFDFCFF)
val md_theme_light_onSurface = Color(0xFF1A1C1E)
val md_theme_light_surfaceVariant = Color(0xFFDFE2EB)
val md_theme_light_onSurfaceVariant = Color(0xFF43474E)
val md_theme_light_outline = Color(0xFF73777F)

val md_theme_dark_primary = Color(0xFF9ECAFF)
val md_theme_dark_onPrimary = Color(0xFF003258)
val md_theme_dark_primaryContainer = Color(0xFF00497D)
val md_theme_dark_onPrimaryContainer = Color(0xFFD1E4FF)
val md_theme_dark_secondary = Color(0xFFBBC7DB)
val md_theme_dark_onSecondary = Color(0xFF253140)
val md_theme_dark_secondaryContainer = Color(0xFF3B4858)
val md_theme_dark_onSecondaryContainer = Color(0xFFD7E3F7)
val md_theme_dark_tertiary = Color(0xFFD7BEE4)
val md_theme_dark_onTertiary = Color(0xFF3B2948)
val md_theme_dark_tertiaryContainer = Color(0xFF523F5F)
val md_theme_dark_onTertiaryContainer = Color(0xFFF2DAFF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF1A1C1E)
val md_theme_dark_onBackground = Color(0xFFE2E2E6)
val md_theme_dark_surface = Color(0xFF1A1C1E)
val md_theme_dark_onSurface = Color(0xFFE2E2E6)
val md_theme_dark_surfaceVariant = Color(0xFF43474E)
val md_theme_dark_onSurfaceVariant = Color(0xFFC3C7CF)
val md_theme_dark_outline = Color(0xFF8D9199)
