package com.ares.analytics.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AresColorScheme = darkColorScheme(
    primary = AresCyan,
    onPrimary = AresBackground,
    primaryContainer = AresCyanDark,
    onPrimaryContainer = AresTextPrimary,

    secondary = AresRed,
    onSecondary = AresTextPrimary,
    secondaryContainer = AresRedDark,
    onSecondaryContainer = AresTextPrimary,

    tertiary = AresGold,
    onTertiary = AresBackground,

    background = AresBackground,
    onBackground = AresTextPrimary,

    surface = AresSurface,
    onSurface = AresTextPrimary,
    surfaceVariant = AresSurfaceElevated,
    onSurfaceVariant = AresTextSecondary,

    error = AresError,
    onError = AresTextPrimary,

    outline = AresBorder,
    outlineVariant = AresBorderFocused
)

private val AresShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun AresTheme(
    colorblindMode: Boolean = false,
    highContrastMode: Boolean = false,
    touchOptimizedMode: Boolean = false,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.SideEffect {
        AresThemeSettings.colorblindMode = colorblindMode
        AresThemeSettings.highContrastMode = highContrastMode
        AresThemeSettings.touchOptimizedMode = touchOptimizedMode
    }

    MaterialTheme(
        colorScheme = AresColorScheme,
        typography = AresTypography,
        shapes = AresShapes,
        content = content
    )
}
