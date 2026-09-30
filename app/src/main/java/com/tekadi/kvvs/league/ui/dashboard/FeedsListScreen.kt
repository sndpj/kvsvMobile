package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.ui.theme.*
import com.tekadi.kvvs.league.util.MultiImagePickerButton

/**
 * KvsvRequest1.1 #7: "In Feeds screen show feeds list, show user can select and update from
 * the same screen. It should be display below of selected feed." Every feed renders as a row;
 * tapping one (if you own it) expands an inline editor directly under that row — no navigation,
 * no separate page. Tapping a different feed swaps the editor to it; tapping the same one again
 * collapses it.
 */
@Composable
fun FeedsListScreen(vm: FeedsListViewModel = viewModel(), onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val contentResolver = LocalContext.current.contentResolver

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Feeds", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))

            if (vm.loading && vm.feeds.isEmpty() && vm.error == null) {
                PageLoader(color = ArcTeal)
            } else if (vm.error != null && vm.feeds.isEmpty()) {
                RetryPanel(message = vm.error!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.load() })
            } else {
                if (vm.error != null) {
                    Text(vm.error!!, color = InfinityRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.feeds, key = { it.id }) { feed ->
                        Column {
                            FeedRow(
                                feed = feed,
                                selected = vm.selectedFeedId == feed.id,
                                canEdit = vm.isOwner(feed),
                                onClick = { if (vm.isOwner(feed)) vm.toggleSelect(feed) },
                            )
                            // "display below of selected feed" — the editor is a sibling item
                            // directly under this row's own Column, not a separate screen.
                            if (vm.selectedFeedId == feed.id) {
                                Spacer(Modifier.height(8.dp))
                                InlineFeedEditor(
                                    vm = vm,
                                    feed = feed,
                                    onUpload = { uris -> vm.uploadAndAppend(contentResolver, uris) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(feed: FeedResponse, selected: Boolean, canEdit: Boolean, onClick: () -> Unit) {
    Surface(
        shape = heroPanelShape(), color = if (selected) GraphitePanelAlt else GraphitePanel,
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (feed.type == "MATCH_SUMMARY") "MATCH" else "ANNOUNCEMENT",
                    color = ArcTealBright, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                )
                if (canEdit) Text(if (selected) "Editing ▾" else "Tap to edit", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(feed.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(feed.createdByName?.let { "Posted by $it" }, feed.createdAt).joinToString(" · "),
                color = TextMuted, style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun InlineFeedEditor(vm: FeedsListViewModel, feed: FeedResponse, onUpload: (List<android.net.Uri>) -> Unit) {
    Surface(shape = heroPanelShape(), color = CardSurfaceAlt, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = vm.editMessage, onValueChange = { vm.editMessage = it },
                label = { Text("Message") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (vm.editImageUrls.isNotEmpty()) {
                Text("${vm.editImageUrls.size} photo(s) attached", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
            }
            MultiImagePickerButton(label = "Add photos", onPicked = onUpload)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.save() }, enabled = !vm.saving, modifier = Modifier.weight(1f)) {
                    Text(if (vm.saving) "Saving…" else "Save")
                }
                OutlinedButton(onClick = { vm.delete(feed) }, enabled = !vm.saving, modifier = Modifier.weight(1f)) {
                    Text("Delete", color = InfinityRed)
                }
                TextButton(onClick = { vm.cancelEdit() }) { Text("Cancel", color = TextMuted) }
            }
        }
    }
}
