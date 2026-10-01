package com.tekadi.kvvs.league.ui.scoring

import com.tekadi.kvvs.league.network.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** KvsvRequest1.3 #6 (admin close with reason) and #7 (Player of the Match override). */
class MatchAdminLogicTest {

    // ---------------- #6 close match ----------------

    @Test
    fun closeIsOfferedToAnAdminForAnyUnfinishedMatch() {
        listOf("SCHEDULED", "LIVE", "INNINGS_COMPLETE").forEach {
            assertTrue(it, showCloseMatchAction(canCloseDirectly = true, matchStatus = it))
        }
    }

    @Test
    fun closeIsNotOfferedForAFinishedMatch() {
        assertFalse(showCloseMatchAction(true, "COMPLETED"))
        assertFalse(showCloseMatchAction(true, "CANCELLED"))
        assertFalse(showCloseMatchAction(true, null))
    }

    @Test
    fun closeIsNotOfferedToNonAdmins() {
        assertFalse(showCloseMatchAction(canCloseDirectly = false, matchStatus = "LIVE"))
    }

    @Test
    fun aReasonIsRequired() {
        assertEquals("Enter a reason for closing the match", closeReasonError("   "))
        assertNull(closeReasonError("Rain stopped play"))
    }

    @Test
    fun aReasonOverTheLimitIsRejected() {
        assertNull(closeReasonError("x".repeat(CLOSE_REASON_MAX)))
        assertEquals("Keep the reason to 300 characters or fewer", closeReasonError("x".repeat(CLOSE_REASON_MAX + 1)))
    }

    @Test
    fun aCancelledMatchIsOverEvenIfOnlyTheScorecardSaysSoYet() {
        assertTrue(isMatchOver("CANCELLED", null))
        assertTrue(isMatchOver("COMPLETED", "COMPLETED"))
        assertTrue(isMatchOver("LIVE", "CANCELLED"))
        assertFalse(isMatchOver("LIVE", "LIVE"))
        assertFalse(isMatchOver("INNINGS_COMPLETE", "INNINGS_COMPLETE"))
    }

    // ---------------- #7 Player of the Match override ----------------

    @Test
    fun overrideIsOnlyForAdminsOnACompletedMatch() {
        assertTrue(showPlayerOfMatchOverride(true, "COMPLETED"))
        assertFalse(showPlayerOfMatchOverride(true, "LIVE"))
        assertFalse(showPlayerOfMatchOverride(true, "CANCELLED"))
        assertFalse(showPlayerOfMatchOverride(false, "COMPLETED"))
    }

    @Test
    fun candidatesAreBothSquadsAlphabeticallyWithoutDuplicates() {
        val a = listOf(PlayerResponse(1, 10, "rohit"), PlayerResponse(2, 10, "Arjun"))
        val b = listOf(PlayerResponse(3, 20, "Kiran"), PlayerResponse(2, 10, "Arjun"))
        assertEquals(listOf("Arjun", "Kiran", "rohit"), playerOfMatchCandidates(a, b).map { it.name })
    }
}
