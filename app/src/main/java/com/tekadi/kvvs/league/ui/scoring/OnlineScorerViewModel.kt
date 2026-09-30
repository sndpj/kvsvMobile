package com.tekadi.kvvs.league.ui.scoring

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.BuildConfig
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.*
import com.tekadi.kvvs.league.util.MatchEvent
import com.tekadi.kvvs.league.util.MatchEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

enum class OnlineScreen { TOSS, OPENERS, LIVE, AWAITING_CLOSE, RESULT }

class OnlineScorerViewModel(app: Application) : AndroidViewModel(app) {

    var match by mutableStateOf<MatchResponse?>(null)
    var teamAPlayers by mutableStateOf<List<PlayerResponse>>(emptyList())
    var teamBPlayers by mutableStateOf<List<PlayerResponse>>(emptyList())
    var scorecard by mutableStateOf<MatchScorecardDto?>(null)
    var screen by mutableStateOf(OnlineScreen.TOSS)

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    // Big-moment callouts (wicket/four/six) for the LIVE screen — see LiveEventToast in
    // Feedback.kt. Detected from the innings' own commentary feed (newest line first, built
    // server-side by ScoringEngine/ScoringService — see their "WICKET!"/"FOUR!"/"SIX!" literals)
    // rather than from which button was tapped, so it fires identically for the scorer's own
    // action and for a ball pushed in live over the socket to a pure viewer.
    var liveEvent by mutableStateOf<MatchEvent?>(null)
    private var lastSeenCommentaryLine: String? = null

    // GAP FIX (feature requests #1/#4/#5 — "only one scorer", "scorer can resume after any
    // interruption", "viewers only ever see what the assigned scorer did"): CurrentUser.canScore
    // used to be the ONLY gate on the scoring UI — true for anyone holding the SCORER role,
    // regardless of which match they opened. A second SCORER-role user (or the same one on a
    // different match) would see the full scoring panel and only discover they couldn't actually
    // submit anything after tapping and getting a 403. This checks the match's *own* assigned
    // scorer (now returned by the backend — see MatchResponse.scorerUserId) against the logged-in
    // user's own id (now returned at login — see CurrentUser.userId), matching the same
    // authorization the server actually enforces. Admins still see the full panel regardless of
    // assignment, mirroring MatchService.requireCanScoreMatch server-side.
    val isAssignedScorer: Boolean
        get() {
            val userId = CurrentUser.userId ?: return false
            return match?.scorerUserId == userId
        }
    val canScoreThisMatch: Boolean
        get() = CurrentUser.isSuperAdmin || CurrentUser.role == "TOURNAMENT_ADMIN" || isAssignedScorer

    /** Feature request #1 (updated): Super Admin or either team's manager can assign a scorer. */
    val canAssignScorerDirectly: Boolean
        get() = CurrentUser.isSuperAdmin || (CurrentUser.userId != null &&
                (CurrentUser.userId == match?.teamAManagerId || CurrentUser.userId == match?.teamBManagerId))

    // Live ball-by-ball push (see LiveScoreSocket) — this is what makes every ball a scorer
    // submits show up on every other device watching the same match, not just the scorer's own.
    var liveConnected by mutableStateOf(false)
        private set
    // GAP FIX (validating "the viewer screen also [has a] fixed live or offline score update
    // mechanism" — a bare WebSocket client with no fallback meant a dropped connection left the
    // viewer's screen frozen on stale data indefinitely, with no way to notice or recover short
    // of leaving and reopening the match). `polling` mirrors whether that fallback is currently
    // active, so the UI can show a distinct "offline — polling" state rather than just "live" vs
    // a silently-stale "connecting…".
    var polling by mutableStateOf(false)
        private set
    private var pollingJob: kotlinx.coroutines.Job? = null
    private val liveSocket = LiveScoreSocket(BuildConfig.API_BASE_URL)
    private var subscribedMatchId: Long? = null

