package com.reggiesoft.dbmeter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reggiesoft.dbmeter.R
import com.reggiesoft.dbmeter.audio.AudioInput
import com.reggiesoft.dbmeter.model.MeterSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicrophoneSheet(
    inputs: List<AudioInput>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var previewKey by rememberSaveable(selectedKey) { mutableStateOf(selectedKey) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.settings_microphone),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            val canApply = inputs.any { it.key == previewKey }
            if (inputs.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_microphones),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(inputs, key = AudioInput::key) { input ->
                        val selected = input.key == previewKey
                        val inputName = if (input.key.isAutomaticInput()) {
                            stringResource(R.string.settings_automatic)
                        } else {
                            input.label
                        }
                        val inputTint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val inputTextColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { previewKey = input.key }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Mic,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 20.dp),
                                tint = inputTint
                            )
                            Text(
                                inputName,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = inputTextColor
                            )
                            if (selected) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = stringResource(R.string.settings_selected),
                                    modifier = Modifier.padding(end = 20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Spacer(Modifier.width(44.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
            }
            Button(
                onClick = {
                    onSelect(previewKey)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                enabled = canApply
            ) {
                Text(stringResource(R.string.settings_apply))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationSheet(
    measuredDb: Double,
    canCalibrate: Boolean,
    calibrated: Boolean,
    onCalibrate: (Double) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var referenceText by rememberSaveable { mutableStateOf("") }
    val referenceDb = referenceText.replace(',', '.').toDoubleOrNull()
    val validReference = referenceDb != null && referenceDb in 30.0..130.0
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_calibration), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_calibration_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_current_reading), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.1f".format(measuredDb)} dB", style = MaterialTheme.typography.titleMedium)
                }
            }
            OutlinedTextField(
                value = referenceText,
                onValueChange = { referenceText = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_reference_level)) },
                suffix = { Text("dB") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text(stringResource(R.string.settings_reference_range)) }
            )
            if (!canCalibrate) {
                Text(
                    stringResource(R.string.settings_microphone_required),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = { referenceDb?.let(onCalibrate); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canCalibrate && validReference
            ) {
                Text(stringResource(R.string.settings_calibrate))
            }
            if (calibrated) {
                TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.settings_reset_calibration))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationFileSheet(
    settings: MeterSettings,
    error: String,
    onSelectUri: (Uri) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            onSelectUri(uri)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_calibration_file), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_calibration_file_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (error.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (settings.calibrationFileName.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(settings.calibrationFileName, style = MaterialTheme.typography.titleSmall)
                        }
                        if (settings.calibrationPoints.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.settings_cal_points,
                                    settings.calibrationPoints.size,
                                    settings.calibrationPoints.first().frequency,
                                    settings.calibrationPoints.last().frequency
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Button(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_choose_different_file))
                }
                TextButton(
                    onClick = {
                        onRemove()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.settings_remove_file))
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_calibration_file_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_select_file))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.settings_about_version), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_license), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_meter_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.isAutomaticInput() = this == "auto"
