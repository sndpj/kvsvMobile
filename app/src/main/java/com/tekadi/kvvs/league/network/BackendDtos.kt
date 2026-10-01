package com.tekadi.kvvs.league.network

import kotlinx.serialization.Serializable

/*
 * These mirror the backend's actual DTO records field-for-field
 * (com.tekadi.kvvs.league.dto.*), not a guess at the shape — see
 * cricket-backend/src/main/java/com/tekadi/kvvs/league/dto/ if the two ever drift.
 * Dates are kept as plain ISO strings (Jackson's default serialization for
 * java.time types) rather than parsed, since the app only displays them.
 */

@Serializable
data class TournamentResponse(
    val id: Long, val name: String, val season: String? = null, val organizer: String? = null,
    val location: String? = null, val startDate: String? = null, val endDate: String? = null,
    val ballType: String, val matchFormat: String, val defaultOvers: Int, val status: String,
)

@Serializable
data class TeamResponse(
    val id: Long, val tournamentId: Long? = null, val name: String, val logoUrl: String? = null,
    val captainId: Long? = null, val captainName: String? = null,
    val viceCaptainId: Long? = null, val viceCaptainName: String? = null,
    val coachName: String? = null,
    val managerId: Long? = null, val managerName: String? = null, val contactNumber: String? = null,
)

@Serializable
// tournamentId, captainId, and viceCaptainId are all required (non-nullable) — mirrors the
// backend's @NotNull on CreateTeamRequest ("make tournament_id, captain, vice-captain compulsory").
data class CreateTeamRequest(
    val tournamentId: Long, val name: String, val logoUrl: String? = null,
    val captainId: Long, val viceCaptainId: Long, val coachName: String? = null, val managerId: Long? = null,
    val contactNumber: String? = null,
)

@Serializable
data class InviteResponse(val code: String, val teamId: Long, val teamName: String, val expiresAt: String)

@Serializable
data class JoinTeamRequest(
    val code: String, val playerName: String? = null, val jerseyNumber: Int? = null, val age: Int? = null,
    val role: String? = null, val battingStyle: String? = null, val bowlingStyle: String? = null,
)

@Serializable
data class PlayerResponse(
    val id: Long, val teamId: Long? = null, val name: String, val photoUrl: String? = null,
    val jerseyNumber: Int? = null, val age: Int? = null, val role: String? = null,
    val battingStyle: String? = null, val bowlingStyle: String? = null,
    val mobileNumber: String? = null, val email: String? = null,
    // Player profile (KvsvRequest1.3) — "little description about the player".
    val bio: String? = null,
)

@Serializable
data class CreatePlayerRequest(
    // Nullable since KvsvRequest1.3 #4: an edit (PUT /api/players/{id}) sends null to keep the
    // player on their current team. Creating a player still always sends one.
    val teamId: Long? = null, val name: String, val photoUrl: String? = null, val jerseyNumber: Int? = null,
    val age: Int? = null, val role: String? = null, val battingStyle: String? = null, val bowlingStyle: String? = null,
    // null = leave unchanged on an edit (the backend no longer wipes contact info a client didn't send).
    val mobileNumber: String? = null, val email: String? = null,
    val bio: String? = null,
)

// ---- Player profile (KvsvRequest1.3), mirrors PlayerProfileDtos on the backend ----

@Serializable
data class TeamAppearance(
    val teamId: Long, val teamName: String, val tournamentId: Long? = null, val tournamentName: String? = null,
    val matches: Int,
)

@Serializable
data class PlayerOfMatchAward(
    val matchId: Long, val matchDate: String? = null, val teamAName: String, val teamBName: String,
    val resultSummary: String? = null,
)

@Serializable
data class CareerBatting(
    val innings: Int, val notOuts: Int, val runs: Int, val ballsFaced: Int, val highestScore: Int,
    val fours: Int, val sixes: Int, val fifties: Int, val hundreds: Int,
    val average: Double? = null, val strikeRate: Double,
)

@Serializable
data class CareerBowling(
    val innings: Int, val ballsBowled: Int, val overs: String, val runsConceded: Int, val wickets: Int,
    val maidens: Int, val average: Double? = null, val economy: Double, val bestFigures: String? = null,
)

@Serializable
data class PlayerProfileResponse(
    // The person's canonical id — the Master Pool player when this was opened from a team-roster
    // copy of them. Edits go to this id.
    val playerId: Long, val name: String, val photoUrl: String? = null, val jerseyNumber: Int? = null,
    val age: Int? = null, val role: String? = null, val battingStyle: String? = null, val bowlingStyle: String? = null,
    val bio: String? = null,
    val matchesPlayed: Int, val teams: List<TeamAppearance> = emptyList(),
    val playerOfMatchCount: Int, val playerOfMatchAwards: List<PlayerOfMatchAward> = emptyList(),
    val batting: CareerBatting, val bowling: CareerBowling,
)

