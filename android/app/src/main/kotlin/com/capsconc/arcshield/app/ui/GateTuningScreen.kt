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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capsconc.arcshield.app.settings.SettingsRepository
import com.capsconc.arcshield.llr.CandidateWindow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateTuningScreen(
    onNavigateBack:  () -> Unit,
    latestWindowFlow: StateFlow<CandidateWindow?>,
    vm: GateTuningViewModel = hiltViewModel(),
) {
    val s = vm.settings
    val tau          by s.gateTau.collectAsState()
    val acousticOn   by s.acousticEnabled.collectAsState()
    val accelOn      by s.accelEnabled.collectAsState()
    val motionOn     by s.motionEnabled.collectAsState()
    val gazeOn       by s.gazeEnabled.collectAsState()
    val hrOn         by s.hrEnabled.collectAsState()
    val rmssdOn      by s.rmssdEnabled.collectAsState()
    val hrvNlOn      by s.hrvNlEnabled.collectAsState()
    val lightMg      by s.lightAccelThresholdMg.collectAsState()
    val moderateMg   by s.moderateAccelThresholdMg.collectAsState()
    val vigorousMg   by s.vigorousAccelThresholdMg.collectAsState()
    val lightFactor  by s.lightGateFactor.collectAsState()
    val modFactor    by s.moderateGateFactor.collectAsState()
    val vigFactor    by s.vigorousGateFactor.collectAsState()
    val gazeBase     by s.gazeDwellBaselineSec.collectAsState()
    val gazeVar      by s.gazeDwellVarianceSec.collectAsState()
    val window       by latestWindowFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gate Tuning") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Live Λ breakdown ─────────────────────────────────────────
            GateSection("Live Λ Breakdown") {
                if (window == null) {
                    Text(
                        "No active session — values show last captured window",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LambdaBreakdownCard(window!!)
                }
            }

            HorizontalDivider()

            // ── Detection threshold ──────────────────────────────────────
            GateSection("Detection Threshold τ") {
                Text(
                    "Fire when Λ ≥ τ. In shadow mode all events are logged regardless.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value         = tau,
                        onValueChange = s::setGateTau,
                        valueRange    = SettingsRepository.MIN_GATE_TAU..SettingsRepository.MAX_GATE_TAU,
                        steps         = 99,
                        modifier      = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${"%.1f".format(tau)}", style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                }
            }

            HorizontalDivider()

            // ── Environmental channels (Λ_env) ────────────────────────────
            GateSection("Λ_env — Environmental Channels") {
                ChannelRow(
                    label        = "Acoustic",
                    description  = "Sound spectrum KL-divergence vs shift-start baseline. Extruder pitch, die hiss, melt sound.",
                    enabled      = acousticOn,
                    onToggle     = s::setAcousticEnabled,
                    lambdaValue  = window?.lambdaAcoustic,
                    sourceStatus = "MIC",
                )
                Spacer(Modifier.height(8.dp))
                ChannelRow(
                    label        = "Accel / Vibration",
                    description  = "Mechanical vibration RMS vs baseline. Screw load, motor coupling, barrel resonance.",
                    enabled      = accelOn,
                    onToggle     = s::setAccelEnabled,
                    lambdaValue  = window?.lambdaAccel,
                    sourceStatus = "IMU",
                )
                Spacer(Modifier.height(8.dp))
                ChannelRow(
                    label        = "Motion",
                    description  = "Frame-to-frame visual scene change (MAD). Operator movement, material flow, steam/smoke.",
                    enabled      = motionOn,
                    onToggle     = s::setMotionEnabled,
                    lambdaValue  = window?.lambdaMotion,
                    sourceStatus = "CAM",
                )
                Spacer(Modifier.height(8.dp))
                ChannelRow(
                    label        = "Gaze Dwell",
                    description  = "Sustained fixation duration vs baseline. Expert locks onto anomaly — most direct perceptual signal. Requires eye-tracking HW (Meta Ray-Bans Gen 2, Phase 2+).",
                    enabled      = gazeOn,
                    onToggle     = s::setGazeEnabled,
                    lambdaValue  = window?.lambdaGaze,
                    sourceStatus = "NO HW",
                    statusColor  = if (gazeOn) Color(0xFFF57F17) else null,
                )
                if (gazeOn) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Gaze baseline (s) — normal scan dwell below which Λ_gaze = 0", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(value = gazeBase, onValueChange = s::setGazeDwellBaselineSec, valueRange = 0f..5f, steps = 49, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text("${"%.1f".format(gazeBase)}s", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
                            }
                            Text("Gaze variance (s²) — σ controls how sharply Λ_gaze rises with dwell", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(value = gazeVar, onValueChange = s::setGazeDwellVarianceSec, valueRange = 0.1f..10f, steps = 98, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text("${"%.1f".format(gazeVar)}s²", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Biometric channels (Λ_bio) ────────────────────────────────
            GateSection("Λ_bio — Biometric Channels") {
                Text(
                    "All bio channels require Polar H10/Verity Sense connected with a captured I-frame baseline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ChannelRow(
                    label        = "Heart Rate",
                    description  = "HR deviation from 5-min rolling baseline. Sympathetic activation — fastest autonomic response to perceived anomaly (~10s).",
                    enabled      = hrOn,
                    onToggle     = s::setHrEnabled,
                    lambdaValue  = null,
                    sourceStatus = "POLAR",
                )
                Spacer(Modifier.height(8.dp))
                ChannelRow(
                    label        = "RMSSD",
                    description  = "Root mean square successive R-R differences. Parasympathetic withdrawal under cognitive load. More specific than HR, lags ~30s.",
                    enabled      = rmssdOn,
                    onToggle     = s::setRmssdEnabled,
                    lambdaValue  = null,
                    sourceStatus = "POLAR",
                )
                Spacer(Modifier.height(8.dp))
                ChannelRow(
                    label        = "HRV Nonlinear (SD1/SD2/SampEn)",
                    description  = "Poincaré scatter and sample entropy. SampEn drops when R-R sequence becomes more regular under stress. H10 only (raw ECG → precise R-R). Requires ≥20 R-R intervals.",
                    enabled      = hrvNlOn,
                    onToggle     = s::setHrvNlEnabled,
                    lambdaValue  = null,
                    sourceStatus = "H10 ONLY",
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Note: Λ_bio component values are folded into the total. Individual HR/RMSSD/HRV-NL breakdowns added in a future update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // ── Activity gate ─────────────────────────────────────────────
            GateSection("Activity Gate") {
                val actClass = window?.let { w ->
                    when {
                        w.activityGate <= 0.15f -> "VIGOROUS  (Λ_bio × ${"%.1f".format(w.activityGate)})"
                        w.activityGate <= 0.45f -> "MODERATE  (Λ_bio × ${"%.1f".format(w.activityGate)})"
                        w.activityGate <= 0.85f -> "LIGHT     (Λ_bio × ${"%.1f".format(w.activityGate)})"
                        else                    -> "RESTING   (Λ_bio × 1.0)"
                    }
                } ?: "—"
                Text(
                    "Current: $actClass",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Phone-accel RMS thresholds and Λ_bio scale factors. High exertion confounds HR/HRV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))

                ActivityGateSlider("Light threshold (mG)", lightMg, 50f..500f, s::setLightAccelThresholdMg)
                ActivityGateSlider("Moderate threshold (mG)", moderateMg, 200f..1000f, s::setModerateAccelThresholdMg)
                ActivityGateSlider("Vigorous threshold (mG)", vigorousMg, 500f..2000f, s::setVigorousAccelThresholdMg)

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                ActivityFactorSlider("Light gate factor", lightFactor, s::setLightGateFactor)
                ActivityFactorSlider("Moderate gate factor", modFactor, s::setModerateGateFactor)
                ActivityFactorSlider("Vigorous gate factor", vigFactor, s::setVigorousGateFactor)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Settings take effect on the next session start.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Supporting composables ─────────────────────────────────────────────────

@Composable
private fun GateSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ChannelRow(
    label:        String,
    description:  String,
    enabled:      Boolean,
    onToggle:     (Boolean) -> Unit,
    lambdaValue:  Float?,
    sourceStatus: String,
    statusColor:  Color? = null,
) {
    val dotColor = statusColor ?: if (enabled) Color(0xFF2E7D32) else Color(0xFF616161)
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    "[$sourceStatus]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                if (lambdaValue != null) {
                    Text(
                        "Λ=${"%.3f".format(lambdaValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (lambdaValue > 0.01f) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LambdaBreakdownCard(w: CandidateWindow) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LambdaRow("Λ total",    w.lambda,       highlight = true)
            LambdaRow("  Λ_env",   w.lambdaEnv,    indent = false)
            LambdaRow("    Λ_acoustic", w.lambdaAcoustic)
            LambdaRow("    Λ_accel",    w.lambdaAccel)
            LambdaRow("    Λ_motion",   w.lambdaMotion)
            LambdaRow("    Λ_gaze",     w.lambdaGaze)
            LambdaRow("  Λ_bio",   w.lambdaBio,    indent = false)
            LambdaRow("    activity gate", w.activityGate)
        }
    }
}

@Composable
private fun LambdaRow(label: String, value: Float, highlight: Boolean = false, indent: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style     = if (highlight) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color     = Color(0xFF8B949E),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "%.4f".format(value),
            style     = if (highlight) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color     = if (highlight) Color(0xFF58A6FF) else if (value > 0.001f) Color(0xFF3FB950) else Color(0xFF8B949E),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ActivityGateSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onSet: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(180.dp))
        Slider(value = value, onValueChange = onSet, valueRange = range, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text("${value.toInt()}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun ActivityFactorSlider(label: String, value: Float, onSet: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(180.dp))
        Slider(value = value, onValueChange = onSet, valueRange = 0f..1f, steps = 19, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text("${"%.2f".format(value)}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
    }
}
