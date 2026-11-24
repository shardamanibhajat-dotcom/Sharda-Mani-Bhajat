package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CreatorPlaylist
import com.example.data.model.UserProfile
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelUiState(
    val creator: UserProfile? = null,
    val videos: List<VideoItem> = emptyList(),
    val longVideos: List<VideoItem> = emptyList(),
    val shortVideos: List<VideoItem> = emptyList(),
    val playlists: List<CreatorPlaylist> = emptyList(),
    val subscribedCreators: List<UserProfile> = emptyList(),
    val isSubscribed: Boolean = false,
    val isBlocked: Boolean = false,
    val totalViews: Long = 0L,
    val totalLikes: Long = 0L
)

class ChannelViewModel(
    private val creatorId: String,
    private val currentUserId: String,
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<ChannelUiState> = combine(
        repository.observeCreatorProfile(creatorId),
        repository.observeVideosByCreator(creatorId),
        repository.observePlaylistsForChannel(currentUserId, creatorId),
        repository.observeSubscribedCreators(currentUserId),
        combine(
            repository.observeIsSubscribed(creatorId, currentUserId),
            repository.observeIsUserBlocked(currentUserId, creatorId)
        ) { sub, blocked -> sub to blocked }
    ) { creator, rawVideos, playlists, subscribedCreators, (isSubscribed, isBlocked) ->
        val isOwn = creator?.id == currentUserId
        val hidePrivateContent = (creator?.isPrivateProfile == true && !isOwn) || isBlocked
        val visibleVideos = if (hidePrivateContent) emptyList() else rawVideos
        val sanitizedCreator = creator?.let { c ->
            if (!isOwn && c.hideSubscriberCount) {
                c.copy(subscriberCount = 0L, emailMasked = "")
            } else if (!isOwn) {
                c.copy(emailMasked = "")
            } else {
                c
            }
        }
        ChannelUiState(
            creator = sanitizedCreator,
            videos = visibleVideos,
            longVideos = visibleVideos.filter { it.format == VideoFormat.LONG },
            shortVideos = visibleVideos.filter { it.format == VideoFormat.SHORT },
            playlists = if (hidePrivateContent) emptyList() else playlists,
            subscribedCreators = subscribedCreators,
            isSubscribed = isSubscribed,
            isBlocked = isBlocked,
            totalViews = if (hidePrivateContent) 0L else rawVideos.sumOf { it.viewCount },
            totalLikes = if (hidePrivateContent) 0L else rawVideos.sumOf { it.likeCount }
        )
    }
        .catchFirestoreErrors("ChannelViewModel.uiState", ChannelUiState())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ChannelUiState())

    fun toggleSubscription() {
        viewModelScope.launch {
            repository.toggleSubscription(creatorId, currentUserId)
                .onFailure { _message.value = it.message ?: "Unable to update subscription" }
        }
    }

    fun toggleBlock() {
        viewModelScope.launch {
            if (uiState.value.isBlocked) {
                repository.unblockUser(currentUserId, creatorId)
                    .onSuccess { _message.value = "Creator unblocked." }
                    .onFailure { _message.value = it.message ?: "Unable to unblock" }
            } else {
                repository.blockUser(currentUserId, creatorId)
                    .onSuccess { _message.value = "Creator blocked." }
                    .onFailure { _message.value = it.message ?: "Unable to block" }
            }
        }
    }

    fun toggleLike(videoId: String) {
        viewModelScope.launch {
            repository.toggleLike(videoId, currentUserId)
                .onFailure { _message.value = it.message ?: "Unable to update like" }
        }
    }

    fun reportVideo(videoId: String, reason: String) {
        viewModelScope.launch {
            repository.reportVideo(videoId, currentUserId, reason)
                .onSuccess { _message.value = "Report submitted." }
                .onFailure { _message.value = it.message ?: "Unable to report video" }
        }
    }

    fun createPlaylist(
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createPlaylist(currentUserId, title, description, thumbnailUri, isPublic)
                .onSuccess {
                    _message.value = "Playlist created."
                    onSuccess()
                }
                .onFailure { _message.value = it.message ?: "Unable to create playlist" }
        }
    }

    fun updatePlaylist(
        playlistId: String,
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updatePlaylist(currentUserId, playlistId, title, description, thumbnailUri, isPublic)
                .onSuccess {
                    _message.value = "Playlist updated."
                    onSuccess()
                }
                .onFailure { _message.value = it.message ?: "Unable to update playlist" }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(currentUserId, playlistId)
                .onSuccess { _message.value = "Playlist deleted." }
                .onFailure { _message.value = it.message ?: "Unable to delete playlist" }
        }
    }

    fun addVideoToPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(currentUserId, playlistId, videoId)
                .onSuccess { _message.value = "Added video to playlist." }
                .onFailure { _message.value = it.message ?: "Unable to add video" }
        }
    }

    fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(currentUserId, playlistId, videoId)
                .onSuccess { _message.value = "Removed video from playlist." }
                .onFailure { _message.value = it.message ?: "Unable to remove video" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(
        private val creatorId: String,
        private val currentUserId: String,
        private val repository: CreatorPaisaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChannelViewModel(creatorId, currentUserId, repository) as T
        }
    }
}