@Serializable
data class PlayerStatsResponse(
    val playerId: Long, val playerName: String, val matches: Int,
    val runs: Int, val ballsFaced: Int, val strikeRate: Double, val highestScore: Int,
    val wickets: Int, val ballsBowled: Int, val economy: Double,
)

@Serializable
data class MatchResponse(
    val id: Long, val tournamentId: Long, val matchDate: String? = null, val oversLimit: Int,
    val teamAId: Long, val teamAName: String, val teamBId: Long, val teamBName: String,
    val status: String, val tossWinnerTeamId: Long? = null, val tossWinnerTeamName: String? = null,
    val tossDecision: String? = null, val resultSummary: String? = null, val bannerImageUrl: String? = null,
    // GAP FIX: the assigned scorer was never exposed here at all — needed so the client can
    // tell "I hold the SCORER role" apart from "I am specifically assigned to THIS match".
    val scorerUserId: Long? = null, val scorerName: String? = null,
    // GAP FIX: needed so the client can tell "am I the manager of one of this match's two
    // teams" without a second round-trip to /api/teams/{id} — Team Managers can now assign a
    // scorer directly for their own matches. UI-gating only; the server re-checks for real.
    val teamAManagerId: Long? = null, val teamBManagerId: Long? = null,
    // KvsvRequest1.3 #1 — maxOversPerBowler is the effective cap, null when the restriction is off.
    val bowlerOverLimitEnabled: Boolean = true, val maxOversPerBowler: Int? = null,
    // KvsvRequest1.3 #7
    val playerOfMatchId: Long? = null, val playerOfMatchName: String? = null,
)

@Serializable
data class CreateMatchRequest(
    val tournamentId: Long, val groundId: Long? = null, val matchDate: String,
    val matchTime: String? = null, val oversLimit: Int, val teamAId: Long, val teamBId: Long,
    // KvsvRequest1.3 #1 — per-bowler over restriction chosen by the match creator. null
    // maxOversPerBowler = the default ceil(overs / 5).
    val bowlerOverLimitEnabled: Boolean = true, val maxOversPerBowler: Int? = null,
    // scorerUserId removed — "always assigned by Super Admin" is now its own explicit,
    // always-Super-Admin-gated action (assignScorer / accepting a scorer request), not a
    // side-channel through match creation. See AssignScorerRequest below.
)

// ---- Scorer assignment / request workflow (mirrors ScorerRequestDtos on the backend) ----

@Serializable
data class AssignScorerRequest(val userId: Long)

@Serializable
data class SubmitScorerRequestRequest(val note: String? = null)

@Serializable
data class RejectScorerRequestRequest(val note: String? = null)

@Serializable
data class ScorerRequestResponse(
    val id: Long, val matchId: Long, val requestedByUserId: Long, val requestedByName: String,
    val status: String, val note: String? = null, val createdAt: String,
    val resolvedAt: String? = null, val resolvedByName: String? = null,
)

// ---- Live scorecard, returned by every scoring action + GET .../scorecard ----

@Serializable
data class BattingLineDto(
    val playerId: Long, val name: String, val runs: Int, val balls: Int, val fours: Int, val sixes: Int,
    val strikeRate: Double, val out: Boolean, val howOut: String? = null, val onCrease: String? = null,
)

@Serializable
data class BowlingLineDto(
    val playerId: Long, val name: String, val overs: String, val runsConceded: Int,
    val wickets: Int, val maidens: Int, val economy: Double,
)

@Serializable
data class InningsScorecardDto(
    val inningsId: Long, val inningsNumber: Int, val battingTeamName: String, val bowlingTeamName: String,
    val target: Int? = null, val totalRuns: Int, val wickets: Int, val oversDisplay: String, val currentRunRate: Double,
    val runsNeeded: Int? = null, val ballsLeft: Int? = null, val requiredRunRate: Double? = null,
    val extrasWide: Int, val extrasNoball: Int, val extrasBye: Int, val extrasLegbye: Int, val extrasTotal: Int,
    val battingCard: List<BattingLineDto>, val bowlingCard: List<BowlingLineDto>,
    val thisOver: List<String>, val commentary: List<String>, val status: String,
    // The authoritative "is a bowler currently assigned" signal — null only while genuinely
    // awaiting selection after an over. See needsNewBowler() in OnlineScorerViewModel for why
    // this replaced an oversDisplay-based guess that couldn't tell "awaiting selection" apart
    // from "bowler already picked, first ball of the new over not yet bowled".
    val currentBowlerId: Long? = null,
)

@Serializable
data class MatchScorecardDto(
    val matchId: Long, val status: String, val resultSummary: String? = null,
    val innings: List<InningsScorecardDto>,
)

// ---- Dashboard & Feed (mirrors DashboardDtos / FeedDtos on the backend) ----

@Serializable
data class RecentMatchSummary(
    val matchId: Long, val teamAName: String, val teamBName: String,
    val resultSummary: String? = null, val bannerImageUrl: String? = null, val matchDate: String? = null,
)

