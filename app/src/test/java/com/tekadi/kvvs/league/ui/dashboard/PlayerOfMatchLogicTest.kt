package com.tekadi.kvvs.league.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** KvsvRequest1.3 #7 — splitting the Player of the Match line out so the name can be bolded. */
class PlayerOfMatchLogicTest {

    private val summary = "Titans vs Warriors — Titans won by 12 runs (Scored by Riya)\nPlayer of the Match: Arjun Rao"

    @Test
    fun theAwardLineIsRemovedFromTheBodyAndTheNameReturned() {
        val parts = splitPlayerOfMatch(summary, structuredName = null)
        assertEquals("Titans vs Warriors — Titans won by 12 runs (Scored by Riya)", parts.body)
        assertEquals("Arjun Rao", parts.playerOfMatch)
    }

    @Test
    fun theStructuredNameWinsOverStaleText() {
        // e.g. an admin override whose feed text hadn't been refreshed
        val parts = splitPlayerOfMatch(summary, structuredName = "Jasprit Bumrah")
        assertEquals("Jasprit Bumrah", parts.playerOfMatch)
        assertEquals("Titans vs Warriors — Titans won by 12 runs (Scored by Riya)", parts.body)
    }

    @Test
    fun aSummaryWithNoAwardIsLeftAsIs() {
        val parts = splitPlayerOfMatch("Titans vs Warriors — Match tied", structuredName = null)
        assertEquals("Titans vs Warriors — Match tied", parts.body)
        assertNull(parts.playerOfMatch)
    }

    @Test
    fun aStructuredNameIsUsedEvenWhenTheTextHasNoAwardLine() {
        val parts = splitPlayerOfMatch("Titans vs Warriors — Titans won", structuredName = "Arjun Rao")
        assertEquals("Titans vs Warriors — Titans won", parts.body)
        assertEquals("Arjun Rao", parts.playerOfMatch)
    }

    @Test
    fun blankNamesCountAsNoAward() {
        assertNull(splitPlayerOfMatch("A vs B\nPlayer of the Match: ", structuredName = "  ").playerOfMatch)
    }

    @Test
    fun onlyTheAwardLineIsRemovedFromAMultiLineMessage() {
        val parts = splitPlayerOfMatch("Line one\nPlayer of the Match: X\nLine three", structuredName = null)
        assertEquals("Line one\nLine three", parts.body)
        assertEquals("X", parts.playerOfMatch)
    }
}
