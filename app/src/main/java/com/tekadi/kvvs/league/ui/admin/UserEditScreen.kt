package com.tekadi.kvvs.league.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.RoleOptionResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast

/**
 * Feature: Super Admin role assign/revoke.
 * "After click on edit icon Show all the user details with roles drop down, from there super
 * admin can update the role, and reset the token for that particular user so he should be login
 * again with new role."
 */
@Composable
fun UserEditScreen(userId: Long, vm: UserEditViewModel = viewModel(), onBack: () -> Unit) {
    ErrorDialog(vm.error) { vm.error = null }
    InfoToast(vm.info) { vm.info = null }
    LaunchedEffect(userId) { vm.load(userId) }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Edit User", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))

            if (vm.loading) PageLoader(color = ArcTeal)

            val user = vm.user
            if (!vm.loading && user == null && vm.error != null) {
                RetryPanel(message = vm.error!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.load(userId) })
            } else if (user != null) {
                UserDetailCard(user.fullName, user.email, user.phone, user.registeredAt, user.active)
                Spacer(Modifier.height(16.dp))

                Text("ROLE", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                RoleDropdown(
                    roles = vm.roles,
                    selectedRoleId = vm.selectedRoleId,
                    onSelect = { vm.selectedRoleId = it },
                )

                val isSelf = CurrentUser.userId != null && CurrentUser.userId == userId
                if (isSelf) {
                    Spacer(Modifier.height(8.dp))
                    Text("You cannot change your own role from this screen.", color = InfinityRed, style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Saving resets this user's session — they'll need to sign in again to pick up the new role.",
                    color = TextMuted, style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.saveRole(userId) {} },
                    enabled = !vm.saving && !isSelf,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.saving) "Saving…" else "Save Role") }
            }
        }
    }
}

@Composable
private fun UserDetailCard(fullName: String, email: String, phone: String?, registeredAt: String?, active: Boolean) {
    Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fullName, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                if (!active) {
                    Spacer(Modifier.width(6.dp))
                    Text("INACTIVE", color = InfinityRed, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(email, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Text(phone ?: "No phone on file", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            registeredAt?.let {
                Spacer(Modifier.height(4.dp))
                Text("Registered ${it.take(10)}", color = TextDisabled, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RoleDropdown(roles: List<RoleOptionResponse>, selectedRoleId: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = roles.find { it.id == selectedRoleId }?.let { userRoleLabel(it.name) } ?: "Select a role"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = roles.isNotEmpty()) {
            Text(selectedLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(userRoleLabel(role.name)) },
                    onClick = { onSelect(role.id); expanded = false },
                )
            }
        }
    }
}
