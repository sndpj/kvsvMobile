package com.tekadi.kvvs.league.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
        // Scrollable: the image gallery can be taller than the screen with several photos.
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
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

                    FeedMessageText(feed, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    if (feed.imageUrls.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        ImageGallery(feed.imageUrls)
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
 * Feature request: feed images shown as a gallery of same-size cards (2 per row; a lone image
 * gets a single full-width card) instead of a slideshow. Tapping a card opens [ImageViewer]
 * at that image. Rows rather than a lazy grid because this screen already scrolls vertically
 * and a lazy grid can't be nested inside an unbounded-height scroller.
 */
@Composable
private fun ImageGallery(imageUrls: List<String>) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val columns = if (imageUrls.size == 1) 1 else 2

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        imageUrls.withIndex().chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (idx, url) ->
                    AsyncImage(
                        model = url, contentDescription = "Photo ${idx + 1} of ${imageUrls.size}. Tap to enlarge",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f)
                            .aspectRatio(if (columns == 1) 16f / 9f else 1f)
                            .clip(heroPanelShape())
                            .background(GraphitePanel)
                            .clickable { viewerIndex = idx },
                    )
                }
                // Keep a trailing odd card the same size as the others.
                if (row.size < columns) Spacer(Modifier.weight((columns - row.size).toFloat()))
            }
        }
    }

    viewerIndex?.let { ImageViewer(imageUrls, it) { viewerIndex = null } }
}

/**
 * Full-screen viewer: swipe between photos, pinch to zoom (1x-5x), drag to pan when zoomed,
 * double-tap to toggle zoom, tap (at 1x) to close. Paging is disabled while the current photo
 * is zoomed so panning doesn't flip pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageViewer(imageUrls: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { imageUrls.size })
        var zoomed by remember { mutableStateOf(false) }

        // A page change resets the new page to 1x, so unlock paging.
        LaunchedEffect(pagerState.currentPage) { zoomed = false }

        Box(Modifier.fillMaxSize().background(VoidBlack)) {
            HorizontalPager(
                state = pagerState, userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    url = imageUrls[page],
                    isCurrent = pagerState.currentPage == page,
                    onZoomChanged = { if (pagerState.currentPage == page) zoomed = it },
                    onSingleTap = onDismiss,
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Text("Close ✕", color = TextPrimary)
            }
            Text(
                when {
                    zoomed -> "Double-tap to reset"
                    imageUrls.size > 1 -> "${pagerState.currentPage + 1} / ${imageUrls.size} · Pinch to zoom"
                    else -> "Pinch to zoom"
                },
                color = TextMuted, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@Composable
private fun ZoomableImage(url: String, isCurrent: Boolean, onZoomChanged: (Boolean) -> Unit, onSingleTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Swiping away from a zoomed photo leaves it at 1x when the user comes back.
    LaunchedEffect(isCurrent) { if (!isCurrent) { scale = 1f; offset = Offset.Zero } }

    fun clampOffset(o: Offset, s: Float, w: Float, h: Float): Offset {
        val maxX = (w * (s - 1f)) / 2f
        val maxY = (h * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (scale <= 1f) onSingleTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                            // Zoom toward the tapped point.
                            val c = Offset(size.width / 2f, size.height / 2f)
                            offset = clampOffset((c - tap) * (scale - 1f), scale, size.width.toFloat(), size.height.toFloat())
                        }
                        onZoomChanged(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                // Custom transform loop instead of detectTransformGestures: that one consumes
                // every drag, which would stop a one-finger swipe reaching the pager at 1x.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            // Unspecified when no pointer was pressed in the previous event
                            // (e.g. a finger just touched down); nothing to transform yet.
                            if (centroid.isUnspecified) continue
                            val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val c = Offset(w / 2f, h / 2f)
                            // Keep the point under the fingers fixed while scaling, then apply pan.
                            val scaled = offset + (centroid - c - offset) * (1f - newScale / scale)
                            offset = if (newScale <= 1f) Offset.Zero else clampOffset(scaled + pan, newScale, w, h)
                            scale = newScale
                            onZoomChanged(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        )
    }
}
