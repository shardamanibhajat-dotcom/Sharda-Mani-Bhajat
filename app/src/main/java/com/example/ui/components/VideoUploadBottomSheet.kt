package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UploadLimits
import com.example.data.model.UploadProgressState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoCategory
import com.example.data.model.VideoFormat
import com.example.data.model.formatDuration
import com.example.data.model.parseTagsInput
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel
import com.example.media.inspectPickedVideoMetadata
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoUploadBottomSheet(
    currentUser: UserProfile,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    dailyUploadCount: Int,
    progressState: UploadProgressState,
    onDismiss: () -> Unit,
    onPublish: (
        title: String,
        description: String,
        format: VideoFormat,
        category: VideoCategory,
        durationSeconds: Long,
        fileSizeBytes: Long,
        thumbnailUri: String,
        videoUri: String,
        tags: List<String>
    ) -> Unit,
    onCancelUpload: () -> Unit,
    onRetryUpload: () -> Unit,
    onResetState: () -> Unit,
    onVideoPublishedNavigate: (String) -> Unit,
    initialVideoUri: String = "",
    initialVideoName: String = "",
    initialThumbnailUri: String = "",
    initialDurationSeconds: Long = 0L,
    initialFileSizeBytes: Long = 0L,
    onPublishWithVisibility: ((
        title: String,
        description: String,
        format: VideoFormat,
        category: VideoCategory,
        durationSeconds: Long,
        fileSizeBytes: Long,
        thumbnailUri: String,
        videoUri: String,
        tags: List<String>,
        isPublic: Boolean
    ) -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var videoUri by remember(initialVideoUri) { mutableStateOf(initialVideoUri) }
    var selectedVideoName by remember(initialVideoName) { mutableStateOf(initialVideoName) }
    var thumbnailUri by remember(initialThumbnailUri) { mutableStateOf(initialThumbnailUri) }
    var customThumbnailPicked by remember { mutableStateOf(false) }
    var durationSeconds by remember(initialVideoUri, initialDurationSeconds) {
        mutableLongStateOf(
            if (initialDurationSeconds > 0L) initialDurationSeconds
            else if (initialVideoUri.isNotBlank()) 30L else 0L
        )
    }
    var fileSizeBytes by remember(initialVideoUri, initialFileSizeBytes) { mutableLongStateOf(initialFileSizeBytes) }
    var isInspectingMedia by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var format by remember(initialVideoUri, initialDurationSeconds) {
        mutableStateOf(
            if (initialDurationSeconds > UploadLimits.MAX_SHORT_DURATION_SECONDS) VideoFormat.LONG
            else VideoFormat.SHORT
        )
    }
    var category by remember { mutableStateOf(VideoCategory.ENTERTAINMENT) }
    var tagsInput by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun handlePickedVideo(uri: Uri?) {
        if (uri == null) return
        videoUri = uri.toString()
        validationError = null
        onResetState()
        isInspectingMedia = true
        coroutineScope.launch {
            val meta = inspectPickedVideoMetadata(
                context = context,
                uri = uri,
                extractThumbnail = !customThumbnailPicked
            )
            selectedVideoName = meta.displayName
            durationSeconds = meta.durationSeconds.coerceAtLeast(1L)
            format = if (durationSeconds <= UploadLimits.MAX_SHORT_DURATION_SECONDS) {
                VideoFormat.SHORT
            } else {
                VideoFormat.LONG
            }
            if (meta.fileSizeBytes > 0L) {
                fileSizeBytes = meta.fileSizeBytes
            }
            if (!customThumbnailPicked && !meta.previewThumbUri.isNullOrBlank()) {
                thumbnailUri = meta.previewThumbUri
            }
            isInspectingMedia = false
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> handlePickedVideo(uri) }

    val documentVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> handlePickedVideo(uri) }

    val thumbnailPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            customThumbnailPicked = true
            thumbnailUri = uri.toString()
        }
    }

    val isUploading = progressState is UploadProgressState.Uploading
    val isApproved = currentUser.isApprovedUploader || currentUser.role == UserRole.ADMIN
    val remainingQuota = (UploadLimits.MAX_DAILY_UPLOADS - dailyUploadCount).coerceAtLeast(0)
    val selectableCategories = remember { VideoCategory.entries.filter { it != VideoCategory.ALL } }
    val effectiveDuration = if (durationSeconds > 0L) durationSeconds else 30L
    val exceedsFormatLimit = when (format) {
        VideoFormat.SHORT -> effectiveDuration > UploadLimits.MAX_SHORT_DURATION_SECONDS
        VideoFormat.LONG -> effectiveDuration > UploadLimits.MAX_LONG_DURATION_SECONDS
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isUploading) onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("video_upload_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strings.uploadTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.uploadBottomSheetSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { if (!isUploading) onDismiss() },
                    enabled = !isUploading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("upload_sheet_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.cancel
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Video file selector card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (videoUri.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_sheet_picker_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (videoUri.isBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.selectVideoPrompt,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${strings.dailyQuotaLabel}: $dailyUploadCount / ${UploadLimits.MAX_DAILY_UPLOADS}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (remainingQuota > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        videoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    enabled = !isUploading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("upload_sheet_select_video_button")
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.pickVideoFile, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { documentVideoPicker.launch(arrayOf("video/*")) },
                                    enabled = !isUploading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("upload_sheet_browse_files_button")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.browseVideoFiles, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(92.dp)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (thumbnailUri.isNotBlank()) {
                                    MediaImage(
                                        uri = thumbnailUri,
                                        contentDescription = "Selected video thumbnail",
                                        modifier = Modifier.matchParentSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.VideoFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedVideoName.ifBlank { "Selected video file" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("upload_sheet_selected_video_name")
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val sizeLabel = if (fileSizeBytes > 0L) {
                                    String.format("%.1f MB", fileSizeBytes / (1024f * 1024f))
                                } else {
                                    "Ready"
                                }
                                Text(
                                    text = "${formatDuration(effectiveDuration)} · $sizeLabel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        if (isInspectingMedia) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                enabled = !isUploading,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("upload_sheet_select_video_button")
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.changeVideoFile, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = {
                                    thumbnailPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploading,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("upload_sheet_pick_thumbnail_button")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.pickThumbnail, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= 120) {
                        title = it
                        validationError = null
                        onResetState()
                    }
                },
                enabled = !isUploading,
                label = { Text(strings.videoTitleLabel) },
                supportingText = { Text("${title.trim().length} / 120 (min 3)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_sheet_title_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description input
            OutlinedTextField(
                value = description,
                onValueChange = {
                    if (it.length <= 1000) {
                        description = it
                    }
                },
                enabled = !isUploading,
                label = { Text(strings.videoDescriptionLabel) },
                supportingText = { Text("${description.length} / 1000") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_sheet_description_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Format chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = format == VideoFormat.SHORT,
                    onClick = {
                        if (!isUploading) {
                            format = VideoFormat.SHORT
                            onResetState()
                        }
                    },
                    label = { Text(strings.filterShorts) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("upload_sheet_format_short")
                )
                FilterChip(
                    selected = format == VideoFormat.LONG,
                    onClick = {
                        if (!isUploading) {
                            format = VideoFormat.LONG
                            onResetState()
                        }
                    },
                    label = { Text(strings.filterLongVideos) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("upload_sheet_format_long")
                )
            }

            if (exceedsFormatLimit) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (format == VideoFormat.SHORT) {
                        "Shorts are limited to 3 minutes (180s). Switch to Long Video."
                    } else {
                        "Long videos are limited to 30 minutes (1800s)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Category chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectableCategories, key = { it.name }) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { if (!isUploading) category = cat },
                        label = { Text(categoryLabel(cat, currentLanguage)) },
                        modifier = Modifier.testTag("upload_sheet_category_${cat.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = tagsInput,
                onValueChange = {
                    if (it.length <= 160) {
                        tagsInput = it
                    }
                },
                enabled = !isUploading,
                label = { Text(strings.videoTagsLabel) },
                placeholder = { Text(strings.videoTagsHint) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_sheet_tags_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = isPublic,
                    onClick = { if (!isUploading) isPublic = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(strings.visibilityPublicOption) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("upload_sheet_visibility_public")
                )
                FilterChip(
                    selected = !isPublic,
                    onClick = { if (!isUploading) isPublic = false },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(strings.visibilityPrivateOption) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("upload_sheet_visibility_private")
                )
            }

            AnimatedVisibility(visible = validationError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("upload_sheet_validation_error")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = validationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (progressState) {
                is UploadProgressState.Uploading -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("upload_sheet_progress")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = progressState.stageLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(progressState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onCancelUpload,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("upload_sheet_cancel_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.cancelUpload)
                            }
                        }
                    }
                }

                is UploadProgressState.Cancelled -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = strings.uploadCancelledNotice,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRetryUpload,
                                modifier = Modifier.testTag("upload_sheet_retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.retryUpload)
                            }
                        }
                    }
                }

                is UploadProgressState.Failure -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("upload_sheet_error")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = progressState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onRetryUpload,
                                modifier = Modifier.testTag("upload_sheet_retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.retryUpload)
                            }
                        }
                    }
                }

                is UploadProgressState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("upload_sheet_success")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Uploaded to Firebase Storage & Firestore!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onVideoPublishedNavigate(progressState.videoId) },
                                modifier = Modifier.testTag("upload_sheet_watch_button")
                            ) {
                                Text("Watch")
                            }
                        }
                    }
                }

                UploadProgressState.Idle -> Unit
            }

            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    when {
                        !isApproved -> {
                            validationError = strings.uploadNotApprovedNotice
                        }
                        videoUri.isBlank() -> {
                            validationError = strings.selectVideoPrompt
                        }
                        trimmedTitle.length < 3 -> {
                            validationError = "Video title must be at least 3 characters."
                        }
                        exceedsFormatLimit -> {
                            validationError = "Selected video exceeds format duration limit."
                        }
                        else -> {
                            validationError = null
                            val size = if (fileSizeBytes > 0L) fileSizeBytes else 1024L
                            val parsedTags = parseTagsInput(tagsInput)
                            if (onPublishWithVisibility != null) {
                                onPublishWithVisibility(
                                    trimmedTitle,
                                    description.trim(),
                                    format,
                                    category,
                                    effectiveDuration,
                                    size,
                                    thumbnailUri,
                                    videoUri,
                                    parsedTags,
                                    isPublic
                                )
                            } else {
                                onPublish(
                                    trimmedTitle,
                                    description.trim(),
                                    format,
                                    category,
                                    effectiveDuration,
                                    size,
                                    thumbnailUri,
                                    videoUri,
                                    parsedTags
                                )
                            }
                        }
                    }
                },
                enabled = !isUploading && !isInspectingMedia,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("upload_sheet_publish_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.uploadToCloudAction, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
