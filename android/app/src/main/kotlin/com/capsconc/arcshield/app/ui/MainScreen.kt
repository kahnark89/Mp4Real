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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToLabeler:  () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebrief:  () -> Unit,
    onRequestPermissions: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state         by viewModel.sessionState.collectAsState()
    val candidateCount by viewModel.candidateCount.collectAsState()
    val cameraPreview  by viewModel.cameraPreview.collectAsState()
    val voiceActive    by viewModel.voiceAnnotationActive.collectAsState()
    val logPath        by viewModel.lastSessionLogPath.collectAsState()
    val context        = LocalContext.current

    val isBuilding  = state is SessionViewModel.SessionState.Building
    val isRecording = state is SessionViewModel.SessionState.Recording
    val isFinished  = state is SessionViewModel.SessionState.Finished

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text("ArcShield — Shadow Mode") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            // ── Camera preview ────────────────────────────────────────────
            val showPreview = cameraPreview != null && (isBuilding || isRecording)
            if (showPreview) {
                key(cameraPreview) {
                    val preview = cameraPreview!!
                    DisposableEffect(preview) { onDispose { preview.setSurfaceProvider(null) } }
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                        },
                        update = { it.setSurfaceProvider(preview.surfaceProvider) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                }
            }

            // ── Status card ───────────────────────────────────────────────
            StatusCard(state, candidateCount)

            // ─────────────────────────────────────────────────────────────
            // SESSION CONTROL
            // ─────────────────────────────────────────────────────────────
            SectionLabel("Session Control")

            when (state) {
                is SessionViewModel.SessionState.Idle -> Button(
                    onClick  = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Start Shadow Capture", style = MaterialTheme.typography.labelLarge)
                }

                is SessionViewModel.SessionState.Building -> Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Building I-frame baseline… (~90 s)") }

                is SessionViewModel.SessionState.Recording -> Button(
                    onClick  = { viewModel.stopSession() },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Stop Session", style = MaterialTheme.typography.labelLarge)
                }

                is SessionViewModel.SessionState.Finished -> Button(
                    onClick  = { viewModel.resetToIdle() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("New Session") }

                is SessionViewModel.SessionState.Error -> Button(
                    onClick  = { viewModel.resetToIdle() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Dismiss Error") }
            }

            // ─────────────────────────────────────────────────────────────
            // CAPTURE EVENT
            // ─────────────────────────────────────────────────────────────
            SectionLabel("Capture Event")

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Manual trigger — OPERATOR_INITIATED window, λ=0, logs to NDJSON
                OutlinedButton(
                    onClick  = { viewModel.manualTrigger() },
                    enabled  = isRecording,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Manual Trigger", style = MaterialTheme.typography.labelLarge)
                }

                // Voice note — toggle mic state; Phase 3 wires full audio recording
                Button(
                    onClick  = { viewModel.toggleVoiceAnnotation() },
                    enabled  = isRecording,
                    modifier = Modifier.weight(1f),
                    colors   = if (voiceActive)
                        ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    else
                        ButtonDefaults.buttonColors(),
                ) {
                    Icon(
                        imageVector        = if (voiceActive) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text  = if (voiceActive) "Stop Voice" else "Voice Note",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // DATA INPUT
            // ─────────────────────────────────────────────────────────────
            SectionLabel("Data Input")

            // Manual CIAER+ entry — pre-populate debrief queue with a blank record
            // so operators can annotate events not caught by the LLR gate
            OutlinedButton(
                onClick  = {
                    viewModel.manualTrigger()
                    onNavigateToDebrief()
                },
                enabled  = isRecording,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("New Manual CIAER+ Entry", style = MaterialTheme.typography.labelLarge)
            }

            // ─────────────────────────────────────────────────────────────
            // REVIEW & ANNOTATE
            // ─────────────────────────────────────────────────────────────
            SectionLabel("Review & Annotate")

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick  = onNavigateToDebrief,
                    modifier = Modifier.weight(1f),
                ) {
                    val label = if (candidateCount > 0 && (isRecording || isFinished))
                        "Debrief ($candidateCount)" else "Debrief Queue"
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick  = onNavigateToLabeler,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Shadow Labeler", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ─────────────────────────────────────────────────────────────
            // SESSION DATA  (only visible when a session is active or done)
            // ─────────────────────────────────────────────────────────────
            if (isRecording || isFinished) {
                SectionLabel("Session Data")

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Export log — copies NDJSON path to clipboard for adb pull / file manager
                    OutlinedButton(
                        onClick  = {
                            val path = logPath
                            if (path != null) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("session log", path))
                                Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                                AppLogger.info("Export", "Copied log path: $path")
                            }
                        },
                        enabled  = logPath != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export Log", style = MaterialTheme.typography.labelLarge)
                    }

                    // Import to Debrief Queue — navigates directly to debrief for inline annotation
                    OutlinedButton(
                        onClick  = onNavigateToDebrief,
                        enabled  = candidateCount > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Import to Queue", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // CONSOLE PANEL
            // ─────────────────────────────────────────────────────────────
            SectionLabel("Console")
            ConsolePanel()
        }
    }
}

// ── Supporting composables ─────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusCard(
    state: SessionViewModel.SessionState,
    candidateCount: Int,
) {
    val (label, color) = when (state) {
        is SessionViewModel.SessionState.Idle      -> "IDLE"                        to Color(0xFF616161)
        is SessionViewModel.SessionState.Building  -> "BUILDING BASELINE"           to Color(0xFFF57F17)
        is SessionViewModel.SessionState.Recording -> "SHADOW MODE — RECORDING"     to Color(0xFF1B5E20)
        is SessionViewModel.SessionState.Finished  -> "FINISHED"                    to Color(0xFF0D47A1)
        is SessionViewModel.SessionState.Error     -> "ERROR"                       to Color(0xFFB71C1C)
    }

    Card(
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            }

            if (state is SessionViewModel.SessionState.Recording) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Candidate windows: $candidateCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }

            if (state is SessionViewModel.SessionState.Error) {
                Spacer(Modifier.height(4.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }

            if (state is SessionViewModel.SessionState.Finished) {
                Spacer(Modifier.height(4.dp))
                val meta = state.metadata
                Text(
                    "Session ${meta.sessionId} — ε_sync ${meta.epsSyncNanos / 1_000_000} ms — $candidateCount windows",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
