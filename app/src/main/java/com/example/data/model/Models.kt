package com.example.data.model

import java.security.MessageDigest
import java.security.SecureRandom

enum class UserRole {
    USER,
    CREATOR,
    ADMIN
}

enum class VideoFormat {
    SHORT,
    LONG
}

enum class VideoCategory(val displayEn: String, val displayHi: String) {
    ALL("All", "सभी"),
    TECH("Tech", "टेक"),
    FOOD("Food", "खान-पान"),
    TRAVEL("Travel", "यात्रा"),
    EDUCATION("Education", "शिक्षा"),
    ENTERTAINMENT("Entertainment", "मनोरंजन"),
    MUSIC("Music", "संगीत")
}

enum class FeedSortOrder {
    LATEST,
    MOST_VIEWED,
    MOST_LIKED
}

enum class NotificationType {
    LIKE,
    COMMENT,
    REPLY,
    SUBSCRIPTION,
    VIDEO_UPLOAD
}

enum class NotificationCategoryFilter {
    ALL,
    LIKES,
    COMMENTS,
    REPLIES,
    SUBSCRIBERS,
    VIDEO_UPDATES
}

enum class ErrorCategory {
    UPLOAD,
    PLAYBACK,
    AUTH
}

enum class ReportStatus {
    OPEN,
    RESOLVED
}

object UploadLimits {
    const val MAX_SHORT_DURATION_SECONDS = 180L // 3 minutes
    const val MAX_LONG_DURATION_SECONDS = 1800L // 30 minutes
    const val MAX_DAILY_UPLOADS = 5
    const val MAX_SHORT_FILE_BYTES = 50L * 1024L * 1024L // 50 MB
    const val MAX_LONG_FILE_BYTES = 250L * 1024L * 1024L // 250 MB
    const val GENUINE_VIEW_COOLDOWN_MS = 24L * 60L * 60L * 1000L // 24h anti-fake-view guard
    const val GENUINE_VIEW_MIN_PLAYBACK_MS = 3000L // 3 seconds genuine watch time
}

/**
 * Public-safe user profile representation. Never exposes passwordHash, passwordSalt, or private credentials.
 */
data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String = username,
    val bio: String,
    val channelDescription: String = bio,
    val avatarUri: String,
    val bannerUri: String = "drawable://img_banner_studio",
    val role: UserRole,
    val isApprovedUploader: Boolean,
    val isSuspended: Boolean,
    val subscriberCount: Long,
    val createdAt: Long,
    val emailMasked: String = "",
    val isPrivateProfile: Boolean = false,
    val hideSubscriberCount: Boolean = false
)

