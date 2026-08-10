package com.ares.analytics.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationTarget(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Speed),
    IMPORT_CENTER("Log Imports", Icons.Default.FolderOpen),
    CLOUD("Cloud Sync", Icons.Default.Cloud),
    PATH_PLANNER("Auto Builder", Icons.Default.Route),
    FIELD_EDITOR("Field Editor", Icons.Default.Layers),
    ACADEMY("ARES Academy", Icons.Default.School),
    KDOC_VIEWER("KDoc Explorer", Icons.Default.Book),
    PIT_DIAGNOSTICS("Pit Self-Test", Icons.Default.Build),
    MATCH_STRATEGY("Match Strategy", Icons.Default.Analytics),
    RUN_HISTORY("Run History", Icons.Default.TableChart),
    DATABASE_VIEWER("Database", Icons.Default.Storage),
    TUNING("Tuning", Icons.Default.Tune),
    SUBSYSTEM_GEN("Subsystem Gen", Icons.Default.Construction),
    PROFILE("Profile", Icons.Default.Person),
    ADMIN("Admin Panel", Icons.Default.SupervisorAccount)
}

enum class NavigationSection(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Speed),
    ROBOT("Robot", Icons.Default.Build),
    AUTONOMOUS("Autonomous", Icons.Default.Route),
    ANALYSIS("Analysis", Icons.Default.Analytics),
    DATA("Data", Icons.Default.Cloud),
    SETTINGS("Settings", Icons.Default.Settings)
}

val primaryNavigationSections = NavigationSection.entries.toList()

val developerToolTargets = setOf(
    NavigationTarget.KDOC_VIEWER,
    NavigationTarget.DATABASE_VIEWER,
    NavigationTarget.SUBSYSTEM_GEN
)

fun NavigationTarget.section(): NavigationSection? = when (this) {
    NavigationTarget.DASHBOARD -> NavigationSection.DASHBOARD
    NavigationTarget.PIT_DIAGNOSTICS, NavigationTarget.TUNING -> NavigationSection.ROBOT
    NavigationTarget.PATH_PLANNER, NavigationTarget.FIELD_EDITOR -> NavigationSection.AUTONOMOUS
    NavigationTarget.RUN_HISTORY, NavigationTarget.MATCH_STRATEGY -> NavigationSection.ANALYSIS
    NavigationTarget.IMPORT_CENTER, NavigationTarget.CLOUD -> NavigationSection.DATA
    NavigationTarget.PROFILE, NavigationTarget.ADMIN -> NavigationSection.SETTINGS
    NavigationTarget.ACADEMY, NavigationTarget.KDOC_VIEWER,
    NavigationTarget.DATABASE_VIEWER, NavigationTarget.SUBSYSTEM_GEN -> null
}

fun NavigationSection.defaultTarget(): NavigationTarget = when (this) {
    NavigationSection.DASHBOARD -> NavigationTarget.DASHBOARD
    NavigationSection.ROBOT -> NavigationTarget.PIT_DIAGNOSTICS
    NavigationSection.AUTONOMOUS -> NavigationTarget.PATH_PLANNER
    NavigationSection.ANALYSIS -> NavigationTarget.RUN_HISTORY
    NavigationSection.DATA -> NavigationTarget.IMPORT_CENTER
    NavigationSection.SETTINGS -> NavigationTarget.PROFILE
}

fun NavigationSection.targets(): List<NavigationTarget> = when (this) {
    NavigationSection.DASHBOARD -> listOf(NavigationTarget.DASHBOARD)
    NavigationSection.ROBOT -> listOf(NavigationTarget.PIT_DIAGNOSTICS, NavigationTarget.TUNING)
    NavigationSection.AUTONOMOUS -> listOf(NavigationTarget.PATH_PLANNER, NavigationTarget.FIELD_EDITOR)
    NavigationSection.ANALYSIS -> listOf(NavigationTarget.RUN_HISTORY, NavigationTarget.MATCH_STRATEGY)
    NavigationSection.DATA -> listOf(NavigationTarget.IMPORT_CENTER, NavigationTarget.CLOUD)
    NavigationSection.SETTINGS -> listOf(NavigationTarget.PROFILE, NavigationTarget.ADMIN)
}

fun availablePaletteTargets(developerMode: Boolean): List<NavigationTarget> = NavigationTarget.entries.filter {
    developerMode || it !in developerToolTargets
}

fun NavigationTarget.groupLabel(): String = section()?.label ?: when (this) {
    NavigationTarget.ACADEMY -> "Help"
    in developerToolTargets -> "Developer tools"
    else -> "Tools"
}
