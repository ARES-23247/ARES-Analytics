package com.ares.analytics.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private fun aresColorScheme(colors: AresColorPalette) = darkColorScheme(
    primary = colors.cyan,
    onPrimary = colors.background,
    primaryContainer = colors.cyanDark,
    onPrimaryContainer = colors.textPrimary,

    secondary = colors.red,
    onSecondary = colors.textPrimary,
    secondaryContainer = colors.redDark,
    onSecondaryContainer = colors.textPrimary,

    tertiary = colors.gold,
    onTertiary = colors.background,

    background = colors.background,
    onBackground = colors.textPrimary,

    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceElevated,
    onSurfaceVariant = colors.textSecondary,

    error = colors.error,
    onError = colors.textPrimary,

    outline = colors.border,
    outlineVariant = colors.borderFocused
)

private val AresShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun AresTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = aresColorScheme(AresThemeSettings.currentColors)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AresTypography,
        shapes = AresShapes,
        content = content
    )
}
