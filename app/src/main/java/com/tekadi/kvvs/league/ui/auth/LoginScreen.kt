package com.tekadi.kvvs.league.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel(), onLoggedIn: () -> Unit, onTryOfflineDemo: () -> Unit = {}, onRegisterAsPlayer: () -> Unit = {}) {

    ErrorDialog(vm.error) { vm.error = null }

    // Auto-advance if a stored session was found on launch.
    LaunchedEffect(vm.checkedExistingSession, vm.session) {
        if (vm.checkedExistingSession && vm.session != null) onLoggedIn()
    }

    Surface(color = PitchBg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            if (!vm.checkedExistingSession) {
                PageLoader(color = Amber)
                return@Box
            }
            Column {
                Text("🏏 Cricket Scorer", color = Amber, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text("Sign in to score live matches", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))

                Surface(color = PanelGreen, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = vm.email, onValueChange = { vm.email = it },
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = vm.password, onValueChange = { vm.password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { vm.login(onLoggedIn) },
                            enabled = !vm.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (vm.loading) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Sign in")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "New accounts default to Viewer (read-only). Ask a Tournament Admin to " +
                        "promote you to Scorer to score matches.",
                    color = Muted, style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onTryOfflineDemo) {
                    Text("Try the offline demo instead (no account needed)", color = InfoBlue)
                }
                TextButton(onClick = onRegisterAsPlayer) {
                    Text("Register as a player (no account needed)", color = InfoBlue)
                }
            }
        }
    }
}
