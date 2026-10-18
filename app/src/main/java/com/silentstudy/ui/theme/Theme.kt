package com.silentstudy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppThemeColors(
    val background: Color,
    val surface: Color,
    val control: Color,
    val border: Color,
    val accent: Color,
    val error: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val lightShadow: Color = border,
    val darkShadow: Color = background
)

val LocalAppColors = staticCompositionLocalOf {
    AppThemeColors(
        background = AppDarkBg,
        surface = AppDarkSurface,
        control = AppDarkControl,
        border = AppDarkBorder,
        accent = AppDarkAccent,
        error = AppDarkError,
        textPrimary = AppDarkTextPrimary,
        textSecondary = AppDarkTextSecondary
    )
}

// Backward compatibility alias for LocalNeuColors
val LocalNeuColors = LocalAppColors
typealias NeuColors = AppThemeColors

@Composable
fun SilentStudyTheme(
    isDarkMode: Boolean = true,
    isUltraDimMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val appColors = if (isUltraDimMode) {
        AppThemeColors(
            background = AppUltraDimBg,
            surface = AppUltraDimSurface,
            control = AppUltraDimControl,
            border = AppUltraDimBorder,
            accent = AppUltraDimAccent,
            error = AppUltraDimError,
            textPrimary = AppUltraDimTextPrimary,
            textSecondary = AppUltraDimTextSecondary
        )
    } else {
        AppThemeColors(
            background = AppDarkBg,
            surface = AppDarkSurface,
            control = AppDarkControl,
            border = AppDarkBorder,
            accent = AppDarkAccent,
            error = AppDarkError,
            textPrimary = AppDarkTextPrimary,
            textSecondary = AppDarkTextSecondary
        )
    }

    val materialColors = darkColorScheme(
        primary = appColors.accent,
        onPrimary = appColors.background,
        primaryContainer = appColors.control,
        onPrimaryContainer = appColors.textPrimary,
        background = appColors.background,
        onBackground = appColors.textPrimary,
        surface = appColors.surface,
        onSurface = appColors.textPrimary,
        surfaceVariant = appColors.control,
        onSurfaceVariant = appColors.textSecondary,
        outline = appColors.border,
        outlineVariant = appColors.border,
        error = appColors.error,
        onError = appColors.background
    )

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalNeuColors provides appColors
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
