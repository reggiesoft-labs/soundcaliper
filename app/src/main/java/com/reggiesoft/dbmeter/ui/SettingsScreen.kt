package com.reggiesoft.dbmeter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reggiesoft.dbmeter.R
import com.reggiesoft.dbmeter.model.MeterSettings

@Composable
fun SettingsScreen(
    settings: MeterSettings,
    inputLabel: String,
    onMicrophone: () -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onCalibration: () -> Unit,
    onCalibrationFile: () -> Unit,
    onAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                SettingsChoiceRow(
                    icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                    label = stringResource(R.string.settings_microphone),
                    value = if (settings.inputKey.isAutomaticInput()) {
                        stringResource(R.string.settings_automatic)
                    } else {
                        inputLabel
                    },
                    onClick = onMicrophone
                )
            }
        }

        SettingsSectionLabel(stringResource(R.string.settings_section_display))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            val keepAwakeLabel = stringResource(R.string.settings_keep_awake)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onKeepAwake(!settings.keepAwake) }
                    .semantics { contentDescription = keepAwakeLabel }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_keep_awake), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_keep_awake_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.keepAwake,
                    onCheckedChange = onKeepAwake,
                    modifier = Modifier.semantics {
                        contentDescription = keepAwakeLabel
                    }
                )
            }
        }

        SettingsSectionLabel(stringResource(R.string.settings_section_meter))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCalibrationFile),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_calibration_file), style = MaterialTheme.typography.titleSmall)
                    val fileStatus = if (settings.calibrationFileName.isNotEmpty()) {
                        "${settings.calibrationFileName} (${settings.calibrationPoints.size} pts)"
                    } else {
                        stringResource(R.string.settings_calibration_file_none)
                    }
                    Text(
                        fileStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCalibration),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_calibration), style = MaterialTheme.typography.titleSmall)
                    val calibrationStatus = if (settings.calibrated) {
                        stringResource(R.string.settings_calibrated)
                    } else {
                        stringResource(R.string.settings_not_calibrated)
                    }
                    Text(
                        calibrationStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAbout),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.settings_about), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SettingsChoiceRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(0.88f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            modifier = Modifier
                .weight(1.12f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f), RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 13.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing
    )
}

private fun String.isAutomaticInput() = this == "auto"
