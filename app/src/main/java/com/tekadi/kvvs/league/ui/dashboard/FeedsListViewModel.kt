package com.tekadi.kvvs.league.ui.dashboard

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.UpdateFeedRequest
import com.tekadi.kvvs.league.util.ImageUpload
import kotlinx.coroutines.launch

/**
 * KvsvRequest1.1 #7: "In Feeds screen show feeds list, show user can select and update from
 * the same screen. It should be display below of selected feed." Distinct from the existing
 * Dashboard carousel (5 latest, read-only) and FeedDetailScreen (a single feed, its own page)
 * — this is a browsable list of every feed where tapping one expands an inline editor directly
 * below that item, in place, without navigating away. Update/delete logic mirrors
 * FeedDetailViewModel (same endpoints, same ownership rule) since both are the same operation
 * with a different surface around it.
 */
class FeedsListViewModel(app: Application) : AndroidViewModel(app) {

    var feeds by mutableStateOf<List<FeedResponse>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Which feed (if any) is currently expanded for editing — drives "below the selected feed".
    var selectedFeedId by mutableStateOf<Long?>(null)
        private set
    var editMessage by mutableStateOf("")
    var editImageUrls by mutableStateOf<List<String>>(emptyList())
    var saving by mutableStateOf(false)

    fun isOwner(feed: FeedResponse): Boolean {
        val userId = CurrentUser.userId ?: return false
        return feed.createdByUserId == userId
    }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                // No dedicated "list all" endpoint — GET /api/feeds/recent already accepts an
                // arbitrary limit (FeedController.recent), so a generous cap stands in for it
                // rather than adding a new backend endpoint for what's functionally the same call.
                val res = RetrofitClient.api.getFeeds(limit = 100)
                if (res.isSuccessful) feeds = res.body().orEmpty()
                else error = "Couldn't load feeds (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }

    /** Tapping a feed that's already selected collapses it again; tapping another swaps the editor to it. */
    fun toggleSelect(feed: FeedResponse) {
        if (selectedFeedId == feed.id) {
            selectedFeedId = null
        } else {
            selectedFeedId = feed.id
            editMessage = feed.message
            editImageUrls = feed.imageUrls
            error = null
        }
    }

    fun cancelEdit() {
        selectedFeedId = null
    }

    fun uploadAndAppend(contentResolver: ContentResolver, uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                ImageUpload.uploadUri(contentResolver, uri)
                    .onSuccess { url -> editImageUrls = editImageUrls + url }
                    .onFailure { error = "Couldn't upload one of the photos: ${it.message}" }
            }
        }
    }

    fun save() {
        val id = selectedFeedId ?: return
        if (editMessage.isBlank()) { error = "Enter a message"; return }
        saving = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.updateFeed(id, UpdateFeedRequest(editMessage.trim(), editImageUrls))
                if (res.isSuccessful && res.body() != null) {
                    val updated = res.body()!!
                    feeds = feeds.map { if (it.id == id) updated else it }
                    selectedFeedId = null
                } else error = when (res.code()) {
                    403 -> "Only the Super Admin who posted this feed can edit it"
                    else -> "Couldn't update the feed (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { saving = false }
        }
    }

    fun delete(feed: FeedResponse) {
        saving = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.deleteFeed(feed.id)
                if (res.isSuccessful) {
                    feeds = feeds.filter { it.id != feed.id }
                    if (selectedFeedId == feed.id) selectedFeedId = null
                } else error = when (res.code()) {
                    403 -> "Only the Super Admin who posted this feed can delete it"
                    else -> "Couldn't delete the feed (HTTP ${res.code()})"
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { saving = false }
        }
    }
}
