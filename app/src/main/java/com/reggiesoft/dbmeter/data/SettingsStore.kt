package com.reggiesoft.dbmeter.data

import android.app.Application
import androidx.core.content.edit
import com.reggiesoft.dbmeter.audio.CalibrationProfile
import com.reggiesoft.dbmeter.audio.ResponseSpeed
import com.reggiesoft.dbmeter.audio.parseCalibrationFile
import com.reggiesoft.dbmeter.model.MeterSettings
import java.io.File

class SettingsStore(application: Application) {
    private val preferences = application.getSharedPreferences("meter", Application.MODE_PRIVATE)
    private val filesDir = application.filesDir

    fun load(): MeterSettings {
        val input = preferences.getString("input", "auto").orEmpty()
        return forInput(input).copy(
            speed = if (preferences.getBoolean("slow", false)) {
                ResponseSpeed.SLOW
            } else {
                ResponseSpeed.FAST
            },
            keepAwake = preferences.getBoolean("awake", false)
        )
    }

    fun forInput(key: String): MeterSettings {
        val fileName = preferences.getString("calFile.$key", null)
        val points = if (fileName != null) {
            val file = calFile(key)
            if (file.exists()) {
                try {
                    parseCalibrationFile(file.readText(), fileName).points
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
        return MeterSettings(
            inputKey = key,
            offsetDb = preferences.getFloat("offset.$key", 110f).toDouble(),
            calibrated = preferences.getBoolean("calibrated.$key", false),
            calibrationFileName = fileName.orEmpty(),
            calibrationPoints = points
        )
    }

    fun save(settings: MeterSettings) {
        preferences.edit {
            putString("input", settings.inputKey)
            putBoolean("slow", settings.speed == ResponseSpeed.SLOW)
            putBoolean("awake", settings.keepAwake)
            putFloat("offset.${settings.inputKey}", settings.offsetDb.toFloat())
            putBoolean("calibrated.${settings.inputKey}", settings.calibrated)
        }
    }

    fun saveCalibrationFile(key: String, fileName: String, content: String): CalibrationProfile {
        val profile = parseCalibrationFile(content, fileName)
        calFile(key).writeText(content)
        preferences.edit {
            putString("calFile.$key", fileName)
        }
        return profile
    }

    fun removeCalibrationFile(key: String) {
        val file = calFile(key)
        if (file.exists()) {
            file.delete()
        }
        preferences.edit {
            remove("calFile.$key")
        }
    }

    private fun calFile(key: String): File = File(filesDir, "cal_${key.hashCode().toUInt()}.txt")
}
