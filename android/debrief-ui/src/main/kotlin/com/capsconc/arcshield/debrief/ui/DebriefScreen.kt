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

package com.capsconc.arcshield.debrief.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capsconc.arcshield.debrief.model.PendingEventRecord
import com.capsconc.arcshield.debrief.viewmodel.DebriefState
import com.capsconc.arcshield.debrief.viewmodel.DebriefViewModel

/**
 * Root debrief queue screen.
 *
 * Shows the list of [PendingEventRecord] objects awaiting HITL annotation.
 * A FAB opens an import dialog where the operator enters a session ID (NDJSON
 * log date string, e.g. "2026-04-08") to pull in new events from the shadow-mode log.
 *
 * Navigation to the annotation form is delegated to [onNavigateToAnnotation] — the
 * host activity/fragment handles the back-stack; this composable only manages queue state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebriefScreen(
    viewModel: DebriefViewModel,
    onNavigateToAnnotation: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state    by viewModel.state.collectAsStateWithLifecycle()
    val snackbar  = remember { SnackbarHostState() }

    var showImportDialog by rememberSaveable { mutableStateOf(false) }

    // Surface error messages via snackbar
    LaunchedEffect(state) {
        if (state is DebriefState.Error) {
            snackbar.showSnackbar((state as DebriefState.Error).message)
        }
    }

    if (showImportDialog) {
        ImportSessionDialog(
            onImport  = { sessionId ->
                showImportDialog = false
                viewModel.importFromSession(sessionId)
            },
            onDismiss = { showImportDialog = false },
        )
    }

    Scaffold(
        modifier     = modifier,
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = {
                    val count = when (val s = state) {
                        is DebriefState.Queue -> s.events.size
                        else -> null
                    }
                    if (count != null) {
                        BadgedBox(badge = { Badge { Text("$count") } }) {
                            Text("Event Debrief Queue")
                        }
                    } else {
                        Text("Event Debrief Queue")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportDialog = true },
                icon    = { Icon(Icons.Filled.Add, contentDescription = "Import session") },
                text    = { Text("Import Session") },
            )
        },
    ) { paddingValues ->
        when (val s = state) {
            is DebriefState.Loading -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            is DebriefState.Queue -> {
                if (s.events.isEmpty()) {
                    EmptyQueueView(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                    )
                } else {
                    QueueList(
                        events             = s.events,
                        viewModel          = viewModel,
                        onNavigate         = onNavigateToAnnotation,
                        paddingValues      = paddingValues,
                    )
                }
            }

            is DebriefState.Error -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text  = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.loadQueue() }) { Text("Retry") }
                    }
                }
            }

            // Submitting / Editing states are handled in EventAnnotationScreen; if we land
            // back here in those states (e.g. deep-link), fall back to a reload.
            else -> {
                LaunchedEffect(Unit) { viewModel.loadQueue() }
                Box(
                    modifier         = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Queue list
// -------------------------------------------------------------------------

@Composable
private fun QueueList(
    events: List<PendingEventRecord>,
    viewModel: DebriefViewModel,
    onNavigate: (String) -> Unit,
    paddingValues: PaddingValues,
) {
    // We need the isComplete check from DebriefRepository — instantiate a dummy one
    // purely for the extension function; actual disk calls go through the ViewModel.
    // In practice the host wires a real repository; this avoids exposing the extension
    // as a top-level function polluting the package namespace.
    val dummyRepo = remember {
        object {
            fun isComplete(record: PendingEventRecord): Boolean =
                record.failureModeTag != null &&
                record.srkLevel != null &&
                record.causalHypothesis != null &&
                record.actionType != null &&
                record.predictionMatch != null &&
                record.outcomeTag != null &&
                record.hypothesisConfirmed != null &&
                record.productQualityImpact != null &&
                (record.hypothesisConfirmed == true || !record.modelRevision.isNullOrBlank())
        }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.eventId }) { event ->
            PendingEventCard(
                event             = event,
                isCompleteFn      = { dummyRepo.isComplete(it) },
                onStartAnnotation = {
                    viewModel.startEditing(event)
                    onNavigate(event.eventId)
                },
                onDelete          = { viewModel.deleteRecord(event.eventId) },
            )
        }
    }
}

// -------------------------------------------------------------------------
// Empty state
// -------------------------------------------------------------------------

@Composable
private fun EmptyQueueView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp),
        ) {
            Text(
                text  = "No events pending review",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "Use \"Import Session\" to pull candidate events from the shadow-mode log. "
                      + "Run the LLR gate in shadow mode for at least one shift to generate candidates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -------------------------------------------------------------------------
// Import session dialog
// -------------------------------------------------------------------------

@Composable
private fun ImportSessionDialog(
    onImport: (sessionId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var sessionId by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Import from shadow log") },
        text    = {
            Column {
                Text(
                    text  = "Enter the session ID (log date, e.g. 2026-04-08):",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = sessionId,
                    onValueChange = { sessionId = it },
                    label         = { Text("Session ID") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (sessionId.isNotBlank()) onImport(sessionId.trim()) },
                enabled  = sessionId.isNotBlank(),
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
