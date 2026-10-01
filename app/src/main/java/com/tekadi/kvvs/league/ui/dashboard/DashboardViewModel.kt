package com.tekadi.kvvs.league.ui.dashboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.*
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    var feeds by mutableStateOf<List<FeedResponse>>(emptyList())
    var recentMatches by mutableStateOf<List<RecentMatchSummary>>(emptyList())
    // KvsvRequest1.3 #3: the Head-to-Head card (latest match's two teams) was removed from the
    // dashboard — and with it the extra getMatch + head-to-head calls it made on every load.
    // The backend endpoint (GET /api/dashboard/head-to-head) is untouched.
    var topBatsmen by mutableStateOf<List<LeaderboardEntry>>(emptyList())
    var topBowlers by mutableStateOf<List<LeaderboardEntry>>(emptyList())
    // Feature request: "Live matches should be display in slider in dashboard screen right now
    // showing only one." Was a single nullable pointer; now a list, one scorecard fetched per
    // match (keyed by matchId) so the slider can show full detail on every page, not just
    // whichever one loaded first.
    var liveMatches by mutableStateOf<List<LiveMatchPointer>>(emptyList())
    var liveScorecards by mutableStateOf<Map<Long, MatchScorecardDto>>(emptyMap())

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val feedsRes = RetrofitClient.api.getFeeds(5)
                val recentRes = RetrofitClient.api.getRecentMatches(5)
                val batsmenRes = RetrofitClient.api.getTopBatsmen(5)
                val bowlersRes = RetrofitClient.api.getTopBowlers(5)

                if (feedsRes.isSuccessful) feeds = feedsRes.body().orEmpty()
                if (recentRes.isSuccessful) recentMatches = recentRes.body().orEmpty()
                if (batsmenRes.isSuccessful) topBatsmen = batsmenRes.body().orEmpty()
                if (bowlersRes.isSuccessful) topBowlers = bowlersRes.body().orEmpty()

                loadLiveMatches()
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    private suspend fun loadLiveMatches() {
        try {
            val res = RetrofitClient.api.getLiveMatches()
            if (!res.isSuccessful) return
            val pointers = res.body().orEmpty()
            liveMatches = pointers
            // Reuses the same scorecard endpoint the live-scoring screen itself uses — no new
            // live-score logic duplicated here, just fetched once per match for the slider.
            val scorecards = mutableMapOf<Long, MatchScorecardDto>()
            for (pointer in pointers) {
                val scoreRes = RetrofitClient.api.getScorecard(pointer.matchId)
                if (scoreRes.isSuccessful) scoreRes.body()?.let { scorecards[pointer.matchId] = it }
            }
            liveScorecards = scorecards
        } catch (_: Exception) {
            // Non-fatal — the rest of the dashboard still renders without the live band.
        }
    }
}
