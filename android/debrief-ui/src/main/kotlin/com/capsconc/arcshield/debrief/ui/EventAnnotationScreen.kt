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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.capsconc.arcshield.debrief.model.CiaerVocabulary
import com.capsconc.arcshield.debrief.model.PendingEventRecord
import com.capsconc.arcshield.debrief.model.ShadowActionRecord
import com.capsconc.arcshield.debrief.ui.components.LabeledDropdown
import com.capsconc.arcshield.debrief.ui.components.LabeledTextField
import com.capsconc.arcshield.debrief.ui.components.LambdaChip
import com.capsconc.arcshield.debrief.ui.components.SectionHeader

/**
 * Full CIAER+ annotation form for one [PendingEventRecord].
 *
 * All mutable form state is held as [rememberSaveable] locals and flushed to the
 * ViewModel (and thus to disk) via [onSave] before submission. The composable is
 * stateless with respect to persistence — it reads the initial values from [record]
 * and calls [onSave] / [onSubmit] with the updated record.
 *
 * Layout: [LazyColumn] with one item per CIAER+ section so long forms scroll correctly
 * without nested scroll conflicts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventAnnotationScreen(
    record: PendingEventRecord,
    onSave: (PendingEventRecord) -> Unit,
    onSubmit: (PendingEventRecord) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // -------------------------------------------------------------------------
    // Local form state (initialized from record, persisted across recompositions)
    // -------------------------------------------------------------------------

    var failureModeTag    by rememberSaveable { mutableStateOf(record.failureModeTag ?: "") }
    var srkLevel          by rememberSaveable { mutableStateOf(record.srkLevel ?: "") }
    var causalHypothesis  by rememberSaveable { mutableStateOf(record.causalHypothesis ?: "") }
    var actionType        by rememberSaveable { mutableStateOf(record.actionType ?: "") }
    var actionRationale   by rememberSaveable { mutableStateOf(record.actionRationale ?: "") }
    var predictionMatch   by rememberSaveable { mutableStateOf(record.predictionMatch ?: "") }
    var outcomeTag        by rememberSaveable { mutableStateOf(record.outcomeTag ?: "") }
    var hypothesisConfirmed by rememberSaveable { mutableStateOf(record.hypothesisConfirmed ?: true) }
    var modelRevision     by rememberSaveable { mutableStateOf(record.modelRevision ?: "") }
    var productQualityImpact by rememberSaveable { mutableStateOf(record.productQualityImpact ?: "") }
    var graphWeight       by rememberSaveable { mutableFloatStateOf(record.graphWeight) }
    var shadowActions     by rememberSaveable { mutableStateOf(record.shadowActions) }

    // Shadow action inline-form state
    var showShadowForm       by rememberSaveable { mutableStateOf(false) }
    var newShadowActionType  by rememberSaveable { mutableStateOf("") }
    var newShadowRationale   by rememberSaveable { mutableStateOf("") }
    var newShadowConfidence  by rememberSaveable { mutableFloatStateOf(0.8f) }

    val snackbar = remember { SnackbarHostState() }

    // -------------------------------------------------------------------------
    // Helper: build current record from form state
    // -------------------------------------------------------------------------

    fun buildRecord() = record.copy(
        failureModeTag       = failureModeTag.ifBlank { null },
        srkLevel             = srkLevel.ifBlank { null },
        causalHypothesis     = causalHypothesis.ifBlank { null },
        actionType           = actionType.ifBlank { null },
        actionRationale      = actionRationale.ifBlank { null },
        predictionMatch      = predictionMatch.ifBlank { null },
        outcomeTag           = outcomeTag.ifBlank { null },
        hypothesisConfirmed  = hypothesisConfirmed,
        modelRevision        = modelRevision.ifBlank { null },
        productQualityImpact = productQualityImpact.ifBlank { null },
        graphWeight          = graphWeight,
        shadowActions        = shadowActions,
    )

    // -------------------------------------------------------------------------
    // Completeness check for Submit button guard
    // -------------------------------------------------------------------------

    fun isComplete(): Boolean {
        val r = buildRecord()
        return r.failureModeTag != null &&
               r.srkLevel != null &&
               !r.causalHypothesis.isNullOrBlank() &&
               r.actionType != null &&
               r.predictionMatch != null &&
               r.outcomeTag != null &&
               r.productQualityImpact != null &&
               // modelRevision is required when hypothesis is NOT confirmed (CLAUDE.md §2.4)
               (hypothesisConfirmed || !r.modelRevision.isNullOrBlank())
    }

    // -------------------------------------------------------------------------
    // Scaffold
    // -------------------------------------------------------------------------

    Scaffold(
        modifier     = modifier,
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Annotate Event")
                        Text(
                            text  = record.eventId.take(8) + "…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {

            // ------------------------------------------------------------------
            // Section: Sensor Context (read-only)
            // ------------------------------------------------------------------

            item {
                SectionHeader("Sensor Context")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LambdaChip(label = "λ",   value = record.lambda)
                    LambdaChip(label = "env", value = record.lambdaEnv)
                    LambdaChip(label = "bio", value = record.lambdaBio)
                }
                if (record.lowSyncConfidence) {
                    Spacer(Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = {},
                        label   = { Text("Low sync confidence — ε_sync > 250 ms") },
                        colors  = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFFFFF3E0),
                            labelColor     = Color(0xFFE65100),
                        ),
                    )
                }
                Text(
                    text  = "Session: ${record.sessionId}   Facility: ${record.facilityId}   Line: ${record.lineId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // ------------------------------------------------------------------
            // Section: Intuition
            // ------------------------------------------------------------------

            item {
                SectionHeader("Intuition")
                LabeledDropdown(
                    label    = "Failure Mode Tag",
                    options  = CiaerVocabulary.FAILURE_MODE_TAGS,
                    selected = failureModeTag.ifBlank { null },
                    onSelect = { failureModeTag = it },
                )
                Spacer(Modifier.height(8.dp))
                LabeledDropdown(
                    label    = "SRK Level",
                    options  = CiaerVocabulary.SRK_LEVELS,
                    selected = srkLevel.ifBlank { null },
                    onSelect = { srkLevel = it },
                )
                Spacer(Modifier.height(8.dp))
                LabeledTextField(
                    label         = "Causal Hypothesis",
                    value         = causalHypothesis,
                    onValueChange = { causalHypothesis = it },
                    minLines      = 3,
                )
                Spacer(Modifier.height(8.dp))
            }

            // ------------------------------------------------------------------
            // Section: Action
            // ------------------------------------------------------------------

            item {
                SectionHeader("Action")
                LabeledDropdown(
                    label    = "Action Type",
                    options  = CiaerVocabulary.ACTION_TYPES,
                    selected = actionType.ifBlank { null },
                    onSelect = { actionType = it },
                )
                Spacer(Modifier.height(8.dp))
                LabeledTextField(
                    label         = "Action Rationale",
                    value         = actionRationale,
                    onValueChange = { actionRationale = it },
                    required      = false,
                    minLines      = 2,
                )
                Spacer(Modifier.height(8.dp))
            }

            // ------------------------------------------------------------------
            // Section: Shadow Actions (rejected alternatives)
            // ------------------------------------------------------------------

            item {
                SectionHeader("Shadow Actions (Rejected Alternatives)")
                if (srkLevel == "KNOWLEDGE" && shadowActions.isEmpty()) {
                    Text(
                        text  = "KNOWLEDGE-level events require at least one rejected alternative (CLAUDE.md §2.4).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            // Existing shadow actions
            items(shadowActions.size) { idx ->
                val sa = shadowActions[idx]
                ShadowActionRow(
                    sa       = sa,
                    onDelete = { shadowActions = shadowActions.toMutableList().also { it.removeAt(idx) } },
                )
                Spacer(Modifier.height(4.dp))
            }

            // Add shadow action inline form
            item {
                if (showShadowForm) {
                    ShadowActionForm(
                        actionType  = newShadowActionType,
                        rationale   = newShadowRationale,
                        confidence  = newShadowConfidence,
                        onActionTypeChange  = { newShadowActionType = it },
                        onRationaleChange   = { newShadowRationale = it },
                        onConfidenceChange  = { newShadowConfidence = it },
                        onAdd = {
                            if (newShadowActionType.isNotBlank() && newShadowRationale.isNotBlank()) {
                                shadowActions = shadowActions + ShadowActionRecord(
                                    actionType            = newShadowActionType,
                                    rejectionRationale    = newShadowRationale,
                                    confidenceInRejection = newShadowConfidence,
                                )
                                newShadowActionType = ""
                                newShadowRationale  = ""
                                newShadowConfidence = 0.8f
                                showShadowForm      = false
                            }
                        },
                        onCancel = { showShadowForm = false },
                    )
                } else {
                    OutlinedButton(
                        onClick  = { showShadowForm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Shadow Action")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ------------------------------------------------------------------
            // Section: Effect
            // ------------------------------------------------------------------

            item {
                SectionHeader("Effect")
                LabeledDropdown(
                    label    = "Prediction Match",
                    options  = CiaerVocabulary.PREDICTION_MATCH_OPTIONS,
                    selected = predictionMatch.ifBlank { null },
                    onSelect = { predictionMatch = it },
                )
                Spacer(Modifier.height(8.dp))
            }

            // ------------------------------------------------------------------
            // Section: Result
            // ------------------------------------------------------------------

            item {
                SectionHeader("Result")
                LabeledDropdown(
                    label    = "Outcome Tag",
                    options  = CiaerVocabulary.OUTCOME_TAGS,
                    selected = outcomeTag.ifBlank { null },
                    onSelect = { outcomeTag = it },
                )
                Spacer(Modifier.height(8.dp))

                // Hypothesis confirmed toggle
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = "Hypothesis Confirmed *",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked         = hypothesisConfirmed,
                        onCheckedChange = { hypothesisConfirmed = it },
                    )
                }

                // Model revision — required when hypothesis not confirmed
                if (!hypothesisConfirmed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "Hypothesis was NOT confirmed — model revision required (CLAUDE.md §2.4).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    LabeledTextField(
                        label         = "Model Revision",
                        value         = modelRevision,
                        onValueChange = { modelRevision = it },
                        required      = true,
                        minLines      = 3,
                    )
                }

                Spacer(Modifier.height(8.dp))
                LabeledDropdown(
                    label    = "Product Quality Impact",
                    options  = CiaerVocabulary.PRODUCT_QUALITY_IMPACTS,
                    selected = productQualityImpact.ifBlank { null },
                    onSelect = { productQualityImpact = it },
                )
                Spacer(Modifier.height(8.dp))

                // Graph weight slider
                Text(
                    text  = "Graph Weight: ${"%.2f".format(graphWeight)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value         = graphWeight,
                    onValueChange = { graphWeight = it },
                    valueRange    = 0f..1f,
                    steps         = 19, // 0.05 increments
                )
                Spacer(Modifier.height(16.dp))
            }

            // ------------------------------------------------------------------
            // Action buttons
            // ------------------------------------------------------------------

            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }

                    OutlinedButton(
                        onClick = { onSave(buildRecord()) },
                    ) { Text("Save Draft") }

                    Button(
                        onClick  = {
                            val updated = buildRecord()
                            if (isComplete()) {
                                onSubmit(updated)
                            } else {
                                // Trigger snackbar inline rather than propagating — the
                                // ViewModel error path is for async failures.
                            }
                        },
                        enabled  = isComplete(),
                    ) { Text("Submit to Corpus") }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// -------------------------------------------------------------------------
// Shadow action row
// -------------------------------------------------------------------------

@Composable
private fun ShadowActionRow(
    sa: ShadowActionRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = sa.actionType,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text  = sa.rejectionRationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = "Confidence in rejection: ${"%.0f".format(sa.confidenceInRejection * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete shadow action",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Shadow action inline add form
// -------------------------------------------------------------------------

@Composable
private fun ShadowActionForm(
    actionType: String,
    rationale: String,
    confidence: Float,
    onActionTypeChange: (String) -> Unit,
    onRationaleChange: (String) -> Unit,
    onConfidenceChange: (Float) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("New Rejected Alternative", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            LabeledDropdown(
                label    = "Action Type",
                options  = CiaerVocabulary.ACTION_TYPES,
                selected = actionType.ifBlank { null },
                onSelect = onActionTypeChange,
            )
            Spacer(Modifier.height(8.dp))
            LabeledTextField(
                label         = "Rejection Rationale",
                value         = rationale,
                onValueChange = onRationaleChange,
                minLines      = 2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "Confidence in rejection: ${"%.0f".format(confidence * 100)}%",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value         = confidence,
                onValueChange = onConfidenceChange,
                valueRange    = 0f..1f,
                steps         = 19,
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick  = onAdd,
                    enabled  = actionType.isNotBlank() && rationale.isNotBlank(),
                ) { Text("Add") }
            }
        }
    }
}
