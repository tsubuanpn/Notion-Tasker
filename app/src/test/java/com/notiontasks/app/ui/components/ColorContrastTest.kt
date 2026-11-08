package com.notiontasks.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {
    @Test
    fun lightNotionCategoryTextMeetsNormalTextContrastTarget() {
        listOf("gray", "brown", "orange", "yellow", "green", "blue", "purple", "pink", "red", null)
            .forEach { colorName ->
                val (background, foreground) = getNotionCategoryColors(colorName, isDark = false)
                assertTrue("$colorName: ${contrastRatio(background, foreground)}", contrastRatio(background, foreground) >= 4.5f)
            }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
