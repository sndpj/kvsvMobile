package com.tekadi.kvvs.league.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tekadi.kvvs.league.network.PlayerProfileResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast

/**
 * Player profile (KvsvRequest1.3): who they are (photo, role, styles, description), how many
 * matches and for which teams, Player of the Match awards, and career batting/bowling. Super
 * Admin gets an Edit button (#4).
 */
@Composable
fun PlayerProfileScreen(playerId: Long, vm: PlayerProfileViewModel = viewModel(), onBack: () -> Unit, onMatchClick: (Long) -> Unit) {
    LaunchedEffect(playerId) { vm.load(playerId) }
    ErrorDialog(if (vm.profile != null) vm.error else null) { vm.error = null }
    InfoToast(vm.info) { vm.info = null }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Player Profile", color = ArcTeal, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (vm.canEdit && vm.editForm == null) {
                    TextButton(onClick = { vm.startEdit() }) { Text("Edit", color = ArcTealBright) }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (vm.loading && vm.profile == null) { PageLoader(color = ArcTeal); return@Column }
            if (vm.error != null && vm.profile == null) {
                RetryPanel(message = vm.error!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.load(playerId) })
                return@Column
            }
            val p = vm.profile ?: return@Column

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { HeaderCard(p) }
                vm.editForm?.let { form -> item { EditCard(vm, form) } }
                item { MatchesCard(p) }
                item { AwardsCard(p, onMatchClick) }
                item { CareerCard(p) }
            }
        }
    }
}

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = heroPanelShape(), color = GraphitePanel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text.uppercase(), color = TextMuted, style = MaterialTheme.typography.labelSmall)

@Composable
private fun HeaderCard(p: PlayerProfileResponse) = ProfileCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val photo = p.photoUrl?.takeIf { it.isNotBlank() }
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = p.name, contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(64.dp).clip(CircleShape).background(TekadiGreen), contentAlignment = Alignment.Center) {
                Text(p.name.trim().firstOrNull()?.uppercase() ?: "?", color = TextOnDark, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            Text(profileSubtitle(p), color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Matches", p.matchesPlayed.toString(), Modifier.weight(1f))
        StatTile("Player of the Match", p.playerOfMatchCount.toString(), Modifier.weight(1f))
        StatTile("Teams", p.teams.count { it.matches > 0 }.toString(), Modifier.weight(1f))
    }
    if (!p.bio.isNullOrBlank()) {
        Spacer(Modifier.height(12.dp))
        SectionLabel("About")
        Spacer(Modifier.height(4.dp))
        Text(p.bio, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(color = GraphitePanelAlt, shape = heroPanelShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = ArcTealBright, style = MaterialTheme.typography.titleLarge)
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun MatchesCard(p: PlayerProfileResponse) = ProfileCard {
    SectionLabel("Teams")
    Text(matchesSummary(p), color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    if (p.teams.isEmpty()) {
        Text("Not on any team yet", color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
    p.teams.forEach { t ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(t.teamName, color = TextPrimary)
                t.tournamentName?.let { Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall) }
            }
            Text("${t.matches} match${if (t.matches == 1) "" else "es"}", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AwardsCard(p: PlayerProfileResponse, onMatchClick: (Long) -> Unit) = ProfileCard {
    SectionLabel("Player of the Match (${p.playerOfMatchCount})")
    if (p.playerOfMatchAwards.isEmpty()) {
        Text("No awards yet", color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
    p.playerOfMatchAwards.forEach { a ->
        Column(Modifier.fillMaxWidth().clickable { onMatchClick(a.matchId) }.padding(vertical = 6.dp)) {
            Text("🏅 ${a.teamAName} vs ${a.teamBName}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(a.matchDate, a.resultSummary).joinToString(" · "), color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CareerCard(p: PlayerProfileResponse) = ProfileCard {
    val b = p.batting
    val w = p.bowling
    SectionLabel("Batting")
    Text(battingHeadline(b), color = TextPrimary)
    StatLine("Average", formatAverage(b.average), "Strike rate", formatRate(b.strikeRate))
    StatLine("50s / 100s", "${b.fifties} / ${b.hundreds}", "4s / 6s", "${b.fours} / ${b.sixes}")
    Spacer(Modifier.height(10.dp))
    SectionLabel("Bowling")
    Text("${w.wickets} wickets in ${w.innings} inn${w.bestFigures?.let { " · Best $it" } ?: ""}", color = TextPrimary)
    StatLine("Overs", w.overs, "Economy", formatRate(w.economy))
    StatLine("Average", formatAverage(w.average), "Maidens", w.maidens.toString())
}

@Composable
private fun StatLine(l1: String, v1: String, l2: String, v2: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("$l1: ", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(v1, color = TextPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text("$l2: ", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(v2, color = TextPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
    }
}

/** KvsvRequest1.3 #4 — Super Admin edits player details. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCard(vm: PlayerProfileViewModel, form: PlayerEditForm) = ProfileCard {
    SectionLabel("Edit player")
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(value = form.name, onValueChange = { vm.editForm = form.copy(name = it) },
        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = form.jerseyNumber, onValueChange = { v -> vm.editForm = form.copy(jerseyNumber = v.filter(Char::isDigit).take(3)) },
            label = { Text("Jersey #") }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = form.age, onValueChange = { v -> vm.editForm = form.copy(age = v.filter(Char::isDigit).take(3)) },
            label = { Text("Age") }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    Spacer(Modifier.height(6.dp))
    OptionChips("Role", PLAYER_ROLE_OPTIONS, form.role, ::roleLabel) { vm.editForm = form.copy(role = it) }
    OptionChips("Batting style", BATTING_STYLE_OPTIONS, form.battingStyle, { battingStyleLabel(it) ?: it }) {
        vm.editForm = form.copy(battingStyle = if (form.battingStyle == it) null else it)
    }
    OptionChips("Bowling style", BOWLING_STYLE_OPTIONS, form.bowlingStyle, { bowlingStyleLabel(it) ?: it }) {
        vm.editForm = form.copy(bowlingStyle = if (form.bowlingStyle == it) null else it)
    }
    OutlinedTextField(value = form.bio, onValueChange = { vm.editForm = form.copy(bio = it.take(BIO_MAX)) },
        label = { Text("About the player") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("${form.bio.trim().length}/$BIO_MAX") })
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.saveEdit() }, enabled = !vm.saving && editFormError(form) == null, modifier = Modifier.weight(1f)) {
            Text(if (vm.saving) "Saving…" else "Save")
        }
        OutlinedButton(onClick = { vm.cancelEdit() }, enabled = !vm.saving, modifier = Modifier.weight(1f)) { Text("Cancel") }
    }
    editFormError(form)?.let { Text(it, color = InfinityRed, style = MaterialTheme.typography.labelSmall) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(label: String, options: List<String>, selected: String?, display: (String) -> String, onSelect: (String) -> Unit) {
    Text(label.uppercase(), color = TextMuted, style = MaterialTheme.typography.labelSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt ->
            FilterChip(selected = selected == opt, onClick = { onSelect(opt) }, label = { Text(display(opt)) })
        }
    }
    Spacer(Modifier.height(6.dp))
}
