package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchScope {
    ALL,
    TITLES,
    CREATORS
}

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val isSearching: Boolean = false,
    val results: List<VideoItem> = emptyList(),
    val matchingCreators: List<UserProfile> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val errorMessage: String? = null,
    val hasSearched: Boolean = false
)

class SearchViewModel(
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _scope = MutableStateFlow(SearchScope.ALL)
    private val _isSearching = MutableStateFlow(false)
    private val _rawResults = MutableStateFlow<List<VideoItem>>(emptyList())
    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _hasSearched = MutableStateFlow(false)

    private var searchJob: Job? = null
    private var debounceJob: Job? = null

    private data class SearchFilterState(
        val query: String,
        val scope: SearchScope,
        val isSearching: Boolean,
        val rawResults: List<VideoItem>,
        val recentQueries: List<String>,
        val errorMessage: String?,
        val hasSearched: Boolean
    )

    private val filterFlow = combine(
        _query,
        _scope,
        _isSearching,
        _rawResults,
        _recentQueries
    ) { query, scope, isSearching, rawResults, recent ->
        SearchFilterState(
            query = query,
            scope = scope,
            isSearching = isSearching,
            rawResults = rawResults,
            recentQueries = recent,
            errorMessage = null,
            hasSearched = false
        )
    }.combine(_errorMessage) { state, err ->
        state.copy(errorMessage = err)
    }.combine(_hasSearched) { state, searched ->
        state.copy(hasSearched = searched)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        filterFlow,
        repository.observeActiveVideos().catchFirestoreErrors("SearchViewModel.activeVideos", emptyList()),
        repository.observeAllCreators().catchFirestoreErrors("SearchViewModel.creators", emptyList())
    ) { state, liveVideos, creators ->
        val liveById = liveVideos.associateBy { it.id }
        val syncedVideos = state.rawResults.map { cached -> liveById[cached.id] ?: cached }
        val clean = state.query.trim().lowercase()

        val scopedVideos = if (clean.isEmpty()) {
            syncedVideos
        } else {
            val tagToken = clean.removePrefix("#")
            when (state.scope) {
                SearchScope.ALL -> syncedVideos.filter {
                    it.title.lowercase().contains(clean) ||
                        it.creatorName.lowercase().contains(clean) ||
                        it.description.lowercase().contains(clean) ||
                        it.tags.any { tag -> tag.contains(tagToken) }
                }
                SearchScope.TITLES -> syncedVideos.filter {
                    it.title.lowercase().contains(clean) || it.tags.any { tag -> tag.contains(tagToken) }
                }
                SearchScope.CREATORS -> syncedVideos.filter {
                    it.creatorName.lowercase().contains(clean)
                }
            }
        }

        val matchingCreators = if (clean.isEmpty() || state.scope == SearchScope.TITLES) {
            emptyList()
        } else {
            creators.filter { creator ->
                creator.displayName.lowercase().contains(clean) ||
                    creator.username.lowercase().contains(clean)
            }.sortedByDescending { it.subscriberCount }
        }

        SearchUiState(
            query = state.query,
            scope = state.scope,
            isSearching = state.isSearching,
            results = scopedVideos,
            matchingCreators = matchingCreators,
            recentQueries = state.recentQueries,
            errorMessage = state.errorMessage,
            hasSearched = state.hasSearched
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SearchUiState(isSearching = true))

    init {
        executeSearch(query = "", recordHistory = false)
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        _errorMessage.value = null
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(250L)
            executeSearch(query = newQuery, recordHistory = false)
        }
    }

    fun setScope(scope: SearchScope) {
        _scope.value = scope
    }

    fun submitSearch(overrideQuery: String? = null) {
        val target = overrideQuery ?: _query.value
        _query.value = target
        debounceJob?.cancel()
        executeSearch(query = target, recordHistory = target.isNotBlank())
    }

    fun clearQuery() {
        _query.value = ""
        _errorMessage.value = null
        debounceJob?.cancel()
        executeSearch(query = "", recordHistory = false)
    }

    fun retry() {
        executeSearch(query = _query.value, recordHistory = _query.value.isNotBlank())
    }

    fun toggleLike(videoId: String, userId: String) {
        viewModelScope.launch {
            repository.toggleLike(videoId, userId)
                .onFailure { _errorMessage.value = it.message ?: "Unable to update like" }
        }
    }

    private fun executeSearch(query: String, recordHistory: Boolean) {
        val trimmed = query.trim()
        if (recordHistory && trimmed.isNotBlank()) {
            val updated = (listOf(trimmed) + _recentQueries.value.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(8)
            _recentQueries.value = updated
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _errorMessage.value = null
            repository.searchVideos(trimmed)
                .onSuccess { list ->
                    _rawResults.value = list
                    _hasSearched.value = trimmed.isNotBlank()
                    _isSearching.value = false
                }
                .onFailure { err ->
                    _errorMessage.value = err.message ?: "Unable to search videos from Firestore."
                    _isSearching.value = false
                }
        }
    }

    class Factory(private val repository: CreatorPaisaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(repository) as T
        }
    }
}
