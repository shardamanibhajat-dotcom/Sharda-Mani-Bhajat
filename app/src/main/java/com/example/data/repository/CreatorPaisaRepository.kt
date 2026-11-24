package com.example.data.repository

import com.example.data.local.BlockedUserEntity
import com.example.data.local.CommentEntity
import com.example.data.local.CreatorPaisaDao
import com.example.data.local.CreatorPaisaDatabase
import com.example.data.local.ErrorLogEntity
import com.example.data.local.MediaStorage
import com.example.data.local.NotificationEntity
import com.example.data.local.NotificationPreferencesEntity
import com.example.data.local.PassthroughMediaStorage
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistVideoEntity
import com.example.data.local.ReportEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.UserEntity
import com.example.data.local.VideoEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.AppErrorLog
import com.example.data.model.AppNotification
import com.example.data.model.AppStatistics
import com.example.data.model.ContentReport
import com.example.data.model.CreatorDashboardStats
import com.example.data.model.CreatorPlaylist
import com.example.data.model.ErrorCategory
import com.example.data.model.NotificationPreferences
import com.example.data.model.NotificationType
import com.example.data.model.PasswordHasher
import com.example.data.model.ReportStatus
import com.example.data.model.UploadDraft
import com.example.data.model.UploadLimits
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoCategory
import com.example.data.model.VideoComment
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.maskEmail
import com.example.data.model.normalizeTag
import com.example.data.model.parseTagsInput
import com.example.data.model.resolveVideoTags
import com.example.localization.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

interface CreatorPaisaRepository {
    val currentUser: Flow<UserProfile?>
    val appLanguage: Flow<AppLanguage>
    val isDarkTheme: Flow<Boolean>

    suspend fun ensureSeeded()
    suspend fun setAppLanguage(language: AppLanguage)
    suspend fun setDarkTheme(enabled: Boolean)

    suspend fun login(email: String, password: String): Result<UserProfile>
    suspend fun signUp(
        email: String,
        username: String,
        password: String,
        bio: String,
        avatarUri: String,
        displayName: String = username
    ): Result<UserProfile>
    suspend fun logout()
    suspend fun updateProfile(userId: String, username: String, bio: String, avatarUri: String): Result<Unit>
    suspend fun updateProfile(
        requesterId: String,
        targetUserId: String,
        displayName: String,
        username: String,
        bio: String,
        channelDescription: String,
        avatarUri: String,
        bannerUri: String
    ): Result<Unit>
    suspend fun updatePrivacySettings(userId: String, isPrivateProfile: Boolean, hideSubscriberCount: Boolean): Result<Unit>
    suspend fun deleteAccount(userId: String): Result<Unit>

    fun observeActiveVideos(): Flow<List<VideoItem>>
    fun observeVideosByTag(tag: String): Flow<List<VideoItem>>
    suspend fun searchVideos(query: String): Result<List<VideoItem>>
    fun observeVideoById(videoId: String): Flow<VideoItem?>
    fun observeVideosByCreator(creatorId: String): Flow<List<VideoItem>>
    fun observeAllVideosForCreatorStudio(creatorId: String): Flow<List<VideoItem>>
    fun observeCreatorProfile(creatorId: String): Flow<UserProfile?>
    fun observeAllCreators(): Flow<List<UserProfile>>
    fun observeSubscribedCreators(subscriberId: String): Flow<List<UserProfile>>
    fun observeIsSubscribed(creatorId: String, subscriberId: String): Flow<Boolean>
    fun observeComments(videoId: String): Flow<List<VideoComment>>

    // Notifications
    fun observeNotifications(userId: String): Flow<List<AppNotification>>
    fun observeNotificationsForUser(requesterId: String, targetUserId: String): Flow<List<AppNotification>>
    fun observeUnreadNotificationCount(userId: String): Flow<Int>
    suspend fun markNotificationRead(notificationId: String)
    suspend fun markNotificationRead(requesterId: String, notificationId: String): Result<Unit>
    suspend fun markAllNotificationsRead(userId: String)
    suspend fun deleteNotification(requesterId: String, notificationId: String): Result<Unit>
    suspend fun clearAllNotifications(requesterId: String): Result<Unit>
    fun observeNotificationPreferences(userId: String): Flow<NotificationPreferences>
    suspend fun updateNotificationPreferences(requesterId: String, preferences: NotificationPreferences): Result<Unit>

    // User Blocking
    fun observeBlockedUsers(blockerId: String): Flow<List<UserProfile>>
    fun observeIsUserBlocked(blockerId: String, targetUserId: String): Flow<Boolean>
    suspend fun blockUser(blockerId: String, targetUserId: String): Result<Unit>
    suspend fun unblockUser(blockerId: String, targetUserId: String): Result<Unit>

    // Creator Dashboard & Content Management (owner-only protected)
    fun observeCreatorDashboard(requesterId: String, creatorId: String): Flow<CreatorDashboardStats?>
    suspend fun updateOwnVideo(
        creatorId: String,
        videoId: String,
        title: String,
        description: String,
        category: VideoCategory,
        thumbnailUri: String? = null,
        tags: List<String>? = null
    ): Result<Unit>
    suspend fun deleteOwnVideo(creatorId: String, videoId: String): Result<Unit>

    // Playlists
    fun observePlaylistsForChannel(viewerId: String, creatorId: String): Flow<List<CreatorPlaylist>>
    suspend fun createPlaylist(
        requesterId: String,
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean
    ): Result<String>
    suspend fun updatePlaylist(
        requesterId: String,
        playlistId: String,
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean
    ): Result<Unit>
    suspend fun deletePlaylist(requesterId: String, playlistId: String): Result<Unit>
    suspend fun addVideoToPlaylist(requesterId: String, playlistId: String, videoId: String): Result<Unit>
    suspend fun removeVideoFromPlaylist(requesterId: String, playlistId: String, videoId: String): Result<Unit>

