package com.tekadi.kvvs.league.network

import com.tekadi.kvvs.league.BuildConfig
import com.tekadi.kvvs.league.KvsvApplication
import com.tekadi.kvvs.league.data.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * KvsvRequest1.2 #9: "Everytime mobile application asking for username/password... suggest
 * best way." Root cause was that login already issued a refresh token, but nothing ever
 * redeemed it — every session silently died after the access token's 60-minute TTL, and the
 * only way back in was a full re-login. This is the fix: on a 401, trade the stored refresh
 * token for a new access token and transparently retry, so a session only actually ends when
 * the user logs out (or the refresh token itself — 14 days — finally expires too).
 *
 * A plain Interceptor can't do this correctly (it doesn't know a 401 means "expired", only that
 * *a* 401 happened); Authenticator is OkHttp's purpose-built hook for exactly this, and runs
 * synchronously on a background thread, which is fine here.
 */
class TokenAuthenticator : Authenticator {

    // A separate, bare client with no auth interceptor/authenticator of its own — calling
    // through the normal RetrofitClient.api here would recurse into this same authenticator.
    private val plainClient = OkHttpClient.Builder().build()
    private val json = Json { ignoreUnknownKeys = true }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never try to "fix" a 401 on the auth endpoints themselves (login/register/refresh) —
        // that 401 means genuinely bad credentials or a dead refresh token, not an expired one.
        val path = response.request.url.encodedPath
        if (path.contains("/api/auth/")) return null

        // Only ever retry once per request — a second 401 right after a successful-looking
        // refresh means something is genuinely wrong, not just "token expired".
        if (responseCount(response) >= 2) return null

        val failedAccessToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(this) {
            // Another thread's 401 may have already refreshed while this one was waiting on the
            // lock — if the session's access token has already moved on, just retry with it.
            val current = SessionHolder.accessToken
            if (current != null && current != failedAccessToken) {
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }

            val refreshToken = SessionHolder.refreshToken ?: return null // nothing to refresh with — give up, let the 401 surface
            val newTokens = performRefresh(refreshToken) ?: run {
                // The refresh token itself is dead — either past its 14-day TTL, or (Super Admin
                // role assign/revoke feature) an admin changed this user's role and reset their
                // token since it was issued, which AuthService.refresh rejects the exact same
                // way (403). Either case means the same thing to the client: clear the session
                // so the app naturally lands back on the login screen, rather than looping on
                // 401s forever. sessionInvalidated additionally lets AppNavHost jump there
                // immediately instead of waiting for the person to notice something's wrong.
                runBlocking { TokenStore(KvsvApplication.appContext).clear() }
                SessionHolder.accessToken = null
                SessionHolder.refreshToken = null
                SessionHolder.sessionInvalidated.tryEmit(Unit)
                return null
            }

            SessionHolder.accessToken = newTokens.accessToken
            SessionHolder.refreshToken = newTokens.refreshToken
            runBlocking { TokenStore(KvsvApplication.appContext).updateTokens(newTokens.accessToken, newTokens.refreshToken) }

            return response.request.newBuilder().header("Authorization", "Bearer ${newTokens.accessToken}").build()
        }
    }

    private fun performRefresh(refreshToken: String): RefreshResponse? {
        return try {
            val body = json.encodeToString(RefreshRequest(refreshToken)).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/auth/refresh")
                .post(body)
                .build()
            plainClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return null
                val text = res.body?.string() ?: return null
                json.decodeFromString(RefreshResponse.serializer(), text)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Walks response.priorResponse to count how many times we've already tried this chain. */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
