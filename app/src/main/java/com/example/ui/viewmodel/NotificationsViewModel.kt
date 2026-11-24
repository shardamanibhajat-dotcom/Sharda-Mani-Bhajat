package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.NotificationCategoryFilter
import com.example.data.model.NotificationPreferences
import com.example.data.model.NotificationType
import com.example.data.repository.CreatorPaisaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val allNotifications: List<AppNotification> = emptyList(),
    val filteredNotifications: List<AppNotification> = emptyList(),
    val selectedCategory: NotificationCategoryFilter = NotificationCategoryFilter.ALL,
    val unreadCount: Int = 0,
    val preferences: NotificationPreferences = NotificationPreferences(userId = ""),
    val errorMessage: String? = null
)

class NotificationsViewModel(
    private val userId: String,
    private val repository: CreatorPaisaRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(NotificationCategoryFilter.ALL)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val notifications: StateFlow<List<AppNotification>> =
        repository.observeNotificationsForUser(userId, userId)
            .catchFirestoreErrors("NotificationsVM.notifications", emptyList())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val uiState: StateFlow<NotificationsUiState> = combine(
        notifications,
        repository.observeNotificationPreferences(userId)
            .catchFirestoreErrors("NotificationsVM.prefs", NotificationPreferences(userId = userId)),
        _selectedCategory,
        _message
    ) { all, prefs, category, err ->
        val filtered = when (category) {
            NotificationCategoryFilter.ALL -> all
            NotificationCategoryFilter.LIKES -> all.filter { it.type == NotificationType.LIKE }
            NotificationCategoryFilter.COMMENTS -> all.filter { it.type == NotificationType.COMMENT }
            NotificationCategoryFilter.REPLIES -> all.filter { it.type == NotificationType.REPLY }
            NotificationCategoryFilter.SUBSCRIBERS -> all.filter { it.type == NotificationType.SUBSCRIPTION }
            NotificationCategoryFilter.VIDEO_UPDATES -> all.filter { it.type == NotificationType.VIDEO_UPLOAD }
        }
        NotificationsUiState(
            allNotifications = all,
            filteredNotifications = filtered,
            selectedCategory = category,
            unreadCount = all.count { !it.isRead },
            preferences = prefs,
            errorMessage = err
        )
    }
        .catchFirestoreErrors(
            "NotificationsVM.uiState",
            NotificationsUiState(preferences = NotificationPreferences(userId = userId))
        )
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            NotificationsUiState(preferences = NotificationPreferences(userId = userId))
        )

    fun selectCategory(category: NotificationCategoryFilter) {
        _selectedCategory.value = category
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(userId, notificationId)
                .onFailure { _message.value = it.message ?: "Unable to mark notification as read." }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching {
                repository.markAllNotificationsRead(userId)
            }.onFailure {
                _message.value = it.message ?: "Unable to update notifications."
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            repository.deleteNotification(userId, notificationId)
                .onSuccess { _message.value = "Notification deleted." }
                .onFailure { _message.value = it.message ?: "Unable to delete notification." }
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications(userId)
                .onSuccess { _message.value = "All notifications cleared." }
                .onFailure { _message.value = it.message ?: "Unable to clear notifications." }
        }
    }

    fun updatePreferences(updated: NotificationPreferences) {
        viewModelScope.launch {
            repository.updateNotificationPreferences(userId, updated.copy(userId = userId))
                .onFailure { _message.value = it.message ?: "Unable to save notification settings." }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(
        private val userId: String,
        private val repository: CreatorPaisaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationsViewModel(userId, repository) as T
        }
    }
}
