package com.tekadi.kvvs.league.ui.pool

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.*
import kotlinx.coroutines.launch

/** Which of the two new teams a pool player is currently earmarked for, if any. */
enum class PoolAssignment { TEAM_A, TEAM_B }

class MasterPoolViewModel(app: Application) : AndroidViewModel(app) {

    var status by mutableStateOf<PoolStatusResponse?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    var lastSplitResult by mutableStateOf<SplitIntoTeamsResponse?>(null)

    // "Create 2 teams" working state — kept here (not local Compose state) so it survives
    // rotation and so addPlayer/removePlayer below can safely drop stale assignments.
    var teamAName by mutableStateOf("")
    var teamBName by mutableStateOf("")
    var assignments by mutableStateOf<Map<Long, PoolAssignment>>(emptyMap())
        private set
    // Required, not optional — "make tournament_id, captain, vice-captain compulsory" applies
    // here too (see SplitIntoTeamsRequest).
    var selectedTournamentId by mutableStateOf<Long?>(null)
    var teamACaptainId by mutableStateOf<Long?>(null)
    var teamAViceCaptainId by mutableStateOf<Long?>(null)
    var teamBCaptainId by mutableStateOf<Long?>(null)
    var teamBViceCaptainId by mutableStateOf<Long?>(null)

    var tournaments by mutableStateOf<List<TournamentResponse>>(emptyList())
    var pendingRegistrations by mutableStateOf<List<RegistrationResponse>>(emptyList())

