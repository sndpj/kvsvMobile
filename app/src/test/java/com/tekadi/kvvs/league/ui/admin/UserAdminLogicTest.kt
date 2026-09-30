package com.tekadi.kvvs.league.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Feature: Super Admin role assign/revoke. Covers the pure logic behind UserListViewModel's
 * search and UserEditViewModel's save-guard — this project has no Compose UI-testing
 * infrastructure set up, so the composable rendering itself isn't covered by an executable
 * test, only the extracted logic is (same pattern as TeamLogoTest / ApiErrorsTest).
 */
class UserAdminLogicTest {

    // ---------------- normalizeUserSearchQuery ----------------

    @Test
    fun blankQueryNormalizesToNull_soTheBackendReturnsEveryUser() {
        assertNull(normalizeUserSearchQuery(""))
        assertNull(normalizeUserSearchQuery("   "))
    }

    @Test
    fun leadingAndTrailingWhitespaceIsTrimmed() {
        assertEquals("dravid", normalizeUserSearchQuery("  dravid  "))
    }

    @Test
    fun aNormalQueryPassesThroughUnchangedOnceTrimmed() {
        assertEquals("98765", normalizeUserSearchQuery("98765"))
    }

    // ---------------- roleUpdateBlockReason ----------------

    @Test
    fun noRoleSelectedIsBlocked() {
        assertEquals("Select a role", roleUpdateBlockReason(targetUserId = 5L, actingUserId = 1L, selectedRoleId = null, currentRoleId = 3))
    }

    @Test
    fun gapCheck_aSuperAdminCannotChangeTheirOwnRole() {
        val reason = roleUpdateBlockReason(targetUserId = 1L, actingUserId = 1L, selectedRoleId = 3, currentRoleId = 1)
        assertEquals("You cannot change your own role", reason)
    }

    @Test
    fun selectingTheSameRoleAlreadyHeldIsBlocked() {
        val reason = roleUpdateBlockReason(targetUserId = 5L, actingUserId = 1L, selectedRoleId = 6, currentRoleId = 6)
        assertEquals("Pick a different role before saving", reason)
    }

    @Test
    fun aDifferentRoleForAnotherUserIsAllowed() {
        assertNull(roleUpdateBlockReason(targetUserId = 5L, actingUserId = 1L, selectedRoleId = 3, currentRoleId = 6))
    }

    @Test
    fun anUnknownActingAdminIdDoesNotFalselyBlock() {
        // Defensive case — actingUserId can be null if CurrentUser hasn't been populated yet;
        // must never be treated as "editing self" (null == targetUserId is never true for a Long).
        assertNull(roleUpdateBlockReason(targetUserId = 5L, actingUserId = null, selectedRoleId = 3, currentRoleId = 6))
    }

    @Test
    fun aBrandNewUserWithNoCurrentRoleYetIsNotBlockedBySameRoleCheck() {
        // currentRoleId can be null before the detail load resolves — must not be mistaken for
        // "same role" against a null-vs-null comparison.
        assertNull(roleUpdateBlockReason(targetUserId = 5L, actingUserId = 1L, selectedRoleId = 3, currentRoleId = null))
    }

    // ---------------- userRoleLabel ----------------

    @Test
    fun mapsEveryKnownRoleToItsDisplayLabel() {
        assertEquals("Super Admin", userRoleLabel("SUPER_ADMIN"))
        assertEquals("Tournament Admin", userRoleLabel("TOURNAMENT_ADMIN"))
        assertEquals("Scorer", userRoleLabel("SCORER"))
        assertEquals("Team Manager", userRoleLabel("TEAM_MANAGER"))
        assertEquals("Umpire", userRoleLabel("UMPIRE"))
        assertEquals("Viewer", userRoleLabel("VIEWER"))
    }

    @Test
    fun gapCheck_anUnrecognizedRoleNameFallsBackToItselfRatherThanDisappearing() {
        assertEquals("SOMETHING_NEW", userRoleLabel("SOMETHING_NEW"))
    }

    // ---------------- roleColorFor ----------------

    @Test
    fun everyKnownRoleGetsADistinctColorFromTheSharedPalette() {
        val colors = listOf("SUPER_ADMIN", "TOURNAMENT_ADMIN", "SCORER", "TEAM_MANAGER", "UMPIRE").map { roleColorFor(it) }
        assertEquals(5, colors.toSet().size)
    }

    @Test
    fun anUnrecognizedRoleFallsBackToTheMutedTextColorRatherThanCrashing() {
        // Just asserts this doesn't throw and returns *a* color — VIEWER and any future role
        // both fall into this branch deliberately (see RoleBadge, which renders no badge at all
        // for VIEWER — this screen still needs to show *something* in the list row).
        roleColorFor("VIEWER")
        roleColorFor("SOMETHING_NEW")
    }
}
