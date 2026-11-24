package com.example.data.remote

import com.example.data.local.MediaStorage
import com.example.data.model.AppNotification
import com.example.data.model.CreatorPlaylist
import com.example.data.model.NotificationPreferences
import com.example.data.model.NotificationType
import com.example.data.model.VideoItem
import com.example.data.remote.FirestoreMappers.toNotification
import com.example.data.remote.FirestoreMappers.toNotificationPreferences
import com.example.data.remote.FirestoreMappers.toPlaylist
import com.example.data.remote.FirestoreMappers.toVideoItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

internal class FirebaseNotificationsAndPlaylistsDelegate(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val mediaStorage: MediaStorage,
    private val observeBlockedIds: (String) -> Flow<Set<String>>,
    private val observeLikedIds: (String) -> Flow<Set<String>>,
    private val observeCreatorPublicVideos: (String) -> Flow<List<VideoItem>>,
    private val observeCreatorAllVideos: (String) -> Flow<List<VideoItem>>
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    fun observeNotifications(userId: String): Flow<List<AppNotification>> = flow {
        val currentUid = auth.currentUser?.uid
        if (userId.isBlank() || currentUid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/notifications"
        val rawFlow = db.collection("users").document(userId).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { toNotification(it) } }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyList())
            }

        emitAll(
            combine(rawFlow, observeBlockedIds(userId), observeNotificationPreferences(userId)) { list, blocked, prefs ->
                list.filter { n ->
                    !blocked.contains(n.actorId) && when (n.type) {
                        NotificationType.LIKE -> prefs.likesEnabled
                        NotificationType.COMMENT -> prefs.commentsEnabled
                        NotificationType.REPLY -> prefs.repliesEnabled
                        NotificationType.SUBSCRIPTION -> prefs.subscribersEnabled
                        NotificationType.VIDEO_UPLOAD -> prefs.videoUploadsEnabled
                    }
                }
            }
        )
    }

    fun observeNotificationsForUser(requesterId: String, targetUserId: String): Flow<List<AppNotification>> {
        if (requesterId.isBlank() || requesterId != targetUserId || auth.currentUser?.uid != requesterId) {
            return flowOf(emptyList())
        }
        return observeNotifications(targetUserId)
    }

    fun observeUnreadNotificationCount(userId: String): Flow<Int> =
        observeNotifications(userId)
            .map { list -> list.count { !it.isRead } }
            .distinctUntilChanged()

    suspend fun markNotificationRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (notificationId.isBlank()) return
        markNotificationRead(uid, notificationId)
    }

    suspend fun markNotificationRead(requesterId: String, notificationId: String): Result<Unit> {
        val path = "users/$requesterId/notifications/$notificationId"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("You can only manage your own notifications."))
            require(notificationId.isNotBlank()) { "Notification ID required." }
            db.document(path).update(mapOf("isRead" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(userId: String) {
        val path = "users/$userId/notifications"
        try {
            val uid = auth.currentUser?.uid ?: return
            if (uid != userId) return
            val unreadSnap = db.collection("users").document(userId).collection("notifications")
                .whereEqualTo("isRead", false)
                .get()
                .await()
            if (unreadSnap.isEmpty) return
            unreadSnap.documents.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.update(doc.reference, mapOf("isRead" to true)) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun deleteNotification(requesterId: String, notificationId: String): Result<Unit> {
        val path = "users/$requesterId/notifications/$notificationId"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("You can only delete your own notifications."))
            require(notificationId.isNotBlank()) { "Notification ID required." }
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllNotifications(requesterId: String): Result<Unit> {
        val path = "users/$requesterId/notifications"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("You can only clear your own notifications."))
            val allSnap = db.collection("users").document(requesterId).collection("notifications").get().await()
            allSnap.documents.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeNotificationPreferences(userId: String): Flow<NotificationPreferences> = flow {
        val currentUid = auth.currentUser?.uid
        if (userId.isBlank() || currentUid != userId) {
            emit(NotificationPreferences(userId = userId))
            return@flow
        }
        val path = "users/$userId/notificationPreferences/settings"
        emitAll(
            db.document(path)
                .snapshots()
                .map { snap -> toNotificationPreferences(snap, userId) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    emit(NotificationPreferences(userId = userId))
                }
                .distinctUntilChanged()
        )
    }

    suspend fun updateNotificationPreferences(
        requesterId: String,
        preferences: NotificationPreferences
    ): Result<Unit> {
        val path = "users/$requesterId/notificationPreferences/settings"
        return try {
            val uid = requireUid()
            if (uid != requesterId || requesterId != preferences.userId) {
                return Result.failure(SecurityException("You can only update your own notification settings."))
            }
            val payload = mapOf(
                "userId" to uid,
                "likesEnabled" to preferences.likesEnabled,
                "commentsEnabled" to preferences.commentsEnabled,
                "repliesEnabled" to preferences.repliesEnabled,
                "subscribersEnabled" to preferences.subscribersEnabled,
                "videoUploadsEnabled" to preferences.videoUploadsEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.document(path).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun dispatchNotificationSafe(
        recipientUserId: String,
        notificationId: String,
        actorId: String,
        actorName: String,
        actorAvatarUri: String,
        type: NotificationType,
        videoId: String?,
        videoThumbnailUri: String?,
        commentId: String?,
        dedupKey: String,
        message: String,
        checkDedupExistsFirst: Boolean
    ) {
        if (recipientUserId.isBlank() || recipientUserId == actorId) return
        val docRef = db.collection("users").document(recipientUserId)
            .collection("notifications").document(notificationId)
        try {
            if (checkDedupExistsFirst) {
                val existing = docRef.get().await()
                if (existing.exists()) return
            }
            val payload = mapOf(
                "recipientUserId" to recipientUserId,
                "actorId" to actorId,
                "actorName" to actorName.ifBlank { "Creator" }.take(80),
                "actorAvatarUri" to actorAvatarUri.take(1000),
                "type" to type.name,
                "videoId" to videoId,
                "videoThumbnailUri" to videoThumbnailUri?.take(1000),
                "commentId" to commentId,
                "message" to message.ifBlank { "New activity" }.take(300),
                "isRead" to false,
                "dedupKey" to dedupKey.take(256),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    suspend fun recordWatchHistory(userId: String, videoId: String, positionMs: Long) {
        val uid = auth.currentUser?.uid ?: return
        if (uid != userId || videoId.isBlank()) return
        val path = "users/$uid/watchHistory/$videoId"
        try {
            val payload = mapOf(
                "videoId" to videoId,
                "positionMs" to positionMs.coerceAtLeast(0L),
                "watchedAt" to FieldValue.serverTimestamp()
            )
            db.document(path).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWatchHistory(userId: String): Flow<List<VideoItem>> = flow {
        val uid = auth.currentUser?.uid
        if (userId.isBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/watchHistory"
        val historyIdsFlow = db.collection("users").document(userId).collection("watchHistory")
            .orderBy("watchedAt", Query.Direction.DESCENDING)
            .limit(30)
            .snapshots()
            .map { snap -> snap.documents.map { it.id } }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyList())
            }

        val activeVideosFlow = db.collection("videos")
            .whereEqualTo("isRemoved", false)
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snap -> snap.documents }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "videos")
                }
                emit(emptyList())
            }

        emitAll(
            combine(historyIdsFlow, activeVideosFlow, observeLikedIds(userId), observeBlockedIds(userId)) { ids, docs, liked, blocked ->
                val videoMap = docs.mapNotNull { toVideoItem(it, liked) }
                    .filterNot { blocked.contains(it.creatorId) }
                    .associateBy { it.id }
                ids.mapNotNull { videoMap[it] }
            }
        )
    }

    suspend fun getSavedWatchPositionMs(userId: String, videoId: String): Long {
        val uid = auth.currentUser?.uid ?: return 0L
        if (uid != userId || videoId.isBlank()) return 0L
        val path = "users/$uid/watchHistory/$videoId"
        return try {
            val snap = db.document(path).get().await()
            snap.getLong("positionMs") ?: 0L
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            0L
        }
    }

    suspend fun clearWatchHistory(userId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (uid != userId) return
        val path = "users/$uid/watchHistory"
        try {
            val snap = db.collection("users").document(uid).collection("watchHistory").get().await()
            snap.documents.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    fun observePlaylistsForChannel(viewerId: String, creatorId: String): Flow<List<CreatorPlaylist>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || creatorId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val isOwn = uid == creatorId
        val query = if (isOwn) {
            db.collection("playlists").whereEqualTo("creatorId", creatorId)
        } else {
            db.collection("playlists").whereEqualTo("creatorId", creatorId).whereEqualTo("isPublic", true)
        }
        val playlistsFlow = query.snapshots()
            .map { snap -> snap.documents }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "playlists")
                }
                emit(emptyList())
            }

        val videosFlow = if (isOwn) observeCreatorAllVideos(creatorId) else observeCreatorPublicVideos(creatorId)
        val creatorFlow = db.collection("users").document(creatorId).snapshots()
            .map { FirestoreMappers.toUserProfile(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(null)
            }

        emitAll(
            combine(creatorFlow, playlistsFlow, videosFlow) { creator, docs, videos ->
                if (creator == null || (creator.isPrivateProfile && !isOwn)) {
                    emptyList()
                } else {
                    val videoMap = videos.associateBy { it.id }
                    docs.mapNotNull { toPlaylist(it, videoMap) }
                        .sortedByDescending { it.updatedAt }
                }
            }
        )
    }

    suspend fun createPlaylist(
        requesterId: String,
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean
    ): Result<String> {
        val pid = "pl_${UUID.randomUUID().toString().take(8)}"
        val path = "playlists/$pid"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("Sign in mismatch."))
            val cleanTitle = title.trim()
            if (cleanTitle.length < 2) return Result.failure(IllegalArgumentException("Playlist title must be at least 2 characters."))
            val resolvedThumb = if (thumbnailUri.isBlank()) {
                "drawable://img_banner_studio"
            } else {
                mediaStorage.storeImageMedia(thumbnailUri).getOrElse { return Result.failure(it) }
            }
            val payload = mapOf(
                "creatorId" to uid,
                "title" to cleanTitle.take(80),
                "description" to description.trim().take(500),
                "thumbnailUri" to resolvedThumb.take(1000),
                "isPublic" to isPublic,
                "videoIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.document(path).set(payload).await()
            Result.success(pid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePlaylist(
        requesterId: String,
        playlistId: String,
        title: String,
        description: String,
        thumbnailUri: String,
        isPublic: Boolean
    ): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("Only playlist owner can edit."))
            val cleanTitle = title.trim()
            if (cleanTitle.length < 2) return Result.failure(IllegalArgumentException("Playlist title must be at least 2 characters."))
            val existing = db.document(path).get().await()
            if (!existing.exists()) return Result.failure(IllegalArgumentException("Playlist not found."))
            if (existing.getString("creatorId") != uid) return Result.failure(SecurityException("Only playlist owner can edit."))
            val oldThumb = existing.getString("thumbnailUri").orEmpty()
            val finalThumb = if (thumbnailUri.isBlank() || thumbnailUri == oldThumb) {
                oldThumb
            } else {
                mediaStorage.storeImageMedia(thumbnailUri).getOrElse { return Result.failure(it) }
            }
            db.document(path).update(
                mapOf(
                    "title" to cleanTitle.take(80),
                    "description" to description.trim().take(500),
                    "thumbnailUri" to finalThumb.take(1000),
                    "isPublic" to isPublic,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (oldThumb != finalThumb) mediaStorage.deleteStoredFileIfOwned(oldThumb)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(requesterId: String, playlistId: String): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("Only playlist owner can delete."))
            val existing = db.document(path).get().await()
            if (!existing.exists()) return Result.failure(IllegalArgumentException("Playlist not found."))
            if (existing.getString("creatorId") != uid) return Result.failure(SecurityException("Only playlist owner can delete."))
            db.document(path).delete().await()
            mediaStorage.deleteStoredFileIfOwned(existing.getString("thumbnailUri"))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addVideoToPlaylist(requesterId: String, playlistId: String, videoId: String): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("Only playlist owner can add videos."))
            val plRef = db.document(path)
            val videoSnap = db.collection("videos").document(videoId).get().await()
            if (!videoSnap.exists() || videoSnap.getBoolean("isRemoved") == true) {
                return Result.failure(IllegalStateException("Video is unavailable for playlists."))
            }
            val videoThumb = videoSnap.getString("thumbnailUri").orEmpty()
            db.runTransaction { tx ->
                val plSnap = tx.get(plRef)
                if (!plSnap.exists()) throw IllegalArgumentException("Playlist not found.")
                if (plSnap.getString("creatorId") != uid) throw SecurityException("Only playlist owner can edit.")
                val existingIds = (plSnap.get("videoIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val nextIds = if (existingIds.contains(videoId)) existingIds else (existingIds + videoId).take(100)
                val currentThumb = plSnap.getString("thumbnailUri").orEmpty()
                val nextThumb = if ((currentThumb.isBlank() || currentThumb == "drawable://img_banner_studio") && videoThumb.isNotBlank()) {
                    videoThumb
                } else currentThumb
                tx.update(
                    plRef,
                    mapOf(
                        "videoIds" to nextIds,
                        "thumbnailUri" to nextThumb.take(1000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeVideoFromPlaylist(requesterId: String, playlistId: String, videoId: String): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            val uid = requireUid()
            if (uid != requesterId) return Result.failure(SecurityException("Only playlist owner can remove videos."))
            val plRef = db.document(path)
            db.runTransaction { tx ->
                val plSnap = tx.get(plRef)
                if (!plSnap.exists()) throw IllegalArgumentException("Playlist not found.")
                if (plSnap.getString("creatorId") != uid) throw SecurityException("Only playlist owner can edit.")
                val existingIds = (plSnap.get("videoIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val nextIds = existingIds.filterNot { it == videoId }
                tx.update(
                    plRef,
                    mapOf(
                        "videoIds" to nextIds,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
