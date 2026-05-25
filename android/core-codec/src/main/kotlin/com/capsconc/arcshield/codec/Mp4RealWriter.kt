package com.capsconc.arcshield.codec

import java.io.File

/**
 * Orchestrates writing a single mp4Real container session — CLAUDE.md §3, FIG. 6.
 *
 * Separation of responsibilities:
 *   core-codec ([Mp4RealWriter]) — container format, PTS, ε_sync sidecar.
 *   core-capture (ring buffer + mux pipeline) — video/audio encoding, ring buffer
 *     windowing, and the decision of which samples to hand to this writer.
 *
 * Workflow:
 *  1. [addVideoTrack] / [addAudioTrack] / [addMetaTrack] — declare all tracks.
 *  2. [start] — opens the muxer with the session start timestamp (PTS anchor).
 *  3. [writeSample] / [writeMetaSample] — write encoded data from the ring buffer.
 *     I-frame samples (baseline period) arrive first; P-frame samples (event
 *     windows) follow. This writer is agnostic to the distinction — it writes
 *     whatever core-capture delivers.
 *  4. [close] — finalises the muxer, writes the .mp4real.json sidecar, returns
 *     updated [SessionMetadata] with ε_sync and track map.
 *
 * Not thread-safe. All calls must arrive from the same mux-pipeline coroutine/thread.
 */
class Mp4RealWriter(
    private val muxer: Mp4RealMuxer,
    private val metadata: SessionMetadata,
    private val outputFile: File,
) {
    private val trackIndices = mutableMapOf<TrackType, Int>()
    private var sessionStartNanos: Long = 0L
    private var started = false

    // ---- Track registration (must complete before start()) ---------------

    fun addVideoTrack(
        width: Int,
        height: Int,
        frameRate: Int = 30,
        csd0: ByteArray = ByteArray(0),
    ) {
        checkNotStarted("addVideoTrack")
        trackIndices[TrackType.Video] = muxer.addVideoTrack(width, height, frameRate, csd0)
    }

    fun addAudioTrack(
        sampleRate: Int = 48_000,
        channelCount: Int = 1,
        csd0: ByteArray = ByteArray(0),
    ) {
        checkNotStarted("addAudioTrack")
        trackIndices[TrackType.Audio] = muxer.addAudioTrack(sampleRate, channelCount, csd0)
    }

    fun addMetaTrack(trackType: TrackType) {
        checkNotStarted("addMetaTrack")
        require(trackType !is TrackType.Video && trackType !is TrackType.Audio) {
            "Use addVideoTrack / addAudioTrack for ${trackType.description}"
        }
        trackIndices[trackType] = muxer.addMetaTrack(trackType)
    }

    // ---- Session lifecycle -----------------------------------------------

    /**
     * Opens the muxer. [sessionStartNanos] is elapsedRealtimeNanos at shift start;
     * it becomes the PTS=0 anchor for all subsequent samples.
     */
    fun start(sessionStartNanos: Long) {
        check(!started) { "start() called twice on the same Mp4RealWriter" }
        this.sessionStartNanos = sessionStartNanos
        muxer.start()
        started = true
    }

    // ---- Sample writes ---------------------------------------------------

    /**
     * Writes one encoded video or audio sample.
     * [presentationTimeNanos] is the raw elapsedRealtimeNanos capture timestamp;
     * this writer passes it through to the muxer unchanged (core-capture is
     * responsible for choosing which samples fall within the I/P-frame windows).
     */
    fun writeSample(
        trackType: TrackType,
        presentationTimeNanos: Long,
        data: ByteArray,
        isKeyFrame: Boolean = false,
    ) {
        checkStarted("writeSample")
        muxer.writeSample(requireIndex(trackType), presentationTimeNanos, data, isKeyFrame)
    }

    /**
     * Writes one timed-metadata sample. [payload] should be JSON-UTF-8 in Phase 1.
     */
    fun writeMetaSample(
        trackType: TrackType,
        presentationTimeNanos: Long,
        payload: ByteArray,
    ) {
        checkStarted("writeMetaSample")
        muxer.writeMetaSample(requireIndex(trackType), presentationTimeNanos, payload)
    }

    // ---- Close -----------------------------------------------------------

    /**
     * Finalises the muxer, writes the .mp4real.json sidecar, and returns the
     * completed [SessionMetadata] with ε_sync and track map populated.
     *
     * [finalEpsSyncNanos] is the session-level ε_sync measured via the NTP-style
     * handshake in core-capture. Pass 0L when no external clock was present
     * (phone-only session — all tracks share the phone clock, so ε_sync is 0).
     */
    fun close(finalEpsSyncNanos: Long = 0L): SessionMetadata {
        checkStarted("close")
        muxer.stop()
        muxer.release()
        started = false

        val trackMap = trackIndices.entries
            .associate { (type, idx) -> type.description to idx.toString() }

        val finalMeta = metadata.copy(
            epsSyncNanos      = finalEpsSyncNanos,
            lowSyncConfidence = finalEpsSyncNanos > EpsSyncMeasure.LOW_SYNC_NS,
            trackMap          = trackMap,
        )
        finalMeta.writeSidecar(outputFile)
        return finalMeta
    }

    // ---- Helpers ---------------------------------------------------------

    private fun requireIndex(trackType: TrackType): Int =
        trackIndices[trackType]
            ?: error("Track '${trackType.description}' was not registered before start()")

    private fun checkNotStarted(caller: String) =
        check(!started) { "$caller() called after start()" }

    private fun checkStarted(caller: String) =
        check(started) { "$caller() called before start()" }
}
