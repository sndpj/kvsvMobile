package com.tekadi.kvvs.league.ui.scoring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.data.Dismissal
import com.tekadi.kvvs.league.ui.theme.*

@Composable
fun LiveScorerScreen(vm: ScorerViewModel = viewModel()) {
    Surface(color = PitchBg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.padding(16.dp)) {
            when (vm.screen) {
                Screen.SETUP -> SetupScreen(vm)
                Screen.TOSS -> TossScreen(vm)
                Screen.OPENERS_1 -> OpenersScreen(vm, isFirstInnings = true)
                Screen.OPENERS_2 -> OpenersScreen(vm, isFirstInnings = false)
                Screen.LIVE -> LiveScreen(vm)
                Screen.BREAK -> BreakScreen(vm)
                Screen.RESULT -> ResultScreen(vm)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Muted, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = PanelGreen, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SetupScreen(vm: ScorerViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("⚡ Live Scorer", color = Amber, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("Set up a match — works fully offline.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Team A"); OutlinedTextField(value = vm.teamA.name, onValueChange = { vm.teamA = vm.teamA.copy(name = it) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            SectionLabel("Team B"); OutlinedTextField(value = vm.teamB.name, onValueChange = { vm.teamB = vm.teamB.copy(name = it) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            SectionLabel("Overs per innings")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 6, 10, 15, 20).forEach { o ->
                    FilterChip(selected = vm.oversLimit == o, onClick = { vm.oversLimit = o }, label = { Text("$o") })
                }
            }
            val cap = com.tekadi.kvvs.league.data.maxOversPerBowler(vm.oversLimit)
            Text("Max $cap over${if (cap == 1) "" else "s"} per bowler at this length", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.screen = Screen.TOSS }, modifier = Modifier.fillMaxWidth()) { Text("Proceed to Toss →") }
        }
    }
}

@Composable
private fun TossScreen(vm: ScorerViewModel) {
    var winner by remember { mutableStateOf<String?>(null) }
    var decision by remember { mutableStateOf<String?>(null) }
    Column {
        Text("🪙 Toss", color = Amber, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Who won the toss?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(vm.teamA, vm.teamB).forEach { t ->
                    FilterChip(selected = winner == t.key, onClick = { winner = t.key }, label = { Text(t.name) })
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Elected to")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("BAT" to "Bat", "BOWL" to "Bowl").forEach { (v, label) ->
                    FilterChip(selected = decision == v, onClick = { decision = v }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.confirmToss(winner!!, decision!!) }, enabled = winner != null && decision != null, modifier = Modifier.fillMaxWidth()) {
                Text("Confirm Toss →")
            }
        }
    }
}

@Composable
private fun OpenersScreen(vm: ScorerViewModel, isFirstInnings: Boolean) {
    val battingKey = if (isFirstInnings) (if (vm.tossDecision == "BAT") vm.tossWinnerKey!! else if (vm.tossWinnerKey == "A") "B" else "A")
                      else (if (vm.innings[0].battingTeamKey == "A") "B" else "A")
    val bat = if (battingKey == "A") vm.teamA else vm.teamB
    val bowl = if (battingKey == "A") vm.teamB else vm.teamA

    var striker by remember { mutableStateOf<String?>(null) }
    var nonStriker by remember { mutableStateOf<String?>(null) }
    var bowler by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(if (isFirstInnings) "Innings 1 — ${bat.name} batting" else "Innings 2 — ${bat.name} batting",
            color = Amber, style = MaterialTheme.typography.headlineMedium)
        if (!isFirstInnings) {
            Text("Target: ${vm.innings[0].totalRuns + 1} runs", color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Opening striker (${bat.name})")
            PlayerDropdown(bat.players, striker) { striker = it }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Non-striker (${bat.name})")
            PlayerDropdown(bat.players.filter { it.id != striker }, nonStriker) { nonStriker = it }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Opening bowler (${bowl.name})")
            PlayerDropdown(bowl.players, bowler) { bowler = it }
            Spacer(Modifier.height(16.dp))
            val ready = striker != null && nonStriker != null && bowler != null
            Button(
                onClick = { if (isFirstInnings) vm.startInnings1(striker!!, nonStriker!!, bowler!!) else vm.startInnings2(striker!!, nonStriker!!, bowler!!) },
                enabled = ready, modifier = Modifier.fillMaxWidth(),
            ) { Text("Start Innings →") }
        }
    }
}

@Composable
private fun PlayerDropdown(players: List<com.tekadi.kvvs.league.data.Player>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = players.find { it.id == selectedId }?.name ?: "Select player"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { p ->
                DropdownMenuItem(text = { Text(p.name) }, onClick = { onSelect(p.id); expanded = false })
            }
        }
    }
}

@Composable
private fun LiveScreen(vm: ScorerViewModel) {
    val inn = vm.current ?: return
    val battingTeam = if (inn.battingTeamKey == "A") vm.teamA else vm.teamB
    val bowlingTeam = if (inn.bowlingTeamKey == "A") vm.teamA else vm.teamB

    val runsNeeded = inn.target?.let { (it - inn.totalRuns).coerceAtLeast(0) }
    val ballsLeft = if (inn.target != null) (vm.oversLimit * 6 - inn.legalBalls).coerceAtLeast(0) else null
    val rrr = if (inn.target != null && ballsLeft != null && ballsLeft > 0) (runsNeeded!! * 6.0 / ballsLeft) else null

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                Text("${battingTeam.name} · Innings ${vm.currentInningsIndex + 1}", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("${inn.totalRuns}/${inn.wickets}", color = Amber, style = MaterialTheme.typography.displayLarge)
                Text("(${inn.oversDisplay} / ${vm.oversLimit} ov) · CRR ${"%.2f".format(inn.currentRunRate)}", color = Chalk)
                if (rrr != null) {
                    Text("Need $runsNeeded off $ballsLeft balls · RRR ${"%.2f".format(rrr)}", color = InfoBlue, modifier = Modifier.padding(top = 4.dp))
                }
                if (vm.syncStatus.isNotEmpty()) {
                    Text(vm.syncStatus, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("This over  ")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    inn.thisOver.forEach { b ->
                        AssistChip(onClick = {}, label = { Text(b) })
                    }
                }
            }
        }

        if (vm.pendingWicket) {
            item {
                Panel {
                    Text("How was ${inn.battingCard[inn.strikerId]?.name} dismissed?", color = WicketRed)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {}
                    Dismissal.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            row.forEach { d -> OutlinedButton(onClick = { vm.confirmWicket(d) }) { Text(d.label) } }
                        }
                    }
                    TextButton(onClick = { vm.pendingWicket = false }) { Text("Cancel", color = Muted) }
                }
            }
        } else if (vm.pendingNewBatter) {
            item {
                Panel {
                    Text("Select next batter", color = Amber)
                    Spacer(Modifier.height(8.dp))
                    battingTeam.players.filter { !inn.battingCard.getValue(it.id).out && !inn.battingCard.getValue(it.id).batted }.forEach { p ->
                        OutlinedButton(onClick = { vm.selectNewBatter(p.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) { Text(p.name) }
                    }
                }
            }
        } else if (vm.pendingNewBowler) {
            item {
                Panel {
                    Text("Over complete — select next bowler", color = Amber)
                    Text("Max ${vm.bowlerCap} over(s) per bowler", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    val eligible = vm.eligibleBowlers()
                    if (eligible.isEmpty()) {
                        Text("No eligible bowler left.", color = WicketRed)
                    } else {
                        eligible.forEach { p ->
                            OutlinedButton(onClick = { vm.selectNewBowler(p.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) { Text(p.name) }
                        }
                    }
                }
            }
        } else {
            item {
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..3).forEach { r -> Button(onClick = { vm.addRuns(r) }, modifier = Modifier.weight(1f)) { Text("$r") } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.addRuns(4) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberDim)) { Text("4") }
                        Button(onClick = { vm.addRuns(6) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberDim)) { Text("6") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { vm.addWide() }, modifier = Modifier.weight(1f)) { Text("Wide") }
                        OutlinedButton(onClick = { vm.addNoBall() }, modifier = Modifier.weight(1f)) { Text("No Ball") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { vm.addBye() }, modifier = Modifier.weight(1f)) { Text("Bye") }
                        OutlinedButton(onClick = { vm.addLegBye() }, modifier = Modifier.weight(1f)) { Text("Leg Bye") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.pendingWicket = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = WicketRed)) { Text("Wicket") }
                        OutlinedButton(onClick = { vm.undo() }, modifier = Modifier.weight(1f)) { Text("Undo") }
                    }
                }
            }
        }

        item { ScorecardPanel(inn, battingTeam, bowlingTeam) }

        item {
            Panel {
                SectionLabel("Commentary")
                Spacer(Modifier.height(6.dp))
                inn.ballLog.take(15).forEach { line -> Text(line, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun ScorecardPanel(
    inn: com.tekadi.kvvs.league.data.Innings,
    battingTeam: com.tekadi.kvvs.league.data.Team,
    bowlingTeam: com.tekadi.kvvs.league.data.Team,
    title: String? = null,
) {
    Panel {
        title?.let { Text(it, color = Amber, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(6.dp)) }
        SectionLabel("Batting")
        battingTeam.players.filter { inn.battingCard.getValue(it.id).batted }.forEach { p ->
            val b = inn.battingCard.getValue(p.id)
            val onCrease = !b.out && (p.id == inn.strikerId || p.id == inn.nonStrikerId)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text((if (onCrease && p.id == inn.strikerId) "▸ " else "") + b.name, color = if (onCrease) Amber else Chalk, modifier = Modifier.weight(1f))
                Text("${b.runs} (${b.balls})", color = Chalk)
            }
            if (b.out) Text(b.howOut.orEmpty() + (b.outBy?.let { " b $it" } ?: ""), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text("Extras: ${inn.extras.total} (wd ${inn.extras.wide}, nb ${inn.extras.noball}, b ${inn.extras.bye}, lb ${inn.extras.legbye})", color = Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        SectionLabel("Bowling")
        bowlingTeam.players.filter { inn.bowlingCard.getValue(it.id).ballsBowled > 0 }.forEach { p ->
            val bw = inn.bowlingCard.getValue(p.id)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bw.name, color = if (p.id == inn.bowlerId) Amber else Chalk, modifier = Modifier.weight(1f))
                Text("${bw.wickets}/${bw.runsConceded} (${bw.overs})", color = Chalk)
            }
        }
    }
}

@Composable
private fun BreakScreen(vm: ScorerViewModel) {
    val i1 = vm.innings[0]
    val t1 = if (i1.battingTeamKey == "A") vm.teamA else vm.teamB
    val t2 = if (i1.bowlingTeamKey == "A") vm.teamA else vm.teamB
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Innings Break", color = Amber, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Panel {
            Text("${i1.totalRuns}/${i1.wickets}", color = Amber, style = MaterialTheme.typography.displayLarge)
            Text("${t1.name} · ${i1.oversDisplay} overs", color = Muted)
            Spacer(Modifier.height(8.dp))
            Text("${t2.name} need ${i1.totalRuns + 1} to win", color = Chalk)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.screen = Screen.OPENERS_2 }, modifier = Modifier.fillMaxWidth()) { Text("Start 2nd Innings →") }
    }
}

@Composable
private fun ResultScreen(vm: ScorerViewModel) {
    val i1 = vm.innings[0]; val i2 = vm.innings[1]
    val t1 = if (i1.battingTeamKey == "A") vm.teamA else vm.teamB
    val t2 = if (i2.battingTeamKey == "A") vm.teamA else vm.teamB
    val resultText = when {
        i2.target != null && i2.totalRuns >= i2.target -> "${t2.name} won by ${10 - i2.wickets} wicket(s)"
        i2.target != null && i2.totalRuns == i2.target - 1 -> "Match tied"
        else -> "${t1.name} won by ${(i2.target ?: 1) - 1 - i2.totalRuns} run(s)"
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                Text("🏆 $resultText", color = Amber, style = MaterialTheme.typography.headlineMedium)
                Text("${t1.name} ${i1.totalRuns}/${i1.wickets} (${i1.oversDisplay})  ·  ${t2.name} ${i2.totalRuns}/${i2.wickets} (${i2.oversDisplay})", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        item { ScorecardPanel(i1, t1, t2, title = "Innings 1 — ${t1.name}") }
        item { ScorecardPanel(i2, t2, t1, title = "Innings 2 — ${t2.name}") }
        item { Button(onClick = { vm.newMatch() }, modifier = Modifier.fillMaxWidth()) { Text("Start New Match") } }
    }
}
