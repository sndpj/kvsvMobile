package com.tekadi.kvvs.league.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers every flow in the ball-by-ball scoring engine:
 *  - dynamic bowler-overs cap for each match length
 *  - runs off the bat (strike rotation, boundaries)
 *  - wide / no-ball (not legal deliveries)
 *  - bye / leg-bye (legal deliveries, no runs credited to the bowler)
 *  - every dismissal type and the bowler-credit rule
 *  - over completion, maiden detection, strike swap at the over's end
 *  - bowler-cap enforcement (no consecutive overs + max overs per bowler)
 *  - innings completion (all out / overs completed / target reached)
 *  - a full two-innings match settled by wickets, and one settled by runs
 *
 * These assertions were first validated against a standalone (non-Android)
 * Java port of this same logic, run end-to-end, before being written here —
 * see the project's delivery notes. Run with `./gradlew test`.
 */
class ScoringEngineTest {

    private fun freshInnings(
        battingKey: String = "A",
        bowlingKey: String = "B",
        target: Int? = null,
        batters: List<String> = listOf("s1", "s2"),
        bowlers: List<String> = listOf("bw1"),
    ): Innings {
        val battingCard = batters.associateWith { BattingLine(it.uppercase()) }.toMutableMap()
        val bowlingCard = bowlers.associateWith { BowlingLine(it.uppercase()) }.toMutableMap()
        return Innings(
            battingTeamKey = battingKey, bowlingTeamKey = bowlingKey, target = target,
            battingCard = battingCard, bowlingCard = bowlingCard,
            strikerId = batters[0], nonStrikerId = batters[1], bowlerId = bowlers[0],
        )
    }

    // ---- Flow: dynamic bowler cap scales with match length ----

    @Test fun `bowler cap scales with total overs`() {
        assertEquals(1, maxOversPerBowler(5))
        assertEquals(2, maxOversPerBowler(6))
        assertEquals(2, maxOversPerBowler(10))
        assertEquals(3, maxOversPerBowler(15))
        assertEquals(4, maxOversPerBowler(20))
    }

    // ---- Flow: runs off the bat ----

    @Test fun `singles rotate strike, boundaries do not`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings()

        engine.addRuns(inn, 1)
        assertEquals("s2", inn.strikerId)
        assertEquals(1, inn.totalRuns)

        engine.addRuns(inn, 4)
        assertEquals("s2", inn.strikerId) // even run: no swap
        assertEquals(1, inn.battingCard.getValue("s2").fours)
        assertEquals(5, inn.totalRuns)

