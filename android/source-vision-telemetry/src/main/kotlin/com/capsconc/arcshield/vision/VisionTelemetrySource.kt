package com.capsconc.arcshield.vision

import com.capsconc.arcshield.schema.capture.VideoFrame
import com.capsconc.arcshield.schema.llm.ElicitationPrompt
import com.capsconc.arcshield.schema.llm.LlmClient
import com.capsconc.arcshield.schema.llm.SensoryContext
import com.capsconc.arcshield.schema.telemetry.PlcTelemetrySource
import com.capsconc.arcshield.schema.telemetry.TelemetrySample
import com.capsconc.arcshield.schema.telemetry.TelemetrySnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

// VisionTelemetrySource extracts process telemetry from camera frames using
// LLM vision rather than a PLC API. This unblocks R_phys extraction in
// environments where PLC access is gated on IT/vendor approvals.
//
// Conforms to PlcTelemetrySource so core-capture and core-codec consumers
// never know whether readings come from PLC comms or optical recognition.
//
// Call onFrame() from a CaptureSource video producer coroutine to feed frames.
// The source retains only the most recent frame — intermediate frames between
// sample intervals are dropped intentionally (rate-distortion gate).
class VisionTelemetrySource(
    private val config: VisionTelemetryConfig,
    private val llmClient: LlmClient,
    private val clock: () -> Long = System::nanoTime,
) : PlcTelemetrySource {

    private val channelMap: Map<String, ChannelConfig> =
        config.channels.associateBy { it.channelId }

    private val latestFrame = AtomicReference<VideoFrame?>(null)

    override val availableChannels: Set<String> = channelMap.keys

    fun onFrame(frame: VideoFrame) {
        latestFrame.set(frame)
    }

    override fun channel(channelId: String): Flow<TelemetrySample> {
        val cfg = channelMap[channelId] ?: return emptyFlow()
        return flow {
            while (true) {
                val frame = latestFrame.get()
                if (frame != null) {
                    val sample = readSample(cfg, frame)
                    if (sample != null) emit(sample)
                }
                delay(config.sampleIntervalMs)
            }
        }
    }

    // Takes a fresh optical reading across all configured channels.
    // The t parameter is accepted for interface compliance; this implementation
    // always reads the most recent retained frame.
    override suspend fun snapshotAt(t: Instant): TelemetrySnapshot {
        val frame = latestFrame.get()
        val results = mutableMapOf<String, TelemetrySample>()
        if (frame != null) {
            for (cfg in config.channels) {
                val sample = readSample(cfg, frame)
                if (sample != null) results[cfg.channelId] = sample
            }
        }
        return TelemetrySnapshot(
            capturedAtNanos = clock(),
            channels = results,
        )
    }

    private suspend fun readSample(cfg: ChannelConfig, frame: VideoFrame): TelemetrySample? {
        val response = llmClient.elicit(
            prompt  = ElicitationPrompt(text = cfg.promptText, channelId = cfg.channelId),
            context = SensoryContext(frame = frame),
        )
        val rawValue = response.parsedValue ?: return null
        return TelemetrySample(
            timestampNanos = clock(),
            channelId      = cfg.channelId,
            value          = cfg.transform(rawValue),
            unit           = cfg.unit,
        )
    }
}
