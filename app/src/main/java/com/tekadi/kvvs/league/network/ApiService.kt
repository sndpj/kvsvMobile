package com.tekadi.kvvs.league.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class LoginResponse(
    val accessToken: String, val refreshToken: String, val role: String, val fullName: String,
    // GAP FIX: the app previously had no way to know its own logged-in user's id at all.
    val userId: Long,
)
@Serializable data class RegisterRequest(val fullName: String, val email: String, val phone: String?, val password: String)

// KvsvRequest1.2 #9 — trade a still-valid refresh token for a new access/refresh pair without
// asking the user to log in again. See RetrofitClient.kt's Authenticator for where this is used.
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class RefreshResponse(val accessToken: String, val refreshToken: String)

@Serializable data class TossRequest(val winnerTeamId: Long, val decision: String) // "BAT" | "BOWL"

@Serializable data class StartInningsRequest(
    val battingTeamId: Long, val strikerId: Long, val nonStrikerId: Long, val bowlerId: Long,
)

@Serializable data class BallRequest(
    val type: String,              // RUN | WIDE | NOBALL | BYE | LEGBYE | WICKET
    val runs: Int = 0,
    val dismissalType: String? = null,
    val dismissedPlayerId: Long? = null,
    val clientBallUuid: String,    // idempotency key so a retried sync never double-applies a ball
)

@Serializable data class NextBowlerRequest(val bowlerId: Long)

// ---- Super Admin: user list/search, role assign/revoke ----
// Mirrors com.tekadi.kvsv.league.dto.UserAdminDtos on the backend.
@Serializable data class UserSummaryResponse(
    val id: Long, val fullName: String, val roleName: String, val phone: String?,
    val email: String, val registeredAt: String?, val active: Boolean,
)
@Serializable data class UserDetailResponse(
    val id: Long, val fullName: String, val email: String, val phone: String?,
    val roleId: Int, val roleName: String, val registeredAt: String?, val active: Boolean,
    val emailVerified: Boolean, val phoneVerified: Boolean,
)
@Serializable data class UpdateUserRoleRequest(val roleId: Int)
@Serializable data class RoleOptionResponse(val id: Int, val name: String, val description: String?)

/**
 * Mirrors the endpoints actually exposed by the Spring Boot backend
 * (AuthController, TournamentController, TeamController, PlayerController,
 * MatchController, ScoringController). Base URL is configured in
 * RetrofitClient / BuildConfig.API_BASE_URL.
 *
 * NOTE: every scoring action below returns the full MatchScorecardDto, not an
 * empty body — an earlier version of this file typed them as Response<Unit>,
 * which would have failed to deserialize the real response. Fixed here.
 */
interface ApiService {

    // ---- Auth (Module 1) ----
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<Unit>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>

    // ---- Read-only browsing (public endpoints — no role required) ----
    @GET("api/tournaments")
    suspend fun getTournaments(): Response<List<TournamentResponse>>

    // Design-brief "Standings" screen.
    @GET("api/tournaments/{tournamentId}/standings")
    suspend fun getStandings(@Path("tournamentId") tournamentId: Long): Response<List<TeamStandingRow>>

    // Feature request: "put image picker instead of Image Url in all over application." Any
    // authenticated user may upload — see UploadController's dilemma note (local disk storage,
    // not production-durable) on the backend side.
    @retrofit2.http.Multipart
    @POST("api/uploads")
    suspend fun uploadImage(@retrofit2.http.Part file: okhttp3.MultipartBody.Part): Response<UploadResponse>

    @GET("api/tournaments/{tournamentId}/teams")
    suspend fun getTeams(@Path("tournamentId") tournamentId: Long): Response<List<TeamResponse>>

    @GET("api/teams/{teamId}/players")
    suspend fun getPlayers(@Path("teamId") teamId: Long): Response<List<PlayerResponse>>

    // ---- Team management (Module 3 — TEAM_MANAGER/TOURNAMENT_ADMIN/SUPER_ADMIN) ----
    @POST("api/teams")
    suspend fun createTeam(@Body body: CreateTeamRequest): Response<TeamResponse>

    @PUT("api/teams/{id}")
    suspend fun updateTeam(@Path("id") id: Long, @Body body: CreateTeamRequest): Response<TeamResponse>

    @retrofit2.http.DELETE("api/teams/{id}")
    suspend fun deleteTeam(@Path("id") id: Long): Response<Unit>

    /** "Invite Players": generates a shareable code (no email/SMS provider in this stack). */
    @POST("api/teams/{id}/invite")
    suspend fun createInvite(@Path("id") id: Long): Response<InviteResponse>

    /** Any authenticated user — the invitee redeems the code themselves. */
    @POST("api/teams/join")
    suspend fun joinTeam(@Body body: JoinTeamRequest): Response<PlayerResponse>

