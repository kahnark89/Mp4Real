package com.capsconc.arcshield.labeler.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.capsconc.arcshield.labeler.ElicitedAction
import com.capsconc.arcshield.labeler.Label
import com.capsconc.arcshield.labeler.LabeledWindow
import java.util.concurrent.TimeUnit

/**
 * One row in the labeling list. Shows timestamp, total Λ, per-channel breakdown,
 * activity gate suppression badge, and TP / FP / ? toggle.
 *
 * [shiftStartNanos] is the detectedAtNanos of the first window in the shift, used
 * to compute relative timestamps. Pass 0L to show absolute nanos.
 */
@Composable
fun CandidateWindowRow(
    lw:              LabeledWindow,
    shiftStartNanos: Long,
    maxLambda:       Float,
    onLabel:         (Label) -> Unit,
    onSeek:          () -> Unit = {},
    onElicit:        () -> Unit = {},
    elicitedAction:  ElicitedAction? = null,
    isListening:     Boolean = false,
    modifier:        Modifier = Modifier,
) {
    val w = lw.window
    val relNanos  = w.detectedAtNanos - shiftStartNanos
    val relSec    = TimeUnit.NANOSECONDS.toSeconds(relNanos)
    val hh        = relSec / 3600
    val mm        = (relSec % 3600) / 60
    val ss        = relSec % 60
    val timeLabel = "%02d:%02d:%02d".format(hh, mm, ss)

    Row(
        modifier             = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Timestamp + total Λ
        Column(modifier = Modifier.width(72.dp)) {
            Text(timeLabel, style = MaterialTheme.typography.bodyMedium)
            Text("Λ=%.3f".format(w.lambda),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Component bars
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            LambdaBar("acoustic", w.lambdaAcoustic, maxLambda, Color(0xFF4CAF50))
            LambdaBar("accel",    w.lambdaAccel,    maxLambda, Color(0xFF2196F3))
            LambdaBar("motion",   w.lambdaMotion,   maxLambda, Color(0xFFFF9800))
            LambdaBar("bio",      w.lambdaBio,      maxLambda, Color(0xFFE91E63))

            // Activity gate badge when Λ_bio is suppressed
            if (w.activityGate < 0.5f) {
                val actLabel = when {
                    w.activityGate <= 0.1f -> "VIGOROUS"
                    w.activityGate <= 0.4f -> "MODERATE"
                    else                   -> "LIGHT"
                }
                SuggestionChip(
                    onClick = {},
                    label   = { Text("$actLabel ×%.1f".format(w.activityGate),
                        style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // TP / FP / UNLABELED toggle + seek + elicit
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(
                onClick  = { onLabel(if (lw.label == Label.TRUE_POSITIVE) Label.UNLABELED else Label.TRUE_POSITIVE) },
                modifier = Modifier.width(48.dp),
            ) {
                Text(if (lw.label == Label.TRUE_POSITIVE) "✓TP" else "TP",
                    style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick  = { onLabel(if (lw.label == Label.FALSE_POSITIVE) Label.UNLABELED else Label.FALSE_POSITIVE) },
                modifier = Modifier.width(48.dp),
            ) {
                Text(if (lw.label == Label.FALSE_POSITIVE) "✓FP" else "FP",
                    style = MaterialTheme.typography.labelSmall)
            }
            // Seek player to 30 s before this window's detection PTS
            IconButton(onClick = onSeek, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Seek to window",
                    tint = MaterialTheme.colorScheme.primary)
            }
            // Voice-first elicitation button
            FilledTonalButton(
                onClick  = onElicit,
                enabled  = !isListening,
                modifier = Modifier.width(48.dp),
            ) {
                Text(
                    if (elicitedAction != null) "●Mic" else "Mic",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (elicitedAction != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    // Show elicited action summary below the row if present
    if (elicitedAction != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            Text(
                "${elicitedAction.actionType} → ${elicitedAction.actionTarget}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                elicitedAction.canonicalIntuition,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun LambdaBar(channel: String, value: Float, maxValue: Float, color: Color) {
    val fraction = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(channel, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(46.dp),
            color    = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .weight(fraction.coerceAtLeast(0.01f))
                .height(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        if (fraction < 1f) Spacer(Modifier.weight((1f - fraction).coerceAtLeast(0.01f)))
        Text("%.3f".format(value),
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(40.dp))
    }
}
