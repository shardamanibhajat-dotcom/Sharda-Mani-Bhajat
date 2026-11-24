package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ErrorCategory
import com.example.data.model.FeedSortOrder
import com.example.data.model.UploadLimits
import com.example.data.model.VideoCategory
import com.example.data.model.VideoComment
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.normalizeTag
import com.example.data.repository.CreatorPaisaRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VideoFeedUiState(
    val isLoading: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val selectedFormat: VideoFormat? = null,
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val sortOrder: FeedSortOrder = FeedSortOrder.LATEST,
    val errorMessage: String? = null
)

private sealed interface FeedSnapshotResult {
    data class Data(val videos: List<VideoItem>) : FeedSnapshotResult
    data class Error(val message: String) : FeedSnapshotResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class VideoFeedViewModel(
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _refreshTick = MutableStateFlow(0)
    private val _selectedFormat = MutableStateFlow<VideoFormat?>(null)
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _sortOrder = MutableStateFlow(FeedSortOrder.LATEST)

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val sourceVideosFlow = _refreshTick.flatMapLatest {
        repository.observeActiveVideos()
            .map<List<VideoItem>, FeedSnapshotResult> { FeedSnapshotResult.Data(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) {
                    throw error
                }
                emit(
                    FeedSnapshotResult.Error(
                        firestoreError?.message
                            ?: error.message
                            ?: "Unable to load uploaded videos from Firestore."
                    )
                )
            }
    }

    val uiState: StateFlow<VideoFeedUiState> = combine(
        sourceVideosFlow,
        _selectedFormat,
        _selectedTag,
        _sortOrder
    ) { snapshot, formatFilter, tagFilter, sortOrder ->
        when (snapshot) {
            is FeedSnapshotResult.Error -> VideoFeedUiState(
                isLoading = false,
                videos = emptyList(),
                selectedFormat = formatFilter,
                selectedTag = tagFilter,
                availableTags = emptyList(),
                sortOrder = sortOrder,
                errorMessage = snapshot.message
            )

            is FeedSnapshotResult.Data -> {
                val validVideos = snapshot.videos.filter { video ->
                    val maxDur = when (video.format) {
                        VideoFormat.SHORT -> UploadLimits.MAX_SHORT_DURATION_SECONDS
                        VideoFormat.LONG -> UploadLimits.MAX_LONG_DURATION_SECONDS
                    }
                    video.durationSeconds in 1L..maxDur && video.videoUri.isNotBlank()
                }

                val tagCounts = linkedMapOf<String, Int>()
                validVideos.forEach { video ->
                    video.tags.forEach { rawTag ->
                        val clean = normalizeTag(rawTag)
                        if (clean.isNotBlank()) {
                            tagCounts[clean] = (tagCounts[clean] ?: 0) + 1
                        }
                    }
                }
                val cleanTagFilter = tagFilter?.let(::normalizeTag)?.ifBlank { null }
                val availableTags = tagCounts.entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .map { it.key }
                    .let { tags ->
                        if (cleanTagFilter != null && !tags.contains(cleanTagFilter)) {
                            tags + cleanTagFilter
                        } else {
                            tags
                        }
                    }
                    .take(20)

                val byFormat = if (formatFilter == null) {
                    validVideos
                } else {
                    validVideos.filter { it.format == formatFilter }
                }
                val byTag = if (cleanTagFilter == null) {
                    byFormat
                } else {
                    byFormat.filter { video ->
                        video.tags.any { normalizeTag(it) == cleanTagFilter }
                    }
                }
                val sorted = when (sortOrder) {
                    FeedSortOrder.LATEST -> byTag.sortedByDescending { it.createdAt }
                    FeedSortOrder.MOST_VIEWED -> byTag.sortedByDescending { it.viewCount }
                    FeedSortOrder.MOST_LIKED -> byTag.sortedByDescending { it.likeCount }
                }

                VideoFeedUiState(
                    isLoading = false,
                    videos = sorted,
                    selectedFormat = formatFilter,
                    selectedTag = cleanTagFilter,
                    availableTags = availableTags,
                    sortOrder = sortOrder,
                    errorMessage = null
                )
            }
        }
    }
        .catchFirestoreErrors("VideoFeedViewModel.uiState", VideoFeedUiState(isLoading = false))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = VideoFeedUiState(isLoading = true)
        )

    fun setFormatFilter(format: VideoFormat?) {
        _selectedFormat.value = format
    }

    fun setTagFilter(tag: String?) {
        _selectedTag.value = tag?.let(::normalizeTag)?.ifBlank { null }
    }

    fun setSortOrder(sortOrder: FeedSortOrder) {
        _sortOrder.value = sortOrder
    }

    fun clearFilters() {
        _selectedFormat.value = null
        _selectedTag.value = null
    }

    fun refresh() {
        _refreshTick.value += 1
    }

    fun toggleLike(videoId: String, userId: String) {
        viewModelScope.launch {
            repository.toggleLike(videoId, userId)
                .onFailure { _actionMessage.value = it.message ?: "Unable to update like." }
        }
    }

    fun toggleFollow(creatorId: String, userId: String) {
        viewModelScope.launch {
            repository.toggleSubscription(creatorId, userId)
                .onFailure { _actionMessage.value = it.message ?: "Unable to update follow status." }
        }
    }

    fun observeComments(videoId: String): Flow<List<VideoComment>> = repository.observeComments(videoId)

    fun addComment(
        videoId: String,
        userId: String,
        text: String,
        parentCommentId: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addComment(videoId, userId, text, parentCommentId)
                .onSuccess { onSuccess() }
                .onFailure { _actionMessage.value = it.message ?: "Unable to post comment." }
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

    fun clearMessage() {
        _actionMessage.value = null
    }

    class Factory(private val repository: CreatorPaisaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VideoFeedViewModel(repository) as T
        }
    }
}
