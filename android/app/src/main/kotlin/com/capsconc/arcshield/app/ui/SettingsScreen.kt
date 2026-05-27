/*
 * Intellectual Property and Trademark Notice
 *
 * mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
 * Company LLC. The multi-track cyber-physical capture architecture, the
 * application of log-likelihood ratio (LLR) gating to multimodal industrial
 * decision events, and the behavioral codebook discretization methods described
 * in this document are the proprietary intellectual property of Kahn Capps and
 * Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
 * implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
 * schemas without explicit licensing is prohibited. All rights reserved.
 */
package com.capsconc.arcshield.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capsconc.arcshield.app.settings.AccelSourceSetting
import com.capsconc.arcshield.app.settings.BiometricSourceSetting
import com.capsconc.arcshield.app.settings.SettingsRepository
import com.capsconc.arcshield.app.settings.VideoSourceSetting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings = viewModel.settings

    val claudeApiKey by settings.claudeApiKey.collectAsState()
    val polarDeviceId by settings.polarDeviceId.collectAsState()
    val biometricSource by settings.biometricSource.collectAsState()
    val videoSource by settings.videoSource.collectAsState()
    val glassesDeviceMac by settings.glassesDeviceMac.collectAsState()
    val accelSource by settings.accelSource.collectAsState()
    val iFrameDurationS by settings.iFrameDurationS.collectAsState()
    val facilityId by settings.facilityId.collectAsState()
    val lineId by settings.lineId.collectAsState()

    var apiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ---- API Keys ------------------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("API KEYS")
            }
            item {
                OutlinedTextField(
                    value = claudeApiKey,
                    onValueChange = { viewModel.setClaudeApiKey(it) },
                    label = { Text("Claude API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                            )
                        }
                    },
                )
                Text(
                    "Key change takes effect on next app start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            // ---- Biometric Source ----------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("BIOMETRIC SOURCE")
            }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BiometricSourceSetting.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = biometricSource == option,
                            onClick = { viewModel.setBiometricSource(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = BiometricSourceSetting.entries.size,
                            ),
                            label = {
                                Text(
                                    when (option) {
                                        BiometricSourceSetting.NONE -> "NONE"
                                        BiometricSourceSetting.POLAR_H10 -> "POLAR H10"
                                        BiometricSourceSetting.POLAR_VERITY_SENSE -> "VERITY SENSE"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
            if (biometricSource != BiometricSourceSetting.NONE) {
                item {
                    OutlinedTextField(
                        value = polarDeviceId,
                        onValueChange = { viewModel.setPolarDeviceId(it) },
                        label = { Text("Polar Device ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    val statusText = if (polarDeviceId.isBlank()) "Not configured"
                        else "Configured – ID: …${polarDeviceId.takeLast(4)}"
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }

            // ---- Video Source --------------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("VIDEO SOURCE")
            }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VideoSourceSetting.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = videoSource == option,
                            onClick = { viewModel.setVideoSource(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = VideoSourceSetting.entries.size,
                            ),
                            label = {
                                Text(
                                    when (option) {
                                        VideoSourceSetting.PHONE_CAMERA -> "PHONE CAMERA"
                                        VideoSourceSetting.GLASSES -> "GLASSES"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
            if (videoSource == VideoSourceSetting.GLASSES) {
                item {
                    OutlinedTextField(
                        value = glassesDeviceMac,
                        onValueChange = { viewModel.setGlassesDeviceMac(it) },
                        label = { Text("Glasses MAC Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    val bonded = viewModel.isGlassesBonded()
                    Text(
                        if (bonded) "Bonded" else "Not bonded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }

            // ---- Accel Source -------------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("ACCEL SOURCE")
            }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AccelSourceSetting.entries.forEachIndexed { index, option ->
                        val isPolarDisabled = option == AccelSourceSetting.POLAR &&
                            biometricSource == BiometricSourceSetting.NONE
                        SegmentedButton(
                            selected = accelSource == option,
                            onClick = { if (!isPolarDisabled) viewModel.setAccelSource(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AccelSourceSetting.entries.size,
                            ),
                            enabled = !isPolarDisabled,
                            label = {
                                Text(
                                    when (option) {
                                        AccelSourceSetting.PHONE_IMU -> "PHONE IMU"
                                        AccelSourceSetting.POLAR -> "POLAR"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            // ---- Session Parameters -------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("SESSION PARAMETERS")
            }
            item {
                Text(
                    "I-frame duration: ${iFrameDurationS} s",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = iFrameDurationS.toFloat(),
                    onValueChange = { viewModel.setIFrameDurationS(it.toInt()) },
                    valueRange = SettingsRepository.MIN_IFRAME_S.toFloat()..SettingsRepository.MAX_IFRAME_S.toFloat(),
                    steps = SettingsRepository.MAX_IFRAME_S - SettingsRepository.MIN_IFRAME_S - 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = facilityId,
                    onValueChange = { viewModel.setFacilityId(it) },
                    label = { Text("Facility ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = lineId,
                    onValueChange = { viewModel.setLineId(it) },
                    label = { Text("Line ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ---- Device Status ------------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("DEVICE STATUS")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusRow(
                        label = "Phone Camera",
                        available = viewModel.phoneCameraAvailable,
                    )
                    StatusRow(
                        label = "Phone Mic",
                        available = viewModel.phoneMicAvailable,
                    )
                    StatusRow(
                        label = "Phone IMU",
                        available = viewModel.phoneImuAvailable,
                    )
                    StatusRow(
                        label = "Polar",
                        available = polarDeviceId.isNotBlank() && biometricSource != BiometricSourceSetting.NONE,
                        trueLabel = "Configured",
                        falseLabel = "Not configured",
                    )
                    StatusRow(
                        label = "Ray-Ban Glasses",
                        available = viewModel.isGlassesBonded(),
                        trueLabel = "Bonded",
                        falseLabel = "Not bonded",
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun StatusRow(
    label: String,
    available: Boolean,
    trueLabel: String = "Available",
    falseLabel: String = "Not available",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (available) Color(0xFF2E7D32) else Color(0xFF757575),
                    CircleShape,
                )
        )
        Text(
            text = "$label: ${if (available) trueLabel else falseLabel}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
