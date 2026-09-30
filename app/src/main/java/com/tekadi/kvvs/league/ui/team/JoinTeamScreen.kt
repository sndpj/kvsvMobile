package com.tekadi.kvvs.league.ui.team

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.network.JoinTeamRequest
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast
import kotlinx.coroutines.launch

class JoinTeamViewModel(app: Application) : AndroidViewModel(app) {
    var code by mutableStateOf("")
    var playerName by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    var joinedTeamName by mutableStateOf<String?>(null)

    fun join() {
        if (code.isBlank()) { error = "Enter the invite code"; return }
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.joinTeam(JoinTeamRequest(code.trim().uppercase(), playerName.ifBlank { null }))
                if (res.isSuccessful) {
                    joinedTeamName = "Joined! You're now on the roster."
                    info = joinedTeamName
                } else error = when (res.code()) {
                    400 -> "Invalid, used, or expired invite code"
                    else -> "Couldn't join (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }
}

@Composable
fun JoinTeamScreen(vm: JoinTeamViewModel = viewModel(), onDone: () -> Unit, onCancel: () -> Unit) {
    ErrorDialog(vm.error) { vm.error = null }
    InfoToast(vm.info) { vm.info = null }
    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("🔗 Join a Team", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            Text("Enter the invite code your team manager shared with you", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(16.dp))

            Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = vm.code, onValueChange = { vm.code = it },
                        label = { Text("Invite code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vm.playerName, onValueChange = { vm.playerName = it },
                        label = { Text("Display name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (vm.joinedTeamName != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(vm.joinedTeamName!!, color = ArcTealBright)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (vm.joinedTeamName != null) {
                        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.join() }, enabled = !vm.loading, modifier = Modifier.weight(1f)) { Text("Join") }
                            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}