    suspend fun getDailyUploadCount(creatorId: String): Int
    suspend fun uploadVideo(
        creatorId: String,
        draft: UploadDraft,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<String>
    suspend fun recordGenuineView(videoId: String, viewerId: String): Boolean
    suspend fun toggleLike(videoId: String, userId: String): Result<Boolean>
    suspend fun addComment(videoId: String, userId: String, text: String, parentCommentId: String? = null): Result<Unit>
    suspend fun toggleSubscription(creatorId: String, subscriberId: String): Result<Boolean>
    suspend fun reportVideo(videoId: String, reporterId: String, reason: String): Result<Unit>
    suspend fun logError(category: ErrorCategory, message: String, userId: String? = null, videoId: String? = null)

    // Watch History
    suspend fun recordWatchHistory(userId: String, videoId: String, positionMs: Long = 0L)
    fun observeWatchHistory(userId: String): Flow<List<VideoItem>>
    suspend fun getSavedWatchPositionMs(userId: String, videoId: String): Long
    suspend fun clearWatchHistory(userId: String)

    // Admin operations (protected by role check)
    fun observeAllVideosForAdmin(requesterId: String): Flow<List<VideoItem>>
    fun observeAllUsersForAdmin(requesterId: String): Flow<List<UserProfile>>
    fun observeAllReportsForAdmin(requesterId: String): Flow<List<ContentReport>>
    fun observeAllErrorLogsForAdmin(requesterId: String): Flow<List<AppErrorLog>>
    fun observeAppStatistics(requesterId: String): Flow<AppStatistics?>

    suspend fun adminRemoveVideo(adminId: String, videoId: String, reason: String): Result<Unit>
    suspend fun adminRestoreVideo(adminId: String, videoId: String): Result<Unit>
    suspend fun adminSetUploaderApproval(adminId: String, targetUserId: String, approved: Boolean): Result<Unit>
    suspend fun adminSetUserSuspended(adminId: String, targetUserId: String, suspended: Boolean): Result<Unit>
    suspend fun adminResolveReport(adminId: String, reportId: String): Result<Unit>
}

class LocalCreatorPaisaRepository(
    private val dao: CreatorPaisaDao,
    private val prefs: SessionPreferences,
    private val mediaStorage: MediaStorage = PassthroughMediaStorage,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : CreatorPaisaRepository {

    override val appLanguage: Flow<AppLanguage> = prefs.appLanguage
    override val isDarkTheme: Flow<Boolean> = prefs.isDarkTheme

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentUser: Flow<UserProfile?> = prefs.currentUserId.flatMapLatest { uid ->
        if (uid.isNullOrBlank()) {
            flowOf(null)
        } else {
            dao.observeUserById(uid).map { it?.toDomain() }
        }
    }

    override suspend fun ensureSeeded() {
        CreatorPaisaDatabase.seedIfNeeded(dao)
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        prefs.setAppLanguage(language)
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        prefs.setDarkTheme(enabled)
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> {
        ensureSeeded()
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty."))
        }
        val user = dao.getUserByEmail(trimmedEmail)
        if (user == null || !PasswordHasher.verify(password, user.passwordSalt, user.passwordHash)) {
            logError(ErrorCategory.AUTH, "Failed login attempt for ${maskEmail(trimmedEmail)}")
            return Result.failure(IllegalArgumentException("Invalid email or password."))
        }
        if (user.isSuspended) {
            return Result.failure(IllegalStateException("This account has been suspended by moderation."))
        }
        prefs.setCurrentUserId(user.id)
        return Result.success(user.toDomain())
    }

    override suspend fun signUp(
        email: String,
        username: String,
        password: String,
        bio: String,
        avatarUri: String,
        displayName: String
    ): Result<UserProfile> {
        ensureSeeded()
        val cleanEmail = email.trim()
        val cleanUsername = username.trim().removePrefix("@").trim()
        val cleanDisplayName = displayName.trim().ifBlank { cleanUsername }
        val cleanBio = bio.trim().ifBlank { "Creator on Creator Paisa" }

        if (cleanEmail.isBlank() || !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Enter a valid email address."))
        }
        if (cleanUsername.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }
        if (dao.getUserByEmail(cleanEmail) != null) {
            return Result.failure(IllegalArgumentException("An account with this email already exists."))
        }
        if (dao.getUserByUsername(cleanUsername) != null) {
            return Result.failure(IllegalArgumentException("This username is already taken."))
        }

        val resolvedAvatar = if (avatarUri.isBlank()) {
            "drawable://img_creator_paisa_logo"
        } else {
            mediaStorage.storeImageMedia(avatarUri).getOrDefault(avatarUri)
        }

        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash(password, salt)
        val newUser = UserEntity(
            id = "user_${UUID.randomUUID().toString().take(8)}",
            email = cleanEmail,
            username = cleanUsername,
            displayName = cleanDisplayName.take(60),
            bio = cleanBio.take(180),
            channelDescription = cleanBio.take(1000),
            avatarUri = resolvedAvatar,
            bannerUri = "drawable://img_banner_studio",
            passwordHash = hash,
            passwordSalt = salt,
            role = UserRole.USER.name,
            isApprovedUploader = false, // Requires admin approval for upload testing
            isSuspended = false,
            subscriberCount = 0L,
            createdAt = timeProvider(),
            isPrivateProfile = false,
            hideSubscriberCount = false
        )
        dao.insertUser(newUser)
        prefs.setCurrentUserId(newUser.id)
        return Result.success(newUser.toDomain())
    }

    override suspend fun logout() {
        prefs.setCurrentUserId(null)
    }

    override suspend fun updateProfile(
        userId: String,
        username: String,
        bio: String,
        avatarUri: String
    ): Result<Unit> {
        val existing = dao.getUserById(userId)
            ?: return Result.failure(IllegalStateException("User not found."))
        return updateProfile(
            requesterId = userId,
            targetUserId = userId,
            displayName = existing.displayName,
            username = username,
            bio = bio,
            channelDescription = existing.channelDescription,
            avatarUri = avatarUri,
            bannerUri = existing.bannerUri
        )
    }

    override suspend fun updateProfile(
        requesterId: String,
        targetUserId: String,
        displayName: String,
        username: String,
        bio: String,
        channelDescription: String,
        avatarUri: String,
        bannerUri: String
    ): Result<Unit> {
        if (requesterId.isBlank() || requesterId != targetUserId) {
            return Result.failure(SecurityException("Only the channel owner can edit this profile."))
        }
        val existing = dao.getUserById(targetUserId)
            ?: return Result.failure(IllegalStateException("User not found."))
        if (existing.isSuspended) {
            return Result.failure(IllegalStateException("Suspended accounts cannot update profile details."))
        }

        val cleanHandle = username.trim().removePrefix("@").trim()
        val cleanDisplayName = displayName.trim().ifBlank { cleanHandle }
        if (cleanDisplayName.length < 2) {
            return Result.failure(IllegalArgumentException("Creator name must be at least 2 characters."))
        }
        if (cleanHandle.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters."))
        }
        if (cleanHandle.contains(" ")) {
            val looksLikeLegacyName = cleanHandle == existing.username
            if (!looksLikeLegacyName) {
                return Result.failure(IllegalArgumentException("Username handle cannot contain spaces."))
            }
        }
        val duplicate = dao.getUserByUsername(cleanHandle)
        if (duplicate != null && duplicate.id != targetUserId) {
            return Result.failure(IllegalArgumentException("This username is already taken. Try another one."))
        }

        val finalAvatar = if (avatarUri.isBlank() || avatarUri == existing.avatarUri) {
            existing.avatarUri
        } else {
            mediaStorage.storeImageMedia(avatarUri).getOrElse { return Result.failure(it) }
        }
        val finalBanner = if (bannerUri.isBlank() || bannerUri == existing.bannerUri) {
            existing.bannerUri
        } else {
            mediaStorage.storeImageMedia(bannerUri).getOrElse { return Result.failure(it) }
        }

        val cleanBio = bio.trim().take(180)
        val cleanDesc = channelDescription.trim().take(1200)
        val updated = existing.copy(
            displayName = cleanDisplayName.take(60),
            username = cleanHandle.take(40),
            bio = cleanBio,
            channelDescription = cleanDesc,
            avatarUri = finalAvatar,
            bannerUri = finalBanner
        )
        dao.updateUser(updated)
        dao.syncCreatorDetailsOnVideos(targetUserId, updated.displayName, finalAvatar)
        dao.syncCreatorDetailsOnComments(targetUserId, updated.displayName, finalAvatar)

        if (existing.avatarUri != finalAvatar) {
            mediaStorage.deleteStoredFileIfOwned(existing.avatarUri)
        }
        if (existing.bannerUri != finalBanner) {
            mediaStorage.deleteStoredFileIfOwned(existing.bannerUri)
        }
        return Result.success(Unit)
    }

    override suspend fun updatePrivacySettings(
        userId: String,
        isPrivateProfile: Boolean,
        hideSubscriberCount: Boolean
    ): Result<Unit> {
        dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User not found."))
        dao.updatePrivacySettings(userId, isPrivateProfile, hideSubscriberCount)
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(userId: String): Result<Unit> {
        val existing = dao.getUserById(userId)
            ?: return Result.failure(IllegalStateException("User not found."))
        val userVideos = dao.getVideosByCreatorOnce(userId)
        dao.deleteUserAccountTransaction(existing.id)
        userVideos.forEach { vid ->
            mediaStorage.deleteStoredFileIfOwned(vid.videoUri)
            mediaStorage.deleteStoredFileIfOwned(vid.thumbnailUri)
        }
        mediaStorage.deleteStoredFileIfOwned(existing.avatarUri)
        mediaStorage.deleteStoredFileIfOwned(existing.bannerUri)
        prefs.setCurrentUserId(null)
        return Result.success(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveVideos(): Flow<List<VideoItem>> {
        return prefs.currentUserId.flatMapLatest { uid ->
            val likesFlow = if (uid.isNullOrBlank()) flowOf(emptyList()) else dao.observeLikedVideoIds(uid)
            val blockedFlow = if (uid.isNullOrBlank()) flowOf(emptyList()) else dao.observeBlockedUserIds(uid)
            combine(dao.observeActiveVideos(), likesFlow, blockedFlow) { videos, likedIds, blockedIds ->
                val likedSet = likedIds.toHashSet()
                val blockedSet = blockedIds.toHashSet()
                videos
                    .filterNot { blockedSet.contains(it.creatorId) }
                    .map { it.toDomain(likedSet.contains(it.id)) }
            }
        }
    }

    override fun observeVideosByTag(tag: String): Flow<List<VideoItem>> {
        val cleanTag = normalizeTag(tag)
        if (cleanTag.isBlank()) return observeActiveVideos()
        return observeActiveVideos().map { list ->
            list.filter { video -> video.tags.contains(cleanTag) }
        }
    }

    override suspend fun searchVideos(query: String): Result<List<VideoItem>> {
        val clean = query.trim().lowercase()
        val active = observeActiveVideos().first()
        if (clean.isEmpty()) {
            return Result.success(active.sortedByDescending { it.createdAt })
        }
        val matched = active.filter { video ->
            video.title.lowercase().contains(clean) ||
                video.creatorName.lowercase().contains(clean) ||
                video.description.lowercase().contains(clean) ||
                video.tags.any { it.contains(clean.removePrefix("#")) }
        }.sortedWith(
            compareByDescending<VideoItem> {
                it.title.lowercase().startsWith(clean) || it.creatorName.lowercase().startsWith(clean)
            }.thenByDescending { it.createdAt }
        )
        return Result.success(matched)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVideoById(videoId: String): Flow<VideoItem?> {
        return prefs.currentUserId.flatMapLatest { uid ->
            val likesFlow = if (uid.isNullOrBlank()) flowOf(emptyList()) else dao.observeLikedVideoIds(uid)
            combine(dao.observeVideoById(videoId), likesFlow) { video, likedIds ->
                video?.toDomain(likedIds.contains(video.id))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVideosByCreator(creatorId: String): Flow<List<VideoItem>> {
        return prefs.currentUserId.flatMapLatest { uid ->
            val likesFlow = if (uid.isNullOrBlank()) flowOf(emptyList()) else dao.observeLikedVideoIds(uid)
            combine(dao.observeVideosByCreator(creatorId), likesFlow) { videos, likedIds ->
                val likedSet = likedIds.toHashSet()
                videos.map { it.toDomain(likedSet.contains(it.id)) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllVideosForCreatorStudio(creatorId: String): Flow<List<VideoItem>> {
        return prefs.currentUserId.flatMapLatest { uid ->
            val likesFlow = if (uid.isNullOrBlank()) flowOf(emptyList()) else dao.observeLikedVideoIds(uid)
            combine(dao.observeAllVideosByCreator(creatorId), likesFlow) { videos, likedIds ->
                val likedSet = likedIds.toHashSet()
                videos.map { it.toDomain(likedSet.contains(it.id)) }
            }
        }
    }

    override fun observeCreatorProfile(creatorId: String): Flow<UserProfile?> =
        dao.observeUserById(creatorId).map { it?.toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllCreators(): Flow<List<UserProfile>> {
        return prefs.currentUserId.flatMapLatest { uid ->
            val blockedFlow = if (uid.isNullOrBlank()) flowOf(emptyList()) else dao.observeBlockedUserIds(uid)
            combine(dao.observeAllUsers(), blockedFlow) { list, blockedIds ->
                val blockedSet = blockedIds.toHashSet()
                list.filter { !it.isSuspended && !blockedSet.contains(it.id) }
                    .map { it.toDomain() }
            }
        }
    }

    override fun observeSubscribedCreators(subscriberId: String): Flow<List<UserProfile>> {
        if (subscriberId.isBlank()) return flowOf(emptyList())
        return combine(
            dao.observeSubscribedCreators(subscriberId),
            dao.observeBlockedUserIds(subscriberId)
        ) { list, blockedIds ->
            val blockedSet = blockedIds.toHashSet()
            list.filter { !blockedSet.contains(it.id) }.map { it.toDomain() }
        }
    }

    override fun observeIsSubscribed(creatorId: String, subscriberId: String): Flow<Boolean> =
        dao.observeSubscription(creatorId, subscriberId).map { it != null }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeComments(videoId: String): Flow<List<VideoComment>> {
        return prefs.currentUserId.flatMapLatest { uid ->
            val blockedFlow = if (uid.isNullOrBlank()) flowOf(emptyList()) else dao.observeBlockedUserIds(uid)
            combine(dao.observeCommentsForVideo(videoId), blockedFlow) { list, blockedIds ->
                val blockedSet = blockedIds.toHashSet()
                list.filterNot { blockedSet.contains(it.userId) }
                    .map {
                        VideoComment(
                            id = it.id,
                            videoId = it.videoId,
                            userId = it.userId,
                            username = it.username,
                            avatarUri = it.avatarUri,
                            text = it.text,
                            createdAt = it.createdAt,
                            parentCommentId = it.parentCommentId
                        )
                    }
            }
        }
    }

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return dao.observeNotifications(userId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeNotificationsForUser(requesterId: String, targetUserId: String): Flow<List<AppNotification>> {
        if (requesterId.isBlank() || requesterId != targetUserId) {
            return flowOf(emptyList())
        }
        return observeNotifications(targetUserId)
    }

    override fun observeUnreadNotificationCount(userId: String): Flow<Int> =
        if (userId.isBlank()) {
            flowOf(0)
        } else {
            dao.observeUnreadNotificationCount(userId).distinctUntilChanged()
        }

    override suspend fun markNotificationRead(notificationId: String) {
        if (notificationId.isBlank()) return
        dao.markNotificationRead(notificationId)
    }

    override suspend fun markNotificationRead(requesterId: String, notificationId: String): Result<Unit> {
        if (requesterId.isBlank()) {
            return Result.failure(SecurityException("Authentication required."))
        }
        val existing = dao.getNotificationById(notificationId)
            ?: return Result.failure(IllegalArgumentException("Notification not found."))
        if (existing.recipientUserId != requesterId) {
            return Result.failure(SecurityException("You can only manage your own notifications."))
        }
        dao.markNotificationRead(notificationId)
        return Result.success(Unit)
    }

    override suspend fun markAllNotificationsRead(userId: String) {
        if (userId.isBlank()) return
        dao.markAllNotificationsRead(userId)
    }

    override suspend fun deleteNotification(requesterId: String, notificationId: String): Result<Unit> {
        if (requesterId.isBlank()) {
            return Result.failure(SecurityException("Authentication required."))
        }
        val existing = dao.getNotificationById(notificationId)
            ?: return Result.failure(IllegalArgumentException("Notification not found."))
        if (existing.recipientUserId != requesterId) {
            return Result.failure(SecurityException("You can only delete your own notifications."))
        }
        dao.deleteNotificationById(notificationId)
        return Result.success(Unit)
    }

    override suspend fun clearAllNotifications(requesterId: String): Result<Unit> {
        if (requesterId.isBlank()) {
            return Result.failure(SecurityException("Authentication required."))
        }
        dao.getUserById(requesterId)
            ?: return Result.failure(SecurityException("User account not found."))
        dao.clearNotificationsForUser(requesterId)
        return Result.success(Unit)
    }

    override fun observeNotificationPreferences(userId: String): Flow<NotificationPreferences> {
        if (userId.isBlank()) return flowOf(NotificationPreferences(userId = ""))
        return dao.observeNotificationPreferences(userId).map { entity ->
            entity?.toDomain() ?: NotificationPreferences(userId = userId)
        }.distinctUntilChanged()
    }

    override suspend fun updateNotificationPreferences(
        requesterId: String,
        preferences: NotificationPreferences
    ): Result<Unit> {
        if (requesterId.isBlank() || requesterId != preferences.userId) {
            return Result.failure(SecurityException("You can only update your own notification settings."))
        }
        dao.getUserById(requesterId)
            ?: return Result.failure(IllegalStateException("User not found."))
        dao.upsertNotificationPreferences(
            NotificationPreferencesEntity(
                userId = requesterId,
                likesEnabled = preferences.likesEnabled,
                commentsEnabled = preferences.commentsEnabled,
                repliesEnabled = preferences.repliesEnabled,
                subscribersEnabled = preferences.subscribersEnabled,
                videoUploadsEnabled = preferences.videoUploadsEnabled,
                updatedAt = timeProvider()
            )
        )
        return Result.success(Unit)
    }

    private suspend fun dispatchNotificationIfAllowed(notification: NotificationEntity) {
        if (notification.recipientUserId.isBlank() ||
            notification.recipientUserId == notification.actorId
        ) {
            return
        }
        val recipient = dao.getUserById(notification.recipientUserId) ?: return
        if (recipient.isSuspended) return
        if (dao.isUserBlockedOnce(notification.recipientUserId, notification.actorId)) return

        val prefs = dao.getNotificationPreferencesOnce(notification.recipientUserId)
            ?: NotificationPreferencesEntity(userId = notification.recipientUserId)
        val type = runCatching { NotificationType.valueOf(notification.type) }.getOrNull() ?: return
        val enabled = when (type) {
            NotificationType.LIKE -> prefs.likesEnabled
            NotificationType.COMMENT -> prefs.commentsEnabled
            NotificationType.REPLY -> prefs.repliesEnabled
            NotificationType.SUBSCRIPTION -> prefs.subscribersEnabled
            NotificationType.VIDEO_UPLOAD -> prefs.videoUploadsEnabled
        }
        if (!enabled) return
        dao.insertNotificationIgnore(notification)
    }

    override fun observeBlockedUsers(blockerId: String): Flow<List<UserProfile>> =
        combine(dao.observeBlockedUserIds(blockerId), dao.observeAllUsers()) { blockedIds, allUsers ->
            val blockedSet = blockedIds.toHashSet()
            allUsers.filter { blockedSet.contains(it.id) }.map { it.toDomain() }
        }

    override fun observeIsUserBlocked(blockerId: String, targetUserId: String): Flow<Boolean> =
        dao.observeBlockedUserIds(blockerId).map { it.contains(targetUserId) }

    override suspend fun blockUser(blockerId: String, targetUserId: String): Result<Unit> {
        if (blockerId == targetUserId) {
            return Result.failure(IllegalArgumentException("You cannot block your own account."))
        }
        dao.insertBlockedUser(
            BlockedUserEntity(
                blockerId = blockerId,
                blockedUserId = targetUserId,
                createdAt = timeProvider()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun unblockUser(blockerId: String, targetUserId: String): Result<Unit> {
        dao.deleteBlockedUser(blockerId, targetUserId)
        return Result.success(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCreatorDashboard(requesterId: String, creatorId: String): Flow<CreatorDashboardStats?> {
        if (requesterId.isBlank() || requesterId != creatorId) {
            return flowOf(null)
        }
        return dao.observeUserById(creatorId).flatMapLatest { userEntity ->
            if (userEntity == null || userEntity.isSuspended) {
                flowOf(null)
            } else {
                val likesFlow = dao.observeLikedVideoIds(creatorId)
                combine(dao.observeAllVideosByCreator(creatorId), likesFlow) { entities, likedIds ->
                    val likedSet = likedIds.toHashSet()
                    val videos = entities.map { it.toDomain(likedSet.contains(it.id)) }
                    val dayStart = startOfDayMillis(timeProvider())
                    CreatorDashboardStats(
                        creator = userEntity.toDomain(),
                        totalUploads = videos.size,
                        longVideoCount = videos.count { it.format == VideoFormat.LONG },
                        shortVideoCount = videos.count { it.format == VideoFormat.SHORT },
                        totalGenuineViews = videos.sumOf { it.viewCount },
                        totalLikes = videos.sumOf { it.likeCount },
                        totalComments = videos.sumOf { it.commentCount },
                        subscriberCount = userEntity.subscriberCount,
                        dailyUploadCount = videos.count { it.createdAt >= dayStart },
                        recentUploads = videos.take(5),
                        allVideos = videos
                    )
                }
            }
        }
    }

    override suspend fun updateOwnVideo(
        creatorId: String,
        videoId: String,
        title: String,
        description: String,
        category: VideoCategory,
        thumbnailUri: String?,
        tags: List<String>?
    ): Result<Unit> {
        val video = dao.getVideoById(videoId)
            ?: return Result.failure(IllegalArgumentException("Video not found."))
        if (video.creatorId != creatorId) {
            return Result.failure(SecurityException("Only the creator can edit this video."))
        }
        val cleanTitle = title.trim()
        if (cleanTitle.length < 3) {
            return Result.failure(IllegalArgumentException("Video title must be at least 3 characters."))
        }
        val finalCat = if (category == VideoCategory.ALL) VideoCategory.ENTERTAINMENT else category
        val finalThumb = if (thumbnailUri.isNullOrBlank() || thumbnailUri == video.thumbnailUri) {
            video.thumbnailUri
        } else {
            mediaStorage.storeImageMedia(thumbnailUri).getOrElse { return Result.failure(it) }
        }
        val finalTagsRaw = if (tags != null) {
            resolveVideoTags(tags, finalCat).joinToString(",")
        } else {
            video.tagsRaw
        }
        dao.updateVideoMetadata(
            videoId = videoId,
            title = cleanTitle.take(120),
            description = description.trim().take(1000),
            category = finalCat.name,
            thumbnailUri = finalThumb,
            tagsRaw = finalTagsRaw
        )
        if (finalThumb != video.thumbnailUri) {
            mediaStorage.deleteStoredFileIfOwned(video.thumbnailUri)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteOwnVideo(creatorId: String, videoId: String): Result<Unit> {
        val video = dao.getVideoById(videoId)
            ?: return Result.failure(IllegalArgumentException("Video not found."))
        if (video.creatorId != creatorId) {
            return Result.failure(SecurityException("Only the creator can delete this video."))
        }
        dao.deleteVideoWithRefsTransaction(videoId)
        mediaStorage.deleteStoredFileIfOwned(video.videoUri)
        mediaStorage.deleteStoredFileIfOwned(video.thumbnailUri)
        return Result.success(Unit)
    }

    override fun observePlaylistsForChannel(viewerId: String, creatorId: String): Flow<List<CreatorPlaylist>> {
        val isOwnChannel = viewerId.isNotBlank() && viewerId == creatorId
        val playlistFlow = if (isOwnChannel) {
            dao.observePlaylistsByCreator(creatorId)
        } else {
            dao.observePublicPlaylistsByCreator(creatorId)
        }
        val likesFlow = if (viewerId.isBlank()) flowOf(emptyList()) else dao.observeLikedVideoIds(viewerId)
        val videosFlow = if (isOwnChannel) {
            dao.observeAllVideosByCreator(creatorId)
        } else {
            dao.observeVideosByCreator(creatorId)
        }
        return combine(
            dao.observeUserById(creatorId),
            playlistFlow,
            dao.observeAllPlaylistVideoRefs(),
            videosFlow,
            likesFlow
        ) { creatorEntity, playlists, refs, creatorVideos, likedIds ->
            if (creatorEntity == null || (creatorEntity.isPrivateProfile && !isOwnChannel)) {
                emptyList()
            } else {
                val likedSet = likedIds.toHashSet()
                val videoMap = creatorVideos.associate { it.id to it.toDomain(likedSet.contains(it.id)) }
                val refsByPlaylist = refs.groupBy { it.playlistId }
                playlists.map { pl ->
                    val plVideos = refsByPlaylist[pl.id]
                        .orEmpty()
                        .sortedWith(compareBy<PlaylistVideoEntity> { it.position }.thenBy { it.addedAt })
                        .mapNotNull { ref -> videoMap[ref.videoId] }
                    val effectiveThumb = pl.thumbnailUri.ifBlank {
                        plVideos.firstOrNull()?.thumbnailUri ?: "drawable://img_banner_studio"
                    }
                    CreatorPlaylist(
                        id = pl.id,
                        creatorId = pl.creatorId,
                        title = pl.title,
                        description = pl.description,
                        thumbnailUri = effectiveThumb,
                        isPublic = pl.isPublic,
                        videoCount = plVideos.size,
                        videos = plVideos,
                        createdAt = pl.createdAt,
                        updatedAt = pl.updatedAt
                    )
                }
            }
        }
    }

    override suspend fun createPlaylist(
        requesterId: String,
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean
    ): Result<String> {
        val user = dao.getUserById(requesterId)
            ?: return Result.failure(SecurityException("Sign in required to create a playlist."))
        if (user.isSuspended) {
            return Result.failure(IllegalStateException("Suspended accounts cannot create playlists."))
        }
        val cleanTitle = title.trim()
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Playlist title must be at least 2 characters."))
        }
        val resolvedThumb = if (thumbnailUri.isBlank()) {
            "drawable://img_banner_studio"
        } else {
            mediaStorage.storeImageMedia(thumbnailUri).getOrElse { return Result.failure(it) }
        }
        val now = timeProvider()
        val id = "pl_${UUID.randomUUID().toString().take(8)}"
        dao.upsertPlaylist(
            PlaylistEntity(
                id = id,
                creatorId = requesterId,
                title = cleanTitle.take(80),
                description = description.trim().take(300),
                thumbnailUri = resolvedThumb,
                isPublic = isPublic,
                createdAt = now,
                updatedAt = now
            )
        )
        return Result.success(id)
    }

    override suspend fun updatePlaylist(
        requesterId: String,
        playlistId: String,
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean
    ): Result<Unit> {
        val existing = dao.getPlaylistById(playlistId)
            ?: return Result.failure(IllegalArgumentException("Playlist not found."))
        if (existing.creatorId != requesterId) {
            return Result.failure(SecurityException("Only the playlist owner can edit this playlist."))
        }
        val cleanTitle = title.trim()
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Playlist title must be at least 2 characters."))
        }
        val finalThumb = if (thumbnailUri.isBlank() || thumbnailUri == existing.thumbnailUri) {
            existing.thumbnailUri
        } else {
            mediaStorage.storeImageMedia(thumbnailUri).getOrElse { return Result.failure(it) }
        }
        dao.upsertPlaylist(
            existing.copy(
                title = cleanTitle.take(80),
                description = description.trim().take(300),
                thumbnailUri = finalThumb,
                isPublic = isPublic,
                updatedAt = timeProvider()
            )
        )
        if (existing.thumbnailUri != finalThumb) {
            mediaStorage.deleteStoredFileIfOwned(existing.thumbnailUri)
        }
        return Result.success(Unit)
    }

    override suspend fun deletePlaylist(requesterId: String, playlistId: String): Result<Unit> {
        val existing = dao.getPlaylistById(playlistId)
            ?: return Result.failure(IllegalArgumentException("Playlist not found."))
        if (existing.creatorId != requesterId) {
            return Result.failure(SecurityException("Only the playlist owner can delete this playlist."))
        }
        dao.deletePlaylistWithVideosTransaction(playlistId)
        mediaStorage.deleteStoredFileIfOwned(existing.thumbnailUri)
        return Result.success(Unit)
    }

    override suspend fun addVideoToPlaylist(requesterId: String, playlistId: String, videoId: String): Result<Unit> {
        val playlist = dao.getPlaylistById(playlistId)
            ?: return Result.failure(IllegalArgumentException("Playlist not found."))
        if (playlist.creatorId != requesterId) {
            return Result.failure(SecurityException("Only the playlist owner can add videos."))
        }
        val video = dao.getVideoById(videoId)
            ?: return Result.failure(IllegalArgumentException("Video not found."))
        if (video.isRemoved) {
            return Result.failure(IllegalStateException("Removed videos cannot be added to playlists."))
        }
        val nextPos = dao.getPlaylistVideoCount(playlistId)
        val now = timeProvider()
        dao.upsertPlaylistVideo(
            PlaylistVideoEntity(
                playlistId = playlistId,
                videoId = videoId,
                position = nextPos,
                addedAt = now
            )
        )
        val nextThumb = if (playlist.thumbnailUri == "drawable://img_banner_studio" && video.thumbnailUri.isNotBlank()) {
            video.thumbnailUri
        } else {
            playlist.thumbnailUri
        }
        dao.upsertPlaylist(playlist.copy(thumbnailUri = nextThumb, updatedAt = now))
        return Result.success(Unit)
    }

    override suspend fun removeVideoFromPlaylist(requesterId: String, playlistId: String, videoId: String): Result<Unit> {
        val playlist = dao.getPlaylistById(playlistId)
            ?: return Result.failure(IllegalArgumentException("Playlist not found."))
        if (playlist.creatorId != requesterId) {
            return Result.failure(SecurityException("Only the playlist owner can remove videos."))
        }
        dao.deletePlaylistVideo(playlistId, videoId)
        dao.upsertPlaylist(playlist.copy(updatedAt = timeProvider()))
        return Result.success(Unit)
    }

    override suspend fun getDailyUploadCount(creatorId: String): Int {
        return dao.countCreatorUploadsSince(creatorId, startOfDayMillis(timeProvider()))
    }

    override suspend fun uploadVideo(
        creatorId: String,
        draft: UploadDraft,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        onProgress(0.03f, "Validating upload permissions & limits...")
        val creator = dao.getUserById(creatorId)
        if (creator == null) {
            val msg = "Authentication required before uploading."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(IllegalStateException(msg))
        }
        if (creator.isSuspended) {
            val msg = "Suspended accounts cannot upload videos."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(IllegalStateException(msg))
        }
        if (!creator.isApprovedUploader && creator.role != UserRole.ADMIN.name) {
            val msg = "Account is not approved for test video uploads yet."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(IllegalStateException(msg))
        }

        val cleanTitle = draft.title.trim()
        if (cleanTitle.length < 3) {
            val msg = "Video title must be at least 3 characters."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (draft.videoUri.isBlank()) {
            val msg = "Please select a video file from your gallery."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (draft.durationSeconds <= 0L) {
            val msg = "Video duration must be greater than 0 seconds."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(IllegalArgumentException(msg))
        }

        // Format-specific duration & size enforcement
        val maxFileBytes = when (draft.format) {
            VideoFormat.SHORT -> {
                if (draft.durationSeconds > UploadLimits.MAX_SHORT_DURATION_SECONDS) {
                    val msg = "Shorts maximum duration is 3 minutes (180 seconds)."
                    logError(ErrorCategory.UPLOAD, msg, creatorId)
                    return Result.failure(IllegalArgumentException(msg))
                }
                if (draft.fileSizeBytes > UploadLimits.MAX_SHORT_FILE_BYTES) {
                    val msg = "Shorts file size exceeds 50 MB limit."
                    logError(ErrorCategory.UPLOAD, msg, creatorId)
                    return Result.failure(IllegalArgumentException(msg))
                }
                UploadLimits.MAX_SHORT_FILE_BYTES
            }
            VideoFormat.LONG -> {
                if (draft.durationSeconds > UploadLimits.MAX_LONG_DURATION_SECONDS) {
                    val msg = "Long videos maximum duration is 30 minutes (1800 seconds)."
                    logError(ErrorCategory.UPLOAD, msg, creatorId)
                    return Result.failure(IllegalArgumentException(msg))
                }
                if (draft.fileSizeBytes > UploadLimits.MAX_LONG_FILE_BYTES) {
                    val msg = "Long video file size exceeds 250 MB limit."
                    logError(ErrorCategory.UPLOAD, msg, creatorId)
                    return Result.failure(IllegalArgumentException(msg))
                }
                UploadLimits.MAX_LONG_FILE_BYTES
            }
        }

        val dailyCount = getDailyUploadCount(creatorId)
        if (dailyCount >= UploadLimits.MAX_DAILY_UPLOADS) {
            val msg = "Daily upload limit reached (maximum 5 videos per day combining Shorts and Long videos)."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(IllegalStateException(msg))
        }

        val videoId = "vid_${UUID.randomUUID().toString().take(8)}"
        val finalCat = if (draft.category == VideoCategory.ALL) VideoCategory.ENTERTAINMENT else draft.category
        val isPublicAndPublished = draft.isPublic && draft.isPublished
        val resolvedTags = resolveVideoTags(draft.tags, finalCat)
        val creatorLabel = creator.displayName.ifBlank { creator.username }
        val customMetadata = mapOf(
            "videoId" to videoId,
            "creatorId" to creator.id,
            "creatorName" to creatorLabel,
            "title" to cleanTitle.take(120),
            "description" to draft.description.trim().take(200),
            "format" to draft.format.name,
            "category" to finalCat.name,
            "durationSeconds" to draft.durationSeconds.toString(),
            "tags" to resolvedTags.joinToString(","),
            "visibility" to if (isPublicAndPublished) "public" else "private"
        )

        val storedResult = mediaStorage.storeUploadMedia(
            sourceVideoUri = draft.videoUri,
            sourceThumbnailUri = draft.thumbnailUri,
            maxVideoBytes = maxFileBytes,
            customMetadata = customMetadata,
            onProgress = onProgress
        )
        if (storedResult.isFailure) {
            val err = storedResult.exceptionOrNull()!!
            val msg = err.message ?: "Failed to store video file."
            logError(ErrorCategory.UPLOAD, msg, creatorId)
            return Result.failure(err)
        }
        val stored = storedResult.getOrThrow()

        val defaultThumb = if (draft.format == VideoFormat.SHORT) {
            "drawable://img_short_travel"
        } else {
            "drawable://img_banner_studio"
        }
        val finalSizeBytes = if (stored.fileSizeBytes > 0L) stored.fileSizeBytes else draft.fileSizeBytes.coerceAtLeast(1024L)
        val now = timeProvider()
        val entity = VideoEntity(
            id = videoId,
            creatorId = creator.id,
            creatorName = creator.displayName.ifBlank { creator.username },
            creatorAvatarUri = creator.avatarUri,
            title = cleanTitle.take(120),
            description = draft.description.trim().take(1000),
            format = draft.format.name,
            category = finalCat.name,
            durationSeconds = draft.durationSeconds,
            fileSizeBytes = finalSizeBytes,
            thumbnailUri = stored.thumbnailUri.ifBlank { defaultThumb },
            videoUri = stored.videoUri,
            viewCount = 0L,
            likeCount = 0L,
            commentCount = 0L,
            reportCount = 0L,
            isRemoved = false,
            removalReason = null,
            createdAt = now,
            isPublic = isPublicAndPublished,
            tagsRaw = resolvedTags.joinToString(",")
        )
        dao.insertVideo(entity)

        if (entity.isPublic && !entity.isRemoved && !creator.isPrivateProfile) {
            val creatorLabel = creator.displayName.ifBlank { creator.username }
            val subscriberIds = dao.getSubscriberIdsForCreator(creator.id)
            val formatTag = if (draft.format == VideoFormat.SHORT) "Short" else "video"
            subscriberIds.forEach { subscriberId ->
                dispatchNotificationIfAllowed(
                    NotificationEntity(
                        id = "notif_${UUID.randomUUID().toString().take(8)}",
                        recipientUserId = subscriberId,
                        actorId = creator.id,
                        actorName = creatorLabel,
                        actorAvatarUri = creator.avatarUri,
                        type = NotificationType.VIDEO_UPLOAD.name,
                        videoId = entity.id,
                        videoThumbnailUri = entity.thumbnailUri,
                        commentId = null,
                        dedupKey = "upload:${entity.id}:$subscriberId",
                        message = "$creatorLabel uploaded a new $formatTag: \"${entity.title}\"",
                        isRead = false,
                        createdAt = now
                    )
                )
            }
        }

        onProgress(1.0f, "Published!")
        return Result.success(videoId)
    }

    override suspend fun recordGenuineView(videoId: String, viewerId: String): Boolean {
        if (viewerId.isBlank()) return false
        val video = dao.getVideoById(videoId) ?: return false
        if (video.isRemoved) return false
        return dao.recordViewIfEligibleTransaction(
            videoId = videoId,
            viewerId = viewerId,
            now = timeProvider(),
            cooldownMs = UploadLimits.GENUINE_VIEW_COOLDOWN_MS
        )
    }

    override suspend fun toggleLike(videoId: String, userId: String): Result<Boolean> {
        val user = dao.getUserById(userId)
            ?: return Result.failure(IllegalStateException("Sign in required to like videos."))
        if (user.isSuspended) {
            return Result.failure(IllegalStateException("Suspended accounts cannot interact."))
        }
        val video = dao.getVideoById(videoId)
            ?: return Result.failure(IllegalArgumentException("Video not found."))
        if (video.isRemoved) {
            return Result.failure(IllegalStateException("Video is unavailable."))
        }
        val actorLabel = user.displayName.ifBlank { user.username }
        val now = timeProvider()
        val liked = dao.toggleLikeTransaction(videoId, userId, now)
        if (liked && video.creatorId != userId) {
            dispatchNotificationIfAllowed(
                NotificationEntity(
                    id = "notif_${UUID.randomUUID().toString().take(8)}",
                    recipientUserId = video.creatorId,
                    actorId = user.id,
                    actorName = actorLabel,
                    actorAvatarUri = user.avatarUri,
                    type = NotificationType.LIKE.name,
                    videoId = video.id,
                    videoThumbnailUri = video.thumbnailUri,
                    commentId = null,
                    dedupKey = "like:${video.id}:${user.id}",
                    message = "$actorLabel liked \"${video.title}\"",
                    isRead = false,
                    createdAt = now
                )
            )
        }
        return Result.success(liked)
    }

    override suspend fun addComment(
        videoId: String,
        userId: String,
        text: String,
        parentCommentId: String?
    ): Result<Unit> {
        val user = dao.getUserById(userId)
            ?: return Result.failure(IllegalStateException("Sign in required to comment."))
        if (user.isSuspended) {
            return Result.failure(IllegalStateException("Suspended accounts cannot comment."))
        }
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Comment cannot be empty."))
        }
        if (cleanText.length > 500) {
            return Result.failure(IllegalArgumentException("Comment cannot exceed 500 characters."))
        }
        val video = dao.getVideoById(videoId)
            ?: return Result.failure(IllegalArgumentException("Video not found."))
        if (video.isRemoved) {
            return Result.failure(IllegalStateException("Cannot comment on removed video."))
        }

        val parentComment = if (!parentCommentId.isNullOrBlank()) {
            val found = dao.getCommentById(parentCommentId)
            if (found == null || found.videoId != videoId) {
                return Result.failure(IllegalArgumentException("Parent comment not found for this video."))
            }
            found
        } else {
            null
        }
        // Keep thread hierarchy flat at 1 level beneath top-level comments
        val resolvedParentId = parentComment?.let { it.parentCommentId ?: it.id }

        val authorName = user.displayName.ifBlank { user.username }
        val now = timeProvider()
        val comment = CommentEntity(
            id = "cmt_${UUID.randomUUID().toString().take(8)}",
            videoId = videoId,
            userId = user.id,
            username = authorName,
            avatarUri = user.avatarUri,
            text = cleanText,
            createdAt = now,
            parentCommentId = resolvedParentId
        )
        dao.insertComment(comment)
        dao.incrementCommentCount(videoId)

        val notifiedRecipientIds = mutableSetOf<String>()
        if (parentComment != null && parentComment.userId != user.id) {
            notifiedRecipientIds += parentComment.userId
            dispatchNotificationIfAllowed(
                NotificationEntity(
                    id = "notif_${UUID.randomUUID().toString().take(8)}",
                    recipientUserId = parentComment.userId,
                    actorId = user.id,
                    actorName = authorName,
                    actorAvatarUri = user.avatarUri,
                    type = NotificationType.REPLY.name,
                    videoId = video.id,
                    videoThumbnailUri = video.thumbnailUri,
                    commentId = comment.id,
                    dedupKey = "reply:${comment.id}",
                    message = "$authorName replied to your comment on \"${video.title}\": \"${cleanText.take(60)}\"",
                    isRead = false,
                    createdAt = now
                )
            )
        }
        if (video.creatorId != user.id && notifiedRecipientIds.add(video.creatorId)) {
            dispatchNotificationIfAllowed(
                NotificationEntity(
                    id = "notif_${UUID.randomUUID().toString().take(8)}",
                    recipientUserId = video.creatorId,
                    actorId = user.id,
                    actorName = authorName,
                    actorAvatarUri = user.avatarUri,
                    type = NotificationType.COMMENT.name,
                    videoId = video.id,
                    videoThumbnailUri = video.thumbnailUri,
                    commentId = comment.id,
                    dedupKey = "comment:${comment.id}",
                    message = "$authorName commented on \"${video.title}\": \"${cleanText.take(60)}\"",
                    isRead = false,
                    createdAt = now
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun toggleSubscription(creatorId: String, subscriberId: String): Result<Boolean> {
        if (creatorId == subscriberId) {
            return Result.failure(IllegalArgumentException("You cannot subscribe to your own channel."))
        }
        val subscriber = dao.getUserById(subscriberId)
            ?: return Result.failure(IllegalStateException("Sign in required to subscribe."))
        if (subscriber.isSuspended) {
            return Result.failure(IllegalStateException("Suspended accounts cannot subscribe."))
        }
        val creator = dao.getUserById(creatorId)
            ?: return Result.failure(IllegalArgumentException("Creator channel not found."))
        if (creator.isSuspended) {
            return Result.failure(IllegalStateException("Channel is unavailable."))
        }
        val subscriberName = subscriber.displayName.ifBlank { subscriber.username }
        val now = timeProvider()
        val subscribed = dao.toggleSubscriptionTransaction(creatorId, subscriberId, now)
        if (subscribed) {
            dispatchNotificationIfAllowed(
                NotificationEntity(
                    id = "notif_${UUID.randomUUID().toString().take(8)}",
                    recipientUserId = creatorId,
                    actorId = subscriber.id,
                    actorName = subscriberName,
                    actorAvatarUri = subscriber.avatarUri,
                    type = NotificationType.SUBSCRIPTION.name,
                    videoId = null,
                    videoThumbnailUri = null,
                    commentId = null,
                    dedupKey = "sub:$creatorId:${subscriber.id}",
                    message = "$subscriberName subscribed to your channel",
                    isRead = false,
                    createdAt = now
                )
            )
        }
        return Result.success(subscribed)
    }

    override suspend fun reportVideo(videoId: String, reporterId: String, reason: String): Result<Unit> {
        val cleanReason = reason.trim()
        if (cleanReason.length < 4) {
            return Result.failure(IllegalArgumentException("Please provide a brief reason for reporting."))
        }
        val video = dao.getVideoById(videoId)
            ?: return Result.failure(IllegalArgumentException("Video not found."))
        val report = ReportEntity(
            id = "rep_${UUID.randomUUID().toString().take(8)}",
            videoId = videoId,
            videoTitle = video.title,
            reporterId = reporterId,
            reason = cleanReason.take(300),
            status = ReportStatus.OPEN.name,
            createdAt = timeProvider()
        )
        dao.insertReport(report)
        dao.incrementReportCount(videoId)
        return Result.success(Unit)
    }

    override suspend fun logError(
        category: ErrorCategory,
        message: String,
        userId: String?,
        videoId: String?
    ) {
        dao.insertErrorLog(
            ErrorLogEntity(
                id = "err_${UUID.randomUUID().toString().take(8)}",
                category = category.name,
                message = message.take(400),
                userId = userId,
                videoId = videoId,
                createdAt = timeProvider()
            )
        )
    }

    override suspend fun recordWatchHistory(userId: String, videoId: String, positionMs: Long) {
        if (userId.isBlank() || videoId.isBlank()) return
        dao.getUserById(userId) ?: return
        val video = dao.getVideoById(videoId) ?: return
        if (video.isRemoved) return
        val existing = dao.getWatchHistoryEntry(userId, videoId)
        val normalizedPos = if (positionMs > 0L) positionMs else (existing?.lastPositionMs ?: 0L)
        dao.upsertWatchHistory(
            WatchHistoryEntity(
                userId = userId,
                videoId = videoId,
                lastPositionMs = normalizedPos.coerceAtLeast(0L),
                watchedAt = timeProvider()
            )
        )
    }

    override fun observeWatchHistory(userId: String): Flow<List<VideoItem>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return combine(
            dao.observeWatchHistoryVideos(userId),
            dao.observeLikedVideoIds(userId),
            dao.observeBlockedUserIds(userId)
        ) { videos, likedIds, blockedIds ->
            val likedSet = likedIds.toHashSet()
            val blockedSet = blockedIds.toHashSet()
            videos
                .filterNot { blockedSet.contains(it.creatorId) }
                .map { it.toDomain(likedSet.contains(it.id)) }
        }
    }

    override suspend fun getSavedWatchPositionMs(userId: String, videoId: String): Long {
        if (userId.isBlank() || videoId.isBlank()) return 0L
        return dao.getWatchHistoryEntry(userId, videoId)?.lastPositionMs ?: 0L
    }

    override suspend fun clearWatchHistory(userId: String) {
        if (userId.isBlank()) return
        dao.clearWatchHistory(userId)
    }

    private suspend fun requireAdmin(adminId: String): Result<UserEntity> {
        val user = dao.getUserById(adminId)
        if (user == null || user.role != UserRole.ADMIN.name) {
            return Result.failure(SecurityException("Unauthorized: Admin permissions required."))
        }
        return Result.success(user)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllVideosForAdmin(requesterId: String): Flow<List<VideoItem>> {
        return dao.observeUserById(requesterId).flatMapLatest { requester ->
            if (requester?.role != UserRole.ADMIN.name) {
                flowOf(emptyList())
            } else {
                dao.observeAllVideosForAdmin().map { list -> list.map { it.toDomain(false) } }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllUsersForAdmin(requesterId: String): Flow<List<UserProfile>> {
        return dao.observeUserById(requesterId).flatMapLatest { requester ->
            if (requester?.role != UserRole.ADMIN.name) {
                flowOf(emptyList())
            } else {
                dao.observeAllUsers().map { list -> list.map { it.toDomain() } }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllReportsForAdmin(requesterId: String): Flow<List<ContentReport>> {
        return dao.observeUserById(requesterId).flatMapLatest { requester ->
            if (requester?.role != UserRole.ADMIN.name) {
                flowOf(emptyList())
            } else {
                dao.observeAllReports().map { list ->
                    list.map {
                        ContentReport(
                            id = it.id,
                            videoId = it.videoId,
                            videoTitle = it.videoTitle,
                            reporterId = it.reporterId,
                            reason = it.reason,
                            status = ReportStatus.valueOf(it.status),
                            createdAt = it.createdAt
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllErrorLogsForAdmin(requesterId: String): Flow<List<AppErrorLog>> {
        return dao.observeUserById(requesterId).flatMapLatest { requester ->
            if (requester?.role != UserRole.ADMIN.name) {
                flowOf(emptyList())
            } else {
                dao.observeAllErrorLogs().map { list ->
                    list.map {
                        AppErrorLog(
                            id = it.id,
                            category = ErrorCategory.valueOf(it.category),
                            message = it.message,
                            userId = it.userId,
                            videoId = it.videoId,
                            createdAt = it.createdAt
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAppStatistics(requesterId: String): Flow<AppStatistics?> {
        return dao.observeUserById(requesterId).flatMapLatest { requester ->
            if (requester?.role != UserRole.ADMIN.name) {
                flowOf(null)
            } else {
                combine(
                    dao.observeAllUsers(),
                    dao.observeAllVideosForAdmin(),
                    dao.observeAllReports(),
                    dao.observeAllErrorLogs()
                ) { users, videos, reports, errors ->
                    AppStatistics(
                        totalUsers = users.size,
                        approvedCreators = users.count { it.isApprovedUploader },
                        totalVideos = videos.size,
                        activeVideos = videos.count { !it.isRemoved },
                        totalViews = videos.sumOf { it.viewCount },
                        openReports = reports.count { it.status == ReportStatus.OPEN.name },
                        totalErrors = errors.size
                    )
                }
            }
        }
    }

    override suspend fun adminRemoveVideo(adminId: String, videoId: String, reason: String): Result<Unit> {
        requireAdmin(adminId).onFailure { return Result.failure(it) }
        dao.markVideoRemoved(videoId, reason.ifBlank { "Removed by moderation team" })
        return Result.success(Unit)
    }

    override suspend fun adminRestoreVideo(adminId: String, videoId: String): Result<Unit> {
        requireAdmin(adminId).onFailure { return Result.failure(it) }
        dao.restoreVideo(videoId)
        return Result.success(Unit)
    }

    override suspend fun adminSetUploaderApproval(
        adminId: String,
        targetUserId: String,
        approved: Boolean
    ): Result<Unit> {
        requireAdmin(adminId).onFailure { return Result.failure(it) }
        dao.setUserUploaderApproval(targetUserId, approved)
        return Result.success(Unit)
    }

    override suspend fun adminSetUserSuspended(
        adminId: String,
        targetUserId: String,
        suspended: Boolean
    ): Result<Unit> {
        requireAdmin(adminId).onFailure { return Result.failure(it) }
        if (targetUserId == adminId) {
            return Result.failure(IllegalArgumentException("Admins cannot suspend their own account."))
        }
        dao.setUserSuspended(targetUserId, suspended)
        return Result.success(Unit)
    }

    override suspend fun adminResolveReport(adminId: String, reportId: String): Result<Unit> {
        requireAdmin(adminId).onFailure { return Result.failure(it) }
        dao.updateReportStatus(reportId, ReportStatus.RESOLVED.name)
        return Result.success(Unit)
    }

    private fun startOfDayMillis(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun UserEntity.toDomain(): UserProfile = UserProfile(
        id = id,
        username = username,
        displayName = displayName.ifBlank { username },
        bio = bio,
        channelDescription = channelDescription.ifBlank { bio },
        avatarUri = avatarUri,
        bannerUri = bannerUri.ifBlank { "drawable://img_banner_studio" },
        role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.USER),
        isApprovedUploader = isApprovedUploader,
        isSuspended = isSuspended,
        subscriberCount = subscriberCount,
        createdAt = createdAt,
        emailMasked = maskEmail(email),
        isPrivateProfile = isPrivateProfile,
        hideSubscriberCount = hideSubscriberCount
    )

    private fun VideoEntity.toDomain(isLiked: Boolean): VideoItem = VideoItem(
        id = id,
        creatorId = creatorId,
        creatorName = creatorName,
        creatorAvatarUri = creatorAvatarUri,
        title = title,
        description = description,
        format = runCatching { VideoFormat.valueOf(format) }.getOrDefault(VideoFormat.LONG),
        category = runCatching { VideoCategory.valueOf(category) }.getOrDefault(VideoCategory.ENTERTAINMENT),
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes,
        thumbnailUri = thumbnailUri,
        videoUri = videoUri,
        viewCount = viewCount,
        likeCount = likeCount,
        commentCount = commentCount,
        reportCount = reportCount,
        isRemoved = isRemoved,
        removalReason = removalReason,
        createdAt = createdAt,
        isLikedByCurrentUser = isLiked,
        tags = parseTagsInput(tagsRaw)
    )

    private fun NotificationEntity.toDomain(): AppNotification = AppNotification(
        id = id,
        recipientUserId = recipientUserId,
        actorId = actorId,
        actorName = actorName,
        actorAvatarUri = actorAvatarUri,
        type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.LIKE),
        videoId = videoId,
        videoThumbnailUri = videoThumbnailUri,
        commentId = commentId,
        message = message,
        isRead = isRead,
        createdAt = createdAt
    )

    private fun NotificationPreferencesEntity.toDomain(): NotificationPreferences = NotificationPreferences(
        userId = userId,
        likesEnabled = likesEnabled,
        commentsEnabled = commentsEnabled,
        repliesEnabled = repliesEnabled,
        subscribersEnabled = subscribersEnabled,
        videoUploadsEnabled = videoUploadsEnabled,
        updatedAt = updatedAt
    )
}
