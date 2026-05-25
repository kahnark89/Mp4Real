package com.capsconc.arcshield.codec

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class Mp4RealWriterTest {

    private fun makeMetadata() = SessionMetadata(
        sessionId         = "test-session-001",
        operatorId        = "kahn_test",
        facilityId        = "hollowell_ppvc_line1",
        lineId            = "ppvc_line_1",
        captureSourceId   = "phone_cameraX_v1",
        biometricSourceId = "polar_h10_v1",
        sessionStartNanos = 1_000_000_000L,
    )

    private fun tempDir() = Files.createTempDirectory("core_codec_test").toFile()

    // ---- Track registration ---------------------------------------------

    @Test
    fun `add video and audio tracks — muxer receives both before start`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))

        writer.addVideoTrack(1920, 1080)
        writer.addAudioTrack()
        writer.start(0L)

        assertEquals(2, fake.tracks.size)
        assertTrue(fake.tracks[0].startsWith("video:"))
        assertTrue(fake.tracks[1].startsWith("audio:"))
        assertTrue(fake.started)
    }

    @Test
    fun `add meta track — registered with correct description`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))

        writer.addMetaTrack(TrackType.BiometricMeta)
        writer.start(0L)

        assertEquals(1, fake.tracks.size)
        assertTrue(fake.tracks[0].contains("biometric"))
    }

    @Test
    fun `addVideoTrack after start throws`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.start(0L)

        try {
            writer.addVideoTrack(1920, 1080)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("after start"))
        }
    }

    @Test
    fun `addMetaTrack with Video type throws`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        try {
            writer.addMetaTrack(TrackType.Video)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected — use addVideoTrack for video
        }
    }

    // ---- writeSample routing -------------------------------------------

    @Test
    fun `writeSample routes to correct track index`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.addAudioTrack()
        writer.start(0L)

        val videoData = byteArrayOf(1, 2, 3)
        writer.writeSample(TrackType.Video, 500_000_000L, videoData, isKeyFrame = true)

        assertEquals(1, fake.writtenSamples.size)
        val s = fake.writtenSamples[0]
        assertEquals(0, s.trackIndex)   // video registered first → index 0
        assertEquals(500_000_000L, s.presentationTimeNanos)
        assertTrue(s.isKeyFrame)
        assertArrayEquals(videoData, s.data)
    }

    @Test
    fun `writeSample audio uses index 1 when video registered first`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.addAudioTrack()
        writer.start(0L)

        writer.writeSample(TrackType.Audio, 1_000_000_000L, byteArrayOf(0xAA.toByte()))

        val s = fake.writtenSamples[0]
        assertEquals(1, s.trackIndex)   // audio registered second → index 1
        assertFalse(s.isKeyFrame)
    }

    @Test
    fun `writeSample before start throws`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        writer.addVideoTrack(1920, 1080)

        try {
            writer.writeSample(TrackType.Video, 0L, byteArrayOf())
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("before start"))
        }
    }

    @Test
    fun `writeSample for unregistered track throws`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.start(0L)

        try {
            writer.writeSample(TrackType.Audio, 0L, byteArrayOf())
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not registered"))
        }
    }

    // ---- writeMetaSample -----------------------------------------------

    @Test
    fun `writeMetaSample routes payload to correct meta track`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        writer.addMetaTrack(TrackType.BiometricMeta)
        writer.addMetaTrack(TrackType.AccelMeta)
        writer.start(0L)

        val bioPayload  = """{"bpm":72,"rmssd":42.1}""".toByteArray()
        val accelPayload = """{"x":0.1,"y":0.2,"z":9.8}""".toByteArray()
        writer.writeMetaSample(TrackType.BiometricMeta, 100_000_000L, bioPayload)
        writer.writeMetaSample(TrackType.AccelMeta,     200_000_000L, accelPayload)

        assertEquals(2, fake.writtenMetaSamples.size)
        val bio = fake.writtenMetaSamples[0]
        assertEquals(0, bio.trackIndex)
        assertEquals(100_000_000L, bio.presentationTimeNanos)
        assertArrayEquals(bioPayload, bio.payload)

        val accel = fake.writtenMetaSamples[1]
        assertEquals(1, accel.trackIndex)
        assertArrayEquals(accelPayload, accel.payload)
    }

    @Test
    fun `writeMetaSample before start throws`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/test.mp4"))
        writer.addMetaTrack(TrackType.BiometricMeta)

        try {
            writer.writeMetaSample(TrackType.BiometricMeta, 0L, byteArrayOf())
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("before start"))
        }
    }

    // ---- close / sidecar -----------------------------------------------

    @Test
    fun `close stops and releases muxer`() {
        val dir = tempDir()
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File(dir, "s.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.start(0L)

        writer.close()

        assertTrue(fake.stopped)
        assertTrue(fake.released)
        dir.deleteRecursively()
    }

    @Test
    fun `close writes sidecar alongside container`() {
        val dir = tempDir()
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File(dir, "session.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.start(0L)

        writer.close()

        assertTrue(java.io.File(dir, "session.mp4real.json").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `close returns metadata with ε_sync and track map`() {
        val dir = tempDir()
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File(dir, "s.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.addMetaTrack(TrackType.BiometricMeta)
        writer.start(0L)

        val final = writer.close(finalEpsSyncNanos = 80_000_000L)

        assertEquals(80_000_000L, final.epsSyncNanos)
        assertFalse(final.lowSyncConfidence)
        assertTrue(final.trackMap.containsKey(TrackType.Video.description))
        assertTrue(final.trackMap.containsKey(TrackType.BiometricMeta.description))
        dir.deleteRecursively()
    }

    @Test
    fun `close flags low sync confidence when ε_sync exceeds 250ms`() {
        val dir = tempDir()
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File(dir, "s.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.start(0L)

        val final = writer.close(finalEpsSyncNanos = 300_000_000L)

        assertTrue(final.lowSyncConfidence)
        dir.deleteRecursively()
    }

    @Test
    fun `close before start throws`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/s.mp4"))

        try {
            writer.close()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("before start"))
        }
    }

    @Test
    fun `sidecar JSON contains session id and facility id`() {
        val dir = tempDir()
        val fake = FakeMuxer()
        val meta = makeMetadata()
        val writer = Mp4RealWriter(fake, meta, java.io.File(dir, "s.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.start(0L)
        writer.close()

        val sidecar = java.io.File(dir, "s.mp4real.json").readText()
        assertTrue(sidecar.contains(meta.sessionId))
        assertTrue(sidecar.contains(meta.facilityId))
        dir.deleteRecursively()
    }

    // ---- multiple samples in sequence ----------------------------------

    @Test
    fun `I-frame then P-frame samples both reach muxer in order`() {
        val fake = FakeMuxer()
        val writer = Mp4RealWriter(fake, makeMetadata(), java.io.File("/tmp/s.mp4"))
        writer.addVideoTrack(1920, 1080)
        writer.addMetaTrack(TrackType.AccelMeta)
        writer.start(0L)

        // Simulate I-frame samples (baseline period, PTS near 0)
        writer.writeSample(TrackType.Video, 0L,           byteArrayOf(1), isKeyFrame = true)
        writer.writeMetaSample(TrackType.AccelMeta, 10_000_000L, """{"x":0}""".toByteArray())

        // Simulate P-frame samples (post gate-fire, PTS later in shift)
        writer.writeSample(TrackType.Video, 120_000_000_000L, byteArrayOf(2))
        writer.writeMetaSample(TrackType.AccelMeta, 120_010_000_000L, """{"x":1}""".toByteArray())

        assertEquals(2, fake.writtenSamples.size)
        assertEquals(0L,              fake.writtenSamples[0].presentationTimeNanos)
        assertEquals(120_000_000_000L, fake.writtenSamples[1].presentationTimeNanos)

        assertEquals(2, fake.writtenMetaSamples.size)
        assertEquals(10_000_000L,       fake.writtenMetaSamples[0].presentationTimeNanos)
        assertEquals(120_010_000_000L,  fake.writtenMetaSamples[1].presentationTimeNanos)
    }
}

// ---- FakeMuxer ---------------------------------------------------------

internal class FakeMuxer : Mp4RealMuxer {

    data class WrittenSample(
        val trackIndex: Int,
        val presentationTimeNanos: Long,
        val data: ByteArray,
        val isKeyFrame: Boolean,
    )

    data class WrittenMetaSample(
        val trackIndex: Int,
        val presentationTimeNanos: Long,
        val payload: ByteArray,
    )

    val tracks             = mutableListOf<String>()
    val writtenSamples     = mutableListOf<WrittenSample>()
    val writtenMetaSamples = mutableListOf<WrittenMetaSample>()
    var started  = false
    var stopped  = false
    var released = false

    override fun addVideoTrack(width: Int, height: Int, frameRate: Int, csd0: ByteArray): Int {
        tracks.add("video:${width}x${height}@${frameRate}")
        return tracks.size - 1
    }

    override fun addAudioTrack(sampleRate: Int, channelCount: Int, csd0: ByteArray): Int {
        tracks.add("audio:${sampleRate}/${channelCount}ch")
        return tracks.size - 1
    }

    override fun addMetaTrack(trackType: TrackType): Int {
        tracks.add("meta:${trackType.description}")
        return tracks.size - 1
    }

    override fun start()   { started  = true }
    override fun stop()    { stopped  = true }
    override fun release() { released = true }

    override fun writeSample(
        trackIndex: Int,
        presentationTimeNanos: Long,
        data: ByteArray,
        isKeyFrame: Boolean,
    ) {
        writtenSamples.add(WrittenSample(trackIndex, presentationTimeNanos, data, isKeyFrame))
    }

    override fun writeMetaSample(
        trackIndex: Int,
        presentationTimeNanos: Long,
        payload: ByteArray,
    ) {
        writtenMetaSamples.add(WrittenMetaSample(trackIndex, presentationTimeNanos, payload))
    }
}
