package com.example.data.remote

import com.example.data.model.AppErrorLog
import com.example.data.model.AppNotification
import com.example.data.model.ContentReport
import com.example.data.model.CreatorPlaylist
import com.example.data.model.ErrorCategory
import com.example.data.model.NotificationPreferences
import com.example.data.model.NotificationType
import com.example.data.model.ReportStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoCategory
import com.example.data.model.VideoComment
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.normalizeTagList
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior

internal object FirestoreMappers {
    private val ESTIMATE = ServerTimestampBehavior.ESTIMATE

    fun Map<String, Any?>.strVal(key: String, default: String = ""): String =
        this[key] as? String ?: default

    fun Map<String, Any?>.optStrVal(key: String): String? =
        this[key] as? String

    fun Map<String, Any?>.longVal(key: String, default: Long = 0L): Long =
        (this[key] as? Number)?.toLong() ?: default

    fun Map<String, Any?>.boolVal(key: String, default: Boolean = false): Boolean =
        this[key] as? Boolean ?: default

    inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default

    fun DocumentSnapshot.tsMillis(key: String, fallback: Long = System.currentTimeMillis()): Long =
        getTimestamp(key, ESTIMATE)?.toDate()?.time ?: fallback

    fun normalizeHandle(raw: String, fallbackSuffix: String = ""): String {
        val cleaned = raw.trim()
            .removePrefix("@")
            .lowercase()
            .replace(Regex("[^a-z0-9_\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        val base = when {
            cleaned.length >= 3 -> cleaned
            fallbackSuffix.isNotBlank() -> "creator_${cleaned}_${fallbackSuffix}".replace(Regex("_+"), "_")
            else -> "creator_${cleaned}".padEnd(3, '0')
        }
        return base.take(40).let { if (it.length < 3) "user_${fallbackSuffix.take(6).ifBlank { "cp1" }}" else it }
    }

    fun toUserProfile(snapshot: DocumentSnapshot, emailMasked: String = ""): UserProfile? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        val uid = data.strVal("uid", snapshot.id).ifBlank { snapshot.id }
        val username = data.strVal("username", "creator").ifBlank { "creator" }
        return UserProfile(
            id = uid,
            username = username,
            displayName = data.strVal("displayName", username).ifBlank { username },
            bio = data.strVal("bio", ""),
            channelDescription = data.strVal("channelDescription", data.strVal("bio", "")),
            avatarUri = data.strVal("avatarUri", "drawable://img_creator_paisa_logo"),
            bannerUri = data.strVal("bannerUri", "drawable://img_banner_studio"),
            role = safeEnum(data.optStrVal("role"), UserRole.USER),
            isApprovedUploader = data.boolVal("isApprovedUploader", false),
            isSuspended = data.boolVal("isSuspended", false),
            subscriberCount = data.longVal("subscriberCount", 0L).coerceAtLeast(0L),
            createdAt = snapshot.tsMillis("createdAt"),
            emailMasked = emailMasked,
            isPrivateProfile = data.boolVal("isPrivateProfile", false),
            hideSubscriberCount = data.boolVal("hideSubscriberCount", false)
        )
    }

