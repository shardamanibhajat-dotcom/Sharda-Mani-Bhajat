package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.NotificationPreferences
import com.example.data.model.UserProfile
import com.example.data.remote.sendPasswordReset as remoteSendPasswordReset
import com.example.data.repository.CreatorPaisaRepository
import com.example.localization.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AppViewModel(
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = repository.currentUser
        .catchFirestoreErrors("AppViewModel.currentUser", null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val appLanguage: StateFlow<AppLanguage> = repository.appLanguage
        .catchFirestoreErrors("AppViewModel.appLanguage", AppLanguage.ENGLISH)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppLanguage.ENGLISH)

    val isDarkTheme: StateFlow<Boolean> = repository.isDarkTheme
        .catchFirestoreErrors("AppViewModel.isDarkTheme", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotificationCount: StateFlow<Int> = repository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(0) else repository.observeUnreadNotificationCount(user.id)
    }
        .catchFirestoreErrors("AppViewModel.unreadCount", 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val blockedUsers: StateFlow<List<UserProfile>> = repository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else repository.observeBlockedUsers(user.id)
    }
        .catchFirestoreErrors("AppViewModel.blockedUsers", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subscribedCreators: StateFlow<List<UserProfile>> = repository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else repository.observeSubscribedCreators(user.id)
    }
        .catchFirestoreErrors("AppViewModel.subscribedCreators", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val notificationPreferences: StateFlow<NotificationPreferences> = repository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(NotificationPreferences(userId = "")) else repository.observeNotificationPreferences(user.id)
    }
        .catchFirestoreErrors("AppViewModel.notifPrefs", NotificationPreferences(userId = ""))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NotificationPreferences(userId = ""))

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureSeeded()
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (appLanguage.value == AppLanguage.ENGLISH) {
                AppLanguage.HINDI
            } else {
                AppLanguage.ENGLISH
            }
            repository.setAppLanguage(next)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(enabled)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            repository.login(email, password)
                .onSuccess { _authState.value = AuthUiState.Idle }
                .onFailure { _authState.value = AuthUiState.Error(it.message ?: "Sign in failed") }
        }
    }

    fun signUp(
        email: String,
        username: String,
        password: String,
        bio: String,
        avatarUri: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            repository.signUp(email, username, password, bio, avatarUri)
                .onSuccess { _authState.value = AuthUiState.Idle }
                .onFailure { _authState.value = AuthUiState.Error(it.message ?: "Sign up failed") }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = remoteSendPasswordReset(email)
            onResult(result)
        }
    }

    fun setAuthLoading(loading: Boolean) {
        _authState.value = if (loading) AuthUiState.Loading else AuthUiState.Idle
    }

    fun setAuthError(message: String) {
        _authState.value = AuthUiState.Error(message)
    }

    fun clearAuthError() {
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _authState.value = AuthUiState.Idle
        }
    }

    fun updateProfile(username: String, bio: String, avatarUri: String) {
        val uid = currentUser.value?.id ?: return
        viewModelScope.launch {
            repository.updateProfile(uid, username, bio, avatarUri)
                .onSuccess { _statusBanner.value = "Profile updated" }
                .onFailure { _statusBanner.value = it.message ?: "Failed to update profile" }
        }
    }

    fun updateChannelProfile(
        displayName: String,
        username: String,
        bio: String,
        channelDescription: String,
        avatarUri: String,
        bannerUri: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = currentUser.value?.id ?: return
        viewModelScope.launch {
            repository.updateProfile(
                requesterId = uid,
                targetUserId = uid,
                displayName = displayName,
                username = username,
                bio = bio,
                channelDescription = channelDescription,
                avatarUri = avatarUri,
                bannerUri = bannerUri
            ).onSuccess {
                _statusBanner.value = "Channel profile updated"
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Failed to update channel profile"
                onError(msg)
                _statusBanner.value = msg
            }
        }
    }

    fun updatePrivacySettings(isPrivateProfile: Boolean, hideSubscriberCount: Boolean) {
        val uid = currentUser.value?.id ?: return
        viewModelScope.launch {
            repository.updatePrivacySettings(uid, isPrivateProfile, hideSubscriberCount)
                .onSuccess { _statusBanner.value = "Privacy settings saved" }
                .onFailure { _statusBanner.value = it.message ?: "Failed to update privacy settings" }
        }
    }

    fun updateNotificationPreferences(preferences: NotificationPreferences) {
        val uid = currentUser.value?.id ?: return
        viewModelScope.launch {
            repository.updateNotificationPreferences(uid, preferences.copy(userId = uid))
                .onSuccess { _statusBanner.value = "Notification preferences updated" }
                .onFailure { _statusBanner.value = it.message ?: "Failed to save notification settings" }
        }
    }

    fun unblockUser(targetUserId: String) {
        val uid = currentUser.value?.id ?: return
        viewModelScope.launch {
            repository.unblockUser(uid, targetUserId)
                .onSuccess { _statusBanner.value = "User unblocked" }
                .onFailure { _statusBanner.value = it.message ?: "Failed to unblock user" }
        }
    }

    fun deleteAccount() {
        val uid = currentUser.value?.id ?: return
        viewModelScope.launch {
            repository.deleteAccount(uid)
                .onSuccess { _statusBanner.value = "Account deleted permanently" }
                .onFailure { _statusBanner.value = it.message ?: "Failed to delete account" }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    class Factory(private val repository: CreatorPaisaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(repository) as T
        }
    }
}
