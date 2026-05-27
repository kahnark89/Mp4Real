package com.capsconc.arcshield.source.meta

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.capsconc.arcshield.schema.capture.AudioFrame
import com.capsconc.arcshield.schema.capture.CaptureSource
import com.capsconc.arcshield.schema.capture.VideoFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Phase 1 stub for Meta Ray-Ban glasses capture.
 *
 * videoFrames() and audioFrames() are emptyFlow() — Meta SDK integration is deferred to Phase 2.
 * These code paths are unreachable in Phase 1 because GLASSES_DEVICE_ID is never set.
 * Detection is via BLE bond check (synchronous, no scan needed).
 */
class MetaRayBansCaptureSource(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") lifecycleOwner: LifecycleOwner,
    val deviceMac: String,
) : CaptureSource {

    override val sourceId: String = "meta_raybans_v1"

    // Phase 2: replace with Meta SDK stream
    override fun videoFrames(): Flow<VideoFrame> = emptyFlow()

    // Phase 2: replace with Meta SDK audio
    override fun audioFrames(): Flow<AudioFrame> = emptyFlow()

    companion object {
        @SuppressLint("MissingPermission")
        fun isAvailable(context: Context, deviceMac: String): Boolean {
            if (deviceMac.isBlank()) return false
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return false
            val adapter: BluetoothAdapter = manager.adapter ?: return false
            return adapter.bondedDevices?.any {
                it.address.equals(deviceMac, ignoreCase = true)
            } == true
        }
    }
}
