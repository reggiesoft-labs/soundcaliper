package com.reggiesoft.dbmeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SignalProcessorTest {
    private val sampleRate = 48000
    private val frameSize = 2048

    @Test
    fun amplitudeDoublingRaisesLevelBySixDecibels() {
        val quiet = SignalProcessor(sampleRate, frameSize).process(tone(1000.0, 0.2), frameSize, ResponseSpeed.FAST, 110.0)
        val loud = SignalProcessor(sampleRate, frameSize).process(tone(1000.0, 0.4), frameSize, ResponseSpeed.FAST, 110.0)

        assertEquals(6.02, loud.aDb - quiet.aDb, 0.08)
        assertEquals(6.02, loud.cDb - quiet.cDb, 0.08)
    }

    @Test
    fun aAndCWeightingAreApproximatelyUnityAtOneKilohertz() {
        val processor = SignalProcessor(sampleRate, frameSize)
        val samples = tone(1000.0, 0.5)
        var result = processor.process(samples, frameSize, ResponseSpeed.FAST, 110.0)
        repeat(12) {
            result = processor.process(samples, frameSize, ResponseSpeed.FAST, 110.0)
        }

        assertEquals(result.aDb, result.cDb, 0.15)
        assertEquals(110.0 + 20.0 * kotlin.math.log10(0.5 / kotlin.math.sqrt(2.0)), result.aDb, 0.5)
    }

    @Test
    fun lowFrequencyAWeightingAttenuatesMoreThanCWeighting() {
        val result = SignalProcessor(sampleRate, frameSize).process(tone(100.0, 0.5), frameSize, ResponseSpeed.FAST, 110.0)

        assertTrue(result.cDb - result.aDb > 15.0)
    }

    @Test
    fun silenceReturnsFiniteFloorLevels() {
        val result = SignalProcessor(sampleRate, frameSize).process(ShortArray(frameSize), frameSize, ResponseSpeed.FAST, 110.0)

        assertEquals(-10.0, result.aDb, 0.001)
        assertEquals(-10.0, result.cDb, 0.001)
        assertTrue(result.spectrum.all { kotlin.math.abs(it.db + 10.0) < 0.001 })
        assertFalse(result.clipping)
    }

    @Test
    fun flatSignalAndItsSpectrumStayFinite() {
        val result = SignalProcessor(sampleRate, frameSize).process(ShortArray(frameSize) { 12000 }, frameSize, ResponseSpeed.FAST, 0.0)

        assertEquals(-120.0, result.aDb, 0.001)
        assertEquals(-120.0, result.cDb, 0.001)
        assertTrue(result.spectrum.all { it.db.isFinite() })
        assertTrue(result.clipping.not())
    }

    @Test
    fun constantDcOffsetDoesNotChangeWeightedToneLevel() {
        val plain = tone(1000.0, 0.15)
        val offset = ShortArray(frameSize) { index -> (plain[index] + 8000).toShort() }
        val plainResult = settledMeasurement(plain)
        val offsetResult = settledMeasurement(offset)

        assertEquals(plainResult.aDb, offsetResult.aDb, 0.05)
        assertEquals(plainResult.cDb, offsetResult.cDb, 0.05)
    }

    @Test
    fun nearFloorImpulseProducesFiniteSpectrumPeak() {
        val impulse = ShortArray(frameSize)
        impulse[frameSize / 2] = 1
        val result = SignalProcessor(sampleRate, frameSize).process(impulse, frameSize, ResponseSpeed.FAST, 110.0)

        assertTrue(result.aDb.isFinite())
        assertTrue(result.cDb.isFinite())
        assertTrue(result.aPeak.isFinite())
        assertTrue(result.cPeak.isFinite())
        assertTrue(result.aAverage.isFinite())
        assertTrue(result.cAverage.isFinite())
        assertTrue(result.peakFrequency.isFinite())
        assertTrue(result.spectrumPeak.isFinite())
        assertTrue(result.spectrum.all { it.frequency.isFinite() && it.db.isFinite() })
        assertTrue(result.spectrumHold.all { it.frequency.isFinite() && it.db.isFinite() })
    }

    @Test
    fun resetClearsPeakAndCumulativeAverage() {
        val processor = SignalProcessor(sampleRate, frameSize)
        val loud = tone(1000.0, 0.7)
        val first = processor.process(loud, frameSize, ResponseSpeed.FAST, 110.0)
        val silence = ShortArray(frameSize)
        val afterSilence = processor.process(silence, frameSize, ResponseSpeed.FAST, 110.0)
        assertEquals(first.aDb, first.aPeak, 0.001)
        assertEquals(first.aPeak, afterSilence.aPeak, 0.001)

        processor.reset()
        val reset = processor.process(silence, frameSize, ResponseSpeed.FAST, 110.0)

        assertTrue(first.aPeak > reset.aPeak)
        assertEquals(-10.0, reset.aAverage, 0.001)
        assertEquals(-10.0, reset.aPeak, 0.001)
    }

    @Test
    fun fftFindsToneFrequency() {
        val result = SignalProcessor(sampleRate, frameSize).process(tone(1500.0, 0.5), frameSize, ResponseSpeed.FAST, 110.0)

        assertEquals(1500.0, result.peakFrequency, 30.0)
        assertTrue(result.spectrum.isNotEmpty())
        assertTrue(result.spectrum.size < 64)
        assertTrue(result.spectrumPeak > 60.0)
    }

    @Test
    fun spectrumHoldTracksMaximumUntilReset() {
        val processor = SignalProcessor(sampleRate, frameSize)
        val loud = tone(1500.0, 0.6)
        val quiet = tone(1500.0, 0.1)
        val first = processor.process(loud, frameSize, ResponseSpeed.FAST, 110.0)
        val lower = processor.process(quiet, frameSize, ResponseSpeed.FAST, 110.0)

        assertEquals(first.spectrum.size, lower.spectrum.size)
        for (index in lower.spectrum.indices) {
            assertTrue(lower.spectrumHold[index].db >= lower.spectrum[index].db)
            assertTrue(lower.spectrumHold[index].db >= first.spectrumHold[index].db)
        }

        val silence = processor.process(ShortArray(frameSize), frameSize, ResponseSpeed.FAST, 110.0)
        for (index in silence.spectrum.indices) {
            assertEquals(lower.spectrumHold[index].db, silence.spectrumHold[index].db, 0.001)
        }

        processor.reset()
        val reset = processor.process(quiet, frameSize, ResponseSpeed.FAST, 110.0)
        for (index in reset.spectrum.indices) {
            assertEquals(reset.spectrum[index].db, reset.spectrumHold[index].db, 0.001)
        }
    }

    @Test
    fun spectrumBandsPreserveOneSidedSinePowerWithoutDuplicatingBins() {
        val result = SignalProcessor(sampleRate, frameSize).process(tone(1500.0, 0.5), frameSize, ResponseSpeed.FAST, 0.0)
        val spectrumPower = result.spectrum.sumOf { Math.pow(10.0, it.db / 10.0) }

        assertEquals(0.5 * 0.5 / 2.0, spectrumPower, 0.005)
    }

    @Test
    fun fastResponseMovesTowardNewLevelMoreQuicklyThanSlowResponse() {
        val fast = SignalProcessor(sampleRate, frameSize)
        val slow = SignalProcessor(sampleRate, frameSize)
        val signal = tone(1000.0, 0.5)
        val fastResult = fast.process(signal, frameSize, ResponseSpeed.FAST, 110.0)
        val slowResult = slow.process(signal, frameSize, ResponseSpeed.SLOW, 110.0)

        assertTrue(fastResult.aDb > slowResult.aDb)
    }

    @Test
    fun calibrationCurveCorrectsToneLevel() {
        val rawProcessor = SignalProcessor(sampleRate, frameSize)
        val calProcessor = SignalProcessor(sampleRate, frameSize)
        calProcessor.setCalibration(listOf(CalibrationPoint(1000.0, 3.0)))
        val tone = tone(1000.0, 0.4)
        var rawResult = rawProcessor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        var calResult = calProcessor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        repeat(12) {
            rawResult = rawProcessor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
            calResult = calProcessor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        }

        assertEquals(3.0, rawResult.aDb - calResult.aDb, 0.1)
        assertEquals(3.0, rawResult.cDb - calResult.cDb, 0.1)
    }

    @Test
    fun emptyCalibrationRestoresDefaultResponse() {
        val processor = SignalProcessor(sampleRate, frameSize)
        val tone = tone(1000.0, 0.3)
        var uncalibrated = processor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        repeat(12) {
            uncalibrated = processor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        }

        processor.setCalibration(listOf(CalibrationPoint(1000.0, 6.0)))
        repeat(12) {
            processor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        }

        processor.setCalibration(emptyList())
        var restored = processor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        repeat(12) {
            restored = processor.process(tone, frameSize, ResponseSpeed.FAST, 110.0)
        }

        assertEquals(uncalibrated.aDb, restored.aDb, 0.05)
    }

    private fun tone(frequency: Double, amplitude: Double): ShortArray {
        return ShortArray(frameSize) { index ->
            (amplitude * Short.MAX_VALUE * sin(2.0 * PI * frequency * index / sampleRate)).toInt().toShort()
        }
    }

    private fun settledMeasurement(samples: ShortArray): Measurement {
        val processor = SignalProcessor(sampleRate, frameSize)
        var measurement = processor.process(samples, frameSize, ResponseSpeed.FAST, 110.0)
        repeat(12) {
            measurement = processor.process(samples, frameSize, ResponseSpeed.FAST, 110.0)
        }
        return measurement
    }
}
