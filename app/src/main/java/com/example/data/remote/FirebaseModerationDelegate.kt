package com.example.data.remote

import com.example.data.model.AppErrorLog
import com.example.data.model.AppStatistics
import com.example.data.model.ContentReport
import com.example.data.model.ErrorCategory
import com.example.data.model.ReportStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoItem
import com.example.data.remote.FirestoreMappers.toErrorLog
import com.example.data.remote.FirestoreMappers.toReport
import com.example.data.remote.FirestoreMappers.toUserProfile
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

internal class FirebaseModerationDelegate(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Authentication required.")

    private fun observeIsAdmin(requesterId: String): Flow<Boolean> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != requesterId) {
            emit(false)
            return@flow
        }
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.getString("role") == UserRole.ADMIN.name }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(false)
                }
        )
    }

    private suspend fun verifyAdmin(adminId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(SecurityException("Authentication required."))
        if (uid != adminId) return Result.failure(SecurityException("Unauthorized admin session."))
        return try {
            val userSnap = db.collection("users").document(uid).get().await()
            if (userSnap.getString("role") != UserRole.ADMIN.name) {
                Result.failure(SecurityException("Unauthorized: Admin permissions required."))
            } else {
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun reportVideo(videoId: String, reporterId: String, reason: String): Result<Unit> {
        val rid = "rep_${UUID.randomUUID().toString().take(8)}"
        val reportRef = db.collection("reports").document(rid)
        return try {
            val uid = requireUid()
            if (uid != reporterId) return Result.failure(SecurityException("Sign in required to report."))
            val cleanReason = reason.trim()
            if (cleanReason.length < 4) {
                return Result.failure(IllegalArgumentException("Please provide a brief reason for reporting."))
            }
            val videoRef = db.collection("videos").document(videoId)
            db.runTransaction { tx ->
                val vSnap = tx.get(videoRef)
                if (!vSnap.exists()) throw IllegalArgumentException("Video not found.")
                val vTitle = vSnap.getString("title").orEmpty().take(120)
                val currentReports = (vSnap.getLong("reportCount") ?: 0L).coerceAtLeast(0L)
                tx.set(
                    reportRef,
                    mapOf(
                        "videoId" to videoId,
                        "videoTitle" to vTitle,
                        "reporterId" to uid,
                        "reason" to cleanReason.take(500),
                        "status" to ReportStatus.OPEN.name,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    videoRef,
                    mapOf(
                        "reportCount" to currentReports + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reportRef.path)
            Result.failure(e)
        }
    }

    suspend fun logError(category: ErrorCategory, message: String, userId: String?, videoId: String?) {
        val uid = auth.currentUser?.uid ?: return
        val logId = "err_${UUID.randomUUID().toString().take(8)}"
        val path = "errorLogs/$logId"
        try {
            val payload = mapOf(
                "category" to category.name,
                "message" to message.ifBlank { "Error" }.take(1000),
                "userId" to (userId ?: uid),
                "videoId" to videoId,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.document(path).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllVideosForAdmin(requesterId: String): Flow<List<VideoItem>> =
        observeIsAdmin(requesterId).flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(emptyList())
            else db.collection("videos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { toVideoItem(it) } }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == requesterId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "videos")
                    }
                    emit(emptyList())
                }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllUsersForAdmin(requesterId: String): Flow<List<UserProfile>> =
        observeIsAdmin(requesterId).flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(emptyList())
            else db.collection("users")
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { toUserProfile(it) } }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == requesterId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users")
                    }
                    emit(emptyList())
                }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllReportsForAdmin(requesterId: String): Flow<List<ContentReport>> =
        observeIsAdmin(requesterId).flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(emptyList())
            else db.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { toReport(it) } }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == requesterId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "reports")
                    }
                    emit(emptyList())
                }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllErrorLogsForAdmin(requesterId: String): Flow<List<AppErrorLog>> =
        observeIsAdmin(requesterId).flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(emptyList())
            else db.collection("errorLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { toErrorLog(it) } }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == requesterId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "errorLogs")
                    }
                    emit(emptyList())
                }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAppStatistics(requesterId: String): Flow<AppStatistics?> =
        observeIsAdmin(requesterId).flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(null)
            else combine(
                observeAllUsersForAdmin(requesterId),
                observeAllVideosForAdmin(requesterId),
                observeAllReportsForAdmin(requesterId),
                observeAllErrorLogsForAdmin(requesterId)
            ) { users, videos, reports, errors ->
                AppStatistics(
                    totalUsers = users.size,
                    approvedCreators = users.count { it.isApprovedUploader },
                    totalVideos = videos.size,
                    activeVideos = videos.count { !it.isRemoved },
                    totalViews = videos.sumOf { it.viewCount },
                    openReports = reports.count { it.status == ReportStatus.OPEN },
                    totalErrors = errors.size
                )
            }
        }

    suspend fun adminRemoveVideo(adminId: String, videoId: String, reason: String): Result<Unit> {
        verifyAdmin(adminId).onFailure { return Result.failure(it) }
        val path = "videos/$videoId"
        return try {
            db.document(path).update(
                mapOf(
                    "isRemoved" to true,
                    "removalReason" to reason.trim().ifBlank { "Removed by moderation team" }.take(300),
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

    suspend fun adminRestoreVideo(adminId: String, videoId: String): Result<Unit> {
        verifyAdmin(adminId).onFailure { return Result.failure(it) }
        val path = "videos/$videoId"
        return try {
            db.document(path).update(
                mapOf(
                    "isRemoved" to false,
                    "removalReason" to null,
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

    suspend fun adminSetUploaderApproval(adminId: String, targetUserId: String, approved: Boolean): Result<Unit> {
        verifyAdmin(adminId).onFailure { return Result.failure(it) }
        val path = "users/$targetUserId"
        return try {
            db.document(path).update(
                mapOf(
                    "isApprovedUploader" to approved,
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

    suspend fun adminSetUserSuspended(adminId: String, targetUserId: String, suspended: Boolean): Result<Unit> {
        verifyAdmin(adminId).onFailure { return Result.failure(it) }
        if (targetUserId == adminId) {
            return Result.failure(IllegalArgumentException("Admins cannot suspend their own account."))
        }
        val path = "users/$targetUserId"
        return try {
            db.document(path).update(
                mapOf(
                    "isSuspended" to suspended,
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

    suspend fun adminResolveReport(adminId: String, reportId: String): Result<Unit> {
        verifyAdmin(adminId).onFailure { return Result.failure(it) }
        val path = "reports/$reportId"
        return try {
            db.document(path).update(mapOf("status" to ReportStatus.RESOLVED.name)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