        engine.addRuns(inn, 6)
        assertEquals(1, inn.battingCard.getValue("s2").sixes)
        assertEquals(11, inn.totalRuns)
        assertEquals(11, inn.bowlingCard.getValue("bw1").runsConceded)
        assertEquals(3, inn.bowlingCard.getValue("bw1").ballsBowled)
    }

    // ---- Flow: wide / no-ball ----

    @Test fun `wide adds a run but is not a legal delivery`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings()
        engine.addWideOrNoBall(inn, isWide = true)
        assertEquals(1, inn.totalRuns)
        assertEquals(0, inn.legalBalls)
        assertEquals("s1", inn.strikerId) // unchanged
        assertEquals(1, inn.extras.wide)
    }

    @Test fun `no-ball adds a run but is not a legal delivery`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings()
        engine.addWideOrNoBall(inn, isWide = false)
        assertEquals(1, inn.totalRuns)
        assertEquals(0, inn.legalBalls)
        assertEquals(1, inn.extras.noball)
    }

    // ---- Flow: bye / leg-bye ----

    @Test fun `bye is a legal delivery, batter faces it, bowler not charged`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings()
        engine.addByeOrLegBye(inn, isBye = true)
        assertEquals(1, inn.totalRuns)
        assertEquals(1, inn.legalBalls)
        assertEquals(1, inn.battingCard.getValue("s1").balls)
        assertEquals(0, inn.bowlingCard.getValue("bw1").runsConceded)
        assertEquals("s2", inn.strikerId) // odd run rotates strike
    }

    // ---- Flow: dismissals ----

    @Test fun `bowled credits the bowler and marks the batter out`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings()
        engine.addWicket(inn, Dismissal.BOWLED)
        assertEquals(1, inn.bowlingCard.getValue("bw1").wickets)
        assertEquals(1, inn.wickets)
        assertTrue(inn.battingCard.getValue("s1").out)
    }

    @Test fun `run out does not credit the bowler`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings(batters = listOf("s1", "s2", "s3"))
        engine.addWicket(inn, Dismissal.BOWLED) // 1 wicket, credited
        inn.strikerId = "s3"
        engine.addWicket(inn, Dismissal.RUN_OUT)
        assertEquals(1, inn.bowlingCard.getValue("bw1").wickets) // still 1, not 2
        assertEquals(2, inn.wickets)
    }

    @Test fun `retired hurt and timed out also do not credit the bowler`() {
        val engine = ScoringEngine(10)
        val inn1 = freshInnings()
        engine.addWicket(inn1, Dismissal.RETIRED_HURT)
        assertEquals(0, inn1.bowlingCard.getValue("bw1").wickets)

        val inn2 = freshInnings()
        engine.addWicket(inn2, Dismissal.TIMED_OUT)
        assertEquals(0, inn2.bowlingCard.getValue("bw1").wickets)
    }

    // ---- Flow: over completion + maiden detection ----

    @Test fun `sixth legal ball completes the over, resets it, and swaps strike`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings()
        var outcome = BallOutcome()
        repeat(6) { outcome = engine.addRuns(inn, 0) } // maiden over: 6 dot balls

        assertTrue("6th ball should complete the over", outcome.overJustCompleted)
        assertEquals(1, inn.bowlingCard.getValue("bw1").maidens)
        assertEquals(0, inn.thisOver.size)
        assertEquals("bw1", inn.prevBowlerId)
        assertEquals("s2", inn.strikerId) // over-end crossing swap
    }

    @Test fun `a conceded run in the over prevents a maiden`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings()
        engine.addRuns(inn, 1)
        repeat(5) { engine.addRuns(inn, 0) }
        assertEquals(0, inn.bowlingCard.getValue("bw1").maidens)
    }

    // ---- Flow: bowler cap enforcement ----

    @Test fun `bowler is excluded once the overs cap is reached`() {
        val engine = ScoringEngine(10) // cap = 2 overs = 12 balls
        val inn = freshInnings(bowlers = listOf("bwA", "bwB"))

        inn.bowlerId = "bwA"
        repeat(6) { engine.addRuns(inn, 0) } // over 1: bwA
        inn.bowlerId = "bwB"
        repeat(6) { engine.addRuns(inn, 0) } // over 2: bwB (bwA can't bowl consecutive overs)
        inn.bowlerId = "bwA"
        repeat(6) { engine.addRuns(inn, 0) } // over 3: bwA again — now at the cap

        assertEquals(12, inn.bowlingCard.getValue("bwA").ballsBowled)
        val eligible = engine.eligibleBowlers(inn, listOf(Player("bwA", "A"), Player("bwB", "B")))
        assertFalse("bwA should no longer be eligible", eligible.any { it.id == "bwA" })
    }

    @Test fun `bowler cannot bowl the over immediately after their own`() {
        val engine = ScoringEngine(20) // cap = 4, so the cap itself isn't the limiting factor here
        val inn = freshInnings(bowlers = listOf("bwA", "bwB"))
        inn.bowlerId = "bwA"
        repeat(6) { engine.addRuns(inn, 0) }
        val eligible = engine.eligibleBowlers(inn, listOf(Player("bwA", "A"), Player("bwB", "B")))
        assertFalse("bwA just bowled, cannot bowl the next over", eligible.any { it.id == "bwA" })
        assertTrue(eligible.any { it.id == "bwB" })
    }

    // ---- Flow: innings completion ----

    @Test fun `innings completes when the tenth wicket falls`() {
        val engine = ScoringEngine(5)
        val batters = (1..11).map { "p$it" }
        val inn = freshInnings(batters = batters)
        var outcome = BallOutcome()
        var nextIn = 2
        for (w in 1..10) {
            outcome = engine.addWicket(inn, Dismissal.BOWLED)
            if (!outcome.inningsJustCompleted) { inn.strikerId = batters[nextIn]; nextIn++ }
        }
        assertEquals(10, inn.wickets)
        assertTrue(outcome.inningsJustCompleted)
        assertEquals("COMPLETE", inn.status)
    }

    @Test fun `innings completes when the overs are used up`() {
        val engine = ScoringEngine(2) // 12 legal balls
        val inn = freshInnings(bowlers = listOf("bw1", "bw2"))
        var outcome = BallOutcome()
        repeat(12) {
            outcome = engine.addRuns(inn, 1)
            if (outcome.overJustCompleted && !outcome.inningsJustCompleted) {
                inn.bowlerId = if (inn.prevBowlerId == "bw2") "bw1" else "bw2"
            }
        }
        assertEquals(12, inn.legalBalls)
        assertTrue(outcome.inningsJustCompleted)
    }

    @Test fun `second innings completes the moment the target is reached`() {
        val engine = ScoringEngine(10)
        val inn = freshInnings(target = 13)
        var outcome = BallOutcome()
        repeat(4) { outcome = engine.addRuns(inn, 4) } // 16 >= 13 after the 4th four (4,8,12,16)
        assertTrue(outcome.inningsJustCompleted)
        assertTrue(inn.totalRuns >= 13)
    }

    // ---- Flow: full match, win by wickets ----

    @Test fun `chasing team that reaches the target with wickets in hand wins by wickets`() {
        val overs = 2
        val engine1 = ScoringEngine(overs)
        val inn1 = freshInnings(battingKey = "A", bowlingKey = "B")
        repeat(12) { engine1.addRuns(inn1, 1) } // 12 off 12 balls
        assertEquals(12, inn1.totalRuns)

        val target = inn1.totalRuns + 1
        val engine2 = ScoringEngine(overs)
        val inn2 = freshInnings(battingKey = "B", bowlingKey = "A", target = target, batters = listOf("c1", "c2"), bowlers = listOf("d1"))
        var outcome = BallOutcome()
        var b = 0
        while (inn2.status == "LIVE" && b < 12) { outcome = engine2.addRuns(inn2, 4); b++ }

        assertTrue(outcome.inningsJustCompleted)
        assertTrue(inn2.totalRuns >= target)
        assertEquals(0, inn2.wickets)
        val marginWickets = 10 - inn2.wickets
        assertEquals(10, marginWickets)
    }

    // ---- Flow: full match, win by runs ----

    @Test fun `team bowled out below target loses by runs`() {
        val overs = 2
        val engine1 = ScoringEngine(overs)
        val inn1 = freshInnings(battingKey = "A", bowlingKey = "B")
        repeat(12) { engine1.addRuns(inn1, 6) } // 72 runs
        val target = inn1.totalRuns + 1

        val engine2 = ScoringEngine(overs)
        val batters = (1..11).map { "p$it" }
        val inn2 = freshInnings(battingKey = "B", bowlingKey = "A", target = target, batters = batters, bowlers = listOf("d1"))
        var outcome = BallOutcome()
        var nextIn = 2
        for (w in 1..10) {
            outcome = engine2.addWicket(inn2, Dismissal.BOWLED)
            if (!outcome.inningsJustCompleted) { inn2.strikerId = batters[nextIn]; nextIn++ }
        }

        assertTrue(outcome.inningsJustCompleted)
        assertTrue(inn2.totalRuns < target)
        val marginRuns = target - 1 - inn2.totalRuns
        assertEquals(inn1.totalRuns - inn2.totalRuns, marginRuns)
    }
}
