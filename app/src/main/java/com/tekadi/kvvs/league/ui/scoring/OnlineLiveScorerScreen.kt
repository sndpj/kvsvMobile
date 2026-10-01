package com.tekadi.kvvs.league.ui.scoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.InningsScorecardDto
import com.tekadi.kvvs.league.network.MatchResponse
import com.tekadi.kvvs.league.network.PlayerResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast
import com.tekadi.kvvs.league.util.LiveEventToast
import com.tekadi.kvvs.league.ui.dashboard.PlayerOfMatchLine
import com.tekadi.kvvs.league.ui.dashboard.PlayerOfMatchPicker

@Composable
fun OnlineLiveScorerScreen(matchId: Long, vm: OnlineScorerViewModel = viewModel(), onExit: () -> Unit) {
    LaunchedEffect(matchId) { vm.loadMatch(matchId) }
    var showCloseDialog by remember { mutableStateOf(false) }
    if (showCloseDialog) {
        CloseMatchDialog(
            match = vm.match, submitting = vm.loading,
            onConfirm = { reason -> vm.adminCloseMatch(reason) { showCloseDialog = false } },
            onDismiss = { showCloseDialog = false },
        )
    }

    Surface(color = PitchBg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.padding(16.dp)) {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    LiveIndicator(vm.liveConnected, vm.polling)
                    // KvsvRequest1.3 #6 — "Open the match — show right side close match option".
                    if (vm.canCloseMatchNow) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showCloseDialog = true }, enabled = !vm.loading) {
                            Text("✕ Close match", color = WicketRed, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (vm.loading && vm.match == null) {
                    PageLoader(color = Amber)
                    return@Column
                }
                // KvsvRequest1.1 #4 — connection-timeout retry, right on the screen the bug
                // report (#6) was about: a failed initial load previously left this screen
                // blank (vm.match stays null, the `?: return@Column` below just exits) with
                // only the small error banner above and no way to try again without leaving.
                if (vm.error != null && vm.match == null) {
                    RetryPanel(message = vm.error!!, accentColor = Amber, textColor = TextOnDark, onRetry = { vm.loadMatch(matchId) })
                    return@Column
                }
                val m = vm.match ?: return@Column
                // Once a match is loaded, any further error (a failed ball submission, a
                // rejected undo, etc.) surfaces as a dialog instead of the old inline red-text
                // banner above — the match/scorecard the scorer already has stays fully visible
                // and interactive underneath it.
                ErrorDialog(vm.error) { vm.error = null }
                InfoToast(vm.info) { vm.info = null }
                // Big-moment callout — wicket/four/six — see OnlineScorerViewModel.applyScorecard.
                // Fires identically whether this device is the one scoring or just watching.
                LiveEventToast(vm.liveEvent) { vm.liveEvent = null }
                when (vm.screen) {
                    OnlineScreen.TOSS -> TossStep(m, vm)
                    OnlineScreen.OPENERS -> OpenersStep(m, vm)
                    OnlineScreen.LIVE -> LiveStep(vm)
                    OnlineScreen.AWAITING_CLOSE -> AwaitingCloseStep(vm)
                    OnlineScreen.RESULT -> ResultStep(m, vm, onExit)
                }
            }
        }
    }
}

/**
 * Small dot + label showing the live-update channel's state: connected (WebSocket push, "LIVE"),
 * disconnected but polling REST every few seconds as a fallback ("offline — polling"), or still
 * connecting for the first time. This is what makes an interrupted connection visible to a
 * viewer instead of a screen that silently goes stale with no indication anything's wrong.
 */
