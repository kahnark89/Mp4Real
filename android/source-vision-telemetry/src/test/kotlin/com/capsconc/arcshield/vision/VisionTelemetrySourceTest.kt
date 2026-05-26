package com.capsconc.arcshield.vision

import com.capsconc.arcshield.schema.capture.VideoFrame
import com.capsconc.arcshield.schema.llm.ElicitationPrompt
import com.capsconc.arcshield.schema.llm.ElicitationResponse
import com.capsconc.arcshield.schema.llm.GuidanceQuery
import com.capsconc.arcshield.schema.llm.LlmClient
import com.capsconc.arcshield.schema.llm.SensoryContext
import com.capsconc.arcshield.schema.llm.TwinGuidance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class VisionTelemetrySourceTest {

    private fun fakeFrame() = VideoFrame(
        timestampNanos = 1_000_000L,
        widthPx        = 1920,
        heightPx       = 1080,
        yuvData        = ByteArray(1920 * 1080 * 3 / 2),
    )

    private fun makeConfig(vararg channels: ChannelConfig) = VisionTelemetryConfig(
        channels         = channels.toList(),
        sampleIntervalMs = 100L,
    )

    // ---- availableChannels -------------------------------------------------

    @Test
    fun `availableChannels matches config channel ids`() {
        val config = makeConfig(HollowellChannelPresets.MOTOR_RPM, HollowellChannelPresets.SCREW_RPM)
        val source = VisionTelemetrySource(config, FakeLlmClient(200.0))
        assertEquals(setOf("motor_rpm", "screw_rpm"), source.availableChannels)
    }

    // ---- channel() routing -------------------------------------------------

    @Test
    fun `channel with unknown id returns empty flow`() = runTest {
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM),
            FakeLlmClient(100.0),
        )
        source.onFrame(fakeFrame())
        val samples = source.channel("not_a_real_channel").toList()
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `channel emits sample when frame present and LLM returns value`() = runTest {
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM),
            FakeLlmClient(300.0),
            clock = { 5_000_000L },
        )
        source.onFrame(fakeFrame())
        val sample = source.channel("motor_rpm").first()
        assertEquals("motor_rpm", sample.channelId)
        assertEquals(300.0, sample.value, 0.001)
        assertEquals("rpm", sample.unit)
        assertEquals(5_000_000L, sample.timestampNanos)
    }

    @Test
    fun `channel emits no sample when no frame has been provided`() = runTest {
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM),
            FakeLlmClient(100.0),
            clock = { 1L },
        )
        // No onFrame() call — latestFrame is null
        val result = runCatching {
            withTimeout(250L) { source.channel("motor_rpm").first() }
        }
        assertTrue("Expected TimeoutCancellationException with no frame", result.isFailure)
    }

    // ---- mathematical transform --------------------------------------------

    @Test
    fun `screw_rpm divides motor RPM reading by 20 to 1 gearbox ratio`() = runTest {
        val motorRpm = 1000.0
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.SCREW_RPM),
            FakeLlmClient(motorRpm),
            clock = { 1L },
        )
        source.onFrame(fakeFrame())
        val sample = source.channel("screw_rpm").first()
        assertEquals(motorRpm / 20.0, sample.value, 0.001)
        assertEquals("screw_rpm", sample.channelId)
        assertEquals("rpm", sample.unit)
    }

    @Test
    fun `motor_rpm applies identity transform - raw LLM value passed through unchanged`() = runTest {
        val motorRpm = 1000.0
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM),
            FakeLlmClient(motorRpm),
            clock = { 1L },
        )
        source.onFrame(fakeFrame())
        val sample = source.channel("motor_rpm").first()
        assertEquals(motorRpm, sample.value, 0.001)
    }

    @Test
    fun `screw_rpm and motor_rpm differ by gearbox ratio on same reading`() = runTest {
        val llm = FakeLlmClient(600.0)
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM, HollowellChannelPresets.SCREW_RPM),
            llm,
            clock = { 1L },
        )
        source.onFrame(fakeFrame())
        val motorSample = source.channel("motor_rpm").first()
        val screwSample  = source.channel("screw_rpm").first()
        assertEquals(motorSample.value / 20.0, screwSample.value, 0.001)
    }

    // ---- LLM parse failure -------------------------------------------------

    @Test
    fun `null parsedValue from LLM produces no sample`() = runTest {
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM),
            FakeLlmClient(parsedValue = null),
            clock = { 1L },
        )
        source.onFrame(fakeFrame())
        val result = runCatching {
            withTimeout(250L) { source.channel("motor_rpm").first() }
        }
        assertTrue("Expected no sample when LLM returns null parsedValue", result.isFailure)
    }

    // ---- snapshotAt --------------------------------------------------------

    @Test
    fun `snapshotAt with no frame returns empty channels map`() = runTest {
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM),
            FakeLlmClient(100.0),
        )
        val snap = source.snapshotAt(Instant.now())
        assertTrue(snap.channels.isEmpty())
    }

    @Test
    fun `snapshotAt with frame returns all configured channels`() = runTest {
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM, HollowellChannelPresets.SCREW_RPM),
            FakeLlmClient(200.0),
            clock = { 99L },
        )
        source.onFrame(fakeFrame())
        val snap = source.snapshotAt(Instant.now())
        assertEquals(2, snap.channels.size)
        assertTrue(snap.channels.containsKey("motor_rpm"))
        assertTrue(snap.channels.containsKey("screw_rpm"))
        assertEquals(200.0, snap.channels["motor_rpm"]!!.value, 0.001)
        assertEquals(200.0 / 20.0, snap.channels["screw_rpm"]!!.value, 0.001)
        assertEquals(99L, snap.capturedAtNanos)
    }

    @Test
    fun `snapshotAt returns empty snapshot when LLM cannot parse all channels`() = runTest {
        val source = VisionTelemetrySource(
            makeConfig(HollowellChannelPresets.MOTOR_RPM, HollowellChannelPresets.MELT_TEMP_F),
            FakeLlmClient(parsedValue = null),
        )
        source.onFrame(fakeFrame())
        val snap = source.snapshotAt(Instant.now())
        assertTrue(snap.channels.isEmpty())
    }
}

// ---- FakeLlmClient ---------------------------------------------------------

internal class FakeLlmClient(private val parsedValue: Double?) : LlmClient {
    override val providerId = "fake"

    override suspend fun elicit(
        prompt: ElicitationPrompt,
        context: SensoryContext,
    ) = ElicitationResponse(
        rawText      = parsedValue?.toString() ?: "unreadable",
        parsedValue  = parsedValue,
        confidence   = if (parsedValue != null) 0.9f else 0.0f,
    )

    override suspend fun generateGuidance(query: GuidanceQuery) =
        TwinGuidance(advisedAction = "no-op", confidence = 0.0f)
}
