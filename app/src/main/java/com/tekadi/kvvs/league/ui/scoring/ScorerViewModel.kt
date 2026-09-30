package com.tekadi.kvvs.league.ui.scoring

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.*
import com.tekadi.kvvs.league.network.BallRequest
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { SETUP, TOSS, OPENERS_1, LIVE, BREAK, OPENERS_2, RESULT }

class ScorerViewModel(app: Application) : AndroidViewModel(app) {

    private val offlineQueue = OfflineQueue(app)
    // Set this once a real match has been created via the backend (see ApiService.startInnings).
    // While null, the UI still works fully offline — nothing is queued for sync.
    var remoteMatchId: Long? = null

    var screen by mutableStateOf(Screen.SETUP)
    var oversLimit by mutableStateOf(10)
    var teamA by mutableStateOf(Team("A", "Titans CC", sampleRoster(TEAM_A_NAMES, "A")))
    var teamB by mutableStateOf(Team("B", "Warriors CC", sampleRoster(TEAM_B_NAMES, "B")))

    var tossWinnerKey by mutableStateOf<String?>(null)
    var tossDecision by mutableStateOf<String?>(null) // "BAT" | "BOWL"

    var innings by mutableStateOf<List<Innings>>(emptyList())
    var currentInningsIndex by mutableStateOf(0)
    val current: Innings? get() = innings.getOrNull(currentInningsIndex)

    var pendingWicket by mutableStateOf(false)
    var pendingNewBatter by mutableStateOf(false)
    var pendingNewBowler by mutableStateOf(false)
    var syncStatus by mutableStateOf("")

    private var engine = ScoringEngine(oversLimit)
    private val history = ArrayDeque<Innings>()

    private fun teamOf(key: String) = if (key == "A") teamA else teamB
    val bowlerCap: Int get() = engine.bowlerCap

    fun confirmToss(winner: String, decision: String) {
        tossWinnerKey = winner; tossDecision = decision
        screen = Screen.OPENERS_1
    }

    private fun battingKeyForInnings(index: Int): String {
        val first = if (tossDecision == "BAT") tossWinnerKey!! else if (tossWinnerKey == "A") "B" else "A"
        return if (index == 0) first else if (first == "A") "B" else "A"
    }

    fun startInnings1(strikerId: String, nonStrikerId: String, bowlerId: String) {
        engine = ScoringEngine(oversLimit)
        val bKey = battingKeyForInnings(0); val bowlKey = if (bKey == "A") "B" else "A"
        val inn = buildInnings(teamOf(bKey), teamOf(bowlKey), null, strikerId, nonStrikerId, bowlerId)
        innings = listOf(inn)
        currentInningsIndex = 0
        screen = Screen.LIVE
    }

    fun startInnings2(strikerId: String, nonStrikerId: String, bowlerId: String) {
        val bKey = battingKeyForInnings(1); val bowlKey = if (bKey == "A") "B" else "A"
        val target = innings[0].totalRuns + 1
        val inn = buildInnings(teamOf(bKey), teamOf(bowlKey), target, strikerId, nonStrikerId, bowlerId)
        innings = innings + inn
        currentInningsIndex = 1
        screen = Screen.LIVE
    }

    private fun buildInnings(bat: Team, bowl: Team, target: Int?, striker: String, nonStriker: String, bowler: String): Innings {
        val battingCard = bat.players.associate { it.id to BattingLine(it.name) }.toMutableMap()
        val bowlingCard = bowl.players.associate { it.id to BowlingLine(it.name) }.toMutableMap()
        battingCard[striker]?.batted = true
        battingCard[nonStriker]?.batted = true
        return Innings(bat.key, bowl.key, target, battingCard = battingCard, bowlingCard = bowlingCard,
            strikerId = striker, nonStrikerId = nonStriker, bowlerId = bowler)
    }

    private fun pushHistory() { current?.let { history.addLast(it.deepCopy()) }; if (history.size > 50) history.removeFirst() }

    private fun apply(mutate: (Innings) -> BallOutcome) {
        val inn = current ?: return
        val outcome = mutate(inn)
        innings = innings.toMutableList().also { it[currentInningsIndex] = inn }
        if (outcome.inningsJustCompleted) {
            pendingWicket = false; pendingNewBatter = false; pendingNewBowler = false
            screen = if (currentInningsIndex == 0) Screen.BREAK else Screen.RESULT
        } else if (outcome.overJustCompleted) {
            pendingNewBowler = true
        }
    }

