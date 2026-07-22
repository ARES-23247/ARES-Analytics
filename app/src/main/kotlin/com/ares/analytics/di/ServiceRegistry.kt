package com.ares.analytics.di

import com.ares.analytics.service.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Centralized service registry that lazy-initializes all application services
 * in correct dependency order. Replaces the 20 separate `remember {}` blocks
 * that previously lived in Main.kt.
 *
 * Usage:
 * ```
 * val services = remember { ServiceRegistry() }
 * DisposableEffect(Unit) { onDispose { services.dispose() } }
 * ```
 */
class ServiceRegistry {

    // ── Tier 0: No dependencies ──────────────────────────────────────────────
    val databaseService by lazy { DatabaseService() }
    val environmentService by lazy { EnvironmentService() }
    val firebaseClientService by lazy { FirebaseClientService() }
    val processManagerService by lazy { ProcessManagerService() }
    val targetScannerService by lazy { TargetScannerService() }
    val keybindingParserService by lazy { KeybindingParserService() }
    val simulationService by lazy { SimulationService() }
    val eventApiService by lazy { EventApiService() }
    val layoutPreferenceService by lazy { LayoutPreferenceService() }
    val updateCheckerService by lazy { UpdateCheckerService() }

    // ── Tier 1: Depend on Tier 0 ─────────────────────────────────────────────
    val nt4ClientService by lazy { Nt4ClientService(databaseService) }
    val logParserService by lazy { LogParserService(databaseService, summaryEngineService) }
    val parquetExporterService by lazy { ParquetExporterService(databaseService) }
    val replayEngineService by lazy { ReplayEngineService(databaseService) }
    val sysIdService by lazy { SysIdService(databaseService) }
    val calibrationService by lazy { CalibrationService(databaseService) }
    val oauthService by lazy { OAuthService(firebaseClientService) }
    val exportService by lazy { ExportService(databaseService) }

    // ── Tier 2: Depend on Tier 0 + Tier 1 ────────────────────────────────────
    val alertEngineService by lazy { AlertEngineService(databaseService, nt4ClientService) }
    val driverAnalysisService by lazy { DriverAnalysisService(databaseService, sysIdService) }
    val summaryEngineService by lazy { SummaryEngineService(databaseService, sysIdService, driverAnalysisService) }
    val hootDecoderService by lazy { HootDecoderService(databaseService, summaryEngineService, sysIdService) }
    val googleDriveService by lazy { GoogleDriveService(oauthService, environmentService, firebaseClientService) }
    val teamApiService by lazy { TeamApiService(firebaseClientService) }
    val syncEngineService by lazy { SyncEngineService(databaseService, parquetExporterService, firebaseClientService, environmentService, teamApiService, summaryEngineService, googleDriveService) }
    val phoenixDiagnosticsService by lazy { PhoenixDiagnosticsService(nt4ClientService) }
    val ftcDashboardService by lazy { FtcDashboardService(nt4ClientService) }

    /**
     * Tears down services that hold coroutine scopes or background jobs.
     * Call from `DisposableEffect { onDispose { ... } }`.
     */
    fun dispose() {
        if (lazyFieldInitialized(::updateCheckerService)) {
            updateCheckerService.dispose()
        }
        if (lazyFieldInitialized(::targetScannerService)) {
            targetScannerService.stopScanning()
        }
        // Nt4ClientService.stop() cancels the WebSocket client job
        if (lazyFieldInitialized(::nt4ClientService)) {
            nt4ClientService.stop()
        }
        // ProcessManagerService.shutdown() cancels build, sim, logcat, and ADB monitor jobs
        if (lazyFieldInitialized(::processManagerService)) {
            processManagerService.shutdown()
        }
        // ReplayEngineService.stop() cancels the replay playback job
        if (lazyFieldInitialized(::replayEngineService)) {
            replayEngineService.stop()
        }
        if (lazyFieldInitialized(::phoenixDiagnosticsService)) {
            phoenixDiagnosticsService.dispose()
        }
        if (lazyFieldInitialized(::ftcDashboardService)) {
            ftcDashboardService.dispose()
        }
        if (lazyFieldInitialized(::oauthService)) {
            oauthService.dispose()
        }
        if (lazyFieldInitialized(::syncEngineService)) {
            syncEngineService.close()
        }
        if (lazyFieldInitialized(::teamApiService)) {
            teamApiService.close()
        }
        if (lazyFieldInitialized(::eventApiService)) {
            eventApiService.close()
        }
        if (lazyFieldInitialized(::firebaseClientService)) {
            firebaseClientService.close()
        }
        if (lazyFieldInitialized(::databaseService)) {
            databaseService.close()
        }
        if (lazyFieldInitialized(::gamepadService)) {
            gamepadService.dispose()
        }
    }

    // ── Global Keyboard Drive State ──────────────────────────────────────────
    val keyboardDriveState by lazy { KeyboardDriveState() }

    // ── Gamepad Service ──────────────────────────────────────────────────────
    val gamepadService by lazy { 
        GamepadService().apply { start() } 
    }

    private fun lazyFieldInitialized(prop: kotlin.reflect.KProperty0<*>): Boolean {
        return try {
            (prop.getDelegate() as? Lazy<*>)?.isInitialized() == true
        } catch (t: Throwable) {
            true // Fallback to true so we dispose the service rather than leaking resources/jobs
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class KeyboardDriveState {
    var enabled by androidx.compose.runtime.mutableStateOf(false)
    var useGamepad by androidx.compose.runtime.mutableStateOf(true)
    
    // Left Stick (W/A/S/D)
    var isWPressed by androidx.compose.runtime.mutableStateOf(false)
    var isSPressed by androidx.compose.runtime.mutableStateOf(false)
    var isAPressed by androidx.compose.runtime.mutableStateOf(false)
    var isDPressed by androidx.compose.runtime.mutableStateOf(false)
    
    // Right Stick (Arrow Keys)
    var isUpPressed by androidx.compose.runtime.mutableStateOf(false)
    var isDownPressed by androidx.compose.runtime.mutableStateOf(false)
    var isLeftPressed by androidx.compose.runtime.mutableStateOf(false)
    var isRightPressed by androidx.compose.runtime.mutableStateOf(false)
    
    // Face Buttons (J, L, U, I)
    var isJPressed by androidx.compose.runtime.mutableStateOf(false)
    var isLPressed by androidx.compose.runtime.mutableStateOf(false)
    var isUPressed by androidx.compose.runtime.mutableStateOf(false)
    var isIPressed by androidx.compose.runtime.mutableStateOf(false)
    
    // Bumpers (Q, E)
    var isQPressed by androidx.compose.runtime.mutableStateOf(false)
    var isEPressed by androidx.compose.runtime.mutableStateOf(false)
    
    // Triggers (Space, Shift)
    var isSpacePressed by androidx.compose.runtime.mutableStateOf(false)
    var isShiftPressed by androidx.compose.runtime.mutableStateOf(false)
}
