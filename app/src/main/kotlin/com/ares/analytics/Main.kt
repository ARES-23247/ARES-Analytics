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

    // Single instance lock using file channel locking
    val lockDir = java.io.File(System.getProperty("user.home") + "/.ares-analytics")
    lockDir.mkdirs()
    val lockFile = java.io.File(lockDir, "app.lock")
    val randomAccessFile = java.io.RandomAccessFile(lockFile, "rw")
    val fileChannel = randomAccessFile.channel
    val lock = try {
        fileChannel.tryLock()
    } catch (e: Exception) {
        null
    }

    if (lock == null) {
        System.err.println("[ARES-Analytics] App is already running (failed to acquire app.lock). Exiting.")
        try {
            randomAccessFile.close()
        } catch (e: Exception) {}
        java.lang.System.exit(0)
        return
    }

    // Keep the file resources open to hold the lock for the JVM lifetime
    // We add a shutdown hook to release it cleanly, though the OS does this automatically on exit
    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            lock.release()
            randomAccessFile.close()
        } catch (e: Exception) {}
    })

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


