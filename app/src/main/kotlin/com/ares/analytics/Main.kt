package com.ares.analytics

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Bounded window for graceful service disposal before the shutdown watchdog forces exit. */
private const val SHUTDOWN_TIMEOUT_MS = 15_000L
private const val WINDOW_HEALTH_CHECK_MS = 1_000
private const val WINDOW_INITIAL_PRESENTATION_DELAY_MS = 2_000
private const val WINDOW_STARTUP_TOPMOST_MS = 2_500
private const val WINDOW_TOPMOST_SETTLEMENT_CHECK_MS = 100
private const val WINDOW_TOPMOST_SETTLEMENT_LIMIT = 20
private const val WINDOW_RECOVERY_FAILURE_LIMIT = 3
private const val STARTUP_CAPTURE_ENV = "ARES_ANALYTICS_STARTUP_CAPTURE"
private const val STARTUP_CAPTURE_CLOSE_ENV = "ARES_ANALYTICS_STARTUP_CAPTURE_CLOSE"

internal fun isExpectedNativeWindow(
    expectedHandle: Long,
    candidateHandle: Long,
    ownerPid: Int,
    currentPid: Int,
    valid: Boolean,
    visible: Boolean,
): Boolean = expectedHandle != 0L &&
    candidateHandle == expectedHandle &&
    ownerPid == currentPid &&
    valid &&
    visible

private fun ownedWindowsTopLevelWindow(
    window: java.awt.Window,
): com.sun.jna.platform.win32.WinDef.HWND? = runCatching {
    if (!window.isDisplayable || !window.isVisible || !window.isShowing) return@runCatching null

    val user32 = com.sun.jna.platform.win32.User32.INSTANCE
    val currentPid = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentProcessId()
    val expectedPointer = com.sun.jna.Native.getWindowPointer(window) ?: return@runCatching null
    val expectedHandle = com.sun.jna.Pointer.nativeValue(expectedPointer)
    if (expectedHandle == 0L) return@runCatching null
    var ownedWindow: com.sun.jna.platform.win32.WinDef.HWND? = null

    // Native.getWindowPointer identifies the actual Compose/AWT peer but is not sufficient by
    // itself: AWT can retain a stale handle after its HWND disappears. Cross-check that exact
    // handle against EnumWindows, which is the same OS-level truth used by strict UI capture.
    user32.EnumWindows({ candidate, _ ->
        val ownerPid = com.sun.jna.ptr.IntByReference()
        user32.GetWindowThreadProcessId(candidate, ownerPid)
        val matches = isExpectedNativeWindow(
            expectedHandle = expectedHandle,
            candidateHandle = com.sun.jna.Pointer.nativeValue(candidate.pointer),
            ownerPid = ownerPid.value,
            currentPid = currentPid,
            valid = user32.IsWindow(candidate),
            visible = user32.IsWindowVisible(candidate),
        )
        if (matches) ownedWindow = candidate
        !matches
    }, null)

    ownedWindow
}.getOrNull()

private fun hasUsableNativeWindow(window: java.awt.Window): Boolean {
    if (!window.isDisplayable || !window.isVisible || !window.isShowing) return false
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return true
    return ownedWindowsTopLevelWindow(window) != null
}

private fun presentDesktopWindow(window: java.awt.Window): Boolean = runCatching {
    require(window.isDisplayable && window.isVisible && window.isShowing) {
        "Compose window is not displayable and visible"
    }

    // Compose owns visibility, native peer creation, and always-on-top state. Keep native APIs
    // observation-only: forcing ShowWindow/SetWindowPos/AttachThreadInput during windowOpened
    // races Compose's peer lifecycle and SetForegroundWindow is allowed to fail silently.
    window.toFront()
    window.requestFocus()
    hasUsableNativeWindow(window)
}.onFailure {
    System.err.println("[ARES-Analytics] Desktop window presentation failed: ${it.message}")
}.getOrDefault(false)

/**
 * Captures the real on-screen window only when the desktop test harness explicitly requests it.
 * Keeping this inside the ARES JVM avoids false negatives when separate test-tool processes are
 * assigned different Windows desktops/window stations. Normal application launches do no I/O.
 */
