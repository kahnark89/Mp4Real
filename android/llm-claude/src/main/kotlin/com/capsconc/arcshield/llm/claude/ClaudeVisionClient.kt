package com.capsconc.arcshield.llm.claude

import com.capsconc.arcshield.schema.llm.ElicitationPrompt
import com.capsconc.arcshield.schema.llm.ElicitationResponse
import com.capsconc.arcshield.schema.llm.GuidanceQuery
import com.capsconc.arcshield.schema.llm.LlmClient
import com.capsconc.arcshield.schema.llm.SensoryContext
import com.capsconc.arcshield.schema.llm.TwinGuidance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// Implements LlmClient against the Anthropic Messages API.
//
// Phase 1 use case: optical gauge reading via VisionTelemetrySource. Each
// elicit() call sends one VideoFrame (as JPEG base64) plus the channel's
// prompt text and expects a numeric string back.
//
// API key management: inject via DI from BuildConfig or a local config file.
// NEVER commit an API key to version control. See :app module for the wiring.
//
// Billing: Anthropic API is a separate product from Claude Pro (claude.ai).
// Get a key at https://console.anthropic.com. At 5-second sample intervals
// with Haiku, gauge reading costs ≈ $0.01–0.02 per hour of operation.
class ClaudeVisionClient(
    private val apiKey: String,
    // Haiku is the recommended model for gauge reading: lowest latency and cost.
    // Switch to Sonnet if gauge legibility requires stronger vision capability.
    private val model: String = "claude-haiku-4-5-20251001",
    private val maxTokens: Int = 64,
    httpClient: OkHttpClient? = null,
) : LlmClient {

    override val providerId = "claude"

    private val http: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------------------
    // LlmClient implementation
    // ---------------------------------------------------------------------------

    override suspend fun elicit(
        prompt: ElicitationPrompt,
        context: SensoryContext,
    ): ElicitationResponse = withContext(Dispatchers.IO) {
        val body = buildRequestJson(prompt.text, context)
        val rawText = post(body)
        val parsed = parseGaugeValue(rawText)
        ElicitationResponse(
            rawText      = rawText,
            parsedValue  = parsed,
            confidence   = if (parsed != null) 0.9f else 0.0f,
        )
    }

    // Phase 3 — Twin advisory guidance is not yet implemented.
    override suspend fun generateGuidance(query: GuidanceQuery): TwinGuidance =
        TwinGuidance(advisedAction = "guidance not available in Phase 1", confidence = 0.0f)

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    internal fun buildRequestJson(promptText: String, context: SensoryContext): JsonObject {
        val content = buildJsonArray {
            context.frame?.let { frame ->
                add(buildJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", "image/jpeg")
                        put("data", VideoFrameEncoder.toJpegBase64(frame))
                    }
                })
            }
            add(buildJsonObject {
                put("type", "text")
                put("text", promptText)
            })
        }

        return buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", content)
                })
            })
        }
    }

    private fun post(body: JsonObject): String {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "(no body)"
            throw IOException("Anthropic API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body from Anthropic API")

        return extractTextContent(json.parseToJsonElement(responseBody).jsonObject)
    }

    private fun extractTextContent(responseJson: JsonObject): String {
        return responseJson["content"]
            ?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?: ""
    }

    // Extracts a Double from the model's text response. The prompts are written
    // to elicit bare numeric values ("Return only the numeric value, no units"),
    // but models sometimes include units or surrounding text. This handles:
    //   "750"          → 750.0
    //   "750 RPM"      → 750.0  (first token that parses as a number)
    //   "~750"         → 750.0  (strips leading non-digit except '-' and '.')
    //   "unreadable"   → null
    //   "N/A"          → null
    internal fun parseGaugeValue(rawText: String): Double? {
        if (rawText.isBlank()) return null

        // Fast path: whole response is a clean number
        rawText.toDoubleOrNull()?.let { return it }

        // Extract the first numeric token (integers and decimals, optional leading minus)
        val match = Regex("""-?\d+(?:\.\d+)?""").find(rawText)
        return match?.value?.toDoubleOrNull()
    }
}
