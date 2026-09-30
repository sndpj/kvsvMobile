package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.network.HeadToHeadResponse
import com.tekadi.kvvs.league.network.LeaderboardEntry
import com.tekadi.kvvs.league.network.LiveMatchPointer
import com.tekadi.kvvs.league.network.MatchScorecardDto
import com.tekadi.kvvs.league.network.InningsScorecardDto
import com.tekadi.kvvs.league.network.BattingLineDto
import com.tekadi.kvvs.league.network.BowlingLineDto
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog

@Composable
fun DashboardScreen(
    vm: DashboardViewModel = viewModel(),
    onFeedClick: (feed: FeedResponse) -> Unit,
    onBrowseMatches: () -> Unit,
    onManageTeams: () -> Unit,
    onManageMasterPool: () -> Unit,
    onViewStandings: () -> Unit,
    onJoinTeam: () -> Unit,
    onPostFeed: () -> Unit,
    onLiveMatchClick: (matchId: Long) -> Unit,
    onLogout: () -> Unit,
    // Feature: Super Admin role assign/revoke — "For only role SUPER_ADMIN add option in mobile
    // dashboard screen."
    onManageUsers: () -> Unit = {},
    // KvsvRequest1.2 #2 fix — flips true once when returning from Post Feed with a change to
    // pick up; see AppNav.kt's DASHBOARD composable for where this comes from.
    refreshFeedsSignal: Boolean = false,
) {
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(refreshFeedsSignal) { if (refreshFeedsSignal) vm.load() }

    Surface(color = TintedCanvasBg, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeaderRow(vm, onLogout) }

            item { LiveMatchSlider(vm.liveMatches, vm.liveScorecards, onLiveMatchClick) }

            item { RoleActionRow(onBrowseMatches, onManageTeams, onManageMasterPool, onViewStandings, onJoinTeam, onPostFeed, onManageUsers) }

            // KvsvRequest1.1 #4 — a genuine load failure with nothing on screen yet gets a
            // Retry button, not just a static message; a partial failure alongside data that
            // did load (e.g. leaderboards failed but feeds didn't) stays a plain inline note
            // so it doesn't block the content that's already there.
            val nothingLoadedYet = vm.feeds.isEmpty() && vm.liveMatches.isEmpty()
            if (vm.error != null && nothingLoadedYet) {
                item { RetryPanel(message = vm.error!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.load() }) }
            } else if (vm.error != null) {
                // A partial failure (e.g. leaderboards failed but feeds didn't) doesn't need a
                // full-screen retry state — the dialog surfaces it once without blocking the
                // content that did load, and clearing vm.error here is safe precisely because
                // this branch is never the one keeping RetryPanel above alive.
                item { ErrorDialog(vm.error) { vm.error = null } }
            }
            if (vm.loading && vm.feeds.isEmpty()) {
                item { PageLoader(color = ArcTeal) }
            }

            item { SectionHeader("Latest Feed") }
            item { FeedCarousel(vm.feeds, onFeedClick) }

            item { SectionHeader("Top Performers") }
            item { LeaderboardRow(vm.topBatsmen, vm.topBowlers) }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HeaderRow(vm: DashboardViewModel, onLogout: () -> Unit) {
    // Direction C's header is plain text on the tinted canvas — no colored block, no big
    // display-face title. Swapped the old "CRICKET COMMAND" hero title (a leftover from before
    // the Tekadi rebrand) for the app name plus the role badge, matching the brief's markup.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Tekadi Cricket", color = TekadiGreen, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(CurrentUser.fullName ?: "", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                if (CurrentUser.roleLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    RoleBadge()
                }
            }
        }
        TextButton(onClick = onLogout) { Text("Log out", color = TextMuted) }
    }
    Spacer(Modifier.height(10.dp))
    vm.headToHead?.let { HeadToHeadWidget(it) }
}

