package com.tekadi.kvvs.league.data

import kotlinx.serialization.Serializable

@Serializable
data class Player(val id: String, val name: String)

@Serializable
data class Team(val key: String, val name: String, val players: List<Player>)

enum class BallType { RUN, WIDE, NOBALL, BYE, LEGBYE, WICKET }

enum class Dismissal(val label: String, val creditsBowler: Boolean) {
    BOWLED("Bowled", true),
    CAUGHT("Caught", true),
    LBW("LBW", true),
    RUN_OUT("Run Out", false),
    STUMPED("Stumped", true),
    HIT_WICKET("Hit Wicket", true),
    RETIRED_HURT("Retired Hurt", false),
    TIMED_OUT("Timed Out", false),
    HANDLED_BALL("Handled Ball", true),
    OBSTRUCTING_FIELD("Obstructing Field", false),
    HIT_BALL_TWICE("Hit Ball Twice", true),
}

data class BattingLine(
    val name: String,
    var runs: Int = 0,
    var balls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var out: Boolean = false,
    var howOut: String? = null,
    var outBy: String? = null,
    var batted: Boolean = false,
) {
    val strikeRate: Double get() = if (balls > 0) runs * 100.0 / balls else 0.0
}

data class BowlingLine(
    val name: String,
    var ballsBowled: Int = 0,
    var runsConceded: Int = 0,
    var wickets: Int = 0,
    var maidens: Int = 0,
    var thisOverRuns: Int = 0,
) {
    val overs: String get() = "${ballsBowled / 6}.${ballsBowled % 6}"
    val economy: Double get() = if (ballsBowled > 0) runsConceded * 6.0 / ballsBowled else 0.0
}

data class Extras(var wide: Int = 0, var noball: Int = 0, var bye: Int = 0, var legbye: Int = 0) {
    val total get() = wide + noball + bye + legbye
}

/** Standard rule: a bowler may bowl at most ceil(totalOvers / 5) overs. */
fun maxOversPerBowler(totalOvers: Int): Int = maxOf(1, (totalOvers + 4) / 5)

data class Innings(
    val battingTeamKey: String,
    val bowlingTeamKey: String,
    val target: Int? = null,
    var totalRuns: Int = 0,
    var wickets: Int = 0,
    var legalBalls: Int = 0,
    val extras: Extras = Extras(),
    val battingCard: MutableMap<String, BattingLine>,
    val bowlingCard: MutableMap<String, BowlingLine>,
    var strikerId: String? = null,
    var nonStrikerId: String? = null,
    var bowlerId: String? = null,
    var prevBowlerId: String? = null,
    val thisOver: MutableList<String> = mutableListOf(),
    val ballLog: MutableList<String> = mutableListOf(),
    var status: String = "LIVE", // LIVE | COMPLETE
) {
    val oversDisplay: String get() = "${legalBalls / 6}.${legalBalls % 6}"
    val currentRunRate: Double get() = if (legalBalls > 0) totalRuns * 6.0 / legalBalls else 0.0

    fun deepCopy(): Innings = copy(
        extras = extras.copy(),
        battingCard = battingCard.mapValues { it.value.copy() }.toMutableMap(),
        bowlingCard = bowlingCard.mapValues { it.value.copy() }.toMutableMap(),
        thisOver = thisOver.toMutableList(),
        ballLog = ballLog.toMutableList(),
    )
}

/** Result of applying one ball: tells the UI what to ask the scorer for next. */
data class BallOutcome(
    val overJustCompleted: Boolean = false,
    val inningsJustCompleted: Boolean = false,
)

/**
 * Pure scoring engine — no Android framework dependencies, fully unit-testable.
 * This mirrors the same rules implemented in the web demo (CricketLiveScorer.jsx):
 * strike rotation, over completion, maiden detection, and the dynamic
 * "max overs per bowler = ceil(totalOvers / 5)" cap.
 */
class ScoringEngine(private val totalOvers: Int) {

    val bowlerCap = maxOversPerBowler(totalOvers)
    val bowlerCapBalls = bowlerCap * 6

    private fun swapStrike(i: Innings) {
        val t = i.strikerId; i.strikerId = i.nonStrikerId; i.nonStrikerId = t
    }

