package com.capsconc.arcshield.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToLabeler: () -> Unit,
    onRequestPermissions: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.sessionState.collectAsState()
    val candidateCount by viewModel.candidateCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ArcShield — Shadow Mode") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ---- Status card --------------------------------------------
            StatusCard(state = state, candidateCount = candidateCount)

            Spacer(Modifier.height(8.dp))

            // ---- Primary action button ----------------------------------
            when (state) {
                is SessionViewModel.SessionState.Idle -> Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Text("  Start Shadow Capture", style = MaterialTheme.typography.labelLarge)
                }

                is SessionViewModel.SessionState.Building -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Building I-frame baseline…")
                }

                is SessionViewModel.SessionState.Recording -> Button(
                    onClick = { viewModel.stopSession() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null,
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
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
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
