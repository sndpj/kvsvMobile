package com.tekadi.kvvs.league.ui.registration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog

private val ROLES = listOf("BATTER", "BOWLER", "WICKET_KEEPER", "ALL_ROUNDER")

/** Public — reachable from the login screen with no account needed. */
// FlowRow's stability varies by Compose Foundation version — @OptIn is always safe to include
// (a no-op if the API is already stable, required if it isn't), so it's applied unconditionally
// here rather than trying to pin down the exact version boundary.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RegistrationScreen(vm: RegistrationViewModel = viewModel(), onBack: () -> Unit) {
    ErrorDialog(vm.error) { vm.error = null }
    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                }
                Text("Player Registration", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Register to join the Master Player Pool. A Super Admin will review your details before you're added.",
                    color = TextMuted, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))

                if (vm.submitted) {
                    Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("✓ Registration submitted", color = ArcTealBright, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Thanks, ${vm.name.ifBlank { "there" }}! A Super Admin will review your submission and add you to the player pool once approved.",
                                color = TextMuted, style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.resetForAnother() }) { Text("Register another player") }
                                Button(onClick = onBack) { Text("Done") }
                            }
                        }
                    }
                    return@Column
                }

                Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = vm.name, onValueChange = { vm.name = it },
                            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = vm.jersey, onValueChange = { vm.jersey = it },
                                label = { Text("Jersey Number") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = vm.age, onValueChange = { vm.age = it },
                                label = { Text("Age") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("ROLE", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        // GAP FIX ("All Rounder option not showing properly"): a plain Row with
                        // 4 chips — one of them "ALL ROUNDER", the longest label — overflowed
                        // off-screen on narrower phones instead of wrapping. FlowRow wraps chips
                        // onto a second line whenever they don't fit, which is what actually
                        // makes this "support all mobile resolutions" rather than just happening
                        // to fit on whichever screen it was last checked on.
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ROLES.forEach { r ->
                                FilterChip(selected = vm.role == r, onClick = { vm.role = r }, label = { Text(r.replace("_", " ")) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = vm.mobileNumber, onValueChange = { vm.mobileNumber = it },
                            label = { Text("Mobile Number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = vm.email, onValueChange = { vm.email = it },
                            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.submit() }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                            if (vm.loading) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Submit Registration")
                            }
                        }
                    }
                }
            }
        }
    }
}
