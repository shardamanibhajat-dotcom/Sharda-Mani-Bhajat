package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.MediaStorage
import com.example.data.local.StoredMediaResult
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseMediaStorage(
    private val context: Context,
    private val storage: FirebaseStorage = Firebase.storage,
    private val auth: FirebaseAuth = Firebase.auth
) : MediaStorage {

    override suspend fun storeUploadMedia(
        sourceVideoUri: String,
        sourceThumbnailUri: String,
        maxVideoBytes: Long,
        customMetadata: Map<String, String>,
        onProgress: (progress: Float, stageLabel: String) -> Unit
    ): Result<StoredMediaResult> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid
            ?: return@withContext Result.failure(
                IllegalStateException("Please sign in with Google before uploading to Firebase Storage.")
            )

        val trimmedVideo = sourceVideoUri.trim()
        if (trimmedVideo.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please select a video file from your device."))
        }

        // Preserve valid remote stream URLs while uploading any local thumbnail if present
        if (trimmedVideo.startsWith("http://", ignoreCase = true) ||
            trimmedVideo.startsWith("https://", ignoreCase = true)
        ) {
            onProgress(0.35f, "Verifying stream URL...")
            val parsed = runCatching { Uri.parse(trimmedVideo) }.getOrNull()
            if (parsed == null || parsed.host.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid stream URL."))
            }
            onProgress(0.70f, "Preparing thumbnail...")
            val uploadedThumb = uploadThumbnailIfLocal(uid, sourceThumbnailUri, customMetadata)
                .getOrElse { return@withContext Result.failure(it) }
            onProgress(0.92f, "Saving video entry in Firestore...")
            return@withContext Result.success(
                StoredMediaResult(
                    videoUri = trimmedVideo,
                    thumbnailUri = uploadedThumb,
                    fileSizeBytes = 1024L
                )
            )
        }

        val parsedVideoUri = runCatching { Uri.parse(trimmedVideo) }.getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid video file URI."))

        val knownSize = resolveUriSizeBytes(parsedVideoUri)
        if (knownSize > maxVideoBytes) {
            return@withContext Result.failure(
                IllegalArgumentException("Selected video exceeds ${maxVideoBytes / (1024 * 1024)} MB limit.")
            )
        }

        val videoFileId = "vid_${UUID.randomUUID().toString().take(12)}.mp4"
        val videoRef = storage.reference.child("videos/$uid/$videoFileId")
        val videoMime = context.contentResolver.getType(parsedVideoUri)
            ?.takeIf { it.startsWith("video/") }
            ?: "video/mp4"
        val resolvedName = resolveUriDisplayName(parsedVideoUri)
        val mergedMetadata = buildMap {
            putAll(customMetadata)
            if (!containsKey("originalFileName") && !resolvedName.isNullOrBlank()) {
                put("originalFileName", resolvedName.take(120))
            }
        }
        val videoMeta = buildVideoStorageMetadata(videoMime, mergedMetadata)

        var uploadedVideoUrl: String? = null
        var uploadedThumbUrl: String? = null
        var tempExtractedThumb: File? = null

        try {
            onProgress(0.08f, "Preparing video for Firebase Storage...")
            val taskSnapshot = awaitPutFileWithProgress(
                ref = videoRef,
                fileUri = parsedVideoUri,
                metadata = videoMeta
            ) { bytesTransferred, totalBytes ->
                val expected = if (totalBytes > 0L) totalBytes else knownSize
                val fraction = if (expected > 0L) {
                    (bytesTransferred.toFloat() / expected.toFloat()).coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                val mapped = 0.10f + (fraction * 0.72f)
                onProgress(mapped, "Uploading video to Firebase Storage (${(fraction * 100).toInt()}%)...")
            }

            val finalBytes = taskSnapshot.totalByteCount.takeIf { it > 0L }
                ?: knownSize.takeIf { it > 0L }
                ?: 1024L
            if (finalBytes > maxVideoBytes) {
                runCatching { videoRef.delete().await() }
                return@withContext Result.failure(
                    IllegalArgumentException("Selected video exceeds ${maxVideoBytes / (1024 * 1024)} MB limit.")
                )
            }

            onProgress(0.84f, "Generating video download URL...")
            uploadedVideoUrl = videoRef.downloadUrl.await().toString()

            onProgress(0.88f, "Uploading thumbnail to Firebase Storage...")
            val finalThumb = if (isLocalMediaUri(sourceThumbnailUri)) {
                uploadThumbnailIfLocal(uid, sourceThumbnailUri, mergedMetadata)
                    .getOrElse { sourceThumbnailUri }
            } else {
                tempExtractedThumb = extractTempFrameThumbnail(parsedVideoUri)
                val extractedFile = tempExtractedThumb
                if (extractedFile != null) {
                    uploadThumbnailFile(uid, Uri.fromFile(extractedFile), mergedMetadata)
                        .getOrElse { sourceThumbnailUri }
                } else {
                    sourceThumbnailUri
                }
            }
            uploadedThumbUrl = finalThumb

            onProgress(0.93f, "Saving video entry in Firestore...")
            Result.success(
                StoredMediaResult(
                    videoUri = uploadedVideoUrl,
                    thumbnailUri = finalThumb,
                    fileSizeBytes = finalBytes
                )
            )
        } catch (ce: CancellationException) {
            runCatching { videoRef.delete().await() }
            uploadedThumbUrl?.let { runCatching { deleteStoredFileIfOwned(it) } }
            throw ce
        } catch (t: Throwable) {
            runCatching { videoRef.delete().await() }
            uploadedThumbUrl?.let { runCatching { deleteStoredFileIfOwned(it) } }
            Result.failure(mapStorageThrowable(t))
        } finally {
            runCatching { tempExtractedThumb?.delete() }
        }
    }

    override suspend fun storeImageMedia(sourceImageUri: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = sourceImageUri.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Image URI cannot be empty."))
        }
        if (!isLocalMediaUri(trimmed)) {
            return@withContext Result.success(trimmed)
        }
        val uid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Sign in required before uploading images."))
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid image URI."))

        val imageRef = storage.reference.child("images/$uid/img_${UUID.randomUUID().toString().take(12)}.jpg")
        val mime = context.contentResolver.getType(parsed)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val meta = storageMetadata { contentType = mime }

        try {
            awaitPutFileWithProgress(imageRef, parsed, meta) { _, _ -> }
            Result.success(imageRef.downloadUrl.await().toString())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(mapStorageThrowable(t))
        }
    }

    override suspend fun deleteStoredFileIfOwned(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val isFirebaseStorageUrl = uriString.startsWith("gs://", ignoreCase = true) ||
            uriString.contains("firebasestorage.googleapis.com", ignoreCase = true) ||
            uriString.contains("firebasestorage.app", ignoreCase = true)
        if (!isFirebaseStorageUrl) return
        withContext(Dispatchers.IO) {
            runCatching {
                storage.getReferenceFromUrl(uriString).delete().await()
            }
        }
    }

    private suspend fun uploadThumbnailIfLocal(
        uid: String,
        thumbUri: String,
        customMetadata: Map<String, String> = emptyMap()
    ): Result<String> {
        val trimmed = thumbUri.trim()
        if (!isLocalMediaUri(trimmed)) {
            return Result.success(trimmed)
        }
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return Result.success(trimmed)
        return uploadThumbnailFile(uid, parsed, customMetadata)
    }

    private suspend fun uploadThumbnailFile(
        uid: String,
        parsedUri: Uri,
        customMetadata: Map<String, String> = emptyMap()
    ): Result<String> {
        val thumbRef = storage.reference.child("thumbnails/$uid/thumb_${UUID.randomUUID().toString().take(12)}.jpg")
        val mime = context.contentResolver.getType(parsedUri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val meta = buildThumbnailStorageMetadata(mime, customMetadata)
        return try {
            awaitPutFileWithProgress(thumbRef, parsedUri, meta) { _, _ -> }
            Result.success(thumbRef.downloadUrl.await().toString())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(mapStorageThrowable(t))
        }
    }

    private fun resolveUriDisplayName(uri: Uri): String? {
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).name }
            "content" -> runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
                }
            }.getOrNull()
            else -> null
        }
    }

    private suspend fun awaitPutFileWithProgress(
        ref: StorageReference,
        fileUri: Uri,
        metadata: StorageMetadata,
        onTransferProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): UploadTask.TaskSnapshot = suspendCancellableCoroutine { cont ->
        val uploadTask = ref.putFile(fileUri, metadata)
        uploadTask.addOnProgressListener { snap ->
            onTransferProgress(snap.bytesTransferred, snap.totalByteCount)
        }.addOnSuccessListener { snap ->
            if (cont.isActive) cont.resume(snap)
        }.addOnFailureListener { err ->
            if (cont.isActive) cont.resumeWithException(err)
        }.addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
        cont.invokeOnCancellation {
            uploadTask.cancel()
        }
    }

    private fun isLocalMediaUri(uriString: String): Boolean {
        val trimmed = uriString.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("drawable://", ignoreCase = true)) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        val scheme = runCatching { Uri.parse(trimmed).scheme?.lowercase() }.getOrNull()
        return scheme == "content" || scheme == "file"
    }

    private fun resolveUriSizeBytes(uri: Uri): Long {
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).length() } ?: -1L
            "content" -> runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
                } ?: -1L
            }.getOrDefault(-1L)
            else -> -1L
        }
    }

    private fun extractTempFrameThumbnail(videoUri: Uri): File? {
        val tempThumb = File(context.cacheDir, "fb_thumb_${UUID.randomUUID().toString().take(8)}.jpg")
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
                ?: return null
            try {
                FileOutputStream(tempThumb).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                if (tempThumb.exists() && tempThumb.length() > 0L) tempThumb else {
                    tempThumb.delete()
                    null
                }
            } finally {
                bitmap.recycle()
            }
        } catch (_: Throwable) {
            tempThumb.delete()
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun mapStorageThrowable(t: Throwable): Throwable {
        if (t is StorageException) {
            val friendly = when (t.errorCode) {
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Firebase Storage bucket is not enabled yet. Enable Storage in Firebase Console and deploy storage.rules."
                StorageException.ERROR_NOT_AUTHENTICATED,
                StorageException.ERROR_NOT_AUTHORIZED ->
                    "Upload rejected by Firebase Storage rules. Verify you are signed in and storage.rules is deployed."
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                    "Network connection timed out while uploading to Firebase Storage. Please retry."
                else -> t.localizedMessage ?: "Firebase Storage upload failed (code ${t.errorCode})."
            }
            return IllegalStateException(friendly, t)
        }
        return t
    }

    companion object {
        internal fun buildVideoStorageMetadata(
            mimeType: String,
            customMetadata: Map<String, String>
        ): StorageMetadata = storageMetadata {
            contentType = mimeType.ifBlank { "video/mp4" }
            customMetadata.forEach { (rawKey, rawValue) ->
                val key = rawKey.trim()
                val value = rawValue.trim()
                if (key.isNotBlank() && value.isNotBlank()) {
                    setCustomMetadata(key.take(64), value.take(512))
                }
            }
        }

        internal fun buildThumbnailStorageMetadata(
            mimeType: String,
            customMetadata: Map<String, String>
        ): StorageMetadata = storageMetadata {
            contentType = mimeType.ifBlank { "image/jpeg" }
            listOf("videoId", "creatorId", "title").forEach { key ->
                val value = customMetadata[key]?.trim().orEmpty()
                if (value.isNotBlank()) {
                    setCustomMetadata(key, value.take(256))
                }
            }
        }
    }
}
