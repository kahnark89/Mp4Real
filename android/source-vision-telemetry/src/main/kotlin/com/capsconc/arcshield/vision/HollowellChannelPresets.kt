package com.capsconc.arcshield.vision

// Predefined channel configs for PPVC Line 1 at Hollowell Industries.
// MOTOR_RPM and SCREW_RPM share the same prompt — one reads the gauge,
// the other divides by the 20:1 gearbox ratio. Both channels are stored
// so consumers can query either the motor-side or screw-side value.
object HollowellChannelPresets {

    private const val GEARBOX_RATIO = 20.0

    private const val MOTOR_RPM_PROMPT =
        "Read the motor speed (RPM) gauge in this image. " +
        "Return only the numeric value — no units, no text, just the number."

    val MOTOR_RPM = ChannelConfig(
        channelId    = "motor_rpm",
        displayName  = "Motor RPM (raw)",
        unit         = "rpm",
        promptText   = MOTOR_RPM_PROMPT,
    )

    val SCREW_RPM = ChannelConfig(
        channelId    = "screw_rpm",
        displayName  = "Screw RPM",
        unit         = "rpm",
        promptText   = MOTOR_RPM_PROMPT,
        transform    = { motorRpm -> motorRpm / GEARBOX_RATIO },
    )

    val MELT_TEMP_F = ChannelConfig(
        channelId    = "melt_temp_f",
        displayName  = "Melt Temperature",
        unit         = "°F",
        promptText   = "Read the melt temperature gauge in this image. " +
                       "Return only the numeric value in Fahrenheit.",
    )

    val LINE_SPEED_FPM = ChannelConfig(
        channelId    = "line_speed_fpm",
        displayName  = "Line Speed",
        unit         = "fpm",
        promptText   = "Read the line speed gauge in this image in feet per minute. " +
                       "Return only the numeric value.",
    )

    val ppvcLine1 = VisionTelemetryConfig(
        channels = listOf(MOTOR_RPM, SCREW_RPM, MELT_TEMP_F, LINE_SPEED_FPM),
    )
}
