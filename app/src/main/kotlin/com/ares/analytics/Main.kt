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
import com.ares.analytics.desktop.AwtDesktopWindowPort
import com.ares.analytics.desktop.DesktopCrashHandler
import com.ares.analytics.desktop.DesktopInstanceLock
import com.ares.analytics.desktop.DesktopShutdownCoordinator
import com.ares.analytics.desktop.DesktopStartupMachine
import com.ares.analytics.desktop.DesktopWindowPresentationController
import com.ares.analytics.desktop.DesktopWindowCreationWatchdog
import com.ares.analytics.di.ServiceRegistry
import com.ares.analytics.ui.screens.MainScreen
import com.ares.analytics.ui.theme.AresTheme
import com.ares.analytics.ui.theme.rememberAresAppIconPainter
import kotlin.system.exitProcess

/**
 * Composition root for the single-instance Compose desktop application.
 *
 * Lifecycle concerns live in `com.ares.analytics.desktop`: the instance lock, crash policy,
 * shutdown coordination (bounded disposal + hard-exit watchdog), the explicit startup state
 * machine, and the window presentation controller (native verification observation-only;
 * Compose owns visibility and always-on-top state). This file only wires them together.
 */
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

    val instanceLock = DesktopInstanceLock.tryAcquire()
    if (instanceLock == null) {
        System.err.println("[ARES-Analytics] App is already running (failed to acquire app.lock). Exiting.")
        java.lang.System.exit(0)
        return
    }
    Runtime.getRuntime().addShutdownHook(Thread { instanceLock.close() })

    DesktopCrashHandler.install {
        // A crash on the AWT event thread leaves no usable window; a windowless JVM must not
        // keep the single-instance lock alive for the next launch.
        exitProcess(1)
    }

    application {
        val windowState = rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(1440.dp, 900.dp),
        )
        val services = remember { ServiceRegistry() }
        val shutdownScope = rememberCoroutineScope()
        val startupMachine = remember { DesktopStartupMachine() }
        val shutdownCoordinator = remember { DesktopShutdownCoordinator(startupMachine) }
        val creationWatchdog = remember {
            DesktopWindowCreationWatchdog(
                machine = startupMachine,
                onUnrecoverableWindow = DesktopShutdownCoordinator::terminateForUnusableWindow,
            )
        }
        var startupAlwaysOnTop by remember { mutableStateOf(true) }

        // This effect belongs to the application composition rather than Window content. It
        // therefore starts even if Compose never creates the native peer/content composition.
        DisposableEffect(creationWatchdog) {
            creationWatchdog.start()
            onDispose { creationWatchdog.stop() }
        }

        Window(
            onCloseRequest = {
                shutdownCoordinator.requestShutdown(
                    scope = shutdownScope,
                    services = services,
                    exitApplication = ::exitApplication,
                )
            },
            title = "${BuildConfig.PRODUCT_NAME} — Mission Control",
            icon = rememberAresAppIconPainter(),
            state = windowState,
            // Windows may deny a foreground request from a Gradle-launched child process. Let
            // Compose create the window topmost so it is visible even when activation is denied,
            // then release topmost status shortly after the native peer has opened.
            alwaysOnTop = startupAlwaysOnTop,
            visible = true,
        ) {
            DisposableEffect(window) {
                window.minimumSize = java.awt.Dimension(1100, 700)
                val presentationController = DesktopWindowPresentationController(
                    windowPort = AwtDesktopWindowPort(window),
                    machine = startupMachine,
                    isShutdownStarted = shutdownCoordinator::isShutdownStarted,
                    onStartupAlwaysOnTopChange = { startupAlwaysOnTop = it },
                    onFocusLost = services.keyboardDriveState::releaseAll,
                    onUnrecoverableWindowLoss = DesktopShutdownCoordinator::terminateForUnusableWindow,
                )
                presentationController.attach()

                onDispose {
                    presentationController.detach(expectedShutdown = shutdownCoordinator.isShutdownStarted)
                }
            }
            AresTheme {
                MainScreen(services = services)
            }
        }
    }
}
