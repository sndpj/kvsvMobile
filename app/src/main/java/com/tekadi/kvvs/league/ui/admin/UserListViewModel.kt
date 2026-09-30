package com.tekadi.kvvs.league.ui.admin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.UserSummaryResponse
import com.tekadi.kvvs.league.network.friendlyErrorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feature: Super Admin role assign/revoke — "users list with search option by name or mobile
 * number." Debounces keystrokes (300ms) rather than firing a network request on every character,
 * same shape as any typeahead search — the list otherwise re-queries on every single keypress.
 */
class UserListViewModel(app: Application) : AndroidViewModel(app) {

    var users by mutableStateOf<List<UserSummaryResponse>>(emptyList())
    var query by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private var searchJob: Job? = null

    /** Initial load — the full user list, same as an empty search. */
    fun load() = search(immediate = true)

    fun onQueryChange(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch()
        }
    }

    fun retry() = search(immediate = true)

    private fun search(immediate: Boolean) {
        if (immediate) {
            searchJob?.cancel()
            viewModelScope.launch { runSearch() }
        }
    }

    private suspend fun runSearch() {
        loading = true; error = null
        try {
            val res = RetrofitClient.api.listUsers(normalizeUserSearchQuery(query))
            if (res.isSuccessful) users = res.body().orEmpty()
            else error = res.friendlyErrorMessage("Couldn't load users (HTTP ${res.code()})")
        } catch (e: Exception) {
            error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
        } finally {
            loading = false
        }
    }
}
