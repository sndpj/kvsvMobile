package com.tekadi.kvvs.league.ui.dashboard

import com.tekadi.kvvs.league.network.FeedResponse

/**
 * KvsvRequest1.2 (Feeds fixes) acceptance criteria #2: pure logic extracted out of
 * FeedPostViewModel specifically so it's testable without Robolectric or a fake ApiService
 * (neither set up in this project yet — same reasoning as LiveMatchLogicTest). Everything here
 * is deterministic and has no Android/network dependency.
 */

/** Only the feed's own creator may edit/delete it — same rule the backend enforces independently. */
fun feedIsOwnedBy(feed: FeedResponse, currentUserId: Long?): Boolean =
    currentUserId != null && feed.createdByUserId == currentUserId

/**
 * What FeedPostViewModel.save() checks before calling the network at all. Takes the count of
 * still-uploading photos rather than the Uri list itself, so this stays plain-JUnit-testable —
 * android.net.Uri isn't usable outside an Android runtime.
 */
fun validateFeedForm(message: String, pendingUploadCount: Int): String? = when {
    message.isBlank() -> "Enter a message"
    pendingUploadCount > 0 -> "Wait for photos to finish uploading"
    else -> null
}

/** A freshly-posted feed goes to the top of the list — newest first, matching the API's own ordering. */
fun feedsAfterCreate(feeds: List<FeedResponse>, created: FeedResponse): List<FeedResponse> =
    listOf(created) + feeds

/** Swap the one edited entry in place; every other feed, and the list's order, is untouched. */
fun feedsAfterUpdate(feeds: List<FeedResponse>, updated: FeedResponse): List<FeedResponse> =
    feeds.map { if (it.id == updated.id) updated else it }

fun feedsAfterDelete(feeds: List<FeedResponse>, deletedId: Long): List<FeedResponse> =
    feeds.filter { it.id != deletedId }
