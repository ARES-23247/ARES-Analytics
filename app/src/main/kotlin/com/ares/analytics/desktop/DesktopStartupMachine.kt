package com.ares.analytics.desktop

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Explicit lifecycle of the single Compose desktop window, replacing the implicit
 * timer/boolean lattice the startup code previously used.
 *
 * CREATING → OPENED → PRESENTED → SETTLED → CLOSING → CLOSED is the happy path.
 * WINDOW_LOST is the only error state and carries a concrete policy: presentation may be
 * re-attempted up to [maxRecoveryAttempts]; beyond that the process must terminate so the
 * single-instance lock cannot be orphaned by a windowless JVM.
 */
enum class DesktopStartupState {
    CREATING,
    OPENED,
    PRESENTED,
    SETTLED,
    WINDOW_LOST,
    CLOSING,
    CLOSED,
}

/**
 * Single-threaded-by-design state machine (all transitions happen on the AWT event thread).
 * Illegal transitions fail loudly: they indicate a lifecycle bug that silent acceptance
 * would turn into another invisible-startup incident.
 */
class DesktopStartupMachine(private val maxRecoveryAttempts: Int = 3) {
    private val stateRef = AtomicReference(DesktopStartupState.CREATING)
    private val recoveryAttempts = AtomicInteger(0)

    val state: DesktopStartupState get() = stateRef.get()
    val isTerminal: Boolean get() = state == DesktopStartupState.CLOSED
    val attemptsUsed: Int get() = recoveryAttempts.get()

    /** True while the window is expected to exist and be usable. */
    val windowExpected: Boolean
        get() = state == DesktopStartupState.OPENED ||
            state == DesktopStartupState.PRESENTED ||
            state == DesktopStartupState.SETTLED

    fun transitionTo(next: DesktopStartupState) {
        val current = stateRef.get()
        require(isLegalTransition(current, next)) {
            "Illegal desktop startup transition: $current -> $next"
        }
        stateRef.set(next)
    }

    /**
     * Records one failed presentation of a window that should exist. Returns true when a
     * recovery attempt is still permitted; false means the policy requires termination.
     */
    fun recordWindowLoss(): Boolean {
        val used = recoveryAttempts.incrementAndGet()
        stateRef.set(DesktopStartupState.WINDOW_LOST)
        return used <= maxRecoveryAttempts
    }

    /** Recovery succeeded: resume from WINDOW_LOST to the state the window was in. */
    fun recordWindowRecovered(recovered: DesktopStartupState) {
        val current = stateRef.get()
        require(
            current == DesktopStartupState.WINDOW_LOST &&
                (recovered == DesktopStartupState.PRESENTED || recovered == DesktopStartupState.SETTLED),
        ) {
            "Illegal desktop window recovery: $current -> $recovered"
        }
        stateRef.set(recovered)
        recoveryAttempts.set(0)
    }

    /** CLOSING is reachable from every non-terminal state: shutdown may start at any point. */
    fun beginClosing() {
        val current = stateRef.get()
        if (current == DesktopStartupState.CLOSING || current == DesktopStartupState.CLOSED) return
        stateRef.set(DesktopStartupState.CLOSING)
    }

    fun markClosed() {
        stateRef.set(DesktopStartupState.CLOSED)
    }

    companion object {
        fun isLegalTransition(current: DesktopStartupState, next: DesktopStartupState): Boolean = when (current) {
            DesktopStartupState.CREATING -> next == DesktopStartupState.OPENED || next == DesktopStartupState.CLOSING
            DesktopStartupState.OPENED -> next == DesktopStartupState.PRESENTED || next == DesktopStartupState.CLOSING
            DesktopStartupState.PRESENTED -> next == DesktopStartupState.SETTLED ||
                next == DesktopStartupState.WINDOW_LOST ||
                next == DesktopStartupState.CLOSING
            DesktopStartupState.SETTLED -> next == DesktopStartupState.WINDOW_LOST || next == DesktopStartupState.CLOSING
            DesktopStartupState.WINDOW_LOST ->
                next == DesktopStartupState.PRESENTED || next == DesktopStartupState.SETTLED || next == DesktopStartupState.CLOSING
            DesktopStartupState.CLOSING -> next == DesktopStartupState.CLOSED
            DesktopStartupState.CLOSED -> false
        }
    }
}