/**
 * Feature request: Direction C's home-screen live band — a full-bleed-styled card (flat, no
 * shadow, hairline top/bottom borders rather than a floating rounded card) showing whatever
 * match is genuinely live right now. Shows a quiet "nothing live" state rather than hiding
 * itself entirely — Direction C's brief describes this as always-present real estate.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveMatchSlider(
    pointers: List<LiveMatchPointer>, scorecards: Map<Long, MatchScorecardDto>,
    onLiveMatchClick: (matchId: Long) -> Unit,
) {
    if (pointers.isEmpty()) {
        Surface(color = CardSurface, modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = TintedHairline)) {
            Column(Modifier.padding(16.dp)) {
                Text("No match live right now", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    // Feature request: "Live matches should be display in slider in dashboard screen right now
    // showing only one." Was a single fixed band; now a swipeable HorizontalPager, one page per
    // live match, each independently clickable to its own scoring screen — not just whichever
    // match happened to load first.
    val pagerState = rememberPagerState(pageCount = { pointers.size })
    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val pointer = pointers[page]
            LiveMatchCard(pointer, scorecards[pointer.matchId], onLiveMatchClick)
        }
        if (pointers.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(pointers.size) { idx ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (idx == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (idx == pagerState.currentPage) TekadiGreen else TintedHairline),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveMatchCard(pointer: LiveMatchPointer, scorecard: MatchScorecardDto?, onLiveMatchClick: (matchId: Long) -> Unit) {
    Surface(
        color = CardSurface,
        // GAP FIX ("after click on Live match should be redirect dashboard to live scoring
        // screen"): this band displayed the live score but had no click handler at all —
        // tapping it did nothing. Reuses the same live-scoring screen the match list already
        // opens (OnlineLiveScorerScreen), which itself already handles the viewer-vs-scorer
        // split, so nothing new needed there. Each slider page routes to its OWN matchId, not a
        // single shared one.
        modifier = Modifier.fillMaxWidth()
            .border(width = 1.dp, color = TintedHairline)
            .clickable { onLiveMatchClick(pointer.matchId) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(WicketRed, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "LIVE" + (pointer.groundName?.let { " AT ${it.uppercase()}" } ?: ""),
                    color = WicketRed, style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(10.dp))
            val inn = scorecard?.innings?.lastOrNull()
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(inn?.battingTeamName ?: pointer.teamAName, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (inn != null) "${inn.totalRuns}/${inn.wickets}" else "${pointer.teamAName} vs ${pointer.teamBName}",
                        color = TekadiGreen, style = MaterialTheme.typography.displayLarge,
                    )
                }
                if (inn != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${inn.oversDisplay} ov", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text("RR ${"%.2f".format(inn.currentRunRate)}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Feature request: "Right now showing only score please add current batsman names
            // with score and striker and bowler name with over status." onCrease/currentBowlerId
            // already existed on the scorecard DTOs (added for the next-bowler bug fix a few
            // turns ago) — this is the first thing on the Dashboard to actually render them.
            if (inn != null) {
                val onCreaseBatsmen = onCreaseBattingLines(inn)
                if (onCreaseBatsmen.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        onCreaseBatsmen.forEach { b ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    (if (b.onCrease == "STRIKER") "▸ " else "") + b.name,
                                    color = if (b.onCrease == "STRIKER") TextPrimary else TextMuted,
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                )
                                Text("${b.runs} (${b.balls})", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                val bowler = currentBowlerLine(inn)
                if (bowler != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bowler: ${bowler.name} — ${bowler.overs}-${bowler.maidens}-${bowler.runsConceded}-${bowler.wickets}",
                        color = TextMuted, style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (inn != null && inn.thisOver.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    inn.thisOver.takeLast(6).forEach { ball ->
                        Box(
                            Modifier.weight(1f).height(4.dp).background(
                                if (ball.contains("W")) WicketRed else if (ball == "0") TintedHairline else TekadiGreen,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** "Battle count" — top-right widget on the dashboard, per the requested feature. */
