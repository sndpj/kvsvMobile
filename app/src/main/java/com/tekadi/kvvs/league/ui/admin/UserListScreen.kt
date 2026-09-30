package com.tekadi.kvvs.league.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.network.UserSummaryResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog

/**
 * Feature: Super Admin role assign/revoke.
 * "Click on users tab super admin - redirect on registered user list. List item will contain
 * Name, Role, mobile number, emailId, Registration Date. and add Edit icon."
 */
@Composable
fun UserListScreen(vm: UserListViewModel = viewModel(), onBack: () -> Unit, onEditUser: (userId: Long) -> Unit) {
    ErrorDialog(vm.error) { vm.error = null }
    LaunchedEffect(Unit) { vm.load() }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Users", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                label = { Text("Search by name or mobile number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            if (vm.loading) PageLoader(color = ArcTeal)

            if (!vm.loading && vm.users.isEmpty()) {
                if (vm.error != null) {
                    RetryPanel(message = vm.error!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.retry() })
                } else {
                    Text(
                        if (vm.query.isBlank()) "No registered users yet." else "No users match \"${vm.query}\".",
                        color = TextMuted,
                    )
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.users, key = { it.id }) { user -> UserRow(user, onClick = { onEditUser(user.id) }) }
            }
        }
    }
}

@Composable
private fun UserRow(user: UserSummaryResponse, onClick: () -> Unit) {
    Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.fullName, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    if (!user.active) {
                        Spacer(Modifier.width(6.dp))
                        Text("INACTIVE", color = InfinityRed, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(userRoleLabel(user.roleName), color = roleColorFor(user.roleName), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text(user.phone ?: "No phone on file", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                Text(user.email, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                user.registeredAt?.let {
                    Text("Registered ${it.take(10)}", color = TextDisabled, style = MaterialTheme.typography.labelSmall)
                }
            }
            // Plain emoji glyph, not an Icons.* one — same reasoning as AppNav.kt's OFFLINE_DEMO
            // back button: material-icons-core isn't a confirmed transitive dependency of
            // material3 in this environment's (unverifiable) build, so this avoids that risk
            // entirely, consistent with every other "icon" already used across the dashboard's
            // ActionChips (emoji, not Icons.Filled.*).
            TextButton(onClick = onClick) {
                Text("✎ Edit", color = ArcTealBright)
            }
        }
    }
}
