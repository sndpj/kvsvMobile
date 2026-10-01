package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.network.InningsScorecardDto
import com.tekadi.kvvs.league.ui.theme.*

@Composable
fun HistoryDetailScreen(
    matchId: Long, vm: HistoryDetailViewModel = viewModel(), onBack: () -> Unit,
    // Player profile (KvsvRequest1.3) — tap any player name on the scorecard.
    onPlayerClick: (playerId: Long) -> Unit = {},
) {
    LaunchedEffect(matchId) { vm.load(matchId) }
    com.tekadi.kvvs.league.util.ErrorDialog(if (vm.match != null) vm.error else null) { vm.error = null }
    com.tekadi.kvvs.league.util.InfoToast(vm.info) { vm.info = null }

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
                                    val icon = if (match.status == "CANCELLED") "🚫" else "🏆"
                                    Text("$icon ${match.resultSummary}", color = RepulsorGold, style = MaterialTheme.typography.bodyLarge)
                                }
                                // KvsvRequest1.3 #7
                                if (match.playerOfMatchName != null && match.playerOfMatchId != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Box(Modifier.clickable { onPlayerClick(match.playerOfMatchId) }) {
                                        PlayerOfMatchLine(match.playerOfMatchName, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                if (vm.canOverridePlayerOfMatch && vm.potmCandidates.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    PlayerOfMatchPicker(vm.potmCandidates, match.playerOfMatchId, !vm.loading) { vm.setPlayerOfMatch(it) }
                                }
                            }
                        }
                    }
                }
                vm.scorecard?.innings?.forEachIndexed { idx, inn ->
                    item { InningsCard(inn, "Innings ${idx + 1} — ${inn.battingTeamName}", onPlayerClick) }
                }
            }
        }
    }
}

@Composable
private fun InningsCard(inn: InningsScorecardDto, title: String, onPlayerClick: (Long) -> Unit) {
    Surface(shape = heroPanelShape(), color = GraphitePanel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
            Text("${inn.totalRuns}/${inn.wickets} (${inn.oversDisplay} ov)", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text("BATTING", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            inn.battingCard.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(b.name, color = TextPrimary, modifier = Modifier.weight(1f).clickable { onPlayerClick(b.playerId) })
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
                    Text(bw.name, color = TextPrimary, modifier = Modifier.weight(1f).clickable { onPlayerClick(bw.playerId) })
                    Text("${bw.wickets}/${bw.runsConceded} (${bw.overs})", color = TextPrimary)
                }
            }
        }
    }
}
