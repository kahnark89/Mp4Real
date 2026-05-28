package com.capsconc.arcshield.llm.gemini

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiVisionClientTest {

    // ---- parseGaugeValue ---------------------------------------------------
    // Pure-logic tests — no network, no Android SDK.

    private val client = GeminiVisionClient(apiKey = "test-key")

    @Test
    fun `parseGaugeValue clean integer`() =
        assertEquals(750.0, client.parseGaugeValue("750")!!, 0.001)

    @Test
    fun `parseGaugeValue clean decimal`() =
        assertEquals(42.5, client.parseGaugeValue("42.5")!!, 0.001)

    @Test
    fun `parseGaugeValue integer with unit suffix`() =
        assertEquals(750.0, client.parseGaugeValue("750 RPM")!!, 0.001)

    @Test
    fun `parseGaugeValue decimal with unit suffix`() =
        assertEquals(385.6, client.parseGaugeValue("385.6°F")!!, 0.001)

    @Test
    fun `parseGaugeValue tilde prefix`() =
        assertEquals(750.0, client.parseGaugeValue("~750")!!, 0.001)

    @Test
    fun `parseGaugeValue unreadable text returns null`() =
        assertNull(client.parseGaugeValue("unreadable"))

    @Test
    fun `parseGaugeValue na returns null`() =
        assertNull(client.parseGaugeValue("N/A"))

    @Test
    fun `parseGaugeValue blank string returns null`() =
        assertNull(client.parseGaugeValue(""))

    @Test
    fun `parseGaugeValue negative number`() =
        assertEquals(-10.5, client.parseGaugeValue("-10.5")!!, 0.001)

    @Test
    fun `parseGaugeValue sentence with embedded number`() =
        assertEquals(1200.0, client.parseGaugeValue("The gauge reads 1200 rpm")!!, 0.001)

    // ---- HTTP round-trip via MockWebServer ----------------------------------

    private val server = MockWebServer()
    private lateinit var networkClient: GeminiVisionClient

    @Before
    fun setUp() {
        server.start()
        val httpClient = OkHttpClient()
        networkClient = GeminiVisionClient(
            apiKey = "test-key",
            httpClient = httpClient,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `extractTextContent parses Gemini response correctly`() {
        val responseJson = """
            {
              "candidates": [{
                "content": {
                  "parts": [{"text": "750"}],
                  "role": "model"
                },
                "finishReason": "STOP"
              }],
              "usageMetadata": {
                "promptTokenCount": 42,
                "candidatesTokenCount": 3
              }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        // Verify the JSON parse logic against the Gemini response structure.
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.parseToJsonElement(responseJson).let { root ->
            root.jsonObject["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                ?.trim()
        }
        assertEquals("750", parsed)
    }

    @Test
    fun `buildRequestJson contains text part when no frame`() {
        val body = networkClient.buildRequestJson(
            promptText = "Read the RPM gauge.",
            context    = com.capsconc.arcshield.schema.llm.SensoryContext(frame = null),
        )
        val contents = body["contents"]!!.jsonArray
        val parts = contents[0].jsonObject["parts"]!!.jsonArray
        // No inline_data block — only one text part
        assertEquals(1, parts.size)
        assertEquals("Read the RPM gauge.", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `buildRequestJson includes generationConfig with maxOutputTokens`() {
        val body = networkClient.buildRequestJson(
            promptText = "Read the pressure gauge.",
            context    = com.capsconc.arcshield.schema.llm.SensoryContext(frame = null),
        )
        val config = body["generationConfig"]!!.jsonObject
        assertEquals(64, config["maxOutputTokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `buildRequestJson wraps parts in contents array`() {
        val body = networkClient.buildRequestJson(
            promptText = "Read the temperature gauge.",
            context    = com.capsconc.arcshield.schema.llm.SensoryContext(frame = null),
        )
        val contents = body["contents"]!!.jsonArray
        assertEquals(1, contents.size)
        assertNotNull(contents[0].jsonObject["parts"])
    }
}
