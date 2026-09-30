package com.tekadi.kvvs.league.ui.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.StoredSession
import com.tekadi.kvvs.league.data.TokenStore
import com.tekadi.kvvs.league.network.LoginRequest
import com.tekadi.kvvs.league.network.RetrofitClient
import kotlinx.coroutines.launch

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = TokenStore(app)

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var session by mutableStateOf<StoredSession?>(null)
    var checkedExistingSession by mutableStateOf(false)

    init {
        // If a previous login is still stored, skip straight past the login screen.
        viewModelScope.launch {
            session = tokenStore.load()
            checkedExistingSession = true
        }
    }

    fun login(onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            error = "Enter both email and password"
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.login(LoginRequest(email.trim(), password))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val stored = StoredSession(body.accessToken, body.refreshToken, body.role, body.fullName, body.userId)
                    tokenStore.save(stored)
                    session = stored
                    onSuccess()
                } else {
                    error = when (response.code()) {
                        400 -> "Invalid email or password"
                        403 -> "This account has been deactivated"
                        else -> "Login failed (HTTP ${response.code()})"
                    }
                }
            } catch (e: Exception) {
                // Network failure, wrong base URL, backend not running, etc.
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            session = null
        }
    }
}
