package com.tekadi.kvvs.league.ui.match

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.*
import kotlinx.coroutines.launch

class MatchBrowserViewModel(app: Application) : AndroidViewModel(app) {

    var tournaments by mutableStateOf<List<TournamentResponse>>(emptyList())
    var selectedTournament by mutableStateOf<TournamentResponse?>(null)
    var matches by mutableStateOf<List<MatchResponse>>(emptyList())
    var teams by mutableStateOf<List<TeamResponse>>(emptyList())

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    // Create-match form state
    var showCreateForm by mutableStateOf(false)
    // Feature request: "show match date as current date by default" — was blank, forcing the
    // person to type a date before they could even see the picker's starting point.
    var newMatchDate by mutableStateOf(java.time.LocalDate.now().toString())   // "yyyy-MM-dd"
    var newMatchOvers by mutableStateOf("10")
    var newMatchTeamAId by mutableStateOf<Long?>(null)
    var newMatchTeamBId by mutableStateOf<Long?>(null)

    fun loadTournaments() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getTournaments()
                if (res.isSuccessful) tournaments = res.body().orEmpty()
                else error = "Couldn't load tournaments (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    // GAP FIX: this screen's ViewModel (and its `matches` list) survives navigating away to
    // score a match and back — Compose Navigation reuses the same back-stack entry rather than
    // recreating it, so without an explicit refresh, returning here after closing a match (or
    // just after a toss/innings start) kept showing whatever status the list was fetched with
    // *before* leaving, e.g. still "Innings Complete" after the match had actually been closed.
    // Call this from the screen's entry point every time it (re)appears.
    fun refreshIfTournamentSelected() {
        selectedTournament?.let { selectTournament(it) }
    }

    fun selectTournament(t: TournamentResponse) {
        selectedTournament = t
        loading = true; error = null
        viewModelScope.launch {
            try {
                val matchesRes = RetrofitClient.api.getMatches(t.id)
                val teamsRes = RetrofitClient.api.getTeams(t.id)
                if (matchesRes.isSuccessful) matches = matchesRes.body().orEmpty()
                if (teamsRes.isSuccessful) teams = teamsRes.body().orEmpty()
                if (!matchesRes.isSuccessful || !teamsRes.isSuccessful) {
                    error = "Couldn't load tournament details"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun createMatch(onCreated: (MatchResponse) -> Unit) {
        val tournament = selectedTournament ?: return
        val teamA = newMatchTeamAId; val teamB = newMatchTeamBId
        val overs = newMatchOvers.toIntOrNull()
        if (teamA == null || teamB == null) { error = "Select both teams"; return }
        if (teamA == teamB) { error = "Team A and Team B must be different"; return }
        if (overs == null || overs <= 0) { error = "Enter a valid overs count"; return }
        if (newMatchDate.isBlank()) { error = "Enter a match date (yyyy-MM-dd)"; return }

        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.createMatch(
                    CreateMatchRequest(
                        tournamentId = tournament.id, matchDate = newMatchDate,
                        oversLimit = overs, teamAId = teamA, teamBId = teamB,
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    showCreateForm = false
                    newMatchDate = java.time.LocalDate.now().toString()
                    info = "Match created"
                    onCreated(res.body()!!)
                } else {
                    error = when (res.code()) {
                        403 -> "You need Tournament Admin (or Super Admin) to create a match"
                        else -> "Couldn't create match (HTTP ${res.code()})"
                    }
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }
}
