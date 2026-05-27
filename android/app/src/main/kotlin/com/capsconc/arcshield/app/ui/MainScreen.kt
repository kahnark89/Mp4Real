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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToLabeler: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.sessionState.collectAsState()
    val candidateCount by viewModel.candidateCount.collectAsState()
    val cameraPreview by viewModel.cameraPreview.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ArcShield — Shadow Mode") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ---- Camera preview (BUILDING or RECORDING, phone camera only) --
            val showPreview = cameraPreview != null &&
                (state is SessionViewModel.SessionState.Building ||
                 state is SessionViewModel.SessionState.Recording)

            if (showPreview) {
                key(cameraPreview) {
                    val preview = cameraPreview!!
                    DisposableEffect(preview) {
                        onDispose { preview.setSurfaceProvider(null) }
                    }
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                        },
                        update = { previewView ->
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                }
            }

            // ---- Status card --------------------------------------------
            StatusCard(state = state, candidateCount = candidateCount)

            Spacer(Modifier.height(8.dp))

            // ---- Primary action button ----------------------------------
            when (state) {
                is SessionViewModel.SessionState.Idle -> Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Text("  Start Shadow Capture", style = MaterialTheme.typography.labelLarge)
                }

                is SessionViewModel.SessionState.Building -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Building I-frame baseline… (~90 s)")
                }

                is SessionViewModel.SessionState.Recording -> Button(
                    onClick = { viewModel.stopSession() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Text("  Stop Session", style = MaterialTheme.typography.labelLarge)
                }

                is SessionViewModel.SessionState.Finished -> {
                    Button(
                        onClick = { viewModel.resetToIdle() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("New Session") }
                }

                is SessionViewModel.SessionState.Error -> {
                    Button(
                        onClick = { viewModel.resetToIdle() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Dismiss Error") }
                }
            }

            // ---- Labeler navigation -------------------------------------
            OutlinedButton(
                onClick = onNavigateToLabeler,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Shadow-Mode Labeler")
            }

            // ---- Live candidate feed (debug overlay) --------------------
            if (state is SessionViewModel.SessionState.Recording && candidateCount > 0) {
                CandidateFeedCard(count = candidateCount)
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: SessionViewModel.SessionState,
    candidateCount: Int,
) {
    val (label, color) = when (state) {
        is SessionViewModel.SessionState.Idle      -> "IDLE"      to Color(0xFF616161)
        is SessionViewModel.SessionState.Building  -> "BUILDING"  to Color(0xFFF57F17)
        is SessionViewModel.SessionState.Recording -> "SHADOW MODE — RECORDING" to Color(0xFF1B5E20)
        is SessionViewModel.SessionState.Finished  -> "FINISHED"  to Color(0xFF0D47A1)
        is SessionViewModel.SessionState.Error     -> "ERROR"     to Color(0xFFB71C1C)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }

            if (state is SessionViewModel.SessionState.Recording) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Candidate windows logged: $candidateCount",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state is SessionViewModel.SessionState.Error) {
                Spacer(Modifier.height(4.dp))
                Text(
                    (state as SessionViewModel.SessionState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }

            if (state is SessionViewModel.SessionState.Finished) {
                val meta = (state as SessionViewModel.SessionState.Finished).metadata
                Spacer(Modifier.height(4.dp))
                Text(
                    "Session ${meta.sessionId} — ε_sync ${meta.epsSyncNanos / 1_000_000} ms",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CandidateFeedCard(count: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "LLR gate fired — $count candidate window${if (count != 1) "s" else ""} this shift",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Open the Labeler to review and label windows for τ calibration.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
