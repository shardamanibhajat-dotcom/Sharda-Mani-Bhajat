package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.CreatorPlaylist
import com.example.data.model.UserProfile
import com.example.data.model.formatCompactCount
import com.example.data.model.formatJoinDate
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.EditChannelDialog
import com.example.ui.components.LongVideoCard
import com.example.ui.components.MediaImage
import com.example.ui.components.MonetizationUnavailableBanner
import com.example.ui.components.PlaylistDetailDialog
import com.example.ui.components.PlaylistEditorDialog
import com.example.ui.components.ShortPreviewCard
import com.example.ui.components.shareVideo
import com.example.ui.viewmodel.ChannelUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    currentUserId: String,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    uiState: ChannelUiState,
    onBack: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onToggleBlock: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenUpload: () -> Unit = {},
    onOpenChannel: (String) -> Unit = {},
    onUpdateChannelProfile: (
        displayName: String,
        username: String,
        bio: String,
        channelDescription: String,
        avatarUri: String,
        bannerUri: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onCreatePlaylist: (title: String, description: String, thumbnailUri: String, isPublic: Boolean, onDone: () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onUpdatePlaylist: (playlistId: String, title: String, description: String, thumbnailUri: String, isPublic: Boolean, onDone: () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onDeletePlaylist: (playlistId: String) -> Unit = {},
    onAddVideoToPlaylist: (playlistId: String, videoId: String) -> Unit = { _, _ -> },
    onRemoveVideoFromPlaylist: (playlistId: String, videoId: String) -> Unit = { _, _ -> },
    onVideoClick: (String) -> Unit,
    onLikeClick: (String) -> Unit,
    onReportVideo: (videoId: String, reason: String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val creator = uiState.creator
    var selectedTab by remember { mutableIntStateOf(0) }
    var homePillIndex by remember { mutableIntStateOf(0) } // 0 = My Channel/Uploads, 1 = Subscribed Channels
    var showEditChannelDialog by remember { mutableStateOf(false) }
    var editProfileError by remember { mutableStateOf<String?>(null) }
    var editingPlaylist by remember { mutableStateOf<CreatorPlaylist?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var deletingPlaylist by remember { mutableStateOf<CreatorPlaylist?>(null) }
    var viewingPlaylistId by remember { mutableStateOf<String?>(null) }

    val quickCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val c = creator
        if (uri != null && c != null && c.id == currentUserId) {
            onUpdateChannelProfile(
                c.displayName,
                c.username,
                c.bio,
                c.channelDescription,
                c.avatarUri,
                uri.toString(),
                {},
                {}
            )
        }
    }

    val quickAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val c = creator
        if (uri != null && c != null && c.id == currentUserId) {
            onUpdateChannelProfile(
                c.displayName,
                c.username,
                c.bio,
                c.channelDescription,
                uri.toString(),
                c.bannerUri,
                {},
                {}
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = creator?.displayName ?: strings.channelTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("channel_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (creator == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text("Channel not found.")
            }
            return@Scaffold
        }

        val isOwnChannel = creator.id == currentUserId
        val isRestrictedVisitor = (creator.isPrivateProfile && !isOwnChannel) || uiState.isBlocked
        val viewingPlaylist = viewingPlaylistId?.let { id ->
            uiState.playlists.firstOrNull { it.id == id }
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .testTag("channel_screen")
            ) {
                // Hero Cover Banner + Overlapping Squircle Avatar + Action Bar
                item(key = "channel_hero") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(164.dp)
                        ) {
                            MediaImage(
                                uri = creator.bannerUri,
                                contentDescription = "Channel cover",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("channel_banner_image")
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.15f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.65f)
                                            )
                                        )
                                    )
                            )
                            if (isOwnChannel) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            editProfileError = null
                                            showEditChannelDialog = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier.testTag("channel_edit_profile_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = strings.editProfile,
                                            tint = Color.White,
                                            modifier = Modifier.padding(8.dp).size(18.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = {
                                            quickCoverPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier.testTag("channel_pick_cover_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = strings.changeCover,
                                            tint = Color.White,
                                            modifier = Modifier.padding(8.dp).size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.size(76.dp)) {
                                    MediaImage(
                                        uri = creator.avatarUri,
                                        contentDescription = creator.displayName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(18.dp))
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                                RoundedCornerShape(18.dp)
                                            )
                                    )
                                    if (isOwnChannel) {
                                        Surface(
                                            onClick = {
                                                quickAvatarPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .testTag("channel_pick_avatar_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoCamera,
                                                contentDescription = strings.changeAvatar,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(4.dp).size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = creator.displayName,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (creator.isApprovedUploader) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Verified Creator",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "@${creator.username}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val subSummary = if (creator.hideSubscriberCount && !isOwnChannel) {
                                        strings.subscribersHidden
                                    } else {
                                        "${formatCompactCount(creator.subscriberCount)} ${strings.subscribersLabel}"
                                    }
                                    Text(
                                        text = "$subSummary · ${formatCompactCount(uiState.totalViews)} ${strings.viewsLabel} · ${uiState.videos.size} ${strings.channelTabVideos}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("channel_subscriber_count")
                                    )
                                }
                            }

                            if (creator.bio.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = creator.bio,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isOwnChannel) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = onOpenUpload,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("channel_upload_button")
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.uploadNewVideo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    OutlinedButton(
                                        onClick = onOpenStudio,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("channel_open_studio_button")
                                    ) {
                                        Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.creatorStudioTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = onToggleSubscribe,
                                        colors = if (uiState.isSubscribed) {
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            ButtonDefaults.buttonColors()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("channel_subscribe_button")
                                    ) {
                                        Text(if (uiState.isSubscribed) strings.subscribed else strings.subscribe)
                                    }
                                    OutlinedButton(
                                        onClick = onToggleBlock,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (uiState.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("channel_block_button")
                                    ) {
                                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (uiState.isBlocked) strings.unblockUser else strings.blockUser)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isRestrictedVisitor) {
                    item(key = "private_profile_notice") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("channel_private_notice")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (uiState.isBlocked) {
                                        strings.blockedUsersTitle
                                    } else {
                                        strings.privateChannelNotice
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // 5 Horizontal Scrollable Channel Tabs: Home, Videos, Shorts, Playlists, About
                item(key = "channel_tabs") {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(strings.channelTabHome) },
                            modifier = Modifier.testTag("channel_tab_home")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("${strings.channelTabVideos} (${uiState.longVideos.size})") },
                            modifier = Modifier.testTag("channel_tab_videos")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("${strings.channelTabShorts} (${uiState.shortVideos.size})") },
                            modifier = Modifier.testTag("channel_tab_shorts")
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("${strings.channelTabPlaylists} (${uiState.playlists.size})") },
                            modifier = Modifier.testTag("channel_tab_playlists")
                        )
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = { Text(strings.channelTabAbout) },
                            modifier = Modifier.testTag("channel_tab_about")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        if (isOwnChannel) {
                            item(key = "channel_home_pills") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    FilterChip(
                                        selected = homePillIndex == 0,
                                        onClick = { homePillIndex = 0 },
                                        label = { Text(strings.myChannelPill) }
                                    )
                                    FilterChip(
                                        selected = homePillIndex == 1,
                                        onClick = { homePillIndex = 1 },
                                        label = {
                                            Text("${strings.subscribedChannelsTitle} (${uiState.subscribedCreators.size})")
                                        },
                                        modifier = Modifier.testTag("channel_subscribed_pill")
                                    )
                                }
                            }
                        }

                        if (isOwnChannel && homePillIndex == 1) {
                            if (uiState.subscribedCreators.isEmpty()) {
                                item(key = "channel_no_subs") {
                                    EmptyChannelSectionCard(text = strings.noSubscribedChannels)
                                }
                            } else {
                                item(key = "channel_subscribed_strip") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.subscribedCreators, key = { it.id }) { subCreator ->
                                            SubscribedCreatorMiniCard(
                                                creator = subCreator,
                                                subscribersLabel = strings.subscribersLabel,
                                                onClick = { onOpenChannel(subCreator.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item(key = "channel_monetization") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    MonetizationUnavailableBanner(showDetail = true)
                                }
                            }

                            if (!isRestrictedVisitor) {
                                if (uiState.shortVideos.isNotEmpty()) {
                                    item(key = "home_shorts_strip") {
                                        Column {
                                            Text(
                                                text = strings.shortsSectionTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                            )
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(uiState.shortVideos, key = { it.id }) { short ->
                                                    ShortPreviewCard(
                                                        video = short,
                                                        onClick = { onVideoClick(short.id) },
                                                        isFollowingCreator = uiState.isSubscribed,
                                                        showFollowButton = !isOwnChannel,
                                                        onCreatorClick = {},
                                                        onFollowClick = onToggleSubscribe,
                                                        onLikeClick = { onLikeClick(short.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val latestUploads = uiState.videos.take(8)
                                if (latestUploads.isEmpty()) {
                                    item(key = "channel_home_empty") {
                                        EmptyChannelSectionCard(text = strings.noVideosFound)
                                    }
                                } else {
                                    item(key = "channel_home_latest_header") {
                                        Text(
                                            text = strings.recentUploadsTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                    items(latestUploads, key = { "home_${it.id}" }, contentType = { "channel_video" }) { video ->
                                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                            LongVideoCard(
                                                video = video,
                                                currentLanguage = currentLanguage,
                                                onVideoClick = { onVideoClick(video.id) },
                                                onCreatorClick = {},
                                                onLikeClick = { onLikeClick(video.id) },
                                                onShareClick = { shareVideo(context, video) },
                                                onReportClick = { onReportVideo(video.id, "Reported from channel") },
                                                isFollowingCreator = uiState.isSubscribed,
                                                showFollowButton = !isOwnChannel,
                                                onFollowClick = onToggleSubscribe
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (uiState.longVideos.isEmpty()) {
                            item(key = "empty_long_videos") {
                                EmptyChannelSectionCard(text = strings.noVideosFound)
                            }
                        } else {
                            items(uiState.longVideos, key = { "long_${it.id}" }, contentType = { "channel_long_video" }) { video ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    LongVideoCard(
                                        video = video,
                                        currentLanguage = currentLanguage,
                                        onVideoClick = { onVideoClick(video.id) },
                                        onCreatorClick = {},
                                        onLikeClick = { onLikeClick(video.id) },
                                        onShareClick = { shareVideo(context, video) },
                                        onReportClick = { onReportVideo(video.id, "Reported from channel") },
                                        isFollowingCreator = uiState.isSubscribed,
                                        showFollowButton = !isOwnChannel,
                                        onFollowClick = onToggleSubscribe
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        if (uiState.shortVideos.isEmpty()) {
                            item(key = "empty_short_videos") {
                                EmptyChannelSectionCard(text = strings.noVideosFound)
                            }
                        } else {
                            items(uiState.shortVideos, key = { "short_${it.id}" }, contentType = { "channel_short_video" }) { short ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    LongVideoCard(
                                        video = short,
                                        currentLanguage = currentLanguage,
                                        onVideoClick = { onVideoClick(short.id) },
                                        onCreatorClick = {},
                                        onLikeClick = { onLikeClick(short.id) },
                                        onShareClick = { shareVideo(context, short) },
                                        onReportClick = { onReportVideo(short.id, "Reported from channel") },
                                        isFollowingCreator = uiState.isSubscribed,
                                        showFollowButton = !isOwnChannel,
                                        onFollowClick = onToggleSubscribe
                                    )
                                }
                            }
                        }
                    }

                    3 -> {
                        if (isOwnChannel) {
                            item(key = "create_playlist_row") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Button(
                                        onClick = { showCreatePlaylistDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("channel_create_playlist_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.createPlaylist)
                                    }
                                }
                            }
                        }

                        if (uiState.playlists.isEmpty()) {
                            item(key = "empty_playlists") {
                                EmptyChannelSectionCard(text = strings.noPlaylistsYet)
                            }
                        } else {
                            items(uiState.playlists, key = { it.id }, contentType = { "channel_playlist" }) { playlist ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    PlaylistCard(
                                        playlist = playlist,
                                        isOwner = isOwnChannel,
                                        onOpen = { viewingPlaylistId = playlist.id },
                                        onEdit = { editingPlaylist = playlist },
                                        onDelete = { deletingPlaylist = playlist }
                                    )
                                }
                            }
                        }
                    }

                    4 -> {
                        item(key = "channel_about") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("channel_about_card")
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(18.dp)
                                    ) {
                                        Text(
                                            text = creator.displayName,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "@${creator.username}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = if (isRestrictedVisitor) {
                                                strings.privateChannelNotice
                                            } else {
                                                creator.channelDescription.ifBlank { creator.bio }
                                            },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        HorizontalDivider()
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarMonth,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${strings.joinedLabel} ${formatJoinDate(creator.createdAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isRestrictedVisitor) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                StatMiniCard(
                                                    label = strings.totalViewsLabel,
                                                    value = formatCompactCount(uiState.totalViews),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                StatMiniCard(
                                                    label = strings.totalLikesLabel,
                                                    value = formatCompactCount(uiState.totalLikes),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                StatMiniCard(
                                                    label = strings.totalUploadsLabel,
                                                    value = uiState.videos.size.toString(),
                                                    modifier = Modifier.weight(1f)
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
        }

        if (showEditChannelDialog && isOwnChannel) {
            EditChannelDialog(
                user = creator,
                externalError = editProfileError,
                onDismiss = {
                    editProfileError = null
                    showEditChannelDialog = false
                },
                onSave = { displayName, username, bio, desc, avatar, banner ->
                    onUpdateChannelProfile(
                        displayName,
                        username,
                        bio,
                        desc,
                        avatar,
                        banner,
                        {
                            editProfileError = null
                            showEditChannelDialog = false
                        },
                        { err -> editProfileError = err }
                    )
                }
            )
        }

        if (showCreatePlaylistDialog && isOwnChannel) {
            PlaylistEditorDialog(
                existing = null,
                onDismiss = { showCreatePlaylistDialog = false },
                onSave = { title, desc, thumb, isPublic ->
                    onCreatePlaylist(title, desc, thumb, isPublic) {
                        showCreatePlaylistDialog = false
                    }
                }
            )
        }

        editingPlaylist?.let { target ->
            PlaylistEditorDialog(
                existing = target,
                onDismiss = { editingPlaylist = null },
                onSave = { title, desc, thumb, isPublic ->
                    onUpdatePlaylist(target.id, title, desc, thumb, isPublic) {
                        editingPlaylist = null
                    }
                }
            )
        }

        deletingPlaylist?.let { target ->
            AlertDialog(
                onDismissRequest = { deletingPlaylist = null },
                title = { Text(strings.deletePlaylist) },
                text = { Text("${strings.deletePlaylist}: \"${target.title}\"?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeletePlaylist(target.id)
                            deletingPlaylist = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("playlist_confirm_delete_button")
                    ) {
                        Text(strings.deleteVideo)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingPlaylist = null }) {
                        Text(strings.cancel)
                    }
                }
            )
        }

        viewingPlaylist?.let { activePlaylist ->
            PlaylistDetailDialog(
                playlist = activePlaylist,
                isOwner = isOwnChannel,
                channelVideos = uiState.videos,
                onDismiss = { viewingPlaylistId = null },
                onPlayVideo = onVideoClick,
                onAddVideo = { vidId -> onAddVideoToPlaylist(activePlaylist.id, vidId) },
                onRemoveVideo = { vidId -> onRemoveVideoFromPlaylist(activePlaylist.id, vidId) }
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: CreatorPlaylist,
    isOwner: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_card_${playlist.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                MediaImage(
                    uri = playlist.thumbnailUri,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = playlist.videoCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (playlist.isPublic) Icons.Default.Public else Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (playlist.isPublic) strings.playlistPublicLabel else strings.playlistPrivateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (isOwner) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("playlist_edit_${playlist.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = strings.editPlaylist)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("playlist_delete_${playlist.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = strings.deletePlaylist,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscribedCreatorMiniCard(
    creator: UserProfile,
    subscribersLabel: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(132.dp)
            .testTag("subscribed_creator_card_${creator.id}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            CreatorAvatar(
                uri = creator.avatarUri,
                contentDescription = creator.displayName,
                size = 54.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = creator.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatCompactCount(creator.subscriberCount)} $subscribersLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyChannelSectionCard(text: String) {
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
