package com.capsconc.arcshield.labeler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured output from the voice-first elicitation pipeline (W-015).
 *
 * Produced when an operator describes a candidate window by voice; the raw
 * transcript is run through the entity-resolution LLM call which normalizes
 * operator jargon to canonical facility hardware IDs.
 *
 * Stored in the .labels.json export alongside TP/FP decisions.
 */
@Serializable
data class ElicitedAction(
    @SerialName("action_target")       val actionTarget:        String,   // canonical hardware node ID
    @SerialName("action_type")         val actionType:          String,   // canonical action enum string
    @SerialName("canonical_intuition") val canonicalIntuition:  String,   // normalized causal hypothesis
    @SerialName("voice_transcript")    val voiceTranscript:     String,   // raw STT output
    @SerialName("confidence")          val confidence:          Float,    // LLM entity-resolution confidence
)
