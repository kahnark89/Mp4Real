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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val consoleBg      = Color(0xFF0D1117)
private val consoleAccent  = Color(0xFF90CAF9)
private val consoleDim     = Color(0xFF546E7A)
private val consoleDivider = Color(0xFF1E272E)

@Composable
fun ConsolePanel(modifier: Modifier = Modifier) {
    val logs by AppLogger.entries.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    val hasError = logs.any { it.level == LogLevel.ERROR }
    val statusColor = if (hasError) Color(0xFFEF5350) else Color(0xFF4CAF50)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = consoleBg),
    ) {
        Column {
            // Header row — tap anywhere to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(statusColor, CircleShape)
                    )
                    Text(
                        text  = "Console  ${logs.size}",
                        color = consoleAccent,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick          = { AppLogger.clear() },
                            contentPadding   = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("Clear", color = consoleDim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Icon(
                        imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint               = consoleDim,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = consoleDivider)

                if (logs.isEmpty()) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No log entries yet",
                            color = Color(0xFF37474F),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                } else {
                    LazyColumn(
                        state          = listState,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(
                            items = logs,
                            key   = { "${it.timestampMs}_${it.message.take(16)}" },
                        ) { entry ->
                            ConsoleLogRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: LogEntry) {
    val (textColor, levelChar) = when (entry.level) {
        LogLevel.DEBUG -> Color(0xFF546E7A) to "D"
        LogLevel.INFO  -> Color(0xFFB0BEC5) to "I"
        LogLevel.WARN  -> Color(0xFFFFB300) to "W"
        LogLevel.ERROR -> Color(0xFFEF5350) to "E"
    }
    Text(
        text     = "${entry.formattedTime} $levelChar/${entry.tag}: ${entry.message}",
        color    = textColor,
        style    = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize   = 10.sp,
            lineHeight = 14.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
