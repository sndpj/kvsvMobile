package com.tekadi.kvvs.league.ui.dashboard

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.CreateFeedRequest
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.UpdateFeedRequest
import com.tekadi.kvvs.league.util.ImageUpload
import kotlinx.coroutines.launch

/**
 * KvsvRequest1.2 (Feeds fixes) #3–#6: one screen owns the whole feed lifecycle now — post,
 * browse, edit, delete — instead of splitting create (FeedPostScreen), browse+edit
 * (FeedsListScreen, now removed) and view+edit (FeedDetailScreen) across three places. The same
 * card UI is reused for both posting a new feed and editing an existing one (`editingFeedId`
 * null vs non-null is the only difference) — that's the "reuse the post feed cart for edit"
 * request; there is deliberately only one form in this file, not two.
 */
class FeedPostViewModel(app: Application) : AndroidViewModel(app) {

    // ---- the list (KvsvRequest1.2 #3/#4) ----
    var feeds by mutableStateOf<List<FeedResponse>>(emptyList())
    var loadingFeeds by mutableStateOf(false)
    var listError by mutableStateOf<String?>(null)

    // ---- the shared card: message + images ----
    var message by mutableStateOf("")
    // Ready-to-submit image URLs — for a new post these arrive only via upload; for an edit,
    // the existing feed's own imageUrls seed this list directly (they're already URLs, nothing
    // to upload). pendingUris tracks local picks still mid-upload so both cases share one field.
    var imageUrls by mutableStateOf<List<String>>(emptyList())
    var pendingUris by mutableStateOf<List<Uri>>(emptyList())
    var editingFeedId by mutableStateOf<Long?>(null)
        private set
    var saving by mutableStateOf(false)

    // ---- delete confirmation (KvsvRequest1.2 #5) ----
    var pendingDelete by mutableStateOf<FeedResponse?>(null)
        private set

    // ---- user-facing operation feedback (acceptance criteria #3) ----
    var successMessage by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    // Set once any post/update/delete succeeds — AppNav reads this on the way back to Dashboard
    // to decide whether to force a refresh there (KvsvRequest1.2 #2's fix).
    var feedsChanged by mutableStateOf(false)
        private set

    fun isOwner(feed: FeedResponse): Boolean = feedIsOwnedBy(feed, CurrentUser.userId)

    fun loadFeeds() {
        loadingFeeds = true; listError = null
        viewModelScope.launch {
            try {
                // No dedicated "list all" endpoint — GET /api/feeds/recent already accepts an
                // arbitrary limit (FeedController.recent), so a generous cap stands in for it.
                val res = RetrofitClient.api.getFeeds(limit = 100)
                if (res.isSuccessful) feeds = res.body().orEmpty()
                else listError = "Couldn't load feeds (HTTP ${res.code()})"
            } catch (e: Exception) {
                listError = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loadingFeeds = false }
        }
    }

    /** Tapping a feed you own loads it into the shared card for editing. */
    fun startEditing(feed: FeedResponse) {
        editingFeedId = feed.id
        message = feed.message
        imageUrls = feed.imageUrls
        pendingUris = emptyList()
        error = null
    }

    /** Cancel editing (or just clear the compose form) back to a blank "new post" state. */
    fun startNewPost() {
        editingFeedId = null
        message = ""
        imageUrls = emptyList()
        pendingUris = emptyList()
        error = null
    }

    fun addPicked(uris: List<Uri>, contentResolver: android.content.ContentResolver) {
        pendingUris = pendingUris + uris
        viewModelScope.launch {
            for (uri in uris) {
                ImageUpload.uploadUri(contentResolver, uri)
                    .onSuccess { url -> imageUrls = imageUrls + url; pendingUris = pendingUris - uri }
                    .onFailure { pendingUris = pendingUris - uri; error = "Couldn't upload one of the photos: ${it.message}" }
            }
        }
    }

    fun removeImage(url: String) { imageUrls = imageUrls - url }

    /** Posts a new feed, or updates the one currently being edited — same card, same button. */
    fun save() {
        val validationError = validateFeedForm(message, pendingUris.size)
        if (validationError != null) { error = validationError; return }
        saving = true; error = null
        val id = editingFeedId
        viewModelScope.launch {
            try {
                if (id == null) {
                    val res = RetrofitClient.api.postFeed(CreateFeedRequest(message.trim(), imageUrls, null))
                    if (res.isSuccessful && res.body() != null) {
                        feeds = feedsAfterCreate(feeds, res.body()!!)
                        successMessage = "Feed posted"
                        feedsChanged = true
                        startNewPost()
                    } else error = when (res.code()) {
                        403 -> "Only Super Admin can post to the feed"
                        else -> "Couldn't post (HTTP ${res.code()})"
                    }
                } else {
                    val res = RetrofitClient.api.updateFeed(id, UpdateFeedRequest(message.trim(), imageUrls))
                    if (res.isSuccessful && res.body() != null) {
                        feeds = feedsAfterUpdate(feeds, res.body()!!)
                        successMessage = "Feed updated"
                        feedsChanged = true
                        startNewPost()
                    } else error = when (res.code()) {
                        403 -> "Only the Super Admin who posted this feed can edit it"
                        else -> "Couldn't update the feed (HTTP ${res.code()})"
                    }
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { saving = false }
        }
    }

    fun requestDelete(feed: FeedResponse) { pendingDelete = feed }
    fun cancelDelete() { pendingDelete = null }

    fun confirmDelete() {
        val feed = pendingDelete ?: return
        saving = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.deleteFeed(feed.id)
                if (res.isSuccessful) {
                    feeds = feedsAfterDelete(feeds, feed.id)
                    if (editingFeedId == feed.id) startNewPost()
                    successMessage = "Feed deleted"
                    feedsChanged = true
                } else error = when (res.code()) {
                    403 -> "Only the Super Admin who posted this feed can delete it"
                    else -> "Couldn't delete the feed (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { saving = false; pendingDelete = null }
        }
    }

    fun dismissSuccessMessage() { successMessage = null }
    fun dismissError() { error = null }
}
