package com.tekadi.kvvs.league.ui.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** KvsvRequest1.3 #1 — the create-match form's per-bowler over restriction. */
class MatchFormLogicTest {

    @Test
    fun blankCustomCapMeansTheDefault() {
        val check = checkBowlerLimit(overs = 20, enabled = true, customText = "  ")
        assertNull(check.error)
        assertNull(check.maxOversPerBowler)
    }

    @Test
    fun aValidCustomCapIsSent() {
        assertEquals(3, checkBowlerLimit(20, true, "3").maxOversPerBowler)
        assertEquals(20, checkBowlerLimit(20, true, "20").maxOversPerBowler)
    }

    @Test
    fun turnedOffIgnoresWhateverWasTyped() {
        val check = checkBowlerLimit(20, enabled = false, customText = "99")
        assertNull(check.error)
        assertNull(check.maxOversPerBowler)
    }

    @Test
    fun zeroIsRejected() {
        assertEquals("Max overs per bowler must be at least 1", checkBowlerLimit(20, true, "0").error)
    }

    @Test
    fun moreThanTheMatchOversIsRejected() {
        val error = checkBowlerLimit(10, true, "11").error
        assertNotNull(error)
        assertTrue(error!!.contains("10 overs"))
    }

    @Test
    fun nonNumbersAreRejected() {
        assertEquals("Max overs per bowler must be a number", checkBowlerLimit(10, true, "two").error)
    }

    @Test
    fun hintShowsTheDefaultForTheTypedOvers() {
        assertEquals("Leave blank for the default: 4 overs each", bowlerLimitHint(20, enabled = true))
        assertEquals("Leave blank for the default: 1 over each", bowlerLimitHint(5, enabled = true))
    }

    @Test
    fun hintExplainsWhatTurningItOffMeans() {
        assertTrue(bowlerLimitHint(20, enabled = false).startsWith("No limit"))
    }

    @Test
    fun hintCopesWithOversNotTypedYet() {
        assertTrue(bowlerLimitHint(null, enabled = true).contains("default"))
    }
}