    init {
        liveSocket.onScorecard = { pushedScorecard ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                applyScorecard(pushedScorecard, fireEvent = true)
                deriveScreen()
            }
        }
        liveSocket.onConnectionStateChanged = { connected ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                liveConnected = connected
                if (connected) stopPolling() else startPollingIfNeeded()
            }
        }
    }

    override fun onCleared() {
        liveSocket.disconnect()
        stopPolling()
        super.onCleared()
    }

    /**
     * Offline fallback for the LIVE screen: as long as the WebSocket is down AND we're actually
     * on the live scoring/viewing screen (no point polling during toss/openers/result), refresh
     * the scorecard over plain REST every few seconds instead of leaving it frozen. Self-checks
     * both conditions on every tick and exits the moment either stops holding — reconnecting the
     * socket or navigating off LIVE both naturally stop it without any extra wiring.
     */
    private fun startPollingIfNeeded() {
        if (liveConnected || screen != OnlineScreen.LIVE) return
        if (pollingJob?.isActive == true) return
        polling = true
        pollingJob = viewModelScope.launch {
            while (!liveConnected && screen == OnlineScreen.LIVE) {
                kotlinx.coroutines.delay(4000)
                if (!liveConnected && screen == OnlineScreen.LIVE) refreshScorecard()
            }
            polling = false
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        polling = false
    }

    /**
     * BUG FIX (KvsvRequest1.1 #6 — "if network is slow and clicking on view option sometime it
     * is showing toss screen after 5-10 second moving to summary's screen"): the old version
     * assigned `match = m` as soon as the match fetch returned, which immediately satisfied the
     * screen's `vm.loading && vm.match == null` loader gate (OnlineLiveScorerScreen.kt) and let
     * Compose render `when (vm.screen)` at `screen`'s *stale default* (TOSS, set once at
     * ViewModel construction) — the real screen only got assigned once the separate,
     * still-in-flight scorecard fetch inside refreshScorecard() finally completed and called
     * deriveScreen(). On a fast connection that gap is imperceptible; on a slow one it's exactly
     * the multi-second "toss screen, then jumps to summary" flash reported here.
     *
     * Fix: don't publish anything to Compose state until every network call this function needs
     * has actually returned, then assign match/players/scorecard and derive the screen together
     * in one synchronous block (no suspension points in between, so Compose batches it into a
     * single recomposition). The loader now correctly stays up for the *entire* wait, and the
     * first screen the user ever sees is the right one.
     */
    fun loadMatch(matchId: Long) {
        if (subscribedMatchId != matchId) {
            subscribedMatchId = matchId
            liveSocket.connectAndSubscribe(matchId)
        }
        loading = true; error = null
        viewModelScope.launch {
            try {
                val matchRes = RetrofitClient.api.getMatch(matchId)
                if (!matchRes.isSuccessful || matchRes.body() == null) {
                    error = "Couldn't load match (HTTP ${matchRes.code()})"
                    return@launch
                }
                val m = matchRes.body()!!

                val aRes = RetrofitClient.api.getPlayers(m.teamAId)
                val bRes = RetrofitClient.api.getPlayers(m.teamBId)
                val aPlayers = aRes.body().orEmpty()
                val bPlayers = bRes.body().orEmpty()

                var sc: MatchScorecardDto? = null
                if (m.tossDecision != null) {
                    val scRes = RetrofitClient.api.getScorecard(m.id)
                    if (scRes.isSuccessful) {
                        sc = scRes.body()
                    } else {
                        error = "Couldn't load the scorecard (HTTP ${scRes.code()})"
                    }
                }

                // Everything the screen needs is here now — publish it all at once.
                match = m
                teamAPlayers = aPlayers
                teamBPlayers = bPlayers
                applyScorecard(sc, fireEvent = false) // first look at this match — nothing "just happened"
                deriveScreen()
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun rosterFor(teamId: Long): List<PlayerResponse> {
        val m = match ?: return emptyList()
        return if (teamId == m.teamAId) teamAPlayers else teamBPlayers
    }

    /** Team batting in the given innings (1 or 2) — derived directly from the toss result. */
    fun battingTeamId(inningsNumber: Int): Long? {
        val m = match ?: return null
        val winnerId = m.tossWinnerTeamId ?: return null
        val firstInningsBatting = if (m.tossDecision == "BAT") winnerId
        else (if (winnerId == m.teamAId) m.teamBId else m.teamAId)
        return if (inningsNumber == 1) firstInningsBatting
        else (if (firstInningsBatting == m.teamAId) m.teamBId else m.teamAId)
    }
    fun bowlingTeamId(inningsNumber: Int): Long? {
        val m = match ?: return null
        val bat = battingTeamId(inningsNumber) ?: return null
        return if (bat == m.teamAId) m.teamBId else m.teamAId
    }

    fun submitToss(winnerTeamId: Long, decision: String) {
        val m = match ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.submitToss(m.id, TossRequest(winnerTeamId, decision))
                if (res.isSuccessful && res.body() != null) {
                    match = res.body()
                    screen = OnlineScreen.OPENERS
                    info = "Toss recorded"
                } else error = "Toss submission failed (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun startInnings(strikerId: Long, nonStrikerId: Long, bowlerId: Long) {
        val m = match ?: return
        val inningsNumber = (scorecard?.innings?.size ?: 0) + 1
        val battingId = battingTeamId(inningsNumber) ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.startInnings(m.id, StartInningsRequest(battingId, strikerId, nonStrikerId, bowlerId))
                if (res.isSuccessful && res.body() != null) {
                    applyScorecard(res.body(), fireEvent = false) // new innings — nothing to call out yet
                    screen = OnlineScreen.LIVE
                    info = "Innings started"
                } else error = "Couldn't start the innings (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    private fun submit(request: BallRequest) {
        val m = match ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.submitBall(m.id, request)
                if (res.isSuccessful && res.body() != null) {
                    applyScorecard(res.body(), fireEvent = true)
                    deriveScreen()
                } else error = "Couldn't submit that ball (HTTP ${res.code()}): ${res.errorBody()?.string() ?: ""}"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun addRuns(runs: Int) = submit(BallRequest("RUN", runs = runs, clientBallUuid = UUID.randomUUID().toString()))

    // GAP FIX (Android side of the backend ScoringEngine fix): these used to always submit a
    // flat +1 with no way to express "the ball ran to the boundary while wide" or "hit for four
    // off a no-ball" — now every extra type can carry its actual run count, matching what the
    // backend now actually does with it (see ScoringEngine.addWideOrNoBall/addByeOrLegBye).
    fun addWide(extraRuns: Int = 0) = submit(BallRequest("WIDE", runs = extraRuns, clientBallUuid = UUID.randomUUID().toString()))
    fun addNoBall(batRuns: Int = 0) = submit(BallRequest("NOBALL", runs = batRuns, clientBallUuid = UUID.randomUUID().toString()))
    fun addBye(runs: Int = 1) = submit(BallRequest("BYE", runs = runs, clientBallUuid = UUID.randomUUID().toString()))
    fun addLegBye(runs: Int = 1) = submit(BallRequest("LEGBYE", runs = runs, clientBallUuid = UUID.randomUUID().toString()))

    /** @param runsCompleted only meaningful for a RUN_OUT — runs the batsmen completed before the throw came in. */
    fun confirmWicket(dismissal: String, runsCompleted: Int = 0) =
        submit(BallRequest("WICKET", runs = runsCompleted, dismissalType = dismissal, clientBallUuid = UUID.randomUUID().toString()))

    fun selectNextBowler(playerId: Long) {
        val m = match ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.setNextBowler(m.id, NextBowlerRequest(playerId))
                if (res.isSuccessful && res.body() != null) { applyScorecard(res.body(), fireEvent = false); deriveScreen() }
                else error = "That bowler wasn't accepted (HTTP ${res.code()}): ${res.errorBody()?.string() ?: ""}"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun selectNextBatter(playerId: Long) {
        val m = match ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.setNextBatter(m.id, playerId)
                if (res.isSuccessful && res.body() != null) { applyScorecard(res.body(), fireEvent = false); deriveScreen() }
                else error = "Couldn't set the next batter (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun undo() {
        val m = match ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.undoBall(m.id)
                if (res.isSuccessful && res.body() != null) {
                    applyScorecard(res.body(), fireEvent = false) // its "new latest line" is really the previous ball
                    deriveScreen()
                    info = "Last ball undone"
                } else error = "Nothing to undo, or undo failed (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    private suspend fun refreshScorecard() {
        val m = match ?: return
        try {
            val res = RetrofitClient.api.getScorecard(m.id)
            if (res.isSuccessful) { applyScorecard(res.body(), fireEvent = true); deriveScreen() }
            else error = "Couldn't load the scorecard (HTTP ${res.code()})"
        } catch (e: Exception) {
            error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
        }
    }

    /**
     * Applies a freshly-fetched/pushed scorecard and, unless suppressed, checks whether the
     * newest commentary line on the currently-live innings is a wicket/four/six worth calling
     * out. [fireEvent] is false for cases where a "new latest line" doesn't mean a new ball
     * actually just happened — the very first load of a match/innings (whatever's on top is
     * old news) and an undo (its "new" latest line is really the previous ball, re-surfacing it
     * as if it just happened would be misleading).
     */
    private fun applyScorecard(sc: MatchScorecardDto?, fireEvent: Boolean) {
        scorecard = sc
        val latestLine = (sc?.innings?.lastOrNull { it.status == "LIVE" } ?: sc?.innings?.lastOrNull())
            ?.commentary?.firstOrNull()
        if (fireEvent && latestLine != null && latestLine != lastSeenCommentaryLine) {
            val detail = latestLine.substringAfter('•').trim().ifEmpty { latestLine }
            liveEvent = when {
                "WICKET!" in latestLine -> MatchEvent(MatchEventType.WICKET, detail)
                "SIX!" in latestLine -> MatchEvent(MatchEventType.SIX, detail)
                "FOUR!" in latestLine -> MatchEvent(MatchEventType.FOUR, detail)
                else -> null
            }
        }
        lastSeenCommentaryLine = latestLine
    }

    private fun deriveScreen() {
        val m = match ?: return
        val sc = scorecard
        screen = when {
            m.status == "COMPLETED" -> OnlineScreen.RESULT
            // Feature request #3: the deciding innings finished, but nothing about the result
            // exists yet — the assigned scorer has to explicitly close the match first. A
            // distinct screen from both LIVE (scoring is genuinely done) and RESULT (no summary
            // exists yet) — see AwaitingCloseStep.
            m.status == "INNINGS_COMPLETE" -> OnlineScreen.AWAITING_CLOSE
            m.tossDecision == null -> OnlineScreen.TOSS
            sc == null || sc.innings.isEmpty() -> OnlineScreen.OPENERS
            sc.innings.last().status == "LIVE" -> OnlineScreen.LIVE
            sc.innings.size == 1 -> OnlineScreen.OPENERS // innings 1 done, set up innings 2
            else -> OnlineScreen.RESULT
        }
        if (screen == OnlineScreen.LIVE) startPollingIfNeeded() else stopPolling()
    }

    /** Feature request #3 — only the assigned scorer (or an admin); backend re-validates regardless. */
    fun closeMatch() {
        val matchId = match?.id ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.closeMatch(matchId)
                if (res.isSuccessful && res.body() != null) {
                    match = res.body()
                    deriveScreen()
                    info = "Match closed"
                } else error = when (res.code()) {
                    403 -> "Only the assigned scorer (or an admin) can close this match"
                    400 -> res.errorBody()?.string() ?: "This match isn't ready to close yet"
                    else -> "Couldn't close the match (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    // ---------------- scorer requests (feature request #2) ----------------

    var scorerRequests by mutableStateOf<List<ScorerRequestResponse>>(emptyList())
    var myScorerRequestStatus by mutableStateOf<String?>(null) // this device's own latest request, if any

    fun requestToScore() {
        val matchId = match?.id ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.submitScorerRequest(matchId, null)
                if (res.isSuccessful && res.body() != null) {
                    myScorerRequestStatus = res.body()!!.status
                    info = "Request to score sent"
                } else error = when (res.code()) {
                    403 -> "Only a player on one of this match's two teams can request to score it"
                    409 -> "You already have a pending request for this match"
                    else -> "Couldn't submit the request (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    /** Super Admin only — backend re-validates regardless. */
    fun loadScorerRequests() {
        val matchId = match?.id ?: return
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getScorerRequests(matchId)
                if (res.isSuccessful) scorerRequests = res.body().orEmpty()
            } catch (_: Exception) { /* the review panel just stays empty if this fails */ }
        }
    }

    fun acceptScorerRequest(requestId: Long) {
        val matchId = match?.id ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.acceptScorerRequest(matchId, requestId)
                if (res.isSuccessful) {
                    loadScorerRequests()
                    loadMatch(matchId) // refresh so scorerUserId reflects the new assignment
                    info = "Scorer request accepted"
                } else error = "Couldn't accept the request (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    fun rejectScorerRequest(requestId: Long) {
        val matchId = match?.id ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.rejectScorerRequest(matchId, requestId, null)
                if (res.isSuccessful) { loadScorerRequests(); info = "Scorer request rejected" }
                else error = "Couldn't reject the request (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    /** Feature request #1 (updated) — direct assignment by admin/manager. */
    fun assignScorerDirect(userId: Long) {
        val matchId = match?.id ?: return
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.assignScorer(matchId, AssignScorerRequest(userId))
                if (res.isSuccessful) {
                    loadMatch(matchId) // refresh to show the new assignment
                    info = "Scorer assigned"
                } else error = "Couldn't assign that scorer (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    // GAP FIX ("after 6-ball over finished, selecting next bowler throws 'the current over
    // isn't finished yet', scoring stuck"): this used to infer "needs a bowler" purely from
    // oversDisplay ending ".0" — but that's identical whether a bowler still needs picking OR
    // one was already picked and the new over's first ball just hasn't been bowled yet
    // (legalBalls doesn't move until it is). So the picker kept showing after a successful
    // selection, and tapping again hit the server's "already have a bowler" rejection — looking
    // exactly like bowler selection was completely broken. currentBowlerId is the server's
    // actual answer, not a guess from ball counts.
    fun needsNewBowler(inn: InningsScorecardDto): Boolean {
        return inn.status == "LIVE" && inn.currentBowlerId == null
    }

    /** Current innings needs a new batter: whoever's marked as striker is actually out. */
    fun needsNewBatter(inn: InningsScorecardDto): Boolean {
        val striker = inn.battingCard.find { it.onCrease == "STRIKER" }
        return inn.status == "LIVE" && striker != null && striker.out
    }

    /** Batting-team players not yet on the scorecard at all (haven't batted, aren't out). */
    fun availableNextBatters(inn: InningsScorecardDto): List<PlayerResponse> {
        val battingId = battingTeamId(inn.inningsNumber) ?: return emptyList()
        val already = inn.battingCard.map { it.playerId }.toSet()
        return rosterFor(battingId).filter { it.id !in already }
    }
}
