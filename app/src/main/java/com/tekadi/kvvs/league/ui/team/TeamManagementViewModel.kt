package com.tekadi.kvvs.league.ui.team

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.*
import kotlinx.coroutines.launch

class TeamManagementViewModel(app: Application) : AndroidViewModel(app) {

    var tournaments by mutableStateOf<List<TournamentResponse>>(emptyList())
    var selectedTournament by mutableStateOf<TournamentResponse?>(null)
    var teams by mutableStateOf<List<TeamResponse>>(emptyList())

    var selectedTeam by mutableStateOf<TeamResponse?>(null)
    var players by mutableStateOf<List<PlayerResponse>>(emptyList())
    var latestInvite by mutableStateOf<InviteResponse?>(null)

    // Candidate captain/vice-captain pool for the "new team" form — team creation now requires
    // both (see CreateTeamRequest), and since a brand-new team has no roster yet, candidates are
    // drawn from the Master Player Pool (the app's "available, unassigned players" reservoir).
    // Picking someone here moves them onto the new team's roster — see TeamService.create.
    var poolPlayers by mutableStateOf<List<PoolPlayerResponse>>(emptyList())

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    // Feature request: "add logo instead of URL like add photo in feed while creating a team."
    var pickedLogoUri by mutableStateOf<android.net.Uri?>(null)
    var uploadedLogoUrl by mutableStateOf<String?>(null)
    var logoUploading by mutableStateOf(false)

    fun pickLogo(contentResolver: android.content.ContentResolver, uri: android.net.Uri) {
        pickedLogoUri = uri
        uploadedLogoUrl = null
        logoUploading = true
        viewModelScope.launch {
            com.tekadi.kvvs.league.util.ImageUpload.uploadUri(contentResolver, uri)
                .onSuccess { uploadedLogoUrl = it }
                .onFailure { error = "Couldn't upload the logo: ${it.message}" }
            logoUploading = false
        }
    }

    fun resetPickedLogo() { pickedLogoUri = null; uploadedLogoUrl = null }

    fun loadTournaments() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getTournaments()
                if (res.isSuccessful) tournaments = res.body().orEmpty()
                else error = "Couldn't load tournaments (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun loadPoolPlayers() {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getMasterPool()
                if (res.isSuccessful) poolPlayers = res.body()?.players.orEmpty()
            } catch (_: Exception) { /* the create-team form just shows an empty candidate list if this fails */ }
        }
    }

    fun selectTournament(t: TournamentResponse) {
        selectedTournament = t
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getTeams(t.id)
                if (res.isSuccessful) teams = res.body().orEmpty()
                else error = "Couldn't load teams (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun createTeam(
        name: String, logoUrl: String?, captainId: Long?, viceCaptainId: Long?,
        coachName: String?, contactNumber: String?, onDone: () -> Unit,
    ) {
        val tournament = selectedTournament
        // Mirrors the backend's @NotNull on tournamentId/captainId/viceCaptainId — caught here
        // too so the person gets an immediate inline message instead of a round-trip 400.
        if (name.isBlank()) { error = "Team name is required"; return }
        if (tournament == null) { error = "Select a tournament first"; return }
        if (captainId == null) { error = "Captain is required"; return }
        if (viceCaptainId == null) { error = "Vice-captain is required"; return }
        if (captainId == viceCaptainId) { error = "Captain and vice-captain must be different players"; return }
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.createTeam(
                    CreateTeamRequest(tournament.id, name.trim(), logoUrl?.ifBlank { null },
                        captainId, viceCaptainId, coachName?.ifBlank { null }, null, contactNumber?.ifBlank { null })
                )
                if (res.isSuccessful) {
                    selectTournament(tournament)
                    loadPoolPlayers() // the chosen captain/vice-captain just left the pool
                    info = "Team '$name' created"
                    onDone()
                } else error = when (res.code()) {
                    403 -> "You need Team Manager (or higher) to add a team"
                    else -> "Couldn't create team (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun deleteTeam(team: TeamResponse) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.deleteTeam(team.id)
                if (res.isSuccessful) {
                    selectedTournament?.let { selectTournament(it) }
                    info = "Team '${team.name}' removed"
                } else error = "Couldn't remove team (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun openTeam(team: TeamResponse) {
        selectedTeam = team
        latestInvite = null
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getPlayers(team.id)
                if (res.isSuccessful) players = res.body().orEmpty()
                else error = "Couldn't load players (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun addPlayer(name: String, jerseyNumber: Int?, age: Int?, role: String, battingStyle: String?, bowlingStyle: String?, onDone: () -> Unit) {
        val team = selectedTeam ?: return
        if (name.isBlank()) { error = "Player name is required"; return }
        duplicateRosterName(name, players.map { it.name })?.let { existing ->
            error = "'$existing' is already on ${team.name}"; return
        }
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.createPlayer(
                    CreatePlayerRequest(team.id, name.trim(), null, jerseyNumber, age, role, battingStyle, bowlingStyle)
                )
                if (res.isSuccessful) { openTeam(team); info = "'$name' added to the roster"; onDone() }
                // 409 = duplicate (name on this team, or a mobile/email already used) — say which.
                else error = res.friendlyErrorMessage("Couldn't add player (HTTP ${res.code()})")
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun removePlayer(player: PlayerResponse) {
        val team = selectedTeam ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.deletePlayer(player.id)
                if (res.isSuccessful) { openTeam(team); info = "'${player.name}' removed from the roster" }
                else error = "Couldn't remove player (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun generateInvite() {
        val team = selectedTeam ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.createInvite(team.id)
                if (res.isSuccessful) { latestInvite = res.body(); info = "Invite code generated" }
                else error = when (res.code()) {
                    403 -> "You need Team Manager (or higher) to invite players"
                    else -> "Couldn't generate an invite (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }
}