    fun loadTournaments() {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getTournaments()
                if (res.isSuccessful) tournaments = res.body().orEmpty()
            } catch (_: Exception) { /* the split-into-teams form just shows an empty picker if this fails */ }
        }
    }

    /** SUPER_ADMIN only, per SecurityConfig — this list only renders for that role anyway. */
    fun loadPendingRegistrations() {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getRegistrations("PENDING")
                if (res.isSuccessful) pendingRegistrations = res.body().orEmpty()
            } catch (_: Exception) { /* shown alongside the pool status error if it also fails */ }
        }
    }

    fun approveRegistration(id: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.approveRegistration(id)
                if (res.isSuccessful) { loadPendingRegistrations(); load(); info = "Registration approved" }
                else error = "Couldn't approve registration (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun rejectRegistration(id: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.rejectRegistration(id, RejectRegistrationRequest())
                if (res.isSuccessful) { loadPendingRegistrations(); info = "Registration rejected" }
                else error = "Couldn't reject registration (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getMasterPool()
                if (res.isSuccessful && res.body() != null) {
                    status = res.body()
                    // Drop any assignment for a player who's no longer in the pool
                    // (e.g. another admin just removed or split them out).
                    val stillInPool = status!!.players.map { it.id }.toSet()
                    assignments = assignments.filterKeys { it in stillInPool }
                } else error = "Couldn't load the master pool (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    // Feature request 3a: "profile pick single image selection" — the pool-add form's photo field.
    var pickedPhotoUri by mutableStateOf<android.net.Uri?>(null)
    var uploadedPhotoUrl by mutableStateOf<String?>(null)
    var photoUploading by mutableStateOf(false)

    fun pickPhoto(contentResolver: android.content.ContentResolver, uri: android.net.Uri) {
        pickedPhotoUri = uri
        uploadedPhotoUrl = null
        photoUploading = true
        viewModelScope.launch {
            com.tekadi.kvvs.league.util.ImageUpload.uploadUri(contentResolver, uri)
                .onSuccess { uploadedPhotoUrl = it }
                .onFailure { error = "Couldn't upload the photo: ${it.message}" }
            photoUploading = false
        }
    }

    fun resetPickedPhoto() { pickedPhotoUri = null; uploadedPhotoUrl = null }

    fun addPlayer(
        name: String, jerseyNumber: Int?, age: Int?, role: String, battingStyle: String?, bowlingStyle: String?,
        mobileNumber: String, email: String, photoUrl: String?, onDone: () -> Unit,
    ) {
        if (name.isBlank()) { error = "Player name is required"; return }
        if (mobileNumber.isBlank()) { error = "Mobile number is required"; return }
        if (email.isBlank()) { error = "Email is required"; return }
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.addPoolPlayer(
                    AddPoolPlayerRequest(name.trim(), photoUrl, jerseyNumber, age, role, battingStyle, bowlingStyle, mobileNumber.trim(), email.trim())
                )
                if (res.isSuccessful) { load(); info = "'$name' added to the pool"; onDone() }
                else error = when (res.code()) {
                    // KvsvRequest1.3 #2: 409 is not only "pool is full" — a duplicate mobile
                    // number, email or name is a 409 too, so show the server's actual reason.
                    409 -> res.friendlyErrorMessage("Pool is full — remove a player before adding another")
                    403 -> "You need Super Admin to add to the master pool"
                    400 -> res.friendlyErrorMessage("Check the player's details and try again")
                    else -> "Couldn't add player (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun removePlayer(playerId: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.removePoolPlayer(playerId)
                if (res.isSuccessful) { load(); info = "Player removed from the pool" }
                else error = when (res.code()) {
                    403 -> "You need Super Admin to remove from the master pool"
                    else -> "Couldn't remove player (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    /** Tapping the same assignment again clears it; tapping the other side re-assigns. */
    fun toggleAssignment(playerId: Long, side: PoolAssignment) {
        assignments = assignments.toMutableMap().apply {
            if (this[playerId] == side) remove(playerId) else this[playerId] = side
        }
        // A captain/vice-captain pick only makes sense while that player is still assigned to
        // that team — if they were unassigned or moved to the other side, drop the stale pick
        // rather than silently submitting a captainId that's no longer valid for that team.
        val aIds = assignments.filterValues { it == PoolAssignment.TEAM_A }.keys
        val bIds = assignments.filterValues { it == PoolAssignment.TEAM_B }.keys
        if (teamACaptainId != null && teamACaptainId !in aIds) teamACaptainId = null
        if (teamAViceCaptainId != null && teamAViceCaptainId !in aIds) teamAViceCaptainId = null
        if (teamBCaptainId != null && teamBCaptainId !in bIds) teamBCaptainId = null
        if (teamBViceCaptainId != null && teamBViceCaptainId !in bIds) teamBViceCaptainId = null
    }

    fun splitIntoTeams() {
        val aIds = assignments.filterValues { it == PoolAssignment.TEAM_A }.keys.toList()
        val bIds = assignments.filterValues { it == PoolAssignment.TEAM_B }.keys.toList()
        if (teamAName.isBlank() || teamBName.isBlank()) { error = "Both team names are required"; return }
        if (aIds.isEmpty() || bIds.isEmpty()) { error = "Pick at least one player for each team"; return }
        val tournamentId = selectedTournamentId
        if (tournamentId == null) { error = "Select a tournament first"; return }
        val aCaptain = teamACaptainId; val aVice = teamAViceCaptainId
        val bCaptain = teamBCaptainId; val bVice = teamBViceCaptainId
        if (aCaptain == null || aVice == null) { error = "Team A needs both a captain and a vice-captain"; return }
        if (bCaptain == null || bVice == null) { error = "Team B needs both a captain and a vice-captain"; return }
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.splitPoolIntoTeams(
                    SplitIntoTeamsRequest(
                        tournamentId,
                        teamAName.trim(), aIds, null, aCaptain, aVice,
                        teamBName.trim(), bIds, null, bCaptain, bVice,
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    lastSplitResult = res.body()
                    teamAName = ""; teamBName = ""; assignments = emptyMap()
                    teamACaptainId = null; teamAViceCaptainId = null; teamBCaptainId = null; teamBViceCaptainId = null
                    info = "Teams created from the pool"
                    load()
                } else error = when (res.code()) {
                    403 -> "You need Team Manager (or Super Admin) to create teams from the pool"
                    400 -> res.errorBody()?.string() ?: "Couldn't create the teams — check for players picked on both sides"
                    else -> "Couldn't create the teams (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }
}
