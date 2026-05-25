package com.capsconc.arcshield.labeler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.capsconc.arcshield.labeler.TauCalibrator

@Composable
fun TauSummaryCard(
    suggestion:    TauCalibrator.TauSuggestion,
    onExport:      () -> Unit,
    modifier:      Modifier = Modifier,
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("τ suggestion", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LabeledStat("τ = %.3f".format(suggestion.suggestedTau), "threshold")
                LabeledStat("%.0f%%".format(suggestion.tpRate * 100), "TP rate")
                LabeledStat("%.0f%%".format(suggestion.fpRate * 100), "FP rate")
                LabeledStat("${suggestion.tpCount} / ${suggestion.fpCount}", "TP / FP")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${suggestion.totalLabeled} windows labeled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export labels")
            }
        }
    }
}

@Composable
private fun LabeledStat(value: String, label: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyLarge)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
