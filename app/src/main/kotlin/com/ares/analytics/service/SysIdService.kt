package com.ares.analytics.service

import com.ares.analytics.shared.CalculatedSummary
import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.shared.TransientClassification
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import org.ejml.simple.SimpleMatrix
import kotlin.math.abs
import kotlin.math.sign

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class SysIdService(private val databaseService: DatabaseService) {

    suspend fun analyzeMotorData(
        sessionId: String,
        voltageKey: String,
        velocityKey: String,
        accelerationKey: String
    ): CalculatedSummary {
        val start = 0L
        val end = Long.MAX_VALUE
        val voltages = databaseService.getTelemetryRange(sessionId, start, end).filter { it.key == voltageKey }
        val velocities = databaseService.getTelemetryRange(sessionId, start, end).filter { it.key == velocityKey }
        val accelerations = databaseService.getTelemetryRange(sessionId, start, end).filter { it.key == accelerationKey }

        if (voltages.isEmpty() || velocities.isEmpty() || accelerations.isEmpty()) {
            return CalculatedSummary()
        }

        // Align data by matching timestamps (nearest neighbor or exact matching)
        val timeMap = voltages.associateBy { it.timestampMs }
        val alignedData = mutableListOf<AlignedDataRow>()

        // Identify direction change timestamps (sign of velocity changes)
        val directionChanges = mutableListOf<Long>()
        var lastSign = 0.0
        val sortedVelocities = velocities.sortedBy { it.timestampMs }
        for (v in sortedVelocities) {
            val currentSign = sign(v.value)
            if (currentSign != 0.0 && currentSign != lastSign) {
                directionChanges.add(v.timestampMs)
                lastSign = currentSign
            }
        }
        val sortedAccels = accelerations.sortedBy { it.timestampMs }
        var accelIdx = 0

        for (v in sortedVelocities) {
            val t = v.timestampMs

            // Apply direction change cleansing: skip data points within ±50ms of a sign change
            val isNearDirectionChange = directionChanges.any { abs(it - t) <= 50 }
            if (isNearDirectionChange) continue
            val volt = timeMap[t]?.value ?: continue

            // Move accelIdx forward to find nearest neighbor in O(N + M)
            while (accelIdx < sortedAccels.size - 1 &&
                abs(sortedAccels[accelIdx + 1].timestampMs - t) <= abs(sortedAccels[accelIdx].timestampMs - t)
            ) {
                accelIdx++
            }
            if (sortedAccels.isEmpty()) continue
            val accel = sortedAccels[accelIdx].value

            alignedData.add(AlignedDataRow(t, volt, v.value, accel))
        }

        return analyzeRawData(alignedData)
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun analyzeRawData(alignedData: List<AlignedDataRow>): CalculatedSummary {
        if (alignedData.size < 10) {
            return CalculatedSummary()
        }

        // Solve OLS: V = kS * sgn(v) + kV * v + kA * a
        // Construct matrices
        val n = alignedData.size
        val X = SimpleMatrix(n, 3)
        val y = SimpleMatrix(n, 1)

        for (i in 0 until n) {
            val row = alignedData[i]
            X.setRow(i, 0, sign(row.velocity), row.velocity, row.accel)
            y.set(i, 0, row.voltage)
        }

        return try {
            val Xt = X.transpose()
            val XtX = Xt.mult(X)
            val XtX_inv = XtX.invert()
            val beta = XtX_inv.mult(Xt).mult(y)
            val kS = beta.get(0, 0)
            val kV = beta.get(1, 0)
            val kA = beta.get(2, 0)

            // Compute R-squared
            val yMean = alignedData.map { it.voltage }.average()
            var ssTot = 0.0
            var ssRes = 0.0
            for (i in 0 until n) {
                val actual = y.get(i, 0)
                val predicted = kS * sign(alignedData[i].velocity) + kV * alignedData[i].velocity + kA * alignedData[i].accel
                ssTot += (actual - yMean) * (actual - yMean)
                ssRes += (actual - predicted) * (actual - predicted)
            }
            val rSquared = if (ssTot > 0) 1.0 - (ssRes / ssTot) else 0.0

            // Classify transient response
            val transientClassification = classifyTransient(alignedData)

            CalculatedSummary(
                kS = kS,
                kV = kV,
                kA = kA,
                rSquared = rSquared,
                transientClassification = transientClassification
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CalculatedSummary()
        }
    }

    private fun classifyTransient(data: List<AlignedDataRow>): TransientClassification {
        // Find a step-like voltage increase (e.g. from < 1.0 to > 6.0)
        var stepStartIdx = -1
        for (i in 1 until data.size) {
            if (data[i - 1].voltage < 1.0 && data[i].voltage > 6.0) {
                stepStartIdx = i
                break
            }
        }
        if (stepStartIdx == -1) return TransientClassification.UNKNOWN

        // Trace velocity after step
        val transientPoints = data.subList(stepStartIdx, minOf(stepStartIdx + 30, data.size))
        if (transientPoints.isEmpty()) return TransientClassification.UNKNOWN
        val maxVel = transientPoints.maxOf { it.velocity }
        // Find steady state velocity (average of last 10 points)
        val steadyStateVel = if (transientPoints.size > 10) {
            transientPoints.takeLast(10).map { it.velocity }.average()
        } else {
            transientPoints.last().velocity
        }

        if (steadyStateVel <= 0.0) return TransientClassification.UNKNOWN
        val overshoot = (maxVel - steadyStateVel) / steadyStateVel
        return when {
            overshoot > 0.05 -> TransientClassification.UNDERDAMPED
            overshoot < -0.05 -> TransientClassification.OVERDAMPED // very sluggish
            else -> TransientClassification.CRITICALLY_DAMPED
        }
    }

    /**
     * FFT analysis of a telemetry signal to find dominant frequencies (e.g. vibrations or oscillations).
     */
    fun performFftAnalysis(values: DoubleArray, sampleRateHz: Double): FftResult {
        if (values.size < 4) return FftResult(emptyDoubleArray(), emptyDoubleArray(), 0.0)

        // FFT size must be power of two
        val n = values.size
        val nextPow2 = nextPowerOfTwo(n)
        val padded = DoubleArray(nextPow2)
        System.arraycopy(values, 0, padded, 0, n)
        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val complex = transformer.transform(padded, TransformType.FORWARD)

        // Magnitudes of first half
        val half = nextPow2 / 2
        val frequencies = DoubleArray(half)
        val magnitudes = DoubleArray(half)

        for (i in 0 until half) {
            frequencies[i] = i * sampleRateHz / nextPow2
            magnitudes[i] = complex[i].abs()
        }

        // Find dominant frequency (excluding DC component at index 0)
        var maxMag = 0.0
        var dominantFreq = 0.0
        for (i in 1 until half) {
            if (magnitudes[i] > maxMag) {
                maxMag = magnitudes[i]
                dominantFreq = frequencies[i]
            }
        }

        return FftResult(frequencies, magnitudes, dominantFreq)
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var k = 1
        while (k < n) k = k shl 1
        return k
    }

    private fun emptyDoubleArray() = DoubleArray(0)
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class AlignedDataRow(
    val timestampMs: Long,
    val voltage: Double,
    val velocity: Double,
    val accel: Double
)

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class FftResult(
    val frequencies: DoubleArray,
    val magnitudes: DoubleArray,
    val dominantFrequency: Double
)
