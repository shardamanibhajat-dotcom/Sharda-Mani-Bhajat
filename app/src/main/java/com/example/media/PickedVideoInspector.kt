package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PickedVideoMetadata(
    val displayName: String,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val previewThumbUri: String?
)

suspend fun inspectPickedVideoMetadata(
    context: Context,
    uri: Uri,
    extractThumbnail: Boolean
): PickedVideoMetadata = withContext(Dispatchers.IO) {
    var displayName = "Selected Video"
    var durationSeconds = 30L
    var fileSizeBytes = 0L
    var previewThumbUri: String? = null

    if (uri.scheme.equals("file", ignoreCase = true)) {
        uri.path?.let { path ->
            val f = File(path)
            if (f.exists()) {
                displayName = f.name
                fileSizeBytes = f.length()
            }
        }
    } else {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
    }

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        if (durationMs != null && durationMs > 0L) {
            durationSeconds = (durationMs / 1000L).coerceAtLeast(1L)
        }
        if (extractThumbnail) {
            val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
            if (bitmap != null) {
                try {
                    val tempThumb = File(context.cacheDir, "preview_thumb_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(tempThumb).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                    if (tempThumb.exists() && tempThumb.length() > 0L) {
                        previewThumbUri = Uri.fromFile(tempThumb).toString()
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    } catch (_: Throwable) {
        // Keep fallback metadata when MediaMetadataRetriever cannot inspect the URI
    } finally {
        runCatching { retriever.release() }
    }

    PickedVideoMetadata(
        displayName = displayName,
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes,
        previewThumbUri = previewThumbUri
    )
}
