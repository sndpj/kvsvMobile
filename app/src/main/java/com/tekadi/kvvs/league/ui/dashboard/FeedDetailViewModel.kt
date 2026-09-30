package com.tekadi.kvvs.league.ui.dashboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.FeedResponse
import com.tekadi.kvvs.league.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * KvsvRequest1.2 (Feeds fixes) #5/#7 dependency review: this used to also own edit/delete
 * (with its own copy of the update/upload/delete logic), duplicating what FeedPostViewModel
 * does for the exact same feed. Editing now lives only in the Post Feed screen — this screen is
 * view-only, reached by tapping a feed from the Dashboard carousel.
 */
class FeedDetailViewModel(app: Application) : AndroidViewModel(app) {

    var feed by mutableStateOf<FeedResponse?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val isOwner: Boolean
        get() {
            val userId = CurrentUser.userId ?: return false
            return feed?.createdByUserId == userId
        }

    fun load(feedId: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.getFeed(feedId)
                if (res.isSuccessful) feed = res.body()
                else error = "Couldn't load this feed (HTTP ${res.code()})"
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally { loading = false }
        }
    }
}
