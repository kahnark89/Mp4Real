package com.capsconc.arcshield.source.polar

import android.content.Context
import android.os.SystemClock
import com.capsconc.arcshield.schema.biometric.*
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarSensorSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow  // Flowable implements Publisher — use reactive extension
import kotlinx.coroutines.rx3.await
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * BiometricSource backed by a Polar H10 or Verity Sense via the PMD protocol.
 *
 * Clock model — CLAUDE.md §3.2, §8.2:
 *   All sample timestamps are elapsedRealtimeNanos (phone monotonic clock).
 *   On first PMD frame receipt we record a ClockAnchor:
 *     offset = elapsedRealtimeNanos() − polarFrameTimestamp
 *   Subsequent sample timestamps are reconstructed from the frame's last-sample
 *   timestamp plus sample-index arithmetic, then shifted by offset.
 *   This gives a continuous monotonic timeline that survives wall-clock adjustments.
 *
 * Gap model — CLAUDE.md §8.5:
 *   Every BLE dropout emits an explicit BiometricGap. Nothing is interpolated.
 *   A gap ≥ 4 000 ms sets lowSyncConfidence = true, which propagates upstream
 *   to flag the surrounding capture window as low_sync_confidence.
 *
 * Reconnect:
 *   Exponential backoff (2 s → 4 s → 8 s → 16 s, capped at 30 s).
 *   H10 offline recording backfill is stubbed; see fetchOfflineRecordings().
 *
 * Thread safety:
 *   Polar callbacks arrive on a BLE thread. All mutable state is guarded by
 *   AtomicReference / AtomicLong or written only from within the scope.
 *   Channel.trySend() is safe from any thread.
 */
