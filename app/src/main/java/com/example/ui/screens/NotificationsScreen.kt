package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.data.model.NotificationCategoryFilter
import com.example.data.model.NotificationPreferences
import com.example.data.model.NotificationType
import com.example.data.model.formatNotificationTimestamp
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.MediaImage
import com.example.ui.viewmodel.NotificationsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    onBack: () -> Unit,
    onSelectCategory: (NotificationCategoryFilter) -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearAll: () -> Unit,
    onUpdatePreferences: (NotificationPreferences) -> Unit,
    onOpenVideo: (videoId: String, commentId: String?) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val countsByCategory = remember(uiState.allNotifications) {
        mapOf(
            NotificationCategoryFilter.ALL to uiState.allNotifications.size,
            NotificationCategoryFilter.LIKES to uiState.allNotifications.count { it.type == NotificationType.LIKE },
            NotificationCategoryFilter.COMMENTS to uiState.allNotifications.count { it.type == NotificationType.COMMENT },
            NotificationCategoryFilter.REPLIES to uiState.allNotifications.count { it.type == NotificationType.REPLY },
            NotificationCategoryFilter.SUBSCRIBERS to uiState.allNotifications.count { it.type == NotificationType.SUBSCRIPTION },
            NotificationCategoryFilter.VIDEO_UPDATES to uiState.allNotifications.count { it.type == NotificationType.VIDEO_UPLOAD }
        )
    }

    Scaffold(
        modifier = Modifier.testTag("notifications_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.notificationsTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = uiState.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("notifications_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.unreadCount > 0) {
                        IconButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.testTag("mark_all_notifications_read_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = strings.markAllRead)
                        }
                    }
                    if (uiState.allNotifications.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllConfirm = true },
                            modifier = Modifier.testTag("clear_all_notifications_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = strings.clearAllNotifications,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("open_notification_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.notificationSettingsTitle
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
            ) {
                // Category filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("notifications_category_row")
                ) {
                    items(NotificationCategoryFilter.entries, key = { it.name }) { filter ->
                        val label = when (filter) {
                            NotificationCategoryFilter.ALL -> strings.notifCatAll
                            NotificationCategoryFilter.LIKES -> strings.notifCatLikes
                            NotificationCategoryFilter.COMMENTS -> strings.notifCatComments
                            NotificationCategoryFilter.REPLIES -> strings.notifCatReplies
                            NotificationCategoryFilter.SUBSCRIBERS -> strings.notifCatSubscribers
                            NotificationCategoryFilter.VIDEO_UPDATES -> strings.notifCatVideoUpdates
                        }
                        val count = countsByCategory[filter] ?: 0
                        FilterChip(
                            selected = uiState.selectedCategory == filter,
                            onClick = { onSelectCategory(filter) },
                            label = {
                                Text(if (count > 0 && filter != NotificationCategoryFilter.ALL) "$label ($count)" else label)
                            },
                            modifier = Modifier.testTag("notif_filter_${filter.name}")
                        )
                    }
                }

                if (uiState.filteredNotifications.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("notifications_empty_state")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.noNotificationsYet,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("notifications_list")
                    ) {
                        items(
                            uiState.filteredNotifications,
                            key = { it.id },
                            contentType = { "notification" }
                        ) { item ->
                            NotificationItemCard(
                                item = item,
                                currentLanguage = currentLanguage,
                                onOpen = {
                                    onMarkRead(item.id)
                                    if (item.videoId != null) {
                                        onOpenVideo(item.videoId, item.commentId)
                                    } else {
                                        onOpenChannel(item.actorId)
                                    }
                                },
                                onMarkRead = { onMarkRead(item.id) },
                                onDelete = { onDeleteNotification(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(strings.clearAllNotifications) },
            text = { Text(strings.confirmClearAllNotifications) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_all_notifications_button")
                ) {
                    Text(strings.clearAllNotifications)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showSettingsDialog) {
        NotificationSettingsDialog(
            preferences = uiState.preferences,
            onDismiss = { showSettingsDialog = false },
            onUpdatePreferences = onUpdatePreferences
        )
    }
}

@Composable
private fun NotificationItemCard(
    item: AppNotification,
    currentLanguage: AppLanguage,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val typeIcon = when (item.type) {
        NotificationType.LIKE -> Icons.Default.Favorite
        NotificationType.COMMENT -> Icons.AutoMirrored.Filled.Comment
        NotificationType.REPLY -> Icons.AutoMirrored.Filled.Reply
        NotificationType.SUBSCRIPTION -> Icons.Default.PersonAdd
        NotificationType.VIDEO_UPLOAD -> Icons.Default.VideoLibrary
    }
    val typeTint = when (item.type) {
        NotificationType.LIKE -> MaterialTheme.colorScheme.primary
        NotificationType.COMMENT -> MaterialTheme.colorScheme.secondary
        NotificationType.REPLY -> MaterialTheme.colorScheme.tertiary
        NotificationType.SUBSCRIPTION -> MaterialTheme.colorScheme.tertiary
        NotificationType.VIDEO_UPLOAD -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isRead) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (item.isRead) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_card_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Sender avatar with type badge overlay
            Box(modifier = Modifier.size(48.dp)) {
                CreatorAvatar(
                    uri = item.actorAvatarUri,
                    contentDescription = item.actorName,
                    size = 44.dp
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, typeTint, CircleShape)
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = item.type.name,
                        tint = typeTint,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!item.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .testTag("notification_unread_dot_${item.id}")
                        )
                    }
                    Text(
                        text = item.actorName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = if (item.isRead) strings.notificationsStatusRead else strings.notificationsStatusUnread,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isRead) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatNotificationTimestamp(
                        item.createdAt,
                        isHindi = currentLanguage == AppLanguage.HINDI
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.videoThumbnailUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                MediaImage(
                    uri = item.videoThumbnailUri,
                    contentDescription = item.message,
                    modifier = Modifier
                        .width(72.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("notification_thumb_${item.id}")
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!item.isRead) {
                    IconButton(
                        onClick = onMarkRead,
                        modifier = Modifier.testTag("mark_read_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = strings.markAsRead,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_notification_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = strings.deleteNotification,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationSettingsDialog(
    preferences: NotificationPreferences,
    onDismiss: () -> Unit,
    onUpdatePreferences: (NotificationPreferences) -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.notificationSettingsTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("notification_settings_dialog")
            ) {
                PreferenceToggleRow(
                    label = strings.likeNotificationsPref,
                    checked = preferences.likesEnabled,
                    testTag = "notif_pref_likes_switch",
                    onCheckedChange = {
                        onUpdatePreferences(preferences.copy(likesEnabled = it))
                    }
                )
                PreferenceToggleRow(
                    label = strings.commentNotificationsPref,
                    checked = preferences.commentsEnabled,
                    testTag = "notif_pref_comments_switch",
                    onCheckedChange = {
                        onUpdatePreferences(preferences.copy(commentsEnabled = it))
                    }
                )
                PreferenceToggleRow(
                    label = strings.replyNotificationsPref,
                    checked = preferences.repliesEnabled,
                    testTag = "notif_pref_replies_switch",
                    onCheckedChange = {
                        onUpdatePreferences(preferences.copy(repliesEnabled = it))
                    }
                )
                PreferenceToggleRow(
                    label = strings.subscriberNotificationsPref,
                    checked = preferences.subscribersEnabled,
                    testTag = "notif_pref_subscribers_switch",
                    onCheckedChange = {
                        onUpdatePreferences(preferences.copy(subscribersEnabled = it))
                    }
                )
                PreferenceToggleRow(
                    label = strings.videoUploadNotificationsPref,
                    checked = preferences.videoUploadsEnabled,
                    testTag = "notif_pref_uploads_switch",
                    onCheckedChange = {
                        onUpdatePreferences(preferences.copy(videoUploadsEnabled = it))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_notification_settings_button")
            ) {
                Text(strings.saveProfile)
            }
        }
    )
}

@Composable
private fun PreferenceToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
