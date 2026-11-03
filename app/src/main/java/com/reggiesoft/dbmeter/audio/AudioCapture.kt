package com.reggiesoft.dbmeter.audio

import android.app.Application
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class AudioInput(val key: String, val label: String)

data class ProcessingConfig(
    val speed: ResponseSpeed = ResponseSpeed.FAST,
    val offsetDb: Double = 110.0,
    val calibrationPoints: List<CalibrationPoint> = emptyList(),
    val resetVersion: Int = 0
)

sealed interface CaptureEvent {
    data class Reading(val value: Measurement) : CaptureEvent
    data class Route(val key: String, val label: String, val unprocessed: Boolean) : CaptureEvent
}

class AudioCapture(application: Application) {
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val processor = SignalProcessor(sampleRate = SAMPLE_RATE, frameSize = FRAME_SIZE)
    private var lastResetVersion = -1

    fun inputs(): Flow<List<AudioInput>> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(audioManager.availableInputs())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(audioManager.availableInputs())
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        trySend(audioManager.availableInputs())
        awaitClose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    fun capture(inputKey: String, config: () -> ProcessingConfig): Flow<CaptureEvent> = flow {
        val selectedInput = if (inputKey == AUTOMATIC_INPUT_KEY) {
            null
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.isSupportedInput() && it.toAudioInput().key == inputKey }
                ?: throw IOException("The selected microphone is no longer available")
        }
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val audioSource = if (unprocessed) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimumBufferSize < 0) {
            throw IOException("Android could not configure microphone capture")
        }
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(maxOf(FRAME_SIZE * BYTES_PER_SAMPLE * 4, minimumBufferSize))
                .build()
        } catch (denied: SecurityException) {
            throw IOException("Microphone permission was denied or revoked", denied)
        }

        try {
            if (selectedInput != null && !recorder.setPreferredDevice(selectedInput)) {
                throw IOException("Android rejected the selected microphone route")
            }
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("Android could not start microphone capture")
            }
            val routedDevice = recorder.awaitRoute(selectedInput)
            val actualInput = routedDevice.toAudioInput()
            if (selectedInput != null && selectedInput.id != routedDevice.id) {
                throw IOException("The requested microphone route is not active")
            }
            emit(CaptureEvent.Route(key = actualInput.key, label = actualInput.label, unprocessed = unprocessed))

            val samples = ShortArray(FRAME_SIZE)
            var filledSamples = 0
            var lastDataTime = SystemClock.elapsedRealtime()
            while (true) {
                currentCoroutineContext().ensureActive()
                val samplesRead = recorder.read(
                    samples,
                    filledSamples,
                    FRAME_SIZE - filledSamples,
                    AudioRecord.READ_NON_BLOCKING
                )
                if (samplesRead < 0) {
                    throw IOException("Microphone capture failed with AudioRecord error $samplesRead")
                }
                if (samplesRead == 0) {
                    if (SystemClock.elapsedRealtime() - lastDataTime > AUDIO_TIMEOUT_MS) {
                        throw IOException("The microphone stopped delivering audio")
                    }
                    delay(10)
                    continue
                }
                lastDataTime = SystemClock.elapsedRealtime()
                filledSamples += samplesRead
                if (filledSamples == FRAME_SIZE) {
                    if (recorder.routedDevice?.id != routedDevice.id) {
                        throw IOException("The active microphone changed. Select an input and retry")
                    }
                    val settings = config()
                    if (settings.resetVersion != lastResetVersion) {
                        processor.reset()
                        processor.setCalibration(settings.calibrationPoints)
                        lastResetVersion = settings.resetVersion
                    }
                    val measurement = processor.process(
                        samples = samples,
                        count = FRAME_SIZE,
                        speed = settings.speed,
                        offsetDb = settings.offsetDb
                    )
                    emit(CaptureEvent.Reading(measurement))
                    filledSamples = 0
                }
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } finally {
                recorder.release()
            }
        }
    }.flowOn(Dispatchers.IO)
}

private fun AudioManager.availableInputs(): List<AudioInput> = getDevices(AudioManager.GET_DEVICES_INPUTS)
    .filter { it.isSupportedInput() }
    .map { it.toAudioInput() }
    .distinctBy { it.key }
    .sortedBy { it.label }
    .let { inputs -> listOf(AUTOMATIC_INPUT) + inputs }

private fun AudioDeviceInfo.isSupportedInput(): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> true
    else -> false
}

private fun AudioDeviceInfo.toAudioInput(): AudioInput {
    val location = when (address.lowercase()) {
        "bottom" -> "Bottom"
        "back" -> "Back"
        "top" -> "Top"
        else -> ""
    }
    val productLabel = productName.toString().trim()
    val defaultLabel = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Microphone"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Microphone"
        else -> "Microphone"
    }
    val baseLabel = productLabel.ifBlank { defaultLabel }
    val label = if (location.isNotEmpty() && !baseLabel.contains(location, ignoreCase = true)) {
        "$baseLabel ($location)"
    } else {
        baseLabel
    }
    return AudioInput(
        key = "$type|$address|$productLabel",
        label = label
    )
}

private const val SAMPLE_RATE = 48_000
private const val FRAME_SIZE = 2_048
private const val BYTES_PER_SAMPLE = 2
private const val AUTOMATIC_INPUT_KEY = "auto"
private val AUTOMATIC_INPUT = AudioInput(AUTOMATIC_INPUT_KEY, "Automatic")

private suspend fun AudioRecord.awaitRoute(preferred: AudioDeviceInfo?): AudioDeviceInfo {
    val route = withTimeoutOrNull(AUDIO_TIMEOUT_MS) {
        var candidate = routedDevice
        while (candidate == null || (preferred != null && candidate.id != preferred.id)) {
            delay(10)
            candidate = routedDevice
        }
        candidate
    }
    return route ?: throw IOException("Android could not activate the requested microphone route")
}

private const val AUDIO_TIMEOUT_MS = 2_000L
