package com.tekadi.kvvs.league.ui.admin

import androidx.compose.ui.graphics.Color
import com.tekadi.kvvs.league.ui.theme.RoleColorScorer
import com.tekadi.kvvs.league.ui.theme.RoleColorSuperAdmin
import com.tekadi.kvvs.league.ui.theme.RoleColorTeamManager
import com.tekadi.kvvs.league.ui.theme.RoleColorTournamentAdmin
import com.tekadi.kvvs.league.ui.theme.RoleColorUmpire
import com.tekadi.kvvs.league.ui.theme.TextMuted

/**
 * Feature: Super Admin role assign/revoke. Pulled out as plain functions (not inlined into the
 * ViewModels) specifically so they're unit-testable — this project has no Compose UI-testing
 * infrastructure set up, so the composable rendering itself isn't covered by an executable test;
 * these are the pieces of genuine logic behind it that can be (same pattern as
 * TeamManagementScreen.teamInitial / DashboardScreen.onCreaseBattingLines).
 */

/**
 * A blank/whitespace-only search box means "browse everyone" — sent to the backend as null
 * (UserRepository.searchUsers' contract on the backend side), not an empty string, and always
 * trimmed so " dravid " and "dravid" hit the same request/cache key.
 */
fun normalizeUserSearchQuery(raw: String): String? = raw.trim().ifBlank { null }

/**
 * Mirrors UserAdminService.updateRole's own checks on the backend (unknown-role aside, which is
 * a dropdown built from the same /api/admin/roles list and so can't happen here) so the person
 * gets an immediate inline message instead of a round-trip 400 — same "mirror the backend rule
 * client-side" pattern as TeamManagementViewModel.createTeam. Returns null when the change is
 * allowed, or the message to show otherwise.
 */
fun roleUpdateBlockReason(
    targetUserId: Long,
    actingUserId: Long?,
    selectedRoleId: Int?,
    currentRoleId: Int?,
): String? = when {
    selectedRoleId == null -> "Select a role"
    actingUserId != null && actingUserId == targetUserId -> "You cannot change your own role"
    currentRoleId != null && selectedRoleId == currentRoleId -> "Pick a different role before saving"
    else -> null
}

/**
 * Human-readable label for any role name string — same mapping RoleBadge uses for the
 * signed-in user's own role, generalized here since this screen shows OTHER users' roles too,
 * not just CurrentUser's. Falls back to the raw name for anything unrecognized rather than
 * hiding it, so a future role never silently disappears from the list.
 */
fun userRoleLabel(roleName: String): String = when (roleName) {
    "SUPER_ADMIN" -> "Super Admin"
    "TOURNAMENT_ADMIN" -> "Tournament Admin"
    "SCORER" -> "Scorer"
    "TEAM_MANAGER" -> "Team Manager"
    "UMPIRE" -> "Umpire"
    "VIEWER" -> "Viewer"
    else -> roleName
}

/** Same role -> color mapping as RoleBadge, generalized for any role name string. */
fun roleColorFor(roleName: String): Color = when (roleName) {
    "SUPER_ADMIN" -> RoleColorSuperAdmin
    "TOURNAMENT_ADMIN" -> RoleColorTournamentAdmin
    "SCORER" -> RoleColorScorer
    "TEAM_MANAGER" -> RoleColorTeamManager
    "UMPIRE" -> RoleColorUmpire
    else -> TextMuted
}
