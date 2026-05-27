package com.capsconc.arcshield.app.capture

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.capsconc.arcshield.schema.capture.CaptureSource
import com.capsconc.arcshield.schema.capture.CaptureSourceFactory
import com.capsconc.arcshield.source.camerax.CameraXCaptureSource
import com.capsconc.arcshield.source.meta.MetaRayBansCaptureSource

class DefaultCaptureSourceFactory(
    private val context: Context,
    private val glassesDeviceMac: String,
) : CaptureSourceFactory {

    override fun create(lifecycleOwner: LifecycleOwner): CaptureSource =
        if (MetaRayBansCaptureSource.isAvailable(context, glassesDeviceMac))
            MetaRayBansCaptureSource(context, lifecycleOwner, glassesDeviceMac)
        else
            CameraXCaptureSource(context, lifecycleOwner)
}
