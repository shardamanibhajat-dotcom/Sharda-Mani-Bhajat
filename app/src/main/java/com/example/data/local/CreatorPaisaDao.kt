package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CreatorPaisaDao {
    // Users
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM users ORDER BY subscriberCount DESC, createdAt DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUsersIgnore(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET isApprovedUploader = :approved WHERE id = :userId")
    suspend fun setUserUploaderApproval(userId: String, approved: Boolean)

    @Query("UPDATE users SET isSuspended = :suspended WHERE id = :userId")
    suspend fun setUserSuspended(userId: String, suspended: Boolean)

    @Query("UPDATE users SET isPrivateProfile = :isPrivate, hideSubscriberCount = :hideSubscribers WHERE id = :userId")
    suspend fun updatePrivacySettings(userId: String, isPrivate: Boolean, hideSubscribers: Boolean)

    @Query("UPDATE users SET subscriberCount = MAX(0, subscriberCount + :delta) WHERE id = :creatorId")
    suspend fun adjustSubscriberCount(creatorId: String, delta: Long)

    // Videos
    @Query("SELECT * FROM videos WHERE isRemoved = 0 AND isPublic = 1 ORDER BY createdAt DESC")
    fun observeActiveVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY createdAt DESC")
    fun observeAllVideosForAdmin(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE creatorId = :creatorId AND isRemoved = 0 AND isPublic = 1 ORDER BY createdAt DESC")
    fun observeVideosByCreator(creatorId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE creatorId = :creatorId ORDER BY createdAt DESC")
    fun observeAllVideosByCreator(creatorId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE creatorId = :creatorId")
    suspend fun getVideosByCreatorOnce(creatorId: String): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    fun observeVideoById(videoId: String): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    suspend fun getVideoById(videoId: String): VideoEntity?

    @Query("SELECT COUNT(*) FROM videos WHERE creatorId = :creatorId AND createdAt >= :sinceMillis")
    suspend fun countCreatorUploadsSince(creatorId: String, sinceMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideosIgnore(videos: List<VideoEntity>)

    @Query("UPDATE videos SET title = :title, description = :description, category = :category, thumbnailUri = :thumbnailUri, tagsRaw = :tagsRaw WHERE id = :videoId")
    suspend fun updateVideoMetadata(
        videoId: String,
        title: String,
        description: String,
        category: String,
        thumbnailUri: String,
        tagsRaw: String
    )

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteVideoById(videoId: String)

    @Transaction
    suspend fun deleteVideoWithRefsTransaction(videoId: String) {
        deletePlaylistEntriesForVideo(videoId)
        deleteVideoById(videoId)
    }

    @Query("UPDATE videos SET isRemoved = 1, removalReason = :reason WHERE id = :videoId")
    suspend fun markVideoRemoved(videoId: String, reason: String)

    @Query("UPDATE videos SET isRemoved = 0, removalReason = NULL WHERE id = :videoId")
    suspend fun restoreVideo(videoId: String)

    @Query("UPDATE videos SET viewCount = viewCount + 1 WHERE id = :videoId")
    suspend fun incrementVideoViewCount(videoId: String)

    @Query("UPDATE videos SET likeCount = MAX(0, likeCount + :delta) WHERE id = :videoId")
    suspend fun adjustVideoLikeCount(videoId: String, delta: Long)

    @Query("UPDATE videos SET commentCount = commentCount + 1 WHERE id = :videoId")
    suspend fun incrementCommentCount(videoId: String)

    @Query("UPDATE videos SET reportCount = reportCount + 1 WHERE id = :videoId")
    suspend fun incrementReportCount(videoId: String)

    @Query("UPDATE videos SET creatorName = :newName, creatorAvatarUri = :newAvatar WHERE creatorId = :creatorId")
    suspend fun syncCreatorDetailsOnVideos(creatorId: String, newName: String, newAvatar: String)

    @Query("UPDATE comments SET username = :newName, avatarUri = :newAvatar WHERE userId = :userId")
    suspend fun syncCreatorDetailsOnComments(userId: String, newName: String, newAvatar: String)

    // Likes
    @Query("SELECT videoId FROM video_likes WHERE userId = :userId")
    fun observeLikedVideoIds(userId: String): Flow<List<String>>

    @Query("SELECT * FROM video_likes WHERE videoId = :videoId AND userId = :userId LIMIT 1")
    suspend fun getLike(videoId: String, userId: String): LikeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLike(like: LikeEntity)

    @Query("DELETE FROM video_likes WHERE videoId = :videoId AND userId = :userId")
    suspend fun deleteLike(videoId: String, userId: String)

    @Transaction
    suspend fun toggleLikeTransaction(videoId: String, userId: String, now: Long): Boolean {
        val existing = getLike(videoId, userId)
        return if (existing == null) {
            insertLike(LikeEntity(videoId = videoId, userId = userId, createdAt = now))
            adjustVideoLikeCount(videoId, 1L)
            true
        } else {
            deleteLike(videoId, userId)
            adjustVideoLikeCount(videoId, -1L)
            false
        }
    }

    // Comments
    @Query("SELECT * FROM comments WHERE videoId = :videoId ORDER BY createdAt DESC")
    fun observeCommentsForVideo(videoId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
    suspend fun getCommentById(commentId: String): CommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCommentsIgnore(comments: List<CommentEntity>)

    // Subscriptions
    @Query("SELECT * FROM subscriptions WHERE creatorId = :creatorId AND subscriberId = :subscriberId LIMIT 1")
    fun observeSubscription(creatorId: String, subscriberId: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE creatorId = :creatorId AND subscriberId = :subscriberId LIMIT 1")
    suspend fun getSubscription(creatorId: String, subscriberId: String): SubscriptionEntity?

    @Query(
        "SELECT u.* FROM users u " +
            "INNER JOIN subscriptions s ON u.id = s.creatorId " +
            "WHERE s.subscriberId = :subscriberId AND u.isSuspended = 0 " +
            "ORDER BY s.createdAt DESC"
    )
    fun observeSubscribedCreators(subscriberId: String): Flow<List<UserEntity>>

    @Query("SELECT subscriberId FROM subscriptions WHERE creatorId = :creatorId")
    suspend fun getSubscriberIdsForCreator(creatorId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE creatorId = :creatorId AND subscriberId = :subscriberId")
    suspend fun deleteSubscription(creatorId: String, subscriberId: String)

    @Transaction
    suspend fun toggleSubscriptionTransaction(creatorId: String, subscriberId: String, now: Long): Boolean {
        val existing = getSubscription(creatorId, subscriberId)
        return if (existing == null) {
            insertSubscription(SubscriptionEntity(creatorId = creatorId, subscriberId = subscriberId, createdAt = now))
            adjustSubscriberCount(creatorId, 1L)
            true
        } else {
            deleteSubscription(creatorId, subscriberId)
            adjustSubscriberCount(creatorId, -1L)
            false
        }
    }

    // Anti-fake-view tracking
    @Query("SELECT * FROM video_views WHERE videoId = :videoId AND viewerId = :viewerId LIMIT 1")
    suspend fun getVideoView(videoId: String, viewerId: String): VideoViewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideoView(view: VideoViewEntity)

    @Transaction
    suspend fun recordViewIfEligibleTransaction(
        videoId: String,
        viewerId: String,
        now: Long,
        cooldownMs: Long
    ): Boolean {
        val existing = getVideoView(videoId, viewerId)
        if (existing != null && (now - existing.lastCountedAt) < cooldownMs) {
            return false
        }
        upsertVideoView(VideoViewEntity(videoId = videoId, viewerId = viewerId, lastCountedAt = now))
        incrementVideoViewCount(videoId)
        return true
    }

    // Notifications
    @Query("SELECT * FROM notifications WHERE recipientUserId = :userId ORDER BY createdAt DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :notificationId LIMIT 1")
    suspend fun getNotificationById(notificationId: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications WHERE recipientUserId = :userId AND isRead = 0")
    fun observeUnreadNotificationCount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotificationIgnore(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotificationsIgnore(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markNotificationRead(notificationId: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE recipientUserId = :userId")
    suspend fun markAllNotificationsRead(userId: String)

    @Query("DELETE FROM notifications WHERE id = :notificationId")
    suspend fun deleteNotificationById(notificationId: String)

    @Query("DELETE FROM notifications WHERE recipientUserId = :userId")
    suspend fun clearNotificationsForUser(userId: String)

    // Notification Preferences
    @Query("SELECT * FROM notification_preferences WHERE userId = :userId LIMIT 1")
    fun observeNotificationPreferences(userId: String): Flow<NotificationPreferencesEntity?>

    @Query("SELECT * FROM notification_preferences WHERE userId = :userId LIMIT 1")
    suspend fun getNotificationPreferencesOnce(userId: String): NotificationPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotificationPreferences(prefs: NotificationPreferencesEntity)

    @Query("DELETE FROM notification_preferences WHERE userId = :userId")
    suspend fun deleteNotificationPreferencesForUser(userId: String)

    // Blocked Users
    @Query("SELECT blockedUserId FROM blocked_users WHERE blockerId = :blockerId")
    fun observeBlockedUserIds(blockerId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE blockerId = :blockerId AND blockedUserId = :blockedUserId)")
    suspend fun isUserBlockedOnce(blockerId: String, blockedUserId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlockedUser(block: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE blockerId = :blockerId AND blockedUserId = :blockedUserId")
    suspend fun deleteBlockedUser(blockerId: String, blockedUserId: String)

    // Reports
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAllReports(): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Query("UPDATE reports SET status = :status WHERE id = :reportId")
    suspend fun updateReportStatus(reportId: String, status: String)

    // Error logs
    @Query("SELECT * FROM error_logs ORDER BY createdAt DESC")
    fun observeAllErrorLogs(): Flow<List<ErrorLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrorLog(log: ErrorLogEntity)

    // Watch History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchHistory(entry: WatchHistoryEntity)

    @Query(
        "SELECT v.* FROM videos v " +
            "INNER JOIN watch_history h ON v.id = h.videoId " +
            "WHERE h.userId = :userId AND v.isRemoved = 0 " +
            "ORDER BY h.watchedAt DESC"
    )
    fun observeWatchHistoryVideos(userId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM watch_history WHERE userId = :userId AND videoId = :videoId LIMIT 1")
    suspend fun getWatchHistoryEntry(userId: String, videoId: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE userId = :userId")
    suspend fun clearWatchHistory(userId: String)

    @Query("DELETE FROM watch_history WHERE userId = :userId OR videoId IN (SELECT id FROM videos WHERE creatorId = :userId)")
    suspend fun deleteWatchHistoryForUser(userId: String)

    // Playlists
    @Query("SELECT * FROM playlists WHERE creatorId = :creatorId ORDER BY updatedAt DESC")
    fun observePlaylistsByCreator(creatorId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE creatorId = :creatorId AND isPublic = 1 ORDER BY updatedAt DESC")
    fun observePublicPlaylistsByCreator(creatorId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_videos ORDER BY position ASC, addedAt ASC")
    fun observeAllPlaylistVideoRefs(): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun getPlaylistVideoCount(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistsIgnore(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistVideo(ref: PlaylistVideoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistVideosIgnore(refs: List<PlaylistVideoEntity>)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deletePlaylistVideo(playlistId: String, videoId: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun deleteAllVideosFromPlaylist(playlistId: String)

    @Query("DELETE FROM playlist_videos WHERE videoId = :videoId")
    suspend fun deletePlaylistEntriesForVideo(videoId: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId IN (SELECT id FROM playlists WHERE creatorId = :userId) OR videoId IN (SELECT id FROM videos WHERE creatorId = :userId)")
    suspend fun deletePlaylistVideosForUser(userId: String)

    @Query("DELETE FROM playlists WHERE creatorId = :userId")
    suspend fun deletePlaylistsByCreator(userId: String)

    @Transaction
    suspend fun deletePlaylistWithVideosTransaction(playlistId: String) {
        deleteAllVideosFromPlaylist(playlistId)
        deletePlaylistById(playlistId)
    }

    // Account Deletion Cleanup
    @Query("DELETE FROM video_likes WHERE userId = :userId")
    suspend fun deleteLikesByUser(userId: String)

    @Query("DELETE FROM video_likes WHERE videoId IN (SELECT id FROM videos WHERE creatorId = :userId)")
    suspend fun deleteLikesOnUsersVideos(userId: String)

    @Query("DELETE FROM comments WHERE userId = :userId OR videoId IN (SELECT id FROM videos WHERE creatorId = :userId)")
    suspend fun deleteCommentsForUserCleanup(userId: String)

    @Query("DELETE FROM subscriptions WHERE creatorId = :userId OR subscriberId = :userId")
    suspend fun deleteSubscriptionsForUser(userId: String)

    @Query("DELETE FROM video_views WHERE viewerId = :userId OR videoId IN (SELECT id FROM videos WHERE creatorId = :userId)")
    suspend fun deleteVideoViewsForUser(userId: String)

    @Query("DELETE FROM notifications WHERE recipientUserId = :userId OR actorId = :userId")
    suspend fun deleteNotificationsForUser(userId: String)

    @Query("DELETE FROM blocked_users WHERE blockerId = :userId OR blockedUserId = :userId")
    suspend fun deleteBlocksForUser(userId: String)

    @Query("DELETE FROM reports WHERE reporterId = :userId")
    suspend fun deleteReportsByUser(userId: String)

    @Query("DELETE FROM videos WHERE creatorId = :userId")
    suspend fun deleteVideosByCreator(userId: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)

    @Transaction
    suspend fun deleteUserAccountTransaction(userId: String) {
        deleteLikesByUser(userId)
        deleteLikesOnUsersVideos(userId)
        deleteCommentsForUserCleanup(userId)
        deleteSubscriptionsForUser(userId)
        deleteVideoViewsForUser(userId)
        deleteNotificationsForUser(userId)
        deleteNotificationPreferencesForUser(userId)
        deleteBlocksForUser(userId)
        deleteReportsByUser(userId)
        deleteWatchHistoryForUser(userId)
        deletePlaylistVideosForUser(userId)
        deletePlaylistsByCreator(userId)
        deleteVideosByCreator(userId)
        deleteUserById(userId)
    }
}
