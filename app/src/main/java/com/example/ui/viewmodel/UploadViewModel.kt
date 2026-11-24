package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.UploadDraft
import com.example.data.model.UploadProgressState
import com.example.data.model.VideoCategory
import com.example.data.model.VideoFormat
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UploadViewModel(
    private val creatorId: String,
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _dailyUploadCount = MutableStateFlow(0)
    val dailyUploadCount: StateFlow<Int> = _dailyUploadCount.asStateFlow()

    private val _progressState = MutableStateFlow<UploadProgressState>(UploadProgressState.Idle)
    val progressState: StateFlow<UploadProgressState> = _progressState.asStateFlow()

    private var uploadJob: Job? = null
    private var lastDraft: UploadDraft? = null

    init {
        refreshQuota()
    }

    fun refreshQuota() {
        viewModelScope.launch {
            _dailyUploadCount.value = repository.getDailyUploadCount(creatorId)
        }
    }

    fun publishVideo(
        title: String,
        description: String,
        format: VideoFormat,
        category: VideoCategory,
        durationSeconds: Long,
        fileSizeBytes: Long,
        thumbnailUri: String,
        videoUri: String,
        tags: List<String> = emptyList(),
        isPublic: Boolean = true
    ) {
        val draft = UploadDraft(
            title = title,
            description = description,
            format = format,
            category = category,
            durationSeconds = durationSeconds,
            fileSizeBytes = fileSizeBytes,
            thumbnailUri = thumbnailUri,
            videoUri = videoUri,
            isPublic = isPublic,
            tags = tags
        )
        startUpload(draft)
    }

    private fun startUpload(draft: UploadDraft) {
        lastDraft = draft
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            try {
                _progressState.value = UploadProgressState.Uploading(0.02f, "Starting upload...")
                repository.uploadVideo(creatorId, draft) { progress, stage ->
                    _progressState.value = UploadProgressState.Uploading(
                        progress = progress.coerceIn(0f, 1f),
                        stageLabel = stage
                    )
                }
                    .onSuccess { videoId ->
                        refreshQuota()
                        _progressState.value = UploadProgressState.Success(videoId)
                    }
                    .onFailure { err ->
                        _progressState.value = UploadProgressState.Failure(
                            err.message ?: "Upload failed due to an unexpected error."
                        )
                    }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _progressState.value = UploadProgressState.Failure(
                    e.message ?: "Upload failed."
                )
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _progressState.value = UploadProgressState.Cancelled("Upload cancelled. You can retry anytime.")
    }

    fun retryUpload() {
        val draft = lastDraft ?: return
        startUpload(draft)
    }

    fun resetState() {
        if (_progressState.value !is UploadProgressState.Uploading) {
            _progressState.value = UploadProgressState.Idle
        }
    }

    class Factory(
        private val creatorId: String,
        private val repository: CreatorPaisaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UploadViewModel(creatorId, repository) as T
        }
    }
}
