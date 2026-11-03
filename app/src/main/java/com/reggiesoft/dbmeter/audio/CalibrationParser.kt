package com.reggiesoft.dbmeter.audio

data class CalibrationPoint(val frequency: Double, val db: Double)

data class CalibrationProfile(
    val fileName: String = "",
    val sensFactor: Double? = null,
    val points: List<CalibrationPoint> = emptyList()
)

fun parseCalibrationFile(content: String, fileName: String = ""): CalibrationProfile {
    var sensFactor: Double? = null
    val points = mutableListOf<CalibrationPoint>()
    val lines = content.lineSequence()

    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isEmpty()) {
            continue
        }
        if (sensFactor == null) {
            val match = SENS_FACTOR_REGEX.find(line)
            if (match != null) {
                sensFactor = match.groupValues[1].toDoubleOrNull()
            }
        }
        if (line.startsWith('#') || line.startsWith(';') || line.startsWith('*') || line.startsWith('"') || line.startsWith("//")) {
            continue
        }
        val parts = line.split(DELIMITER_REGEX).filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val freq = parts[0].toDoubleOrNull()
            val db = parts[1].toDoubleOrNull()
            if (freq != null && freq > 0.0 && db != null) {
                points.add(CalibrationPoint(frequency = freq, db = db))
            }
        }
    }

    if (points.isEmpty()) {
        throw IllegalArgumentException("No valid frequency calibration points found in file")
    }

    val sortedPoints = points.sortedBy { it.frequency }.distinctBy { it.frequency }
    return CalibrationProfile(
        fileName = fileName,
        sensFactor = sensFactor,
        points = sortedPoints
    )
}

fun calibrationCorrectionAt(frequency: Double, points: List<CalibrationPoint>): Double {
    if (points.isEmpty()) {
        return 0.0
    }
    if (frequency <= points.first().frequency) {
        return points.first().db
    }
    if (frequency >= points.last().frequency) {
        return points.last().db
    }
    var low = 0
    var high = points.size - 1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val midFreq = points[mid].frequency
        if (midFreq < frequency) {
            low = mid + 1
        } else if (midFreq > frequency) {
            high = mid - 1
        } else {
            return points[mid].db
        }
    }
    val p0 = points[high]
    val p1 = points[low]
    val fraction = (frequency - p0.frequency) / (p1.frequency - p0.frequency)
    return p0.db + fraction * (p1.db - p0.db)
}

private val SENS_FACTOR_REGEX = Regex("""(?i)(?:Sens\s*Factor|Sensitivity)\s*=?\s*([+-]?\d+(?:\.\d+)?)\s*dB""")
private val DELIMITER_REGEX = Regex("""[,;\s]+""")
