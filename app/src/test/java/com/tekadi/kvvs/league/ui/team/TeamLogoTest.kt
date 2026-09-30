package com.tekadi.kvvs.league.ui.team

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Feature request: "add default team logo if not selected." Covers teamInitial(), the one piece
 * of pure logic behind the default-logo fallback badge (see TeamManagementScreen.TeamLogo) —
 * this project has no Compose UI-testing setup, so the composable rendering itself isn't
 * covered by an executable test, only this extracted logic is.
 */
class TeamLogoTest {

    // ---------------- positive ----------------

    @Test
    fun usesTheFirstLetterUppercased() {
        assertEquals("T", teamInitial("Titans CC"))
    }

    @Test
    fun lowercaseNameStillProducesAnUppercaseInitial() {
        assertEquals("W", teamInitial("warriors"))
    }

    @Test
    fun leadingWhitespaceIsIgnored() {
        assertEquals("P", teamInitial("  Panthers"))
    }

    // ---------------- negative / edge cases ----------------

    @Test
    fun gapCheck_anEmptyNameFallsBackToAQuestionMarkRatherThanCrashing() {
        assertEquals("?", teamInitial(""))
    }

    @Test
    fun gapCheck_aWhitespaceOnlyNameAlsoFallsBackToAQuestionMark() {
        assertEquals("?", teamInitial("   "))
    }

    @Test
    fun aNameStartingWithADigitOrSymbolStillProducesSomething_notACrash() {
        assertEquals("7", teamInitial("7 Stars CC"))
        assertEquals("#", teamInitial("#1 Warriors"))
    }
}
