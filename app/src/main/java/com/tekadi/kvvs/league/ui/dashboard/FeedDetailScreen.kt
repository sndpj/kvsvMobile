package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tekadi.kvvs.league.ui.theme.*

/**
 * Feature request: "clicked on feed it should redirect on detail page screen" — view-only.
 * KvsvRequest1.2 (Feeds fixes) #5/#7: edit/delete used to also live here, duplicating
 * FeedPostViewModel's own copy of the same logic for the same feed — that's now consolidated
 * into the Post Feed screen exclusively, so this screen is purely for reading a feed a user
 * tapped from the Dashboard carousel. Owners see a pointer to where to manage it instead of a
 * second edit form.
 */
@Composable
fun FeedDetailScreen(
    feedId: Long, vm: FeedDetailViewModel = viewModel(),
    onBack: () -> Unit, onViewMatch: (matchId: Long) -> Unit,
) {
    LaunchedEffect(feedId) { vm.load(feedId) }

    Surface(color = VoidBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                Text("Feed", color = ArcTeal, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))

            if (vm.loading && vm.feed == null) { PageLoader(color = ArcTeal); return@Column }
            if (vm.error != null && vm.feed == null) {
                RetryPanel(message = vm.error!!, accentColor = ArcTeal, textColor = TextPrimary, onRetry = { vm.load(feedId) })
                return@Column
            }

            val feed = vm.feed ?: return@Column

            Surface(color = GraphitePanel, shape = heroPanelShape(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (feed.type == "MATCH_SUMMARY") "MATCH" else "ANNOUNCEMENT", color = ArcTealBright, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))

                    Text(feed.message, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    if (feed.imageUrls.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        ImageSlideshow(feed.imageUrls)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(feed.createdByName?.let { "Posted by $it" }, feed.createdAt).joinToString(" · "),
                        color = TextMuted, style = MaterialTheme.typography.labelSmall,
                    )

                    if (feed.matchId != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { onViewMatch(feed.matchId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("View Match")
                        }
                    }

                    if (vm.isOwner) {
                        Spacer(Modifier.height(12.dp))
                        Text("Manage this post from the Post Feed screen", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Feature request: "for feeds user can select multiple images it should visible in slide show
 * in details pages." HorizontalPager (part of foundation, no extra dependency) with a dot
 * indicator — swipe between photos rather than a static grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageSlideshow(imageUrls: List<String>) {
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })
    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(200.dp)) { page ->
            AsyncImage(
                model = imageUrls[page], contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(heroPanelShape()),
            )
        }
        if (imageUrls.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(imageUrls.size) { idx ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (idx == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (idx == pagerState.currentPage) ArcTeal else HudLine),
                    )
                }
            }
        }
    }
}
