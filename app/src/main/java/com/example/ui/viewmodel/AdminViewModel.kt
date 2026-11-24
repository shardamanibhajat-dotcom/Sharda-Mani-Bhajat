package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppErrorLog
import com.example.data.model.AppStatistics
import com.example.data.model.ContentReport
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val adminId: String,
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    val statistics: StateFlow<AppStatistics?> = repository.observeAppStatistics(adminId)
        .catchFirestoreErrors("AdminViewModel.stats", null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val allVideos: StateFlow<List<VideoItem>> = repository.observeAllVideosForAdmin(adminId)
        .catchFirestoreErrors("AdminViewModel.videos", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allUsers: StateFlow<List<UserProfile>> = repository.observeAllUsersForAdmin(adminId)
        .catchFirestoreErrors("AdminViewModel.users", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reports: StateFlow<List<ContentReport>> = repository.observeAllReportsForAdmin(adminId)
        .catchFirestoreErrors("AdminViewModel.reports", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val errorLogs: StateFlow<List<AppErrorLog>> = repository.observeAllErrorLogsForAdmin(adminId)
        .catchFirestoreErrors("AdminViewModel.errorLogs", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _adminMessage = MutableStateFlow<String?>(null)
    val adminMessage: StateFlow<String?> = _adminMessage.asStateFlow()

    fun removeVideo(videoId: String, reason: String) {
        viewModelScope.launch {
            repository.adminRemoveVideo(adminId, videoId, reason)
                .onSuccess { _adminMessage.value = "Video removed from public feed." }
                .onFailure { _adminMessage.value = it.message ?: "Action denied" }
        }
    }

    fun restoreVideo(videoId: String) {
        viewModelScope.launch {
            repository.adminRestoreVideo(adminId, videoId)
                .onSuccess { _adminMessage.value = "Video restored." }
                .onFailure { _adminMessage.value = it.message ?: "Action denied" }
        }
    }

    fun setUploaderApproval(targetUserId: String, approved: Boolean) {
        viewModelScope.launch {
            repository.adminSetUploaderApproval(adminId, targetUserId, approved)
                .onSuccess {
                    _adminMessage.value = if (approved) "Creator upload access approved." else "Creator upload access revoked."
                }
                .onFailure { _adminMessage.value = it.message ?: "Action denied" }
        }
    }

    fun setUserSuspended(targetUserId: String, suspended: Boolean) {
        viewModelScope.launch {
            repository.adminSetUserSuspended(adminId, targetUserId, suspended)
                .onSuccess {
                    _adminMessage.value = if (suspended) "User suspended." else "User reactivated."
                }
                .onFailure { _adminMessage.value = it.message ?: "Action denied" }
        }
    }

    fun resolveReport(reportId: String) {
        viewModelScope.launch {
            repository.adminResolveReport(adminId, reportId)
                .onSuccess { _adminMessage.value = "Report marked resolved." }
                .onFailure { _adminMessage.value = it.message ?: "Action denied" }
        }
    }

    fun clearMessage() {
        _adminMessage.value = null
    }

    class Factory(
        private val adminId: String,
        private val repository: CreatorPaisaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminViewModel(adminId, repository) as T
        }
    }
}
