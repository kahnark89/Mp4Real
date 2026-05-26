package com.capsconc.arcshield.labeler

import android.util.Log
import com.capsconc.arcshield.llr.CandidateWindow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Appends serialized [CandidateWindow] objects to an NDJSON file on device storage.
 *
 * One JSON object per line; file lives at `filesDir/shadow_mode/<date>.ndjson`.
 * Write path is mutex-protected and flushes immediately after every record so
 * a crash mid-shift leaves a valid (partial) file.
 *
 * Usage:
 *   val log = CandidateWindowLog.forShift(context.filesDir, shiftStartEpochMs)
 *   llrGate(...).collect { log.append(it) }
 *   log.close()
 */
class CandidateWindowLog(val logFile: File) {

    private val mutex  = Mutex()
    private val writer: BufferedWriter

    init {
        logFile.parentFile?.mkdirs()
        writer = FileWriter(logFile, /* append = */ true).buffered()
    }

    suspend fun append(window: CandidateWindow) = mutex.withLock {
        writer.write(Json.encodeToString(window))
        writer.newLine()
        writer.flush()
    }

    fun close() {
        writer.close()
    }

    /**
     * Reads all windows from the log file. Skips malformed lines with a logged warning
     * rather than throwing — a corrupt line must not discard the whole shift.
     */
    fun read(): List<CandidateWindow> {
        if (!logFile.exists()) return emptyList()
        var skipped = 0
        val result = logFile.readLines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            try {
                Json.decodeFromString<CandidateWindow>(line)
            } catch (e: Exception) {
                skipped++
                null
            }
        }
        if (skipped > 0) Log.w(TAG, "Skipped $skipped malformed lines in ${logFile.name}")
        return result
    }

    companion object {
        private const val TAG = "CandidateWindowLog"
        private val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

        fun forShift(filesDir: File, shiftStartEpochMs: Long): CandidateWindowLog {
            val dateStr = ISO_DATE.format(Instant.ofEpochMilli(shiftStartEpochMs))
            val file = File(filesDir, "shadow_mode/$dateStr.ndjson")
            return CandidateWindowLog(file)
        }

        /** Returns shift log files sorted most-recent-first. */
        fun listShiftLogs(filesDir: File): List<File> =
            File(filesDir, "shadow_mode")
                .takeIf { it.isDirectory }
                ?.listFiles { f -> f.extension == "ndjson" }
                ?.sortedDescending()
                ?: emptyList()
    }
}
