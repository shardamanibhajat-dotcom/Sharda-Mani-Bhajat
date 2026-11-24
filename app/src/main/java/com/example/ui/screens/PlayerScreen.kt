package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.example.data.model.CreatorPlaylist
import com.example.data.model.UploadLimits
import com.example.data.model.VideoComment
import com.example.data.model.VideoItem
import com.example.data.model.formatCompactCount
import com.example.data.model.formatDuration
import com.example.data.model.formatUploadDate
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel
import com.example.media.ActivePlaybackCoordinator
import com.example.media.ExoPlayerFactory
import com.example.media.PlaybackTrackInfo
import com.example.media.VideoQualityOption
import com.example.ui.components.AnimatedHeartIcon
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.MediaImage
import com.example.ui.components.MonetizationUnavailableBanner
import com.example.ui.components.SaveToPlaylistDialog
import com.example.ui.components.VideoCommentsBottomSheet
import com.example.ui.components.shareVideo
import com.example.ui.viewmodel.PlayerUiState
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    currentUserId: String,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    uiState: PlayerUiState,
    targetCommentId: String? = null,
    ownPlaylists: List<CreatorPlaylist> = emptyList(),
    onAddToPlaylist: (playlistId: String) -> Unit = {},
    onCreatePlaylistAndAdd: (title: String) -> Unit = {},
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit = {},
    onCreatorClick: (String) -> Unit,
    onGenuinePlaybackReached: () -> Unit,
    onSaveWatchHistory: (Long) -> Unit = {},
    onPlaybackError: (String) -> Unit,
    onClearPlaybackError: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onBlockCreator: () -> Unit,
    onPostComment: (String, String?, () -> Unit) -> Unit,
    onReportVideo: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val video = uiState.video

    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember(video?.id) { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPausedByUser by remember(video?.id) { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasRenderedFirstFrame by remember(video?.id) { mutableStateOf(false) }
    var currentPositionMs by remember(video?.id) { mutableLongStateOf(0L) }
    var totalDurationMs by remember(video?.id) { mutableLongStateOf(0L) }
    var restoredSavedPosition by remember(video?.id) { mutableStateOf(false) }
    var playbackSpeed by remember(video?.id) { mutableFloatStateOf(1.0f) }
    var volumeLevel by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var selectedQuality by remember(video?.id) { mutableStateOf(VideoQualityOption.AUTO) }
    var trackInfo by remember(video?.id) { mutableStateOf(PlaybackTrackInfo()) }
    var commentText by remember { mutableStateOf("") }
    var replyingToComment by remember(video?.id) { mutableStateOf<VideoComment?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCommentsSheet by remember(video?.id) { mutableStateOf(false) }
    val latestComments by rememberUpdatedState(uiState.comments)
    val commentsSheetFlow = remember(video?.id) { snapshotFlow { latestComments } }

    val exoPlayer = remember(video?.id, video?.videoUri) {
        ExoPlayerFactory.create(context, isShortLoop = false).apply {
            if (video != null && video.videoUri.isNotBlank()) {
                setMediaItem(ExoPlayerFactory.buildMediaItem(video.videoUri))
                prepare()
                playWhenReady = true
            }
        }
    }

    LaunchedEffect(selectedQuality, exoPlayer) {
        ExoPlayerFactory.applyQuality(exoPlayer, selectedQuality)
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(volumeLevel, isMuted, exoPlayer) {
        exoPlayer.volume = if (isMuted) 0f else volumeLevel.coerceIn(0f, 1f)
    }

    val currentPausedByUser by rememberUpdatedState(isPausedByUser)
    val currentPlaybackError by rememberUpdatedState(uiState.playbackError)
    val latestSaveWatchHistory by rememberUpdatedState(onSaveWatchHistory)

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (exoPlayer.duration > 0L) {
                    totalDurationMs = exoPlayer.duration
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    ActivePlaybackCoordinator.onPlaybackStarted(exoPlayer)
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                trackInfo = ExoPlayerFactory.inspectTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError(error.message ?: "Playback error (${error.errorCodeName})")
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    latestSaveWatchHistory(exoPlayer.currentPosition.coerceAtLeast(0L))
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> if (!currentPausedByUser && currentPlaybackError == null) {
                    exoPlayer.play()
                }
                else -> Unit
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestSaveWatchHistory(exoPlayer.currentPosition.coerceAtLeast(0L))
            exoPlayer.pause()
            exoPlayer.removeListener(listener)
            ActivePlaybackCoordinator.onPlayerReleased(exoPlayer)
            exoPlayer.release()
        }
    }

    // Restore last saved watch position once ready
    LaunchedEffect(video?.id, playbackState, uiState.savedPositionMs) {
        if (video == null || restoredSavedPosition) return@LaunchedEffect
        if (playbackState == Player.STATE_READY && uiState.savedPositionMs > 1_500L) {
            val dur = exoPlayer.duration
            if (dur <= 0L || uiState.savedPositionMs < dur - 3_000L) {
                exoPlayer.seekTo(uiState.savedPositionMs)
                currentPositionMs = uiState.savedPositionMs
            }
            restoredSavedPosition = true
        }
    }

    // Poll current playback position & periodically persist watch history
    LaunchedEffect(exoPlayer, video?.id, isPlaying) {
        if (video == null) return@LaunchedEffect
        var ticksSinceSave = 0
        if (isPlaying) {
            latestSaveWatchHistory(exoPlayer.currentPosition.coerceAtLeast(0L))
        }
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            currentPositionMs = pos
            if (dur > 0L) {
                totalDurationMs = dur
            }
            if (isPlaying) {
                ticksSinceSave++
                if (ticksSinceSave >= 20) {
                    ticksSinceSave = 0
                    latestSaveWatchHistory(pos)
                }
            }
            delay(250L)
        }
    }

    // Genuine view tracking: only count after >= 3 seconds of actual active playback
    LaunchedEffect(video?.id, isPlaying, uiState.isViewRecordedThisSession) {
        if (video == null || !isPlaying || uiState.isViewRecordedThisSession) return@LaunchedEffect
        var activePlaybackMillis = 0L
        while (isPlaying && !uiState.isViewRecordedThisSession) {
            delay(500L)
            activePlaybackMillis += 500L
            if (activePlaybackMillis >= UploadLimits.GENUINE_VIEW_MIN_PLAYBACK_MS) {
                onGenuinePlaybackReached()
                break
            }
        }
    }

    val isBuffering = (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) &&
        video?.videoUri?.isNotBlank() == true &&
        uiState.playbackError == null

    val onTogglePlayPause = {
        if (exoPlayer.isPlaying) {
            isPausedByUser = true
            latestSaveWatchHistory(exoPlayer.currentPosition.coerceAtLeast(0L))
            exoPlayer.pause()
        } else {
            isPausedByUser = false
            if (playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
                currentPositionMs = 0L
            }
            exoPlayer.play()
        }
    }

    val onSeekTo: (Long) -> Unit = { targetMs ->
        val maxDur = if (totalDurationMs > 0L) {
            totalDurationMs
        } else {
            ((video?.durationSeconds ?: 0L).coerceAtLeast(1L)) * 1000L
        }
        val clamped = targetMs.coerceIn(0L, maxDur)
        exoPlayer.seekTo(clamped)
        currentPositionMs = clamped
        latestSaveWatchHistory(clamped)
    }

    val onSeekBy: (Long) -> Unit = { deltaMs ->
        val basePos = exoPlayer.currentPosition.coerceAtLeast(0L)
        onSeekTo(basePos + deltaMs)
    }

    val onRetryPlayback = {
        onClearPlaybackError()
        if (video != null && video.videoUri.isNotBlank()) {
            exoPlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(video.videoUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Group comments into top-level threads and chronological replies
    val topLevelComments = remember(uiState.comments) {
        val commentIds = uiState.comments.map { it.id }.toHashSet()
        uiState.comments.filter { it.parentCommentId == null || !commentIds.contains(it.parentCommentId) }
    }
    val repliesByParent = remember(uiState.comments) {
        uiState.comments
            .filter { it.parentCommentId != null }
            .groupBy { it.parentCommentId!! }
            .mapValues { (_, list) -> list.sortedBy { it.createdAt } }
    }

    val listState = rememberLazyListState()
    var hasScrolledToTargetComment by remember(video?.id, targetCommentId) { mutableStateOf(false) }

    LaunchedEffect(targetCommentId, topLevelComments.size, repliesByParent.size) {
        val target = targetCommentId ?: return@LaunchedEffect
        if (hasScrolledToTargetComment || topLevelComments.isEmpty()) return@LaunchedEffect
        val matchedIndex = topLevelComments.indexOfFirst { top ->
            top.id == target || repliesByParent[top.id].orEmpty().any { it.id == target }
        }
        if (matchedIndex >= 0) {
            hasScrolledToTargetComment = true
            listState.animateScrollToItem(index = 3 + matchedIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(video?.title ?: "Player", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (video == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text("Video unavailable or removed.")
            }
            return@Scaffold
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .testTag("player_screen")
            ) {
                item(key = "player_viewport", contentType = "player") {
                    PlayerViewportBox(
                        video = video,
                        exoPlayer = exoPlayer,
                        isFullscreen = isFullscreen,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        hasRenderedFirstFrame = hasRenderedFirstFrame,
                        playbackError = uiState.playbackError,
                        currentPositionMs = currentPositionMs,
                        totalDurationMs = totalDurationMs,
                        playbackSpeed = playbackSpeed,
                        volumeLevel = volumeLevel,
                        isMuted = isMuted,
                        selectedQuality = selectedQuality,
                        trackInfo = trackInfo,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekTo = onSeekTo,
                        onSeekBy = onSeekBy,
                        onChangeSpeed = { speed ->
                            playbackSpeed = speed
                            exoPlayer.setPlaybackSpeed(speed)
                        },
                        onChangeVolume = { vol ->
                            volumeLevel = vol.coerceIn(0f, 1f)
                            if (vol > 0.01f && isMuted) {
                                isMuted = false
                            } else if (vol <= 0.01f) {
                                isMuted = true
                            }
                        },
                        onToggleMute = { isMuted = !isMuted },
                        onSelectQuality = { option ->
                            selectedQuality = option
                            ExoPlayerFactory.applyQuality(exoPlayer, option)
                        },
                        onEnterFullscreen = { isFullscreen = true },
                        onRetryPlayback = onRetryPlayback,
                        isFollowingCreator = uiState.isSubscribed,
                        showFollowButton = video.creatorId != currentUserId,
                        onCreatorClick = { onCreatorClick(video.creatorId) },
                        onFollowClick = onToggleSubscribe,
                        onToggleLike = onToggleLike,
                        onOpenComments = { showCommentsSheet = true }
                    )
                }

                item(key = "player_details", contentType = "details") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${video.format.name} · ${formatDuration(video.durationSeconds)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = categoryLabel(video.category, currentLanguage),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (trackInfo.isAdaptiveStream || video.videoUri.contains(".m3u8", ignoreCase = true)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = strings.adaptiveStreamBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${formatCompactCount(video.viewCount)} ${strings.viewsLabel} · ${
                                formatUploadDate(video.createdAt, currentLanguage == AppLanguage.HINDI)
                            }",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("player_view_count")
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (uiState.isViewRecordedThisSession) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isViewRecordedThisSession) {
                                        strings.genuineViewCounted
                                    } else {
                                        strings.genuineViewPending
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (video.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = video.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        MonetizationUnavailableBanner()
                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Button(
                                onClick = onToggleLike,
                                colors = if (video.isLikedByCurrentUser) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier.testTag("player_like_button")
                            ) {
                                AnimatedHeartIcon(
                                    isLiked = video.isLikedByCurrentUser,
                                    animationKey = video.id,
                                    iconSize = 18.dp,
                                    likedTint = MaterialTheme.colorScheme.onPrimary,
                                    unlikedTint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatCompactCount(video.likeCount),
                                    modifier = Modifier.testTag("player_like_count")
                                )
                            }

                            OutlinedButton(
                                onClick = { showCommentsSheet = true },
                                modifier = Modifier.testTag("player_comment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = strings.commentsLabel,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatCompactCount(
                                        video.commentCount.coerceAtLeast(uiState.comments.size.toLong())
                                    ),
                                    modifier = Modifier.testTag("player_comment_count")
                                )
                            }

                            OutlinedButton(
                                onClick = { shareVideo(context, video) },
                                modifier = Modifier.testTag("player_share_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.share)
                            }

                            OutlinedButton(
                                onClick = { showPlaylistPicker = true },
                                modifier = Modifier.testTag("player_save_playlist_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = strings.saveToPlaylist, modifier = Modifier.size(18.dp))
                            }

                            OutlinedButton(
                                onClick = {
                                    reportReason = ""
                                    showReportDialog = true
                                },
                                modifier = Modifier.testTag("player_report_button")
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = strings.report, modifier = Modifier.size(18.dp))
                            }

                            if (video.creatorId != currentUserId) {
                                OutlinedButton(
                                    onClick = { showBlockConfirm = true },
                                    modifier = Modifier.testTag("player_block_creator_button")
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = strings.blockUser, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Creator Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onCreatorClick(video.creatorId) }
                                        .testTag("player_creator_link")
                                ) {
                                    CreatorAvatar(
                                        uri = video.creatorAvatarUri,
                                        contentDescription = video.creatorName,
                                        size = 44.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = video.creatorName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val creatorObj = uiState.creator
                                        val subText = if (creatorObj?.hideSubscriberCount == true && creatorObj.id != currentUserId) {
                                            strings.subscribersHidden
                                        } else {
                                            "${formatCompactCount(creatorObj?.subscriberCount ?: 0L)} ${strings.subscribersLabel}"
                                        }
                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (video.creatorId != currentUserId) {
                                    Button(
                                        onClick = onToggleSubscribe,
                                        colors = if (uiState.isSubscribed) {
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            ButtonDefaults.buttonColors()
                                        },
                                        modifier = Modifier.testTag("player_subscribe_button")
                                    ) {
                                        Text(if (uiState.isSubscribed) strings.subscribed else strings.subscribe)
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "comment_composer", contentType = "composer") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "${strings.commentsLabel} (${uiState.comments.size.coerceAtLeast(video.commentCount.toInt())})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("player_comments_header_count")
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        replyingToComment?.let { replyTarget ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .testTag("replying_to_banner")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${strings.replyingTo} @${replyTarget.username}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { replyingToComment = null },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("cancel_reply_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.cancel,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val commentTrimLen = commentText.trim().length
                        val commentOverLimit = commentTrimLen > 500
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                placeholder = {
                                    Text(
                                        if (replyingToComment != null) {
                                            "${strings.replyingTo} @${replyingToComment!!.username}..."
                                        } else {
                                            strings.addCommentHint
                                        }
                                    )
                                },
                                isError = commentOverLimit,
                                supportingText = if (commentText.isNotEmpty()) {
                                    {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (commentOverLimit) strings.commentTooLongError else "",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text("$commentTrimLen / 500")
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("comment_input")
                            )
                            Button(
                                enabled = !commentOverLimit,
                                onClick = {
                                    val parentId = replyingToComment?.id
                                    onPostComment(commentText, parentId) {
                                        commentText = ""
                                        replyingToComment = null
                                    }
                                },
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("post_comment_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.postComment)
                            }
                        }
                    }
                }

                items(topLevelComments, key = { it.id }, contentType = { "comment" }) { comment ->
                    val replies = repliesByParent[comment.id].orEmpty()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        CommentItemRow(
                            comment = comment,
                            currentLanguage = currentLanguage,
                            replyLabel = strings.reply,
                            onReplyClick = { replyingToComment = comment },
                            isReply = false,
                            isHighlighted = targetCommentId == comment.id
                        )
                        replies.forEach { reply ->
                            CommentItemRow(
                                comment = reply,
                                currentLanguage = currentLanguage,
                                replyLabel = strings.reply,
                                onReplyClick = { replyingToComment = comment },
                                isReply = true,
                                isHighlighted = targetCommentId == reply.id
                            )
                        }
                    }
                }

                if (uiState.relatedVideos.isNotEmpty()) {
                    item(key = "related_videos_header", contentType = "section_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = strings.relatedVideosTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("related_videos_section_header")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    items(uiState.relatedVideos, key = { "rel_${it.id}" }, contentType = { "related_video" }) { related ->
                        RelatedVideoRow(
                            video = related,
                            currentLanguage = currentLanguage,
                            onClick = { onVideoClick(related.id) }
                        )
                    }
                }
            }
        }
    }

    if (isFullscreen && video != null) {
        FullscreenPlayerDialog(
            video = video,
            exoPlayer = exoPlayer,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            playbackError = uiState.playbackError,
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            playbackSpeed = playbackSpeed,
            volumeLevel = volumeLevel,
            isMuted = isMuted,
            selectedQuality = selectedQuality,
            trackInfo = trackInfo,
            showControls = showControls,
            onToggleControls = { showControls = !showControls },
            onTogglePlayPause = onTogglePlayPause,
            onSeekTo = onSeekTo,
            onSeekBy = onSeekBy,
            onChangeSpeed = { speed ->
                playbackSpeed = speed
                exoPlayer.setPlaybackSpeed(speed)
            },
            onChangeVolume = { vol ->
                volumeLevel = vol.coerceIn(0f, 1f)
                if (vol > 0.01f && isMuted) {
                    isMuted = false
                } else if (vol <= 0.01f) {
                    isMuted = true
                }
            },
            onToggleMute = { isMuted = !isMuted },
            onSelectQuality = { option ->
                selectedQuality = option
                ExoPlayerFactory.applyQuality(exoPlayer, option)
            },
            onExitFullscreen = { isFullscreen = false },
            onRetryPlayback = onRetryPlayback,
            isFollowingCreator = uiState.isSubscribed,
            showFollowButton = video.creatorId != currentUserId,
            onCreatorClick = {
                isFullscreen = false
                onCreatorClick(video.creatorId)
            },
            onFollowClick = onToggleSubscribe,
            onToggleLike = onToggleLike,
            onOpenComments = {
                isFullscreen = false
                showCommentsSheet = true
            }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(strings.reportDialogTitle) },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onReportVideo(reportReason)
                    showReportDialog = false
                }) {
                    Text(strings.reportSubmit)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showBlockConfirm && video != null) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text(strings.blockUser) },
            text = { Text("Hide all videos and comments from ${video.creatorName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        onBlockCreator()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.blockUser)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showPlaylistPicker && video != null) {
        SaveToPlaylistDialog(
            playlists = ownPlaylists,
            onDismiss = { showPlaylistPicker = false },
            onSelectPlaylist = { pid ->
                onAddToPlaylist(pid)
                showPlaylistPicker = false
            },
            onCreateAndAdd = { title ->
                onCreatePlaylistAndAdd(title)
                showPlaylistPicker = false
            }
        )
    }

    if (showCommentsSheet && video != null) {
        VideoCommentsBottomSheet(
            video = video,
            currentLanguage = currentLanguage,
            observeComments = { commentsSheetFlow },
            onPostComment = { _, text, parentCommentId, onSuccess ->
                onPostComment(text, parentCommentId, onSuccess)
            },
            onCreatorClick = { cid ->
                showCommentsSheet = false
                onCreatorClick(cid)
            },
            onDismiss = { showCommentsSheet = false }
        )
    }
}

@Composable
private fun CommentItemRow(
    comment: VideoComment,
    currentLanguage: AppLanguage,
    replyLabel: String,
    onReplyClick: () -> Unit,
    isReply: Boolean,
    isHighlighted: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("highlighted_comment_${comment.id}")
                } else {
                    Modifier
                }
            )
            .padding(
                start = if (isReply) 38.dp else 0.dp,
                top = if (isReply) 6.dp else 2.dp,
                bottom = 2.dp
            )
            .testTag("comment_item_${comment.id}")
    ) {
        CreatorAvatar(
            uri = comment.avatarUri,
            contentDescription = comment.username,
            size = if (isReply) 28.dp else 34.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.username,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatUploadDate(comment.createdAt, currentLanguage == AppLanguage.HINDI),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onReplyClick,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("reply_button_${comment.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = replyLabel,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(replyLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RelatedVideoRow(
    video: VideoItem,
    currentLanguage: AppLanguage,
    onClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("related_video_card_${video.id}")
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            MediaImage(
                uri = video.thumbnailUri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
            ) {
                Text(
                    text = formatDuration(video.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.creatorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatCompactCount(video.viewCount)} ${strings.viewsLabel} · ${
                    formatUploadDate(video.createdAt, currentLanguage == AppLanguage.HINDI)
                }",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
