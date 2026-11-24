package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.VideoCategory

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val username: String,
    val displayName: String = username,
    val bio: String,
    val channelDescription: String = bio,
    val avatarUri: String,
    val bannerUri: String = "drawable://img_banner_studio",
    val passwordHash: String,
    val passwordSalt: String,
    val role: String, // UserRole.name
    val isApprovedUploader: Boolean,
    val isSuspended: Boolean,
    val subscriberCount: Long,
    val createdAt: Long,
    val isPrivateProfile: Boolean = false,
    val hideSubscriberCount: Boolean = false
)

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["isRemoved", "createdAt"]),
        Index(value = ["creatorId", "createdAt"])
    ]
)
data class VideoEntity(
    @PrimaryKey val id: String,
    val creatorId: String,
    val creatorName: String,
    val creatorAvatarUri: String,
    val title: String,
    val description: String,
    val format: String, // VideoFormat.name
    val category: String = VideoCategory.ENTERTAINMENT.name,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val thumbnailUri: String,
    val videoUri: String,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val reportCount: Long,
    val isRemoved: Boolean,
    val removalReason: String?,
    val createdAt: Long,
    val isPublic: Boolean = true,
    val tagsRaw: String = ""
)

@Entity(
    tableName = "video_likes",
    primaryKeys = ["videoId", "userId"]
)
data class LikeEntity(
    val videoId: String,
    val userId: String,
    val createdAt: Long
)

@Entity(
    tableName = "comments",
    indices = [
        Index(value = ["videoId", "createdAt"]),
        Index(value = ["parentCommentId"])
    ]
)
data class CommentEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val userId: String,
    val username: String,
    val avatarUri: String,
    val text: String,
    val createdAt: Long,
    val parentCommentId: String? = null
)

@Entity(
    tableName = "subscriptions",
    primaryKeys = ["creatorId", "subscriberId"]
)
data class SubscriptionEntity(
    val creatorId: String,
    val subscriberId: String,
    val createdAt: Long
)

@Entity(
    tableName = "video_views",
    primaryKeys = ["videoId", "viewerId"]
)
data class VideoViewEntity(
    val videoId: String,
    val viewerId: String,
    val lastCountedAt: Long
)

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["recipientUserId", "isRead", "createdAt"]),
        Index(value = ["dedupKey"], unique = true)
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val recipientUserId: String,
    val actorId: String,
    val actorName: String,
    val actorAvatarUri: String,
    val type: String, // NotificationType.name
    val videoId: String?,
    val videoThumbnailUri: String? = null,
    val commentId: String? = null,
    val dedupKey: String? = null,
    val message: String,
    val isRead: Boolean,
    val createdAt: Long
)

@Entity(tableName = "notification_preferences")
data class NotificationPreferencesEntity(
    @PrimaryKey val userId: String,
    val likesEnabled: Boolean = true,
    val commentsEnabled: Boolean = true,
    val repliesEnabled: Boolean = true,
    val subscribersEnabled: Boolean = true,
    val videoUploadsEnabled: Boolean = true,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "blocked_users",
    primaryKeys = ["blockerId", "blockedUserId"],
    indices = [Index(value = ["blockerId"])]
)
data class BlockedUserEntity(
    val blockerId: String,
    val blockedUserId: String,
    val createdAt: Long
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val videoTitle: String,
    val reporterId: String,
    val reason: String,
    val status: String, // ReportStatus.name
    val createdAt: Long
)

@Entity(tableName = "error_logs")
data class ErrorLogEntity(
    @PrimaryKey val id: String,
    val category: String, // ErrorCategory.name
    val message: String,
    val userId: String?,
    val videoId: String?,
    val createdAt: Long
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["userId", "videoId"],
    indices = [Index(value = ["userId", "watchedAt"])]
)
data class WatchHistoryEntity(
    val userId: String,
    val videoId: String,
    val lastPositionMs: Long = 0L,
    val watchedAt: Long
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["creatorId", "updatedAt"])]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val creatorId: String,
    val title: String,
    val description: String,
    val thumbnailUri: String,
    val isPublic: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "videoId"],
    indices = [Index(value = ["playlistId", "position"])]
)
data class PlaylistVideoEntity(
    val playlistId: String,
    val videoId: String,
    val position: Int,
    val addedAt: Long
)
