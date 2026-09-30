package com.tekadi.kvvs.league.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import com.tekadi.kvvs.league.ui.auth.LoginScreen
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.network.SessionHolder
import com.tekadi.kvvs.league.ui.admin.UserEditScreen
import com.tekadi.kvvs.league.ui.admin.UserListScreen
import com.tekadi.kvvs.league.ui.dashboard.DashboardScreen
import com.tekadi.kvvs.league.ui.dashboard.FeedDetailScreen
import com.tekadi.kvvs.league.ui.dashboard.FeedPostScreen
import com.tekadi.kvvs.league.ui.dashboard.HistoryDetailScreen
import com.tekadi.kvvs.league.ui.match.MatchBrowserScreen
import com.tekadi.kvvs.league.ui.pool.MasterPoolScreen
import com.tekadi.kvvs.league.ui.registration.RegistrationScreen
import com.tekadi.kvvs.league.ui.scoring.LiveScorerScreen
import com.tekadi.kvvs.league.ui.scoring.OnlineLiveScorerScreen
import com.tekadi.kvvs.league.ui.standings.StandingsScreen
import com.tekadi.kvvs.league.ui.team.JoinTeamScreen
import com.tekadi.kvvs.league.ui.team.TeamManagementScreen
import kotlinx.coroutines.launch

