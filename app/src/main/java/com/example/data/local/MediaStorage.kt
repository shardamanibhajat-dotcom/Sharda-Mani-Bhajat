package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class StoredMediaResult(
    val videoUri: String,
    val thumbnailUri: String,
    val fileSizeBytes: Long
)

interface MediaStorage {
    suspend fun storeUploadMedia(
        sourceVideoUri: String,
        sourceThumbnailUri: String,
        maxVideoBytes: Long,
        customMetadata: Map<String, String> = emptyMap(),
        onProgress: (progress: Float, stageLabel: String) -> Unit
    ): Result<StoredMediaResult>

    suspend fun storeImageMedia(sourceImageUri: String): Result<String> =
        Result.success(sourceImageUri.trim())

    suspend fun deleteStoredFileIfOwned(uriString: String?)
}

class LocalMediaStorage(
    private val context: Context
) : MediaStorage {

    private val baseDir: File
        get() = File(context.applicationContext.filesDir, "creator_paisa_media")

    private val videosDir: File
        get() = File(baseDir, "videos").apply { mkdirs() }

    private val thumbsDir: File
        get() = File(baseDir, "thumbnails").apply { mkdirs() }

    override suspend fun storeUploadMedia(
        sourceVideoUri: String,
        sourceThumbnailUri: String,
        maxVideoBytes: Long,
        customMetadata: Map<String, String>,
        onProgress: (progress: Float, stageLabel: String) -> Unit
    ): Result<StoredMediaResult> = withContext(Dispatchers.IO) {
        val trimmedVideo = sourceVideoUri.trim()
        if (trimmedVideo.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please select a video file from your gallery."))
        }

        // Handle remote stream URLs (http/https) without fake delays
        if (trimmedVideo.startsWith("http://", ignoreCase = true) ||
            trimmedVideo.startsWith("https://", ignoreCase = true)
        ) {
            onProgress(0.35f, "Verifying stream URL...")
            val parsed = runCatching { Uri.parse(trimmedVideo) }.getOrNull()
            if (parsed == null || parsed.host.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid stream URL."))
            }
            onProgress(0.75f, "Preparing thumbnail...")
            val storedThumb = copyThumbnailIfLocal(sourceThumbnailUri)
            onProgress(0.95f, "Saving video metadata...")
            return@withContext Result.success(
                StoredMediaResult(
                    videoUri = trimmedVideo,
                    thumbnailUri = storedThumb,
                    fileSizeBytes = 0L
                )
            )
        }

        val parsedUri = runCatching { Uri.parse(trimmedVideo) }.getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid video file URI."))

        val destVideoFile = File(videosDir, "vid_${UUID.randomUUID().toString().take(12)}.mp4")
        var copiedBytes = 0L

        try {
            val expectedSize = resolveUriSizeBytes(parsedUri)
            if (expectedSize > maxVideoBytes) {
                return@withContext Result.failure(
                    IllegalArgumentException("Selected video exceeds ${maxVideoBytes / (1024 * 1024)} MB limit.")
                )
            }

            onProgress(0.08f, "Reading video from device...")
            val input = openInputStream(parsedUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Could not read selected video file. Please pick it from gallery again.")
                )

            input.use { stream ->
                FileOutputStream(destVideoFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReportedBucket = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = stream.read(buffer)
                        if (read == -1) break
                        copiedBytes += read
                        if (copiedBytes > maxVideoBytes) {
                            throw IllegalArgumentException(
                                "Selected video exceeds ${maxVideoBytes / (1024 * 1024)} MB limit."
                            )
                        }
                        output.write(buffer, 0, read)

                        val fraction = if (expectedSize > 0L) {
                            (copiedBytes.toFloat() / expectedSize.toFloat()).coerceIn(0f, 1f)
                        } else {
                            (copiedBytes.toFloat() / (maxVideoBytes.coerceAtLeast(1L).toFloat())).coerceIn(0.05f, 0.9f)
                        }
                        val mappedProgress = 0.10f + (fraction * 0.72f)
                        val bucket = (mappedProgress * 50).toInt()
                        if (bucket != lastReportedBucket) {
                            lastReportedBucket = bucket
                            onProgress(mappedProgress, "Storing video file (${(mappedProgress * 100).toInt()}%)...")
                        }
                    }
                    output.flush()
                }
            }

            if (copiedBytes <= 0L || !destVideoFile.exists() || destVideoFile.length() <= 0L) {
                destVideoFile.delete()
                return@withContext Result.failure(IllegalStateException("Selected video file is empty or unreadable."))
            }

            coroutineContext.ensureActive()
            onProgress(0.86f, "Saving thumbnail...")
            val finalThumb = resolveAndStoreThumbnail(sourceThumbnailUri, destVideoFile)

            onProgress(0.96f, "Finalizing upload...")
            Result.success(
                StoredMediaResult(
                    videoUri = Uri.fromFile(destVideoFile).toString(),
                    thumbnailUri = finalThumb,
                    fileSizeBytes = copiedBytes
                )
            )
        } catch (t: Throwable) {
            runCatching { destVideoFile.delete() }
            if (t is kotlinx.coroutines.CancellationException) throw t
            Result.failure(t)
        }
    }

    override suspend fun storeImageMedia(sourceImageUri: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = sourceImageUri.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Image URI cannot be empty."))
        }
        val stored = copyThumbnailIfLocal(trimmed)
        Result.success(stored)
    }

    override suspend fun deleteStoredFileIfOwned(uriString: String?) {
        if (uriString.isNullOrBlank() || !uriString.startsWith("file://")) return
        withContext(Dispatchers.IO) {
            runCatching {
                val path = Uri.parse(uriString).path ?: return@runCatching
                val target = File(path)
                val ownedRoot = baseDir.canonicalPath
                if (target.exists() && target.canonicalPath.startsWith(ownedRoot)) {
                    target.delete()
                }
            }
        }
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

    private fun openInputStream(uri: Uri): InputStream? {
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { path ->
                val f = File(path)
                if (f.exists() && f.canRead()) FileInputStream(f) else null
            }
            else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        }
    }

    private fun resolveAndStoreThumbnail(sourceThumbnailUri: String, storedVideoFile: File): String {
        val copiedCustom = copyThumbnailIfLocal(sourceThumbnailUri)
        if (copiedCustom.isNotBlank() && !copiedCustom.startsWith("drawable://")) {
            return copiedCustom
        }
        // Extract a real frame thumbnail from the uploaded video when no custom thumbnail was chosen
        val extracted = extractVideoFrameThumbnail(storedVideoFile)
        if (extracted != null) {
            return extracted
        }
        return sourceThumbnailUri
    }

    private fun copyThumbnailIfLocal(sourceThumbnailUri: String): String {
        val trimmed = sourceThumbnailUri.trim()
        if (trimmed.isBlank() ||
            trimmed.startsWith("drawable://") ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
        if (parsed.scheme?.lowercase() == "file") {
            val existingPath = parsed.path?.let { File(it).canonicalPath }.orEmpty()
            val ownedThumbsRoot = runCatching { thumbsDir.canonicalPath }.getOrDefault("")
            if (ownedThumbsRoot.isNotEmpty() && existingPath.startsWith(ownedThumbsRoot)) {
                return trimmed
            }
        }
        val destThumb = File(thumbsDir, "thumb_${UUID.randomUUID().toString().take(12)}.jpg")
        return runCatching {
            openInputStream(parsed)?.use { input ->
                FileOutputStream(destThumb).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }
            if (destThumb.exists() && destThumb.length() > 0L) {
                Uri.fromFile(destThumb).toString()
            } else {
                destThumb.delete()
                trimmed
            }
        }.getOrElse {
            destThumb.delete()
            trimmed
        }
    }

    private fun extractVideoFrameThumbnail(videoFile: File): String? {
        val destThumb = File(thumbsDir, "thumb_${UUID.randomUUID().toString().take(12)}.jpg")
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
                ?: return null
            try {
                FileOutputStream(destThumb).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (destThumb.exists() && destThumb.length() > 0L) {
                    Uri.fromFile(destThumb).toString()
                } else {
                    destThumb.delete()
                    null
                }
            } finally {
                bitmap.recycle()
            }
        } catch (_: Throwable) {
            destThumb.delete()
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/**
 * Fallback no-op storage used when constructing LocalCreatorPaisaRepository in pure unit tests
 * that do not supply an Android filesDir-backed LocalMediaStorage.
 */
object PassthroughMediaStorage : MediaStorage {
    override suspend fun storeUploadMedia(
        sourceVideoUri: String,
        sourceThumbnailUri: String,
        maxVideoBytes: Long,
        customMetadata: Map<String, String>,
        onProgress: (Float, String) -> Unit
    ): Result<StoredMediaResult> {
        onProgress(0.5f, "Validating media...")
        if (sourceVideoUri.isBlank()) {
            return Result.failure(IllegalArgumentException("Please select a video file or provide a valid stream URL."))
        }
        onProgress(0.95f, "Finalizing upload...")
        return Result.success(
            StoredMediaResult(
                videoUri = sourceVideoUri.trim(),
                thumbnailUri = sourceThumbnailUri.trim(),
                fileSizeBytes = 0L
            )
        )
    }

    override suspend fun storeImageMedia(sourceImageUri: String): Result<String> =
        Result.success(sourceImageUri.trim())

    override suspend fun deleteStoredFileIfOwned(uriString: String?) = Unit
}
