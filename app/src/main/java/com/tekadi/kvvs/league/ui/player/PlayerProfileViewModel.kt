package com.tekadi.kvvs.league.ui.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.PlayerProfileResponse
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.friendlyErrorMessage
import kotlinx.coroutines.launch

/** Player profile (KvsvRequest1.3), plus the Super Admin edit form (#4). */
class PlayerProfileViewModel(app: Application) : AndroidViewModel(app) {

    var profile by mutableStateOf<PlayerProfileResponse?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    var editForm by mutableStateOf<PlayerEditForm?>(null) // non-null while editing
    var saving by mutableStateOf(false)

    val canEdit: Boolean get() = CurrentUser.canEditPlayerDetails && profile != null

    fun load(playerId: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getPlayerProfile(playerId)
                if (res.isSuccessful && res.body() != null) profile = res.body()
                else error = if (res.code() == 404) "That player no longer exists" else "Couldn't load the profile (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun startEdit() { editForm = profile?.toEditForm() }
    fun cancelEdit() { editForm = null }

    fun saveEdit() {
        val form = editForm ?: return
        val p = profile ?: return
        editFormError(form)?.let { error = it; return }
        saving = true; error = null
        viewModelScope.launch {
            try {
                // Edits go to the canonical id (the pool player when there is one); the backend
                // copies the change onto every team-roster copy of them.
                val res = RetrofitClient.api.updatePlayer(p.playerId, form.toRequest())
                if (res.isSuccessful) {
                    editForm = null
                    info = "Player details saved"
                    load(p.playerId)
                } else error = when (res.code()) {
                    403 -> "Only a Super Admin can edit player details"
                    else -> res.friendlyErrorMessage("Couldn't save (HTTP ${res.code()})")
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                saving = false
            }
        }
    }
}
