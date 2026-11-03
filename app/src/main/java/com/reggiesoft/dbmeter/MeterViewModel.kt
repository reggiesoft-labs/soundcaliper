package com.reggiesoft.dbmeter

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reggiesoft.dbmeter.audio.AudioCapture
import com.reggiesoft.dbmeter.audio.CaptureEvent
import com.reggiesoft.dbmeter.audio.Measurement
import com.reggiesoft.dbmeter.audio.ProcessingConfig
import com.reggiesoft.dbmeter.audio.ResponseSpeed
import com.reggiesoft.dbmeter.data.SettingsStore
import com.reggiesoft.dbmeter.model.MeterSettings
import com.reggiesoft.dbmeter.model.MeterState
import com.reggiesoft.dbmeter.model.MeterTab
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MeterViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val audio = AudioCapture(application)
    private val _uiState = MutableStateFlow(
        MeterState(
            settings = store.load(),
            permissionGranted = ContextCompat.checkSelfPermission(application, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audio.inputs().distinctUntilChanged().collect { inputs ->
                val previous = _uiState.value
                val selectedPresent = inputs.any { it.key == previous.settings.inputKey }
                if (!selectedPresent) {
                    selectInput("auto")
                }
                _uiState.update { it.copy(inputs = inputs, routeVersion = it.routeVersion + 1, error = "") }
            }
        }
        viewModelScope.launch {
            uiState.map { state ->
                CaptureSession(
                    active = state.visible && state.permissionGranted && !state.paused && state.error.isEmpty(),
                    inputKey = state.settings.inputKey,
                    routeVersion = state.routeVersion
                )
            }.distinctUntilChanged().collectLatest { session ->
                if (session.active) {
                    try {
                        audio.capture(inputKey = session.inputKey, config = ::processingConfig)
                            .buffer(0)
                            .collect { event ->
                                when (event) {
                                    is CaptureEvent.Route -> _uiState.update {
                                        val changed = it.actualInputKey != "auto" && it.actualInputKey != event.key
                                        it.copy(
                                            capturing = true, routeLabel = event.label,
                                            actualInputKey = event.key, unprocessed = event.unprocessed,
                                            resetVersion = if (changed) { it.resetVersion + 1 } else { it.resetVersion },
                                            measurement = if (changed) { Measurement() } else { it.measurement },
                                            hasReading = !changed && it.hasReading
                                        )
                                    }
                                    is CaptureEvent.Reading -> _uiState.update {
                                        it.copy(measurement = event.value, hasReading = true)
                                    }
                                }
                            }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        val stillGranted = ContextCompat.checkSelfPermission(application, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        _uiState.update {
                            it.copy(error = failure.message ?: failure.javaClass.simpleName, capturing = false, permissionGranted = stillGranted)
                        }
                    } finally {
                        _uiState.update { it.copy(capturing = false) }
                    }
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        _uiState.update { it.copy(visible = visible) }
    }

    fun setPermission(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted, error = "") }
    }

    fun selectTab(tab: MeterTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun togglePause() {
        _uiState.update { it.copy(paused = !it.paused) }
    }

    fun retry() {
        _uiState.update { it.copy(error = "", routeVersion = it.routeVersion + 1) }
    }

    fun reset() {
        _uiState.update {
            it.copy(measurement = Measurement(), hasReading = false, resetVersion = it.resetVersion + 1)
        }
    }

    fun setSpeed(speed: ResponseSpeed) {
        saveSettings(_uiState.value.settings.copy(speed = speed))
    }

    fun setKeepAwake(awake: Boolean) {
        saveSettings(_uiState.value.settings.copy(keepAwake = awake))
    }

    fun selectInput(key: String) {
        val current = _uiState.value.settings
        val calibration = store.forInput(key)
        val next = current.copy(
            inputKey = key,
            offsetDb = calibration.offsetDb,
            calibrated = calibration.calibrated,
            calibrationFileName = calibration.calibrationFileName,
            calibrationPoints = calibration.calibrationPoints
        )
        replaceCalibration(next)
    }

    fun calibrate(referenceDb: Double) {
        val state = _uiState.value
        if (!state.capturing || !state.hasReading || state.paused) {
            return
        }
        val key = state.actualInputKey
        val corrected = state.settings.offsetDb + referenceDb - state.measurement.aDb
        replaceCalibration(state.settings.copy(inputKey = key, offsetDb = corrected, calibrated = true))
    }

    fun loadCalibrationContent(fileName: String, content: String) {
        val state = _uiState.value
        val key = if (state.settings.inputKey != "auto") {
            state.settings.inputKey
        } else if (state.actualInputKey.isNotEmpty() && state.actualInputKey != "auto") {
            state.actualInputKey
        } else {
            state.settings.inputKey
        }
        val profile = store.saveCalibrationFile(key = key, fileName = fileName, content = content)
        val offset = if (profile.sensFactor != null) {
            94.0 - profile.sensFactor
        } else {
            state.settings.offsetDb
        }
        val next = state.settings.copy(
            inputKey = key,
            offsetDb = offset,
            calibrated = true,
            calibrationFileName = profile.fileName,
            calibrationPoints = profile.points
        )
        replaceCalibration(next)
    }

    fun loadCalibrationUri(uri: Uri) {
        try {
            val content = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw IOException("Could not read selected file")
            val fileName = queryDisplayName(uri).ifEmpty { "calibration.txt" }
            loadCalibrationContent(fileName = fileName, content = content)
            _uiState.update { it.copy(calibrationError = "") }
        } catch (failure: Exception) {
            _uiState.update { it.copy(calibrationError = failure.message ?: "Invalid calibration file") }
        }
    }

    fun removeCalibrationFile() {
        val key = _uiState.value.settings.inputKey
        store.removeCalibrationFile(key)
        val next = _uiState.value.settings.copy(
            calibrationFileName = "",
            calibrationPoints = emptyList()
        )
        replaceCalibration(next)
    }

    fun clearCalibrationError() {
        _uiState.update { it.copy(calibrationError = "") }
    }

    fun resetCalibration() {
        val key = _uiState.value.settings.inputKey
        store.removeCalibrationFile(key)
        replaceCalibration(
            _uiState.value.settings.copy(
                offsetDb = 110.0,
                calibrated = false,
                calibrationFileName = "",
                calibrationPoints = emptyList()
            )
        )
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = ""
        val cursor = getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = it.getString(index).orEmpty()
                }
            }
        }
        return name
    }

    private fun replaceCalibration(settings: MeterSettings) {
        store.save(settings)
        _uiState.update {
            it.copy(
                settings = settings,
                measurement = Measurement(),
                hasReading = false,
                resetVersion = it.resetVersion + 1,
                routeLabel = "",
                error = "",
                calibrationError = ""
            )
        }
    }

    private fun saveSettings(settings: MeterSettings) {
        store.save(settings)
        _uiState.update { it.copy(settings = settings) }
    }

    private fun processingConfig(): ProcessingConfig {
        val state = _uiState.value
        return ProcessingConfig(
            speed = state.settings.speed,
            offsetDb = state.settings.offsetDb,
            calibrationPoints = state.settings.calibrationPoints,
            resetVersion = state.resetVersion
        )
    }
}

private data class CaptureSession(val active: Boolean, val inputKey: String, val routeVersion: Int)