private object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val MATCHES = "matches"
    const val SCORING = "scoring/{matchId}"
    const val HISTORY = "history/{matchId}"
    const val FEED_DETAIL = "feed/{feedId}"
    const val POST_FEED = "post_feed"
    const val TEAM_MANAGEMENT = "team_management"
    const val JOIN_TEAM = "join_team"
    const val MASTER_POOL = "master_pool"
    const val STANDINGS = "standings"
    const val REGISTRATION = "registration"
    const val OFFLINE_DEMO = "offline_demo"
    // Feature: Super Admin role assign/revoke.
    const val USERS = "users"
    const val USER_EDIT = "users/{userId}"
    fun scoring(matchId: Long) = "scoring/$matchId"
    fun history(matchId: Long) = "history/$matchId"
    fun feedDetail(feedId: Long) = "feed/$feedId"
    fun userEdit(userId: Long) = "users/$userId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // Feature: Super Admin role assign/revoke — "reset the token ... so he should be login
    // again with new role." When TokenAuthenticator gives up on refreshing (the stored refresh
    // token is dead — naturally expired, or an admin reset this user's token_version) it clears
    // the session and signals here; this collector runs for the whole app lifetime (AppNavHost
    // is the navigation root) so it reacts wherever the person happens to be, not just on
    // screens that separately check for a 401 themselves.
    LaunchedEffect(Unit) {
        SessionHolder.sessionInvalidated.collect {
            navController.navigate(Routes.LOGIN) { popUpTo(0) }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onTryOfflineDemo = { navController.navigate(Routes.OFFLINE_DEMO) },
                onRegisterAsPlayer = { navController.navigate(Routes.REGISTRATION) },
            )
        }

        // Public — reachable without logging in, straight from the login screen.
        composable(Routes.REGISTRATION) {
            RegistrationScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DASHBOARD) { backStackEntry ->
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            // KvsvRequest1.2 #2 fix: Dashboard stays alive on the back stack while Post Feed is
            // open, so its own LaunchedEffect(Unit) never re-runs on the way back — a newly
            // posted/edited/deleted feed used to only show up after a full logout/login. Post
            // Feed flips this flag on its own backStackEntry (the one before it, i.e. this one)
            // before popping; Dashboard reloads once when it sees it, then resets it so
            // navigating back again without a real change doesn't keep re-triggering a reload.
            val feedsChanged by backStackEntry.savedStateHandle
                .getStateFlow("feeds_changed", false).collectAsState()
            androidx.compose.runtime.LaunchedEffect(feedsChanged) {
                if (feedsChanged) backStackEntry.savedStateHandle["feeds_changed"] = false
            }
            DashboardScreen(
                 onFeedClick = { feed ->
                    // GAP FIX (#1): type-aware routing — a match-summary feed opens the match's
                    // own summary screen; everything else opens the generic feed-detail page.
                    if (feed.type == "MATCH_SUMMARY" && feed.matchId != null) navController.navigate(Routes.history(feed.matchId))
                    else navController.navigate(Routes.feedDetail(feed.id))
                },
                onBrowseMatches = { navController.navigate(Routes.MATCHES) },
                onManageTeams = { navController.navigate(Routes.TEAM_MANAGEMENT) },
                onManageMasterPool = { navController.navigate(Routes.MASTER_POOL) },
                onViewStandings = { navController.navigate(Routes.STANDINGS) },
                onJoinTeam = { navController.navigate(Routes.JOIN_TEAM) },
                onPostFeed = { navController.navigate(Routes.POST_FEED) },
                onManageUsers = { navController.navigate(Routes.USERS) },
                refreshFeedsSignal = feedsChanged,
				onLiveMatchClick = { matchId -> navController.navigate(Routes.scoring(matchId)) },
                onLogout = {
                    scope.launch {
                        com.tekadi.kvvs.league.data.TokenStore(context).clear()
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    }
                },
            )
        }

        composable(Routes.MATCHES) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            MatchBrowserScreen(
                // BUG FIX (review item #1 — "match summary... not showing also from completed
                // match list. It should be visible from both location"): this always opened the
                // live-scoring screen regardless of status; a COMPLETED (or CANCELLED — see
                // MatchCloseRequestService) match now opens the same history/summary screen the
                // Dashboard feed already correctly routes to, instead of the live scorer.
                onMatchSelected = { match ->
                    if (match.status == "COMPLETED" || match.status == "CANCELLED") navController.navigate(Routes.history(match.id))
                    else navController.navigate(Routes.scoring(match.id))
                },
                onLogout = {
                    scope.launch {
                        com.tekadi.kvvs.league.data.TokenStore(context).clear()
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    }
                },
            )
        }

        composable(
            Routes.SCORING,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: return@composable
            OnlineLiveScorerScreen(matchId = matchId, onExit = { navController.popBackStack(Routes.MATCHES, inclusive = false) })
        }

        composable(
            Routes.HISTORY,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: return@composable
            HistoryDetailScreen(matchId = matchId, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.FEED_DETAIL,
            arguments = listOf(navArgument("feedId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val feedId = backStackEntry.arguments?.getLong("feedId") ?: return@composable
            FeedDetailScreen(
                feedId = feedId,
                onBack = { navController.popBackStack() },
                onViewMatch = { matchId -> navController.navigate(Routes.history(matchId)) },
            )
        }

        composable(Routes.POST_FEED) {
            FeedPostScreen(
                onBack = {
                    // See the DASHBOARD composable above for why this flag exists.
                    navController.previousBackStackEntry?.savedStateHandle?.set("feeds_changed", true)
                    navController.popBackStack()
                },
            )
        }

        // Feature: Super Admin role assign/revoke — reachable only from the Dashboard's Users
        // quick action, which is itself only enabled for CurrentUser.isSuperAdmin; the backend
        // independently enforces the same restriction on every /api/admin/** call regardless.
        composable(Routes.USERS) {
            UserListScreen(
                onBack = { navController.popBackStack() },
                onEditUser = { userId -> navController.navigate(Routes.userEdit(userId)) },
            )
        }

        composable(
            Routes.USER_EDIT,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: return@composable
            UserEditScreen(userId = userId, onBack = { navController.popBackStack() })
        }

        composable(Routes.TEAM_MANAGEMENT) {
            TeamManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MASTER_POOL) {
            MasterPoolScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.STANDINGS) {
            StandingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.JOIN_TEAM) {
            JoinTeamScreen(
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        // Kept reachable for offline testing / demoing the scoring engine without a backend.
        // This is intentionally a step removed from the main flow (only reachable from the
        // login screen), since it's a demo mode, not a real feature alongside online scoring.
        composable(Routes.OFFLINE_DEMO) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Offline Demo") },
                        navigationIcon = {
                            // Plain text, not an Icons.* glyph — material-icons-core isn't a
                            // confirmed transitive dependency of material3 here and this project
                            // has no working Gradle build in this environment to verify it
                            // resolves, so this avoids that risk entirely.
                            TextButton(onClick = { navController.popBackStack() }) {
                                Text("‹ Back")
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Box(Modifier.padding(innerPadding)) {
                    LiveScorerScreen()
                }
            }
        }
    }
}
