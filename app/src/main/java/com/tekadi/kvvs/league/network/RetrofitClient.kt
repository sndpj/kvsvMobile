package com.tekadi.kvvs.league.network

import com.tekadi.kvvs.league.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Holds the current session token in memory. TokenStore (DataStore-backed)
 * is responsible for persisting it across app restarts.
 */
object SessionHolder {
    @Volatile var accessToken: String? = null
    // KvsvRequest1.2 #9 — kept in memory alongside the access token so TokenAuthenticator can
    // reach it synchronously from an OkHttp callback thread without a suspend DataStore read.
    @Volatile var refreshToken: String? = null

    // Feature: Super Admin role assign/revoke — "reset the token ... so he should be login
    // again with new role." TokenAuthenticator emits here (via tryEmit, from a background
    // OkHttp thread — Authenticator.authenticate is synchronous, not a suspend function — any
    // time a refresh attempt fails: a genuinely expired refresh token, or one an admin reset)
    // right after clearing the stored session, so the UI layer (AppNavHost, subscribed for the
    // whole app lifetime) can react by navigating back to the login screen immediately instead
    // of leaving the person stuck on a screen that will silently keep failing every request. No
    // replay (0, the default) is deliberate: this is a one-shot "go to login now" signal, not
    // state — replaying it would re-fire against a brand new subscriber after a fresh, valid
    // login, forcing them straight back out again for no reason.
    val sessionInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

object RetrofitClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            SessionHolder.accessToken?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        chain.proceed(request)
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        // GAP FIX: no timeouts were set at all, so every request ran on OkHttp's plain
        // defaults (10s connect/read/write, no overall call timeout). On a cellular
        // connection at a cricket ground, or against a backend that's cold-starting
        // (Cloud Run scale-to-zero can take well over 10s to boot a Spring Boot app),
        // 10s is exactly long enough to fail right as the request was about to succeed.
        // These are deliberately generous for a live-scoring app on mobile data, not a
        // typical snappy-API client.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        // Ceiling across the WHOLE call including every retry attempt in RetryInterceptor
        // below (worst case: 3 attempts x up to 15s connect + ~3.5s of backoff between them)
        // — generous enough that the retry policy actually gets to run, not so long that a
        // truly dead connection leaves the UI spinning forever.
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)
        .addInterceptor(RetryInterceptor())
        .addInterceptor(logging) // last, so it logs what actually went over the wire (post-retry)
        // KvsvRequest1.2 #9: on a 401, silently trade the stored refresh token for a new
        // access token and retry — this is what actually stops the app from asking the user
        // to log in again every time the 60-minute access token expires. Authenticators run
        // after the response is already a 401, separate from (and complementary to)
        // RetryInterceptor above, which only handles transient network failures.
        .authenticator(TokenAuthenticator())
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
