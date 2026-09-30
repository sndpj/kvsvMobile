package com.tekadi.kvvs.league.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tekadi.kvvs.league.network.SessionHolder
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "auth_session")

private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
private val KEY_ROLE = stringPreferencesKey("role")
private val KEY_FULL_NAME = stringPreferencesKey("full_name")
private val KEY_USER_ID = longPreferencesKey("user_id")

data class StoredSession(val accessToken: String, val refreshToken: String, val role: String, val fullName: String, val userId: Long)

class TokenStore(private val context: Context) {

    suspend fun save(session: StoredSession) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = session.accessToken
            prefs[KEY_REFRESH_TOKEN] = session.refreshToken
            prefs[KEY_ROLE] = session.role
            prefs[KEY_FULL_NAME] = session.fullName
            prefs[KEY_USER_ID] = session.userId
        }
        // Keep the in-memory holders (read by RetrofitClient's auth interceptor, and by any
        // screen wanting role-based UI) in sync.
        SessionHolder.accessToken = session.accessToken
        SessionHolder.refreshToken = session.refreshToken
        CurrentUser.role = session.role
        CurrentUser.fullName = session.fullName
        CurrentUser.userId = session.userId
    }

    /**
     * KvsvRequest1.2 #9 — called by TokenAuthenticator after a successful silent refresh.
     * Deliberately only touches the two token fields, not role/fullName/userId (unchanged by a
     * refresh, and re-writing them from a background OkHttp thread would be redundant work).
     */
    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
        SessionHolder.accessToken = accessToken
        SessionHolder.refreshToken = refreshToken
    }

    suspend fun load(): StoredSession? {
        val prefs = context.authDataStore.data.first()
        val token = prefs[KEY_ACCESS_TOKEN] ?: return null
        val refresh = prefs[KEY_REFRESH_TOKEN] ?: return null
        val role = prefs[KEY_ROLE] ?: return null
        val name = prefs[KEY_FULL_NAME] ?: return null
        // A session saved before this field existed won't have it — treat as "unknown", not a
        // reason to force a fresh login. isAssignedScorer() below just fails safe (treats the
        // user as a viewer) until they log in again and this gets populated for real.
        val userId = prefs[KEY_USER_ID] ?: -1L
        SessionHolder.accessToken = token
        SessionHolder.refreshToken = refresh
        CurrentUser.role = role
        CurrentUser.fullName = name
        CurrentUser.userId = userId
        return StoredSession(token, refresh, role, name, userId)
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
        SessionHolder.accessToken = null
        SessionHolder.refreshToken = null
        CurrentUser.clear()
    }
}
