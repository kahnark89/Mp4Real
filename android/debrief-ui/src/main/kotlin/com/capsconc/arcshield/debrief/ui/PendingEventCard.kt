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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.capsconc.arcshield.debrief.model.PendingEventRecord
import com.capsconc.arcshield.debrief.ui.components.LambdaChip

/**
 * Card summarising one [PendingEventRecord] in the debrief queue.
 *
 * Shows:
 *   - truncated event ID + human-readable timestamp derived from detectedAtNanos
 *   - lambda component chips (lambda, lambdaEnv, lambdaBio)
 *   - low-sync-confidence warning when flagged
 *   - missing field count to communicate completeness at a glance
 *   - "Start Annotation" CTA and "Delete" button with confirmation dialog
 */
@Composable
fun PendingEventCard(
    event: PendingEventRecord,
    isCompleteFn: (PendingEventRecord) -> Boolean,
    onStartAnnotation: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete event?") },
            text    = { Text("This will permanently remove event ${event.eventId.take(8)} from the debrief queue. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Card(
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header row: event ID + timestamp
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "Event ${event.eventId.take(8)}…",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text  = formatNanos(event.detectedAtNanos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Lambda chips row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LambdaChip(label = "λ",    value = event.lambda)
                LambdaChip(label = "env",  value = event.lambdaEnv)
                LambdaChip(label = "bio",  value = event.lambdaBio)
            }

            // Low sync confidence warning
            if (event.lowSyncConfidence) {
                Spacer(Modifier.height(6.dp))
                SuggestionChip(
                    onClick = {},
                    label   = { Text("Low sync confidence") },
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFFFFF3E0), // orange-50
                        labelColor     = Color(0xFFE65100), // deep-orange-900
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Completeness summary
            val complete = isCompleteFn(event)
            if (complete) {
                Text(
                    text  = "All fields complete — ready to submit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                val missing = missingFields(event)
                Text(
                    text  = "Missing: ${missing.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    colors  = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }

                Spacer(Modifier.width(8.dp))

                Button(onClick = onStartAnnotation) {
                    Text(if (complete) "Review / Submit" else "Start Annotation")
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

/** Returns a human-readable time string from a nanosecond elapsedRealtime value. */
private fun formatNanos(nanos: Long): String {
    // elapsedRealtimeNanos is not wall-clock time; display as hours:minutes:seconds of shift time.
    val totalSeconds = nanos / 1_000_000_000L
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** Returns the short names of HITL fields that are still null. */
private fun missingFields(event: PendingEventRecord): List<String> {
    val missing = mutableListOf<String>()
    if (event.failureModeTag == null)      missing.add("failure mode")
    if (event.srkLevel == null)            missing.add("SRK level")
    if (event.causalHypothesis == null)    missing.add("hypothesis")
    if (event.actionType == null)          missing.add("action type")
    if (event.predictionMatch == null)     missing.add("prediction match")
    if (event.outcomeTag == null)          missing.add("outcome")
    if (event.hypothesisConfirmed == null) missing.add("hypothesis confirmed")
    if (event.productQualityImpact == null) missing.add("quality impact")
    if (event.hypothesisConfirmed == false && event.modelRevision.isNullOrBlank())
        missing.add("model revision")
    return missing
}
