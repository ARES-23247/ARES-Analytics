package com.ares.analytics

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ares.analytics.di.ServiceRegistry
import com.ares.analytics.ui.theme.AresTheme
import com.ares.analytics.ui.screens.MainScreen
import com.ares.analytics.ui.theme.rememberAresLogoPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Bounded window for graceful service disposal before the shutdown watchdog forces exit. */
private const val SHUTDOWN_TIMEOUT_MS = 15_000L

/**
 * Last-resort exit guarantee: [androidx.compose.ui.window.ApplicationScope.exitApplication]
 * ends the Compose loop but the JVM only terminates once all non-daemon threads finish — a
 * thread stuck inside a hung service leaves a zombie process holding the single-instance
 * lock. This daemon thread escalates to [Runtime.halt] after a grace period; halt skips
 * shutdown hooks, which is acceptable here because the disposal path (the thing that ran
 * the hooks' work: lock release is OS-automatic, telemetry persistence is dispose's job)
 * already had its chance.
 */
private fun watchHardExit(graceMs: Long = 3_000L) {
    thread(isDaemon = true, name = "shutdown-halt-watchdog") {
        Thread.sleep(graceMs)
        System.err.println("Shutdown watchdog: JVM still alive after exitApplication; halting.")
        Runtime.getRuntime().halt(1)
    }
}

/** Starts the single-instance Compose desktop application and owns process-level cleanup. */
fun main(args: Array<String>) {
    runPackagedProjectValidationCommand(args)?.let { exitCode ->
        if (exitCode != 0) exitProcess(exitCode)
        return
    }

    launchDesktopApplication()
}

private fun launchDesktopApplication() {
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
        runCatching(randomAccessFile::close)
        java.lang.System.exit(0)
        return
    }

    // Keep the file resources open to hold the lock for the JVM lifetime
    // We add a shutdown hook to release it cleanly, though the OS does this automatically on exit
    Runtime.getRuntime().addShutdownHook(Thread {
        runCatching {
            lock.release()
            randomAccessFile.close()
        }.onFailure(Throwable::printStackTrace)
    })

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        val fatalDesktopUiFailure = thread.name.startsWith("AWT-EventQueue")
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
        } finally {
            if (fatalDesktopUiFailure) {
                System.err.println(
                    "[ARES-Analytics] Fatal desktop UI failure left no usable window; " +
                        "terminating so the single-instance lock cannot become orphaned."
                )
                exitProcess(1)
            }
        }
    }

    application {
        val windowState = rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(1440.dp, 900.dp),
        )
        val services = remember { ServiceRegistry() }
        val shutdownScope = rememberCoroutineScope()
        val shutdownStarted = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

        Window(
            onCloseRequest = {
                if (shutdownStarted.compareAndSet(false, true)) {
                    shutdownScope.launch {
                        // Watchdog: a hung service teardown (NT4 flush retries, DuckDB
                        // checkpoint, drive I/O) must not leave an unclosable window that
                        // also holds the single-instance lock. Disposal runs as its own job
                        // because a blocking, non-cooperative teardown cannot be cancelled —
                        // we stop waiting for it instead, then guarantee process exit.
                        val disposeJob = launch(Dispatchers.IO) {
                            try {
                                services.disposeAndJoin()
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        }
                        val finished = withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { disposeJob.join() }
                        if (finished == null) {
                            System.err.println(
                                "Shutdown watchdog: disposal did not finish in " +
                                    "$SHUTDOWN_TIMEOUT_MS ms; forcing application exit."
                            )
                            watchHardExit()
                        }
                        exitApplication()
                    }
                }
            },
            title = "ARES Analytics — Mission Control",
            state = windowState,
            visible = true,
        ) {
            DisposableEffect(window) {
                window.minimumSize = java.awt.Dimension(1100, 700)
                val listener = object : java.awt.event.WindowAdapter() {
                    override fun windowLostFocus(event: java.awt.event.WindowEvent?) {
                        services.keyboardDriveState.releaseAll()
                    }
                }
                val lifecycleListener = object : java.awt.event.WindowAdapter() {
                    override fun windowOpened(event: java.awt.event.WindowEvent?) {
                        println("[ARES-Analytics] Desktop window opened")
                    }

                    override fun windowClosing(event: java.awt.event.WindowEvent?) {
                        println("[ARES-Analytics] Desktop window closing")
                    }

                    override fun windowClosed(event: java.awt.event.WindowEvent?) {
                        println("[ARES-Analytics] Desktop window closed")
                    }
                }
                val visibilityListener = object : java.awt.event.ComponentAdapter() {
                    override fun componentShown(event: java.awt.event.ComponentEvent?) {
                        println("[ARES-Analytics] Desktop window shown")
                    }

                    override fun componentHidden(event: java.awt.event.ComponentEvent?) {
                        println("[ARES-Analytics] Desktop window hidden")
                    }
                }
                window.addWindowFocusListener(listener)
                window.addWindowListener(lifecycleListener)
                window.addComponentListener(visibilityListener)

                // Window() creates the native peer asynchronously. Present it on the next AWT
                // event instead of racing peer creation from the initial composition pass.
                java.awt.EventQueue.invokeLater {
                    if (!window.isDisplayable) {
                        System.err.println(
                            "[ARES-Analytics] Desktop window peer was disposed before presentation."
                        )
                    } else {
                        if (!window.isVisible) window.isVisible = true
                        window.toFront()
                        window.requestFocus()
                        println(
                            "[ARES-Analytics] Desktop window presented: " +
                                "size=${window.size}, location=${window.location}, showing=${window.isShowing}"
                        )
                    }
                }

                onDispose {
                    val shutdownWasRequested = shutdownStarted.get()
                    println(
                        "[ARES-Analytics] Desktop window composition disposed: " +
                            "displayable=${window.isDisplayable}, visible=${window.isVisible}, " +
                            "showing=${window.isShowing}, shutdownStarted=$shutdownWasRequested"
                    )
                    window.removeComponentListener(visibilityListener)
                    window.removeWindowListener(lifecycleListener)
                    window.removeWindowFocusListener(listener)
                    if (!shutdownWasRequested) {
                        System.err.println(
                            "[ARES-Analytics] Desktop window disappeared without a shutdown request; " +
                                "terminating so the next launch can acquire app.lock."
                        )
                        exitProcess(1)
                    }
                }
            }
            AresTheme {
                MainScreen(services = services)
            }
        }
    }
}
