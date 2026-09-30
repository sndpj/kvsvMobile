package com.tekadi.kvvs.league.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tekadi.kvvs.league.network.BallRequest
import com.tekadi.kvvs.league.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "offline_ball_queue")
private val QUEUE_KEY = stringPreferencesKey("pending_balls")

/**
 * Every scored ball is written here immediately (works fully offline at the
 * ground with no signal), then flushSync() pushes anything pending to the
 * backend once connectivity returns. Each entry carries a clientBallUuid so a
 * retried sync is idempotent server-side.
 */
class OfflineQueue(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    data class QueuedBall(val matchId: Long, val request: BallRequest)

    suspend fun enqueue(matchId: Long, request: BallRequest) {
        val entry = QueuedBall(matchId, request.copy(clientBallUuid = UUID.randomUUID().toString()))
        val current = readAll().toMutableList()
        current.add(entry)
        writeAll(current)
    }

    suspend fun pendingCount(): Int = readAll().size

    /** Attempts to push every queued ball to the server, in order. Stops at the first failure. */
    suspend fun flushSync(): Result<Int> {
        val pending = readAll().toMutableList()
        var synced = 0
        while (pending.isNotEmpty()) {
            val next = pending.first()
            val response = try {
                RetrofitClient.api.submitBall(next.matchId, next.request)
            } catch (e: Exception) {
                writeAll(pending) // persist whatever's left
                return Result.failure(e)
            }
            if (!response.isSuccessful) {
                writeAll(pending)
                return Result.failure(IllegalStateException("Sync failed: HTTP ${response.code()}"))
            }
            pending.removeAt(0)
            synced++
        }
        writeAll(pending)
        return Result.success(synced)
    }

    private suspend fun readAll(): List<QueuedBall> {
        val raw = context.dataStore.data.first()[QUEUE_KEY] ?: return emptyList()
        return runCatching { json.decodeFromString<List<QueuedBallSerializable>>(raw).map { it.toQueuedBall() } }
            .getOrDefault(emptyList())
    }

    private suspend fun writeAll(list: List<QueuedBall>) {
        context.dataStore.edit { prefs ->
            prefs[QUEUE_KEY] = json.encodeToString(list.map { QueuedBallSerializable.from(it) })
        }
    }

    // BallRequest itself is @Serializable; this wrapper just pairs it with the matchId for storage.
    @kotlinx.serialization.Serializable
    private data class QueuedBallSerializable(val matchId: Long, val request: BallRequest) {
        fun toQueuedBall() = QueuedBall(matchId, request)
        companion object {
            fun from(q: QueuedBall) = QueuedBallSerializable(q.matchId, q.request)
        }
    }
}
