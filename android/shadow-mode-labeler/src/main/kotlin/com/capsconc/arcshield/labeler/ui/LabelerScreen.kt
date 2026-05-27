package com.capsconc.arcshield.labeler.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capsconc.arcshield.labeler.Label
import com.capsconc.arcshield.labeler.LabelerViewModel
import com.capsconc.arcshield.schema.llm.LlmClient
import java.io.File

/**
 * Top-level entry point for the shadow-mode labeler UI.
 *
 * [llmClient] is injected from [MainActivity] (Hilt-provided) and forwarded
 * to [LabelerViewModel.factory] so voice elicitation has access to Claude.
 *
 * Shows the shift picker (no log selected) or the labeling+playback pane (log selected).
 */
@Composable
fun LabelerScreen(
    llmClient: LlmClient,
    vm: LabelerViewModel = viewModel(
        factory = LabelerViewModel.factory(llmClient)
    ),
) {
    val selectedLog by vm.selectedLog.collectAsState()
    if (selectedLog == null) ShiftPickerPane(vm) else LabelingPane(vm)
}

// ---------------------------------------------------------------------------
// Shift picker
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftPickerPane(vm: LabelerViewModel) {
    val logs by vm.shiftLogs.collectAsState()

    Scaffold(
        topBar = { androidx.compose.material3.TopAppBar(title = { Text("Shadow mode logs") }) }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("No shift logs found.\nRun the LLR gate in shadow mode first.",
                style = MaterialTheme.typography.bodyMedium) }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs) { file ->
                    ShiftLogItem(file, onClick = { vm.selectLog(file) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ShiftLogItem(file: File, onClick: () -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyLarge)
            Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Labeling + playback pane
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelingPane(vm: LabelerViewModel) {
    val windows         by vm.windows.collectAsState()
    val suppressed      by vm.suppressionEnabled.collectAsState()
    val suggestion      by vm.tauSuggestion.collectAsState()
    val exportState     by vm.exportState.collectAsState()
    val elicitState     by vm.elicitationState.collectAsState()
    val elicitedActions by vm.elicitedActions.collectAsState()
    val hasVideo        by vm.hasVideo.collectAsState()
    val snackbar        = remember { SnackbarHostState() }

    val labeled    = windows.count { it.label != Label.UNLABELED }
    val maxLambda  = windows.maxOfOrNull { it.window.lambda } ?: 1f
    val shiftStart = windows.firstOrNull()?.window?.detectedAtNanos ?: 0L

    val isListening = elicitState is LabelerViewModel.ElicitationState.Listening

    // Export feedback
    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is LabelerViewModel.ExportState.Done  -> {
                snackbar.showSnackbar("Exported to ${s.file.name}")
                vm.resetExportState()
            }
            is LabelerViewModel.ExportState.Error -> {
                snackbar.showSnackbar("Export failed: ${s.msg}")
                vm.resetExportState()
            }
            else -> {}
        }
    }

    // Voice elicitation feedback
    LaunchedEffect(elicitState) {
        when (val s = elicitState) {
            is LabelerViewModel.ElicitationState.Done -> {
                snackbar.showSnackbar("Elicited: ${s.action.actionType} → ${s.action.actionTarget}")
                vm.resetElicitationState()
            }
            is LabelerViewModel.ElicitationState.Error -> {
                snackbar.showSnackbar("Elicitation error: ${s.msg}")
                vm.resetElicitationState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        topBar = {
            androidx.compose.material3.TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.clearSelection() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(vm.selectedLog.collectAsState().value?.nameWithoutExtension ?: "")
                        Text("$labeled / ${windows.size} labeled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (isListening) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    AssistChip(
                        onClick = { vm.toggleSuppression() },
                        label   = { Text(if (suppressed) "NMS 60s ON" else "NMS OFF") },
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {

            // Video player for offline playback (W-015)
            item {
                VideoPlayerView(
                    exoPlayer = vm.exoPlayer,
                    hasVideo  = hasVideo,
                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }

            // Tau summary card — pinned when ≥ 3 labeled
            if (suggestion != null && labeled >= 3) {
                item {
                    TauSummaryCard(
                        suggestion = suggestion!!,
                        onExport   = { vm.exportLabels() },
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Candidate windows
            items(windows, key = { it.window.detectedAtNanos }) { lw ->
                CandidateWindowRow(
                    lw              = lw,
                    shiftStartNanos = shiftStart,
                    maxLambda       = maxLambda,
                    elicitedAction  = elicitedActions[lw.window.detectedAtNanos],
                    isListening     = isListening,
                    onLabel         = { label -> vm.setLabel(lw.window.detectedAtNanos, label) },
                    onSeek          = { vm.seekToWindow(lw.window.detectedAtNanos) },
                    onElicit        = { vm.elicitForWindow(lw.window.detectedAtNanos) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }

            if (windows.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("No candidate windows in this log.", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
