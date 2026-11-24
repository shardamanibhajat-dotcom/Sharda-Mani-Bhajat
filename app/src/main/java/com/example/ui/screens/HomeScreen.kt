package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.data.model.FeedSortOrder
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoCategory
import com.example.data.model.VideoComment
import com.example.data.model.VideoItem
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel
import com.example.localization.sortLabel
import com.example.media.ActivePlaybackCoordinator
import com.example.media.ExoPlayerFactory
import com.example.ui.components.CreatorPaisaLogoBadge
import com.example.ui.components.LanguageTogglePill
import com.example.ui.components.LongVideoCard
import com.example.ui.components.MonetizationUnavailableBanner
import com.example.ui.components.NotificationBellButton
import com.example.ui.components.ShortPreviewCard
import com.example.ui.components.VideoCommentsBottomSheet
import com.example.ui.components.shareVideo
import com.example.ui.viewmodel.FeedFilter
import com.example.ui.viewmodel.FeedUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun HomeScreen(
    currentUser: UserProfile,
    uiState: FeedUiState,
    currentLanguage: AppLanguage,
    unreadNotificationCount: Int,
    onToggleLanguage: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (FeedFilter) -> Unit,
    onCategoryChange: (VideoCategory) -> Unit,
    onTagChange: (String?) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onSortChange: (FeedSortOrder) -> Unit,
    onVideoClick: (String) -> Unit,
    onCreatorClick: (String) -> Unit,
    onLikeClick: (String) -> Unit,
    onReportVideo: (videoId: String, reason: String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    onOpenUpload: () -> Unit = {},
    onOpenVideoFeed: () -> Unit = {},
    followedCreatorIds: Set<String> = emptySet(),
    onFollowClick: (String) -> Unit = {},
    showHeader: Boolean = true,
    observeComments: (String) -> Flow<List<VideoComment>> = { flowOf(emptyList()) },
    onPostComment: (videoId: String, text: String, parentCommentId: String?, onSuccess: () -> Unit) -> Unit = { _, _, _, _ -> }
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reportTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var activeCommentVideoId by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val tagRowState = rememberLazyListState()
    val categoryRowState = rememberLazyListState()
    val shortsRowState = rememberLazyListState()
    val feedList = remember(uiState.filter, uiState.longVideos, uiState.shorts) {
        when (uiState.filter) {
            FeedFilter.ALL -> uiState.longVideos
            FeedFilter.SHORTS -> uiState.shorts
            FeedFilter.LONG -> uiState.longVideos
        }
    }

    // Determine the most-centered visible video card in the vertical feed
    val activeFeedVideoId by remember(feedList, listState) {
        derivedStateOf {
            if (feedList.isEmpty()) {
                null
            } else {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val feedIds = feedList.map { it.id }.toSet()
                layoutInfo.visibleItemsInfo
                    .filter { info -> info.key is String && feedIds.contains(info.key as String) }
                    .minByOrNull { info ->
                        val itemCenter = info.offset + info.size / 2
                        abs(itemCenter - viewportCenter)
                    }
                    ?.key as? String
            }
        }
    }
    val activeFeedVideo = remember(activeFeedVideoId, feedList) {
        feedList.firstOrNull { it.id == activeFeedVideoId }
    }

    // Single pooled ExoPlayer reused smoothly across vertical scroll items
    val feedInlinePlayer = remember {
        ExoPlayerFactory.create(context, isShortLoop = false).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }
    var isFeedMuted by remember { mutableStateOf(true) }
    var isInlinePlaying by remember { mutableStateOf(false) }
    var inlinePlaybackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var inlineFirstFrame by remember(activeFeedVideo?.id) { mutableStateOf(false) }
    var inlineProgress by remember(activeFeedVideo?.id) { mutableFloatStateOf(0f) }
    var loadedInlineKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isLifecycleResumed by remember { mutableStateOf(true) }

    LaunchedEffect(isFeedMuted) {
        feedInlinePlayer.volume = if (isFeedMuted) 0f else 1f
    }

    LaunchedEffect(activeFeedVideo?.id, activeFeedVideo?.videoUri, isLifecycleResumed) {
        val target = activeFeedVideo
        if (target == null || target.videoUri.isBlank() || !isLifecycleResumed) {
            if (feedInlinePlayer.isPlaying || feedInlinePlayer.playWhenReady) {
                feedInlinePlayer.playWhenReady = false
                feedInlinePlayer.pause()
            }
            return@LaunchedEffect
        }
        val keyPair = target.id to target.videoUri
        if (loadedInlineKey != keyPair) {
            loadedInlineKey = keyPair
            inlineFirstFrame = false
            inlineProgress = 0f
            inlinePlaybackState = Player.STATE_BUFFERING
            feedInlinePlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(target.videoUri), true)
            feedInlinePlayer.prepare()
        }
        feedInlinePlayer.playWhenReady = true
        feedInlinePlayer.play()
    }

    // Prefetch upcoming card in vertical scroll
    LaunchedEffect(activeFeedVideo?.id, feedList) {
        val idx = feedList.indexOfFirst { it.id == activeFeedVideo?.id }
        val nextUri = if (idx >= 0) feedList.getOrNull(idx + 1)?.videoUri else null
        if (!nextUri.isNullOrBlank()) {
            launch { ExoPlayerFactory.prefetchToCache(context, nextUri) }
        }
    }

    LaunchedEffect(isInlinePlaying, activeFeedVideo?.id) {
        while (isInlinePlaying && activeFeedVideo != null) {
            val dur = feedInlinePlayer.duration.coerceAtLeast(0L)
            val pos = feedInlinePlayer.currentPosition.coerceAtLeast(0L)
            inlineProgress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            delay(250L)
        }
    }

    DisposableEffect(feedInlinePlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                inlinePlaybackState = state
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isInlinePlaying = playing
                if (playing) {
                    ActivePlaybackCoordinator.onPlaybackStarted(feedInlinePlayer)
                }
            }

            override fun onRenderedFirstFrame() {
                inlineFirstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                inlinePlaybackState = Player.STATE_IDLE
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isLifecycleResumed = false
                    feedInlinePlayer.playWhenReady = false
                    feedInlinePlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                }
                else -> Unit
            }
        }
        feedInlinePlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            feedInlinePlayer.removeListener(listener)
            ActivePlaybackCoordinator.onPlayerReleased(feedInlinePlayer)
            feedInlinePlayer.release()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.availableTags.isNotEmpty() || uiState.selectedTag != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        state = tagRowState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .align(Alignment.CenterHorizontally)
                            .testTag("tag_chip_bar")
                    ) {
                        item(key = "tag_all", contentType = "tag_chip") {
                            FilterChip(
                                selected = uiState.selectedTag == null,
                                onClick = { onTagChange(null) },
                                label = { Text(strings.allTagsFilter) },
                                modifier = Modifier.testTag("tag_chip_all")
                            )
                        }
                        items(
                            items = uiState.availableTags,
                            key = { "tag_$it" },
                            contentType = { "tag_chip" }
                        ) { tag ->
                            val isSelected = uiState.selectedTag == tag
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTagChange(if (isSelected) null else tag) },
                                label = { Text("#$tag") },
                                modifier = Modifier.testTag("tag_chip_$tag")
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("home_feed_list")
            ) {
            if (showHeader) {
                item(key = "home_header", contentType = "header") {
                    Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CreatorPaisaLogoBadge(size = 36.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = strings.tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LanguageTogglePill(
                            currentLanguage = currentLanguage,
                            onToggle = onToggleLanguage
                        )
                        NotificationBellButton(
                            unreadCount = unreadNotificationCount,
                            onClick = onOpenNotifications
                        )
                        IconButton(
                            onClick = onOpenStudio,
                            modifier = Modifier.testTag("open_studio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = strings.creatorStudioTitle,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (currentUser.role == UserRole.ADMIN) {
                            IconButton(
                                onClick = onOpenAdmin,
                                modifier = Modifier.testTag("open_admin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = strings.adminPanelTitle,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        IconButton(
                            onClick = onOpenSetupGuide,
                            modifier = Modifier.testTag("open_setup_guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsSuggest,
                                contentDescription = strings.setupGuideTitle,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            }

            item(key = "monetization_banner", contentType = "banner") {
                MonetizationUnavailableBanner()
            }

            item(key = "search_bar", contentType = "search") {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(strings.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            }

            item(key = "format_and_sort_row", contentType = "filters") {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.filter == FeedFilter.ALL,
                            onClick = { onFilterChange(FeedFilter.ALL) },
                            label = { Text(strings.filterAll) },
                            modifier = Modifier.testTag("filter_chip_all")
                        )
                        FilterChip(
                            selected = uiState.filter == FeedFilter.SHORTS,
                            onClick = { onFilterChange(FeedFilter.SHORTS) },
                            label = { Text(strings.filterShorts) },
                            modifier = Modifier.testTag("filter_chip_shorts")
                        )
                        FilterChip(
                            selected = uiState.filter == FeedFilter.LONG,
                            onClick = { onFilterChange(FeedFilter.LONG) },
                            label = { Text(strings.filterLongVideos) },
                            modifier = Modifier.testTag("filter_chip_long")
                        )
                    }

                    Box {
                        Surface(
                            onClick = { showSortMenu = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.testTag("home_sort_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
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
                                    modifier = Modifier.testTag("sort_option_${order.name}")
                                )
                            }
                        }
                    }
                }
            }

            item(key = "category_chips_row", contentType = "categories") {
                LazyRow(
                    state = categoryRowState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_category_row")
                ) {
                    items(
                        items = VideoCategory.entries,
                        key = { it.name },
                        contentType = { "category_chip" }
                    ) { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = { onCategoryChange(category) },
                            label = { Text(categoryLabel(category, currentLanguage)) },
                            modifier = Modifier.testTag("category_chip_${category.name}")
                        )
                    }
                }
            }

            if (uiState.filter != FeedFilter.LONG && uiState.shorts.isNotEmpty()) {
                item(key = "shorts_header", contentType = "section_header") {
                    Text(
                        text = strings.shortsSectionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                item(key = "shorts_carousel", contentType = "carousel") {
                    LazyRow(
                        state = shortsRowState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_shorts_carousel")
                    ) {
                        items(
                            items = uiState.shorts,
                            key = { it.id },
                            contentType = { "short_preview" }
                        ) { shortVideo ->
                            ShortPreviewCard(
                                video = shortVideo,
                                onClick = { onVideoClick(shortVideo.id) },
                                isFollowingCreator = followedCreatorIds.contains(shortVideo.creatorId),
                                showFollowButton = shortVideo.creatorId != currentUser.id,
                                onCreatorClick = { onCreatorClick(shortVideo.creatorId) },
                                onFollowClick = { onFollowClick(shortVideo.creatorId) },
                                onLikeClick = { onLikeClick(shortVideo.id) }
                            )
                        }
                    }
                }
            }

            item(key = "main_feed_header", contentType = "section_header") {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = if (uiState.filter == FeedFilter.SHORTS) {
                            strings.shortsSectionTitle
                        } else {
                            strings.longVideosSectionTitle
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = onOpenVideoFeed,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_video_feed_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.videoFeedTitle,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (feedList.isEmpty()) {
                item(key = "empty_or_loading_feed", contentType = "empty") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator()
                            Text(
                                text = "Fetching videos from Firestore...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
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
                            if (uiState.searchQuery.isBlank() &&
                                uiState.selectedCategory == VideoCategory.ALL &&
                                uiState.selectedTag == null
                            ) {
                                Button(onClick = onOpenUpload) {
                                    Text(strings.uploadNewVideo)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onClearFilters,
                                    modifier = Modifier.testTag("home_clear_filters_button")
                                ) {
                                    Text(strings.clearFiltersLabel)
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    items = feedList,
                    key = { it.id },
                    contentType = { "long_video" }
                ) { video ->
                    val isInlineActive = activeFeedVideo?.id == video.id
                    LongVideoCard(
                        video = video,
                        currentLanguage = currentLanguage,
                        onVideoClick = { onVideoClick(video.id) },
                        onCreatorClick = { onCreatorClick(video.creatorId) },
                        onLikeClick = { onLikeClick(video.id) },
                        onShareClick = { shareVideo(context, video) },
                        onReportClick = {
                            reportTargetVideo = video
                            reportReason = ""
                        },
                        inlinePlayer = if (isInlineActive) feedInlinePlayer else null,
                        isInlineActive = isInlineActive,
                        hasRenderedFirstFrame = isInlineActive && inlineFirstFrame,
                        isInlineBuffering = isInlineActive && inlinePlaybackState == Player.STATE_BUFFERING,
                        isMuted = isFeedMuted,
                        playbackProgress = if (isInlineActive) inlineProgress else 0f,
                        onToggleMute = { isFeedMuted = !isFeedMuted },
                        isFollowingCreator = followedCreatorIds.contains(video.creatorId),
                        showFollowButton = video.creatorId != currentUser.id,
                        onFollowClick = { onFollowClick(video.creatorId) },
                        onTagClick = { clickedTag ->
                            onTagChange(if (uiState.selectedTag == clickedTag) null else clickedTag)
                        },
                        onCommentsClick = { activeCommentVideoId = video.id }
                    )
                }
            }
        }
        }
    }

    val activeCommentVideo = remember(activeCommentVideoId, uiState.allMatchingVideos, uiState.longVideos, uiState.shorts) {
        val targetId = activeCommentVideoId ?: return@remember null
        uiState.allMatchingVideos.firstOrNull { it.id == targetId }
            ?: uiState.longVideos.firstOrNull { it.id == targetId }
            ?: uiState.shorts.firstOrNull { it.id == targetId }
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

    reportTargetVideo?.let { target ->
        AlertDialog(
            onDismissRequest = { reportTargetVideo = null },
            title = { Text(strings.reportDialogTitle) },
            text = {
                Column {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("Reason (spam, misleading, inappropriate)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportVideo(target.id, reportReason)
                        reportTargetVideo = null
                    },
                    modifier = Modifier.testTag("confirm_report_button")
                ) {
                    Text(strings.reportSubmit)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTargetVideo = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
