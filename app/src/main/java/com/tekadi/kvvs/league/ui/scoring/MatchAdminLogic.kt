package com.tekadi.kvvs.league.ui.scoring

import com.tekadi.kvvs.league.network.PlayerResponse

/**
 * KvsvRequest1.3 #6 (close a match any time with a reason) and #7 (override Player of the
 * Match) — the decisions behind those buttons, kept Android-free so they're unit-tested (see
 * MatchAdminLogicTest). The backend re-checks every one of these.
 */

/** Same set as the backend's MatchCloseRequestService.CLOSABLE_MATCH_STATUSES. */
val CLOSABLE_MATCH_STATUSES = setOf("SCHEDULED", "LIVE", "INNINGS_COMPLETE")

/** match_close_request.reason is VARCHAR(300) on the backend. */
const val CLOSE_REASON_MAX = 300

fun showCloseMatchAction(canCloseDirectly: Boolean, matchStatus: String?): Boolean =
    canCloseDirectly && matchStatus in CLOSABLE_MATCH_STATUSES

/** null when the reason is fine to submit. */
fun closeReasonError(reason: String): String? = when {
    reason.isBlank() -> "Enter a reason for closing the match"
    reason.trim().length > CLOSE_REASON_MAX -> "Keep the reason to $CLOSE_REASON_MAX characters or fewer"
    else -> null
}

/**
 * A match is over (show the result screen) once it's completed or cancelled. The scorecard's own
 * status is checked too: a live push/poll can report CANCELLED before the match record is reloaded.
 */
fun isMatchOver(matchStatus: String?, scorecardStatus: String?): Boolean =
    matchStatus == "COMPLETED" || matchStatus == "CANCELLED" || scorecardStatus == "CANCELLED"

fun showPlayerOfMatchOverride(canSetPlayerOfMatch: Boolean, matchStatus: String?): Boolean =
    canSetPlayerOfMatch && matchStatus == "COMPLETED"

/** Both squads, one list, alphabetical — the choices for a Player of the Match override. */
fun playerOfMatchCandidates(teamA: List<PlayerResponse>, teamB: List<PlayerResponse>): List<PlayerResponse> =
    (teamA + teamB).distinctBy { it.id }.sortedBy { it.name.lowercase() }
