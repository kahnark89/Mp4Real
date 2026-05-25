package com.capsconc.arcshield.schema.llm

import com.capsconc.arcshield.schema.capture.VideoFrame

// Phase 1 minimal elicitation types. SensoryContext carries whatever multimodal
// data the active implementation needs. Full advisory (generateGuidance) is wired
// when the Twin layer becomes active in Phase 3.

data class SensoryContext(
    val frame: VideoFrame? = null,
    val audioRmsDb: Float? = null,
    val heartRateBpm: Int? = null,
)

data class ElicitationPrompt(
    val text: String,
    val channelId: String = "",
)

data class ElicitationResponse(
    val rawText: String,
    val parsedValue: Double? = null,
    val confidence: Float = 1.0f,
)

data class GuidanceQuery(val contextText: String)

data class TwinGuidance(
    val advisedAction: String,
    val confidence: Float,
)

interface LlmClient {
    suspend fun elicit(prompt: ElicitationPrompt, context: SensoryContext): ElicitationResponse
    suspend fun generateGuidance(query: GuidanceQuery): TwinGuidance
    val providerId: String
}