    // ---- Player management (Module 4 — TEAM_MANAGER/TOURNAMENT_ADMIN/SUPER_ADMIN) ----
    @POST("api/players")
    suspend fun createPlayer(@Body body: CreatePlayerRequest): Response<PlayerResponse>

    @PUT("api/players/{id}")
    suspend fun updatePlayer(@Path("id") id: Long, @Body body: CreatePlayerRequest): Response<PlayerResponse>

    @retrofit2.http.DELETE("api/players/{id}")
    suspend fun deletePlayer(@Path("id") id: Long): Response<Unit>

    @GET("api/players/{id}/stats")
    suspend fun getPlayerStats(@Path("id") id: Long): Response<PlayerStatsResponse>

    @GET("api/matches")
    suspend fun getMatches(@Query("tournamentId") tournamentId: Long): Response<List<MatchResponse>>

    @GET("api/matches/{matchId}")
    suspend fun getMatch(@Path("matchId") matchId: Long): Response<MatchResponse>

    @GET("api/matches/{matchId}/scorecard")
    suspend fun getScorecard(@Path("matchId") matchId: Long): Response<MatchScorecardDto>

    // ---- Match setup (TOURNAMENT_ADMIN/SUPER_ADMIN for creation; SCORER+ for the rest) ----
    @POST("api/matches")
    suspend fun createMatch(@Body body: CreateMatchRequest): Response<MatchResponse>

    @POST("api/matches/{matchId}/toss")
    suspend fun submitToss(@Path("matchId") matchId: Long, @Body body: TossRequest): Response<MatchResponse>

    // Feature request #1 (updated): scorer assignment is Super Admin, or a Team Manager acting
    // on a match involving a team they manage (see OnlineScorerViewModel.canAssignScorerDirectly).
    @PATCH("api/matches/{matchId}/scorer")
    suspend fun assignScorer(@Path("matchId") matchId: Long, @Body body: AssignScorerRequest): Response<MatchResponse>

    // Feature request #3: "ask scorer to close the match and generate summary after that."
    @POST("api/matches/{matchId}/close")
    suspend fun closeMatch(@Path("matchId") matchId: Long): Response<MatchResponse>

    // ---- Scorer requests (feature request #2) ----
    @POST("api/matches/{matchId}/scorer-requests")
    suspend fun submitScorerRequest(@Path("matchId") matchId: Long, @Body body: SubmitScorerRequestRequest? = null): Response<ScorerRequestResponse>

    @GET("api/matches/{matchId}/scorer-requests")
    suspend fun getScorerRequests(@Path("matchId") matchId: Long): Response<List<ScorerRequestResponse>>

    @POST("api/matches/{matchId}/scorer-requests/{requestId}/accept")
    suspend fun acceptScorerRequest(@Path("matchId") matchId: Long, @Path("requestId") requestId: Long): Response<ScorerRequestResponse>

    @POST("api/matches/{matchId}/scorer-requests/{requestId}/reject")
    suspend fun rejectScorerRequest(@Path("matchId") matchId: Long, @Path("requestId") requestId: Long, @Body body: RejectScorerRequestRequest? = null): Response<ScorerRequestResponse>

    // ---- Live scoring (SCORER/TOURNAMENT_ADMIN/SUPER_ADMIN) ----
    @POST("api/matches/{matchId}/innings/start")
    suspend fun startInnings(@Path("matchId") matchId: Long, @Body body: StartInningsRequest): Response<MatchScorecardDto>

    @POST("api/matches/{matchId}/ball")
    suspend fun submitBall(@Path("matchId") matchId: Long, @Body body: BallRequest): Response<MatchScorecardDto>

    @POST("api/matches/{matchId}/ball/undo")
    suspend fun undoBall(@Path("matchId") matchId: Long): Response<MatchScorecardDto>

    @PATCH("api/matches/{matchId}/bowler")
    suspend fun setNextBowler(@Path("matchId") matchId: Long, @Body body: NextBowlerRequest): Response<MatchScorecardDto>

    @PATCH("api/matches/{matchId}/batter/{playerId}")
    suspend fun setNextBatter(@Path("matchId") matchId: Long, @Path("playerId") playerId: Long): Response<MatchScorecardDto>

    @PATCH("api/matches/{matchId}/banner")
    suspend fun setBanner(@Path("matchId") matchId: Long, @Body body: SetBannerRequest): Response<MatchResponse>

    // ---- Dashboard (public reads) ----
    @GET("api/dashboard/recent-matches")
    suspend fun getRecentMatches(@Query("limit") limit: Int = 5): Response<List<RecentMatchSummary>>

