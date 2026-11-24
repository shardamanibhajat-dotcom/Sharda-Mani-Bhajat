package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.components.AnimatedOverlayCommentButton
import com.example.ui.components.AnimatedOverlayLikeButton
import com.example.ui.components.CreatorProfileOverlayBadge
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.formatDuration
import com.example.localization.LocalAppStrings
import com.example.media.PlaybackTrackInfo
import com.example.media.VideoQualityOption
import com.example.ui.components.MediaImage

val PlaybackSpeedOptions = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

fun formatSpeedLabel(speed: Float): String = when (speed) {
    1.0f -> "1x"
    2.0f -> "2x"
    0.5f -> "0.5x"
    else -> "${speed}x"
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerViewportBox(
    video: VideoItem,
    exoPlayer: ExoPlayer,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    hasRenderedFirstFrame: Boolean,
    playbackError: String?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    playbackSpeed: Float,
    volumeLevel: Float,
    isMuted: Boolean,
    selectedQuality: VideoQualityOption,
    trackInfo: PlaybackTrackInfo,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onEnterFullscreen: () -> Unit,
    onRetryPlayback: () -> Unit,
    modifier: Modifier = Modifier,
    isFollowingCreator: Boolean = false,
    showFollowButton: Boolean = false,
    onCreatorClick: () -> Unit = {},
    onFollowClick: () -> Unit = {},
    onToggleLike: (() -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (video.format == VideoFormat.SHORT) 9f / 11f else 16f / 9f)
            .background(Color.Black)
    ) {
        if (!isFullscreen) {
            ExoPlayerSurface(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls
                )
        )

        if (!hasRenderedFirstFrame && playbackError == null && video.thumbnailUri.isNotBlank()) {
            MediaImage(
                uri = video.thumbnailUri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isFullscreen) {
            PlayerHudOverlay(
                video = video,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                playbackError = playbackError,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                playbackSpeed = playbackSpeed,
                volumeLevel = volumeLevel,
                isMuted = isMuted,
                selectedQuality = selectedQuality,
                trackInfo = trackInfo,
                showControls = showControls || !isPlaying,
                isFullscreen = false,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onSeekBy = onSeekBy,
                onChangeSpeed = onChangeSpeed,
                onChangeVolume = onChangeVolume,
                onToggleMute = onToggleMute,
                onSelectQuality = onSelectQuality,
                onToggleFullscreen = onEnterFullscreen,
                onRetryPlayback = onRetryPlayback,
                isFollowingCreator = isFollowingCreator,
                showFollowButton = showFollowButton,
                onCreatorClick = onCreatorClick,
                onFollowClick = onFollowClick,
                onToggleLike = onToggleLike,
                onOpenComments = onOpenComments
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun FullscreenPlayerDialog(
    video: VideoItem,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackError: String?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    playbackSpeed: Float,
    volumeLevel: Float,
    isMuted: Boolean,
    selectedQuality: VideoQualityOption,
    trackInfo: PlaybackTrackInfo,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onExitFullscreen: () -> Unit,
    onRetryPlayback: () -> Unit,
    isFollowingCreator: Boolean = false,
    showFollowButton: Boolean = false,
    onCreatorClick: () -> Unit = {},
    onFollowClick: () -> Unit = {},
    onToggleLike: (() -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onExitFullscreen,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
        ) {
            ExoPlayerSurface(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleControls
                    )
            )
            PlayerHudOverlay(
                video = video,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                playbackError = playbackError,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                playbackSpeed = playbackSpeed,
                volumeLevel = volumeLevel,
                isMuted = isMuted,
                selectedQuality = selectedQuality,
                trackInfo = trackInfo,
                showControls = showControls || !isPlaying,
                isFullscreen = true,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onSeekBy = onSeekBy,
                onChangeSpeed = onChangeSpeed,
                onChangeVolume = onChangeVolume,
                onToggleMute = onToggleMute,
                onSelectQuality = onSelectQuality,
                onToggleFullscreen = onExitFullscreen,
                onRetryPlayback = onRetryPlayback,
                isFollowingCreator = isFollowingCreator,
                showFollowButton = showFollowButton,
                onCreatorClick = onCreatorClick,
                onFollowClick = onFollowClick,
                onToggleLike = onToggleLike,
                onOpenComments = onOpenComments
            )
        }
    }
}

@Composable
private fun PlayerHudOverlay(
    video: VideoItem,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackError: String?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    playbackSpeed: Float,
    volumeLevel: Float,
    isMuted: Boolean,
    selectedQuality: VideoQualityOption,
    trackInfo: PlaybackTrackInfo,
    showControls: Boolean,
    isFullscreen: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onToggleFullscreen: () -> Unit,
    onRetryPlayback: () -> Unit,
    isFollowingCreator: Boolean = false,
    showFollowButton: Boolean = false,
    onCreatorClick: () -> Unit = {},
    onFollowClick: () -> Unit = {},
    onToggleLike: (() -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val fallbackDurationMs = (video.durationSeconds.coerceAtLeast(1L)) * 1000L
    val effectiveDurationMs = if (totalDurationMs > 0L) totalDurationMs else fallbackDurationMs
    val clampedPosMs = currentPositionMs.coerceIn(0L, effectiveDurationMs)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val liveFraction = (clampedPosMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
    val displayedFraction = if (isScrubbing) scrubFraction else liveFraction
    val displayedPosSeconds = ((displayedFraction * effectiveDurationMs).toLong() / 1000L)
    val totalSeconds = (effectiveDurationMs / 1000L).coerceAtLeast(video.durationSeconds)

    Box(modifier = Modifier.fillMaxSize()) {
        if (isBuffering) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("player_buffering_indicator")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.bufferingVideo,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        if (playbackError != null || video.videoUri.isBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("playback_error_overlay")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playbackError ?: "Stream URL is missing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRetryPlayback,
                        modifier = Modifier.testTag("retry_playback_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.retry)
                    }
                }
            }
            return@Box
        }

        if (showControls) {
            // Subtle top + bottom readability scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.58f),
                                Color.Black.copy(alpha = 0.22f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )

            // Top bar: Creator profile badge on the left, Speed/Quality/Fullscreen on the right
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                CreatorProfileOverlayBadge(
                    creatorName = video.creatorName,
                    creatorAvatarUri = video.creatorAvatarUri,
                    isFollowing = isFollowingCreator,
                    showFollowButton = showFollowButton,
                    badgeTestTag = "player_overlay_profile_badge_${video.id}",
                    creatorTestTag = "player_overlay_creator_${video.id}",
                    followTestTag = "player_overlay_follow_button_${video.id}",
                    onCreatorClick = onCreatorClick,
                    onFollowClick = onFollowClick,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Playback Speed Selector
                Box {
                    Surface(
                        onClick = { showSpeedMenu = true },
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.testTag("player_speed_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = strings.speedLabel,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatSpeedLabel(playbackSpeed),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        PlaybackSpeedOptions.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(formatSpeedLabel(speed)) },
                                trailingIcon = {
                                    if (playbackSpeed == speed) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    onChangeSpeed(speed)
                                    showSpeedMenu = false
                                },
                                modifier = Modifier.testTag("speed_option_$speed")
                            )
                        }
                    }
                }

                // Quality selector: ONLY shown when source exposes multiple adaptive video tracks
                if (trackInfo.isAdaptiveStream) {
                    Box {
                        Surface(
                            onClick = { showQualityMenu = true },
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.72f),
                            modifier = Modifier.testTag("player_quality_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = strings.videoQualityLabel,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val activeLabel = if (selectedQuality == VideoQualityOption.AUTO && trackInfo.activeResolutionLabel != null) {
                                    "Auto (${trackInfo.activeResolutionLabel})"
                                } else {
                                    selectedQuality.label
                                }
                                Text(
                                    text = activeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false }
                        ) {
                            VideoQualityOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = {
                                        if (selectedQuality == option) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        onSelectQuality(option)
                                        showQualityMenu = false
                                    },
                                    modifier = Modifier.testTag("quality_option_${option.name}")
                                )
                            }
                        }
                    }
                }

                // Fullscreen / Exit Fullscreen button
                Surface(
                    onClick = onToggleFullscreen,
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.testTag(
                        if (isFullscreen) "exit_fullscreen_button" else "player_fullscreen_button"
                    )
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) strings.exitFullscreen else strings.fullscreen,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                }
            }

            // Center Transport Controls: Seek Back 10s, Play/Pause, Seek Forward 10s
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    onClick = { onSeekBy(-10_000L) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_seek_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Seek backward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("player_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Surface(
                    onClick = { onSeekBy(10_000L) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_seek_forward_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Seek forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Bottom Controls: Time Display, Volume Mute + Slider, and Progress Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${formatDuration(displayedPosSeconds)} / ${formatDuration(totalSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("player_time_display")
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (onToggleLike != null) {
                        AnimatedOverlayLikeButton(
                            isLiked = video.isLikedByCurrentUser,
                            likeCount = video.likeCount,
                            onClick = onToggleLike,
                            animationKey = video.id,
                            compact = true,
                            buttonTestTag = "player_overlay_like_button_${video.id}",
                            countTestTag = "player_overlay_like_count_${video.id}",
                            iconTestTag = "player_overlay_like_icon_${video.id}"
                        )
                    }

                    if (onOpenComments != null) {
                        AnimatedOverlayCommentButton(
                            commentCount = video.commentCount,
                            onClick = onOpenComments,
                            compact = true,
                            buttonTestTag = "player_overlay_comment_button_${video.id}",
                            countTestTag = "player_overlay_comment_count_${video.id}",
                            iconTestTag = "player_overlay_comment_icon_${video.id}"
                        )
                    }

                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("player_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted || volumeLevel <= 0.01f) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = strings.volumeLabel,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Slider(
                        value = if (isMuted) 0f else volumeLevel,
                        onValueChange = onChangeVolume,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .width(if (isFullscreen) 96.dp else 68.dp)
                            .height(28.dp)
                            .testTag("player_volume_slider")
                    )
                }

                Slider(
                    value = displayedFraction,
                    onValueChange = { fraction ->
                        isScrubbing = true
                        scrubFraction = fraction.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        val targetMs = (scrubFraction * effectiveDurationMs).toLong()
                            .coerceIn(0L, effectiveDurationMs)
                        isScrubbing = false
                        onSeekTo(targetMs)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .testTag("player_progress_slider")
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExoPlayerSurface(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = exoPlayer
            }
        },
        update = { playerView ->
            if (playerView.player !== exoPlayer) {
                playerView.player = exoPlayer
            }
        },
        onRelease = { playerView ->
            playerView.player = null
        },
        modifier = modifier
    )
}
