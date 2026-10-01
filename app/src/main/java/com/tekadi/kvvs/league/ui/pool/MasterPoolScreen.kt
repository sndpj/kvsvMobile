package com.tekadi.kvvs.league.ui.pool

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.PoolPlayerResponse
import com.tekadi.kvvs.league.network.RegistrationResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast

private val PLAYER_ROLES = listOf("BATTER", "BOWLER", "WICKET_KEEPER", "ALL_ROUNDER")

@Composable
fun MasterPoolScreen(
    vm: MasterPoolViewModel = viewModel(), onBack: () -> Unit,
    // Player profile (KvsvRequest1.3) — tap a pool player's name.
    onPlayerClick: (playerId: Long) -> Unit = {},
) {
    ErrorDialog(vm.error) { vm.error = null }
    InfoToast(vm.info) { vm.info = null }
    LaunchedEffect(Unit) {
        vm.load()
        vm.loadTournaments()
        if (CurrentUser.canManageMasterPool) vm.loadPendingRegistrations()
    }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Master Player Pool", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            val s = vm.status
            Text(
                if (s != null) "${s.playerCount} / ${s.capacity} players" else "Loading…",
                color = TextMuted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            if (vm.loading) PageLoader(color = ArcTeal)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (CurrentUser.canManageMasterPool && vm.pendingRegistrations.isNotEmpty()) {
                    item { PendingRegistrationsPanel(vm) }
                }
                if (CurrentUser.canManageMasterPool) {
                    item { AddPlayerPanel(vm) }
                }
                if (CurrentUser.canSplitPoolIntoTeams) {
                    item { SplitIntoTeamsPanel(vm) }
                }
                item { Text("POOL ROSTER", color = TextMuted, style = MaterialTheme.typography.labelSmall) }
                val players = s?.players.orEmpty()
                if (players.isEmpty() && !vm.loading) {
                    item { Text("No players in the pool yet.", color = TextMuted) }
                }
                items(players) { p ->
                    PoolPlayerRow(
                        player = p,
                        assignment = vm.assignments[p.id],
                        showAssignmentToggles = CurrentUser.canSplitPoolIntoTeams,
                        showRemove = CurrentUser.canManageMasterPool,
                        onAssign = { side -> vm.toggleAssignment(p.id, side) },
                        onRemove = { vm.removePlayer(p.id) },
                        onOpenProfile = { onPlayerClick(p.id) },
                    )
                }
            }
        }
    }
}

/** Super Admin only — review queue for public player self-registrations (approve moves them into the pool). */
@Composable
private fun PendingRegistrationsPanel(vm: MasterPoolViewModel) {
    Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Pending Registrations", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            vm.pendingRegistrations.forEach { reg -> PendingRegistrationRow(reg, vm) }
        }
    }
}

