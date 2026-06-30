package com.ares.analytics

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ares.analytics.di.ServiceRegistry
import com.ares.analytics.ui.theme.AresTheme
import com.ares.analytics.ui.screens.MainScreen

fun main() {
    // Disable Java Assistive Technology check to prevent crash on Windows systems with screen readers active
    System.setProperty("javax.accessibility.assistive_technologies", "")

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val logDir = java.io.File(System.getProperty("user.home") + "/.ares-analytics/logs")
            logDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(java.util.Date())
            val crashFile = java.io.File(logDir, "crash-$timestamp.log")
            java.io.PrintWriter(java.io.FileWriter(crashFile)).use { writer ->
                writer.println("Thread: ${thread.name}")
                writer.println("Timestamp: ${java.time.Instant.now()}")
                writer.println("Exception: ${throwable.message}")
                throwable.printStackTrace(writer)
            }
            System.err.println("CRITICAL FAULT: Uncaught exception in thread '${thread.name}'. Log: ${crashFile.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    application {
        val windowState = rememberWindowState(
            width = 1440.dp,
            height = 900.dp
        )
        val services = remember { ServiceRegistry() }

        Window(
            onCloseRequest = {
                try {
                    services.dispose()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                exitApplication()
                java.lang.System.exit(0)
            },
            title = "ARES Analytics — Mission Control",
            state = windowState
        ) {
            AresTheme {
                MainScreen(services = services)
            }
        }
    }
}


