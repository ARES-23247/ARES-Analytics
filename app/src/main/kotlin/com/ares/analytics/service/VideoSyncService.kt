package com.ares.analytics.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class VideoSyncService(private val replayEngineService: ReplayEngineService) {

    private val _videoFile = MutableStateFlow<File?>(null)
    val videoFile: StateFlow<File?> = _videoFile.asStateFlow()

    private val _videoDurationMs = MutableStateFlow(120000L) // Default 2 minutes
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()

    private val _currentVideoTimeMs = MutableStateFlow(0L)
    val currentVideoTimeMs: StateFlow<Long> = _currentVideoTimeMs.asStateFlow()

    // Alignment offset: logTimeMs = videoTimeMs + logOffsetMs
    // Therefore: videoTimeMs = logTimeMs - logOffsetMs
    private val _logOffsetMs = MutableStateFlow(0L)
    val logOffsetMs: StateFlow<Long> = _logOffsetMs.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var syncJob: Job? = null

    init {
        // Sync video time to log replay playhead updates
        syncJob = serviceScope.launch {
            replayEngineService.currentFrame.collect { frame ->
                if (frame != null) {
                    val logTimeMs = frame.timestampMs
                    val calculatedVideoTime = logTimeMs - _logOffsetMs.value
                    _currentVideoTimeMs.value = calculatedVideoTime.coerceIn(0L, _videoDurationMs.value)
                }
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun loadVideo(file: File) {
        _videoFile.value = file
        // Mock video duration based on file size if no metadata decoder is available
        val estimatedDuration = (file.length() / (1024 * 1024) * 2000L).coerceIn(30000L, 300000L)
        _videoDurationMs.value = estimatedDuration
        _currentVideoTimeMs.value = 0L
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun setVideoDuration(durationMs: Long) {
        _videoDurationMs.value = durationMs
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun alignTimestamp(videoTimeMs: Long, logTimeMs: Long) {
        _logOffsetMs.value = logTimeMs - videoTimeMs
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun adjustOffset(deltaMs: Long) {
        _logOffsetMs.value += deltaMs
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun play() {
        replayEngineService.play()
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun pause() {
        replayEngineService.pause()
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun seekVideo(videoTimeMs: Long) {
        val clamped = videoTimeMs.coerceIn(0L, _videoDurationMs.value)
        _currentVideoTimeMs.value = clamped
        
        // Seek log to match this video position
        val targetLogTimeMs = clamped + _logOffsetMs.value
        val timestamps = replayEngineService.currentFrame.value?.timestampMs // fallback or calculate percentage
        
        // Scrub replay engine based on estimated percentage of target log time
        serviceScope.launch {
            val session = replayEngineService.currentFrame.value ?: return@launch
            // We can approximate percentage if we look up loaded timestamps
            // Since replayEngineService.scrubTo uses a percentage:
            // But we can also let scrubTo handle percentage based on total log duration
            // Let's compute percentage using the current frame if available
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun dispose() {
        syncJob?.cancel()
        serviceScope.cancel()
    }
}
