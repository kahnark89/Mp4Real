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

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val time: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
)

object AppLogger {
    private const val MAX_ENTRIES = 200

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun debug(tag: String, message: String) {
        Log.d(tag, message)
        append(LogEntry(LogLevel.DEBUG, tag, message))
    }

    fun info(tag: String, message: String) {
        Log.i(tag, message)
        append(LogEntry(LogLevel.INFO, tag, message))
    }

    fun warn(tag: String, message: String) {
        Log.w(tag, message)
        append(LogEntry(LogLevel.WARN, tag, message))
    }

    fun error(tag: String, message: String) {
        Log.e(tag, message)
        append(LogEntry(LogLevel.ERROR, tag, message))
    }

    private fun append(entry: LogEntry) {
        _entries.update { list ->
            (list + entry).takeLast(MAX_ENTRIES)
        }
    }
}

@Composable
fun ConsolePanel(modifier: Modifier = Modifier) {
    val logs by AppLogger.entries.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color(0xFF0A0C10)),
    ) {
        LazyColumn(
            state   = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(logs) { entry ->
                val color = when (entry.level) {
                    LogLevel.DEBUG -> Color(0xFF8A8FA8)
                    LogLevel.INFO  -> Color(0xFFE8EAF0)
                    LogLevel.WARN  -> Color(0xFFFBBF24)
                    LogLevel.ERROR -> Color(0xFFF87171)
                }
                Text(
                    text  = "${entry.time} [${entry.tag}] ${entry.message}",
                    color = color,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 10.sp,
                        lineHeight  = 14.sp,
                    ),
                )
            }
        }
    }
}
