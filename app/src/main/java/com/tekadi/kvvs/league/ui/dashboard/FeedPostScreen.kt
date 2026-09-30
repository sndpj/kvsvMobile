package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.ErrorDialog
import com.tekadi.kvvs.league.util.InfoToast
import com.tekadi.kvvs.league.util.MultiImagePickerButton

/**
 * KvsvRequest1.2 (Feeds fixes): the post card at the top doubles as the edit card — tapping a
 * feed below loads it in (#3, #5); the list below the card shows every feed as message + date
 * (#4); delete asks for confirmation first (#5); success/error feedback is a plain-language
 * banner, not a raw HTTP code (acceptance criteria #3). This replaces the previous split across
 * FeedPostScreen (create-only) and FeedsListScreen (browse+edit, now removed) — one screen for
 * the whole feed lifecycle, reachable only from here (#6 — no separate "Manage Feeds" entry).
 */
@Composable
fun FeedPostScreen(vm: FeedPostViewModel = viewModel(), onBack: () -> Unit) {
    InfoToast(vm.successMessage) { vm.dismissSuccessMessage() }
    ErrorDialog(vm.error) { vm.dismissError() }
    LaunchedEffect(Unit) { vm.loadFeeds() }
    val contentResolver = LocalContext.current.contentResolver

    if (vm.pendingDelete != null) {
        val feed = vm.pendingDelete!!
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text("Delete this feed?") },
            text = { Text(feed.message, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = { vm.confirmDelete() }, enabled = !vm.saving) {
                    Text("Delete", color = InfinityRed)
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelDelete() }) { Text("Cancel") } },
        )
    }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("📣 Post Feed", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text("Visible to everyone on the dashboard", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(16.dp))

            PostCard(vm, contentResolver)

            Spacer(Modifier.height(20.dp))
            Text("ALL FEEDS", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))

            if (vm.loadingFeeds && vm.feeds.isEmpty() && vm.listError == null) {
                PageLoader(color = ArcTeal)
            } else if (vm.listError != null && vm.feeds.isEmpty()) {
                RetryPanel(message = vm.listError!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.loadFeeds() })
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.feeds, key = { it.id }) { feed ->
                        FeedRow(
                            feed = feed,
                            editing = vm.editingFeedId == feed.id,
                            canEdit = vm.isOwner(feed),
                            onClick = { if (vm.isOwner(feed)) vm.startEditing(feed) },
                            onDelete = { vm.requestDelete(feed) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(vm: FeedPostViewModel, contentResolver: android.content.ContentResolver) {
    val isEditing = vm.editingFeedId != null
    Surface(shape = heroPanelShape(), color = GraphitePanel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (isEditing) {
                Text("Editing feed", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = vm.message, onValueChange = { vm.message = it },
                label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            if (vm.imageUrls.isNotEmpty() || vm.pendingUris.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.imageUrls) { url ->
                        Box(Modifier.size(72.dp)) {
                            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().clip(heroBadgeShape()))
                            IconButton(onClick = { vm.removeImage(url) }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp)) {
                                Text("✕", color = TextOnDark, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(vm.pendingUris) { uri ->
                        Box(Modifier.size(72.dp)) {
                            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(heroBadgeShape()))
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ArcTeal, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            MultiImagePickerButton(label = "Add photos") { uris -> vm.addPicked(uris, contentResolver) }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.save() }, enabled = !vm.saving, modifier = Modifier.weight(1f)) {
                    Text(if (vm.saving) "Saving…" else if (isEditing) "Update" else "Post")
                }
                if (isEditing) {
                    OutlinedButton(onClick = { vm.startNewPost() }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(feed: FeedResponse, editing: Boolean, canEdit: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = heroPanelShape(), color = if (editing) GraphitePanelAlt else GraphitePanel,
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // KvsvRequest1.2 #4 — message as title, posted date underneath.
                Text(feed.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(feed.createdAt, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (canEdit) {
                IconButton(onClick = onDelete) { Text("🗑", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
