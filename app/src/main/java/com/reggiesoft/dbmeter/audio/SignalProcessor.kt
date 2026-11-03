package com.reggiesoft.dbmeter.audio

import kotlin.math.pow

private const val SPECTRUM_BINS = 64
private const val FLOOR_DB = -120.0
private const val FLOOR_POWER = 1.0e-12
private const val LN_TEN = 2.302585092994046

enum class ResponseSpeed {
    FAST,
    SLOW
}

data class SpectrumPoint(val frequency: Double, val db: Double)

data class Measurement(
    val aDb: Double = 0.0,
    val cDb: Double = 0.0,
    val aPeak: Double = 0.0,
    val cPeak: Double = 0.0,
    val aAverage: Double = 0.0,
    val cAverage: Double = 0.0,
    val spectrum: List<SpectrumPoint> = emptyList(),
    val spectrumHold: List<SpectrumPoint> = emptyList(),
    val peakFrequency: Double = 0.0,
    val spectrumPeak: Double = 0.0,
    val clipping: Boolean = false
)

class SignalProcessor(
    val sampleRate: Int = 48000,
    val frameSize: Int = 2048
) {
    private val real = DoubleArray(frameSize)
    private val imaginary = DoubleArray(frameSize)
    private val window = DoubleArray(frameSize)
    private val binPower = DoubleArray(frameSize / 2 + 1)
    private val aPowerWeight = DoubleArray(frameSize / 2 + 1)
    private val cPowerWeight = DoubleArray(frameSize / 2 + 1)
    private val calPowerWeight = DoubleArray(frameSize / 2 + 1) { 1.0 }
    private val twiddleReal = DoubleArray(frameSize / 2)
    private val twiddleImaginary = DoubleArray(frameSize / 2)
    private val spectrumBin = IntArray(frameSize / 2 + 1) { -1 }
    private val spectrumCenters = DoubleArray(SPECTRUM_BINS)
    private val spectrumPower = DoubleArray(SPECTRUM_BINS)
    private val spectrumHoldPower = DoubleArray(SPECTRUM_BINS)
    private val spectrumBands: IntArray
    private val windowPower: Double
    private var smoothedAPower = 0.0
    private var smoothedCPower = 0.0
    private var totalAPower = 0.0
    private var totalCPower = 0.0
    private var totalSamples = 0L
    private var peakA = FLOOR_POWER
    private var peakC = FLOOR_POWER

    init {
        val spectrumBinCount = IntArray(SPECTRUM_BINS)
        val spectrumMaximumFrequencies = DoubleArray(SPECTRUM_BINS)
        for (index in 0 until frameSize) {
            window[index] = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * index / (frameSize - 1))
        }
        windowPower = window.sumOf { it * it }
        for (bin in binPower.indices) {
            val frequency = bin.toDouble() * sampleRate / frameSize
            aPowerWeight[bin] = dbToPower(aWeighting(frequency))
            cPowerWeight[bin] = dbToPower(cWeighting(frequency))
            if (frequency in 20.0..20000.0) {
                val band = (ln(frequency / 20.0) / ln(1000.0) * SPECTRUM_BINS)
                    .toInt()
                    .coerceAtMost(SPECTRUM_BINS - 1)
                spectrumBin[bin] = band
                spectrumBinCount[band]++
                if (spectrumBinCount[band] == 1) {
                    spectrumCenters[band] = frequency
                }
                spectrumMaximumFrequencies[band] = frequency
            }
        }
        for (band in 0 until SPECTRUM_BINS) {
            if (spectrumBinCount[band] > 0) {
                spectrumCenters[band] = kotlin.math.sqrt(spectrumCenters[band] * spectrumMaximumFrequencies[band])
            }
        }
        spectrumBands = (0 until SPECTRUM_BINS).filter { spectrumBinCount[it] > 0 }.toIntArray()
        for (index in twiddleReal.indices) {
            val angle = -2.0 * Math.PI * index / frameSize
            twiddleReal[index] = kotlin.math.cos(angle)
            twiddleImaginary[index] = kotlin.math.sin(angle)
        }
    }

    fun process(samples: ShortArray, count: Int, speed: ResponseSpeed, offsetDb: Double): Measurement {
        var clipping = false
        var mean = 0.0
        for (index in 0 until count) {
            val sample = samples[index].toInt()
            if (kotlin.math.abs(sample) >= 32760) {
                clipping = true
            }
            mean += sample / 32768.0
        }
        mean /= count
        for (index in 0 until count) {
            real[index] = (samples[index] / 32768.0 - mean) * window[index]
            imaginary[index] = 0.0
        }

        transform()

        var aPower = 0.0
        var cPower = 0.0
        var strongestPower = 0.0
        var strongestBin = 0
        spectrumPower.fill(0.0)
        for (bin in binPower.indices) {
            val magnitude = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            val oneSidedPower = magnitude / (frameSize * windowPower) * (if (bin == 0 || bin == frameSize / 2) { 1.0 } else { 2.0 }) * calPowerWeight[bin]
            binPower[bin] = oneSidedPower
            aPower += oneSidedPower * aPowerWeight[bin]
            cPower += oneSidedPower * cPowerWeight[bin]
            val band = spectrumBin[bin]
            if (band >= 0) {
                spectrumPower[band] += oneSidedPower
            }
            if (oneSidedPower > strongestPower) {
                strongestPower = oneSidedPower
                strongestBin = bin
            }
        }

        val elapsedSeconds = count.toDouble() / sampleRate
        val timeConstant = when (speed) {
            ResponseSpeed.FAST -> 0.125
            ResponseSpeed.SLOW -> 1.0
        }
        val smoothing = 1.0 - kotlin.math.exp(-elapsedSeconds / timeConstant)
        smoothedAPower += (aPower - smoothedAPower) * smoothing
        smoothedCPower += (cPower - smoothedCPower) * smoothing
        totalAPower += aPower * count
        totalCPower += cPower * count
        totalSamples += count
        peakA = maxOf(peakA, smoothedAPower)
        peakC = maxOf(peakC, smoothedCPower)

        val spectrum = ArrayList<SpectrumPoint>(spectrumBands.size)
        val spectrumHold = ArrayList<SpectrumPoint>(spectrumBands.size)
        for (index in spectrumBands) {
            spectrumHoldPower[index] = maxOf(spectrumHoldPower[index], spectrumPower[index])
            val frequency = spectrumCenters[index]
            spectrum.add(SpectrumPoint(frequency, level(spectrumPower[index], offsetDb)))
            spectrumHold.add(SpectrumPoint(frequency, level(spectrumHoldPower[index], offsetDb)))
        }

        val interpolatedBin = if (strongestBin in 1 until binPower.lastIndex) {
            val left = peakLog(binPower[strongestBin - 1])
            val middle = peakLog(binPower[strongestBin])
            val right = peakLog(binPower[strongestBin + 1])
            val curvature = left - 2.0 * middle + right
            if (curvature < 0.0) {
                strongestBin + 0.5 * (left - right) / curvature
            } else {
                strongestBin.toDouble()
            }
        } else {
            strongestBin.toDouble()
        }
        val peakFrequency = interpolatedBin * sampleRate / frameSize

        return Measurement(
            aDb = level(smoothedAPower, offsetDb),
            cDb = level(smoothedCPower, offsetDb),
            aPeak = level(peakA, offsetDb),
            cPeak = level(peakC, offsetDb),
            aAverage = level(totalAPower / totalSamples, offsetDb),
            cAverage = level(totalCPower / totalSamples, offsetDb),
            spectrum = spectrum,
            spectrumHold = spectrumHold,
            peakFrequency = peakFrequency,
            spectrumPeak = level(binPower[strongestBin], offsetDb),
            clipping = clipping
        )
    }

    fun setCalibration(points: List<CalibrationPoint>) {
        if (points.isEmpty()) {
            calPowerWeight.fill(1.0)
            return
        }
        for (bin in calPowerWeight.indices) {
            val frequency = bin.toDouble() * sampleRate / frameSize
            val correctionDb = calibrationCorrectionAt(frequency, points)
            calPowerWeight[bin] = dbToPower(-correctionDb)
        }
    }

    fun reset() {
        smoothedAPower = 0.0
        smoothedCPower = 0.0
        totalAPower = 0.0
        totalCPower = 0.0
        totalSamples = 0L
        peakA = FLOOR_POWER
        peakC = FLOOR_POWER
        spectrumHoldPower.fill(0.0)
    }

    private fun transform() {
        var j = 0
        for (i in 1 until frameSize) {
            var bit = frameSize shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }

        var length = 2
        while (length <= frameSize) {
            val twiddleStep = frameSize / length
            for (start in 0 until frameSize step length) {
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val factorReal = twiddleReal[offset * twiddleStep]
                    val factorImaginary = twiddleImaginary[offset * twiddleStep]
                    val oddReal = real[odd] * factorReal - imaginary[odd] * factorImaginary
                    val oddImaginary = real[odd] * factorImaginary + imaginary[odd] * factorReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                }
            }
            length *= 2
        }
    }

    private fun aWeighting(frequency: Double): Double {
        val squared = frequency * frequency
        val numerator = 12194.0 * 12194.0 * squared * squared
        val denominator = (squared + 20.6 * 20.6) * kotlin.math.sqrt((squared + 107.7 * 107.7) * (squared + 737.9 * 737.9)) * (squared + 12194.0 * 12194.0)
        return 20.0 * log10(numerator / denominator) + 2.0
    }

    private fun cWeighting(frequency: Double): Double {
        val squared = frequency * frequency
        val numerator = 12194.0 * 12194.0 * squared
        val denominator = (squared + 20.6 * 20.6) * (squared + 12194.0 * 12194.0)
        return 20.0 * log10(numerator / denominator) + 0.06
    }

    private fun level(power: Double, offsetDb: Double): Double {
        return maxOf(FLOOR_DB + offsetDb, 10.0 * log10(power.coerceAtLeast(FLOOR_POWER)) + offsetDb)
    }

    private fun dbToPower(db: Double): Double = Math.pow(10.0, db / 10.0)

    private fun log10(value: Double): Double = kotlin.math.ln(value) / LN_TEN

    private fun ln(value: Double): Double = kotlin.math.ln(value.coerceAtLeast(FLOOR_POWER))

    private fun peakLog(power: Double): Double = kotlin.math.ln(power.coerceAtLeast(Double.MIN_VALUE))

}