    fun toVideoItem(snapshot: DocumentSnapshot, likedIds: Set<String> = emptySet()): VideoItem? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        val vid = snapshot.id
        return VideoItem(
            id = vid,
            creatorId = data.strVal("creatorId"),
            creatorName = data.strVal("creatorName", "Creator"),
            creatorAvatarUri = data.strVal("creatorAvatarUri", "drawable://img_creator_paisa_logo"),
            title = data.strVal("title"),
            description = data.strVal("description"),
            format = safeEnum(data.optStrVal("format"), VideoFormat.LONG),
            category = safeEnum(data.optStrVal("category"), VideoCategory.ENTERTAINMENT),
            durationSeconds = data.longVal("durationSeconds", 0L),
            fileSizeBytes = data.longVal("fileSizeBytes", 0L),
            thumbnailUri = data.strVal("thumbnailUri", "drawable://img_banner_studio"),
            videoUri = data.strVal("videoUri"),
            viewCount = data.longVal("viewCount", 0L).coerceAtLeast(0L),
            likeCount = data.longVal("likeCount", 0L).coerceAtLeast(0L),
            commentCount = data.longVal("commentCount", 0L).coerceAtLeast(0L),
            reportCount = data.longVal("reportCount", 0L).coerceAtLeast(0L),
            isRemoved = data.boolVal("isRemoved", false),
            removalReason = data.optStrVal("removalReason"),
            createdAt = snapshot.tsMillis("createdAt"),
            isLikedByCurrentUser = likedIds.contains(vid),
            tags = (data["tags"] as? List<*>)?.mapNotNull { it as? String }?.let(::normalizeTagList).orEmpty()
        )
    }

    fun toComment(snapshot: DocumentSnapshot): VideoComment? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        return VideoComment(
            id = snapshot.id,
            videoId = data.strVal("videoId"),
            userId = data.strVal("userId"),
            username = data.strVal("username", "User"),
            avatarUri = data.strVal("avatarUri", "drawable://img_creator_paisa_logo"),
            text = data.strVal("text"),
            createdAt = snapshot.tsMillis("createdAt"),
            parentCommentId = data.optStrVal("parentCommentId")
        )
    }

    fun toNotification(snapshot: DocumentSnapshot): AppNotification? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        return AppNotification(
            id = snapshot.id,
            recipientUserId = data.strVal("recipientUserId"),
            actorId = data.strVal("actorId"),
            actorName = data.strVal("actorName", "Creator"),
            actorAvatarUri = data.strVal("actorAvatarUri", "drawable://img_creator_paisa_logo"),
            type = safeEnum(data.optStrVal("type"), NotificationType.LIKE),
            videoId = data.optStrVal("videoId"),
            videoThumbnailUri = data.optStrVal("videoThumbnailUri"),
            commentId = data.optStrVal("commentId"),
            message = data.strVal("message"),
            isRead = data.boolVal("isRead", false),
            createdAt = snapshot.tsMillis("createdAt")
        )
    }

    fun toNotificationPreferences(snapshot: DocumentSnapshot, fallbackUserId: String): NotificationPreferences {
        if (!snapshot.exists()) return NotificationPreferences(userId = fallbackUserId)
        val data = snapshot.data ?: return NotificationPreferences(userId = fallbackUserId)
        return NotificationPreferences(
            userId = data.strVal("userId", fallbackUserId).ifBlank { fallbackUserId },
            likesEnabled = data.boolVal("likesEnabled", true),
            commentsEnabled = data.boolVal("commentsEnabled", true),
            repliesEnabled = data.boolVal("repliesEnabled", true),
            subscribersEnabled = data.boolVal("subscribersEnabled", true),
            videoUploadsEnabled = data.boolVal("videoUploadsEnabled", true),
            updatedAt = snapshot.tsMillis("updatedAt", 0L)
        )
    }

    fun toPlaylist(snapshot: DocumentSnapshot, videoMap: Map<String, VideoItem>): CreatorPlaylist? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        val rawIds = (data["videoIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val resolvedVideos = rawIds.mapNotNull { videoMap[it] }
        val storedThumb = data.strVal("thumbnailUri", "")
        val effectiveThumb = storedThumb.ifBlank {
            resolvedVideos.firstOrNull()?.thumbnailUri ?: "drawable://img_banner_studio"
        }
        return CreatorPlaylist(
            id = snapshot.id,
            creatorId = data.strVal("creatorId"),
            title = data.strVal("title"),
            description = data.strVal("description"),
            thumbnailUri = effectiveThumb,
            isPublic = data.boolVal("isPublic", true),
            videoCount = resolvedVideos.size,
            videos = resolvedVideos,
            createdAt = snapshot.tsMillis("createdAt"),
            updatedAt = snapshot.tsMillis("updatedAt")
        )
    }

    fun toReport(snapshot: DocumentSnapshot): ContentReport? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        return ContentReport(
            id = snapshot.id,
            videoId = data.strVal("videoId"),
            videoTitle = data.strVal("videoTitle"),
            reporterId = data.strVal("reporterId"),
            reason = data.strVal("reason"),
            status = safeEnum(data.optStrVal("status"), ReportStatus.OPEN),
            createdAt = snapshot.tsMillis("createdAt")
        )
    }

    fun toErrorLog(snapshot: DocumentSnapshot): AppErrorLog? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        return AppErrorLog(
            id = snapshot.id,
            category = safeEnum(data.optStrVal("category"), ErrorCategory.UPLOAD),
            message = data.strVal("message"),
            userId = data.optStrVal("userId"),
            videoId = data.optStrVal("videoId"),
            createdAt = snapshot.tsMillis("createdAt")
        )
    }
}
