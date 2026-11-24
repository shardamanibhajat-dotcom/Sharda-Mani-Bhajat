package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.MediaStorage
import com.example.data.local.PassthroughMediaStorage
import com.example.data.local.SeedData
import com.example.data.local.SessionPreferences
import com.example.data.model.AppErrorLog
import com.example.data.model.AppNotification
import com.example.data.model.AppStatistics
import com.example.data.model.ContentReport
import com.example.data.model.CreatorDashboardStats
import com.example.data.model.CreatorPlaylist
import com.example.data.model.ErrorCategory
import com.example.data.model.NotificationPreferences
import com.example.data.model.NotificationType
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
import com.example.data.remote.FirestoreMappers.normalizeHandle
import com.example.data.remote.FirestoreMappers.toComment
import com.example.data.remote.FirestoreMappers.toUserProfile
import com.example.data.remote.FirestoreMappers.toVideoItem
import com.example.data.repository.CreatorPaisaRepository
import com.example.localization.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.UUID

class FirebaseCreatorPaisaRepository(
    private val db: FirebaseFirestore,
    private val prefs: SessionPreferences,
    private val mediaStorage: MediaStorage = PassthroughMediaStorage,
    private val auth: FirebaseAuth = Firebase.auth,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : CreatorPaisaRepository {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        prefs = SessionPreferences(context.applicationContext),
        mediaStorage = FirebaseMediaStorage(context.applicationContext)
    )

    private val profileBootstrapMutex = Mutex()
    private val ensuredUids = mutableSetOf<String>()

    private val moderationDelegate = FirebaseModerationDelegate(db, auth)
    private val notifPlaylistDelegate = FirebaseNotificationsAndPlaylistsDelegate(
        db = db,
        auth = auth,
        mediaStorage = mediaStorage,
        observeBlockedIds = ::observeBlockedIds,
        observeLikedIds = ::observeLikedIds,
        observeCreatorPublicVideos = ::observeVideosByCreator,
        observeCreatorAllVideos = ::observeAllVideosForCreatorStudio
    )

    override val appLanguage: Flow<AppLanguage> = prefs.appLanguage
    override val isDarkTheme: Flow<Boolean> = prefs.isDarkTheme

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentUser: Flow<UserProfile?> = auth.authStateFlow().flatMapLatest { fbUser ->
        if (fbUser == null) {
            flowOf(null)
        } else {
            flow {
                ensureSignedInProfile(fbUser)
                val userDocFlow = db.collection("users").document(fbUser.uid)
                    .snapshots()
                    .map { toUserProfile(it, maskEmail(fbUser.email.orEmpty())) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        if (auth.currentUser?.uid == fbUser.uid) {
                            handleFirestoreError(firestoreError ?: error, OperationType.GET, "users/${fbUser.uid}")
                        }
                        emit(null)
                    }
                emitAll(userDocFlow)
            }
        }
    }

    override suspend fun ensureSeeded() {
        val fbUser = auth.currentUser ?: return
        ensureSignedInProfile(fbUser)
    }

    suspend fun ensureSignedInProfile(fbUser: FirebaseUser) {
        val uid = fbUser.uid
        profileBootstrapMutex.withLock {
            if (ensuredUids.contains(uid)) return
            val userRef = db.collection("users").document(uid)
            try {
                val existing = userRef.get().await()
                val isAdmin = fbUser.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) && fbUser.isEmailVerified
                if (!existing.exists()) {
                    val rawHandle = fbUser.email?.substringBefore("@")
                        ?: fbUser.displayName
                        ?: "creator"
                    var handle = normalizeHandle(rawHandle, uid.take(4).lowercase())
                    val reservedSnap = db.collection("usernames").document(handle).get().await()
                    if (reservedSnap.exists() && reservedSnap.getString("uid") != uid) {
                        handle = normalizeHandle("${handle}_${uid.take(5).lowercase()}", uid.take(6).lowercase())
                    }
                    val displayName = (fbUser.displayName?.takeIf { it.isNotBlank() } ?: handle).take(60)
                    val avatarUri = fbUser.photoUrl?.toString()?.take(1000)
                        ?: "drawable://img_creator_paisa_logo"

                    val batch = db.batch()
                    batch.set(
                        userRef,
                        mapOf(
                            "uid" to uid,
                            "username" to handle,
                            "displayName" to displayName,
                            "bio" to "Creator on Creator Paisa",
                            "channelDescription" to "Independent creator sharing videos & Shorts on Creator Paisa.",
                            "avatarUri" to avatarUri,
                            "bannerUri" to "drawable://img_banner_studio",
                            "role" to if (isAdmin) UserRole.ADMIN.name else UserRole.CREATOR.name,
                            "isApprovedUploader" to true,
                            "isSuspended" to false,
                            "subscriberCount" to 0L,
                            "isPrivateProfile" to false,
                            "hideSubscriberCount" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        userRef.collection("private").document("info"),
                        mapOf(
                            "uid" to uid,
                            "emailMasked" to maskEmail(fbUser.email.orEmpty()).ifBlank { "***" },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        db.collection("usernames").document(handle),
                        mapOf(
                            "uid" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        userRef.collection("notificationPreferences").document("settings"),
                        mapOf(
                            "userId" to uid,
                            "likesEnabled" to true,
                            "commentsEnabled" to true,
                            "repliesEnabled" to true,
                            "subscribersEnabled" to true,
                            "videoUploadsEnabled" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                }
                if (isAdmin) {
                    seedInitialCatalogIfEmpty()
                }
                ensuredUids.add(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, userRef.path)
            }
        }
    }

    private suspend fun seedInitialCatalogIfEmpty() {
        try {
            val activeSnap = db.collection("videos")
                .whereEqualTo("isRemoved", false)
                .whereEqualTo("isPublic", true)
                .limit(1)
                .get()
                .await()
            if (!activeSnap.isEmpty) return

            val batch = db.batch()
            SeedData.initialUsers().forEach { user ->
                val userRef = db.collection("users").document(user.id)
                batch.set(
                    userRef,
                    mapOf(
                        "uid" to user.id,
                        "username" to user.username,
                        "displayName" to user.displayName,
                        "bio" to user.bio,
                        "channelDescription" to user.channelDescription,
                        "avatarUri" to user.avatarUri,
                        "bannerUri" to user.bannerUri,
                        "role" to user.role,
                        "isApprovedUploader" to user.isApprovedUploader,
                        "isSuspended" to user.isSuspended,
                        "subscriberCount" to user.subscriberCount,
                        "isPrivateProfile" to user.isPrivateProfile,
                        "hideSubscriberCount" to user.hideSubscriberCount,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            SeedData.initialVideos().forEach { v ->
                val vRef = db.collection("videos").document(v.id)
                batch.set(
                    vRef,
                    mapOf(
                        "creatorId" to v.creatorId,
                        "creatorName" to v.creatorName,
                        "creatorAvatarUri" to v.creatorAvatarUri,
                        "title" to v.title,
                        "description" to v.description,
                        "format" to v.format,
                        "category" to v.category,
                        "durationSeconds" to v.durationSeconds,
                        "fileSizeBytes" to v.fileSizeBytes.coerceAtLeast(1024L),
                        "thumbnailUri" to v.thumbnailUri,
                        "videoUri" to v.videoUri,
                        "viewCount" to v.viewCount,
                        "likeCount" to v.likeCount,
                        "commentCount" to v.commentCount,
                        "reportCount" to v.reportCount,
                        "isPublic" to v.isPublic,
                        "isRemoved" to v.isRemoved,
                        "tags" to parseTagsInput(v.tagsRaw),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val refsByPlaylist = SeedData.initialPlaylistVideos().groupBy { it.playlistId }
            SeedData.initialPlaylists().forEach { pl ->
                val plRef = db.collection("playlists").document(pl.id)
                val videoIds = refsByPlaylist[pl.id].orEmpty().sortedBy { it.position }.map { it.videoId }
                batch.set(
                    plRef,
                    mapOf(
                        "creatorId" to pl.creatorId,
                        "title" to pl.title,
                        "description" to pl.description,
                        "thumbnailUri" to pl.thumbnailUri,
                        "isPublic" to pl.isPublic,
                        "videoIds" to videoIds,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "videos")
        }
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        prefs.setAppLanguage(language)
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        prefs.setDarkTheme(enabled)
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Please sign in with Google to access Creator Paisa cloud."))

    override suspend fun signUp(
        email: String,
        username: String,
        password: String,
        bio: String,
        avatarUri: String,
        displayName: String
    ): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Please sign in with Google to create your Creator Paisa channel."))

    override suspend fun logout() {
        val uid = auth.currentUser?.uid
        ensuredUids.remove(uid)
        auth.signOut()
    }

    override suspend fun updateProfile(
        userId: String,
        username: String,
        bio: String,
        avatarUri: String
    ): Result<Unit> {
        val snap = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
        val existing = snap?.let { toUserProfile(it) }
            ?: return Result.failure(IllegalStateException("User profile not found."))
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
        val path = "users/$targetUserId"
        return try {
            val uid = requireUserId()
            if (uid != requesterId || requesterId != targetUserId) {
                return Result.failure(SecurityException("Only the channel owner can edit this profile."))
            }
            val userRef = db.collection("users").document(uid)
            val existingSnap = userRef.get().await()
            val existing = toUserProfile(existingSnap)
                ?: return Result.failure(IllegalStateException("User not found."))
            if (existing.isSuspended) {
                return Result.failure(IllegalStateException("Suspended accounts cannot update profile details."))
            }

            val cleanHandle = normalizeHandle(username)
            val cleanDisplayName = displayName.trim().ifBlank { cleanHandle }.take(60)
            if (cleanDisplayName.length < 2) {
                return Result.failure(IllegalArgumentException("Creator name must be at least 2 characters."))
            }
            if (username.trim().removePrefix("@").trim().length < 3) {
                return Result.failure(IllegalArgumentException("Username must be at least 3 characters."))
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

            val newHandleRef = db.collection("usernames").document(cleanHandle)
            val oldHandle = existing.username
            db.runTransaction { tx ->
                val reservedSnap = tx.get(newHandleRef)
                if (reservedSnap.exists() && reservedSnap.getString("uid") != uid) {
                    throw IllegalArgumentException("This username is already taken. Try another one.")
                }
                tx.set(
                    newHandleRef,
                    mapOf("uid" to uid, "updatedAt" to FieldValue.serverTimestamp())
                )
                if (oldHandle.isNotBlank() && oldHandle != cleanHandle) {
                    val oldRef = db.collection("usernames").document(oldHandle)
                    val oldSnap = tx.get(oldRef)
                    if (oldSnap.exists() && oldSnap.getString("uid") == uid) {
                        tx.delete(oldRef)
                    }
                }
                tx.update(
                    userRef,
                    mapOf(
                        "username" to cleanHandle,
                        "displayName" to cleanDisplayName,
                        "bio" to bio.trim().take(300),
                        "channelDescription" to channelDescription.trim().take(1000),
                        "avatarUri" to finalAvatar.take(1000),
                        "bannerUri" to finalBanner.take(1000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            // Propagate display name & avatar to creator's existing videos
            val ownVideos = db.collection("videos").whereEqualTo("creatorId", uid).get().await()
            ownVideos.documents.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "creatorName" to cleanDisplayName,
                            "creatorAvatarUri" to finalAvatar.take(1000),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            if (existing.avatarUri != finalAvatar) mediaStorage.deleteStoredFileIfOwned(existing.avatarUri)
            if (existing.bannerUri != finalBanner) mediaStorage.deleteStoredFileIfOwned(existing.bannerUri)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updatePrivacySettings(
        userId: String,
        isPrivateProfile: Boolean,
        hideSubscriberCount: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            if (uid != userId) return Result.failure(SecurityException("Owner mismatch."))
            db.document(path).update(
                mapOf(
                    "isPrivateProfile" to isPrivateProfile,
                    "hideSubscriberCount" to hideSubscriberCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val fbUser = auth.currentUser
                ?: return Result.failure(IllegalStateException("Not signed in."))
            val uid = fbUser.uid
            if (uid != userId) return Result.failure(SecurityException("Owner mismatch."))

            val userSnap = db.document(path).get().await()
            val profile = toUserProfile(userSnap)

            // Clean up creator videos
            val ownVideos = db.collection("videos").whereEqualTo("creatorId", uid).get().await()
            ownVideos.documents.forEach { vDoc ->
                vDoc.reference.delete().await()
                mediaStorage.deleteStoredFileIfOwned(vDoc.getString("videoUri"))
                mediaStorage.deleteStoredFileIfOwned(vDoc.getString("thumbnailUri"))
            }

            // Clean up playlists
            val ownPlaylists = db.collection("playlists").whereEqualTo("creatorId", uid).get().await()
            ownPlaylists.documents.forEach { plDoc ->
                plDoc.reference.delete().await()
                mediaStorage.deleteStoredFileIfOwned(plDoc.getString("thumbnailUri"))
            }

            // Clean up user subcollections
            listOf("notifications", "blocked", "watchHistory", "likedVideos", "notificationPreferences", "private").forEach { sub ->
                val subSnap = db.document(path).collection(sub).get().await()
                subSnap.documents.forEach { it.reference.delete().await() }
            }
            val subRefs = db.document(path).collection("subscriptions").get().await()
            subRefs.documents.forEach { sDoc ->
                runCatching { db.collection("creators").document(sDoc.id).collection("subscribers").document(uid).delete().await() }
                sDoc.reference.delete().await()
            }

            profile?.username?.takeIf { it.isNotBlank() }?.let { handle ->
                runCatching { db.collection("usernames").document(handle).delete().await() }
            }
            db.document(path).delete().await()

            mediaStorage.deleteStoredFileIfOwned(profile?.avatarUri)
            mediaStorage.deleteStoredFileIfOwned(profile?.bannerUri)

            ensuredUids.remove(uid)
            runCatching { fbUser.delete().await() }
                .onFailure { auth.signOut() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun observeLikedIds(userId: String): Flow<Set<String>> = flow {
        val uid = auth.currentUser?.uid
        if (userId.isBlank() || uid != userId) {
            emit(emptySet())
            return@flow
        }
        val path = "users/$uid/likedVideos"
        emitAll(
            db.collection("users").document(uid).collection("likedVideos")
                .snapshots()
                .map { snap -> snap.documents.map { it.id }.toHashSet() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    emit(emptySet())
                }
        )
    }

    private fun observeBlockedIds(blockerId: String): Flow<Set<String>> = flow {
        val uid = auth.currentUser?.uid
        if (blockerId.isBlank() || uid != blockerId) {
            emit(emptySet())
            return@flow
        }
        val path = "users/$uid/blocked"
        emitAll(
            db.collection("users").document(uid).collection("blocked")
                .snapshots()
                .map { snap -> snap.documents.map { it.id }.toHashSet() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    emit(emptySet())
                }
        )
    }

    override fun observeActiveVideos(): Flow<List<VideoItem>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val rawVideosFlow = db.collection("videos")
            .whereEqualTo("isRemoved", false)
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snap -> snap.documents }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "videos")
                }
                emit(emptyList())
            }

        emitAll(
            combine(rawVideosFlow, observeLikedIds(uid), observeBlockedIds(uid)) { docs, liked, blocked ->
                docs.mapNotNull { toVideoItem(it, liked) }
                    .filterNot { blocked.contains(it.creatorId) }
                    .sortedByDescending { it.createdAt }
            }
        )
    }

    override fun observeVideosByTag(tag: String): Flow<List<VideoItem>> = flow {
        val cleanTag = normalizeTag(tag)
        if (cleanTag.isBlank()) {
            emitAll(observeActiveVideos())
            return@flow
        }
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val rawVideosFlow = db.collection("videos")
            .whereEqualTo("isRemoved", false)
            .whereEqualTo("isPublic", true)
            .whereArrayContains("tags", cleanTag)
            .snapshots()
            .map { snap -> snap.documents }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "videos")
                }
                emit(emptyList())
            }

        emitAll(
            combine(rawVideosFlow, observeLikedIds(uid), observeBlockedIds(uid)) { docs, liked, blocked ->
                docs.mapNotNull { toVideoItem(it, liked) }
                    .filterNot { blocked.contains(it.creatorId) }
                    .sortedByDescending { it.createdAt }
            }
        )
    }

    override suspend fun searchVideos(query: String): Result<List<VideoItem>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(SecurityException("Sign in required to search videos."))
        }
        val path = "videos"
        return try {
            val clean = query.trim().lowercase()
            val videosSnap = db.collection(path)
                .whereEqualTo("isRemoved", false)
                .whereEqualTo("isPublic", true)
                .limit(100L)
                .get()
                .await()

            val likedDocs = db.collection("users").document(uid)
                .collection("likedVideos")
                .get()
                .await()
            val likedIds = likedDocs.documents.map { it.id }.toHashSet()

            val blockedDocs = db.collection("users").document(uid)
                .collection("blocked")
                .get()
                .await()
            val blockedIds = blockedDocs.documents.map { it.id }.toHashSet()

            val videos = videosSnap.documents
                .mapNotNull { toVideoItem(it, likedIds) }
                .filterNot { blockedIds.contains(it.creatorId) }

            val filtered = if (clean.isEmpty()) {
                videos.sortedByDescending { it.createdAt }
            } else {
                videos.filter { video ->
                    video.title.lowercase().contains(clean) ||
                        video.creatorName.lowercase().contains(clean) ||
                        video.description.lowercase().contains(clean) ||
                        video.tags.any { it.contains(clean.removePrefix("#")) }
                }.sortedWith(
                    compareByDescending<VideoItem> {
                        it.title.lowercase().startsWith(clean) || it.creatorName.lowercase().startsWith(clean)
                    }.thenByDescending { it.createdAt }
                )
            }
            Result.success(filtered)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    override fun observeVideoById(videoId: String): Flow<VideoItem?> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || videoId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "videos/$videoId"
        val docFlow = db.document(path)
            .snapshots()
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
        emitAll(
            combine(docFlow, observeLikedIds(uid)) { snap, liked ->
                toVideoItem(snap, liked)
            }.catch { emit(null) }
        )
    }

    override fun observeVideosByCreator(creatorId: String): Flow<List<VideoItem>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || creatorId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val videosFlow = db.collection("videos")
            .whereEqualTo("creatorId", creatorId)
            .whereEqualTo("isRemoved", false)
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snap -> snap.documents }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "videos")
                }
                emit(emptyList())
            }
        emitAll(
            combine(videosFlow, observeLikedIds(uid)) { docs, liked ->
                docs.mapNotNull { toVideoItem(it, liked) }.sortedByDescending { it.createdAt }
            }
        )
    }

    override fun observeAllVideosForCreatorStudio(creatorId: String): Flow<List<VideoItem>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != creatorId) {
            emit(emptyList())
            return@flow
        }
        val videosFlow = db.collection("videos")
            .whereEqualTo("creatorId", creatorId)
            .snapshots()
            .map { snap -> snap.documents }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "videos")
                }
                emit(emptyList())
            }
        emitAll(
            combine(videosFlow, observeLikedIds(uid)) { docs, liked ->
                docs.mapNotNull { toVideoItem(it, liked) }.sortedByDescending { it.createdAt }
            }
        )
    }

    override fun observeCreatorProfile(creatorId: String): Flow<UserProfile?> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || creatorId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$creatorId"
        emitAll(
            db.document(path)
                .snapshots()
                .map { toUserProfile(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    emit(null)
                }
        )
    }

    override fun observeAllCreators(): Flow<List<UserProfile>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val usersFlow = db.collection("users")
            .whereEqualTo("isSuspended", false)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { toUserProfile(it) } }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users")
                }
                emit(emptyList())
            }
        emitAll(
            combine(usersFlow, observeBlockedIds(uid)) { creators, blocked ->
                creators.filterNot { blocked.contains(it.id) }
                    .sortedByDescending { it.subscriberCount }
            }
        )
    }

    override fun observeSubscribedCreators(subscriberId: String): Flow<List<UserProfile>> = flow {
        val uid = auth.currentUser?.uid
        if (subscriberId.isBlank() || uid != subscriberId) {
            emit(emptyList())
            return@flow
        }
        val subIdsFlow = db.collection("users").document(uid).collection("subscriptions")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users/$uid/subscriptions")
                }
                emit(emptySet())
            }
        emitAll(
            combine(subIdsFlow, observeAllCreators()) { subIds, creators ->
                creators.filter { subIds.contains(it.id) }
            }
        )
    }

    override fun observeIsSubscribed(creatorId: String, subscriberId: String): Flow<Boolean> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != subscriberId || creatorId.isBlank()) {
            emit(false)
            return@flow
        }
        val path = "users/$uid/subscriptions/$creatorId"
        emitAll(
            db.document(path)
                .snapshots()
                .map { it.exists() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(false)
                }
        )
    }

    override fun observeComments(videoId: String): Flow<List<VideoComment>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || videoId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "videos/$videoId/comments"
        val commentsFlow = db.collection("videos").document(videoId).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { toComment(it) } }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyList())
            }
        emitAll(
            combine(commentsFlow, observeBlockedIds(uid)) { list, blocked ->
                list.filterNot { blocked.contains(it.userId) }
            }
        )
    }

    // Notifications & Watch History & Playlists delegated cleanly
    override fun observeNotifications(userId: String) = notifPlaylistDelegate.observeNotifications(userId)
    override fun observeNotificationsForUser(requesterId: String, targetUserId: String) =
        notifPlaylistDelegate.observeNotificationsForUser(requesterId, targetUserId)
    override fun observeUnreadNotificationCount(userId: String) =
        notifPlaylistDelegate.observeUnreadNotificationCount(userId)
    override suspend fun markNotificationRead(notificationId: String) =
        notifPlaylistDelegate.markNotificationRead(notificationId)
    override suspend fun markNotificationRead(requesterId: String, notificationId: String) =
        notifPlaylistDelegate.markNotificationRead(requesterId, notificationId)
    override suspend fun markAllNotificationsRead(userId: String) =
        notifPlaylistDelegate.markAllNotificationsRead(userId)
    override suspend fun deleteNotification(requesterId: String, notificationId: String) =
        notifPlaylistDelegate.deleteNotification(requesterId, notificationId)
    override suspend fun clearAllNotifications(requesterId: String) =
        notifPlaylistDelegate.clearAllNotifications(requesterId)
    override fun observeNotificationPreferences(userId: String) =
        notifPlaylistDelegate.observeNotificationPreferences(userId)
    override suspend fun updateNotificationPreferences(requesterId: String, preferences: NotificationPreferences) =
        notifPlaylistDelegate.updateNotificationPreferences(requesterId, preferences)

    override suspend fun recordWatchHistory(userId: String, videoId: String, positionMs: Long) =
        notifPlaylistDelegate.recordWatchHistory(userId, videoId, positionMs)
    override fun observeWatchHistory(userId: String) =
        notifPlaylistDelegate.observeWatchHistory(userId)
    override suspend fun getSavedWatchPositionMs(userId: String, videoId: String) =
        notifPlaylistDelegate.getSavedWatchPositionMs(userId, videoId)
    override suspend fun clearWatchHistory(userId: String) =
        notifPlaylistDelegate.clearWatchHistory(userId)

    override fun observePlaylistsForChannel(viewerId: String, creatorId: String) =
        notifPlaylistDelegate.observePlaylistsForChannel(viewerId, creatorId)
    override suspend fun createPlaylist(requesterId: String, title: String, description: String, thumbnailUri: String, isPublic: Boolean) =
        notifPlaylistDelegate.createPlaylist(requesterId, title, description, thumbnailUri, isPublic)
    override suspend fun updatePlaylist(requesterId: String, playlistId: String, title: String, description: String, thumbnailUri: String, isPublic: Boolean) =
        notifPlaylistDelegate.updatePlaylist(requesterId, playlistId, title, description, thumbnailUri, isPublic)
    override suspend fun deletePlaylist(requesterId: String, playlistId: String) =
        notifPlaylistDelegate.deletePlaylist(requesterId, playlistId)
    override suspend fun addVideoToPlaylist(requesterId: String, playlistId: String, videoId: String) =
        notifPlaylistDelegate.addVideoToPlaylist(requesterId, playlistId, videoId)
    override suspend fun removeVideoFromPlaylist(requesterId: String, playlistId: String, videoId: String) =
        notifPlaylistDelegate.removeVideoFromPlaylist(requesterId, playlistId, videoId)

    override fun observeBlockedUsers(blockerId: String): Flow<List<UserProfile>> = flow {
        val uid = auth.currentUser?.uid
        if (blockerId.isBlank() || uid != blockerId) {
            emit(emptyList())
            return@flow
        }
        val allUsersFlow = db.collection("users").whereEqualTo("isSuspended", false)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { toUserProfile(it) } }
            .catch { emit(emptyList()) }
        emitAll(
            combine(observeBlockedIds(uid), allUsersFlow) { blockedIds, users ->
                users.filter { blockedIds.contains(it.id) }
            }
        )
    }

    override fun observeIsUserBlocked(blockerId: String, targetUserId: String): Flow<Boolean> =
        observeBlockedIds(blockerId).map { it.contains(targetUserId) }

    override suspend fun blockUser(blockerId: String, targetUserId: String): Result<Unit> {
        val path = "users/$blockerId/blocked/$targetUserId"
        return try {
            val uid = requireUserId()
            if (uid != blockerId) return Result.failure(SecurityException("Owner mismatch."))
            if (uid == targetUserId) return Result.failure(IllegalArgumentException("You cannot block your own account."))
            db.document(path).set(
                mapOf(
                    "blockedUserId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(blockerId: String, targetUserId: String): Result<Unit> {
        val path = "users/$blockerId/blocked/$targetUserId"
        return try {
            val uid = requireUserId()
            if (uid != blockerId) return Result.failure(SecurityException("Owner mismatch."))
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCreatorDashboard(requesterId: String, creatorId: String): Flow<CreatorDashboardStats?> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || requesterId != creatorId || uid != creatorId) {
            return flowOf(null)
        }
        return combine(observeCreatorProfile(creatorId), observeAllVideosForCreatorStudio(creatorId)) { creator, videos ->
            if (creator == null || creator.isSuspended) null
            else {
                val dayStart = startOfDayMillis(timeProvider())
                CreatorDashboardStats(
                    creator = creator,
                    totalUploads = videos.size,
                    longVideoCount = videos.count { it.format == VideoFormat.LONG },
                    shortVideoCount = videos.count { it.format == VideoFormat.SHORT },
                    totalGenuineViews = videos.sumOf { it.viewCount },
                    totalLikes = videos.sumOf { it.likeCount },
                    totalComments = videos.sumOf { it.commentCount },
                    subscriberCount = creator.subscriberCount,
                    dailyUploadCount = videos.count { it.createdAt >= dayStart },
                    recentUploads = videos.take(5),
                    allVideos = videos
                )
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
        val path = "videos/$videoId"
        return try {
            val uid = requireUserId()
            if (uid != creatorId) return Result.failure(SecurityException("Only the creator can edit this video."))
            val cleanTitle = title.trim()
            if (cleanTitle.length < 3) return Result.failure(IllegalArgumentException("Video title must be at least 3 characters."))
            val vSnap = db.document(path).get().await()
            if (!vSnap.exists()) return Result.failure(IllegalArgumentException("Video not found."))
            if (vSnap.getString("creatorId") != uid) return Result.failure(SecurityException("Only the creator can edit this video."))
            val oldThumb = vSnap.getString("thumbnailUri").orEmpty()
            val finalCat = if (category == VideoCategory.ALL) VideoCategory.ENTERTAINMENT else category
            val finalThumb = if (thumbnailUri.isNullOrBlank() || thumbnailUri == oldThumb) {
                oldThumb
            } else {
                mediaStorage.storeImageMedia(thumbnailUri).getOrElse { return Result.failure(it) }
            }
            val updates = mutableMapOf<String, Any>(
                "title" to cleanTitle.take(120),
                "description" to description.trim().take(1000),
                "category" to finalCat.name,
                "thumbnailUri" to finalThumb.take(1000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (tags != null) {
                updates["tags"] = resolveVideoTags(tags, finalCat)
            }
            db.document(path).update(updates).await()
            if (finalThumb != oldThumb) mediaStorage.deleteStoredFileIfOwned(oldThumb)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteOwnVideo(creatorId: String, videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            val uid = requireUserId()
            if (uid != creatorId) return Result.failure(SecurityException("Only the creator can delete this video."))
            val vSnap = db.document(path).get().await()
            if (!vSnap.exists()) return Result.failure(IllegalArgumentException("Video not found."))
            if (vSnap.getString("creatorId") != uid) return Result.failure(SecurityException("Only the creator can delete this video."))
            db.document(path).delete().await()
            mediaStorage.deleteStoredFileIfOwned(vSnap.getString("videoUri"))
            mediaStorage.deleteStoredFileIfOwned(vSnap.getString("thumbnailUri"))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun getDailyUploadCount(creatorId: String): Int {
        val uid = auth.currentUser?.uid ?: return 0
        if (uid != creatorId) return 0
        return try {
            val dayStartMs = startOfDayMillis(timeProvider())
            val snap = db.collection("videos")
                .whereEqualTo("creatorId", uid)
                .get()
                .await()
            snap.documents.count { doc ->
                val createdMs = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                createdMs >= dayStartMs
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "videos")
            0
        }
    }

    override suspend fun uploadVideo(
        creatorId: String,
        draft: UploadDraft,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        val videoId = "vid_${UUID.randomUUID().toString().take(8)}"
        val path = "videos/$videoId"
        return try {
            onProgress(0.03f, "Validating upload permissions & limits...")
            val uid = requireUserId()
            if (uid != creatorId) return Result.failure(SecurityException("Owner mismatch."))
            val userSnap = db.collection("users").document(uid).get().await()
            val creator = toUserProfile(userSnap)
                ?: return Result.failure(IllegalStateException("Creator profile not ready."))
            if (creator.isSuspended) {
                return Result.failure(IllegalStateException("Suspended accounts cannot upload videos."))
            }
            if (!creator.isApprovedUploader && creator.role != UserRole.ADMIN) {
                return Result.failure(IllegalStateException("Account is not approved for uploads yet."))
            }

            val cleanTitle = draft.title.trim()
            if (cleanTitle.length < 3) return Result.failure(IllegalArgumentException("Video title must be at least 3 characters."))
            if (draft.videoUri.isBlank()) return Result.failure(IllegalArgumentException("Please select a video file or provide a stream URL."))
            if (draft.durationSeconds <= 0L) return Result.failure(IllegalArgumentException("Duration must be greater than 0 seconds."))

            val maxBytes = when (draft.format) {
                VideoFormat.SHORT -> {
                    if (draft.durationSeconds > UploadLimits.MAX_SHORT_DURATION_SECONDS) {
                        return Result.failure(IllegalArgumentException("Shorts maximum duration is 3 minutes (180 seconds)."))
                    }
                    if (draft.fileSizeBytes > UploadLimits.MAX_SHORT_FILE_BYTES) {
                        return Result.failure(IllegalArgumentException("Shorts file size exceeds 50 MB limit."))
                    }
                    UploadLimits.MAX_SHORT_FILE_BYTES
                }
                VideoFormat.LONG -> {
                    if (draft.durationSeconds > UploadLimits.MAX_LONG_DURATION_SECONDS) {
                        return Result.failure(IllegalArgumentException("Long videos maximum duration is 30 minutes (1800 seconds)."))
                    }
                    if (draft.fileSizeBytes > UploadLimits.MAX_LONG_FILE_BYTES) {
                        return Result.failure(IllegalArgumentException("Long videos file size exceeds 250 MB limit."))
                    }
                    UploadLimits.MAX_LONG_FILE_BYTES
                }
            }

            if (getDailyUploadCount(uid) >= UploadLimits.MAX_DAILY_UPLOADS) {
                return Result.failure(IllegalStateException("Daily limit reached (${UploadLimits.MAX_DAILY_UPLOADS} uploads/day)."))
            }

            val finalCat = if (draft.category == VideoCategory.ALL) VideoCategory.ENTERTAINMENT else draft.category
            val isPublicAndPublished = draft.isPublic && draft.isPublished
            val creatorLabel = creator.displayName.ifBlank { creator.username }.take(80)
            val resolvedTags = resolveVideoTags(draft.tags, finalCat)
            val storageCustomMetadata = mapOf(
                "videoId" to videoId,
                "creatorId" to uid,
                "creatorName" to creatorLabel,
                "title" to cleanTitle.take(120),
                "description" to draft.description.trim().take(200),
                "format" to draft.format.name,
                "category" to finalCat.name,
                "durationSeconds" to draft.durationSeconds.toString(),
                "tags" to resolvedTags.joinToString(","),
                "visibility" to if (isPublicAndPublished) "public" else "private"
            )

            val stored = mediaStorage.storeUploadMedia(
                sourceVideoUri = draft.videoUri,
                sourceThumbnailUri = draft.thumbnailUri,
                maxVideoBytes = maxBytes,
                customMetadata = storageCustomMetadata,
                onProgress = onProgress
            ).getOrElse { err ->
                logError(ErrorCategory.UPLOAD, err.message ?: "Media error", uid)
                return Result.failure(err)
            }

            val defaultThumb = if (draft.format == VideoFormat.SHORT) "drawable://img_short_travel" else "drawable://img_banner_studio"
            val finalThumb = stored.thumbnailUri.ifBlank { defaultThumb }.take(1000)
            val finalSizeBytes = if (stored.fileSizeBytes > 0L) stored.fileSizeBytes else draft.fileSizeBytes.coerceAtLeast(1024L)

            onProgress(0.95f, "Creating video document in Firestore...")
            val payload = mapOf(
                "creatorId" to uid,
                "creatorName" to creatorLabel,
                "creatorAvatarUri" to creator.avatarUri.take(1000),
                "title" to cleanTitle.take(120),
                "description" to draft.description.trim().take(1000),
                "format" to draft.format.name,
                "category" to finalCat.name,
                "tags" to resolvedTags,
                "durationSeconds" to draft.durationSeconds,
                "fileSizeBytes" to finalSizeBytes,
                "thumbnailUri" to finalThumb,
                "videoUri" to stored.videoUri.take(1000),
                "viewCount" to 0L,
                "likeCount" to 0L,
                "commentCount" to 0L,
                "reportCount" to 0L,
                "isPublic" to isPublicAndPublished,
                "isRemoved" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                db.document(path).set(payload).await()
            } catch (writeErr: Throwable) {
                if (stored.videoUri != draft.videoUri) {
                    runCatching { mediaStorage.deleteStoredFileIfOwned(stored.videoUri) }
                }
                if (stored.thumbnailUri != draft.thumbnailUri) {
                    runCatching { mediaStorage.deleteStoredFileIfOwned(stored.thumbnailUri) }
                }
                throw writeErr
            }

            if (isPublicAndPublished && !creator.isPrivateProfile) {
                val subsSnap = db.collection("creators").document(uid).collection("subscribers").get().await()
                val formatTag = if (draft.format == VideoFormat.SHORT) "Short" else "video"
                subsSnap.documents.forEach { subDoc ->
                    val subscriberId = subDoc.id
                    notifPlaylistDelegate.dispatchNotificationSafe(
                        recipientUserId = subscriberId,
                        notificationId = "upload_${videoId}_$subscriberId",
                        actorId = uid,
                        actorName = creatorLabel,
                        actorAvatarUri = creator.avatarUri,
                        type = NotificationType.VIDEO_UPLOAD,
                        videoId = videoId,
                        videoThumbnailUri = finalThumb,
                        commentId = null,
                        dedupKey = "upload:$videoId:$subscriberId",
                        message = "$creatorLabel uploaded a new $formatTag: \"${cleanTitle.take(120)}\"",
                        checkDedupExistsFirst = false
                    )
                }
            }

            onProgress(1.0f, "Published!")
            Result.success(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun recordGenuineView(videoId: String, viewerId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        if (uid != viewerId || videoId.isBlank()) return false
        val videoRef = db.collection("videos").document(videoId)
        val viewRef = videoRef.collection("views").document(uid)
        return try {
            val viewSnap = viewRef.get().await()
            if (viewSnap.exists()) {
                val lastMs = viewSnap.getTimestamp("lastCountedAt")?.toDate()?.time ?: 0L
                if (timeProvider() - lastMs < UploadLimits.GENUINE_VIEW_COOLDOWN_MS) {
                    return false
                }
            }
            val counted = db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                if (!vSnap.exists() || vSnap.getBoolean("isRemoved") == true) return@runTransaction false
                val currentViews = (vSnap.getLong("viewCount") ?: 0L).coerceAtLeast(0L)
                tx.set(
                    viewRef,
                    mapOf("userId" to uid, "lastCountedAt" to FieldValue.serverTimestamp())
                )
                tx.update(
                    videoRef,
                    mapOf("viewCount" to currentViews + 1L, "updatedAt" to FieldValue.serverTimestamp())
                )
                true
            }.await()
            counted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, viewRef.path)
            false
        }
    }

    override suspend fun toggleLike(videoId: String, userId: String): Result<Boolean> {
        val videoRef = db.collection("videos").document(videoId)
        return try {
            val uid = requireUserId()
            if (uid != userId) return Result.failure(SecurityException("Sign in mismatch."))
            val userSnap = db.collection("users").document(uid).get().await()
            val actor = toUserProfile(userSnap)
                ?: return Result.failure(IllegalStateException("User profile not found."))
            if (actor.isSuspended) return Result.failure(IllegalStateException("Suspended accounts cannot interact."))

            val likeRef = videoRef.collection("likes").document(uid)
            val userLikedRef = db.collection("users").document(uid).collection("likedVideos").document(videoId)
            var creatorId = ""
            var videoTitle = ""
            var videoThumb = ""
            val nowLiked = db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                val lSnap = tx.get(likeRef)
                if (!vSnap.exists() || vSnap.getBoolean("isRemoved") == true) {
                    throw IllegalStateException("Video is unavailable.")
                }
                creatorId = vSnap.getString("creatorId").orEmpty()
                videoTitle = vSnap.getString("title").orEmpty()
                videoThumb = vSnap.getString("thumbnailUri").orEmpty()
                val currentLikes = (vSnap.getLong("likeCount") ?: 0L).coerceAtLeast(0L)
                val willLike = !lSnap.exists()
                if (willLike) {
                    tx.set(likeRef, mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp()))
                    tx.set(userLikedRef, mapOf("videoId" to videoId, "createdAt" to FieldValue.serverTimestamp()))
                    tx.update(videoRef, mapOf("likeCount" to currentLikes + 1L, "updatedAt" to FieldValue.serverTimestamp()))
                } else {
                    tx.delete(likeRef)
                    tx.delete(userLikedRef)
                    tx.update(videoRef, mapOf("likeCount" to (currentLikes - 1L).coerceAtLeast(0L), "updatedAt" to FieldValue.serverTimestamp()))
                }
                willLike
            }.await()

            if (nowLiked && creatorId.isNotBlank() && creatorId != uid) {
                val actorLabel = actor.displayName.ifBlank { actor.username }
                notifPlaylistDelegate.dispatchNotificationSafe(
                    recipientUserId = creatorId,
                    notificationId = "like_${videoId}_$uid",
                    actorId = uid,
                    actorName = actorLabel,
                    actorAvatarUri = actor.avatarUri,
                    type = NotificationType.LIKE,
                    videoId = videoId,
                    videoThumbnailUri = videoThumb,
                    commentId = null,
                    dedupKey = "like:$videoId:$uid",
                    message = "$actorLabel liked \"$videoTitle\"",
                    checkDedupExistsFirst = true
                )
            }
            Result.success(nowLiked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, videoRef.path)
            Result.failure(e)
        }
    }

    override suspend fun addComment(
        videoId: String,
        userId: String,
        text: String,
        parentCommentId: String?
    ): Result<Unit> {
        val videoRef = db.collection("videos").document(videoId)
        val commentId = "cmt_${UUID.randomUUID().toString().take(8)}"
        val commentRef = videoRef.collection("comments").document(commentId)
        return try {
            val uid = requireUserId()
            if (uid != userId) return Result.failure(SecurityException("Sign in mismatch."))
            val cleanText = text.trim()
            if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Comment cannot be empty."))
            if (cleanText.length > 500) return Result.failure(IllegalArgumentException("Comment cannot exceed 500 characters."))

            val actorSnap = db.collection("users").document(uid).get().await()
            val actor = toUserProfile(actorSnap)
                ?: return Result.failure(IllegalStateException("User profile not found."))
            if (actor.isSuspended) return Result.failure(IllegalStateException("Suspended accounts cannot comment."))

            val authorName = actor.displayName.ifBlank { actor.username }.take(80)
            var videoCreatorId = ""
            var videoTitle = ""
            var videoThumb = ""
            var parentAuthorId: String? = null

            db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                if (!vSnap.exists() || vSnap.getBoolean("isRemoved") == true) {
                    throw IllegalStateException("Cannot comment on unavailable video.")
                }
                val parentSnap = if (!parentCommentId.isNullOrBlank()) {
                    tx.get(videoRef.collection("comments").document(parentCommentId))
                } else null
                if (parentSnap != null && !parentSnap.exists()) {
                    throw IllegalArgumentException("Parent comment not found for this video.")
                }
                val resolvedParentId = parentSnap?.let { it.getString("parentCommentId") ?: it.id }
                parentAuthorId = parentSnap?.getString("userId")
                videoCreatorId = vSnap.getString("creatorId").orEmpty()
                videoTitle = vSnap.getString("title").orEmpty()
                videoThumb = vSnap.getString("thumbnailUri").orEmpty()
                val currentComments = (vSnap.getLong("commentCount") ?: 0L).coerceAtLeast(0L)

                val commentPayload = mapOf(
                    "videoId" to videoId,
                    "userId" to uid,
                    "username" to authorName,
                    "avatarUri" to actor.avatarUri.take(1000),
                    "text" to cleanText,
                    "parentCommentId" to resolvedParentId,
                    "createdAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                tx.set(commentRef, commentPayload)
                tx.update(
                    videoRef,
                    mapOf("commentCount" to currentComments + 1L, "updatedAt" to FieldValue.serverTimestamp())
                )
            }.await()

            val notifiedRecipients = mutableSetOf<String>()
            val targetParentUser = parentAuthorId
            if (!targetParentUser.isNullOrBlank() && targetParentUser != uid) {
                notifiedRecipients += targetParentUser
                notifPlaylistDelegate.dispatchNotificationSafe(
                    recipientUserId = targetParentUser,
                    notificationId = "reply_$commentId",
                    actorId = uid,
                    actorName = authorName,
                    actorAvatarUri = actor.avatarUri,
                    type = NotificationType.REPLY,
                    videoId = videoId,
                    videoThumbnailUri = videoThumb,
                    commentId = commentId,
                    dedupKey = "reply:$commentId",
                    message = "$authorName replied to your comment on \"$videoTitle\": \"${cleanText.take(60)}\"",
                    checkDedupExistsFirst = false
                )
            }
            if (videoCreatorId.isNotBlank() && videoCreatorId != uid && notifiedRecipients.add(videoCreatorId)) {
                notifPlaylistDelegate.dispatchNotificationSafe(
                    recipientUserId = videoCreatorId,
                    notificationId = "comment_$commentId",
                    actorId = uid,
                    actorName = authorName,
                    actorAvatarUri = actor.avatarUri,
                    type = NotificationType.COMMENT,
                    videoId = videoId,
                    videoThumbnailUri = videoThumb,
                    commentId = commentId,
                    dedupKey = "comment:$commentId",
                    message = "$authorName commented on \"$videoTitle\": \"${cleanText.take(60)}\"",
                    checkDedupExistsFirst = false
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, commentRef.path)
            Result.failure(e)
        }
    }

    override suspend fun toggleSubscription(creatorId: String, subscriberId: String): Result<Boolean> {
        val subRef = db.collection("users").document(subscriberId).collection("subscriptions").document(creatorId)
        return try {
            val uid = requireUserId()
            if (uid != subscriberId) return Result.failure(SecurityException("Sign in mismatch."))
            if (creatorId == uid) return Result.failure(IllegalArgumentException("You cannot subscribe to your own channel."))

            val creatorRef = db.collection("users").document(creatorId)
            val subscriberRef = db.collection("users").document(uid)
            val creatorIndexRef = db.collection("creators").document(creatorId).collection("subscribers").document(uid)

            var subscriberName = "Creator"
            var subscriberAvatar = "drawable://img_creator_paisa_logo"

            val nowSubscribed = db.runTransaction { tx ->
                val creatorSnap = tx.get(creatorRef)
                val subscriberSnap = tx.get(subscriberRef)
                val subSnap = tx.get(subRef)
                val cIndexSnap = tx.get(creatorIndexRef)

                val creator = toUserProfile(creatorSnap)
                    ?: throw IllegalArgumentException("Creator channel not found.")
                val subscriber = toUserProfile(subscriberSnap)
                    ?: throw IllegalStateException("Subscriber profile not found.")
                if (subscriber.isSuspended) throw IllegalStateException("Suspended accounts cannot subscribe.")
                if (creator.isSuspended) throw IllegalStateException("Channel is unavailable.")

                subscriberName = subscriber.displayName.ifBlank { subscriber.username }
                subscriberAvatar = subscriber.avatarUri
                val currentCount = creator.subscriberCount.coerceAtLeast(0L)
                val willSubscribe = !subSnap.exists()

                if (willSubscribe) {
                    val subData = mapOf(
                        "creatorId" to creatorId,
                        "subscriberId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(subRef, subData)
                    tx.set(creatorIndexRef, subData)
                    tx.update(
                        creatorRef,
                        mapOf("subscriberCount" to currentCount + 1L, "updatedAt" to FieldValue.serverTimestamp())
                    )
                } else {
                    tx.delete(subRef)
                    if (cIndexSnap.exists()) tx.delete(creatorIndexRef)
                    tx.update(
                        creatorRef,
                        mapOf("subscriberCount" to (currentCount - 1L).coerceAtLeast(0L), "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
                willSubscribe
            }.await()

            if (nowSubscribed) {
                notifPlaylistDelegate.dispatchNotificationSafe(
                    recipientUserId = creatorId,
                    notificationId = "sub_${creatorId}_$uid",
                    actorId = uid,
                    actorName = subscriberName,
                    actorAvatarUri = subscriberAvatar,
                    type = NotificationType.SUBSCRIPTION,
                    videoId = null,
                    videoThumbnailUri = null,
                    commentId = null,
                    dedupKey = "sub:$creatorId:$uid",
                    message = "$subscriberName subscribed to your channel",
                    checkDedupExistsFirst = true
                )
            }
            Result.success(nowSubscribed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, subRef.path)
            Result.failure(e)
        }
    }

    override suspend fun reportVideo(videoId: String, reporterId: String, reason: String) =
        moderationDelegate.reportVideo(videoId, reporterId, reason)

    override suspend fun logError(category: ErrorCategory, message: String, userId: String?, videoId: String?) {
        Log.w("CreatorPaisaError", "[$category] $message (user=$userId, video=$videoId)")
        moderationDelegate.logError(category, message, userId, videoId)
    }

    override fun observeAllVideosForAdmin(requesterId: String): Flow<List<VideoItem>> =
        moderationDelegate.observeAllVideosForAdmin(requesterId)

    override fun observeAllUsersForAdmin(requesterId: String): Flow<List<UserProfile>> =
        moderationDelegate.observeAllUsersForAdmin(requesterId)

    override fun observeAllReportsForAdmin(requesterId: String): Flow<List<ContentReport>> =
        moderationDelegate.observeAllReportsForAdmin(requesterId)

    override fun observeAllErrorLogsForAdmin(requesterId: String): Flow<List<AppErrorLog>> =
        moderationDelegate.observeAllErrorLogsForAdmin(requesterId)

    override fun observeAppStatistics(requesterId: String): Flow<AppStatistics?> =
        moderationDelegate.observeAppStatistics(requesterId)

    override suspend fun adminRemoveVideo(adminId: String, videoId: String, reason: String) =
        moderationDelegate.adminRemoveVideo(adminId, videoId, reason)

    override suspend fun adminRestoreVideo(adminId: String, videoId: String) =
        moderationDelegate.adminRestoreVideo(adminId, videoId)

    override suspend fun adminSetUploaderApproval(adminId: String, targetUserId: String, approved: Boolean) =
        moderationDelegate.adminSetUploaderApproval(adminId, targetUserId, approved)

    override suspend fun adminSetUserSuspended(adminId: String, targetUserId: String, suspended: Boolean) =
        moderationDelegate.adminSetUserSuspended(adminId, targetUserId, suspended)

    override suspend fun adminResolveReport(adminId: String, reportId: String) =
        moderationDelegate.adminResolveReport(adminId, reportId)

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

    companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "shardamanibhajat@gmail.com"
    }
}
