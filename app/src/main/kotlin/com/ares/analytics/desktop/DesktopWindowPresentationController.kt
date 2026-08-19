package com.ares.analytics.desktop

import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Timer

internal const val WINDOW_HEALTH_CHECK_MS = 1_000
internal const val WINDOW_INITIAL_PRESENTATION_DELAY_MS = 2_000
internal const val WINDOW_STARTUP_TOPMOST_MS = 2_500
internal const val WINDOW_TOPMOST_SETTLEMENT_CHECK_MS = 100
internal const val WINDOW_TOPMOST_SETTLEMENT_LIMIT = 20
internal const val WINDOW_RECOVERY_FAILURE_LIMIT = 3
internal const val STARTUP_CAPTURE_ENV = "ARES_ANALYTICS_STARTUP_CAPTURE"
internal const val STARTUP_CAPTURE_CLOSE_ENV = "ARES_ANALYTICS_STARTUP_CAPTURE_CLOSE"

/** Pure decisions of the presentation policy, separated from Swing timers for unit testing. */
internal object DesktopPresentationPolicy {
    /**
     * Windows may deny a foreground request from a Gradle-launched child process, so Compose
     * starts the window topmost; the topmost state is released only after the bounded startup
     * interval, never before.
     */
    fun shouldReleaseStartupTopmost(elapsedMs: Long): Boolean = elapsedMs >= WINDOW_STARTUP_TOPMOST_MS

    /** Settled requires the Compose-owned topmost state to have actually returned to false. */
    fun isSettled(alwaysOnTop: Boolean): Boolean = !alwaysOnTop

    fun settlementExceeded(checks: Int): Boolean = checks >= WINDOW_TOPMOST_SETTLEMENT_LIMIT

    fun captureRequested(envValue: String?): Boolean = !envValue.orEmpty().trim().isEmpty()

    fun closeAfterCaptureRequested(envValue: String?): Boolean = envValue.toBoolean()
}

/**
 * Owns the desktop window's startup presentation, topmost settlement, health recovery, and
 * the opt-in same-process capture/WM_CLOSE verifier. Compose remains the sole owner of
 * visibility and always-on-top state: this controller only observes the native window,
 * calls the AWT-level toFront()/requestFocus(), and drives Compose state via [onStartupAlwaysOnTopChange].
 *
 * All work runs on the AWT event thread (Swing timers + window callbacks).
 */
