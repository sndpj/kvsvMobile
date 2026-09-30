package com.tekadi.kvvs.league.ui.dashboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.MatchResponse
import com.tekadi.kvvs.league.network.MatchScorecardDto
import com.tekadi.kvvs.league.network.RetrofitClient
import kotlinx.coroutines.launch

class HistoryDetailViewModel(app: Application) : AndroidViewModel(app) {

    var match by mutableStateOf<MatchResponse?>(null)
    var scorecard by mutableStateOf<MatchScorecardDto?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun load(matchId: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val matchRes = RetrofitClient.api.getMatch(matchId)
                val scorecardRes = RetrofitClient.api.getScorecard(matchId)
                if (matchRes.isSuccessful) match = matchRes.body()
                if (scorecardRes.isSuccessful) scorecard = scorecardRes.body()
                if (!matchRes.isSuccessful) error = "Couldn't load match (HTTP ${matchRes.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }
}
