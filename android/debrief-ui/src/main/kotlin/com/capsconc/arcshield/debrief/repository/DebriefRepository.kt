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

package com.capsconc.arcshield.debrief.repository

import android.content.Context
import android.util.Log
import com.capsconc.arcshield.debrief.model.PendingEventRecord
import com.capsconc.arcshield.debrief.model.ShadowActionRecord
import com.capsconc.arcshield.llr.CandidateWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

/**
 * Reads pending CIAER+ event records from disk and submits completed records to the backend.
 *
 * Disk layout:
 *   - `filesDir/shadow_mode/<date>.ndjson` — written by [CandidateWindowLog]; read-only here
 *   - `filesDir/debrief_pending/<eventId>.json` — in-progress HITL records; read/write here
 *
 * No in-memory caching: every [loadPendingEvents] call re-reads from disk so the UI stays
 * consistent with concurrent writes from other app sessions.
 */
class DebriefRepository(
    private val context: Context,
    private val backendBaseUrl: String = "http://localhost:8080",
    private val facilityId: String = "HOLLOWELL_PPVC1",
    private val lineId: String = "PPVC_LINE_1",
    private val operatorId: String = "operator_hash_placeholder",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val httpClient = OkHttpClient()

    /** Directory containing shadow-mode NDJSON logs written by [CandidateWindowLog]. */
    private val shadowDir: File
        get() = File(context.filesDir, "shadow_mode")

    /** Directory for in-progress HITL records. One JSON file per event. */
    private val pendingDir: File
        get() = File(context.filesDir, "debrief_pending")

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /**
     * Scans [pendingDir] for `*.json` files, parses [PendingEventRecord], and returns
     * them sorted by [PendingEventRecord.detectedAtNanos] ascending (oldest first).
     */
    suspend fun loadPendingEvents(): List<PendingEventRecord> = withContext(Dispatchers.IO) {
        val dir = pendingDir
        if (!dir.isDirectory) return@withContext emptyList()
        var skipped = 0
        val records = dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<PendingEventRecord>(file.readText())
                } catch (e: Exception) {
                    skipped++
                    Log.w(TAG, "Failed to parse pending record ${file.name}: ${e.message}")
                    null
                }
            }
            ?.sortedBy { it.detectedAtNanos }
            ?: emptyList()
        if (skipped > 0) Log.w(TAG, "Skipped $skipped malformed files in debrief_pending/")
        records
    }

    // -------------------------------------------------------------------------
    // Import from shadow log
    // -------------------------------------------------------------------------

    /**
     * Reads `shadow_mode/<sessionId>.ndjson`, parses every [CandidateWindow] line,
     * converts each to a [PendingEventRecord] (null HITL fields), and saves them to [pendingDir].
     *
     * Skips windows whose eventId is already present in [pendingDir] to avoid duplicates
     * if the same session is imported more than once.
     *
     * Returns the list of newly created records sorted by [PendingEventRecord.detectedAtNanos].
     */
    suspend fun importFromShadowLog(sessionId: String): List<PendingEventRecord> =
        withContext(Dispatchers.IO) {
            val logFile = File(shadowDir, "$sessionId.ndjson")
            if (!logFile.exists()) {
                Log.w(TAG, "Shadow log not found: ${logFile.absolutePath}")
                return@withContext emptyList()
            }

            pendingDir.mkdirs()
            val existingIds = pendingDir.listFiles { f -> f.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?.toSet()
                ?: emptySet()

            var skipped = 0
            val created = mutableListOf<PendingEventRecord>()

            logFile.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                // Skip metadata header line written by CandidateWindowLog.writeHeader()
                if (line.contains("\"_meta\"")) return@forEach
                try {
                    val window = json.decodeFromString<CandidateWindow>(line)
                    val eventId = UUID.randomUUID().toString()
                    // Skip if we already have a record for this detectedAtNanos+session combo.
                    // Because eventId is random, we key deduplication on sessionId+nanos file name.
                    val dedupKey = "${sessionId}_${window.detectedAtNanos}"
                    if (existingIds.contains(dedupKey)) return@forEach

                    val record = PendingEventRecord(
                        eventId          = eventId,
                        sessionId        = sessionId,
                        facilityId       = facilityId,
                        lineId           = lineId,
                        operatorId       = operatorId,
                        detectedAtNanos  = window.detectedAtNanos,
                        lambda           = window.lambda,
                        lambdaEnv        = window.lambdaEnv,
                        lambdaBio        = window.lambdaBio,
                        lowSyncConfidence = false, // refined by ε_sync metadata if available
                    )
                    val outFile = File(pendingDir, "$eventId.json")
                    outFile.writeText(json.encodeToString(record))
                    created.add(record)
                } catch (e: Exception) {
                    skipped++
                    Log.w(TAG, "Skipped malformed line in $sessionId.ndjson: ${e.message}")
                }
            }
            if (skipped > 0) Log.w(TAG, "Skipped $skipped malformed lines from session $sessionId")
            created.sortedBy { it.detectedAtNanos }
        }

    // -------------------------------------------------------------------------
    // Persist progress
    // -------------------------------------------------------------------------

    /**
     * Writes the current state of [record] to `debrief_pending/<eventId>.json`.
     * Creates the directory if it doesn't exist.
     */
    suspend fun saveProgress(record: PendingEventRecord) = withContext(Dispatchers.IO) {
        pendingDir.mkdirs()
        val file = File(pendingDir, "${record.eventId}.json")
        file.writeText(json.encodeToString(record))
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    /**
     * Validates [record] via [PendingEventRecord.isComplete], builds a minimal CIAER+ JSON
     * payload, and POSTs it to `<backendBaseUrl>/ingest_event`.
     *
     * Returns [Result.success] with the event ID on HTTP 2xx, or [Result.failure] with a
     * descriptive message on validation failure, network error, or non-2xx response.
     *
     * On success, the pending file is NOT deleted — call [deleteRecord] explicitly after
     * confirming backend acceptance so the UI can show the completed record before removal.
     */
    suspend fun submitEvent(record: PendingEventRecord): Result<String> =
        withContext(Dispatchers.IO) {
            if (!record.isComplete()) {
                return@withContext Result.failure(
                    IllegalStateException("Event ${record.eventId} is not complete — missing required HITL fields")
                )
            }

            val payload = buildCiaerJson(record)
            val body    = payload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$backendBaseUrl/ingest_event")
                .post(body)
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        Result.success(record.eventId)
                    } else {
                        val errorBody = resp.body?.string()?.take(256) ?: "(no body)"
                        Result.failure(
                            RuntimeException("Backend returned ${resp.code}: $errorBody")
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /** Removes `debrief_pending/<eventId>.json` from disk. No-op if file doesn't exist. */
    suspend fun deleteRecord(eventId: String) = withContext(Dispatchers.IO) {
        File(pendingDir, "$eventId.json").delete()
    }

    // -------------------------------------------------------------------------
    // Completeness check (exposed as extension for ViewModel / UI use)
    // -------------------------------------------------------------------------

    /**
     * Returns true when all REQUIRED HITL fields are non-null and the schema invariants
     * from CLAUDE.md §2.4 are satisfied:
     *   - failureModeTag, srkLevel, causalHypothesis, actionType, predictionMatch,
     *     outcomeTag, hypothesisConfirmed, productQualityImpact are all non-null
     *   - modelRevision is non-null when hypothesisConfirmed == false
     */
    fun PendingEventRecord.isComplete(): Boolean =
        failureModeTag != null &&
        srkLevel != null &&
        causalHypothesis != null &&
        actionType != null &&
        predictionMatch != null &&
        outcomeTag != null &&
        hypothesisConfirmed != null &&
        productQualityImpact != null &&
        (hypothesisConfirmed == true || !modelRevision.isNullOrBlank())

    // -------------------------------------------------------------------------
    // CIAER+ JSON builder
    // -------------------------------------------------------------------------

    /**
     * Constructs a minimal valid CIAER+ JSON event string for the backend `/ingest_event`
     * endpoint. All fields required by the CIAER+ schema (CLAUDE.md §2) are populated;
     * sensor_readings is an empty list (filled from the mp4Real container on backend ingest).
     *
     * escalation_state = 0 and escalation_delta = 0 are placeholders until the PLC
     * telemetry path (Phase 3) provides real escalation state.
     */
    private fun buildCiaerJson(record: PendingEventRecord): String {
        val shadowActionsJson = record.shadowActions.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { sa ->
            """{"action_type":${sa.actionType.jsonString()},"rejection_rationale":${sa.rejectionRationale.jsonString()},"confidence_in_rejection":${sa.confidenceInRejection}}"""
        }

        return """
{
  "schema_version": "1.0",
  "event_id": ${record.eventId.jsonString()},
  "envelope": {
    "schema_version": "1.0",
    "event_id": ${record.eventId.jsonString()},
    "timestamp_start": ${record.detectedAtNanos},
    "operator_id": ${record.operatorId.jsonString()},
    "facility_id": ${record.facilityId.jsonString()},
    "line_id": ${record.lineId.jsonString()},
    "session_id": ${record.sessionId.jsonString()},
    "low_sync_confidence": ${record.lowSyncConfidence}
  },
  "pre_env": {
    "shift_phase": "mid_shift",
    "material_batch_id": null,
    "ambient_temp_f": null,
    "recent_events_summary": null,
    "crew_state_tag": null
  },
  "cause": {
    "capture_timestamp": ${record.detectedAtNanos},
    "trigger_source": "OPERATOR_MANUAL",
    "sensor_readings": [],
    "lambda": ${record.lambda},
    "lambda_env": ${record.lambdaEnv},
    "lambda_bio": ${record.lambdaBio}
  },
  "intuition": {
    "srk_level": ${record.srkLevel.jsonString()},
    "causal_hypothesis": ${record.causalHypothesis.jsonString()},
    "failure_mode_tag": ${record.failureModeTag.jsonString()},
    "confidence_level": 0.8,
    "projection": null,
    "voice_transcript": null,
    "biometric_signature": null
  },
  "action": {
    "action_type": ${record.actionType.jsonString()},
    "action_timestamp": ${record.detectedAtNanos},
    "action_rationale": ${record.actionRationale.jsonString()},
    "action_sequence": []
  },
  "shadow_actions": $shadowActionsJson,
  "effect": {
    "capture_timestamp": ${record.detectedAtNanos},
    "sensor_readings": [],
    "deltas": [],
    "prediction_match": ${record.predictionMatch.jsonString()}
  },
  "result": {
    "completed_at": ${record.detectedAtNanos},
    "outcome_tag": ${record.outcomeTag.jsonString()},
    "escalation_state_at_result": 0,
    "escalation_delta": 0,
    "hypothesis_confirmed": ${record.hypothesisConfirmed},
    "model_revision": ${record.modelRevision.jsonString()},
    "product_quality_impact": ${record.productQualityImpact.jsonString()},
    "graph_weight": ${record.graphWeight}
  }
}
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** JSON-encodes a nullable string: null → "null", non-null → "\"escaped\"". */
    private fun String?.jsonString(): String =
        if (this == null) "null"
        else "\"${this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""

    companion object {
        private const val TAG = "DebriefRepository"
    }
}