internal class DesktopWindowPresentationController(
    private val window: Window,
    private val machine: DesktopStartupMachine,
    private val isShutdownStarted: () -> Boolean,
    private val onStartupAlwaysOnTopChange: (Boolean) -> Unit,
    private val onFocusLost: () -> Unit,
    private val onUnrecoverableWindowLoss: (reason: String) -> Nothing,
) {
    private val initialPresentationScheduled = AtomicBoolean(false)
    private var topmostSettlementChecks = 0
    private var consecutiveRecoveryFailures = 0

    private lateinit var healthTimer: Timer
    private val topmostSettlementTimer = Timer(WINDOW_TOPMOST_SETTLEMENT_CHECK_MS) {
        topmostSettlementChecks++
        val settled = DesktopPresentationPolicy.isSettled(window.isAlwaysOnTop)
        if (settled || DesktopPresentationPolicy.settlementExceeded(topmostSettlementChecks)) {
            (it.source as Timer).stop()
            val message =
                "[ARES-Analytics] Desktop startup presentation settled: " +
                    "alwaysOnTop=${window.isAlwaysOnTop}, focused=${window.isFocused}, " +
                    "active=${window.isActive}, showing=${window.isShowing}"
            if (settled) println(message) else System.err.println(message)
            machine.transitionTo(DesktopStartupState.SETTLED)
            val captureSucceeded = captureStartupWindowIfRequested()
            closeCapturedStartupWindowIfRequested(captureSucceeded)
        }
    }.apply { isRepeats = true }

    private val topmostReleaseTimer = Timer(WINDOW_STARTUP_TOPMOST_MS) {
        onStartupAlwaysOnTopChange(false)
        topmostSettlementChecks = 0
        topmostSettlementTimer.restart()
    }.apply { isRepeats = false }

    private val focusListener = object : WindowAdapter() {
        override fun windowGainedFocus(event: WindowEvent?) {
            println("[ARES-Analytics] Desktop window focused")
        }

        override fun windowLostFocus(event: WindowEvent?) {
            onFocusLost()
        }
    }

    private val lifecycleListener = object : WindowAdapter() {
        override fun windowOpened(event: WindowEvent?) {
            println("[ARES-Analytics] Desktop window opened")
            machine.transitionTo(DesktopStartupState.OPENED)
            scheduleInitialPresentation("windowOpened")
        }

        override fun windowClosing(event: WindowEvent?) {
            println("[ARES-Analytics] Desktop window closing")
        }

        override fun windowClosed(event: WindowEvent?) {
            println("[ARES-Analytics] Desktop window closed")
            machine.markClosed()
        }
    }

    private val visibilityListener = object : ComponentAdapter() {
        override fun componentShown(event: ComponentEvent?) {
            println("[ARES-Analytics] Desktop window shown")
        }

        override fun componentHidden(event: ComponentEvent?) {
            println("[ARES-Analytics] Desktop window hidden")
        }
    }

    private val initialPresentationFallback = Timer(WINDOW_INITIAL_PRESENTATION_DELAY_MS) {
        scheduleInitialPresentation("startup fallback")
    }.apply { isRepeats = false }

    private var healthTimerInitialized = false

    fun attach() {
        window.addWindowFocusListener(focusListener)
        window.addWindowListener(lifecycleListener)
        window.addComponentListener(visibilityListener)
        initialPresentationFallback.start()
    }

    fun detach(expectedShutdown: Boolean) {
        println(
            "[ARES-Analytics] Desktop window composition disposed: " +
                "displayable=${window.isDisplayable}, visible=${window.isVisible}, " +
                "showing=${window.isShowing}, shutdownStarted=$expectedShutdown"
        )
        window.removeComponentListener(visibilityListener)
        window.removeWindowListener(lifecycleListener)
        window.removeWindowFocusListener(focusListener)
        initialPresentationFallback.stop()
        topmostReleaseTimer.stop()
        topmostSettlementTimer.stop()
        if (healthTimerInitialized) healthTimer.stop()
        if (!expectedShutdown) {
            onUnrecoverableWindowLoss(
                "Desktop window disappeared without a shutdown request; " +
                    "terminating so the next launch can acquire app.lock."
            )
        }
    }

    private fun scheduleInitialPresentation(reason: String) {
        if (!initialPresentationScheduled.compareAndSet(false, true)) return

        // Let the lifecycle event finish before touching focus/Z-order. windowOpened proves
        // that Compose's real AWT peer reached its opened lifecycle instead of exposing a
        // transient HWND that a generic startup invokeLater could validate too early.
        java.awt.EventQueue.invokeLater {
            if (isShutdownStarted()) return@invokeLater

            if (!presentDesktopWindow()) {
                System.err.println(
                    "[ARES-Analytics] Desktop window was not usable after $reason; " +
                        "the native recovery watchdog is active."
                )
            } else {
                val hwnd = NativeWindowProbe.ownedTopLevelWindow(window)
                println(
                    "[ARES-Analytics] Desktop window presented after $reason: " +
                        "size=${window.size}, location=${window.location}, " +
                        "showing=${window.isShowing}, nativeVisible=true, " +
                        "hwnd=${hwnd?.pointer?.let { com.sun.jna.Pointer.nativeValue(it) }}"
                )
                machine.transitionTo(DesktopStartupState.PRESENTED)
            }
            if (!topmostReleaseTimer.isRunning) {
                topmostReleaseTimer.start()
            }
            ensureHealthTimerStarted()
        }
    }

    /** Compose owns visibility, native peer creation, and always-on-top state; native APIs stay observation-only. */
    private fun presentDesktopWindow(): Boolean = runCatching {
        require(window.isDisplayable && window.isVisible && window.isShowing) {
            "Compose window is not displayable and visible"
        }
        window.toFront()
        window.requestFocus()
        NativeWindowProbe.hasUsableNativeWindow(window)
    }.onFailure {
        System.err.println("[ARES-Analytics] Desktop window presentation failed: ${it.message}")
    }.getOrDefault(false)

    private fun ensureHealthTimerStarted() {
        if (healthTimerInitialized) {
            if (!healthTimer.isRunning) healthTimer.start()
            return
        }
        healthTimerInitialized = true
        healthTimer = Timer(WINDOW_HEALTH_CHECK_MS) {
            if (isShutdownStarted()) {
                (it.source as Timer).stop()
            } else if (!NativeWindowProbe.hasUsableNativeWindow(window)) {
                consecutiveRecoveryFailures++
                System.err.println(
                    "[ARES-Analytics] Native desktop window is missing; " +
                        "recovery attempt $consecutiveRecoveryFailures/$WINDOW_RECOVERY_FAILURE_LIMIT."
                )
                if (presentDesktopWindow()) {
                    machine.recordWindowRecovered(DesktopStartupState.PRESENTED)
                    consecutiveRecoveryFailures = 0
                    val hwnd = NativeWindowProbe.ownedTopLevelWindow(window)
                    println(
                        "[ARES-Analytics] Native desktop window recovered: " +
                            "size=${window.size}, location=${window.location}, " +
                            "showing=${window.isShowing}, " +
                            "hwnd=${hwnd?.pointer?.let { com.sun.jna.Pointer.nativeValue(it) }}"
                    )
                } else if (consecutiveRecoveryFailures >= WINDOW_RECOVERY_FAILURE_LIMIT) {
                    onUnrecoverableWindowLoss(
                        "Native desktop window could not be recovered; " +
                            "terminating so the next launch can acquire app.lock."
                    )
                }
            } else {
                consecutiveRecoveryFailures = 0
            }
        }.apply {
            initialDelay = WINDOW_HEALTH_CHECK_MS
            isRepeats = true
        }
        healthTimer.start()
    }

    /**
     * Captures the real on-screen window only when the desktop test harness explicitly
     * requests it. Keeping this inside the ARES JVM avoids false negatives when separate
     * test-tool processes are assigned different Windows desktops/window stations. Normal
     * application launches do no I/O.
     */
    private fun captureStartupWindowIfRequested(): Boolean {
        val outputPath = System.getenv(STARTUP_CAPTURE_ENV)?.trim().orEmpty()
        if (!DesktopPresentationPolicy.captureRequested(outputPath)) return false

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
    private fun closeCapturedStartupWindowIfRequested(captureSucceeded: Boolean) {
        if (!captureSucceeded ||
            !DesktopPresentationPolicy.closeAfterCaptureRequested(System.getenv(STARTUP_CAPTURE_CLOSE_ENV))
        ) {
            return
        }
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return

        val hwnd = NativeWindowProbe.ownedTopLevelWindow(window)
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
}
