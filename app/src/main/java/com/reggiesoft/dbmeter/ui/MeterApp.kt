package com.reggiesoft.dbmeter.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reggiesoft.dbmeter.R
import com.reggiesoft.dbmeter.audio.ResponseSpeed
import com.reggiesoft.dbmeter.model.MeterState
import com.reggiesoft.dbmeter.model.MeterTab

@Composable
fun MeterApp(
    state: MeterState,
    onTab: (MeterTab) -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSpeed: (ResponseSpeed) -> Unit,
    onEnable: () -> Unit,
    onRetry: () -> Unit,
    onInput: (String) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onCalibrate: (Double) -> Unit,
    onResetCalibration: () -> Unit,
    onSelectCalibrationUri: (Uri) -> Unit,
    onRemoveCalibrationFile: () -> Unit
) {
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val inputLabel = state.inputs.firstOrNull { it.key == state.settings.inputKey }?.label.orEmpty()
    Scaffold(
        containerColor = BACKGROUND,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = { MeterNavigation(tab = state.tab, onTab = onTab) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (state.measurement.clipping && state.capturing) {
                    Text(
                        stringResource(R.string.clipping),
                        color = CORAL,
                        fontSize = 10.sp
                    )
                }
            }
            if (!state.permissionGranted) {
                CaptureNotice(
                    title = stringResource(R.string.mic_permission_title),
                    body = stringResource(R.string.mic_permission_body),
                    action = stringResource(R.string.enable_microphone),
                    onAction = onEnable
                )
            } else if (state.error.isNotEmpty()) {
                CaptureNotice(
                    title = stringResource(R.string.capture_error),
                    body = state.error,
                    action = stringResource(R.string.retry),
                    onAction = onRetry
                )
            }
            Box(Modifier.weight(1f)) {
                when (state.tab) {
                    MeterTab.METER -> Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DualMeter(state = state, modifier = Modifier.weight(1f).fillMaxWidth())
                        Transport(state = state, onPause = onPause, onReset = onReset, onSpeed = onSpeed)
                        if (state.settings.calibrated) {
                            ReadingNote()
                        }
                    }
                    MeterTab.RTA -> Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SpectrumScreen(state = state, modifier = Modifier.weight(1f).fillMaxWidth())
                        Transport(state = state, onPause = onPause, onReset = onReset, onSpeed = onSpeed)
                        if (state.settings.calibrated) {
                            ReadingNote()
                        }
                    }
                    MeterTab.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        inputLabel = inputLabel,
                        onMicrophone = { sheet = Sheet.MICROPHONE },
                        onKeepAwake = onKeepAwake,
                        onCalibration = { sheet = Sheet.CALIBRATION },
                        onCalibrationFile = { sheet = Sheet.CALIBRATION_FILE },
                        onAbout = { sheet = Sheet.ABOUT }
                    )
                }
            }
        }
    }
    val dismiss = { sheet = Sheet.NONE }
    when (sheet) {
        Sheet.NONE -> Unit
        Sheet.MICROPHONE -> MicrophoneSheet(
            inputs = state.inputs,
            selectedKey = state.settings.inputKey,
            onSelect = onInput,
            onDismiss = dismiss
        )
        Sheet.CALIBRATION -> CalibrationSheet(
            measuredDb = state.measurement.aDb,
            canCalibrate = state.hasReading && state.capturing && !state.paused,
            calibrated = state.settings.calibrated,
            onCalibrate = onCalibrate,
            onReset = onResetCalibration,
            onDismiss = dismiss
        )
        Sheet.CALIBRATION_FILE -> CalibrationFileSheet(
            settings = state.settings,
            error = state.calibrationError,
            onSelectUri = onSelectCalibrationUri,
            onRemove = onRemoveCalibrationFile,
            onDismiss = dismiss
        )
        Sheet.ABOUT -> AboutSheet(onDismiss = dismiss)
    }
}

@Composable
private fun CaptureNotice(title: String, body: String, action: String, onAction: () -> Unit) {
    Surface(color = PANEL, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = TEAL)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MUTED)
                TextButton(onClick = onAction, modifier = Modifier.testTag("capture-action")) { Text(action) }
            }
        }
    }
}

@Composable
private fun MeterNavigation(tab: MeterTab, onTab: (MeterTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(BACKGROUND).navigationBarsPadding().height(72.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (item in MeterTab.entries) {
            val color = if (item == tab) { TEAL } else { MUTED }
            val title = stringResource(when (item) { MeterTab.METER -> R.string.tab_meter; MeterTab.RTA -> R.string.tab_rta; MeterTab.SETTINGS -> R.string.tab_settings })
            TextButton(onClick = { onTab(item) }, modifier = Modifier.testTag("nav-${item.name.lowercase()}"), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(when (item) { MeterTab.METER -> Icons.Outlined.Speed; MeterTab.RTA -> Icons.Outlined.Equalizer; MeterTab.SETTINGS -> Icons.Outlined.Tune }, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(title, fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun Transport(state: MeterState, onPause: () -> Unit, onReset: () -> Unit, onSpeed: (ResponseSpeed) -> Unit) {
    Surface(color = PANEL, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            FilledIconButton(onClick = onReset, enabled = state.hasReading, modifier = Modifier.testTag("transport-reset"), shape = RoundedCornerShape(12.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF252C30), contentColor = MUTED)) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.reset_readings))
            }
            Row(Modifier.background(INSET, RoundedCornerShape(50)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                for (speed in ResponseSpeed.entries) {
                    val selected = state.settings.speed == speed
                    TextButton(
                        onClick = { onSpeed(speed) },
                        modifier = Modifier.height(40.dp).testTag("speed-${speed.name.lowercase()}"),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selected) { Color(0xFF252C30) } else { Color.Transparent },
                            contentColor = if (selected) { TEAL } else { MUTED }
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        val speedLabel = if (speed == ResponseSpeed.FAST) {
                            R.string.fast
                        } else {
                            R.string.slow
                        }
                        Text(stringResource(speedLabel), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            FilledIconButton(onClick = onPause, enabled = state.permissionGranted && state.error.isEmpty(), modifier = Modifier.testTag("transport-pause"), shape = RoundedCornerShape(12.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF20272B), contentColor = TEAL)) {
                val icon = if (state.paused) { Icons.Outlined.PlayArrow } else { Icons.Outlined.Pause }
                val description = if (state.paused) { R.string.resume } else { R.string.pause }
                Icon(icon, contentDescription = stringResource(description))
            }
        }
    }
}

@Composable
private fun ReadingNote() {
    Text(
        stringResource(R.string.calibrated_note),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        fontSize = 10.sp, color = MUTED, textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

private enum class Sheet { NONE, MICROPHONE, CALIBRATION, CALIBRATION_FILE, ABOUT }
