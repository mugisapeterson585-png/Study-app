package com.silentstudy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class NeuColors(
    val background: Color,
    val surface: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val LocalNeuColors = staticCompositionLocalOf {
    NeuColors(
        background = NeuDarkBg,
        surface = NeuDarkSurface,
        lightShadow = NeuDarkLightShadow,
        darkShadow = NeuDarkDarkShadow,
        accent = NeuDarkAccent,
        textPrimary = NeuDarkTextPrimary,
        textSecondary = NeuDarkTextSecondary
    )
}

@Composable
fun SilentStudyTheme(
    isDarkMode: Boolean = true,         // Default DARK (FIX 2)
    isUltraDimMode: Boolean = false,     // Ultra dim warm option (FIX 2)
    content: @Composable () -> Unit
) {
    val neuColors = when {
        isUltraDimMode -> NeuColors(
            background = NeuUltraDimBg,
            surface = NeuUltraDimSurface,
            lightShadow = NeuUltraDimLightShadow,
            darkShadow = NeuUltraDimDarkShadow,
            accent = NeuUltraDimAccent,
            textPrimary = NeuUltraDimTextPrimary,
            textSecondary = NeuUltraDimTextSecondary
        )
        isDarkMode -> NeuColors(
            background = NeuDarkBg,
            surface = NeuDarkSurface,
            lightShadow = NeuDarkLightShadow,
            darkShadow = NeuDarkDarkShadow,
            accent = NeuDarkAccent,
            textPrimary = NeuDarkTextPrimary,
            textSecondary = NeuDarkTextSecondary
        )
        else -> NeuColors(
            background = NeuLightBg,
            surface = NeuLightSurface,
            lightShadow = NeuLightLightShadow,
            darkShadow = NeuLightDarkShadow,
            accent = NeuLightAccent,
            textPrimary = NeuLightTextPrimary,
            textSecondary = NeuLightTextSecondary
        )
    }

    val materialColors = darkColorScheme(
        primary = neuColors.accent,
        background = neuColors.background,
        surface = neuColors.surface,
        onPrimary = neuColors.background,
        onBackground = neuColors.textPrimary,
        onSurface = neuColors.textPrimary
    )

    CompositionLocalProvider(LocalNeuColors provides neuColors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
