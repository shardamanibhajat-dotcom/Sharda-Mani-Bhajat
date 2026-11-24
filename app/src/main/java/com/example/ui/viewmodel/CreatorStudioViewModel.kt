package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.model.VideoCategory
import com.example.data.model.VideoItem
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CreatorStudioUiState(
    val creator: UserProfile? = null,
    val videos: List<VideoItem> = emptyList(),
    val recentUploads: List<VideoItem> = emptyList(),
    val longVideoCount: Int = 0,
    val shortVideoCount: Int = 0,
    val totalViews: Long = 0L,
    val totalLikes: Long = 0L,
    val totalComments: Long = 0L,
    val subscriberCount: Long = 0L,
    val dailyUploadCount: Int = 0,
    val isAuthorized: Boolean = false
)

class CreatorStudioViewModel(
    private val requesterId: String,
    private val creatorId: String = requesterId,
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<CreatorStudioUiState> =
        repository.observeCreatorDashboard(requesterId, creatorId)
            .map { stats ->
                if (stats == null) {
                    CreatorStudioUiState(isAuthorized = false)
                } else {
                    CreatorStudioUiState(
                        creator = stats.creator,
                        videos = stats.allVideos,
                        recentUploads = stats.recentUploads,
                        longVideoCount = stats.longVideoCount,
                        shortVideoCount = stats.shortVideoCount,
                        totalViews = stats.totalGenuineViews,
                        totalLikes = stats.totalLikes,
                        totalComments = stats.totalComments,
                        subscriberCount = stats.subscriberCount,
                        dailyUploadCount = stats.dailyUploadCount,
                        isAuthorized = true
                    )
                }
            }
            .catchFirestoreErrors("CreatorDashboardVM.uiState", CreatorStudioUiState())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CreatorStudioUiState())

    fun updateVideo(
        videoId: String,
        title: String,
        description: String,
        category: VideoCategory,
        thumbnailUri: String? = null,
        tags: List<String>? = null
    ) {
        viewModelScope.launch {
            repository.updateOwnVideo(requesterId, videoId, title, description, category, thumbnailUri, tags)
                .onSuccess { _message.value = "Video updated." }
                .onFailure { _message.value = it.message ?: "Unable to update video" }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            repository.deleteOwnVideo(requesterId, videoId)
                .onSuccess { _message.value = "Video deleted from channel." }
                .onFailure { _message.value = it.message ?: "Unable to delete video" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(
        private val requesterId: String,
        private val creatorId: String = requesterId,
        private val repository: CreatorPaisaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreatorStudioViewModel(requesterId, creatorId, repository) as T
        }
    }
}
