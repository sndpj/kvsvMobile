package com.tekadi.kvvs.league.data

object CurrentUser {
    var role: String? = null       // SUPER_ADMIN | TOURNAMENT_ADMIN | SCORER | TEAM_MANAGER | UMPIRE | VIEWER
    var fullName: String? = null
    // GAP FIX: the app previously had no way to know its own logged-in user's id at all —
    // needed so the client can tell "am I the specific scorer assigned to this match" apart
    // from merely "I hold the SCORER role". See OnlineScorerViewModel.isAssignedScorer.
    var userId: Long? = null

    fun clear() { role = null; fullName = null; userId = null }

    val isSuperAdmin get() = role == "SUPER_ADMIN"
    val canManageTournaments get() = role == "SUPER_ADMIN" || role == "TOURNAMENT_ADMIN"
    // Feature request: "disable new match creation option for viewer" — mirrors the backend's
    // actual POST /api/matches rule exactly (TOURNAMENT_ADMIN or SUPER_ADMIN), so the button
    // simply isn't there for anyone who'd get a 403 anyway, rather than showing it and letting
    // them find out by tapping.
    val canCreateMatch get() = role == "SUPER_ADMIN" || role == "TOURNAMENT_ADMIN"
    val canScore get() = role == "SUPER_ADMIN" || role == "TOURNAMENT_ADMIN" || role == "SCORER"
    val canManageTeams get() = role == "SUPER_ADMIN" || role == "TOURNAMENT_ADMIN" || role == "TEAM_MANAGER"
    // Master Player Pool: adding/removing pool players is Super Admin only (narrower than
    // canManageTeams above); splitting the pool into 2 teams is Team Manager + Super Admin
    // (narrower than canManageTeams too — Tournament Admin can create ordinary teams via
    // POST /api/teams, but is deliberately not allowed to draw from the master pool).
    val canManageMasterPool get() = role == "SUPER_ADMIN"
    val canSplitPoolIntoTeams get() = role == "SUPER_ADMIN" || role == "TEAM_MANAGER"

    // Feature request: role-based theme, resolved as an accent/badge layer (see RoleBadge.kt for
    // the actual composable — color mapping lives in ui.theme.Color.kt, kept out of this data
    // class to avoid a Compose dependency here). null means "no badge" — deliberately the case
    // for VIEWER, since it's the unmarked default state everyone starts from.
    val roleLabel: String?
        get() = when (role) {
            "SUPER_ADMIN" -> "Super Admin"
            "TOURNAMENT_ADMIN" -> "Tournament Admin"
            "SCORER" -> "Scorer"
            "TEAM_MANAGER" -> "Team Manager"
            "UMPIRE" -> "Umpire"
            else -> null
        }
}