data class CreatorPlaylist(
    val id: String,
    val creatorId: String,
    val title: String,
    val description: String,
    val thumbnailUri: String,
    val isPublic: Boolean,
    val videoCount: Int,
    val videos: List<VideoItem> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

data class CreatorDashboardStats(
    val creator: UserProfile,
    val totalUploads: Int,
    val longVideoCount: Int,
    val shortVideoCount: Int,
    val totalGenuineViews: Long,
    val totalLikes: Long,
    val totalComments: Long,
    val subscriberCount: Long,
    val dailyUploadCount: Int,
    val recentUploads: List<VideoItem>,
    val allVideos: List<VideoItem>
)

data class VideoItem(
    val id: String,
    val creatorId: String,
    val creatorName: String,
    val creatorAvatarUri: String,
    val title: String,
    val description: String,
    val format: VideoFormat,
    val category: VideoCategory = VideoCategory.ENTERTAINMENT,
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
    val isLikedByCurrentUser: Boolean = false,
    val tags: List<String> = emptyList()
)

data class VideoComment(
    val id: String,
    val videoId: String,
    val userId: String,
    val username: String,
    val avatarUri: String,
    val text: String,
    val createdAt: Long,
    val parentCommentId: String? = null
)

data class AppNotification(
    val id: String,
    val recipientUserId: String,
    val actorId: String,
    val actorName: String,
    val actorAvatarUri: String,
    val type: NotificationType,
    val videoId: String?,
    val videoThumbnailUri: String? = null,
    val commentId: String? = null,
    val message: String,
    val isRead: Boolean,
    val createdAt: Long
)

data class NotificationPreferences(
    val userId: String,
    val likesEnabled: Boolean = true,
    val commentsEnabled: Boolean = true,
    val repliesEnabled: Boolean = true,
    val subscribersEnabled: Boolean = true,
    val videoUploadsEnabled: Boolean = true,
    val updatedAt: Long = 0L
)

data class ContentReport(
    val id: String,
    val videoId: String,
    val videoTitle: String,
    val reporterId: String,
    val reason: String,
    val status: ReportStatus,
    val createdAt: Long
)

data class AppErrorLog(
    val id: String,
    val category: ErrorCategory,
    val message: String,
    val userId: String?,
    val videoId: String?,
    val createdAt: Long
)

data class AppStatistics(
    val totalUsers: Int,
    val approvedCreators: Int,
    val totalVideos: Int,
    val activeVideos: Int,
    val totalViews: Long,
    val openReports: Int,
    val totalErrors: Int
)

data class UploadDraft(
    val title: String,
    val description: String,
    val format: VideoFormat,
    val category: VideoCategory = VideoCategory.ENTERTAINMENT,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val thumbnailUri: String,
    val videoUri: String,
    val isPublic: Boolean = true,
    val isPublished: Boolean = true,
    val tags: List<String> = emptyList()
)

sealed interface UploadProgressState {
    data object Idle : UploadProgressState
    data class Uploading(val progress: Float, val stageLabel: String) : UploadProgressState
    data class Success(val videoId: String) : UploadProgressState
    data class Cancelled(val message: String) : UploadProgressState
    data class Failure(val message: String) : UploadProgressState
}

object PasswordHasher {
    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean {
        return hash(password, salt) == expectedHash
    }
}

fun maskEmail(email: String): String {
    val parts = email.split("@")
    if (parts.size != 2) return "***"
    val local = parts[0]
    val visible = if (local.length <= 2) local.take(1) else local.take(2)
    return "$visible***@${parts[1]}"
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

fun formatUploadDate(createdAtMillis: Long, isHindi: Boolean = false, nowMillis: Long = System.currentTimeMillis()): String {
    val diffMs = (nowMillis - createdAtMillis).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val days = diffMs / 86_400_000L
    return when {
        minutes < 60L -> if (isHindi) "${minutes.coerceAtLeast(1L)} मि. पहले" else "${minutes.coerceAtLeast(1L)}m ago"
        hours < 24L -> if (isHindi) "$hours घंटे पहले" else "${hours}h ago"
        days < 7L -> if (isHindi) "$days दिन पहले" else "${days}d ago"
        else -> {
            val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
            fmt.format(java.util.Date(createdAtMillis))
        }
    }
}

fun formatJoinDate(createdAtMillis: Long): String {
    val fmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.ENGLISH)
    return fmt.format(java.util.Date(createdAtMillis))
}

fun formatNotificationTimestamp(
    createdAtMillis: Long,
    isHindi: Boolean = false,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val relative = formatUploadDate(createdAtMillis, isHindi, nowMillis)
    val timeFmt = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.ENGLISH)
    return "$relative · ${timeFmt.format(java.util.Date(createdAtMillis))}"
}

fun normalizeTag(raw: String): String {
    return raw.trim()
        .removePrefix("#")
        .lowercase()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("[^a-z0-9_\\-]"), "")
        .replace(Regex("[-_]{2,}"), "-")
        .trim('-', '_')
        .take(24)
}

fun normalizeTagList(rawTags: List<String>): List<String> {
    return rawTags
        .map(::normalizeTag)
        .filter { it.isNotBlank() }
        .distinct()
        .take(10)
}

fun parseTagsInput(input: String): List<String> {
    if (input.isBlank()) return emptyList()
    return input.split(',', '\n', ';')
        .flatMap { segment ->
            val trimmed = segment.trim()
            if (trimmed.contains('#')) trimmed.split(Regex("\\s+")) else listOf(trimmed)
        }
        .map(::normalizeTag)
        .filter { it.isNotBlank() }
        .distinct()
        .take(10)
}

fun resolveVideoTags(rawTags: List<String>, category: VideoCategory): List<String> {
    val normalized = normalizeTagList(rawTags)
    if (normalized.isNotEmpty()) return normalized
    return if (category != VideoCategory.ALL) {
        listOf(normalizeTag(category.name)).filter { it.isNotBlank() }
    } else {
        emptyList()
    }
}

