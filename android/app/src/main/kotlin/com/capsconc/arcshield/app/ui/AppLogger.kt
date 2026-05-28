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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
}

// Process-lifetime in-memory log ring buffer. Capped at MAX_ENTRIES to bound memory.
// Consumed by ConsolePanel in MainScreen. Not persisted to disk — ephemeral debug view only.
object AppLogger {
    private const val MAX_ENTRIES = 200

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(level: LogLevel, tag: String, message: String) {
        _entries.update { list ->
            val entry = LogEntry(level, tag, message)
            if (list.size >= MAX_ENTRIES) list.drop(1) + entry else list + entry
        }
    }

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String)  = log(LogLevel.INFO,  tag, message)
    fun warn(tag: String, message: String)  = log(LogLevel.WARN,  tag, message)
    fun error(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    fun clear() { _entries.value = emptyList() }
}
