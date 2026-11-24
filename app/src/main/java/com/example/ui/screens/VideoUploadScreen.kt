package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
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
import com.example.media.ActivePlaybackCoordinator
import com.example.media.ExoPlayerFactory
import com.example.media.inspectPickedVideoMetadata
import com.example.ui.components.MediaImage
import com.example.ui.components.MonetizationUnavailableBanner
import kotlinx.coroutines.launch
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoUploadScreen(
    currentUser: UserProfile,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    dailyUploadCount: Int,
    progressState: UploadProgressState,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

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

    fun handlePickedVideoUri(uri: Uri?) {
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

    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> handlePickedVideoUri(uri) }

    val documentVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> handlePickedVideoUri(uri) }

    val thumbnailPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            customThumbnailPicked = true
            thumbnailUri = uri.toString()
        }
    }

    val previewPlayer = remember(videoUri) {
        if (videoUri.isNotBlank()) {
            ExoPlayerFactory.create(context, isShortLoop = false).apply {
                setMediaItem(ExoPlayerFactory.buildMediaItem(videoUri))
                prepare()
                playWhenReady = false
            }
        } else {
            null
        }
    }

    var isPreviewPlaying by remember(videoUri) { mutableStateOf(false) }
    var previewPlaybackState by remember(videoUri) { mutableIntStateOf(Player.STATE_IDLE) }
    var previewError by remember(videoUri) { mutableStateOf<String?>(null) }

    DisposableEffect(previewPlayer, lifecycleOwner) {
        val player = previewPlayer
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    previewPlaybackState = state
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPreviewPlaying = playing
                    if (playing) {
                        ActivePlaybackCoordinator.onPlaybackStarted(player)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    previewError = error.message ?: "Unable to preview selected video."
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    player.pause()
                }
            }
            player.addListener(listener)
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.removeListener(listener)
                ActivePlaybackCoordinator.onPlayerReleased(player)
                player.release()
            }
        }
    }

    LaunchedEffect(progressState) {
        if (progressState is UploadProgressState.Uploading) {
            previewPlayer?.pause()
        }
    }

    val isApproved = currentUser.isApprovedUploader || currentUser.role == UserRole.ADMIN
    val remainingQuota = (UploadLimits.MAX_DAILY_UPLOADS - dailyUploadCount).coerceAtLeast(0)
    val selectableCategories = remember { VideoCategory.entries.filter { it != VideoCategory.ALL } }
    val maxAllowedBytes = if (format == VideoFormat.SHORT) {
        UploadLimits.MAX_SHORT_FILE_BYTES
    } else {
        UploadLimits.MAX_LONG_FILE_BYTES
    }
    val exceedsFormatDuration = when (format) {
        VideoFormat.SHORT -> durationSeconds > UploadLimits.MAX_SHORT_DURATION_SECONDS
        VideoFormat.LONG -> durationSeconds > UploadLimits.MAX_LONG_DURATION_SECONDS
    }
    val exceedsFileSize = fileSizeBytes > maxAllowedBytes

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("video_upload_screen")
        ) {
            // Header + Firebase Storage pipeline callout
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = strings.uploadTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.firebaseStoragePipelineLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MonetizationUnavailableBanner(showDetail = false)

            // Creator Quota & Upload Approval Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.dailyQuotaLabel,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "$dailyUploadCount / ${UploadLimits.MAX_DAILY_UPLOADS}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingQuota > 0) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.testTag("video_upload_quota_text")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            (dailyUploadCount.toFloat() / UploadLimits.MAX_DAILY_UPLOADS).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!isApproved) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.uploadNotApprovedNotice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.testTag("video_upload_not_approved_banner")
                                )
                            }
                        }
                    }
                }
            }

            // Device Video File Picker + Media3 Preview Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (videoUri.isNotBlank()) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_upload_media_card")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (format == VideoFormat.SHORT) 16f / 10f else 16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF141824),
                                        Color(0xFF201A2C)
                                    )
                                )
                            )
                            .testTag("video_upload_preview_box")
                    ) {
                        if (previewPlayer != null) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = true
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        player = previewPlayer
                                    }
                                },
                                update = { pv ->
                                    if (pv.player !== previewPlayer) pv.player = previewPlayer
                                },
                                onRelease = { pv -> pv.player = null },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (previewPlaybackState == Player.STATE_BUFFERING && previewError == null) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }

                            if (previewError == null) {
                                Surface(
                                    onClick = {
                                        if (previewPlayer.isPlaying) {
                                            previewPlayer.pause()
                                        } else {
                                            if (previewPlaybackState == Player.STATE_ENDED) {
                                                previewPlayer.seekTo(0L)
                                            }
                                            previewPlayer.play()
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_upload_preview_play_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPreviewPlaying) "Pause preview" else "Play preview",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPreviewPlaying) "Pause" else "Preview",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = strings.selectVideoPrompt,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "MP4 · MOV · MKV (Shorts ≤ 50 MB · Long ≤ 250 MB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }

                    if (isInspectingMedia) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (videoUri.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${selectedVideoName.ifBlank { "Selected device video" }} · ${
                                            if (durationSeconds > 0L) formatDuration(durationSeconds) else "Ready"
                                        } · ${formatReadableSize(fileSizeBytes)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("video_upload_selected_file_badge")
                                    )
                                    Text(
                                        text = "Firebase Storage: videos/${currentUser.id}/...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = strings.storageMetadataAttachedHint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        previewPlayer?.pause()
                                        videoUri = ""
                                        selectedVideoName = ""
                                        durationSeconds = 0L
                                        fileSizeBytes = 0L
                                        if (!customThumbnailPicked) {
                                            thumbnailUri = ""
                                        }
                                        validationError = null
                                        onResetState()
                                    },
                                    enabled = progressState !is UploadProgressState.Uploading,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_upload_clear_file_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.clearSelectedFile
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                galleryVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = progressState !is UploadProgressState.Uploading,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("video_upload_pick_gallery_button")
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (videoUri.isBlank()) strings.pickVideoFile else strings.changeVideoFile,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { documentVideoPicker.launch(arrayOf("video/*")) },
                            enabled = progressState !is UploadProgressState.Uploading,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("video_upload_browse_files_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.browseVideoFiles, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (thumbnailUri.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                MediaImage(
                                    uri = thumbnailUri,
                                    contentDescription = "Selected thumbnail preview",
                                    modifier = Modifier
                                        .width(84.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Text(
                                    text = if (customThumbnailPicked) "Custom thumbnail" else "Auto frame thumbnail",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        OutlinedButton(
                            onClick = {
                                thumbnailPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = progressState !is UploadProgressState.Uploading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_upload_pick_thumb_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.pickThumbnail, maxLines = 1)
                        }
                    }
                }
            }

            // Video Details Card: Title, Description, Format, Category, Tags
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { next ->
                            if (next.length <= 120) {
                                title = next
                                validationError = null
                                onResetState()
                            }
                        },
                        label = { Text(strings.videoTitleLabel) },
                        supportingText = {
                            Text("${title.trim().length}/120")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_upload_title_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { next ->
                            if (next.length <= 1000) {
                                description = next
                                validationError = null
                            }
                        },
                        label = { Text(strings.videoDescriptionLabel) },
                        supportingText = {
                            Text("${description.trim().length}/1000")
                        },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_upload_description_input")
                    )

                    Text(
                        text = strings.videoFormatLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = format == VideoFormat.SHORT,
                            onClick = {
                                format = VideoFormat.SHORT
                                validationError = null
                                onResetState()
                            },
                            label = { Text(strings.shortFormatOption) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_upload_format_short")
                        )
                        FilterChip(
                            selected = format == VideoFormat.LONG,
                            onClick = {
                                format = VideoFormat.LONG
                                validationError = null
                                onResetState()
                            },
                            label = { Text(strings.longFormatOption) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_upload_format_long")
                        )
                    }

                    if (exceedsFormatDuration || exceedsFileSize) {
                        Text(
                            text = when {
                                exceedsFileSize -> "Selected video exceeds ${maxAllowedBytes / (1024 * 1024)} MB limit for ${format.name.lowercase()}."
                                format == VideoFormat.SHORT -> "Video exceeds 3-minute (180s) Shorts limit. Switch to Long Video."
                                else -> "Video exceeds 30-minute (1800s) Long Video limit."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = strings.videoCategoryLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_upload_category_row")
                    ) {
                        items(selectableCategories, key = { it.name }) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(categoryLabel(cat, currentLanguage)) },
                                modifier = Modifier.testTag("video_upload_category_${cat.name}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { if (it.length <= 160) tagsInput = it },
                        label = { Text(strings.videoTagsLabel) },
                        placeholder = { Text(strings.videoTagsHint) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_upload_tags_input")
                    )

                    Text(
                        text = strings.videoVisibilityLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = isPublic,
                            onClick = { isPublic = true },
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
                                .testTag("video_upload_visibility_public")
                        )
                        FilterChip(
                            selected = !isPublic,
                            onClick = { isPublic = false },
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
                                .testTag("video_upload_visibility_private")
                        )
                    }
                }
            }

            AnimatedVisibility(visible = validationError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_upload_validation_error")
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

            // Firebase Storage Transfer & Result State Cards
            when (progressState) {
                is UploadProgressState.Uploading -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_upload_progress_card")
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
                                Text(
                                    text = "${(progressState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
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
                                    .testTag("video_upload_cancel_button")
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
                            .testTag("video_upload_cancelled_banner")
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_upload_retry_button")
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
                            .testTag("video_upload_error_banner")
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onRetryUpload,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_upload_retry_button")
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
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_upload_success_banner")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Uploaded to Firebase Storage & published!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { onVideoPublishedNavigate(progressState.videoId) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_upload_watch_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Watch")
                                }
                                OutlinedButton(
                                    onClick = {
                                        title = ""
                                        description = ""
                                        tagsInput = ""
                                        isPublic = true
                                        videoUri = ""
                                        selectedVideoName = ""
                                        thumbnailUri = ""
                                        customThumbnailPicked = false
                                        durationSeconds = 0L
                                        fileSizeBytes = 0L
                                        validationError = null
                                        onResetState()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_upload_another_button")
                                ) {
                                    Text(strings.uploadAnotherVideo)
                                }
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
                        videoUri.isBlank() -> {
                            validationError = strings.videoFileRequiredError
                        }
                        trimmedTitle.length < 3 -> {
                            validationError = strings.videoTitleTooShort
                        }
                        exceedsFormatDuration || exceedsFileSize -> {
                            validationError = "Selected video exceeds format limits."
                        }
                        else -> {
                            validationError = null
                            previewPlayer?.pause()
                            val effectiveDuration = if (durationSeconds > 0L) durationSeconds else 30L
                            val effectiveSize = if (fileSizeBytes > 0L) fileSizeBytes else 1024L
                            val parsedTags = parseTagsInput(tagsInput)
                            if (onPublishWithVisibility != null) {
                                onPublishWithVisibility(
                                    trimmedTitle,
                                    description.trim(),
                                    format,
                                    category,
                                    effectiveDuration,
                                    effectiveSize,
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
                                    effectiveSize,
                                    thumbnailUri,
                                    videoUri,
                                    parsedTags
                                )
                            }
                        }
                    }
                },
                enabled = progressState !is UploadProgressState.Uploading && !isInspectingMedia,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("video_upload_submit_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.uploadToCloudAction, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatReadableSize(bytes: Long): String {
    if (bytes <= 0L) return "Ready"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = (bytes / 1024.0).coerceAtLeast(1.0)
        String.format(Locale.US, "%.0f KB", kb)
    }
}