@Composable
private fun HeadToHeadWidget(h2h: HeadToHeadResponse) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Surface(
            shape = heroPanelShape(10.dp), color = GraphitePanel,
            modifier = Modifier.border(glowBorder(), heroPanelShape(10.dp)).widthIn(max = 220.dp),
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HEAD TO HEAD", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(h2h.teamAWins.toString(), color = ArcTealBright, style = MaterialTheme.typography.titleLarge)
                    Text(h2h.teamAName.take(3).uppercase(), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text("–", color = TextMuted)
                    Text(h2h.teamBName.take(3).uppercase(), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(h2h.teamBWins.toString(), color = ArcTealBright, style = MaterialTheme.typography.titleLarge)
                }
                if (h2h.ties > 0) {
                    Text("${h2h.ties} tied", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RoleActionRow(
    onBrowseMatches: () -> Unit, onManageTeams: () -> Unit, onManageMasterPool: () -> Unit,
    onViewStandings: () -> Unit, onJoinTeam: () -> Unit, onPostFeed: () -> Unit,
    onManageUsers: () -> Unit,
) {
    Column {
        Text("QUICK ACTIONS", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip("⚔ Matches", enabled = true, requiredRoleLabel = null, onClick = onBrowseMatches, modifier = Modifier.weight(1f))
            ActionChip("👥 Teams", enabled = CurrentUser.canManageTeams, requiredRoleLabel = "Team Manager+", onClick = onManageTeams, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Design-brief "Standings" screen — table position, points, etc. Public read, same
            // as match browsing, so no role gate here.
            ActionChip("🏆 Standings", enabled = true, requiredRoleLabel = null, onClick = onViewStandings, modifier = Modifier.weight(1f))
            // Any authenticated user can redeem an invite — this one is never role-gated,
            // deliberately consistent with the backend's POST /api/teams/join rule.
            ActionChip("🔗 Join Team", enabled = true, requiredRoleLabel = null, onClick = onJoinTeam, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // KvsvRequest1.2 #6 — Feeds are managed exclusively from the Post Feed screen now
            // (browse/edit/delete all live there too); no separate "Manage Feeds" entry here.
            ActionChip("📣 Post Feed", enabled = CurrentUser.isSuperAdmin, requiredRoleLabel = "Super Admin", onClick = onPostFeed, modifier = Modifier.weight(1f))
            // Visible to Team Manager and Super Admin — the only two roles that can do
            // anything on this screen (Super Admin add/remove, both create-teams-from-pool).
            ActionChip(
                "🎯 Player Pool", enabled = CurrentUser.canManageMasterPool || CurrentUser.canSplitPoolIntoTeams,
                requiredRoleLabel = "Team Manager+", onClick = onManageMasterPool, modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Feature: Super Admin role assign/revoke — "For only role SUPER_ADMIN add option
            // in mobile dashboard screen ... users list with search option by name or mobile
            // number." Mirrors the backend's /api/admin/** -> hasRole("SUPER_ADMIN") rule
            // exactly, same "always visible, enabled only for the right role" pattern as every
            // other chip in this row.
            ActionChip("🛡 Users", enabled = CurrentUser.isSuperAdmin, requiredRoleLabel = "Super Admin", onClick = onManageUsers, modifier = Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * "Enable/disable features based on role": every action is always visible so the
 * full feature set is discoverable, but tapping a role-gated one you don't have
 * access to does nothing except show which role unlocks it — rather than hiding
 * it entirely, which would make the app feel like it's missing features instead
 * of just currently locking them.
 */
@Composable
private fun ActionChip(label: String, enabled: Boolean, requiredRoleLabel: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (enabled) {
            Button(onClick = onClick, shape = heroBadgeShape(), modifier = Modifier.fillMaxWidth()) { Text(label) }
        } else {
            OutlinedButton(
                onClick = {}, enabled = false, shape = heroBadgeShape(), modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = TextDisabled),
            ) { Text(label) }
        }
        if (!enabled && requiredRoleLabel != null) {
            Text("Requires $requiredRoleLabel", color = TextDisabled, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(16.dp).background(ArcTeal))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), color = TextPrimary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun FeedCarousel(feeds: List<FeedResponse>, onFeedClick: (FeedResponse) -> Unit) {
    if (feeds.isEmpty()) {
        Text("No feed items yet.", color = TextMuted)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(feeds) { feed -> FeedCard(feed, onFeedClick) }
    }
}

@Composable
private fun FeedCard(feed: FeedResponse, onFeedClick: (FeedResponse) -> Unit) {
    Column(modifier = androidx.compose.ui.Modifier.width(260.dp)) {
        // GAP FIX ("redirect to Match summary page and news feed should be feed details page"):
        // a MATCH_SUMMARY card now opens the match's own summary/scorecard screen directly,
        // rather than the generic feed-detail page every card used to open regardless of type.
        // A plain ANNOUNCEMENT card still opens the generic feed-detail page (with edit/delete
        // for its owner — see FeedDetailScreen).
        Surface(
            shape = heroPanelShape(), color = GraphitePanel, onClick = { onFeedClick(feed) },
            modifier = Modifier.width(260.dp).height(150.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (feed.imageUrl != null) {
                    AsyncImage(
                        model = feed.imageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(GraphitePanelAlt))
                }
                Box(Modifier.fillMaxSize().background(bannerScrim))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
                    AssistChip(
                        onClick = {}, modifier = Modifier.height(22.dp),
                        label = { Text(if (feed.type == "MATCH_SUMMARY") "MATCH" else "NEWS", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = AccentOrange, labelColor = TextOnDark,
                        ),
                        border = null,
                    )
                    Spacer(Modifier.height(6.dp))
                    // On the dark scrim over a photo, not a card — needs the light text token, not TextPrimary.
                    Text(feed.message, color = TextOnDark, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
        TextButton(onClick = { onFeedClick(feed) }, contentPadding = PaddingValues(top = 4.dp, start = 4.dp)) {
            Text(if (feed.type == "MATCH_SUMMARY") "View match summary →" else "View details →", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LeaderboardRow(batsmen: List<LeaderboardEntry>, bowlers: List<LeaderboardEntry>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LeaderboardPanel("Top Batsmen", "runs", batsmen, Modifier.weight(1f))
        LeaderboardPanel("Top Bowlers", "wkts", bowlers, Modifier.weight(1f))
    }
}

@Composable
private fun LeaderboardPanel(title: String, unit: String, entries: List<LeaderboardEntry>, modifier: Modifier) {
    Surface(shape = heroPanelShape(), color = GraphitePanel, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title.uppercase(), color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text("No data yet", color = TextDisabled, style = MaterialTheme.typography.labelSmall)
            }
            entries.forEachIndexed { idx, e ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(20.dp).clip(heroBadgeShape(4.dp))
                                .background(if (idx == 0) RepulsorGold else HudLine),
                            contentAlignment = Alignment.Center,
                        ) {
                            // TrophyGold background needs dark text, not the (now light) VoidBlack alias — see Color.kt's note.
                            Text("${idx + 1}", color = if (idx == 0) TextPrimary else TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(e.playerName, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    Text("${e.total} $unit", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Feature request: "add current batsman names with score and striker and bowler name." Pulled
 * out as plain functions (not inlined into LiveMatchCard) specifically so they're unit-testable
 * — see LiveMatchLogicTest.kt. This project has no Compose UI-testing infrastructure set up, so
 * the composable rendering itself isn't covered by an executable test; these are the pieces of
 * genuine logic inside it that can be.
 */
fun onCreaseBattingLines(inn: InningsScorecardDto): List<BattingLineDto> = inn.battingCard.filter { it.onCrease != null }

fun currentBowlerLine(inn: InningsScorecardDto): BowlingLineDto? = inn.bowlingCard.find { it.playerId == inn.currentBowlerId }