private fun captureStartupWindowIfRequested(window: java.awt.Window): Boolean {
    val outputPath = System.getenv(STARTUP_CAPTURE_ENV)?.trim().orEmpty()
    if (outputPath.isEmpty()) return false

    return runCatching {
        require(window.isShowing) { "desktop window is not showing" }
        val bounds = window.bounds
        require(bounds.width > 0 && bounds.height > 0) { "desktop window has invalid bounds: $bounds" }
        val outputFile = java.io.File(outputPath).absoluteFile
        outputFile.parentFile?.mkdirs()
        val image = java.awt.Robot(window.graphicsConfiguration.device).createScreenCapture(bounds)
        require(javax.imageio.ImageIO.write(image, "png", outputFile)) {
            "no PNG writer is available"
        }
        println(
            "[ARES-Analytics] Desktop startup capture written: " +
                "path=${outputFile.absolutePath}, size=${image.width}x${image.height}"
        )
    }.onFailure {
        System.err.println("[ARES-Analytics] Desktop startup capture failed: ${it.message}")
    }.isSuccess
}

/** Posts the same native WM_CLOSE used by the external tester, but only in opt-in capture runs. */
private fun closeCapturedStartupWindowIfRequested(
    window: java.awt.Window,
    captureSucceeded: Boolean,
) {
    if (!captureSucceeded || !System.getenv(STARTUP_CAPTURE_CLOSE_ENV).toBoolean()) return
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return

    val hwnd = ownedWindowsTopLevelWindow(window)
    if (hwnd == null) {
        System.err.println("[ARES-Analytics] Desktop startup capture close failed: native HWND is missing")
        return
    }

    com.sun.jna.platform.win32.User32.INSTANCE.PostMessage(
        hwnd,
        com.sun.jna.platform.win32.WinUser.WM_CLOSE,
        com.sun.jna.platform.win32.WinDef.WPARAM(0L),
        com.sun.jna.platform.win32.WinDef.LPARAM(0L),
    )
    println(
        "[ARES-Analytics] Desktop startup capture WM_CLOSE posted: " +
            "hwnd=${com.sun.jna.Pointer.nativeValue(hwnd.pointer)}, requestSent=true"
    )
}

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
        var startupAlwaysOnTop by remember { mutableStateOf(true) }

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
            // Windows may deny a foreground request from a Gradle-launched child process. Let
            // Compose create the window topmost so it is visible even when activation is denied,
            // then release topmost status shortly after the native peer has opened.
            alwaysOnTop = startupAlwaysOnTop,
            visible = true,
        ) {
            DisposableEffect(window) {
                window.minimumSize = java.awt.Dimension(1100, 700)
                val initialPresentationScheduled = java.util.concurrent.atomic.AtomicBoolean(false)
                lateinit var windowHealthTimer: javax.swing.Timer
                var topmostSettlementChecks = 0
                val startupTopmostSettlementTimer = javax.swing.Timer(WINDOW_TOPMOST_SETTLEMENT_CHECK_MS) {
                    topmostSettlementChecks++
                    val settled = !window.isAlwaysOnTop
                    if (settled || topmostSettlementChecks >= WINDOW_TOPMOST_SETTLEMENT_LIMIT) {
                        (it.source as javax.swing.Timer).stop()
                        val message =
                            "[ARES-Analytics] Desktop startup presentation settled: " +
                                "alwaysOnTop=${window.isAlwaysOnTop}, focused=${window.isFocused}, " +
                                "active=${window.isActive}, showing=${window.isShowing}"
                        if (settled) println(message) else System.err.println(message)
                        val captureSucceeded = captureStartupWindowIfRequested(window)
                        closeCapturedStartupWindowIfRequested(window, captureSucceeded)
                    }
                }.apply {
                    isRepeats = true
                }
                val startupTopmostReleaseTimer = javax.swing.Timer(WINDOW_STARTUP_TOPMOST_MS) {
                    startupAlwaysOnTop = false
                    topmostSettlementChecks = 0
                    startupTopmostSettlementTimer.restart()
                }.apply {
                    isRepeats = false
                }

                fun scheduleInitialPresentation(reason: String) {
                    if (!initialPresentationScheduled.compareAndSet(false, true)) return

                    // Let the lifecycle event finish before manipulating focus/Z-order. Unlike a
                    // generic startup invokeLater, windowOpened proves that Compose's real AWT
                    // peer reached its opened lifecycle instead of exposing a transient HWND.
                    java.awt.EventQueue.invokeLater {
                        if (shutdownStarted.get()) return@invokeLater

                        if (!presentDesktopWindow(window)) {
                            System.err.println(
                                "[ARES-Analytics] Desktop window was not usable after $reason; " +
                                    "the native recovery watchdog is active."
                            )
                        } else {
                            val hwnd = ownedWindowsTopLevelWindow(window)
                            println(
                                "[ARES-Analytics] Desktop window presented after $reason: " +
                                    "size=${window.size}, location=${window.location}, " +
                                    "showing=${window.isShowing}, nativeVisible=true, " +
                                    "hwnd=${hwnd?.pointer?.let { com.sun.jna.Pointer.nativeValue(it) }}"
                            )
                        }
                        if (!startupTopmostReleaseTimer.isRunning) {
                            startupTopmostReleaseTimer.start()
                        }
                        if (!windowHealthTimer.isRunning) windowHealthTimer.start()
                    }
                }

                val listener = object : java.awt.event.WindowAdapter() {
                    override fun windowGainedFocus(event: java.awt.event.WindowEvent?) {
                        println("[ARES-Analytics] Desktop window focused")
                    }

                    override fun windowLostFocus(event: java.awt.event.WindowEvent?) {
                        services.keyboardDriveState.releaseAll()
                    }
                }
                val lifecycleListener = object : java.awt.event.WindowAdapter() {
                    override fun windowOpened(event: java.awt.event.WindowEvent?) {
                        println("[ARES-Analytics] Desktop window opened")
                        scheduleInitialPresentation("windowOpened")
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

                var consecutiveRecoveryFailures = 0
                windowHealthTimer = javax.swing.Timer(WINDOW_HEALTH_CHECK_MS) {
                    if (shutdownStarted.get()) {
                        (it.source as javax.swing.Timer).stop()
                    } else if (!hasUsableNativeWindow(window)) {
                        consecutiveRecoveryFailures++
                        System.err.println(
                            "[ARES-Analytics] Native desktop window is missing; " +
                                "recovery attempt $consecutiveRecoveryFailures/$WINDOW_RECOVERY_FAILURE_LIMIT."
                        )
                        if (presentDesktopWindow(window)) {
                            consecutiveRecoveryFailures = 0
                            val hwnd = ownedWindowsTopLevelWindow(window)
                            println(
                                "[ARES-Analytics] Native desktop window recovered: " +
                                    "size=${window.size}, location=${window.location}, " +
                                    "showing=${window.isShowing}, " +
                                    "hwnd=${hwnd?.pointer?.let { com.sun.jna.Pointer.nativeValue(it) }}"
                            )
                        } else if (consecutiveRecoveryFailures >= WINDOW_RECOVERY_FAILURE_LIMIT) {
                            System.err.println(
                                "[ARES-Analytics] Native desktop window could not be recovered; " +
                                    "terminating so the next launch can acquire app.lock."
                            )
                            exitProcess(1)
                        }
                    } else {
                        consecutiveRecoveryFailures = 0
                    }
                }.apply {
                    initialDelay = WINDOW_HEALTH_CHECK_MS
                    isRepeats = true
                }

                val initialPresentationFallback = javax.swing.Timer(WINDOW_INITIAL_PRESENTATION_DELAY_MS) {
                    scheduleInitialPresentation("startup fallback")
                }.apply {
                    isRepeats = false
                }

                window.addWindowFocusListener(listener)
                window.addWindowListener(lifecycleListener)
                window.addComponentListener(visibilityListener)
                initialPresentationFallback.start()

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
                    initialPresentationFallback.stop()
                    startupTopmostReleaseTimer.stop()
                    startupTopmostSettlementTimer.stop()
                    windowHealthTimer.stop()
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
