package com.capsconc.arcshield.vision

data class ChannelConfig(
    val channelId: String,
    val displayName: String,
    val unit: String,
    val promptText: String,
    val transform: (Double) -> Double = { it },
)
