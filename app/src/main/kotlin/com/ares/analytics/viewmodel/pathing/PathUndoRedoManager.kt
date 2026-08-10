package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.viewmodel.PathPlannerState
import kotlinx.coroutines.flow.MutableStateFlow

/** Bounded snapshot history for path-editor undo and redo operations. */
class PathUndoRedoManager(
    private val stateFlow: MutableStateFlow<PathPlannerState>
) {
    private val undoStack = mutableListOf<PathPlannerState>()
    private val redoStack = mutableListOf<PathPlannerState>()

    fun saveSnapshot() {
        undoStack.add(stateFlow.value)
        redoStack.clear()
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = stateFlow.value
            val previous = undoStack.removeLast()
            redoStack.add(current)
            stateFlow.value = previous
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = stateFlow.value
            val next = redoStack.removeLast()
            undoStack.add(current)
            stateFlow.value = next
        }
    }
}
