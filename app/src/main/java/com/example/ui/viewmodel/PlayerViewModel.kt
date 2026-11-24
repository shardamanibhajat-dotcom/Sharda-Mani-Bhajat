package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ErrorCategory
import com.example.data.model.UploadLimits
import com.example.data.model.UserProfile
import com.example.data.model.VideoComment
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val video: VideoItem? = null,
    val creator: UserProfile? = null,
    val comments: List<VideoComment> = emptyList(),
    val relatedVideos: List<VideoItem> = emptyList(),
    val isSubscribed: Boolean = false,
    val isCreatorBlocked: Boolean = false,
    val isViewRecordedThisSession: Boolean = false,
    val savedPositionMs: Long = 0L,
    val playbackError: String? = null
)

class PlayerViewModel(
    private val videoId: String,
    private val currentUserId: String,
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _viewRecorded = MutableStateFlow(false)
    private val _playbackError = MutableStateFlow<String?>(null)
    private val _savedPositionMs = MutableStateFlow(0L)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _savedPositionMs.value = repository.getSavedWatchPositionMs(currentUserId, videoId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val creatorFlow = repository.observeVideoById(videoId).flatMapLatest { video ->
        if (video == null) flowOf(null) else repository.observeCreatorProfile(video.creatorId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val subscriptionFlow = repository.observeVideoById(videoId).flatMapLatest { video ->
        if (video == null) flowOf(false) else repository.observeIsSubscribed(video.creatorId, currentUserId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val blockedFlow = repository.observeVideoById(videoId).flatMapLatest { video ->
        if (video == null) flowOf(false) else repository.observeIsUserBlocked(currentUserId, video.creatorId)
    }

    private val relatedVideosFlow = combine(
        repository.observeVideoById(videoId),
        repository.observeActiveVideos()
    ) { currentVideo, activeVideos ->
        if (currentVideo == null) {
            emptyList()
        } else {
            activeVideos
                .filter { candidate ->
                    candidate.id != currentVideo.id &&
                        candidate.videoUri.isNotBlank() &&
                        when (candidate.format) {
                            VideoFormat.SHORT -> candidate.durationSeconds in 1L..UploadLimits.MAX_SHORT_DURATION_SECONDS
                            VideoFormat.LONG -> candidate.durationSeconds in 1L..UploadLimits.MAX_LONG_DURATION_SECONDS
                        }
                }
                .sortedWith(
                    compareByDescending<VideoItem> { it.format == currentVideo.format }
                        .thenByDescending { it.category == currentVideo.category }
                        .thenByDescending { it.creatorId == currentVideo.creatorId }
                        .thenByDescending { it.viewCount }
                )
                .take(8)
        }
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        repository.observeVideoById(videoId),
        creatorFlow,
        repository.observeComments(videoId),
        relatedVideosFlow,
        combine(
            combine(subscriptionFlow, blockedFlow) { isSub, isBlocked -> isSub to isBlocked },
            combine(_viewRecorded, _playbackError, _savedPositionMs) { recorded, err, pos ->
                Triple(recorded, err, pos)
            }
        ) { subPair, playbackTriple -> subPair to playbackTriple }
    ) { video, creator, comments, related, (subPair, playbackTriple) ->
        PlayerUiState(
            video = video,
            creator = creator,
            comments = comments,
            relatedVideos = related,
            isSubscribed = subPair.first,
            isCreatorBlocked = subPair.second,
            isViewRecordedThisSession = playbackTriple.first,
            playbackError = playbackTriple.second,
            savedPositionMs = playbackTriple.third
        )
    }
        .catchFirestoreErrors("PlayerViewModel.uiState", PlayerUiState())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlayerUiState())

    fun onGenuinePlaybackReached() {
        if (_viewRecorded.value) return
        viewModelScope.launch {
            repository.recordGenuineView(videoId, currentUserId)
            _viewRecorded.value = true
        }
    }

    fun recordWatchHistory(positionMs: Long) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            repository.recordWatchHistory(currentUserId, videoId, positionMs)
        }
    }

    fun onPlaybackError(errorMessage: String) {
        _playbackError.value = errorMessage
        viewModelScope.launch {
            repository.logError(
                category = ErrorCategory.PLAYBACK,
                message = errorMessage,
                userId = currentUserId,
                videoId = videoId
            )
        }
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    fun toggleLike() {
        viewModelScope.launch {
            repository.toggleLike(videoId, currentUserId)
                .onFailure { _feedbackMessage.value = it.message ?: "Failed to update like" }
        }
    }

    fun toggleSubscribe() {
        val creatorId = uiState.value.video?.creatorId ?: return
        viewModelScope.launch {
            repository.toggleSubscription(creatorId, currentUserId)
                .onFailure { _feedbackMessage.value = it.message ?: "Failed to update subscription" }
        }
    }

    fun blockCreator(onBlocked: () -> Unit = {}) {
        val creatorId = uiState.value.video?.creatorId ?: return
        viewModelScope.launch {
            repository.blockUser(currentUserId, creatorId)
                .onSuccess {
                    _feedbackMessage.value = "Creator blocked."
                    onBlocked()
                }
                .onFailure { _feedbackMessage.value = it.message ?: "Failed to block creator" }
        }
    }

    fun postComment(text: String, parentCommentId: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addComment(videoId, currentUserId, text, parentCommentId)
                .onSuccess { onSuccess() }
                .onFailure { _feedbackMessage.value = it.message ?: "Failed to post comment" }
        }
    }

    fun reportVideo(reason: String) {
        viewModelScope.launch {
            repository.reportVideo(videoId, currentUserId, reason)
                .onSuccess { _feedbackMessage.value = "Video reported to moderation." }
                .onFailure { _feedbackMessage.value = it.message ?: "Failed to report video" }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    class Factory(
        private val videoId: String,
        private val currentUserId: String,
        private val repository: CreatorPaisaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(videoId, currentUserId, repository) as T
        }
    }
}
