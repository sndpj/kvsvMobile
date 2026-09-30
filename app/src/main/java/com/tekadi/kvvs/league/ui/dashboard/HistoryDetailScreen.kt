package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.network.InningsScorecardDto
import com.tekadi.kvvs.league.ui.theme.*

@Composable
fun HistoryDetailScreen(matchId: Long, vm: HistoryDetailViewModel = viewModel(), onBack: () -> Unit) {
    LaunchedEffect(matchId) { vm.load(matchId) }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Match Details", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))

            if (vm.loading && vm.match == null) { PageLoader(color = ArcTeal); return@Column }
            if (vm.error != null && vm.match == null) {
                RetryPanel(message = vm.error!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.load(matchId) })
                return@Column
            }

            val match = vm.match
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (match != null) {
                    item {
                        Surface(shape = heroPanelShape(), color = GraphitePanel, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${match.teamAName} vs ${match.teamBName}", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                                Text("${match.oversLimit} overs · ${match.status}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                if (match.resultSummary != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("🏆 ${match.resultSummary}", color = RepulsorGold, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                vm.scorecard?.innings?.forEachIndexed { idx, inn ->
                    item { InningsCard(inn, "Innings ${idx + 1} — ${inn.battingTeamName}") }
                }
            }
        }
    }
}

@Composable
private fun InningsCard(inn: InningsScorecardDto, title: String) {
    Surface(shape = heroPanelShape(), color = GraphitePanel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
            Text("${inn.totalRuns}/${inn.wickets} (${inn.oversDisplay} ov)", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text("BATTING", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            inn.battingCard.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(b.name, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("${b.runs} (${b.balls})", color = TextPrimary)
                }
                if (b.out && b.howOut != null) {
                    Text(b.howOut, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("BOWLING", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            inn.bowlingCard.forEach { bw ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bw.name, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("${bw.wickets}/${bw.runsConceded} (${bw.overs})", color = TextPrimary)
                }
            }
        }
    }
}
