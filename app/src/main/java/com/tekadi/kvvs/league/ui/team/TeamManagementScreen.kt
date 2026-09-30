package com.tekadi.kvvs.league.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.network.PlayerResponse
import com.tekadi.kvvs.league.network.PoolPlayerResponse
import com.tekadi.kvvs.league.network.TeamResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast
import com.tekadi.kvvs.league.util.SingleImagePickerButton

private val PLAYER_ROLES = listOf("BATTER", "BOWLER", "WICKET_KEEPER", "ALL_ROUNDER")
private val BATTING_STYLES = listOf("RIGHT_HAND", "LEFT_HAND")
private val BOWLING_STYLES = listOf("RIGHT_ARM_FAST", "MEDIUM", "SPIN", "LEFT_ARM_SPIN")

@Composable
fun TeamManagementScreen(vm: TeamManagementViewModel = viewModel(), onBack: () -> Unit) {
    ErrorDialog(vm.error) { vm.error = null }
    InfoToast(vm.info) { vm.info = null }
    LaunchedEffect(Unit) { vm.loadTournaments(); vm.loadPoolPlayers() }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = vm.selectedTeam?.name ?: vm.selectedTournament?.name ?: "Team Management"
                TextButton(onClick = {
                    when {
                        vm.selectedTeam != null -> vm.selectedTeam = null
                        vm.selectedTournament != null -> vm.selectedTournament = null
                        else -> onBack()
                    }
                }) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text(title, color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))

            if (vm.loading) PageLoader(color = ArcTeal)

            when {
                vm.selectedTeam != null -> TeamDetail(vm)
                vm.selectedTournament != null -> TeamList(vm)
                else -> TournamentPicker(vm)
            }
        }
    }
}

