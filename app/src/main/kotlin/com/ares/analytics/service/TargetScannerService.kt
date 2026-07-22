package com.ares.analytics.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class TargetScannerService {
    private val _isLocalSimOnline = MutableStateFlow(false)
    val isLocalSimOnline: StateFlow<Boolean> = _isLocalSimOnline.asStateFlow()

    private val _isLiveRobotOnline = MutableStateFlow(false)
    val isLiveRobotOnline: StateFlow<Boolean> = _isLiveRobotOnline.asStateFlow()

    private var scannerJob: Job? = null

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun startScanning(liveRobotHost: String) {
        scannerJob?.cancel()
        scannerJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                // Check Local Sim
                _isLocalSimOnline.value = checkPort("127.0.0.1", 5810)

                // Check Live Robot
                _isLiveRobotOnline.value = checkPort(liveRobotHost, 5810)

                delay(2000) // Poll every 2 seconds
            }
        }
    }

    private fun checkPort(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 200)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun stopScanning() {
        scannerJob?.cancel()
        scannerJob = null
    }
}