class PolarBleBiometricSource(
    private val api:        PolarBleApi,
    private val deviceId:   String,
    val deviceType:         PolarDeviceType,
    private val scope:      CoroutineScope,
) : BiometricSource {

    override val sourceId:    String                 = deviceType.sourceId
    override val capabilities: Set<BiometricChannel> = deviceType.capabilities

    // -----------------------------------------------------------------------
    // Clock anchor
    // -----------------------------------------------------------------------

    private data class ClockAnchor(
        val phoneNanos:          Long,
        val deviceNanos:         Long,
        val sampleIntervalNanos: Long,
    ) {
        fun toPhoneNanos(deviceTimestamp: Long): Long =
            phoneNanos + (deviceTimestamp - deviceNanos)
    }

    private val clockAnchor   = AtomicReference<ClockAnchor?>(null)
    private val _syncListener = AtomicReference<((Long, Long) -> Unit)?>(null)

    override fun registerSyncListener(listener: ((phoneNanos: Long, externalNanos: Long) -> Unit)?) {
        _syncListener.set(listener)
    }

    private fun anchorIfNeeded(deviceTimestampNanos: Long, sampleRateHz: Int) {
        val phoneNanos = SystemClock.elapsedRealtimeNanos()
        val newAnchor = ClockAnchor(
            phoneNanos          = phoneNanos,
            deviceNanos         = deviceTimestampNanos,
            sampleIntervalNanos = 1_000_000_000L / sampleRateHz,
        )
        if (clockAnchor.compareAndSet(null, newAnchor)) {
            _syncListener.get()?.invoke(phoneNanos, deviceTimestampNanos)
        }
    }

    /**
     * Reconstruct phone-monotonic timestamps for all samples in a PMD frame.
     * Polar gives the timestamp of the *last* sample in each frame; we work
     * backwards by one sample interval per index.
     */
    private fun frameTimestamps(
        frameLastSampleDeviceNanos: Long,
        frameSize: Int,
        sampleRateHz: Int,
    ): LongArray {
        val anchor = clockAnchor.get()
            ?: return LongArray(frameSize) { SystemClock.elapsedRealtimeNanos() }
        val intervalNanos    = 1_000_000_000L / sampleRateHz
        val lastPhoneNanos   = anchor.toPhoneNanos(frameLastSampleDeviceNanos)
        return LongArray(frameSize) { i -> lastPhoneNanos - (frameSize - 1 - i) * intervalNanos }
    }

    // -----------------------------------------------------------------------
    // Gap channel — CLAUDE.md §8.5
    // Backed by an unlimited channel so a burst of reconnects never drops a gap.
    // -----------------------------------------------------------------------

    private val _gapChannel        = Channel<BiometricGap>(Channel.UNLIMITED)
    private val disconnectedAtNanos = AtomicLong(0L)

    override fun gaps(): Flow<BiometricGap> = _gapChannel.receiveAsFlow()

    // -----------------------------------------------------------------------
    // HR / RR channels — fed by the Polar callback (not PMD streaming)
    // Accel backfill channel — fed only from fetchOfflineRecordings()
    // -----------------------------------------------------------------------

    private val _hrChannel            = Channel<HrSample>(Channel.UNLIMITED)
    private val _rrChannel            = Channel<RrSample>(Channel.UNLIMITED)
    private val _accelBackfillChannel = Channel<AccelSample>(Channel.UNLIMITED)

    // -----------------------------------------------------------------------
    // Polar SDK callback
    // -----------------------------------------------------------------------

    private val polarCallback = object : PolarBleApiCallback() {

        override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
            clockAnchor.set(null)   // re-anchor on next PMD frame after reconnect
            val gapStart = disconnectedAtNanos.getAndSet(0L)
            if (gapStart != 0L) {
                val gapEnd    = SystemClock.elapsedRealtimeNanos()
                val durationMs = (gapEnd - gapStart) / 1_000_000L
                _gapChannel.trySend(BiometricGap(
                    startNanos        = gapStart,
                    endNanos          = gapEnd,
                    durationMs        = durationMs,
                    reason            = GapReason.BLE_DROPOUT,
                    lowSyncConfidence = durationMs >= LOW_SYNC_CONFIDENCE_THRESHOLD_MS,
                ))
                if (deviceType.supportsOfflineRecording) {
                    scope.launch { fetchOfflineRecordings() }
                }
            }
        }

        override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            disconnectedAtNanos.set(SystemClock.elapsedRealtimeNanos())
            startReconnectLoop()
        }

        override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
            if (identifier != deviceId) return
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            _hrChannel.trySend(HrSample(timestampNanos = nowNanos, bpm = data.hr))
            if (data.rrAvailable) {
                data.rrsMs.forEach { rrMs ->
                    _rrChannel.trySend(RrSample(timestampNanos = nowNanos, rrMs = rrMs))
                }
            }
        }

        override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {}
        override fun disInformationReceived(identifier: String, uuid: java.util.UUID, value: String) {}
        override fun batteryLevelReceived(identifier: String, level: Int) {}
        override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {}
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    init {
        api.setApiCallback(polarCallback)
    }

    /** Begin BLE discovery and connection. Call once from the owning component. */
    fun connect() {
        try {
            api.connectToDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            _gapChannel.trySend(BiometricGap(
                startNanos        = SystemClock.elapsedRealtimeNanos(),
                endNanos          = SystemClock.elapsedRealtimeNanos(),
                durationMs        = 0L,
                reason            = GapReason.SYNC_LOST,
                lowSyncConfidence = false,
            ))
        }
    }

    // -----------------------------------------------------------------------
    // Reconnect — exponential backoff, capped at 30 s
    // -----------------------------------------------------------------------

    private fun startReconnectLoop() {
        scope.launch {
            var delayMs = RECONNECT_BASE_DELAY_MS
            while (isActive) {
                delay(delayMs)
                try {
                    api.connectToDevice(deviceId)
                    break
                } catch (_: PolarInvalidArgument) { /* retry */ }
                delayMs = minOf(delayMs * 2L, RECONNECT_MAX_DELAY_MS)
            }
        }
    }

    // -----------------------------------------------------------------------
    // H10 offline recording backfill — CLAUDE.md §8.5
    // H10 stores ~30 min of data internally on link loss.
    // On reconnect we fetch buffered recordings and emit them with reconstructed
    // timestamps before live streaming resumes, so the corpus window has no gap.
    // -----------------------------------------------------------------------

    private suspend fun fetchOfflineRecordings() {
        // Convert a Polar wall-clock epoch nanoseconds to phone monotonic nanoseconds.
        // Polar PMD timestamps are nanoseconds since the UNIX epoch; apply the
        // phone wall-clock → elapsedRealtime offset computed at call time.
        val wallOffsetNanos = System.currentTimeMillis() * 1_000_000L - SystemClock.elapsedRealtimeNanos()
        fun polarEpochNanosToPhone(polarNanos: Long): Long {
            val anchor = clockAnchor.get()
            return if (anchor != null) anchor.toPhoneNanos(polarNanos)
                   else polarNanos - wallOffsetNanos
        }

        try {
            val entries = api.listOfflineRecordings(deviceId).asFlow().toList()
            for (entry in entries) {
                @Suppress("UNCHECKED_CAST")
                val recording = api.getOfflineRecord(deviceId, entry, null).await()
                when (recording) {
                    is PolarOfflineRecordingData.HrOfflineRecording -> {
                        // HR offline samples have no individual timestamps.
                        // H10 records HR at ~1 Hz; space them by HR_SAMPLE_INTERVAL_NANOS.
                        val startNanos = recording.startTime.timeInMillis * 1_000_000L - wallOffsetNanos
                        recording.data.samples.forEachIndexed { i, sample ->
                            val phoneNanos = startNanos + i * HR_SAMPLE_INTERVAL_NANOS
                            _hrChannel.trySend(HrSample(timestampNanos = phoneNanos, bpm = sample.hr))
                            if (sample.rrAvailable) {
                                var cumNanos = phoneNanos
                                sample.rrsMs.forEach { rrMs ->
                                    cumNanos += rrMs * 1_000_000L
                                    _rrChannel.trySend(RrSample(timestampNanos = cumNanos, rrMs = rrMs))
                                }
                            }
                        }
                    }
                    is PolarOfflineRecordingData.PpiOfflineRecording -> {
                        // PPI = peak-to-peak interval in ms; equivalent to R-R.
                        // Cumulate intervals from startTime for individual beat timestamps.
                        var cumNanos = recording.startTime.timeInMillis * 1_000_000L - wallOffsetNanos
                        recording.data.samples.forEach { sample ->
                            cumNanos += sample.ppi * 1_000_000L
                            _rrChannel.trySend(RrSample(timestampNanos = cumNanos, rrMs = sample.ppi))
                        }
                    }
                    is PolarOfflineRecordingData.AccOfflineRecording -> {
                        // ACC offline samples carry per-sample Polar epoch timestamps;
                        // emit into the shared accel backfill channel.
                        recording.data.samples.forEach { sample ->
                            _accelBackfillChannel.trySend(AccelSample(
                                timestampNanos = polarEpochNanosToPhone(sample.timeStamp),
                                xMg            = sample.x.toFloat(),
                                yMg            = sample.y.toFloat(),
                                zMg            = sample.z.toFloat(),
                            ))
                        }
                    }
                    else -> { /* gyro / mag / ppg not consumed by this source */ }
                }
            }
        } catch (_: Exception) { /* offline backfill is best-effort; dropout gap already emitted */ }
    }

    // -----------------------------------------------------------------------
    // BiometricSource — flow implementations
    // -----------------------------------------------------------------------

    override fun heartRate(): Flow<HrSample> = _hrChannel.receiveAsFlow()

    override fun rrIntervals(): Flow<RrSample> = _rrChannel.receiveAsFlow()

    override fun ecgWaveform(): Flow<EcgSample> {
        if (BiometricChannel.ECG_WAVEFORM !in capabilities) return emptyFlow()
        return flow {
            val settings = api
                .requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
                .await()
            api.startEcgStreaming(deviceId, settings).asFlow().collect { frame: PolarEcgData ->
                anchorIfNeeded(frame.timeStamp, ECG_SAMPLE_RATE_HZ)
                val ts = frameTimestamps(frame.timeStamp, frame.samples.size, ECG_SAMPLE_RATE_HZ)
                frame.samples.forEachIndexed { i, sample ->
                    emit(EcgSample(timestampNanos = ts[i], microVolts = sample.voltage))
                }
            }
        }
    }

    override fun accelerometer(): Flow<AccelSample> = merge(
        _accelBackfillChannel.receiveAsFlow(),
        flow {
            val settings = api
                .requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
                .await()
            val sampleRateHz = settings.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]
                ?.firstOrNull() ?: ACC_DEFAULT_SAMPLE_RATE_HZ
            api.startAccStreaming(deviceId, settings).asFlow().collect { frame: PolarAccelerometerData ->
                anchorIfNeeded(frame.timeStamp, sampleRateHz)
                val ts = frameTimestamps(frame.timeStamp, frame.samples.size, sampleRateHz)
                frame.samples.forEachIndexed { i, sample ->
                    emit(AccelSample(
                        timestampNanos = ts[i],
                        xMg = sample.x.toFloat(),
                        yMg = sample.y.toFloat(),
                        zMg = sample.z.toFloat(),
                    ))
                }
            }
        },
    )

    /** EDA is permanently absent from all Polar devices — CLAUDE.md §8.1. */
    override fun edaWaveform(): Flow<EdaSample> = emptyFlow()

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    companion object {
        private const val ECG_SAMPLE_RATE_HZ               = 130
        private const val ACC_DEFAULT_SAMPLE_RATE_HZ       = 200
        private const val LOW_SYNC_CONFIDENCE_THRESHOLD_MS = 4_000L
        private const val RECONNECT_BASE_DELAY_MS          = 2_000L
        private const val RECONNECT_MAX_DELAY_MS           = 30_000L
        private const val HR_SAMPLE_INTERVAL_NANOS         = 1_000_000_000L  // H10 offline HR at ~1 Hz

        fun create(
            context:    Context,
            deviceId:   String,
            deviceType: PolarDeviceType,
            scope:      CoroutineScope,
        ): PolarBleBiometricSource {
            val api = PolarBleApiDefaultImpl.defaultImplementation(
                context,
                setOf(
                    PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                    PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                    PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                ),
            )
            return PolarBleBiometricSource(api, deviceId, deviceType, scope)
        }
    }
}
