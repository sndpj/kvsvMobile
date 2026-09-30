package com.tekadi.kvvs.league.ui.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.network.TeamStandingRow
import com.tekadi.kvvs.league.network.TournamentResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog

/** Design-brief "Standings" screen — points table for a tournament, ranked by points then wins. */
@Composable
fun StandingsScreen(vm: StandingsViewModel = viewModel(), onBack: () -> Unit) {
    ErrorDialog(vm.error) { vm.error = null }
    LaunchedEffect(Unit) { vm.loadTournaments() }

    Surface(color = PitchBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Standings", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))


            if (vm.tournaments.size > 1) {
                TournamentPicker(vm.tournaments, vm.selectedTournament) { vm.selectTournament(it) }
                Spacer(Modifier.height(12.dp))
            }

            if (vm.loading && vm.rows.isEmpty()) {
                PageLoader(color = ArcTeal)
                return@Column
            }
            if (vm.selectedTournament == null) {
                Text("Select a tournament to see its table.", color = TextMuted)
                return@Column
            }
            if (vm.rows.isEmpty()) {
                Text("No teams registered in this tournament yet.", color = TextMuted)
                return@Column
            }

            StandingsTable(vm.rows)
        }
    }
}

@Composable
private fun TournamentPicker(tournaments: List<TournamentResponse>, selected: TournamentResponse?, onSelect: (TournamentResponse) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: "Select tournament")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tournaments.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { onSelect(t); expanded = false }) }
        }
    }
}

@Composable
private fun StandingsTable(rows: List<TeamStandingRow>) {
    Surface(shape = heroPanelShape(), color = GraphitePanel, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("#", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
                Text("TEAM", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("P", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(26.dp))
                Text("W", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(26.dp))
                Text("PTS", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
            }
            LazyColumn {
                items(rows) { r ->
                    // Top four is the design brief's own qualification cutoff, kept as a visual
                    // cue even though there's no tournament-configurable "qualifying spots"
                    // concept yet — a reasonable, clearly-scoped default rather than nothing.
                    val qualifying = r.position <= 4
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (qualifying) ChipGreenBg.copy(alpha = 0.4f) else CardSurface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.position.toString(), color = TextMuted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(22.dp))
                        Text(
                            r.teamName, color = TextPrimary, modifier = Modifier.weight(1f),
                            fontWeight = if (qualifying) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                        )
                        Text(r.played.toString(), color = TextMuted, modifier = Modifier.width(26.dp))
                        Text(r.won.toString(), color = TextMuted, modifier = Modifier.width(26.dp))
                        Text(
                            r.points.toString(), color = TextPrimary, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.width(34.dp),
                        )
                    }
                }
            }
            // Honest about the current simplification (see StandingsDtos.TeamStandingRow on the
            // backend) rather than promising NRR-based tiebreaking that isn't built yet.
            Text(
                "Ranked by points, then wins. Top four highlighted.",
                color = TextMuted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}