@Composable
private fun LiveIndicator(connected: Boolean, polling: Boolean) {
    val (color, label) = when {
        connected -> Amber to "LIVE"
        polling -> InfoBlue to "offline — polling"
        else -> Muted to "connecting…"
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun Label(text: String) = Text(text.uppercase(), color = Muted, style = MaterialTheme.typography.labelSmall)

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = PanelGreen, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun WaitingForScorer(what: String) {
    Panel {
        Text("Waiting for the scorer…", color = Amber)
        Spacer(Modifier.height(4.dp))
        Text("The match hasn't $what yet. This screen will update live once it does.", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TossStep(m: MatchResponse, vm: OnlineScorerViewModel) {
    ScorerAssignmentPanel(m, vm)
    Spacer(Modifier.height(12.dp))
    if (!vm.canScoreThisMatch) {
        Text("🪙 Toss — ${m.teamAName} vs ${m.teamBName}", color = Amber, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        WaitingForScorer(what = "started")
        return
    }
    var winnerId by remember { mutableStateOf<Long?>(null) }
    var decision by remember { mutableStateOf<String?>(null) }
    Text("🪙 Toss — ${m.teamAName} vs ${m.teamBName}", color = Amber, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Panel {
        Label("Who won the toss?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(selected = winnerId == m.teamAId, onClick = { winnerId = m.teamAId }, label = { Text(m.teamAName) })
            FilterChip(selected = winnerId == m.teamBId, onClick = { winnerId = m.teamBId }, label = { Text(m.teamBName) })
        }
        Spacer(Modifier.height(16.dp))
        Label("Elected to")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(selected = decision == "BAT", onClick = { decision = "BAT" }, label = { Text("Bat") })
            FilterChip(selected = decision == "BOWL", onClick = { decision = "BOWL" }, label = { Text("Bowl") })
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.submitToss(winnerId!!, decision!!) },
            enabled = winnerId != null && decision != null && !vm.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Confirm Toss →") }
    }
}

@Composable
private fun OpenersStep(m: MatchResponse, vm: OnlineScorerViewModel) {
    val inningsNumber = (vm.scorecard?.innings?.size ?: 0) + 1
    val battingId = vm.battingTeamId(inningsNumber)
    val bowlingId = vm.bowlingTeamId(inningsNumber)
    if (battingId == null || bowlingId == null) {
        Text("Waiting for toss result…", color = Muted)
        return
    }
    val battingName = if (battingId == m.teamAId) m.teamAName else m.teamBName
    val bowlingName = if (bowlingId == m.teamAId) m.teamAName else m.teamBName

    Text("Innings $inningsNumber — $battingName batting", color = Amber, style = MaterialTheme.typography.headlineMedium)
    if (inningsNumber == 2) {
        val target = vm.scorecard?.innings?.getOrNull(0)?.let { it.totalRuns + 1 }
        if (target != null) Text("Target: $target runs", color = Muted, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(16.dp))

    if (!vm.canScoreThisMatch) {
        WaitingForScorer(what = "picked openers")
        return
    }

    var striker by remember(inningsNumber) { mutableStateOf<Long?>(null) }
    var nonStriker by remember(inningsNumber) { mutableStateOf<Long?>(null) }
    var bowler by remember(inningsNumber) { mutableStateOf<Long?>(null) }
    Panel {
        Label("Opening striker ($battingName)")
        PlayerDropdown(vm.rosterFor(battingId), striker) { striker = it }
        Spacer(Modifier.height(12.dp))
        Label("Non-striker ($battingName)")
        PlayerDropdown(vm.rosterFor(battingId).filter { it.id != striker }, nonStriker) { nonStriker = it }
        Spacer(Modifier.height(12.dp))
        Label("Opening bowler ($bowlingName)")
        PlayerDropdown(vm.rosterFor(bowlingId), bowler) { bowler = it }
        Spacer(Modifier.height(16.dp))
        val ready = striker != null && nonStriker != null && bowler != null
        Button(
            onClick = { vm.startInnings(striker!!, nonStriker!!, bowler!!) },
            enabled = ready && !vm.loading, modifier = Modifier.fillMaxWidth(),
        ) { Text("Start Innings →") }
    }
}

@Composable
private fun PlayerDropdown(players: List<PlayerResponse>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = players.find { it.id == selectedId }?.name ?: "Select player"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { onSelect(p.id); expanded = false }) }
        }
    }
}

private val DISMISSALS = listOf(
    "BOWLED", "CAUGHT", "LBW", "RUN_OUT", "STUMPED", "HIT_WICKET",
    "RETIRED_HURT", "TIMED_OUT", "HANDLED_BALL", "OBSTRUCTING_FIELD", "HIT_BALL_TWICE",
)

/** Which run-count picker is currently showing in place of the normal button grid. */
private enum class PendingExtra { WIDE, NOBALL, BYE, LEGBYE, RUN_OUT_RUNS }

@Composable
private fun LiveStep(vm: OnlineScorerViewModel) {
    val inn = vm.scorecard?.innings?.lastOrNull() ?: run { Text("Loading innings…", color = Muted); return }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScoreHeader(inn, vm.loading) }
        item {
            Row {
                Label("This over  ")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    inn.thisOver.forEach { b -> AssistChip(onClick = {}, label = { Text(b) }) }
                }
            }
        }

        if (vm.canScoreThisMatch) {
            item { ScoringControls(vm, inn) }
        } else {
            item {
                Panel {
                    Text("Watching live", color = Amber)
                    Text(
                        if (CurrentUser.canScore) "You're not the scorer assigned to this match — view-only."
                        else "You have view-only access to this match.",
                        color = Muted, style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        item { ScorecardPanel(inn) }

        item {
            Panel {
                Label("Commentary")
                Spacer(Modifier.height(6.dp))
                inn.commentary.take(15).forEach { line -> Text(line, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun ScoreHeader(inn: InningsScorecardDto, loading: Boolean) {
    // The one deliberate "hero" moment in this reskin, matching the mockup's
    // live-score panel exactly: dark green surface, cream team name, vivid
    // orange score (safe here — vivid orange has strong contrast against
    // dark green, unlike on the white/cream cards everywhere else — see
    // Color.kt's note on the Amber alias for why other screens use a darker
    // orange instead). Uses TekadiGreenDim, not the brighter primary
    // TekadiGreen — see Color.kt's header note on why that swap was needed
    // once the primary green was refined to match the actual logo (a
    // brighter green has less contrast against vivid orange text).
    // Every other panel in this screen stays the plain white Panel() so
    // this one stands out exactly once, not everywhere.
    Surface(color = TekadiGreenDim, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Innings ${inn.inningsNumber} · ${inn.battingTeamName}", color = ChipGreenBg, style = MaterialTheme.typography.labelSmall)
            Text("${inn.totalRuns}/${inn.wickets}", color = AccentOrange, style = MaterialTheme.typography.displayLarge)
            Text("(${inn.oversDisplay} ov) · CRR ${"%.2f".format(inn.currentRunRate)}", color = TextOnDark)
            if (inn.target != null && inn.runsNeeded != null && inn.ballsLeft != null) {
                Text("Need ${inn.runsNeeded} off ${inn.ballsLeft} balls · RRR ${"%.2f".format(inn.requiredRunRate ?: 0.0)}",
                    color = ChipGreenBg, modifier = Modifier.padding(top = 4.dp))
            }
            if (loading) Text("Syncing…", color = ChipGreenBg, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** KvsvRequest1.3 #5 — pick up players added to either team after the match started. */
@Composable
private fun RefreshSquadButton(vm: OnlineScorerViewModel) {
    TextButton(onClick = { vm.refreshSquads() }, enabled = !vm.loading) {
        Text("↻ Player added to the team? Refresh squad", color = InfoBlue, style = MaterialTheme.typography.labelSmall)
    }
}

/** Everything a scorer (not a viewer) sees: the wicket flow, next-batter/bowler prompts, and the button grid. */
@Composable
private fun ScoringControls(vm: OnlineScorerViewModel, inn: InningsScorecardDto) {
    var pendingWicket by remember { mutableStateOf(false) }
    var pendingDismissal by remember { mutableStateOf<String?>(null) }
    var pendingExtra by remember { mutableStateOf<PendingExtra?>(null) }
    val needsBatter = vm.needsNewBatter(inn)
    val needsBowler = vm.needsNewBowler(inn)

    fun resetPending() { pendingWicket = false; pendingDismissal = null; pendingExtra = null }

    when {
        pendingExtra != null -> RunsCountPicker(
            title = when (pendingExtra) {
                PendingExtra.WIDE -> "Wide — extra runs run (0 if none)"
                PendingExtra.NOBALL -> "No ball — runs off the bat (0 if none)"
                PendingExtra.BYE -> "Bye — total runs run"
                PendingExtra.LEGBYE -> "Leg bye — total runs run"
                PendingExtra.RUN_OUT_RUNS -> "Run out — runs completed before the throw"
                null -> ""
            },
            options = when (pendingExtra) {
                PendingExtra.BYE, PendingExtra.LEGBYE -> listOf(1, 2, 3, 4)
                else -> listOf(0, 1, 2, 3, 4, 6)
            },
            onPick = { count ->
                when (pendingExtra) {
                    PendingExtra.WIDE -> vm.addWide(count)
                    PendingExtra.NOBALL -> vm.addNoBall(count)
                    PendingExtra.BYE -> vm.addBye(count)
                    PendingExtra.LEGBYE -> vm.addLegBye(count)
                    PendingExtra.RUN_OUT_RUNS -> vm.confirmWicket(pendingDismissal ?: "RUN_OUT", count)
                    null -> {}
                }
                resetPending()
            },
            onCancel = { resetPending() },
        )
        pendingWicket -> Panel {
            Text("Dismissal type", color = WicketRed)
            Spacer(Modifier.height(8.dp))
            DISMISSALS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    row.forEach { d ->
                        OutlinedButton(onClick = {
                            if (d == "RUN_OUT") {
                                pendingDismissal = d; pendingWicket = false; pendingExtra = PendingExtra.RUN_OUT_RUNS
                            } else {
                                vm.confirmWicket(d); resetPending()
                            }
                        }) { Text(d) }
                    }
                }
            }
            TextButton(onClick = { resetPending() }) { Text("Cancel", color = Muted) }
        }
        needsBatter -> Panel {
            Text("Select next batter", color = Amber)
            Spacer(Modifier.height(8.dp))
            vm.availableNextBatters(inn).forEach { p ->
                OutlinedButton(onClick = { vm.selectNextBatter(p.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) { Text(p.name) }
            }
            RefreshSquadButton(vm)
        }
        needsBowler -> Panel {
            Text("Over complete — select next bowler", color = Amber)
            // KvsvRequest1.3 #1 — the cap is now per match (or off), so say which applies.
            val cap = vm.match?.let { m ->
                if (!m.bowlerOverLimitEnabled) "no overs cap this match" else m.maxOversPerBowler?.let { "max $it over${if (it == 1) "" else "s"} each" }
            }
            Text(
                "(Backend validates eligibility — no consecutive overs${cap?.let { ", $it" } ?: ", overs cap"})",
                color = Muted, style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(8.dp))
            val bowlingId = vm.bowlingTeamId(inn.inningsNumber)
            if (bowlingId != null) {
                vm.rosterFor(bowlingId).forEach { p ->
                    OutlinedButton(onClick = { vm.selectNextBowler(p.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) { Text(p.name) }
                }
            }
            RefreshSquadButton(vm)
        }
        else -> Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { r -> Button(onClick = { vm.addRuns(r) }, modifier = Modifier.weight(1f), enabled = !vm.loading) { Text("$r") } }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.addRuns(4) }, modifier = Modifier.weight(1f), enabled = !vm.loading, colors = ButtonDefaults.buttonColors(containerColor = AmberDim)) { Text("4") }
                Button(onClick = { vm.addRuns(6) }, modifier = Modifier.weight(1f), enabled = !vm.loading, colors = ButtonDefaults.buttonColors(containerColor = AmberDim)) { Text("6") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { pendingExtra = PendingExtra.WIDE }, modifier = Modifier.weight(1f), enabled = !vm.loading) { Text("Wide") }
                OutlinedButton(onClick = { pendingExtra = PendingExtra.NOBALL }, modifier = Modifier.weight(1f), enabled = !vm.loading) { Text("No Ball") }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { pendingExtra = PendingExtra.BYE }, modifier = Modifier.weight(1f), enabled = !vm.loading) { Text("Bye") }
                OutlinedButton(onClick = { pendingExtra = PendingExtra.LEGBYE }, modifier = Modifier.weight(1f), enabled = !vm.loading) { Text("Leg Bye") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pendingWicket = true }, modifier = Modifier.weight(1f), enabled = !vm.loading,
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed)) { Text("Wicket") }
                OutlinedButton(onClick = { vm.undo() }, modifier = Modifier.weight(1f), enabled = !vm.loading) { Text("Undo") }
            }
        }
    }
}

/** Same shape/panel as the wicket-dismissal picker — a quick "how many runs" follow-up step. */
@Composable
private fun RunsCountPicker(title: String, options: List<Int>, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    Panel {
        Text(title, color = Amber)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { n ->
                OutlinedButton(onClick = { onPick(n) }, modifier = Modifier.weight(1f)) { Text("$n") }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel", color = Muted) }
    }
}

@Composable
private fun ScorecardPanel(inn: InningsScorecardDto, title: String? = null) {
    Panel {
        title?.let { Text(it, color = Amber, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(6.dp)) }
        Label("Batting")
        inn.battingCard.forEach { b ->
            val onCrease = b.onCrease != null && !b.out
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text((if (b.onCrease == "STRIKER" && onCrease) "▸ " else "") + b.name, color = if (onCrease) Amber else Chalk, modifier = Modifier.weight(1f))
                Text("${b.runs} (${b.balls})", color = Chalk)
            }
            if (b.out) Text(b.howOut.orEmpty(), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text("Extras: ${inn.extrasTotal} (wd ${inn.extrasWide}, nb ${inn.extrasNoball}, b ${inn.extrasBye}, lb ${inn.extrasLegbye})",
            color = Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        Label("Bowling")
        inn.bowlingCard.forEach { bw ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bw.name, color = Chalk, modifier = Modifier.weight(1f))
                Text("${bw.wickets}/${bw.runsConceded} (${bw.overs})", color = Chalk)
            }
        }
    }
}

@Composable
private fun ResultStep(m: MatchResponse, vm: OnlineScorerViewModel, onExit: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                val icon = if (m.status == "CANCELLED") "🚫" else "🏆"
                Text("$icon ${m.resultSummary ?: "Match complete"}", color = Amber, style = MaterialTheme.typography.headlineMedium)
                // KvsvRequest1.3 #7
                m.playerOfMatchName?.let { name ->
                    Spacer(Modifier.height(6.dp))
                    PlayerOfMatchLine(name, color = Chalk, style = MaterialTheme.typography.bodyLarge)
                }
                if (vm.canOverridePlayerOfMatch) {
                    Spacer(Modifier.height(8.dp))
                    PlayerOfMatchPicker(
                        candidates = playerOfMatchCandidates(vm.teamAPlayers, vm.teamBPlayers),
                        currentId = m.playerOfMatchId, enabled = !vm.loading,
                        onPick = { vm.setPlayerOfMatch(it) },
                    )
                }
            }
        }
        vm.scorecard?.innings?.forEachIndexed { idx, inn ->
            item { ScorecardPanel(inn, title = "Innings ${idx + 1} — ${inn.battingTeamName}") }
        }
        item { Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to Matches") } }
    }
}

/**
 * Feature request #3: the deciding innings just finished, but no result/summary exists until
 * the assigned scorer explicitly closes the match. Everyone else (viewers, a scorer-role user
 * not assigned here) just sees a waiting message — same read-only pattern as every other step.
 */
@Composable
private fun AwaitingCloseStep(vm: OnlineScorerViewModel) {
    val lastInnings = vm.scorecard?.innings?.lastOrNull()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                Text("Innings complete", color = Amber, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "The match has finished — close it to generate the result and summary.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        lastInnings?.let { inn -> item { ScorecardPanel(inn, title = "Innings ${inn.inningsNumber} — ${inn.battingTeamName}") } }
        if (vm.canScoreThisMatch) {
            item {
                Button(onClick = { vm.closeMatch() }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                    Text("Close Match & Generate Summary")
                }
            }
        } else {
            item {
                Panel {
                    Text("Waiting for the scorer to close the match…", color = Amber)
                    Text("The result will appear here once they do.", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Feature request #2: a "Request to Score" button for a team player when no scorer is assigned
 * yet, plus (Super Admin only) a review panel for pending requests. Shown at the top of the Toss
 * step, since that's the earliest point a scorer identity actually matters.
 */
@Composable
private fun ScorerAssignmentPanel(m: MatchResponse, vm: OnlineScorerViewModel) {
    LaunchedEffect(m.id, CurrentUser.isSuperAdmin) {
        if (CurrentUser.isSuperAdmin) vm.loadScorerRequests()
    }

    if (m.scorerUserId != null) {
        Panel {
            Text("Scorer: ${m.scorerName ?: "assigned"}", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        return
    }

    Panel {
        Text("No scorer assigned yet", color = Amber, style = MaterialTheme.typography.labelSmall)

        // GAP FIX: "Super admin/team management role can assign as a scorer from current
        // match" — direct assignment previously had no UI at all (only the request/accept
        // workflow below did), and the backend itself only allowed Super Admin. Both are now
        // fixed; this panel is shown to Super Admin or the manager of either competing team.
        if (vm.canAssignScorerDirectly) {
            Spacer(Modifier.height(8.dp))
            var userIdText by remember(m.id) { mutableStateOf("") }
            OutlinedTextField(
                value = userIdText,
                onValueChange = { userIdText = it },
                label = { Text("User ID to assign as scorer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { userIdText.toLongOrNull()?.let { vm.assignScorerDirect(it) } },
                enabled = !vm.loading && userIdText.toLongOrNull() != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Assign Scorer") }
        }

        if (!CurrentUser.isSuperAdmin) {
            Spacer(Modifier.height(8.dp))
            when (vm.myScorerRequestStatus) {
                null -> Button(onClick = { vm.requestToScore() }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                    Text("Request to Score")
                }
                "PENDING" -> Text("Your request is pending Super Admin approval.", color = Muted, style = MaterialTheme.typography.labelSmall)
                else -> Text("Your request was ${vm.myScorerRequestStatus?.lowercase()}.", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (CurrentUser.isSuperAdmin && vm.scorerRequests.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            vm.scorerRequests.filter { it.status == "PENDING" }.forEach { req ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(req.requestedByName, color = Chalk, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.acceptScorerRequest(req.id) }) { Text("Accept", color = Amber) }
                    TextButton(onClick = { vm.rejectScorerRequest(req.id) }) { Text("Reject", color = WicketRed) }
                }
            }
        }
    }
}


/**
 * KvsvRequest1.3 #6 — confirmation before an admin closes the match, with a required reason.
 * The match is cancelled straight away (no Super Admin approval step, unlike a scorer's close
 * request), so the dialog says that plainly.
 */
@Composable
private fun CloseMatchDialog(match: MatchResponse?, submitting: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    val reasonError = closeReasonError(reason)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close this match?") },
        text = {
            Column {
                Text(
                    (match?.let { "${it.teamAName} vs ${it.teamBName} will be closed now and marked cancelled. " } ?: "") +
                        "No more scoring will be possible.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it.take(CLOSE_REASON_MAX) },
                    label = { Text("Reason (required)") },
                    placeholder = { Text("e.g. Rain stopped play") },
                    supportingText = { Text("${reason.trim().length}/$CLOSE_REASON_MAX") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = reasonError == null && !submitting) {
                Text(if (submitting) "Closing…" else "Close match", color = WicketRed)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
