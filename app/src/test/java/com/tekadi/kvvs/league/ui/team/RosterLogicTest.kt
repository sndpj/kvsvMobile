package com.tekadi.kvvs.league.ui.team

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** KvsvRequest1.3 #2 — "No duplicate player entry", the add-player form's immediate check. */
class RosterLogicTest {

    private val roster = listOf("Arjun Rao", "Rohit Sharma")

    @Test
    fun theSameNameInADifferentCaseOrSpacingIsADuplicate() {
        assertEquals("Arjun Rao", duplicateRosterName("  arjun   RAO ", roster))
    }

    @Test
    fun aNewNameIsNotADuplicate() {
        assertNull(duplicateRosterName("Kiran More", roster))
    }

    @Test
    fun aNameThatOnlyStartsTheSameIsNotADuplicate() {
        assertNull(duplicateRosterName("Arjun", roster))
    }

    @Test
    fun blankIsNeverReportedAsADuplicate() {
        assertNull(duplicateRosterName("   ", roster + ""))
    }

    @Test
    fun normalisationMatchesTheBackendRule() {
        assertEquals("arjun rao", normalisePlayerName("\tArjun \n Rao  "))
    }
}
