package com.tekadi.kvvs.league.network

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * ACCEPTANCE-CRITERIA GAP FIX: the app had zero WebSocket/STOMP client anywhere (confirmed by
 * grep across the whole module before writing this) — every screen only ever saw an update to
 * the live scorecard as the *response of its own REST call*. That's correct for the person
 * actively tapping the scoring buttons, but means nobody else watching that match (any other
 * device, any Viewer/Umpire/second Scorer) ever saw a ball-by-ball update — the backend already
 * broadcasts one over STOMP after every ball (see WebSocketConfig.java /
 * LiveScoreBroadcaster.java on the backend), it just had no Android listener.
 *
 * This is a minimal STOMP-over-WebSocket client built directly on OkHttp's WebSocket API —
 * no new library dependency, since all this needs is "CONNECT / SUBSCRIBE / parse MESSAGE
 * bodies as JSON", not a general STOMP implementation.
 *
 * ASSUMPTION (flagged per project convention — confirm/correct if the backend's WebSocket setup
 * ever changes): WebSocketConfig registers the STOMP endpoint at "/ws" with `.withSockJS()`.
 * Spring's SockJS handler exposes a raw (non-SockJS-framed) WebSocket transport at
 * "{endpoint}/websocket" for exactly this purpose — native clients that don't need SockJS's
 * browser-compatibility fallbacks connect straight to it and speak plain STOMP frames, skipping
 * the SockJS envelope entirely. This client connects to ".../ws/websocket". If a future change
 * swaps in a bare WebSocketHandler (no SockJS), this path is still correct as-is.
 */
class LiveScoreSocket(baseHttpUrl: String) {

    private val wsUrl: String = baseHttpUrl
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .trimEnd('/') + "/ws/websocket"

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS) // WebSocket-level ping/pong keepalive, independent of STOMP heart-beats
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private var webSocket: WebSocket? = null
    private var stompConnected = false
    private var subscribedMatchId: Long? = null
    private var pendingSubscribeMatchId: Long? = null

    /** Invoked on the OkHttp callback thread — callers dispatch back to the main/coroutine thread themselves. */
    var onScorecard: ((MatchScorecardDto) -> Unit)? = null
    var onConnectionStateChanged: ((connected: Boolean) -> Unit)? = null

    /** Opens the socket if needed and (re)subscribes to the given match's live topic. */
    fun connectAndSubscribe(matchId: Long) {
        if (subscribedMatchId == matchId && (stompConnected || pendingSubscribeMatchId == matchId)) return
        pendingSubscribeMatchId = matchId

        if (webSocket != null) {
            if (stompConnected) subscribe(matchId)
            return // already opening/open — CONNECTED callback (or the check above) will subscribe
        }

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // heart-beat:0,0 — this backend's simple broker has no TaskScheduler configured
                // for STOMP heart-beats, so there's nothing to negotiate; disabling them avoids
                // this client ever disconnecting itself for "missing" a heartbeat the server was
                // never going to send.
                ws.send("CONNECT\naccept-version:1.1,1.2\nheart-beat:0,0\n\n\u0000")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                stompConnected = false
                webSocket = null
                onConnectionStateChanged?.invoke(false)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                stompConnected = false
                webSocket = null
                onConnectionStateChanged?.invoke(false)
            }
        })
    }

    private fun handleFrame(raw: String) {
        // STOMP frames are NULL-terminated; a heart-beat is a lone newline with no command.
        val frame = raw.trimEnd('\u0000', '\n')
        if (frame.isBlank()) return
        val command = frame.substringBefore('\n')
        when (command) {
            "CONNECTED" -> {
                stompConnected = true
                onConnectionStateChanged?.invoke(true)
                pendingSubscribeMatchId?.let { subscribe(it) }
            }
            "MESSAGE" -> {
                val body = frame.substringAfter("\n\n", "")
                if (body.isNotBlank()) {
                    runCatching { json.decodeFromString(MatchScorecardDto.serializer(), body) }
                        .onSuccess { onScorecard?.invoke(it) }
                    // A malformed/unexpected body is swallowed on purpose — the caller still has
                    // whatever it last got from its own REST call, and the next successful push
                    // (or the next manual refresh) will catch it up. Not worth surfacing as an
                    // error for what's ultimately a best-effort live-update channel.
                }
            }
            "ERROR" -> {
                stompConnected = false
                onConnectionStateChanged?.invoke(false)
            }
        }
    }

    private fun subscribe(matchId: Long) {
        if (subscribedMatchId != null && subscribedMatchId != matchId) unsubscribeCurrent()
        subscribedMatchId = matchId
        pendingSubscribeMatchId = null
        webSocket?.send("SUBSCRIBE\nid:sub-$matchId\ndestination:/topic/match/$matchId\n\n\u0000")
    }

    private fun unsubscribeCurrent() {
        val id = subscribedMatchId ?: return
        webSocket?.send("UNSUBSCRIBE\nid:sub-$id\n\n\u0000")
        subscribedMatchId = null
    }

    /** Call from the owning ViewModel's onCleared() — never leave a socket open past its screen. */
    fun disconnect() {
        unsubscribeCurrent()
        webSocket?.send("DISCONNECT\n\n\u0000")
        webSocket?.close(1000, "screen closed")
        webSocket = null
        stompConnected = false
        pendingSubscribeMatchId = null
    }
}
