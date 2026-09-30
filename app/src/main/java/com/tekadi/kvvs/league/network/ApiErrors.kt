package com.tekadi.kvvs.league.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/**
 * BUG FIX (review item #5 — "showing json response" instead of a user-facing message):
 * every backend error response is a JSON object shaped like
 * {"timestamp":"...","status":400,"message":"..."} (see GlobalExceptionHandler /
 * ErrorResponseWriter on the backend). Several screens were calling
 * `res.errorBody()?.string()` directly and putting that raw JSON string straight into the
 * on-screen error text — the user would literally see
 * `{"timestamp":"2026-08-31T...","status":400,"message":"Jersey number already taken"}`
 * instead of just "Jersey number already taken".
 *
 * This pulls the "message" field out of that JSON shape, falling back to the raw body (still
 * better than nothing) only if it isn't parseable JSON, and finally to a generic message.
 */
private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Response<*>.friendlyErrorMessage(fallback: String = "Something went wrong. Please try again."): String {
    val raw = try {
        errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (raw.isNullOrBlank()) return fallback
    return try {
        val obj = lenientJson.parseToJsonElement(raw).jsonObject
        obj["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: fallback
    } catch (_: Exception) {
        // Not the expected JSON error shape (e.g. an HTML error page from a proxy) — the raw
        // body is at least not worse than the fallback, but keep it short so it never reads
        // like a stack trace on screen.
        raw.take(200).takeIf { it.isNotBlank() } ?: fallback
    }
}
