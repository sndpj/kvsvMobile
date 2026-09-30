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
    var headToHead by mutableStateOf<HeadToHeadResponse?>(null)
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

                // "Battle count" widget: defaults to the two teams from the most recent match,
                // since a generic dashboard has no other natural pair of teams to compare —
                // see README for this assumption.
                loadHeadToHeadForMostRecentMatch()
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

    private suspend fun loadHeadToHeadForMostRecentMatch() {
        // recent-matches doesn't carry team IDs (only names, for display) — fetch the match list
        // for team IDs isn't available here either without a tournament context, so instead we
        // resolve via the match detail endpoint using the first recent match's id if we have one.
        // Simpler and robust: dashboard/recent-matches already gives us enough for display; for
        // the head-to-head widget specifically we need IDs, so fetch the full match record once.
        val mostRecentSummary = recentMatches.firstOrNull() ?: return
        try {
            val matchRes = RetrofitClient.api.getMatch(mostRecentSummary.matchId)
            val m = matchRes.body() ?: return
            val h2hRes = RetrofitClient.api.getHeadToHead(m.teamAId, m.teamBId)
            if (h2hRes.isSuccessful) headToHead = h2hRes.body()
        } catch (_: Exception) {
            // Non-fatal — the rest of the dashboard still renders without this widget.
        }
    }
}
