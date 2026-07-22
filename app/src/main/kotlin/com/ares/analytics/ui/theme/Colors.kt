package com.ares.analytics.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

// ── Theme State Holder ───────────────────────────────────────────────────────
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
object AresThemeSettings {
    var colorblindMode by mutableStateOf(false)
    var highContrastMode by mutableStateOf(false)
    var touchOptimizedMode by mutableStateOf(false)
    val currentColors: AresColorPalette
        get() = getAresColors(colorblindMode, highContrastMode)
}

// ── Dynamic Color Lookups ───────────────────────────────────────────────────
val AresBackground: Color get() = AresThemeSettings.currentColors.background
val AresSurface: Color get() = AresThemeSettings.currentColors.surface
val AresSurfaceElevated: Color get() = AresThemeSettings.currentColors.surfaceElevated
val AresRed: Color get() = AresThemeSettings.currentColors.red
val AresRedDark: Color get() = AresThemeSettings.currentColors.redDark
val AresRedGlow: Color get() = AresThemeSettings.currentColors.redGlow
val AresCyan: Color get() = AresThemeSettings.currentColors.cyan
val AresCyanDark: Color get() = AresThemeSettings.currentColors.cyanDark
val AresCyanGlow: Color get() = AresThemeSettings.currentColors.cyanGlow
val AresGold: Color get() = AresThemeSettings.currentColors.gold
val AresAmber: Color get() = AresThemeSettings.currentColors.amber
val AresGreen: Color get() = AresThemeSettings.currentColors.green
val AresError: Color get() = AresThemeSettings.currentColors.error
val AresTextPrimary: Color get() = AresThemeSettings.currentColors.textPrimary
val AresTextSecondary: Color get() = AresThemeSettings.currentColors.textSecondary
val AresTextTertiary: Color get() = AresThemeSettings.currentColors.textTertiary
val AresBorder: Color get() = AresThemeSettings.currentColors.border
val AresBorderFocused: Color get() = AresThemeSettings.currentColors.borderFocused
val AresGlass: Color get() = AresThemeSettings.currentColors.glass
val AresGlassBorder: Color get() = AresThemeSettings.currentColors.glassBorder
val AresAlertActive: Color get() = AresThemeSettings.currentColors.alertActive
val AresAlertLatched: Color get() = AresThemeSettings.currentColors.alertLatched
val AresAlertTriaged: Color get() = AresThemeSettings.currentColors.alertTriaged
val ModeLive: Color get() = AresThemeSettings.currentColors.modeLive
val ModeLiveGlow: Color get() = AresThemeSettings.currentColors.modeLiveGlow
val ModeRewind: Color get() = AresThemeSettings.currentColors.modeRewind
val ModeRewindGlow: Color get() = AresThemeSettings.currentColors.modeRewindGlow
val ModeReplay: Color get() = AresThemeSettings.currentColors.modeReplay
val ModeReplayGlow: Color get() = AresThemeSettings.currentColors.modeReplayGlow
val AresPathPlanned: Color get() = AresThemeSettings.currentColors.pathPlanned
val AresPathActual: Color get() = AresThemeSettings.currentColors.pathActual
val AresDeviationLow: Color get() = AresThemeSettings.currentColors.deviationLow
val AresDeviationMedium: Color get() = AresThemeSettings.currentColors.deviationMedium
val AresDeviationHigh: Color get() = AresThemeSettings.currentColors.deviationHigh

// ── Color Palette Generator ──────────────────────────────────────────────────
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class AresColorPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val red: Color,
    val redDark: Color,
    val redGlow: Color,
    val cyan: Color,
    val cyanDark: Color,
    val cyanGlow: Color,
    val gold: Color,
    val amber: Color,
    val green: Color,
    val error: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val borderFocused: Color,
    val glass: Color,
    val glassBorder: Color,
    val alertActive: Color,
    val alertLatched: Color,
    val alertTriaged: Color,
    val modeLive: Color,
    val modeLiveGlow: Color,
    val modeRewind: Color,
    val modeRewindGlow: Color,
    val modeReplay: Color,
    val modeReplayGlow: Color,
    val pathPlanned: Color,
    val pathActual: Color,
    val deviationLow: Color,
    val deviationMedium: Color,
    val deviationHigh: Color
)

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun getAresColors(colorblind: Boolean, highContrast: Boolean): AresColorPalette {
    val textPrimary = Color(0xFFE8ECF4)
    val textSecondary = if (highContrast) Color(0xFFF3F5F9) else Color(0xFF9CA3B4)
    val textTertiary = if (highContrast) Color(0xFFCAD0DE) else Color(0xFF5A6174)
    val green = if (colorblind) Color(0xFF2979FF) else Color(0xFF66BB6A)
    val error = if (colorblind) Color(0xFFFF6D00) else Color(0xFFFF5252)
    val gold = Color(0xFFFFD54F)
    val amber = Color(0xFFFFA726)
    val deviationLow = green
    val deviationMedium = amber
    val deviationHigh = error
    val red = if (colorblind) Color(0xFFFF6D00) else Color(0xFFE53935)
    val redDark = if (colorblind) Color(0xFFE65100) else Color(0xFFB71C1C)
    val redGlow = if (colorblind) Color(0x40FF6D00) else Color(0x40E53935)
    val cyan = Color(0xFF00E5FF)
    val cyanDark = Color(0xFF00B8D4)
    val cyanGlow = Color(0x3000E5FF)
    val background = Color(0xFF0D0F14)
    val surface = Color(0xFF161A22)
    val surfaceElevated = Color(0xFF1E2330)
    val border = if (highContrast) Color(0xFF8B9BB8) else Color(0xFF6B7B98)
    val borderFocused = if (highContrast) Color(0xFFBACCDD) else Color(0xFF8B9BB8)
    val glass = Color(0x1AFFFFFF)
    val glassBorder = Color(0x20FFFFFF)

    return AresColorPalette(
        background = background,
        surface = surface,
        surfaceElevated = surfaceElevated,
        red = red,
        redDark = redDark,
        redGlow = redGlow,
        cyan = cyan,
        cyanDark = cyanDark,
        cyanGlow = cyanGlow,
        gold = gold,
        amber = amber,
        green = green,
        error = error,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textTertiary = textTertiary,
        border = border,
        borderFocused = borderFocused,
        glass = glass,
        glassBorder = glassBorder,
        alertActive = error,
        alertLatched = amber,
        alertTriaged = textTertiary,
        modeLive = red,
        modeLiveGlow = redGlow,
        modeRewind = amber,
        modeRewindGlow = Color(0x40FFA726),
        modeReplay = cyan,
        modeReplayGlow = cyanGlow,
        pathPlanned = cyan,
        pathActual = gold,
        deviationLow = deviationLow,
        deviationMedium = deviationMedium,
        deviationHigh = deviationHigh
    )
}
