package com.ares.analytics.desktop

import java.awt.Window
import java.awt.event.ComponentListener
import java.awt.event.WindowFocusListener
import java.awt.event.WindowListener
import java.io.File

/**
 * Window-facing operations the presentation controller needs. The production binding
 * targets the real AWT/Compose window; tests substitute a fake so controller sequencing
 * can be verified without native windows, JNA, or a Robot capture.
 *
 * [presentWindow] must keep validating through [NativeWindowProbe]: Compose owns
 * visibility and focus, and the exact-peer native check stays the acceptance criterion.
 */
internal interface DesktopWindowPort {
    /** Registers the AWT listeners owned by the presentation controller. */
    fun attachListeners(
        focusListener: WindowFocusListener,
        lifecycleListener: WindowListener,
        visibilityListener: ComponentListener,
    )

    /** Removes the AWT listeners previously registered by [attachListeners]. */
    fun detachListeners(
        focusListener: WindowFocusListener,
        lifecycleListener: WindowListener,
        visibilityListener: ComponentListener,
    )

    /** Current AWT visibility fragment used by the disposal diagnostic. */
    fun disposalDiagnostics(): String

    /** Observation-only native usability check of the exact Compose/AWT peer. */
    fun isNativeWindowUsable(): Boolean

    /** AWT-level presentation (toFront/requestFocus) followed by the native probe. */
    fun presentWindow(): Boolean

    /** Reads the Compose-owned always-on-top state; never writes it. */
    fun isAlwaysOnTop(): Boolean

    /** Verified native handle for diagnostics, or null when the window cannot be verified. */
    fun nativeWindowHandle(): Long?

    /** `size=..., location=..., showing=...` fragment for presentation diagnostics. */
    fun windowDiagnostics(): String

    /** `alwaysOnTop=..., focused=..., active=..., showing=...` fragment for settlement diagnostics. */
    fun windowFocusDiagnostics(): String

    /** Opt-in same-process startup capture; false unless requested and successful. */
    fun attemptStartupCapture(): Boolean

    /** Opt-in WM_CLOSE to the verified native window after a successful capture. */
    fun postCaptureCloseRequest(captureSucceeded: Boolean)
}

/** Production binding to the real desktop window. */
internal class AwtDesktopWindowPort(private val window: Window) : DesktopWindowPort {
    override fun attachListeners(
        focusListener: WindowFocusListener,
        lifecycleListener: WindowListener,
        visibilityListener: ComponentListener,
    ) {
        window.addWindowFocusListener(focusListener)
        window.addWindowListener(lifecycleListener)
        window.addComponentListener(visibilityListener)
    }

    override fun detachListeners(
        focusListener: WindowFocusListener,
        lifecycleListener: WindowListener,
        visibilityListener: ComponentListener,
    ) {
        window.removeComponentListener(visibilityListener)
        window.removeWindowListener(lifecycleListener)
        window.removeWindowFocusListener(focusListener)
    }

    override fun disposalDiagnostics(): String =
        "displayable=${window.isDisplayable}, visible=${window.isVisible}, showing=${window.isShowing}"

    override fun isNativeWindowUsable(): Boolean = NativeWindowProbe.hasUsableNativeWindow(window)

    /** Compose owns visibility, native peer creation, and always-on-top state; native APIs stay observation-only. */
    override fun presentWindow(): Boolean = runCatching {
        require(window.isDisplayable && window.isVisible && window.isShowing) {
            "Compose window is not displayable and visible"
        }
        window.toFront()
        window.requestFocus()
        NativeWindowProbe.hasUsableNativeWindow(window)
    }.onFailure {
        System.err.println("[ARES-Analytics] Desktop window presentation failed: ${it.message}")
    }.getOrDefault(false)

    override fun isAlwaysOnTop(): Boolean = window.isAlwaysOnTop

    override fun nativeWindowHandle(): Long? =
        NativeWindowProbe.ownedTopLevelWindow(window)?.pointer?.let { com.sun.jna.Pointer.nativeValue(it) }

    override fun windowDiagnostics(): String =
        "size=${window.size}, location=${window.location}, showing=${window.isShowing}"

    override fun windowFocusDiagnostics(): String =
        "alwaysOnTop=${window.isAlwaysOnTop}, focused=${window.isFocused}, " +
            "active=${window.isActive}, showing=${window.isShowing}"

    /**
     * Captures the real on-screen window only when the desktop test harness explicitly
     * requests it. Keeping this inside the ARES JVM avoids false negatives when separate
     * test-tool processes are assigned different Windows desktops/window stations. Normal
     * application launches do no I/O.
     */
    override fun attemptStartupCapture(): Boolean {
        val outputPath = System.getenv(STARTUP_CAPTURE_ENV)?.trim().orEmpty()
        if (!DesktopPresentationPolicy.captureRequested(outputPath)) return false

        return runCatching {
            require(window.isShowing) { "desktop window is not showing" }
            val bounds = window.bounds
            require(bounds.width > 0 && bounds.height > 0) { "desktop window has invalid bounds: $bounds" }
            val outputFile = File(outputPath).absoluteFile
            outputFile.parentFile?.mkdirs()
            val image = run {
                var captured = runCatching {
                    java.awt.Robot(window.graphicsConfiguration.device).createScreenCapture(bounds)
                }.getOrNull()

                fun isAllBlack(img: java.awt.image.BufferedImage): Boolean {
                    for (y in 0 until img.height step 50) {
                        for (x in 0 until img.width step 50) {
                            if ((img.getRGB(x, y) and 0x00FFFFFF) != 0) return false
                        }
                    }
                    return true
                }

                if (captured == null || isAllBlack(captured)) {
                    val offscreen = java.awt.image.BufferedImage(
                        bounds.width.coerceAtLeast(1),
                        bounds.height.coerceAtLeast(1),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB
                    )
                    val g2 = offscreen.createGraphics()
                    try {
                        window.paintAll(g2)
                    } finally {
                        g2.dispose()
                    }
                    offscreen
                } else {
                    captured
                }
            }
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
    override fun postCaptureCloseRequest(captureSucceeded: Boolean) {
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
