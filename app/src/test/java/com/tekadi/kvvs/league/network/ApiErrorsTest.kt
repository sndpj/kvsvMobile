package com.tekadi.kvvs.league.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * Covers review item #5 ("toast messages showing json response instead of a user-facing
 * message"): GlobalExceptionHandler on the backend always returns a JSON object shaped like
 * timestamp/status/message for errors — this proves
 * Response.friendlyErrorMessage() extracts just the message instead of surfacing that raw JSON
 * blob to the user, and that it degrades sensibly when the body isn't that shape at all.
 */
class ApiErrorsTest {

    private fun <T> errorResponse(body: String, code: Int = 400): Response<T> {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return Response.error(code, responseBody)
    }

    // ---------------- positive ----------------

    @Test
    fun extractsTheMessageFieldFromABackendErrorBody() {
        val res = errorResponse<Unit>("""{"timestamp":"2026-08-31T10:00:00Z","status":400,"message":"Jersey number already taken"}""")
        assertEquals("Jersey number already taken", res.friendlyErrorMessage("fallback"))
    }

    @Test
    fun ignoresExtraFieldsAroundMessage() {
        val res = errorResponse<Unit>("""{"status":409,"message":"Pool is full","extra":{"nested":true}}""")
        assertEquals("Pool is full", res.friendlyErrorMessage("fallback"))
    }

    // ---------------- negative: never leak raw JSON or crash ----------------

    @Test
    fun fallsBackWhenBodyIsBlank() {
        val res = errorResponse<Unit>("")
        assertEquals("fallback", res.friendlyErrorMessage("fallback"))
    }

    @Test
    fun fallsBackToTruncatedRawBodyWhenNotTheExpectedJsonShape() {
        // e.g. an HTML error page from a proxy/load balancer, not the backend's own JSON body.
        val res = errorResponse<Unit>("<html><body>502 Bad Gateway</body></html>")
        val message = res.friendlyErrorMessage("fallback")
        assertEquals("<html><body>502 Bad Gateway</body></html>", message)
    }

    @Test
    fun fallsBackWhenJsonHasNoMessageField() {
        val res = errorResponse<Unit>("""{"timestamp":"2026-08-31T10:00:00Z","status":500}""")
        assertEquals("fallback", res.friendlyErrorMessage("fallback"))
    }

    @Test
    fun theRawJsonBlobItselfIsNeverReturnedVerbatimWhenAMessageFieldExists() {
        // GAP CHECK: guards specifically against the original bug — showing the whole JSON
        // object (including the timestamp/status noise) instead of just its message.
        val res = errorResponse<Unit>("""{"timestamp":"2026-08-31T10:00:00Z","status":400,"message":"Check your details and try again"}""")
        val result = res.friendlyErrorMessage("fallback")
        assertEquals("Check your details and try again", result)
        assertEquals(false, result.contains("timestamp"))
        assertEquals(false, result.startsWith("{"))
    }
}