    // Feature request: Direction C's home-screen live band. 204 (no body) means nothing is live.
    @GET("api/dashboard/live-match")
    @Deprecated("use getLiveMatches() — see the backend's own @Deprecated note on liveMatch()")
    suspend fun getLiveMatch(): Response<LiveMatchPointer>

    // Feature request: "Live matches should be display in slider in dashboard screen right now
    // showing only one."
    @GET("api/dashboard/live-matches")
    suspend fun getLiveMatches(): Response<List<LiveMatchPointer>>

    @GET("api/dashboard/head-to-head")
    suspend fun getHeadToHead(@Query("teamAId") teamAId: Long, @Query("teamBId") teamBId: Long): Response<HeadToHeadResponse>

    @GET("api/dashboard/top-batsmen")
    suspend fun getTopBatsmen(@Query("limit") limit: Int = 5): Response<List<LeaderboardEntry>>

    @GET("api/dashboard/top-bowlers")
    suspend fun getTopBowlers(@Query("limit") limit: Int = 5): Response<List<LeaderboardEntry>>

    // ---- Feed (public read; write is Super Admin AND owner-checked server-side) ----
    @GET("api/feeds/recent")
    suspend fun getFeeds(@Query("limit") limit: Int = 5): Response<List<FeedResponse>>

    // Feature request: "clicked on feed it should redirect on detail page screen."
    @GET("api/feeds/{id}")
    suspend fun getFeed(@Path("id") id: Long): Response<FeedResponse>

    @POST("api/feeds")
    suspend fun postFeed(@Body body: CreateFeedRequest): Response<FeedResponse>

    // Feature request: "edit ... option applicable for feeds owner."
    @PUT("api/feeds/{id}")
    suspend fun updateFeed(@Path("id") id: Long, @Body body: UpdateFeedRequest): Response<FeedResponse>

    @retrofit2.http.DELETE("api/feeds/{id}")
    suspend fun deleteFeed(@Path("id") id: Long): Response<Unit>

    // ---- Master Player Pool ----
    // GET is public; POST/DELETE .../players is SUPER_ADMIN only; POST teams-split is
    // TEAM_MANAGER/SUPER_ADMIN only — all enforced server-side (SecurityConfig), the app
    // just hides the corresponding UI via CurrentUser's role checks so a role-mismatched
    // tap never has to round-trip to discover it's a 403.
    @GET("api/master-pool/players")
    suspend fun getMasterPool(): Response<PoolStatusResponse>

    @POST("api/master-pool/players")
    suspend fun addPoolPlayer(@Body body: AddPoolPlayerRequest): Response<PoolPlayerResponse>

    @retrofit2.http.DELETE("api/master-pool/players/{playerId}")
    suspend fun removePoolPlayer(@Path("playerId") playerId: Long): Response<Unit>

    @POST("api/master-pool/teams-split")
    suspend fun splitPoolIntoTeams(@Body body: SplitIntoTeamsRequest): Response<SplitIntoTeamsResponse>

    // ---- Player Registration ----
    // POST (submit) is public, no auth required; everything else is SUPER_ADMIN only.
    @POST("api/player-registrations")
    suspend fun submitRegistration(@Body body: SubmitRegistrationRequest): Response<RegistrationResponse>

    @GET("api/player-registrations")
    suspend fun getRegistrations(@Query("status") status: String? = null): Response<List<RegistrationResponse>>

    @POST("api/player-registrations/{id}/approve")
    suspend fun approveRegistration(@Path("id") id: Long): Response<RegistrationResponse>

    @POST("api/player-registrations/{id}/reject")
    suspend fun rejectRegistration(@Path("id") id: Long, @Body body: RejectRegistrationRequest): Response<RegistrationResponse>

    // ---- Super Admin: user list/search, role assign/revoke ----
    // All of /api/admin/** is SUPER_ADMIN only, enforced server-side (SecurityConfig) — the app
    // just hides the "Users" entry point for anyone else (CurrentUser.isSuperAdmin), same
    // pattern used for every other role-gated feature in this file.
    @GET("api/admin/users")
    suspend fun listUsers(@Query("query") query: String? = null): Response<List<UserSummaryResponse>>

    @GET("api/admin/users/{userId}")
    suspend fun getUserDetail(@Path("userId") userId: Long): Response<UserDetailResponse>

    // "update the role, and reset the token for that particular user so he should be login
    // again with new role" — one request does both, see the backend's UpdateUserRoleRequest.
    @PATCH("api/admin/users/{userId}/role")
    suspend fun updateUserRole(@Path("userId") userId: Long, @Body body: UpdateUserRoleRequest): Response<UserDetailResponse>

    @GET("api/admin/roles")
    suspend fun listRoles(): Response<List<RoleOptionResponse>>
}
