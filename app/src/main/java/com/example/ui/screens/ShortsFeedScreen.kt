package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.UploadLimits
import com.example.data.model.VideoComment
import com.example.data.model.VideoItem
import com.example.data.model.formatCompactCount
import com.example.data.model.formatDuration
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel
import com.example.media.ActivePlaybackCoordinator
import com.example.media.ExoPlayerFactory
import com.example.ui.components.AnimatedHeartIcon
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.MediaImage
import com.example.ui.components.MonetizationUnavailableBanner
import com.example.ui.components.shareVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val ShortsOverlayGradient = Brush.verticalGradient(
    listOf(
        Color.Black.copy(alpha = 0.42f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.88f)
    )
)

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsFeedScreen(
    currentUserId: String,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    shorts: List<VideoItem>,
    isLoading: Boolean = false,
    observeComments: (String) -> Flow<List<VideoComment>>,
    observeIsSubscribed: (creatorId: String) -> Flow<Boolean> = { flowOf(false) },
    onPlayShort: (String) -> Unit,
    onCreatorClick: (String) -> Unit,
    onSubscribeClick: (creatorId: String) -> Unit = {},
    onLikeClick: (String) -> Unit,
    onPostComment: (videoId: String, text: String, onDone: () -> Unit) -> Unit,
    onPostReply: ((videoId: String, text: String, parentCommentId: String, onDone: () -> Unit) -> Unit)? = null,
    onReportVideo: (videoId: String, reason: String) -> Unit,
    onBlockCreator: (creatorId: String) -> Unit,
    onGenuineViewReached: (videoId: String) -> Unit,
    onPlaybackError: (videoId: String, message: String) -> Unit,
    onOpenUpload: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isMuted by remember { mutableStateOf(false) }
    var activeCommentVideoId by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<VideoComment?>(null) }
    var reportTarget by remember { mutableStateOf<VideoItem?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var blockTargetCreator by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (shorts.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
                .testTag("shorts_empty_or_loading")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading vertical video feed from Firestore...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = strings.noVideosFound,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onOpenUpload) {
                        Text(strings.uploadNewVideo)
                    }
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { shorts.size })
    val settledIndex = pagerState.settledPage.coerceIn(0, shorts.lastIndex)
    val settledShort = shorts[settledIndex]

    // Reuse ONE pooled ExoPlayer across all Shorts swipes to prevent MediaCodec decoder thrashing
    val sharedPlayer = remember {
        ExoPlayerFactory.create(context, isShortLoop = true).apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isPausedByUser by remember(settledShort.id) { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasRenderedFirstFrame by remember(settledShort.id) { mutableStateOf(false) }
    var playbackProgress by remember(settledShort.id) { mutableFloatStateOf(0f) }
    var errorMessage by remember(settledShort.id) { mutableStateOf<String?>(null) }
    var loadedMediaKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var viewRecordedForId by remember(settledShort.id) { mutableStateOf(false) }

    val isSubscribedFlow = remember(settledShort.creatorId, currentUserId) {
        observeIsSubscribed(settledShort.creatorId)
    }
    val isSubscribedToCreator by isSubscribedFlow.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(isMuted) {
        sharedPlayer.volume = if (isMuted) 0f else 1f
    }

    // Switch media only when the settled Short's ID or URI actually changes (ignores like/comment count updates)
    LaunchedEffect(settledShort.id, settledShort.videoUri) {
        val targetKey = settledShort.id to settledShort.videoUri
        if (loadedMediaKey != targetKey) {
            loadedMediaKey = targetKey
            hasRenderedFirstFrame = false
            errorMessage = null
            if (settledShort.videoUri.isNotBlank()) {
                playbackState = Player.STATE_BUFFERING
                sharedPlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(settledShort.videoUri), true)
                sharedPlayer.prepare()
                sharedPlayer.playWhenReady = !isPausedByUser
            } else {
                playbackState = Player.STATE_IDLE
                sharedPlayer.stop()
                sharedPlayer.clearMediaItems()
                errorMessage = "Short video source is unavailable."
            }
        }
    }

    // Preload next & previous Short segments into disk cache on Dispatchers.IO without extra players
    LaunchedEffect(settledIndex, shorts) {
        val nextUri = shorts.getOrNull(settledIndex + 1)?.videoUri
        val prevUri = shorts.getOrNull(settledIndex - 1)?.videoUri
        if (!nextUri.isNullOrBlank()) {
            launch { ExoPlayerFactory.prefetchToCache(context, nextUri) }
        }
        if (!prevUri.isNullOrBlank()) {
            launch { ExoPlayerFactory.prefetchToCache(context, prevUri, maxBytes = 750_000L) }
        }
    }

    val currentShortId by rememberUpdatedState(settledShort.id)
    val currentPausedByUser by rememberUpdatedState(isPausedByUser)

    DisposableEffect(sharedPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    ActivePlaybackCoordinator.onPlaybackStarted(sharedPlayer)
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = error.message ?: "Short playback error (${error.errorCodeName})"
                errorMessage = msg
                onPlaybackError(currentShortId, msg)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> sharedPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (!currentPausedByUser) sharedPlayer.play()
                else -> Unit
            }
        }
        sharedPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sharedPlayer.removeListener(listener)
            ActivePlaybackCoordinator.onPlayerReleased(sharedPlayer)
            sharedPlayer.release()
        }
    }

    // Genuine 3-second view timer using local variable (only runs while real video frames are actively playing)
    LaunchedEffect(settledShort.id, isPlaying, hasRenderedFirstFrame, viewRecordedForId) {
        if (!isPlaying || !hasRenderedFirstFrame || viewRecordedForId) return@LaunchedEffect
        var activePlayMs = 0L
        while (isPlaying && hasRenderedFirstFrame && !viewRecordedForId) {
            delay(500L)
            activePlayMs += 500L
            if (activePlayMs >= UploadLimits.GENUINE_VIEW_MIN_PLAYBACK_MS) {
                viewRecordedForId = true
                onGenuineViewReached(settledShort.id)
                break
            }
        }
    }

    // Update subtle bottom progress bar smoothly while actively playing
    LaunchedEffect(settledShort.id, isPlaying) {
        while (isPlaying) {
            val dur = sharedPlayer.duration.coerceAtLeast(0L)
            val pos = sharedPlayer.currentPosition.coerceAtLeast(0L)
            playbackProgress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            delay(200L)
        }
    }

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        key = { index -> shorts[index].id },
        modifier = Modifier
            .fillMaxSize()
            .testTag("shorts_vertical_pager")
    ) { index ->
        val shortVideo = shorts[index]
        val isActivePage = settledIndex == index

        ShortSwipePage(
            shortVideo = shortVideo,
            isActivePage = isActivePage,
            sharedPlayer = if (isActivePage) sharedPlayer else null,
            isPlaying = if (isActivePage) isPlaying else false,
            isPausedByUser = if (isActivePage) isPausedByUser else false,
            isBuffering = isActivePage && playbackState == Player.STATE_BUFFERING && errorMessage == null,
            hasRenderedFirstFrame = if (isActivePage) hasRenderedFirstFrame else false,
            playbackProgress = if (isActivePage) playbackProgress else 0f,
            errorMessage = if (isActivePage) errorMessage else null,
            isMuted = isMuted,
            isSubscribed = if (isActivePage) isSubscribedToCreator else false,
            currentUserId = currentUserId,
            currentLanguage = currentLanguage,
            onTogglePlayPause = {
                if (isActivePage) {
                    if (sharedPlayer.isPlaying || (!isPausedByUser && sharedPlayer.playWhenReady)) {
                        isPausedByUser = true
                        sharedPlayer.playWhenReady = false
                        sharedPlayer.pause()
                    } else {
                        isPausedByUser = false
                        sharedPlayer.playWhenReady = true
                        sharedPlayer.play()
                    }
                }
            },
            onRetryPlayback = {
                if (isActivePage && shortVideo.videoUri.isNotBlank()) {
                    errorMessage = null
                    hasRenderedFirstFrame = false
                    isPausedByUser = false
                    playbackState = Player.STATE_BUFFERING
                    sharedPlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(shortVideo.videoUri), true)
                    sharedPlayer.prepare()
                    sharedPlayer.playWhenReady = true
                }
            },
            onToggleMute = { isMuted = !isMuted },
            onOpenFullPlayer = { onPlayShort(shortVideo.id) },
            onCreatorClick = { onCreatorClick(shortVideo.creatorId) },
            onSubscribeClick = { onSubscribeClick(shortVideo.creatorId) },
            onLikeClick = { onLikeClick(shortVideo.id) },
            onOpenComments = { activeCommentVideoId = shortVideo.id },
            onShareClick = { shareVideo(context, shortVideo) },
            onReportClick = {
                reportTarget = shortVideo
                reportReason = ""
            },
            onBlockCreatorClick = {
                blockTargetCreator = shortVideo.creatorId to shortVideo.creatorName
            }
        )
    }

    activeCommentVideoId?.let { vid ->
        val commentsFlow = remember(vid) { observeComments(vid) }
        val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        val topComments = remember(comments) { comments.filter { it.parentCommentId == null } }
        val repliesByParent = remember(comments) {
            comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId!! }
        }
        ModalBottomSheet(
            onDismissRequest = {
                activeCommentVideoId = null
                replyingToComment = null
            },
            modifier = Modifier.testTag("shorts_comments_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${strings.commentsLabel} (${comments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            activeCommentVideoId = null
                            replyingToComment = null
                        },
                        modifier = Modifier.testTag("shorts_comments_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.cancel
                        )
                    }
                }
                replyingToComment?.let { parent ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${strings.replyingTo} @${parent.username}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        TextButton(onClick = { replyingToComment = null }) {
                            Text(strings.cancel)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = { Text(strings.addCommentHint) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shorts_comment_input")
                    )
                    Button(
                        onClick = {
                            val parent = replyingToComment
                            if (parent != null && onPostReply != null) {
                                onPostReply(vid, commentDraft, parent.id) {
                                    commentDraft = ""
                                    replyingToComment = null
                                }
                            } else {
                                onPostComment(vid, commentDraft) {
                                    commentDraft = ""
                                    replyingToComment = null
                                }
                            }
                        },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("shorts_comment_send")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.postComment)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(topComments, key = { it.id }) { comment ->
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                CreatorAvatar(
                                    uri = comment.avatarUri,
                                    contentDescription = comment.username,
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(comment.username, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = comment.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (onPostReply != null) {
                                    TextButton(onClick = { replyingToComment = comment }) {
                                        Text(strings.reply)
                                    }
                                }
                            }
                            repliesByParent[comment.id].orEmpty().forEach { replyItem ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(start = 36.dp, top = 6.dp)
                                ) {
                                    CreatorAvatar(
                                        uri = replyItem.avatarUri,
                                        contentDescription = replyItem.username,
                                        size = 24.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(replyItem.username, style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = replyItem.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    reportTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { reportTarget = null },
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
                    onReportVideo(target.id, reportReason)
                    reportTarget = null
                }) {
                    Text(strings.reportSubmit)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    blockTargetCreator?.let { (creatorId, creatorName) ->
        AlertDialog(
            onDismissRequest = { blockTargetCreator = null },
            title = { Text(strings.blockUser) },
            text = { Text("Hide all Shorts and videos from $creatorName?") },
            confirmButton = {
                Button(
                    onClick = {
                        onBlockCreator(creatorId)
                        blockTargetCreator = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.blockUser)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTargetCreator = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ShortSwipePage(
    shortVideo: VideoItem,
    isActivePage: Boolean,
    sharedPlayer: ExoPlayer?,
    isPlaying: Boolean,
    isPausedByUser: Boolean,
    isBuffering: Boolean,
    hasRenderedFirstFrame: Boolean,
    playbackProgress: Float,
    errorMessage: String?,
    isMuted: Boolean,
    isSubscribed: Boolean,
    currentUserId: String,
    currentLanguage: AppLanguage,
    onTogglePlayPause: () -> Unit,
    onRetryPlayback: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onCreatorClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onLikeClick: () -> Unit,
    onOpenComments: () -> Unit,
    onShareClick: () -> Unit,
    onReportClick: () -> Unit,
    onBlockCreatorClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    var isDescriptionExpanded by remember(shortVideo.id) { mutableStateOf(false) }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("short_page_${shortVideo.id}")
    ) {
        val isWideViewport = maxHeight > 0.dp && (maxWidth / maxHeight) > (9f / 16f) + 0.08f
        val stageModifier = if (isWideViewport) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(9f / 16f)
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = stageModifier
                .background(Color.Black)
        ) {
            if (isActivePage && sharedPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            player = sharedPlayer
                        }
                    },
                    update = { pv ->
                        if (pv.player !== sharedPlayer) pv.player = sharedPlayer
                    },
                    onRelease = { pv ->
                        pv.player = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Keep crisp thumbnail visible until the first video frame renders (never flashes back during mid-play rebuffering)
            if (!isActivePage || !hasRenderedFirstFrame) {
                MediaImage(
                    uri = shortVideo.thumbnailUri,
                    contentDescription = shortVideo.title,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ShortsOverlayGradient)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlayPause
                    )
                    .testTag("shorts_video_stage_${shortVideo.id}")
            )

            MonetizationUnavailableBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(14.dp)
            )

            // Buffering spinner when stream is loading or rebuffering
            if (isBuffering) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("shorts_buffering_${shortVideo.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.bufferingVideo, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Subtle pause indicator when paused by user
            AnimatedVisibility(
                visible = isActivePage && isPausedByUser && errorMessage == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("shorts_center_pause_badge_${shortVideo.id}")
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume Short",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            // Playback error + Retry overlay
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .testTag("shorts_error_overlay_${shortVideo.id}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRetryPlayback,
                            modifier = Modifier.testTag("shorts_retry_button_${shortVideo.id}")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.retry)
                        }
                    }
                }
            }

            // Right-side action rail (Play/Pause, Sound, Like/Unlike, Comments, Share, Report, Block)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 24.dp)
            ) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("shorts_play_pause_button_${shortVideo.id}")
                ) {
                    Icon(
                        imageVector = if (isPlaying && !isPausedByUser) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying && !isPausedByUser) "Pause" else "Play",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("shorts_mute_button_${shortVideo.id}")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onLikeClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("shorts_like_button_${shortVideo.id}")
                    ) {
                        AnimatedHeartIcon(
                            isLiked = shortVideo.isLikedByCurrentUser,
                            animationKey = shortVideo.id,
                            iconSize = 24.dp,
                            likedTint = MaterialTheme.colorScheme.primary,
                            unlikedTint = Color.White,
                            contentDescription = strings.like,
                            modifier = Modifier.testTag("shorts_like_icon_${shortVideo.id}")
                        )
                    }
                    Text(
                        text = formatCompactCount(shortVideo.likeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.testTag("shorts_like_count_${shortVideo.id}")
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onOpenComments,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("shorts_comment_button_${shortVideo.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = strings.commentsLabel,
                            tint = Color.White
                        )
                    }
                    Text(
                        text = formatCompactCount(shortVideo.commentCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.testTag("shorts_comment_count_${shortVideo.id}")
                    )
                }

                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("shorts_share_button_${shortVideo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = strings.share,
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onReportClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("shorts_report_button_${shortVideo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = strings.report,
                        tint = Color.White
                    )
                }

                if (shortVideo.creatorId != currentUserId) {
                    IconButton(
                        onClick = onBlockCreatorClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("shorts_block_button_${shortVideo.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = strings.blockUser,
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom creator row (Avatar + Username + Subscribe), Title, Description, Actual Views & Full Player
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 84.dp, bottom = 20.dp)
            ) {
                // Creator profile badge overlay with avatar, name, and Follow button
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier.testTag("shorts_profile_badge_${shortVideo.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onCreatorClick)
                                .padding(end = 4.dp)
                                .testTag("shorts_creator_profile_${shortVideo.id}")
                        ) {
                            CreatorAvatar(
                                uri = shortVideo.creatorAvatarUri,
                                contentDescription = shortVideo.creatorName,
                                size = 34.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "@${shortVideo.creatorName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (shortVideo.creatorId != currentUserId) {
                            Button(
                                onClick = onSubscribeClick,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) {
                                        Color.White.copy(alpha = 0.24f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .heightIn(min = 38.dp)
                                    .testTag("shorts_subscribe_button_${shortVideo.id}")
                            ) {
                                Text(
                                    text = if (isSubscribed) strings.following else strings.follow,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = shortVideo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("shorts_title_${shortVideo.id}")
                )

                if (shortVideo.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = shortVideo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = if (isDescriptionExpanded) 6 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                            .testTag("shorts_description_${shortVideo.id}")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.testTag("shorts_views_${shortVideo.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${formatCompactCount(shortVideo.viewCount)} ${strings.viewsLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "${categoryLabel(shortVideo.category, currentLanguage)} · ${formatDuration(shortVideo.durationSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        onClick = onOpenFullPlayer,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("shorts_full_player_button_${shortVideo.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = strings.fullscreen,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = strings.fullscreen,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (isActivePage && playbackProgress > 0f) {
                LinearProgressIndicator(
                    progress = { playbackProgress.coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("shorts_progress_bar_${shortVideo.id}")
                )
            }
        }
    }
}
