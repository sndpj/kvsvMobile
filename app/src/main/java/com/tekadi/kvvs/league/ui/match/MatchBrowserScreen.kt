package com.tekadi.kvvs.league.ui.match

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.MatchResponse
import com.tekadi.kvvs.league.network.TournamentResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun MatchBrowserScreen(vm: MatchBrowserViewModel = viewModel(), onMatchSelected: (MatchResponse) -> Unit, onLogout: () -> Unit) {
    ErrorDialog(vm.error) { vm.error = null }
    InfoToast(vm.info) { vm.info = null }

    // GAP FIX: refreshIfTournamentSelected() re-fetches the match list every time this screen
    // (re)appears — including navigating back here after scoring/closing a match — so a status
    // change made elsewhere (e.g. INNINGS_COMPLETE -> COMPLETED via close-match) is never left
    // showing stale data. See MatchBrowserViewModel.refreshIfTournamentSelected.
    LaunchedEffect(Unit) {
        vm.loadTournaments()
        vm.refreshIfTournamentSelected()
    }

    Surface(color = PitchBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    if (vm.selectedTournament == null) "Tournaments" else vm.selectedTournament!!.name,
                    color = Amber, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f),
                )
                if (vm.selectedTournament != null) {
                    TextButton(onClick = { vm.selectedTournament = null }) { Text("Back", color = Muted) }
                } else {
                    TextButton(onClick = onLogout) { Text("Log out", color = Muted) }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (vm.loading) {
                PageLoader(color = Amber)
            }

            if (vm.selectedTournament == null) {
                TournamentList(vm.tournaments) { vm.selectTournament(it) }
            } else {
                MatchList(vm, onMatchSelected)
            }
        }
    }
}

@Composable
private fun TournamentList(tournaments: List<TournamentResponse>, onSelect: (TournamentResponse) -> Unit) {
    if (tournaments.isEmpty()) {
        Text(
            "No tournaments yet. Create one via the backend API " +
                "(POST /api/tournaments) — tournament setup isn't part of this app's scope.",
            color = Muted, style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tournaments) { t ->
            Surface(
                color = PanelGreen, shape = MaterialTheme.shapes.medium,
                onClick = { onSelect(t) }, modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(t.name, color = Chalk, style = MaterialTheme.typography.bodyLarge)
                    Text("${t.matchFormat} · ${t.ballType} · ${t.status}", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Human-readable label instead of the raw backend status string (was literally showing "INNINGS_COMPLETE" etc.). */
private fun statusLabel(status: String): String = when (status) {
    "SCHEDULED" -> "Scheduled"
    "LIVE" -> "Live"
    "INNINGS_COMPLETE" -> "Innings Complete"
    "COMPLETED" -> "Completed"
    "CANCELLED" -> "Cancelled"
    else -> status
}

/** "Score" to start a fresh match, "View" once it's fully done, "Open" for anything still in progress. */
private fun actionLabel(status: String): String = when (status) {
    "SCHEDULED" -> "Score"
    "COMPLETED" -> "View"
    else -> "Open"
}

@Composable
private fun MatchList(vm: MatchBrowserViewModel, onMatchSelected: (MatchResponse) -> Unit) {
    Column {
        // Feature request: "disable new match creation option for viewer" — was shown to
        // everyone regardless of role, and only failed with a 403 after tapping "Create Match".
        if (CurrentUser.canCreateMatch) {
            Button(onClick = { vm.showCreateForm = !vm.showCreateForm }, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.showCreateForm) "Cancel" else "+ New Match")
            }
            Spacer(Modifier.height(8.dp))
        }

        if (vm.showCreateForm && CurrentUser.canCreateMatch) {
            CreateMatchForm(vm, onMatchSelected)
            Spacer(Modifier.height(12.dp))
        }

        if (vm.matches.isEmpty() && !vm.loading) {
            Text("No matches in this tournament yet.", color = Muted)
        }

        // Feature request: "Show latest match on top" — the backend now returns matches ordered
        // by matchDate/createdAt descending (see MatchRepository), so no client-side re-sort is
        // needed here; this list renders in whatever order the server sends.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.matches) { m ->
                Surface(color = PanelGreen, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${m.teamAName} vs ${m.teamBName}", color = Chalk)
                            // Feature request: "Match list item should contain date."
                            Text(
                                listOfNotNull(m.matchDate, "${m.oversLimit} overs", statusLabel(m.status))
                                    .joinToString(" · ") + (m.resultSummary?.let { " · $it" } ?: ""),
                                color = Muted, style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Button(onClick = { onMatchSelected(m) }) { Text(actionLabel(m.status)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateMatchForm(vm: MatchBrowserViewModel, onMatchSelected: (MatchResponse) -> Unit) {
    Surface(color = PanelAlt, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            MatchDatePicker(vm.newMatchDate) { vm.newMatchDate = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.newMatchOvers, onValueChange = { vm.newMatchOvers = it },
                label = { Text("Overs per innings") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TeamDropdown("Team A", vm.teams, vm.newMatchTeamAId) { vm.newMatchTeamAId = it }
            Spacer(Modifier.height(8.dp))
            TeamDropdown("Team B", vm.teams, vm.newMatchTeamBId) { vm.newMatchTeamBId = it }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.createMatch(onMatchSelected) }, modifier = Modifier.fillMaxWidth()) {
                Text("Create Match")
            }
        }
    }
}

/**
 * Feature request: "Show match date as current date by default and show calendar to pick date
 * by user." Was a raw text field with no default and a manually-typed "yyyy-MM-dd" — easy to
 * get wrong. `currentValue` already defaults to today (see MatchBrowserViewModel.newMatchDate).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDatePicker(currentValue: String, onDateSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val initialMillis = runCatching { LocalDate.parse(currentValue).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (currentValue.isNotBlank()) "Match date: $currentValue" else "Pick match date")
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(picked.toString())
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TeamDropdown(label: String, teams: List<com.tekadi.kvvs.league.network.TeamResponse>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = teams.find { it.id == selectedId }?.name ?: "Select $label"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            teams.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { onSelect(t.id); expanded = false }) }
        }
    }
}
