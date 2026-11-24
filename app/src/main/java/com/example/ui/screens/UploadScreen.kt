package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun UploadScreen(
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
    onVideoPublishedNavigate: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(VideoFormat.SHORT) }
    var category by remember { mutableStateOf(VideoCategory.ENTERTAINMENT) }
    var tagsInput by remember { mutableStateOf("") }

    // Start empty — never pre-fill sample videos as real uploaded videos
    var videoUri by remember { mutableStateOf("") }
    var selectedVideoName by remember { mutableStateOf("") }
    var thumbnailUri by remember { mutableStateOf("") }
    var customThumbnailPicked by remember { mutableStateOf(false) }
    var durationInput by remember { mutableStateOf("") }
    var pickedFileSizeBytes by remember { mutableLongStateOf(0L) }
    var isInspectingMedia by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            videoUri = uri.toString()
            onResetState()
            isInspectingMedia = true
            coroutineScope.launch {
                val meta = inspectPickedVideoMetadata(
                    context = context,
                    uri = uri,
                    extractThumbnail = !customThumbnailPicked
                )
                selectedVideoName = meta.displayName
                if (meta.durationSeconds > 0L) {
                    durationInput = meta.durationSeconds.toString()
                    // Automatically suggest Short vs Long format based on duration
                    format = if (meta.durationSeconds <= UploadLimits.MAX_SHORT_DURATION_SECONDS) {
                        VideoFormat.SHORT
                    } else {
                        VideoFormat.LONG
                    }
                }
                if (meta.fileSizeBytes > 0L) {
                    pickedFileSizeBytes = meta.fileSizeBytes
                }
                if (!customThumbnailPicked && !meta.previewThumbUri.isNullOrBlank()) {
                    thumbnailUri = meta.previewThumbUri
                }
                isInspectingMedia = false
            }
        }
    }

    val thumbnailPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            customThumbnailPicked = true
            thumbnailUri = uri.toString()
        }
    }

    // Real Media3 video preview player for the selected gallery video before uploading
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

    // Reset form smoothly after a successful publish if user uploads another video, keep Success banner visible
    LaunchedEffect(progressState) {
        if (progressState is UploadProgressState.Uploading) {
            previewPlayer?.pause()
        }
    }

    val isApproved = currentUser.isApprovedUploader || currentUser.role == UserRole.ADMIN
    val remainingQuota = (UploadLimits.MAX_DAILY_UPLOADS - dailyUploadCount).coerceAtLeast(0)
    val selectableCategories = remember { VideoCategory.entries.filter { it != VideoCategory.ALL } }
    val parsedDuration = durationInput.toLongOrNull() ?: 0L
    val exceedsFormatLimit = when (format) {
        VideoFormat.SHORT -> parsedDuration > UploadLimits.MAX_SHORT_DURATION_SECONDS
        VideoFormat.LONG -> parsedDuration > UploadLimits.MAX_LONG_DURATION_SECONDS
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("upload_screen")
        ) {
            Text(
                text = strings.uploadTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))
            MonetizationUnavailableBanner(showDetail = false)
            Spacer(modifier = Modifier.height(12.dp))

            // Quota & Approval card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            color = if (remainingQuota > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("daily_quota_text")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (dailyUploadCount.toFloat() / UploadLimits.MAX_DAILY_UPLOADS).coerceIn(0f, 1f) },
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
                                    modifier = Modifier.testTag("upload_not_approved_banner")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Video Preview (playable before uploading) + Empty Picker State
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_video_preview")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (format == VideoFormat.SHORT) 16f / 10f else 16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
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

                            if (previewError != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = previewError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
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
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .testTag("upload_preview_play_button")
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
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoFile,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Select a video from your Android gallery to preview",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    if (isInspectingMedia) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (videoUri.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val sizeMb = if (pickedFileSizeBytes > 0L) {
                            String.format("%.1f MB", pickedFileSizeBytes / (1024f * 1024f))
                        } else {
                            "Ready"
                        }
                        val durLabel = if (parsedDuration > 0L) formatDuration(parsedDuration) else "--:--"
                        Text(
                            text = "${selectedVideoName.ifBlank { "Selected video" }} · $durLabel · $sizeMb",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_video_button")
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.pickVideoFile, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                thumbnailPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_thumbnail_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.pickThumbnail, maxLines = 1)
                        }
                    }

                    if (thumbnailUri.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MediaImage(
                                uri = thumbnailUri,
                                contentDescription = "Selected thumbnail preview",
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                text = if (customThumbnailPicked) "Custom thumbnail selected" else "Auto-extracted video thumbnail",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Format selector
            Text(
                text = strings.videoFormatLabel,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = format == VideoFormat.SHORT,
                    onClick = {
                        format = VideoFormat.SHORT
                        onResetState()
                    },
                    label = { Text(strings.shortFormatOption) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("format_short_chip")
                )
                FilterChip(
                    selected = format == VideoFormat.LONG,
                    onClick = {
                        format = VideoFormat.LONG
                        onResetState()
                    },
                    label = { Text(strings.longFormatOption) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("format_long_chip")
                )
            }

            if (exceedsFormatLimit) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (format == VideoFormat.SHORT) {
                        "Selected video exceeds 3-minute (180s) Shorts limit. Switch to Long Video (max 30 min)."
                    } else {
                        "Selected video exceeds 30-minute (1800s) Long Video limit."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category selector
            Text(
                text = strings.videoCategoryLabel,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectableCategories, key = { it.name }) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(categoryLabel(cat, currentLanguage)) },
                        modifier = Modifier.testTag("upload_category_${cat.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    onResetState()
                },
                label = { Text(strings.videoTitleLabel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(strings.videoDescriptionLabel) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_description_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = tagsInput,
                onValueChange = { if (it.length <= 160) tagsInput = it },
                label = { Text(strings.videoTagsLabel) },
                placeholder = { Text(strings.videoTagsHint) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_tags_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = {
                        durationInput = it.filter { ch -> ch.isDigit() }
                        onResetState()
                    },
                    label = { Text(strings.durationSecondsLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("upload_duration_input")
                )
                OutlinedTextField(
                    value = videoUri,
                    onValueChange = {
                        videoUri = it
                        onResetState()
                    },
                    label = { Text(strings.orVideoUrlLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(2f)
                        .testTag("upload_video_uri_input")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (progressState) {
                is UploadProgressState.Uploading -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_progress_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = progressState.stageLabel,
                                    style = MaterialTheme.typography.labelLarge
                                )
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
                                    .testTag("cancel_upload_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.cancelUpload)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is UploadProgressState.Cancelled -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("upload_cancelled_banner")
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
                                modifier = Modifier.testTag("retry_upload_button")
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
                            .testTag("upload_error_banner")
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
                                modifier = Modifier.testTag("retry_upload_button")
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
                            .testTag("upload_success_banner")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Video published!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            OutlinedButton(onClick = { onVideoPublishedNavigate(progressState.videoId) }) {
                                Text("Watch")
                            }
                        }
                    }
                }
                UploadProgressState.Idle -> Unit
            }

            Button(
                onClick = {
                    previewPlayer?.pause()
                    val secs = durationInput.toLongOrNull() ?: 0L
                    val size = if (pickedFileSizeBytes > 0L) pickedFileSizeBytes else 1024L
                    onPublish(
                        title,
                        description,
                        format,
                        category,
                        secs,
                        size,
                        thumbnailUri,
                        videoUri,
                        parseTagsInput(tagsInput)
                    )
                },
                enabled = progressState !is UploadProgressState.Uploading && !isInspectingMedia,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("publish_video_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.publishVideo, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
