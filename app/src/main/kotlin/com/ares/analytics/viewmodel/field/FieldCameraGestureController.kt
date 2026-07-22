package com.ares.analytics.viewmodel.field

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class FieldCameraGestureController {
    var zoomLevel: Float = 1.0f
    var panOffsetX: Float = 0.0f
    var panOffsetY: Float = 0.0f

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun reset() {
        zoomLevel = 1.0f
        panOffsetX = 0.0f
        panOffsetY = 0.0f
    }
}
