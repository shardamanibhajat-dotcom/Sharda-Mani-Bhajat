package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ErrorCategory
import com.example.data.model.FeedSortOrder
import com.example.data.model.UploadLimits
import com.example.data.model.UserProfile
import com.example.data.model.VideoCategory
import com.example.data.model.VideoComment
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.normalizeTag
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedFilter {
    ALL,
    SHORTS,
    LONG
}

data class FeedUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val filter: FeedFilter = FeedFilter.ALL,
    val selectedCategory: VideoCategory = VideoCategory.ALL,
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val sortOrder: FeedSortOrder = FeedSortOrder.LATEST,
    val shorts: List<VideoItem> = emptyList(),
    val longVideos: List<VideoItem> = emptyList(),
    val allMatchingVideos: List<VideoItem> = emptyList()
)

class FeedViewModel(
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(FeedFilter.ALL)
    val filter: StateFlow<FeedFilter> = _filter.asStateFlow()

    private val _selectedCategory = MutableStateFlow(VideoCategory.ALL)
    val selectedCategory: StateFlow<VideoCategory> = _selectedCategory.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _sortOrder = MutableStateFlow(FeedSortOrder.LATEST)
    val sortOrder: StateFlow<FeedSortOrder> = _sortOrder.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private data class FeedFilterParams(
        val query: String,
        val filter: FeedFilter,
        val category: VideoCategory,
        val selectedTag: String?,
        val sortOrder: FeedSortOrder
    )

    val uiState: StateFlow<FeedUiState> = combine(
        repository.observeActiveVideos(),
        combine(_searchQuery, _filter, _selectedCategory, _selectedTag, _sortOrder) { query, filter, category, tag, sortOrder ->
            FeedFilterParams(query, filter, category, tag, sortOrder)
        }
    ) { videos, params ->
        val validAllSource = videos.filter { video ->
            val maxDur = when (video.format) {
                VideoFormat.SHORT -> UploadLimits.MAX_SHORT_DURATION_SECONDS
                VideoFormat.LONG -> UploadLimits.MAX_LONG_DURATION_SECONDS
            }
            video.durationSeconds in 1L..maxDur && video.videoUri.isNotBlank()
        }

        val tagCounts = linkedMapOf<String, Int>()
        validAllSource.forEach { video ->
            video.tags.forEach { tag ->
                val clean = normalizeTag(tag)
                if (clean.isNotBlank()) {
                    tagCounts[clean] = (tagCounts[clean] ?: 0) + 1
                }
            }
        }
        val activeSelectedTag = params.selectedTag?.let(::normalizeTag)?.ifBlank { null }
        val sortedAvailableTags = tagCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .let { list ->
                if (activeSelectedTag != null && !list.contains(activeSelectedTag)) {
                    list + activeSelectedTag
                } else {
                    list
                }
            }
            .take(20)

        val q = params.query.trim().lowercase()
        val byQuery = if (q.isEmpty()) {
            validAllSource
        } else {
            validAllSource.filter { video ->
                video.title.lowercase().contains(q) ||
                    video.description.lowercase().contains(q) ||
                    video.creatorName.lowercase().contains(q) ||
                    video.category.displayEn.lowercase().contains(q) ||
                    video.category.displayHi.lowercase().contains(q) ||
                    video.tags.any { it.contains(q.removePrefix("#")) }
            }
        }
        val byCategory = if (params.category == VideoCategory.ALL) {
            byQuery
        } else {
            byQuery.filter { it.category == params.category }
        }
        val byTag = if (activeSelectedTag == null) {
            byCategory
        } else {
            byCategory.filter { video ->
                video.tags.any { normalizeTag(it) == activeSelectedTag }
            }
        }
        val sorted = when (params.sortOrder) {
            FeedSortOrder.LATEST -> byTag.sortedByDescending { it.createdAt }
            FeedSortOrder.MOST_VIEWED -> byTag.sortedByDescending { it.viewCount }
            FeedSortOrder.MOST_LIKED -> byTag.sortedByDescending { it.likeCount }
        }
        val validShorts = sorted.filter { it.format == VideoFormat.SHORT }
        val validLongVideos = sorted.filter { it.format == VideoFormat.LONG }
        val filtered = when (params.filter) {
            FeedFilter.ALL -> sorted
            FeedFilter.SHORTS -> validShorts
            FeedFilter.LONG -> validLongVideos
        }
        FeedUiState(
            isLoading = false,
            searchQuery = params.query,
            filter = params.filter,
            selectedCategory = params.category,
            selectedTag = activeSelectedTag,
            availableTags = sortedAvailableTags,
            sortOrder = params.sortOrder,
            shorts = validShorts,
            longVideos = validLongVideos,
            allMatchingVideos = filtered
        )
    }
        .catchFirestoreErrors("FeedViewModel.uiState", FeedUiState(isLoading = false))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), FeedUiState(isLoading = true))

    val creators: StateFlow<List<UserProfile>> = repository.observeAllCreators()
        .catchFirestoreErrors("FeedViewModel.creators", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: FeedFilter) {
        _filter.value = filter
    }

    fun setCategory(category: VideoCategory) {
        _selectedCategory.value = category
    }

    fun setTag(tag: String?) {
        _selectedTag.value = tag?.let(::normalizeTag)?.ifBlank { null }
    }

    fun setSortOrder(sortOrder: FeedSortOrder) {
        _sortOrder.value = sortOrder
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _filter.value = FeedFilter.ALL
        _selectedCategory.value = VideoCategory.ALL
        _selectedTag.value = null
    }

    fun toggleLike(videoId: String, userId: String) {
        viewModelScope.launch {
            repository.toggleLike(videoId, userId)
                .onFailure { _actionMessage.value = it.message ?: "Unable to update like" }
        }
    }

    fun addComment(videoId: String, userId: String, text: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addComment(videoId, userId, text)
                .onSuccess { onSuccess() }
                .onFailure { _actionMessage.value = it.message ?: "Unable to post comment" }
        }
    }

    fun addReply(
        videoId: String,
        userId: String,
        text: String,
        parentCommentId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addComment(videoId, userId, text, parentCommentId)
                .onSuccess { onSuccess() }
                .onFailure { _actionMessage.value = it.message ?: "Unable to post reply" }
        }
    }

    fun observeComments(videoId: String): Flow<List<VideoComment>> = repository.observeComments(videoId)

    fun observeIsSubscribed(creatorId: String, subscriberId: String): Flow<Boolean> =
        repository.observeIsSubscribed(creatorId, subscriberId)

    fun toggleSubscription(creatorId: String, subscriberId: String) {
        viewModelScope.launch {
            repository.toggleSubscription(creatorId, subscriberId)
                .onFailure { _actionMessage.value = it.message ?: "Unable to update subscription" }
        }
    }

    fun recordGenuineView(videoId: String, viewerId: String) {
        viewModelScope.launch {
            repository.recordGenuineView(videoId, viewerId)
        }
    }

    fun logPlaybackError(videoId: String, userId: String, message: String) {
        viewModelScope.launch {
            repository.logError(ErrorCategory.PLAYBACK, message, userId, videoId)
        }
    }

    fun reportVideo(videoId: String, reporterId: String, reason: String) {
        viewModelScope.launch {
            repository.reportVideo(videoId, reporterId, reason)
                .onSuccess { _actionMessage.value = "Report submitted for moderation review." }
                .onFailure { _actionMessage.value = it.message ?: "Unable to submit report" }
        }
    }

    fun blockUser(blockerId: String, targetUserId: String) {
        viewModelScope.launch {
            repository.blockUser(blockerId, targetUserId)
                .onSuccess { _actionMessage.value = "Creator blocked. Their videos are now hidden." }
                .onFailure { _actionMessage.value = it.message ?: "Unable to block user" }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    class Factory(private val repository: CreatorPaisaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedViewModel(repository) as T
        }
    }
}
