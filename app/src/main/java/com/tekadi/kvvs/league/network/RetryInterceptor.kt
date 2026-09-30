package com.tekadi.kvvs.league.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * GAP FIX: there was no retry policy at all — a single dropped packet or a slow
 * cold-start on the backend surfaced straight to the user as "Couldn't reach the
 * server: timeout error", even though the very next attempt would usually succeed.
 *
 * Retries are ONLY safe to do automatically for requests that can't cause harm if
 * applied twice:
 *  - GET requests are always safe (they don't change anything).
 *  - POST .../ball is safe specifically because every ball carries a clientBallUuid
 *    that the backend already dedupes on (see ScoringService.applyBall) — a retried
 *    submit can't double-score a run even if the first attempt actually succeeded
 *    and only the response was lost.
 *  - PATCH .../admin/users/{id}/role (Super Admin role assign/revoke) is safe for the
 *    same reason: it sets the target user's role to a specific roleId, not a relative
 *    change, so applying it twice lands on the exact same role either way. The one side
 *    effect that isn't strictly idempotent — token_version incrementing again on a
 *    retry — is harmless: it just means that user's already-issued tokens are
 *    invalidated (same outcome the endpoint always produces), never an incorrect role.
 *
 * Every other mutating endpoint (toss, innings/start, bowler/batter selection, undo)
 * has no such idempotency key, so retrying it blind could double-apply a state
 * change — those are deliberately left to surface a normal error and let the
 * scorer retry manually, or (for /ball specifically) fall back to the offline
 * queue if every attempt here still fails.
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val baseBackoffMillis: Long = 500,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isRetryable(request.method, request.url.encodedPath)) {
            return chain.proceed(request)
        }

        var lastException: IOException? = null
        var lastResponse: Response? = null

        for (attempt in 1..maxAttempts) {
            lastResponse?.close()
            try {
                val response = chain.proceed(request)
                // Retry on the transient-failure status range; anything else (2xx, 4xx —
                // including a real validation error) is returned as-is immediately.
                if (response.code !in TRANSIENT_STATUS_CODES || attempt == maxAttempts) {
                    return response
                }
                lastResponse = response
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxAttempts) throw e
            }

            Thread.sleep(baseBackoffMillis * (1L shl (attempt - 1))) // 500ms, 1000ms, 2000ms...
        }

        // Unreachable in practice (the loop always returns or throws on the last attempt),
        // but keeps the compiler happy about a non-null return.
        return lastResponse ?: throw (lastException ?: IOException("Retry failed with no response"))
    }

    companion object {
        private val TRANSIENT_STATUS_CODES = setOf(502, 503, 504)

        // Pulled out as a plain, testable function (not inlined into intercept()) specifically
        // so the retryability rule itself can be unit-tested without mocking an OkHttp
        // Interceptor.Chain — see RetryInterceptorTest.
        internal fun isRetryable(method: String, encodedPath: String): Boolean =
            method == "GET" ||
                encodedPath.endsWith("/ball") ||
                (method == "PATCH" && encodedPath.endsWith("/role") && encodedPath.contains("/admin/users/"))
    }
}