    private fun finalize(i: Innings): BallOutcome {
        var overDone = false
        if (i.legalBalls > 0 && i.legalBalls % 6 == 0) {
            val bowler = i.bowlingCard.getValue(i.bowlerId!!)
            if (bowler.thisOverRuns == 0) bowler.maidens += 1
            bowler.thisOverRuns = 0
            i.prevBowlerId = i.bowlerId
            i.thisOver.clear()
            swapStrike(i)
            overDone = true
        }
        val allOut = i.wickets >= 10
        val oversDone = i.legalBalls >= totalOvers * 6
        val targetReached = i.target != null && i.totalRuns >= i.target
        val inningsDone = allOut || oversDone || targetReached
        if (inningsDone) i.status = "COMPLETE"
        return BallOutcome(overDone, inningsDone)
    }

    fun addRuns(i: Innings, runs: Int): BallOutcome {
        val striker = i.battingCard.getValue(i.strikerId!!)
        val bowler = i.bowlingCard.getValue(i.bowlerId!!)
        i.totalRuns += runs
        striker.runs += runs; striker.balls += 1
        if (runs == 4) striker.fours += 1
        if (runs == 6) striker.sixes += 1
        bowler.runsConceded += runs; bowler.ballsBowled += 1; bowler.thisOverRuns += runs
        i.legalBalls += 1
        i.thisOver.add(runs.toString())
        val label = when (runs) { 0 -> "no run"; 4 -> "FOUR!"; 6 -> "SIX!"; else -> "$runs runs" }
        i.ballLog.add(0, "${overBallLabel(i.legalBalls)} • ${striker.name} — $label")
        if (runs % 2 == 1) swapStrike(i)
        return finalize(i)
    }

    fun addWideOrNoBall(i: Innings, isWide: Boolean): BallOutcome {
        val bowler = i.bowlingCard.getValue(i.bowlerId!!)
        i.totalRuns += 1
        if (isWide) i.extras.wide += 1 else i.extras.noball += 1
        bowler.runsConceded += 1; bowler.thisOverRuns += 1
        i.thisOver.add(if (isWide) "Wd" else "Nb")
        i.ballLog.add(0, "${i.legalBalls / 6}.${i.legalBalls % 6 + 1} • ${if (isWide) "Wide" else "No ball"} (+1)")
        val inningsDone = i.target != null && i.totalRuns >= i.target
        if (inningsDone) i.status = "COMPLETE"
        return BallOutcome(overJustCompleted = false, inningsJustCompleted = inningsDone)
    }

    fun addByeOrLegBye(i: Innings, isBye: Boolean): BallOutcome {
        val striker = i.battingCard.getValue(i.strikerId!!)
        val bowler = i.bowlingCard.getValue(i.bowlerId!!)
        i.totalRuns += 1
        if (isBye) i.extras.bye += 1 else i.extras.legbye += 1
        striker.balls += 1; bowler.ballsBowled += 1; i.legalBalls += 1
        i.thisOver.add(if (isBye) "B" else "LB")
        i.ballLog.add(0, "${overBallLabel(i.legalBalls)} • ${if (isBye) "Bye" else "Leg bye"} (+1)")
        swapStrike(i)
        return finalize(i)
    }

    fun addWicket(i: Innings, dismissal: Dismissal): BallOutcome {
        val striker = i.battingCard.getValue(i.strikerId!!)
        val bowler = i.bowlingCard.getValue(i.bowlerId!!)
        i.legalBalls += 1; striker.balls += 1; bowler.ballsBowled += 1
        striker.out = true; striker.howOut = dismissal.label
        if (dismissal.creditsBowler) { bowler.wickets += 1; striker.outBy = bowler.name }
        i.wickets += 1
        i.thisOver.add("W")
        i.ballLog.add(0, "${overBallLabel(i.legalBalls)} • WICKET! ${striker.name} ${dismissal.label}${if (dismissal.creditsBowler) " b ${bowler.name}" else ""}")
        return finalize(i)
    }

    /** Bowlers eligible for the next over: not the previous over's bowler, and under the cap. */
    fun eligibleBowlers(i: Innings, allBowlingPlayers: List<Player>): List<Player> =
        allBowlingPlayers.filter { p ->
            val line = i.bowlingCard[p.id]
            p.id != i.prevBowlerId && (line == null || line.ballsBowled < bowlerCapBalls)
        }

    private fun overBallLabel(legalBalls: Int): String {
        if (legalBalls <= 0) return "0.1"
        val overs = (legalBalls - 1) / 6
        val ball = (legalBalls - 1) % 6 + 1
        return "$overs.$ball"
    }
}
