package com.tekadi.kvvs.league.ui.standings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.TeamStandingRow
import com.tekadi.kvvs.league.network.TournamentResponse
import kotlinx.coroutines.launch

class StandingsViewModel(app: Application) : AndroidViewModel(app) {

    var tournaments by mutableStateOf<List<TournamentResponse>>(emptyList())
    var selectedTournament by mutableStateOf<TournamentResponse?>(null)
    var rows by mutableStateOf<List<TeamStandingRow>>(emptyList())

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun loadTournaments() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getTournaments()
                if (res.isSuccessful) {
                    tournaments = res.body().orEmpty()
                    // A single-tournament club (the common case) shouldn't need an extra tap —
                    // auto-select if there's exactly one.
                    if (tournaments.size == 1) selectTournament(tournaments.first())
                } else error = "Couldn't load tournaments (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun selectTournament(t: TournamentResponse) {
        selectedTournament = t
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getStandings(t.id)
                if (res.isSuccessful) rows = res.body().orEmpty()
                else error = "Couldn't load standings (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }
}
