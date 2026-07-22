package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.viewmodel.PathPlannerState
import kotlinx.coroutines.flow.MutableStateFlow

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class PathUndoRedoManager(
    private val stateFlow: MutableStateFlow<PathPlannerState>
) {
    private val undoStack = mutableListOf<PathPlannerState>()
    private val redoStack = mutableListOf<PathPlannerState>()

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun saveSnapshot() {
        undoStack.add(stateFlow.value)
        redoStack.clear()
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = stateFlow.value
            val previous = undoStack.removeLast()
            redoStack.add(current)
            stateFlow.value = previous
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = stateFlow.value
            val next = redoStack.removeLast()
            undoStack.add(current)
            stateFlow.value = next
        }
    }
}
