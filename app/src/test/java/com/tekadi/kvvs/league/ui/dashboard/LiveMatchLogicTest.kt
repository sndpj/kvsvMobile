package com.tekadi.kvvs.league.ui.dashboard

import com.tekadi.kvvs.league.network.BattingLineDto
import com.tekadi.kvvs.league.network.BowlingLineDto
import com.tekadi.kvvs.league.network.InningsScorecardDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature request: "add current batsman names with score and striker and bowler name with over
 * status." Covers onCreaseBattingLines()/currentBowlerLine(), the pure logic behind the
 * Dashboard's live-match slider card (see DashboardScreen.LiveMatchCard) — this project has no
 * Compose UI-testing setup, so only the extracted logic itself is covered, not the rendering.
 */
class LiveMatchLogicTest {

    private fun battingLine(id: Long, name: String, runs: Int, balls: Int, onCrease: String?) =
        BattingLineDto(id, name, runs, balls, 0, 0, 0.0, false, null, onCrease)

    private fun bowlingLine(id: Long, name: String) =
        BowlingLineDto(id, name, "3.2", 18, 1, 0, 5.4)

    private fun inningsWith(battingCard: List<BattingLineDto>, bowlingCard: List<BowlingLineDto>, currentBowlerId: Long?) =
        InningsScorecardDto(
            inningsId = 1, inningsNumber = 1, battingTeamName = "Titans", bowlingTeamName = "Warriors",
            target = null, totalRuns = 80, wickets = 2, oversDisplay = "10.2", currentRunRate = 7.7,
            runsNeeded = null, ballsLeft = null, requiredRunRate = null,
            extrasWide = 0, extrasNoball = 0, extrasBye = 0, extrasLegbye = 0, extrasTotal = 0,
            battingCard = battingCard, bowlingCard = bowlingCard,
            thisOver = emptyList(), commentary = emptyList(), status = "LIVE",
            currentBowlerId = currentBowlerId,
        )

    // ---------------- onCreaseBattingLines: positive ----------------

    @Test
    fun returnsExactlyTheStrikerAndNonStriker() {
        val striker = battingLine(1, "Aarav Mehta", 34, 22, "STRIKER")
        val nonStriker = battingLine(2, "Kabir Joshi", 18, 15, "NON_STRIKER")
        val alreadyOut = battingLine(3, "Zara Khan", 12, 10, null)
        val notYetIn = battingLine(4, "Riya Shah", 0, 0, null)
        val inn = inningsWith(listOf(alreadyOut, striker, nonStriker, notYetIn), emptyList(), null)

        val result = onCreaseBattingLines(inn)

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Aarav Mehta" })
        assertTrue(result.any { it.name == "Kabir Joshi" })
    }

    // ---------------- onCreaseBattingLines: negative ----------------

    @Test
    fun gapCheck_returnsEmptyWhenNoOneIsCurrentlyOnCrease() {
        // e.g. between innings, or right after an all-out — no striker/non-striker set yet.
        val allOut = battingLine(1, "Aarav Mehta", 34, 22, null)
        val inn = inningsWith(listOf(allOut), emptyList(), null)

        assertTrue(onCreaseBattingLines(inn).isEmpty())
    }

    @Test
    fun gapCheck_returnsEmptyForAnEmptyBattingCard() {
        val inn = inningsWith(emptyList(), emptyList(), null)
        assertTrue(onCreaseBattingLines(inn).isEmpty())
    }

    // ---------------- currentBowlerLine: positive ----------------

    @Test
    fun findsTheBowlingLineMatchingCurrentBowlerId() {
        val bowler1 = bowlingLine(10, "Vikram Rao")
        val bowler2 = bowlingLine(11, "Sameer Patel")
        val inn = inningsWith(emptyList(), listOf(bowler1, bowler2), currentBowlerId = 11L)

        val result = currentBowlerLine(inn)

        assertEquals("Sameer Patel", result?.name)
    }

    // ---------------- currentBowlerLine: negative ----------------

    @Test
    fun gapCheck_returnsNullWhenCurrentBowlerIdIsNull() {
        // e.g. right after an over completes, awaiting the next bowler selection.
        val bowler = bowlingLine(10, "Vikram Rao")
        val inn = inningsWith(emptyList(), listOf(bowler), currentBowlerId = null)

        assertNull(currentBowlerLine(inn))
    }

    @Test
    fun gapCheck_returnsNullWhenNoBowlingLineMatchesTheId() {
        // Defensive: shouldn't happen in practice, but must not crash if the id doesn't resolve.
        val bowler = bowlingLine(10, "Vikram Rao")
        val inn = inningsWith(emptyList(), listOf(bowler), currentBowlerId = 999L)

        assertNull(currentBowlerLine(inn))
    }
}
