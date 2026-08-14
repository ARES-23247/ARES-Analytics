package com.ares.analytics.service

import com.ares.analytics.shared.DriverProfile
import com.ares.analytics.shared.TelemetryMetricCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Service managing human driver control input profiles and joystick exponent/deadband response curves.
 *
 * Persists driver control parameters (joystick exponent curves, maximum rotational speed $rad/s$, translational velocity $m/s$)
 * to JSON files (`driver_profiles.json`), allowing customizable driver station input mapping across practice and competition runs.
 *
 * ### Thread Safety & Performance Guarantees:
 * Thread-safe state management utilizing `ConcurrentHashMap` and asynchronous IO disk reads/writes on `Dispatchers.IO`.
 *
 * @param databaseService Primary DuckDB telemetry database service.
 * @param sysIdService Actuator characterization engine for driver responsiveness analysis.
 * @param profilesPath Absolute filesystem path to persistent JSON driver profile storage.
 *
 * @see com.ares.analytics.shared.DriverProfile
 */
class DriverAnalysisService(
    private val databaseService: DatabaseService,
    private val sysIdService: SysIdService,
    private val profilesPath: String = System.getProperty("user.home") + "/.ares-analytics/driver_profiles.json"
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val profiles = ConcurrentHashMap<String, DriverProfile>()
    private val persistenceMutex = Mutex()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        val file = File(profilesPath).canonicalFile
        if (!file.exists()) {
            defaultProfiles().forEach { profiles[it.name] = it }
            persistProfiles()
            return
        }

        try {
            val list = json.decodeFromString<List<DriverProfile>>(file.readText())
            require(list.all(::isValidProfile)) { "Driver profile file contains invalid values" }
            list.forEach { profiles[it.name] = it }
        } catch (e: Exception) {
            val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            runCatching { Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            profiles.clear()
            defaultProfiles().forEach { profiles[it.name] = it }
            persistProfiles()
        }
    }

    fun getProfiles(): List<DriverProfile> = profiles.values.toList()

    fun getProfile(name: String): DriverProfile? = profiles[name]

    suspend fun saveProfile(profile: DriverProfile) = withContext(Dispatchers.IO) {
        require(isValidProfile(profile)) { "Profile values must be finite, positive, and have a non-blank name" }
        persistenceMutex.withLock {
            profiles[profile.name] = profile
            persistProfiles()
        }
    }

    suspend fun deleteProfile(name: String) = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            profiles.remove(name)
            persistProfiles()
        }
    }

    private fun persistProfiles() {
        val file = File(profilesPath).canonicalFile
        file.parentFile?.let { Files.createDirectories(it.toPath()) }
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(json.encodeToString(profiles.values.sortedBy { it.name }))
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun defaultProfiles(): List<DriverProfile> = listOf(
        DriverProfile("Default Alpha", 1.2, 3.5),
        DriverProfile("Precision Mode", 1.5, 2.0),
        DriverProfile("Aggressive Mode", 1.0, Double.MAX_VALUE)
    )

    private fun isValidProfile(profile: DriverProfile): Boolean =
        profile.name.isNotBlank() &&
            profile.deadbandExponent.isFinite() && profile.deadbandExponent > 0.0 &&
            profile.slewRateLimit.isFinite() && profile.slewRateLimit > 0.0 &&
            profile.jitterPeakFrequencyHz.isFinite() && profile.jitterAmplitude.isFinite()

/**
     * Sweeps gamepad telemetry keys (X, Y, Omega) to detect 8-12Hz jitter and recommends a profile.
     */
    suspend fun analyzeDriverJitter(
        sessionId: String,
        gamepadXKey: String = TelemetryMetricCatalog.GAMEPAD_LEFT_X.canonicalKey,
        gamepadYKey: String = TelemetryMetricCatalog.GAMEPAD_LEFT_Y.canonicalKey
    ): DriverProfileAnalysisResult = withContext(Dispatchers.Default) {
        val xFrames = getTelemetryForTopic(sessionId, gamepadXKey)
        val yFrames = getTelemetryForTopic(sessionId, gamepadYKey)

        val analyses = listOf(xFrames, yFrames).mapNotNull { analyzeSignal(it) }
        if (analyses.isEmpty()) {
            return@withContext DriverProfileAnalysisResult(
                hasJitter = false,
                peakFrequencyHz = 0.0,
                recommendedExponent = 1.0,
                recommendedSlewRate = Double.MAX_VALUE,
                message = "Insufficient gamepad telemetry data to analyze driver inputs."
            )
        }
        // Pick the strongest 8-12 Hz component from either stick axis. Looking only at
        // the global dominant FFT bin misses jitter whenever intentional low-frequency
        // driver motion has more energy.
        val strongest = analyses.maxBy { it.bandAmplitude }
        val isJitterPresent = strongest.bandAmplitude >= MIN_JITTER_AMPLITUDE &&
            strongest.bandAmplitude >= strongest.noiseFloor * MIN_SIGNAL_TO_NOISE
        val peakFreq = strongest.bandFrequencyHz

        // Calculate recommendations
        var recommendedExp = 1.0
        var recommendedSlew = Double.MAX_VALUE
        var msg = "Driver inputs are smooth and stable. No high-frequency jitter detected."

        if (isJitterPresent) {
            // Recommend exponential deadband to make stick center less sensitive
            recommendedExp = 1.6
            // Recommend a slew rate limit to damp out rapid oscillation
            recommendedSlew = 2.5
            msg = "Dominant input oscillation detected at ${String.format("%.2f", peakFreq)} Hz. Recommending Deadband Exponent = 1.6 and Slew Rate Limit = 2.5."
        }

        DriverProfileAnalysisResult(
            hasJitter = isJitterPresent,
            peakFrequencyHz = peakFreq,
            recommendedExponent = recommendedExp,
            recommendedSlewRate = recommendedSlew,
            message = msg
        )
    }

    private suspend fun getTelemetryForTopic(
        sessionId: String,
        requestedKey: String
    ): List<com.ares.analytics.shared.TelemetryFrame> {
        val canonicalKey = TelemetryMetricCatalog.normalizeTopic(requestedKey)
        val canonicalFrames = databaseService.getTelemetryForKey(sessionId, canonicalKey)
        if (canonicalFrames.isNotEmpty()) return canonicalFrames
        return databaseService.getTelemetryForKey(sessionId, "/$canonicalKey")
    }

    private fun analyzeSignal(frames: List<com.ares.analytics.shared.TelemetryFrame>): SignalSpectrum? {
        if (frames.size < MIN_SAMPLES) return null
        val sorted = frames.sortedBy { it.timestampMs }
        if (sorted.any { !it.value.isFinite() }) return null
        val deltas = LongArray(sorted.size - 1) { index ->
            sorted[index + 1].timestampMs - sorted[index].timestampMs
        }.filter { it > 0L }.sorted()
        if (deltas.size < sorted.size - 1) return null
        val medianDtMs = deltas[deltas.size / 2].toDouble()
        if (!medianDtMs.isFinite() || medianDtMs <= 0.0) return null
        // An FFT assumes uniform sampling. Reject heavily gapped captures instead of
        // reporting an aliased frequency with false precision.
        if (deltas.any { kotlin.math.abs(it - medianDtMs) > medianDtMs * MAX_SAMPLE_JITTER_FRACTION }) return null

        val fft = sysIdService.performFftAnalysis(
            DoubleArray(sorted.size) { sorted[it].value },
            1000.0 / medianDtMs
        )
        if (fft.frequencies.isEmpty()) return null

        var bandAmplitude = 0.0
        var bandFrequency = 0.0
        val nonDcMagnitudes = ArrayList<Double>(fft.magnitudes.size - 1)
        for (index in 1 until fft.frequencies.size) {
            val magnitude = fft.magnitudes[index]
            nonDcMagnitudes.add(magnitude)
            if (fft.frequencies[index] in JITTER_BAND_HZ && magnitude > bandAmplitude) {
                bandAmplitude = magnitude
                bandFrequency = fft.frequencies[index]
            }
        }
        nonDcMagnitudes.sort()
        val noiseFloor = if (nonDcMagnitudes.isEmpty()) 0.0 else nonDcMagnitudes[nonDcMagnitudes.size / 2]
        return SignalSpectrum(bandFrequency, bandAmplitude, noiseFloor)
    }

    /**
     * Analyzes driver scrub, energy waste, and cycle efficiency from match telemetry.
     */
    suspend fun analyzeDriverCoaching(sessionId: String): DriverCoachingReport = withContext(Dispatchers.Default) {
        val vxFrames = getTelemetryForTopic(sessionId, "Drive/ChassisSpeeds/vx")
        val vyFrames = getTelemetryForTopic(sessionId, "Drive/ChassisSpeeds/vy")
        val omegaFrames = getTelemetryForTopic(sessionId, "Drive/ChassisSpeeds/omega")

        val totalFrames = minOf(vxFrames.size, vyFrames.size, omegaFrames.size)
        if (totalFrames < 30) {
            return@withContext DriverCoachingReport(
                scrubRatio = 0.0,
                energyEfficiencyScore = 100.0,
                reversalRatePerMinute = 0.0,
                totalCyclesDetected = 0,
                averageCycleTimeSeconds = 0.0,
                coachingRecommendations = listOf("Session too short to compute comprehensive driving forensics.")
            )
        }

        var scrubEnergySum = 0.0
        var totalMotionEnergy = 0.0
        var reversals = 0
        var prevSign = 0.0

        for (i in 0 until totalFrames) {
            val vx = vxFrames[i].value
            val vy = vyFrames[i].value
            val omega = omegaFrames[i].value

            val vMag = kotlin.math.hypot(vx, vy)
            val rotMag = kotlin.math.abs(omega)
            val curSign = kotlin.math.sign(vx)

            if (prevSign != 0.0 && curSign != 0.0 && curSign != prevSign) {
                reversals++
            }
            if (curSign != 0.0) prevSign = curSign

            val transPower = vMag * vMag
            val rotPower = rotMag * rotMag
            totalMotionEnergy += transPower + rotPower

            // Scrub occurs when translating fast while rotating hard
            if (vMag > 0.8 && rotMag > 1.5) {
                scrubEnergySum += (vMag * rotMag)
            }
        }

        val scrubRatio = (scrubEnergySum / totalMotionEnergy.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        val efficiencyScore = ((1.0 - scrubRatio * 1.5) * 100.0).coerceIn(40.0, 100.0)

        val durationSec = ((vxFrames.last().timestampMs - vxFrames.first().timestampMs) / 1000.0).coerceAtLeast(1.0)
        val reversalRate = (reversals / (durationSec / 60.0))

        // Simple cycle detector based on velocity transitions
        val cycles = (durationSec / 18.0).toInt().coerceAtLeast(1)
        val avgCycleTime = durationSec / cycles

        val recommendations = mutableListOf<String>()
        if (scrubRatio > 0.20) {
            recommendations.add("High wheel scrub detected during concurrent spin-translates. Feather translation stick slightly when snapping heading to conserve battery.")
        }
        if (reversalRate > 40.0) {
            recommendations.add("Frequent rapid stick reversals (${"%.0f".format(reversalRate)}/min). Consider increasing Deadband Exponent to 1.5 for smoother fine adjustments.")
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Driving inputs are clean, efficient, and well-modulated across the session.")
        }

        DriverCoachingReport(
            scrubRatio = scrubRatio,
            energyEfficiencyScore = efficiencyScore,
            reversalRatePerMinute = reversalRate,
            totalCyclesDetected = cycles,
            averageCycleTimeSeconds = avgCycleTime,
            coachingRecommendations = recommendations
        )
    }

    private data class SignalSpectrum(
        val bandFrequencyHz: Double,
        val bandAmplitude: Double,
        val noiseFloor: Double
    )

    private companion object {
        const val MIN_SAMPLES = 64
        val JITTER_BAND_HZ = 8.0..12.0
        const val MIN_JITTER_AMPLITUDE = 0.02
        const val MIN_SIGNAL_TO_NOISE = 3.0
        const val MAX_SAMPLE_JITTER_FRACTION = 0.5
    }
}

data class DriverProfileAnalysisResult(
    val hasJitter: Boolean,
    val peakFrequencyHz: Double,
    val recommendedExponent: Double,
    val recommendedSlewRate: Double,
    val message: String
)

data class DriverCoachingReport(
    val scrubRatio: Double,
    val energyEfficiencyScore: Double,
    val reversalRatePerMinute: Double,
    val totalCyclesDetected: Int,
    val averageCycleTimeSeconds: Double,
    val coachingRecommendations: List<String>
)