@Composable
private fun TournamentPicker(vm: TeamManagementViewModel) {
    if (vm.tournaments.isEmpty() && !vm.loading) {
        Text("No tournaments yet.", color = TextMuted)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vm.tournaments) { t ->
            Surface(color = GraphitePanel, shape = heroPanelShape(), onClick = { vm.selectTournament(t) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t.name, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(t.matchFormat, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TeamList(vm: TeamManagementViewModel) {
    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var coachName by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf("") }
    var captainId by remember { mutableStateOf<Long?>(null) }
    var viceCaptainId by remember { mutableStateOf<Long?>(null) }
    val contentResolver = LocalContext.current.contentResolver

    Column {
        Button(onClick = { showAddForm = !showAddForm }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAddForm) "Cancel" else "+ New Team")
        }
        if (showAddForm) {
            Spacer(Modifier.height(8.dp))
            Surface(color = GraphitePanelAlt, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Team name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))

                    // GAP FIX ("add logo instead of URL like add photo in feed") — was a plain
                    // "Logo URL" text field; now a real device picker, same pattern as the feed
                    // and profile-photo pickers. If nothing is picked, TeamLogo below falls back
                    // to a default initial badge instead of leaving the field a bare text input.
                    Text("TEAM LOGO (OPTIONAL)", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (vm.pickedLogoUri != null) {
                            Box(Modifier.size(48.dp)) {
                                coil.compose.AsyncImage(
                                    model = vm.pickedLogoUri, contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(heroBadgeShape()),
                                )
                                if (vm.logoUploading) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = ArcTeal, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        SingleImagePickerButton(label = if (vm.pickedLogoUri == null) "Pick logo" else "Change logo") { uri ->
                            vm.pickLogo(contentResolver, uri)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Candidates come from the Master Player Pool — a new team has no roster of
                    // its own yet, so captain/vice-captain are picked from unassigned players and
                    // moved onto this team's roster when it's created (see TeamService.create).
                    if (vm.poolPlayers.isEmpty()) {
                        Text("No players in the Master Player Pool to pick a captain/vice-captain from — add some first.",
                            color = InfinityRed, style = MaterialTheme.typography.labelSmall)
                    }
                    PlayerIdDropdown(
                        label = vm.poolPlayers.find { it.id == captainId }?.name?.let { "Captain: $it" } ?: "Captain",
                        options = vm.poolPlayers, onSelect = { captainId = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    PlayerIdDropdown(
                        label = vm.poolPlayers.find { it.id == viceCaptainId }?.name?.let { "Vice-captain: $it" } ?: "Vice-captain",
                        options = vm.poolPlayers, onSelect = { viceCaptainId = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = coachName, onValueChange = { coachName = it }, label = { Text("Coach (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = contactNumber, onValueChange = { contactNumber = it }, label = { Text("Contact number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.createTeam(name, vm.uploadedLogoUrl, captainId, viceCaptainId, coachName, contactNumber) {
                                showAddForm = false; name = ""; captainId = null; viceCaptainId = null; vm.resetPickedLogo()
                            }
                        },
                        enabled = !vm.loading && !vm.logoUploading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (vm.logoUploading) "Uploading logo…" else "Add Team") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.teams.isEmpty() && !vm.loading) {
            Text("No teams in this tournament yet.", color = TextMuted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.teams) { team ->
                Surface(color = GraphitePanel, shape = heroPanelShape(), onClick = { vm.openTeam(team) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TeamLogo(team)
                            Column {
                                Text(team.name, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                Text("Captain: ${team.captainName ?: "—"} · Vice-captain: ${team.viceCaptainName ?: "—"}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TextButton(onClick = { vm.deleteTeam(team) }) { Text("Remove", color = InfinityRed) }
                    }
                }
            }
        }
    }
}

/**
 * Feature request: "add default team logo if not selected." Rather than persisting a fake
 * placeholder URL on the backend (which breaks the moment that asset moves, and isn't really a
 * "logo" at all), this renders a colored initial badge purely client-side whenever
 * team.logoUrl is null — the same pattern contact/mail apps use for accounts with no avatar
 * photo. No new image asset needed, and it's automatically theme-consistent.
 */
@Composable
private fun TeamLogo(team: TeamResponse) {
    if (team.logoUrl != null) {
        coil.compose.AsyncImage(
            model = team.logoUrl, contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
    } else {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(TekadiGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                teamInitial(team.name),
                color = TextOnDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/**
 * Pulled out as a plain function (not inlined into the composable) specifically so it's
 * unit-testable — see TeamLogoTest.kt. This project has no Compose UI-testing infrastructure
 * (Robolectric/instrumentation) set up, so the composable rendering itself isn't covered by an
 * executable test; this is the one piece of genuine logic inside it that can be.
 */
fun teamInitial(teamName: String): String = teamName.trim().firstOrNull()?.uppercase() ?: "?"

@Composable
private fun PlayerIdDropdown(label: String, options: List<PoolPlayerResponse>, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = options.isNotEmpty()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { onSelect(p.id); expanded = false }) }
        }
    }
}

@Composable
private fun TeamDetail(vm: TeamManagementViewModel) {
    var showAddPlayer by remember { mutableStateOf(false) }
    var pName by remember { mutableStateOf("") }
    var pJersey by remember { mutableStateOf("") }
    var pAge by remember { mutableStateOf("") }
    var pRole by remember { mutableStateOf(PLAYER_ROLES[0]) }
    var pBatting by remember { mutableStateOf<String?>(null) }
    var pBowling by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Invite Players", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    val invite = vm.latestInvite
                    if (invite != null) {
                        Text(invite.code, color = RepulsorGold, fontSize = 28.sp)
                        Text("Share this code — expires ${invite.expiresAt.take(10)}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = { vm.generateInvite() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (invite == null) "Generate Invite Code" else "Generate New Code")
                    }
                }
            }
        }

        item {
            Button(onClick = { showAddPlayer = !showAddPlayer }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAddPlayer) "Cancel" else "+ Add Player")
            }
        }
        if (showAddPlayer) {
            item {
                Surface(color = GraphitePanelAlt, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = pJersey, onValueChange = { pJersey = it }, label = { Text("Jersey #") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = pAge, onValueChange = { pAge = it }, label = { Text("Age") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        ChipPicker("Role", PLAYER_ROLES, pRole) { pRole = it }
                        Spacer(Modifier.height(8.dp))
                        ChipPicker("Batting style", BATTING_STYLES, pBatting) { pBatting = it }
                        Spacer(Modifier.height(8.dp))
                        ChipPicker("Bowling style", BOWLING_STYLES, pBowling) { pBowling = it }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                vm.addPlayer(pName, pJersey.toIntOrNull(), pAge.toIntOrNull(), pRole, pBatting, pBowling) {
                                    showAddPlayer = false; pName = ""; pJersey = ""; pAge = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add Player") }
                    }
                }
            }
        }

        item { Text("ROSTER", color = TextMuted, style = MaterialTheme.typography.labelSmall) }
        if (vm.players.isEmpty()) {
            item { Text("No players yet.", color = TextMuted) }
        }
        items(vm.players) { p -> PlayerRow(p, onRemove = { vm.removePlayer(p) }) }
    }
}

@Composable
private fun PlayerRow(p: PlayerResponse, onRemove: () -> Unit) {
    Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${p.name}${p.jerseyNumber?.let { " #$it" } ?: ""}", color = TextPrimary)
                Text(listOfNotNull(p.role, p.battingStyle, p.bowlingStyle).joinToString(" · "), color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onRemove) { Text("Remove", color = InfinityRed) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipPicker(label: String, options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column {
        Text(label.uppercase(), color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        // GAP FIX ("All Rounder option not showing properly") — same overflow issue as the
        // Registration/MasterPool role pickers; this shared ChipPicker is used for exactly the
        // same PLAYER_ROLES list (plus batting/bowling styles) in the add-player form here.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { opt ->
                FilterChip(selected = selected == opt, onClick = { onSelect(opt) }, label = { Text(opt.replace("_", " ")) })
            }
        }
    }
}
