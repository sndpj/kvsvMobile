package com.tekadi.kvvs.league.ui.dashboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.MatchResponse
import com.tekadi.kvvs.league.network.MatchScorecardDto
import com.tekadi.kvvs.league.network.PlayerResponse
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.SetPlayerOfMatchRequest
import com.tekadi.kvvs.league.network.friendlyErrorMessage
import com.tekadi.kvvs.league.ui.scoring.playerOfMatchCandidates
import com.tekadi.kvvs.league.ui.scoring.showPlayerOfMatchOverride
import kotlinx.coroutines.launch

class HistoryDetailViewModel(app: Application) : AndroidViewModel(app) {

    var match by mutableStateOf<MatchResponse?>(null)
    var scorecard by mutableStateOf<MatchScorecardDto?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    // KvsvRequest1.3 #7 — both squads, only loaded for someone who can override the award.
    var potmCandidates by mutableStateOf<List<PlayerResponse>>(emptyList())
    val canOverridePlayerOfMatch: Boolean get() = showPlayerOfMatchOverride(CurrentUser.canSetPlayerOfMatch, match?.status)

    fun load(matchId: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val matchRes = RetrofitClient.api.getMatch(matchId)
                val scorecardRes = RetrofitClient.api.getScorecard(matchId)
                if (matchRes.isSuccessful) match = matchRes.body()
                if (scorecardRes.isSuccessful) scorecard = scorecardRes.body()
                if (!matchRes.isSuccessful) error = "Couldn't load match (HTTP ${matchRes.code()})"
                val m = match
                if (m != null && canOverridePlayerOfMatch) {
                    val a = RetrofitClient.api.getPlayers(m.teamAId).body().orEmpty()
                    val b = RetrofitClient.api.getPlayers(m.teamBId).body().orEmpty()
                    potmCandidates = playerOfMatchCandidates(a, b)
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun setPlayerOfMatch(playerId: Long) {
        val m = match ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.setPlayerOfMatch(m.id, SetPlayerOfMatchRequest(playerId))
                if (res.isSuccessful && res.body() != null) {
                    match = res.body()
                    info = "Player of the Match updated"
                } else error = res.friendlyErrorMessage("Couldn't update Player of the Match (HTTP ${res.code()})")
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }
}
