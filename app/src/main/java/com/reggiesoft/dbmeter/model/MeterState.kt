package com.reggiesoft.dbmeter.model

import com.reggiesoft.dbmeter.audio.AudioInput
import com.reggiesoft.dbmeter.audio.CalibrationPoint
import com.reggiesoft.dbmeter.audio.Measurement
import com.reggiesoft.dbmeter.audio.ResponseSpeed

enum class MeterTab { METER, RTA, SETTINGS }

data class MeterSettings(
    val speed: ResponseSpeed = ResponseSpeed.FAST,
    val inputKey: String = "auto",
    val keepAwake: Boolean = false,
    val offsetDb: Double = 110.0,
    val calibrated: Boolean = false,
    val calibrationFileName: String = "",
    val calibrationPoints: List<CalibrationPoint> = emptyList()
)

data class MeterState(
    val settings: MeterSettings = MeterSettings(),
    val measurement: Measurement = Measurement(),
    val inputs: List<AudioInput> = emptyList(),
    val tab: MeterTab = MeterTab.METER,
    val visible: Boolean = false,
    val permissionGranted: Boolean = false,
    val paused: Boolean = false,
    val capturing: Boolean = false,
    val hasReading: Boolean = false,
    val routeLabel: String = "",
    val actualInputKey: String = "auto",
    val unprocessed: Boolean = false,
    val error: String = "",
    val calibrationError: String = "",
    val resetVersion: Int = 0,
    val routeVersion: Int = 0
)
