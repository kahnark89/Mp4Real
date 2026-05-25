package com.capsconc.arcshield.codec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Immutable session descriptor written to the .mp4real.json sidecar on container close.
 *
 * The sidecar lives alongside the container file and carries all metadata that
 * cannot be embedded in the MP4 udta box without a post-processing pass with
 * mp4parser (Phase 2 upgrade). Downstream consumers (ingest, codebook matcher)
 * read the sidecar to get ε_sync, track layout, and source identifiers.
 *
 * [epsSyncNanos] and [lowSyncConfidence] are populated at close time by
 * [Mp4RealWriter.close]; they are zero/false on construction.
 *
 * [trackMap] maps TrackType.description → muxer track index string, populated
 * at close time once all tracks have been registered.
 */
@Serializable
data class SessionMetadata(
    val sessionId: String,
    val operatorId: String,
    val facilityId: String,
    val lineId: String,
    val captureSourceId: String,
    val biometricSourceId: String,
    /** elapsedRealtimeNanos at shift start (PTS=0 anchor). */
    val sessionStartNanos: Long,
    /** Measured ε_sync for this session in nanoseconds. Written at close. */
    val epsSyncNanos: Long = 0L,
    /** True when epsSyncNanos > EpsSyncMeasure.LOW_SYNC_NS (250 ms). */
    val lowSyncConfidence: Boolean = false,
    /** TrackType.description → muxer track index. Written at close. */
    val trackMap: Map<String, String> = emptyMap(),
)

private val sidecarJson = Json { prettyPrint = true }

/**
 * Writes this metadata as a pretty-printed JSON file alongside [containerFile].
 * The sidecar name is {container_stem}.mp4real.json.
 */
internal fun SessionMetadata.writeSidecar(containerFile: java.io.File) {
    val sidecar = java.io.File(
        containerFile.parent,
        containerFile.nameWithoutExtension + ".mp4real.json",
    )
    sidecar.writeText(sidecarJson.encodeToString(SessionMetadata.serializer(), this))
}
