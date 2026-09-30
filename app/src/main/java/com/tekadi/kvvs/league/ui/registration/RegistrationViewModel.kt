package com.tekadi.kvvs.league.ui.registration

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.SubmitRegistrationRequest
import kotlinx.coroutines.launch

/**
 * Public player self-registration (Name, Jersey Number, Age, Role, Mobile Number, Email) — no
 * login required to submit. A Super Admin reviews the submission and either approves it
 * (creating a real Master Player Pool entry) or rejects it — see MasterPoolScreen's "Pending
 * Registrations" panel.
 */
class RegistrationViewModel(app: Application) : AndroidViewModel(app) {

    var name by mutableStateOf("")
    var jersey by mutableStateOf("")
    var age by mutableStateOf("")
    var role by mutableStateOf("BATTER")
    var mobileNumber by mutableStateOf("")
    var email by mutableStateOf("")

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var submitted by mutableStateOf(false)

    fun submit() {
        if (name.isBlank()) { error = "Name is required"; return }
        if (mobileNumber.isBlank()) { error = "Mobile number is required"; return }
        if (email.isBlank()) { error = "Email is required"; return }
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.submitRegistration(
                    SubmitRegistrationRequest(name.trim(), jersey.toIntOrNull(), age.toIntOrNull(), role, mobileNumber.trim(), email.trim())
                )
                if (res.isSuccessful) submitted = true
                else error = when (res.code()) {
                    400 -> res.errorBody()?.string() ?: "Check your details and try again"
                    else -> "Couldn't submit your registration (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun resetForAnother() {
        name = ""; jersey = ""; age = ""; role = "BATTER"; mobileNumber = ""; email = ""
        submitted = false; error = null
    }
}
