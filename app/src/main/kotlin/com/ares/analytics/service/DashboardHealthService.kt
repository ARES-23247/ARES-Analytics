package com.ares.analytics.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DashboardHealthStatus { HEALTHY, DEGRADED, CRITICAL }

data class DashboardHealthSnapshot(
    val status: DashboardHealthStatus = DashboardHealthStatus.HEALTHY,
    val ingestFramesPerSecond: Double = 0.0,
    val activeTopics: Int = 0,
    val bufferedFrames: Long = 0,
    val droppedFrames: Long = 0,
    val databaseP95Ms: Double = 0.0,
    val databaseMaxMs: Double = 0.0,
    val databaseQueries: Long = 0,
    val replayCacheFrames: Int = 0,
    val replayCacheHitRatio: Double = 0.0,
    val replayTruncatedWindows: Long = 0,
    val reconnects: Long = 0,
    val connected: Boolean = false
)

/** Aggregates the dashboard's own operational metrics into one observable health snapshot. */
class DashboardHealthService(
    private val telemetryStore: TelemetryStore,
    private val databaseMetrics: DatabaseMetrics,
    private val nt4ClientService: Nt4ClientService,
    private val replayEngineService: ReplayEngineService
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutableHealth = MutableStateFlow(DashboardHealthSnapshot())
    val health: StateFlow<DashboardHealthSnapshot> = mutableHealth.asStateFlow()
    private var samplerJob: Job? = null

    init {
        samplerJob = scope.launch {
            var previousFrames = 0L
            var previousSampleNanos = System.nanoTime()
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                val now = System.nanoTime()
                val telemetry = telemetryStore.snapshotMetrics()
                val elapsedSeconds = ((now - previousSampleNanos) / 1_000_000_000.0).coerceAtLeast(0.001)
                val ingestRate = (telemetry.acceptedFrames - previousFrames).coerceAtLeast(0L) / elapsedSeconds
                previousFrames = telemetry.acceptedFrames
                previousSampleNanos = now

                val database = databaseMetrics.snapshot()
                val connection = nt4ClientService.connectionMetrics()
                val replay = replayEngineService.cacheMetrics.value
                val cacheLookups = replay.windowLoads + replay.prefetchHits
                val hitRatio = if (cacheLookups == 0L) 0.0 else replay.prefetchHits.toDouble() / cacheLookups
                val status = when {
                    replay.truncatedWindows > 0 || database.p95QueryMs >= CRITICAL_QUERY_P95_MS -> DashboardHealthStatus.CRITICAL
                    replay.droppedEmissionFrames > 0 || database.p95QueryMs >= DEGRADED_QUERY_P95_MS || connection.reconnects >= DEGRADED_RECONNECTS -> DashboardHealthStatus.DEGRADED
                    else -> DashboardHealthStatus.HEALTHY
                }
                mutableHealth.value = DashboardHealthSnapshot(
                    status = status,
                    ingestFramesPerSecond = ingestRate,
                    activeTopics = telemetry.activeTopics,
                    bufferedFrames = telemetry.bufferedFrames,
                    droppedFrames = replay.droppedEmissionFrames,
                    databaseP95Ms = database.p95QueryMs,
                    databaseMaxMs = database.maxQueryMs,
                    databaseQueries = database.queryCount,
                    replayCacheFrames = replay.cachedFrames,
                    replayCacheHitRatio = hitRatio,
                    replayTruncatedWindows = replay.truncatedWindows,
                    reconnects = connection.reconnects,
                    connected = connection.connected
                )
            }
        }
    }

    fun dispose() {
        samplerJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val DEGRADED_QUERY_P95_MS = 75.0
        const val CRITICAL_QUERY_P95_MS = 250.0
        const val DEGRADED_RECONNECTS = 3L
    }
}