@Composable
private fun PendingRegistrationRow(reg: RegistrationResponse, vm: MasterPoolViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${reg.name}${reg.jerseyNumber?.let { " #$it" } ?: ""}", color = TextPrimary)
            Text(listOfNotNull(reg.role, reg.age?.let { "$it yrs" }).joinToString(" · "), color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { vm.approveRegistration(reg.id) }) { Text("Approve", color = ArcTealBright) }
        TextButton(onClick = { vm.rejectRegistration(reg.id) }) { Text("Reject", color = InfinityRed) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddPlayerPanel(vm: MasterPoolViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var jersey by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(PLAYER_ROLES[0]) }
    var mobileNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val contentResolver = androidx.compose.ui.platform.LocalContext.current.contentResolver

    Column {
        Button(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Cancel" else "+ Add Player to Pool")
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Surface(color = GraphitePanelAlt, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = jersey, onValueChange = { jersey = it }, label = { Text("Jersey #") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = mobileNumber, onValueChange = { mobileNumber = it }, label = { Text("Mobile Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))

                    // Feature request 3a: "profile pick single image selection" — replaces what
                    // used to be a plain "Photo URL" text field with a real device picker.
                    Text("PHOTO (OPTIONAL)", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (vm.pickedPhotoUri != null) {
                            Box(Modifier.size(48.dp)) {
                                coil.compose.AsyncImage(
                                    model = vm.pickedPhotoUri, contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(heroBadgeShape()),
                                )
                                if (vm.photoUploading) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        CircularProgressIndicator(color = ArcTeal, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        com.tekadi.kvvs.league.util.SingleImagePickerButton(label = if (vm.pickedPhotoUri == null) "Pick photo" else "Change photo") { uri ->
                            vm.pickPhoto(contentResolver, uri)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("ROLE", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    // GAP FIX ("All Rounder option not showing properly") — same overflow issue
                    // and fix as RegistrationScreen's role picker; see its comment.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PLAYER_ROLES.forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.replace("_", " ")) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.addPlayer(name, jersey.toIntOrNull(), age.toIntOrNull(), role, null, null, mobileNumber, email, vm.uploadedPhotoUrl) {
                                expanded = false; name = ""; jersey = ""; age = ""; mobileNumber = ""; email = ""; vm.resetPickedPhoto()
                            }
                        },
                        enabled = !vm.loading && !vm.photoUploading, modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (vm.photoUploading) "Uploading photo…" else "Add to Pool") }
                }
            }
        }
    }
}

@Composable
private fun SplitIntoTeamsPanel(vm: MasterPoolViewModel) {
    val aIds = vm.assignments.filterValues { it == PoolAssignment.TEAM_A }.keys.toList()
    val bIds = vm.assignments.filterValues { it == PoolAssignment.TEAM_B }.keys.toList()
    val poolPlayers = vm.status?.players.orEmpty()
    fun nameFor(id: Long) = poolPlayers.find { it.id == id }?.name ?: "Player #$id"

    Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Create 2 Teams From the Pool", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap A / B next to each pool player below to assign them, then name both teams. " +
                    "Tournament, captain, and vice-captain are required for each team.",
                color = TextMuted, style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(12.dp))

            Text("TOURNAMENT", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            SimpleDropdown(
                label = vm.tournaments.find { it.id == vm.selectedTournamentId }?.name ?: "Select tournament",
                options = vm.tournaments.map { it.id to it.name },
                onSelect = { vm.selectedTournamentId = it },
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = vm.teamAName, onValueChange = { vm.teamAName = it },
                label = { Text("Team A name (${aIds.size} picked)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (aIds.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SimpleDropdown(
                    label = vm.teamACaptainId?.let { "Captain: ${nameFor(it)}" } ?: "Select captain",
                    options = aIds.map { it to nameFor(it) },
                    onSelect = { vm.teamACaptainId = it },
                )
                Spacer(Modifier.height(6.dp))
                SimpleDropdown(
                    label = vm.teamAViceCaptainId?.let { "Vice-Captain: ${nameFor(it)}" } ?: "Select vice-captain",
                    options = aIds.map { it to nameFor(it) },
                    onSelect = { vm.teamAViceCaptainId = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.teamBName, onValueChange = { vm.teamBName = it },
                label = { Text("Team B name (${bIds.size} picked)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (bIds.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SimpleDropdown(
                    label = vm.teamBCaptainId?.let { "Captain: ${nameFor(it)}" } ?: "Select captain",
                    options = bIds.map { it to nameFor(it) },
                    onSelect = { vm.teamBCaptainId = it },
                )
                Spacer(Modifier.height(6.dp))
                SimpleDropdown(
                    label = vm.teamBViceCaptainId?.let { "Vice-Captain: ${nameFor(it)}" } ?: "Select vice-captain",
                    options = bIds.map { it to nameFor(it) },
                    onSelect = { vm.teamBViceCaptainId = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.splitIntoTeams() },
                enabled = !vm.loading && aIds.isNotEmpty() && bIds.isNotEmpty() &&
                    vm.teamAName.isNotBlank() && vm.teamBName.isNotBlank() &&
                    vm.selectedTournamentId != null &&
                    vm.teamACaptainId != null && vm.teamAViceCaptainId != null &&
                    vm.teamBCaptainId != null && vm.teamBViceCaptainId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create Both Teams") }

            vm.lastSplitResult?.let { result ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "✓ Created \"${result.teamA.name}\" (${result.teamAPlayers.size} players) and " +
                        "\"${result.teamB.name}\" (${result.teamBPlayers.size} players)",
                    color = ArcTealBright, style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Small reusable dropdown for single-selection (tournament / captain / vice-captain pickers above). */
@Composable
private fun SimpleDropdown(label: String, options: List<Pair<Long?, String>>, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable
private fun PoolPlayerRow(
    player: PoolPlayerResponse,
    assignment: PoolAssignment?,
    showAssignmentToggles: Boolean,
    showRemove: Boolean,
    onAssign: (PoolAssignment) -> Unit,
    onRemove: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpenProfile)) {
                Text("${player.name}${player.jerseyNumber?.let { " #$it" } ?: ""}", color = TextPrimary)
                Text(listOfNotNull(player.role, player.battingStyle, player.bowlingStyle).joinToString(" · "),
                    color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (showAssignmentToggles) {
                FilterChip(selected = assignment == PoolAssignment.TEAM_A, onClick = { onAssign(PoolAssignment.TEAM_A) }, label = { Text("A") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = assignment == PoolAssignment.TEAM_B, onClick = { onAssign(PoolAssignment.TEAM_B) }, label = { Text("B") })
            }
            if (showRemove) {
                TextButton(onClick = onRemove) { Text("Remove", color = InfinityRed) }
            }
        }
    }
}