    fun addRuns(runs: Int) {
        pushHistory()
        apply { engine.addRuns(it, runs) }
        syncBall(BallRequest("RUN", runs = runs, clientBallUuid = UUID.randomUUID().toString()))
    }

    fun addWide() { pushHistory(); apply { engine.addWideOrNoBall(it, isWide = true) }; syncBall(BallRequest("WIDE", clientBallUuid = UUID.randomUUID().toString())) }
    fun addNoBall() { pushHistory(); apply { engine.addWideOrNoBall(it, isWide = false) }; syncBall(BallRequest("NOBALL", clientBallUuid = UUID.randomUUID().toString())) }
    fun addBye() { pushHistory(); apply { engine.addByeOrLegBye(it, isBye = true) }; syncBall(BallRequest("BYE", clientBallUuid = UUID.randomUUID().toString())) }
    fun addLegBye() { pushHistory(); apply { engine.addByeOrLegBye(it, isBye = false) }; syncBall(BallRequest("LEGBYE", clientBallUuid = UUID.randomUUID().toString())) }

    fun confirmWicket(dismissal: Dismissal) {
        pushHistory()
        pendingWicket = false
        apply { engine.addWicket(it, dismissal) }
        if (current?.status == "LIVE") pendingNewBatter = true
        syncBall(BallRequest("WICKET", dismissalType = dismissal.name, clientBallUuid = UUID.randomUUID().toString()))
    }

    fun selectNewBatter(playerId: String) {
        current?.let { it.strikerId = playerId; it.battingCard[playerId]?.batted = true }
        innings = innings.toMutableList().also { it[currentInningsIndex] = current!! }
        pendingNewBatter = false
    }

    fun selectNewBowler(playerId: String) {
        current?.let { it.bowlerId = playerId }
        innings = innings.toMutableList().also { it[currentInningsIndex] = current!! }
        pendingNewBowler = false
    }

    fun eligibleBowlers(): List<Player> {
        val inn = current ?: return emptyList()
        return engine.eligibleBowlers(inn, teamOf(inn.bowlingTeamKey).players)
    }

    fun undo() {
        if (history.isEmpty()) return
        val prev = history.removeLast()
        innings = innings.toMutableList().also { it[currentInningsIndex] = prev }
        pendingWicket = false; pendingNewBatter = false; pendingNewBowler = false
    }

    fun newMatch() {
        screen = Screen.SETUP; tossWinnerKey = null; tossDecision = null
        innings = emptyList(); currentInningsIndex = 0; history.clear()
        pendingWicket = false; pendingNewBatter = false; pendingNewBowler = false
    }

    /** Queues the ball locally (works fully offline) and opportunistically syncs. */
    private fun syncBall(request: BallRequest) {
        val matchId = remoteMatchId ?: return
        viewModelScope.launch {
            offlineQueue.enqueue(matchId, request)
            val result = offlineQueue.flushSync()
            syncStatus = result.fold(
                onSuccess = { n -> if (n > 0) "Synced" else "Up to date" },
                onFailure = { "Offline — ${offlineQueue.pendingCount()} pending" },
            )
        }
    }

    companion object {
        val TEAM_A_NAMES = listOf(
            "Aarav Mehta", "Rohan Kapoor", "Vikram Desai", "Siddharth Rao", "Karan Malhotra",
            "Arjun Nair", "Ishaan Verma", "Yash Chauhan", "Aditya Singh", "Manav Trivedi", "Nikhil Bhatt",
        )
        val TEAM_B_NAMES = listOf(
            "Kabir Joshi", "Dhruv Saxena", "Varun Reddy", "Aakash Menon", "Rajat Kulkarni",
            "Sameer Khanna", "Tanmay Ghosh", "Pranav Mishra", "Harsh Dubey", "Vivek Pillai", "Naveen Thakur",
        )
        fun sampleRoster(names: List<String>, prefix: String) = names.mapIndexed { i, n -> Player("$prefix$i", n) }
    }
}
