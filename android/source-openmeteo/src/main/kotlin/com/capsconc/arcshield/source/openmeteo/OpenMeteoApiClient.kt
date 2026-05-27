package com.capsconc.arcshield.source.openmeteo

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches current ambient temperature from the Open-Meteo free weather API.
 * No API key required. Response is cached for [cacheWindowMs] to avoid hammering
 * the public endpoint.
 *
 * Open-Meteo endpoint:
 *   GET https://api.open-meteo.com/v1/forecast
 *       ?latitude={lat}&longitude={lon}
 *       &current_weather=true&temperature_unit=fahrenheit
 */
internal class OpenMeteoApiClient(
    private val latitudeDeg: Double,
    private val longitudeDeg: Double,
    private val cacheWindowMs: Long = 5 * 60 * 1_000L,  // 5 minutes
) {
    data class WeatherResult(val tempF: Float, val fetchedAtNanos: Long)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var cached: WeatherResult? = null

    suspend fun currentTempF(): Float = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtimeNanos()
        val c = cached
        if (c != null && (now - c.fetchedAtNanos) < cacheWindowMs * 1_000_000L) {
            return@withContext c.tempF
        }
        val tempF = fetchFromApi()
        cached = WeatherResult(tempF, now)
        tempF
    }

    private fun fetchFromApi(): Float {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(latitudeDeg)
            append("&longitude=").append(longitudeDeg)
            append("&current_weather=true&temperature_unit=fahrenheit")
        }
        val request = Request.Builder().url(url).build()
        val responseBody = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open-Meteo HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response from Open-Meteo")
        }
        return JSONObject(responseBody)
            .getJSONObject("current_weather")
            .getDouble("temperature")
            .toFloat()
    }
}