@Serializable
data class LiveMatchPointer(val matchId: Long, val teamAName: String, val teamBName: String, val groundName: String? = null)

@Serializable
data class LeaderboardEntry(val playerId: Long, val playerName: String, val total: Int)

@Serializable
// Design-brief "Standings" screen. Mirrors StandingsDtos.TeamStandingRow on the backend —
// ranked by points then wins, not Net Run Rate yet; see the backend record's doc comment.
data class TeamStandingRow(
    val position: Int, val teamId: Long, val teamName: String,
    val played: Int, val won: Int, val lost: Int, val tied: Int, val points: Int,
)

@Serializable
data class UploadResponse(val url: String)

@Serializable
data class FeedResponse(
    val id: Long, val type: String, val message: String,
    val imageUrl: String? = null, val imageUrls: List<String> = emptyList(),
    val matchId: Long? = null, val matchSummary: String? = null,
    // GAP FIX: createdByUserId was never exposed before — needed to compare against
    // CurrentUser.userId for "add, edit and delete option applicable for feeds owner".
    val createdByUserId: Long? = null, val createdByName: String? = null, val createdAt: String,
    // KvsvRequest1.3 #7 — "show the name in match summary feeds in bold".
    val playerOfMatchId: Long? = null, val playerOfMatchName: String? = null,
)

// ---- Match close / Player of the Match admin actions (KvsvRequest1.3 #6, #7) ----

@Serializable
data class AdminCloseMatchRequest(val reason: String)

@Serializable
data class MatchCloseRequestResponse(
    val id: Long, val matchId: Long, val requestedByUserId: Long, val requestedByName: String,
    val reason: String, val status: String, val reviewNote: String? = null,
    val createdAt: String, val resolvedAt: String? = null, val resolvedByName: String? = null,
)

@Serializable
data class SetPlayerOfMatchRequest(val playerId: Long)

@Serializable
// Feature request: "for feeds user can select multiple images, slide show in details page."
data class UpdateFeedRequest(val message: String? = null, val imageUrls: List<String>? = null)

@Serializable
data class CreateFeedRequest(val message: String, val imageUrls: List<String> = emptyList(), val matchId: Long? = null)

@Serializable
data class SetBannerRequest(val bannerImageUrl: String)

// ---- Master Player Pool (mirrors MasterPoolDtos on the backend) ----

@Serializable
data class PoolPlayerResponse(
    val id: Long, val name: String, val photoUrl: String? = null, val jerseyNumber: Int? = null,
    val age: Int? = null, val role: String? = null, val battingStyle: String? = null, val bowlingStyle: String? = null,
    val mobileNumber: String? = null, val email: String? = null,
)

@Serializable
data class PoolStatusResponse(val playerCount: Int, val capacity: Int, val players: List<PoolPlayerResponse>)

@Serializable
// mobileNumber/email are required — mirrors the backend's @NotBlank/@Email.
data class AddPoolPlayerRequest(
    val name: String, val photoUrl: String? = null, val jerseyNumber: Int? = null, val age: Int? = null,
    val role: String? = null, val battingStyle: String? = null, val bowlingStyle: String? = null,
    val mobileNumber: String, val email: String,
)

@Serializable
// tournamentId and each team's captainId/viceCaptainId are now required — mirrors the
// backend's @NotNull ("make tournament_id, captain, vice-captain compulsory", applied to both
// team-creation paths in this API).
data class SplitIntoTeamsRequest(
    val tournamentId: Long,
    val teamAName: String, val teamAPlayerIds: List<Long>, val teamALogoUrl: String? = null,
    val teamACaptainId: Long, val teamAViceCaptainId: Long,
    val teamBName: String, val teamBPlayerIds: List<Long>, val teamBLogoUrl: String? = null,
    val teamBCaptainId: Long, val teamBViceCaptainId: Long,
)

@Serializable
data class SplitIntoTeamsResponse(
    val teamA: TeamResponse, val teamAPlayers: List<PoolPlayerResponse>,
    val teamB: TeamResponse, val teamBPlayers: List<PoolPlayerResponse>,
)

// ---- Player Registration (mirrors PlayerRegistrationDtos on the backend) ----
// Public self-registration (Name, Jersey Number, Age, Role) -> Super Admin approves into the
// Master Player Pool, or rejects. See RegistrationScreen.kt / MasterPoolScreen's review panel.

@Serializable
data class SubmitRegistrationRequest(
    val name: String, val jerseyNumber: Int? = null, val age: Int? = null, val role: String,
    val mobileNumber: String, val email: String,
)

@Serializable
data class RegistrationResponse(
    val id: Long, val name: String, val jerseyNumber: Int? = null, val age: Int? = null, val role: String,
    val mobileNumber: String? = null, val email: String? = null,
    val status: String, val reviewNote: String? = null, val resultingPlayerId: Long? = null,
    val createdAt: String, val reviewedAt: String? = null,
)

@Serializable
data class RejectRegistrationRequest(val note: String? = null)
