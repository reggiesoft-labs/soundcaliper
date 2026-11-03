package com.reggiesoft.dbmeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationParserTest {
    @Test
    fun parsesUmikCalibrationFileWithSensFactor() {
        val content = """
            "Sens Factor =-3.215dB, SERNO: 7012345"
            10.000	-2.500
            20.000	-1.000
            100.000	0.200
            1000.000	0.000
            10000.000	1.500
            20000.000	-0.500
        """.trimIndent()

        val profile = parseCalibrationFile(content, "7012345.txt")
        assertEquals("7012345.txt", profile.fileName)
        assertNotNull(profile.sensFactor)
        assertEquals(-3.215, profile.sensFactor!!, 0.0001)
        assertEquals(6, profile.points.size)
        assertEquals(10.0, profile.points.first().frequency, 0.0001)
        assertEquals(-2.5, profile.points.first().db, 0.0001)
        assertEquals(20000.0, profile.points.last().frequency, 0.0001)
        assertEquals(-0.5, profile.points.last().db, 0.0001)
    }

    @Test
    fun parsesRewFormatWithCommentsAndPhase() {
        val content = """
            # REW Calibration File
            # Sensitivity = -12.4 dBFS
            * Microphone Calibration
            20.0 -1.5 15.0
            50.0 -0.5 8.0
            1000.0 0.0 0.0
            20000.0 2.0 -30.0
        """.trimIndent()

        val profile = parseCalibrationFile(content, "mic.cal")
        assertEquals(-12.4, profile.sensFactor!!, 0.0001)
        assertEquals(4, profile.points.size)
    }

    @Test
    fun parsesCommaSeparatedDataWithoutSensFactor() {
        val content = """
            20.0, -1.2
            100.0, 0.1
            1000.0, 0.0
        """.trimIndent()

        val profile = parseCalibrationFile(content, "generic.txt")
        assertNull(profile.sensFactor)
        assertEquals(3, profile.points.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsWhenNoCalibrationDataFound() {
        val content = """
            # Only comments here
            "Sens Factor =-3.215dB"
        """.trimIndent()

        parseCalibrationFile(content, "empty.txt")
    }

    @Test
    fun interpolatesCalibrationPointsLinearly() {
        val points = listOf(
            CalibrationPoint(100.0, -2.0),
            CalibrationPoint(200.0, 2.0)
        )

        assertEquals(-2.0, calibrationCorrectionAt(50.0, points), 0.0001)
        assertEquals(-2.0, calibrationCorrectionAt(100.0, points), 0.0001)
        assertEquals(0.0, calibrationCorrectionAt(150.0, points), 0.0001)
        assertEquals(2.0, calibrationCorrectionAt(200.0, points), 0.0001)
        assertEquals(2.0, calibrationCorrectionAt(300.0, points), 0.0001)
    }
}
