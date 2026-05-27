package com.capsconc.arcshield.labeler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.capsconc.arcshield.schema.llm.ElicitationPrompt
import com.capsconc.arcshield.schema.llm.LlmClient
import com.capsconc.arcshield.schema.llm.SensoryContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Voice-first elicitation pipeline (W-015, CLAUDE.md §3):
 *   1. TTS speaks a prompt to the operator.
 *   2. STT captures the operator's natural-language response.
 *   3. Claude entity-resolution call maps jargon → canonical hardware IDs.
 *
 * [llmClient] is the app-level LlmClient (ClaudeVisionClient in Phase 1).
 * Call [release] when the owning ViewModel is cleared.
 *
 * Thread safety: [elicit] may be called from any coroutine; TTS init is
 * fire-and-forget; STT setup is dispatched to [Dispatchers.Main] internally.
 */
class VoiceElicitationManager(
    private val context: Context,
    private val llmClient: LlmClient,
) {
    private val ttsReady = CompletableDeferred<Unit>()
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) ttsReady.complete(Unit)
        else ttsReady.completeExceptionally(IllegalStateException("TTS init failed: $status"))
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Full pipeline: speak [prompt], listen for operator response, resolve entities.
     * Returns [ElicitedAction] on success or a fallback with the raw transcript on error.
     */
    suspend fun elicit(prompt: String): ElicitedAction {
        speak(prompt)
        val transcript = listen()
        return if (transcript.isBlank()) {
            ElicitedAction("unknown", "UNKNOWN", "No response captured", "", 0f)
        } else {
            resolveEntities(transcript)
        }
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }

    // -----------------------------------------------------------------------
    // TTS
    // -----------------------------------------------------------------------

    private suspend fun speak(text: String) {
        ttsReady.await()
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { done.complete(Unit) }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        done.await()
    }

    // -----------------------------------------------------------------------
    // STT
    // -----------------------------------------------------------------------

    private suspend fun listen(): String = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return@withContext ""
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(bundle: Bundle?) {
                    val text = bundle
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    recognizer.destroy()
                    if (cont.isActive) cont.resume(text)
                }
                override fun onError(error: Int) {
                    recognizer.destroy()
                    if (cont.isActive) cont.resume("")
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            recognizer.startListening(intent)
            cont.invokeOnCancellation {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Entity resolution via LLM
    // -----------------------------------------------------------------------

    private suspend fun resolveEntities(transcript: String): ElicitedAction {
        val fullPrompt = "$ENTITY_RESOLUTION_PROMPT\n\nOperator: $transcript"
        return try {
            val response = llmClient.elicit(
                ElicitationPrompt(text = fullPrompt, channelId = "voice_elicitation"),
                SensoryContext(),
            )
            parseResponse(response.rawText, transcript)
        } catch (_: Exception) {
            ElicitedAction("unknown", "UNKNOWN", transcript, transcript, 0f)
        }
    }

    private fun parseResponse(raw: String, transcript: String): ElicitedAction {
        // Extract the first JSON object from the response (model may add surrounding prose)
        val jsonStart = raw.indexOf('{')
        val jsonEnd   = raw.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1) {
            return ElicitedAction("unknown", "UNKNOWN", transcript, transcript, 0f)
        }
        return try {
            val obj = Json.parseToJsonElement(raw.substring(jsonStart, jsonEnd + 1)).jsonObject
            ElicitedAction(
                actionTarget       = obj["action_target"]?.jsonPrimitive?.content ?: "unknown",
                actionType         = obj["action_type"]?.jsonPrimitive?.content   ?: "UNKNOWN",
                canonicalIntuition = obj["canonical_intuition"]?.jsonPrimitive?.content ?: transcript,
                voiceTranscript    = transcript,
                confidence         = obj["confidence"]?.jsonPrimitive?.float ?: 0f,
            )
        } catch (_: Exception) {
            ElicitedAction("unknown", "UNKNOWN", transcript, transcript, 0f)
        }
    }

    // -----------------------------------------------------------------------
    // System prompt — Hollowell Line 1 jargon map
    // -----------------------------------------------------------------------

    private companion object {
        const val ENTITY_RESOLUTION_PROMPT = """You are an entity-resolution engine for a PVC extrusion line (Hollowell Line 1, PPVC).
Parse the operator's voice input and respond with ONLY a valid JSON object — no prose, no markdown.

Required keys:
  action_target        – canonical hardware node ID (string)
  action_type          – one of: INCREASE DECREASE INSPECT ADJUST CLEAR_BLOCKAGE REDUCE_SPEED INCREASE_SPEED CHANGE_MATERIAL CALL_MAINTENANCE (string)
  canonical_intuition  – one-sentence normalized causal hypothesis (string)
  confidence           – your confidence in the mapping, 0.0–1.0 (float)

Hollowell Line 1 jargon → canonical ID:
  "front die bolts" / "die bolts" / "adjust bolts"          → "hollowell.ppvc.die_face.bolts"
  "barrel temp" / "heater" / "zone temp" / "barrel zones"   → "hollowell.ppvc.barrel.zone_temp"
  "screw speed" / "rpms" / "extruder speed"                 → "hollowell.ppvc.extruder.screw_speed_rpm"
  "feed throat" / "hopper" / "feeder"                       → "hollowell.ppvc.feed_throat"
  "puller" / "haul-off" / "pull speed"                      → "hollowell.ppvc.haul_off.speed"
  "water bath" / "sizing tank" / "cooling"                  → "hollowell.ppvc.cooling.water_bath"
  "head pressure" / "die pressure" / "melt pressure"        → "hollowell.ppvc.die.melt_pressure_bar"
  "cutter" / "saw" / "cut-off"                              → "hollowell.ppvc.cutter"
  "vacuum" / "sizing vacuum"                                 → "hollowell.ppvc.cooling.vacuum_bar"
  "material" / "compound" / "resin"                         → "hollowell.ppvc.material_batch"

If the jargon does not match any known term, prefix with "unknown." and use the verbatim term.

Example output: {"action_target":"hollowell.ppvc.die_face.bolts","action_type":"ADJUST","canonical_intuition":"Die face non-uniformity causing wall thickness variation; operator adjusting die bolts to re-center melt flow.","confidence":0.92}"""
    }
}
