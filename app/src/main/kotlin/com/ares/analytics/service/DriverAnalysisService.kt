package com.ares.analytics.service

import com.ares.analytics.shared.DriverProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DriverAnalysisService(
    private val databaseService: DatabaseService,
    private val sysIdService: SysIdService,
    private val profilesPath: String = System.getProperty("user.home") + "/.ares-analytics/driver_profiles.json"
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val profiles = ConcurrentHashMap<String, DriverProfile>()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        val file = File(profilesPath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val defaults = listOf(
                DriverProfile("Default Alpha", 1.2, 3.5),
                DriverProfile("Precision Mode", 1.5, 2.0),
                DriverProfile("Aggressive Mode", 1.0, Double.MAX_VALUE)
            )
            file.writeText(json.encodeToString(defaults))
        }

        try {
            val list = json.decodeFromString<List<DriverProfile>>(file.readText())
            list.forEach { profiles[it.name] = it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProfiles(): List<DriverProfile> = profiles.values.toList()

    fun getProfile(name: String): DriverProfile? = profiles[name]

    suspend fun saveProfile(profile: DriverProfile) = withContext(Dispatchers.IO) {
        profiles[profile.name] = profile
        val file = File(profilesPath)
        file.writeText(json.encodeToString(profiles.values.toList()))
    }

    suspend fun deleteProfile(name: String) = withContext(Dispatchers.IO) {
        profiles.remove(name)
        val file = File(profilesPath)
        file.writeText(json.encodeToString(profiles.values.toList()))
    }

    suspend fun exportToConstants(recommendedExponent: Double, recommendedSlewRate: Double, constantsService: ConstantsParserService, projectPath: String): Boolean = withContext(Dispatchers.IO) {
        val constants = constantsService.loadTunableConstants(projectPath)
        
        val deadbandConst = constants.firstOrNull { it.name == "DRIVER_DEADBAND_EXPONENT" }
        val slewRateConst = constants.firstOrNull { it.name == "DRIVER_SLEW_RATE" }
        
        val root = File(projectPath)
        if (!root.exists() || !root.isDirectory) return@withContext false
        
        val targetFile = root.walkTopDown()
            .filter { it.name == "Constants.kt" || it.name == "RobotConfig.java" || it.name == "Constants.java" || it.name == "TunerConstants.kt" || it.name == "TunerConstants.java" }
            .firstOrNull() ?: return@withContext false
            
        val isKotlin = targetFile.name.endsWith(".kt")
        
        if (deadbandConst == null) {
            val line = if (isKotlin) "\nval DRIVER_DEADBAND_EXPONENT = $recommendedExponent" else "\npublic static double DRIVER_DEADBAND_EXPONENT = $recommendedExponent;"
            targetFile.appendText(line)
        } else {
            constantsService.saveConstant(deadbandConst, recommendedExponent)
        }
        
        val slewVal = if (recommendedSlewRate == Double.MAX_VALUE) 999.0 else recommendedSlewRate
        if (slewRateConst == null) {
            val line = if (isKotlin) "\nval DRIVER_SLEW_RATE = $slewVal" else "\npublic static double DRIVER_SLEW_RATE = $slewVal;"
            targetFile.appendText(line)
        } else {
            constantsService.saveConstant(slewRateConst, slewVal)
        }
        
        true
    }

    /**
     * Sweeps gamepad telemetry keys (X, Y, Omega) to detect 8-12Hz jitter and recommends a profile.
     */
    suspend fun analyzeDriverJitter(
        sessionId: String,
        gamepadXKey: String = "/Gamepad1/LeftX",
        gamepadYKey: String = "/Gamepad1/LeftY"
    ): DriverProfileAnalysisResult = withContext(Dispatchers.Default) {
        val xFrames = databaseService.getTelemetryRange(sessionId, 0, Long.MAX_VALUE).filter { it.key == gamepadXKey }
        val yFrames = databaseService.getTelemetryRange(sessionId, 0, Long.MAX_VALUE).filter { it.key == gamepadYKey }

        if (xFrames.size < 64) {
            return@withContext DriverProfileAnalysisResult(
                hasJitter = false,
                peakFrequencyHz = 0.0,
                recommendedExponent = 1.0,
                recommendedSlewRate = Double.MAX_VALUE,
                message = "Insufficient gamepad telemetry data to analyze driver inputs."
            )
        }

        val alignedTimes = xFrames.map { it.timestampMs }.sorted()
        if (alignedTimes.size < 2) {
            return@withContext DriverProfileAnalysisResult(false, 0.0, 1.0, Double.MAX_VALUE, "Time delta calculation failed.")
        }
        val avgDtMs = (alignedTimes.last() - alignedTimes.first()).toDouble() / (alignedTimes.size - 1)
        val sampleRateHz = 1000.0 / avgDtMs

        val xValues = DoubleArray(xFrames.size) { xFrames[it].value }

        // Run FFT
        val fftRes = sysIdService.performFftAnalysis(xValues, sampleRateHz)

        // Check for dominant peak in the 8-12 Hz jitter band
        val isJitterPresent = fftRes.dominantFrequency in 8.0..12.0
        val peakFreq = fftRes.dominantFrequency

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
}

data class DriverProfileAnalysisResult(
    val hasJitter: Boolean,
    val peakFrequencyHz: Double,
    val recommendedExponent: Double,
    val recommendedSlewRate: Double,
    val message: String
)
