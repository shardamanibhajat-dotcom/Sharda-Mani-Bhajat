package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.FeedSortOrder
import com.example.data.model.UploadLimits
import com.example.data.model.VideoComment
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.formatCompactCount
import com.example.data.model.formatDuration
import com.example.data.model.formatUploadDate
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel
import com.example.localization.sortLabel
import com.example.media.ActivePlaybackCoordinator
import com.example.media.ExoPlayerFactory
import com.example.ui.components.AnimatedHeartIcon
import com.example.ui.components.AnimatedOverlayCommentButton
import com.example.ui.components.AnimatedOverlayLikeButton
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.CreatorProfileOverlayBadge
import com.example.ui.components.MediaImage
import com.example.ui.components.VideoCommentsBottomSheet
import com.example.ui.components.shareVideo
import com.example.ui.viewmodel.VideoFeedUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs

private val FeedVideoGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.45f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.72f)
    )
)

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedScreen(
    currentUserId: String,
    uiState: VideoFeedUiState,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    followedCreatorIds: Set<String> = emptySet(),
    onBack: () -> Unit,
    onFormatFilterChange: (VideoFormat?) -> Unit,
    onTagFilterChange: (String?) -> Unit,
    onSortChange: (FeedSortOrder) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onVideoClick: (String) -> Unit,
    onCreatorClick: (String) -> Unit,
    onLikeClick: (String) -> Unit,
    onFollowClick: (String) -> Unit,
    onOpenUpload: () -> Unit = {},
    onGenuineViewReached: (String) -> Unit = {},
    onPlaybackError: (videoId: String, message: String) -> Unit = { _, _ -> },
    observeComments: (String) -> Flow<List<VideoComment>> = { flowOf(emptyList()) },
    onPostComment: (videoId: String, text: String, parentCommentId: String?, onSuccess: () -> Unit) -> Unit = { _, _, _, _ -> }
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var autoPlayEnabled by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isUserPaused by remember { mutableStateOf(false) }
    var pinnedVideoId by remember { mutableStateOf<String?>(null) }
    var activeCommentVideoId by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val videos = uiState.videos

    // Release manual pin automatically if user scrolls that video off-screen
    val isPinnedVisible by remember(pinnedVideoId, listState) {
        derivedStateOf {
            val id = pinnedVideoId ?: return@derivedStateOf false
            listState.layoutInfo.visibleItemsInfo.any { it.key == id }
        }
    }
    LaunchedEffect(isPinnedVisible, pinnedVideoId) {
        if (pinnedVideoId != null && !isPinnedVisible) {
            pinnedVideoId = null
        }
    }

    // Resolve centered visible video item when autoplay is active
    val centeredVisibleVideoId by remember(videos, listState) {
        derivedStateOf {
            if (videos.isEmpty()) {
                null
            } else {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val validIds = videos.map { it.id }.toSet()
                layoutInfo.visibleItemsInfo
                    .filter { info -> info.key is String && validIds.contains(info.key as String) }
                    .minByOrNull { info ->
                        val itemCenter = info.offset + info.size / 2
                        abs(itemCenter - viewportCenter)
                    }
                    ?.key as? String ?: videos.firstOrNull()?.id
            }
        }
    }

    val activeVideoId = remember(pinnedVideoId, centeredVisibleVideoId, autoPlayEnabled, videos) {
        when {
            pinnedVideoId != null && videos.any { it.id == pinnedVideoId } -> pinnedVideoId
            autoPlayEnabled -> centeredVisibleVideoId
            else -> null
        }
    }
    val activeVideo = remember(activeVideoId, videos) {
        videos.firstOrNull { it.id == activeVideoId }
    }

    // Pooled ExoPlayer instance shared smoothly across feed cards
    val feedPlayer = remember {
        ExoPlayerFactory.create(context, isShortLoop = false).apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasRenderedFirstFrame by remember(activeVideo?.id) { mutableStateOf(false) }
    var positionMs by remember(activeVideo?.id) { mutableLongStateOf(0L) }
    var durationMs by remember(activeVideo?.id) { mutableLongStateOf(0L) }
    var activePlaybackError by remember(activeVideo?.id) { mutableStateOf<String?>(null) }
    var loadedMediaKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var viewCountedForActive by remember(activeVideo?.id) { mutableStateOf(false) }
    var isLifecycleResumed by remember { mutableStateOf(true) }

    val currentVideos by rememberUpdatedState(videos)
    val currentActiveVideo by rememberUpdatedState(activeVideo)
    val currentAutoPlay by rememberUpdatedState(autoPlayEnabled)
    val currentUserPaused by rememberUpdatedState(isUserPaused)

    LaunchedEffect(isMuted) {
        feedPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(activeVideo?.id, activeVideo?.videoUri, isUserPaused, isLifecycleResumed) {
        val target = activeVideo
        if (target == null || target.videoUri.isBlank() || !isLifecycleResumed) {
            if (feedPlayer.isPlaying || feedPlayer.playWhenReady) {
                feedPlayer.playWhenReady = false
                feedPlayer.pause()
            }
            return@LaunchedEffect
        }

        val keyPair = target.id to target.videoUri
        if (loadedMediaKey != keyPair) {
            loadedMediaKey = keyPair
            hasRenderedFirstFrame = false
            activePlaybackError = null
            positionMs = 0L
            durationMs = (target.durationSeconds * 1000L).coerceAtLeast(0L)
            playbackState = Player.STATE_BUFFERING
            feedPlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(target.videoUri), true)
            feedPlayer.prepare()
        }

        if (isUserPaused) {
            feedPlayer.playWhenReady = false
            feedPlayer.pause()
        } else {
            feedPlayer.playWhenReady = true
            feedPlayer.play()
        }
    }

    // Disk-cache prefetch upcoming & previous videos while user scrolls
    LaunchedEffect(activeVideo?.id, videos) {
        val idx = videos.indexOfFirst { it.id == activeVideo?.id }
        if (idx >= 0) {
            videos.getOrNull(idx + 1)?.videoUri?.takeIf { it.isNotBlank() }?.let { nextUri ->
                launch { ExoPlayerFactory.prefetchToCache(context, nextUri) }
            }
            videos.getOrNull(idx - 1)?.videoUri?.takeIf { it.isNotBlank() }?.let { prevUri ->
                launch { ExoPlayerFactory.prefetchToCache(context, prevUri, maxBytes = 750_000L) }
            }
        }
    }

    // Genuine 3-second playback view counter
    LaunchedEffect(activeVideo?.id, isPlaying, hasRenderedFirstFrame, viewCountedForActive) {
        val video = activeVideo ?: return@LaunchedEffect
        if (!isPlaying || !hasRenderedFirstFrame || viewCountedForActive) return@LaunchedEffect
        var elapsed = 0L
        while (isPlaying && hasRenderedFirstFrame && !viewCountedForActive) {
            delay(500L)
            elapsed += 500L
            if (elapsed >= UploadLimits.GENUINE_VIEW_MIN_PLAYBACK_MS) {
                viewCountedForActive = true
                onGenuineViewReached(video.id)
                break
            }
        }
    }

    // Track position & duration smoothly while playing
    LaunchedEffect(activeVideo?.id, isPlaying, playbackState) {
        while (isPlaying || playbackState == Player.STATE_READY) {
            val dur = feedPlayer.duration.takeIf { it > 0L }
                ?: ((activeVideo?.durationSeconds ?: 0L) * 1000L)
            durationMs = dur.coerceAtLeast(0L)
            positionMs = feedPlayer.currentPosition.coerceAtLeast(0L)
            if (!isPlaying) break
            delay(250L)
        }
    }

    DisposableEffect(feedPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_ENDED && currentAutoPlay && !currentUserPaused) {
                    val list = currentVideos
                    val currentId = currentActiveVideo?.id
                    val currentIndex = list.indexOfFirst { it.id == currentId }
                    if (currentIndex >= 0 && currentIndex + 1 < list.size) {
                        val nextVideo = list[currentIndex + 1]
                        pinnedVideoId = nextVideo.id
                        isUserPaused = false
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentIndex + 1)
                        }
                    } else if (currentIndex >= 0) {
                        feedPlayer.seekTo(0L)
                        feedPlayer.playWhenReady = true
                        feedPlayer.play()
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    ActivePlaybackCoordinator.onPlaybackStarted(feedPlayer)
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                val vid = currentActiveVideo?.id ?: return
                val msg = error.message ?: "Unable to stream video (${error.errorCodeName})"
                activePlaybackError = msg
                onPlaybackError(vid, msg)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isLifecycleResumed = false
                    feedPlayer.playWhenReady = false
                    feedPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                }
                else -> Unit
            }
        }

        feedPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            feedPlayer.removeListener(listener)
            ActivePlaybackCoordinator.onPlayerReleased(feedPlayer)
            feedPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("video_feed_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = strings.videoFeedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${strings.videoFeedSubtitle} (${videos.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    FilterChip(
                        selected = autoPlayEnabled,
                        onClick = {
                            autoPlayEnabled = !autoPlayEnabled
                            if (!autoPlayEnabled) {
                                isUserPaused = true
                                feedPlayer.playWhenReady = false
                                feedPlayer.pause()
                            } else {
                                isUserPaused = false
                            }
                        },
                        label = {
                            Text(
                                text = strings.autoPlayLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("video_feed_autoplay_toggle")
                    )
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.testTag("video_feed_mute_toggle")
                    ) {
                        Icon(
                            imageVector = if (isMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isMuted) "Unmute feed" else "Mute feed"
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("video_feed_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.refreshFeed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("video_feed_screen")
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Format, Sort, and Tag filter strip
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.selectedFormat == null,
                                onClick = { onFormatFilterChange(null) },
                                label = { Text(strings.filterAll) },
                                modifier = Modifier.testTag("video_feed_filter_all")
                            )
                            FilterChip(
                                selected = uiState.selectedFormat == VideoFormat.LONG,
                                onClick = { onFormatFilterChange(VideoFormat.LONG) },
                                label = { Text(strings.filterLongVideos) },
                                modifier = Modifier.testTag("video_feed_filter_long")
                            )
                            FilterChip(
                                selected = uiState.selectedFormat == VideoFormat.SHORT,
                                onClick = { onFormatFilterChange(VideoFormat.SHORT) },
                                label = { Text(strings.filterShorts) },
                                modifier = Modifier.testTag("video_feed_filter_shorts")
                            )
                        }

                        Box {
                            Surface(
                                onClick = { showSortMenu = true },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_feed_sort_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort feed",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = sortLabel(uiState.sortOrder, strings),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FeedSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(sortLabel(order, strings)) },
                                        trailingIcon = {
                                            if (uiState.sortOrder == order) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            onSortChange(order)
                                            showSortMenu = false
                                        },
                                        modifier = Modifier.testTag("video_feed_sort_${order.name}")
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.availableTags.isNotEmpty() || uiState.selectedTag != null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                                .testTag("video_feed_tag_bar")
                        ) {
                            item(key = "vf_tag_all") {
                                FilterChip(
                                    selected = uiState.selectedTag == null,
                                    onClick = { onTagFilterChange(null) },
                                    label = { Text(strings.allTagsFilter) },
                                    modifier = Modifier.testTag("video_feed_tag_all")
                                )
                            }
                            items(uiState.availableTags, key = { "vf_tag_$it" }) { tag ->
                                val selected = uiState.selectedTag == tag
                                FilterChip(
                                    selected = selected,
                                    onClick = { onTagFilterChange(if (selected) null else tag) },
                                    label = { Text("#$tag") },
                                    modifier = Modifier.testTag("video_feed_tag_$tag")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    uiState.isLoading && videos.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .testTag("video_feed_loading")
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Fetching videos from Firestore...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    uiState.errorMessage != null && videos.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .testTag("video_feed_error_state")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("video_feed_error_retry_button")
                            ) {
                                Text(strings.retry)
                            }
                        }
                    }

                    videos.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .testTag("video_feed_empty_state")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = strings.noVideosFound,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.selectedFormat != null || uiState.selectedTag != null) {
                                OutlinedButton(
                                    onClick = onClearFilters,
                                    modifier = Modifier.testTag("video_feed_clear_filters_button")
                                ) {
                                    Text(strings.clearFiltersLabel)
                                }
                            } else {
                                Button(onClick = onOpenUpload) {
                                    Text(strings.uploadNewVideo)
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 680.dp)
                                .testTag("video_feed_list")
                        ) {
                            items(
                                items = videos,
                                key = { it.id },
                                contentType = { "video_feed_card" }
                            ) { video ->
                                val isCardActive = activeVideo?.id == video.id
                                val isCardPlaying = isCardActive && isPlaying && !isUserPaused
                                val cardDurationMs = if (isCardActive && durationMs > 0L) {
                                    durationMs
                                } else {
                                    (video.durationSeconds * 1000L).coerceAtLeast(0L)
                                }
                                val cardProgress = if (isCardActive && cardDurationMs > 0L) {
                                    (positionMs.toFloat() / cardDurationMs.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }

                                VideoFeedCard(
                                    video = video,
                                    currentLanguage = currentLanguage,
                                    isActive = isCardActive,
                                    isPlaying = isCardPlaying,
                                    isBuffering = isCardActive &&
                                        playbackState == Player.STATE_BUFFERING &&
                                        activePlaybackError == null,
                                    hasRenderedFirstFrame = isCardActive && hasRenderedFirstFrame,
                                    playbackError = if (isCardActive) activePlaybackError else null,
                                    isMuted = isMuted,
                                    progress = cardProgress,
                                    positionMs = if (isCardActive) positionMs else 0L,
                                    durationMs = cardDurationMs,
                                    sharedPlayer = if (isCardActive) feedPlayer else null,
                                    isFollowingCreator = followedCreatorIds.contains(video.creatorId),
                                    showFollowButton = video.creatorId != currentUserId,
                                    onTogglePlay = {
                                        if (isCardActive) {
                                            if (isPlaying && !isUserPaused) {
                                                isUserPaused = true
                                                feedPlayer.playWhenReady = false
                                                feedPlayer.pause()
                                            } else {
                                                isUserPaused = false
                                                if (playbackState == Player.STATE_ENDED) {
                                                    feedPlayer.seekTo(0L)
                                                }
                                                feedPlayer.playWhenReady = true
                                                feedPlayer.play()
                                            }
                                        } else {
                                            pinnedVideoId = video.id
                                            isUserPaused = false
                                        }
                                    },
                                    onSeekToFraction = { fraction ->
                                        if (isCardActive && cardDurationMs > 0L) {
                                            val targetMs = (fraction.coerceIn(0f, 1f) * cardDurationMs).toLong()
                                            positionMs = targetMs
                                            feedPlayer.seekTo(targetMs)
                                        }
                                    },
                                    onRetryPlayback = {
                                        pinnedVideoId = video.id
                                        isUserPaused = false
                                        activePlaybackError = null
                                        hasRenderedFirstFrame = false
                                        if (video.videoUri.isNotBlank()) {
                                            playbackState = Player.STATE_BUFFERING
                                            feedPlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(video.videoUri), true)
                                            feedPlayer.prepare()
                                            feedPlayer.playWhenReady = true
                                            feedPlayer.play()
                                        }
                                    },
                                    onToggleMute = { isMuted = !isMuted },
                                    onOpenFullPlayer = { onVideoClick(video.id) },
                                    onCreatorClick = { onCreatorClick(video.creatorId) },
                                    onLikeClick = { onLikeClick(video.id) },
                                    onFollowClick = { onFollowClick(video.creatorId) },
                                    onTagClick = { clickedTag ->
                                        onTagFilterChange(if (uiState.selectedTag == clickedTag) null else clickedTag)
                                    },
                                    onShareClick = { shareVideo(context, video) },
                                    onCommentsClick = { activeCommentVideoId = video.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val activeCommentVideo = remember(activeCommentVideoId, videos) {
        val targetId = activeCommentVideoId ?: return@remember null
        videos.firstOrNull { it.id == targetId }
    }
    activeCommentVideo?.let { commentTarget ->
        VideoCommentsBottomSheet(
            video = commentTarget,
            currentLanguage = currentLanguage,
            observeComments = observeComments,
            onPostComment = onPostComment,
            onCreatorClick = { cid ->
                activeCommentVideoId = null
                onCreatorClick(cid)
            },
            onDismiss = { activeCommentVideoId = null }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoFeedCard(
    video: VideoItem,
    currentLanguage: AppLanguage,
    isActive: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    hasRenderedFirstFrame: Boolean,
    playbackError: String?,
    isMuted: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    sharedPlayer: ExoPlayer?,
    isFollowingCreator: Boolean,
    showFollowButton: Boolean,
    onTogglePlay: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onRetryPlayback: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onCreatorClick: () -> Unit,
    onLikeClick: () -> Unit,
    onFollowClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onShareClick: () -> Unit,
    onCommentsClick: () -> Unit
) {
    val strings = LocalAppStrings.current

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 6.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_feed_card_${video.id}")
    ) {
        Column {
            // ExoPlayer Video Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (isActive && sharedPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("video_feed_player_${video.id}")
                    )
                }

                // Keep thumbnail displayed smoothly until ExoPlayer renders first frame
                if (!isActive || !hasRenderedFirstFrame) {
                    MediaImage(
                        uri = video.thumbnailUri,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(FeedVideoGradient)
                        .clickable(onClick = onTogglePlay)
                )

                // Top Overlay: Category / Active Playing Badge + Creator Profile Badge
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.68f)
                        ) {
                            Text(
                                text = categoryLabel(video.category, currentLanguage),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                        AnimatedVisibility(visible = isActive && isPlaying) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                modifier = Modifier.testTag("video_feed_playing_badge_${video.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = strings.playingNowBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    CreatorProfileOverlayBadge(
                        creatorName = video.creatorName,
                        creatorAvatarUri = video.creatorAvatarUri,
                        isFollowing = isFollowingCreator,
                        showFollowButton = showFollowButton,
                        badgeTestTag = "video_feed_profile_badge_${video.id}",
                        creatorTestTag = "video_feed_creator_${video.id}",
                        followTestTag = "video_feed_follow_button_${video.id}",
                        onCreatorClick = onCreatorClick,
                        onFollowClick = onFollowClick,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // Center State: Error Overlay or Play/Pause Button (with subtle buffering ring)
                if (playbackError != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                            .testTag("video_feed_error_overlay_${video.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = playbackError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Button(
                                onClick = onRetryPlayback,
                                modifier = Modifier.testTag("video_feed_retry_button_${video.id}")
                            ) {
                                Text(strings.retry)
                            }
                        }
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier
                                    .size(62.dp)
                                    .testTag("video_feed_buffering_${video.id}")
                            )
                        }
                        Surface(
                            onClick = onTogglePlay,
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = if (isPlaying) 0.42f else 0.72f),
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("video_feed_play_pause_${video.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause ${video.title}" else "Play ${video.title}",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Overlay: Mute, Animated Like Button, Elapsed/Duration Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (isActive && sharedPlayer != null) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                .testTag("video_feed_mute_${video.id}")
                        ) {
                            Icon(
                                imageVector = if (isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AnimatedOverlayLikeButton(
                            isLiked = video.isLikedByCurrentUser,
                            likeCount = video.likeCount,
                            onClick = onLikeClick,
                            animationKey = video.id,
                            compact = true,
                            buttonTestTag = "video_feed_like_button_${video.id}",
                            countTestTag = "video_feed_like_count_${video.id}",
                            iconTestTag = "video_feed_like_icon_${video.id}"
                        )
                        AnimatedOverlayCommentButton(
                            commentCount = video.commentCount,
                            onClick = onCommentsClick,
                            compact = true,
                            buttonTestTag = "video_feed_overlay_comment_button_${video.id}",
                            countTestTag = "video_feed_overlay_comment_count_${video.id}",
                            iconTestTag = "video_feed_overlay_comment_icon_${video.id}"
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.78f),
                            modifier = Modifier.testTag("video_feed_time_${video.id}")
                        ) {
                            val timeText = if (isActive && durationMs > 0L) {
                                "${formatDuration(positionMs / 1000L)} / ${formatDuration(durationMs / 1000L)}"
                            } else if (video.format == VideoFormat.SHORT) {
                                "SHORT · ${formatDuration(video.durationSeconds)}"
                            } else {
                                formatDuration(video.durationSeconds)
                            }
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (isActive && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }

            // Interactive Seek Slider for Active Video
            AnimatedVisibility(visible = isActive && durationMs > 0L) {
                Slider(
                    value = progress,
                    onValueChange = onSeekToFraction,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(28.dp)
                        .testTag("video_feed_scrubber_${video.id}")
                )
            }

            // Video Info & Seamless Action Footer
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CreatorAvatar(
                        uri = video.creatorAvatarUri,
                        contentDescription = video.creatorName,
                        size = 40.dp,
                        modifier = Modifier.clickable(onClick = onCreatorClick)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onCreatorClick)
                        ) {
                            Text(
                                text = video.creatorName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified creator",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = " · ${formatCompactCount(video.viewCount)} ${strings.viewsLabel} · ${
                                    formatUploadDate(video.createdAt, currentLanguage == AppLanguage.HINDI)
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (video.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = video.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (video.tags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(video.tags.take(4), key = { "${video.id}_tag_$it" }) { tag ->
                            Surface(
                                onClick = { onTagClick(tag) },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = onLikeClick,
                            shape = RoundedCornerShape(50),
                            color = if (video.isLikedByCurrentUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_feed_footer_like_button_${video.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                AnimatedHeartIcon(
                                    isLiked = video.isLikedByCurrentUser,
                                    animationKey = video.id,
                                    iconSize = 17.dp,
                                    likedTint = MaterialTheme.colorScheme.primary,
                                    unlikedTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    contentDescription = strings.like
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatCompactCount(video.likeCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("video_feed_footer_like_count_${video.id}")
                                )
                            }
                        }

                        Surface(
                            onClick = onCommentsClick,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_feed_comment_button_${video.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = strings.commentsLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatCompactCount(video.commentCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("video_feed_comment_count_${video.id}")
                                )
                            }
                        }

                        TextButton(
                            onClick = onTogglePlay,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_feed_inline_action_${video.id}")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPlaying) "Pause" else "Play")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_feed_share_button_${video.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = strings.share,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenFullPlayer,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_feed_open_player_${video.id}")
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.openFullPlayer)
                        }
                    }
                }
            }
        }
    }
}
