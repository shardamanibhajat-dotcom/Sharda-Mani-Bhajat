package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.formatCompactCount
import com.example.data.model.formatDuration
import com.example.data.model.formatUploadDate
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel

@Composable
fun CreatorPaisaLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Image(
        painter = painterResource(id = R.drawable.img_creator_paisa_logo),
        contentDescription = "Creator Paisa Logo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
    )
}

@Composable
fun LanguageTogglePill(
    currentLanguage: AppLanguage,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("language_toggle_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Switch Language",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (currentLanguage == AppLanguage.ENGLISH) "EN/हि" else "हि/EN",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun NotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.testTag("open_notifications_button")
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = strings.notificationsTitle,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        if (unreadCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("notifications_unread_badge")
            ) {
                Text(if (unreadCount > 99) "99+" else unreadCount.toString())
            }
        }
    }
}

@Composable
fun MonetizationUnavailableBanner(
    modifier: Modifier = Modifier,
    showDetail: Boolean = false
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("monetization_unavailable_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = strings.monetizationNotice,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                if (showDetail) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.monetizationDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

private object AppImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("cp_image_cache"))
                        .maxSizeBytes(50L * 1024L * 1024L)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(150)
                .build()
                .also { instance = it }
        }
    }
}

private val ShortCardGradientBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.15f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.88f)
    )
)

private val LongCardGradientBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.48f))
)

@Composable
fun MediaImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val drawableRes = when (uri) {
        "drawable://img_creator_paisa_logo" -> R.drawable.img_creator_paisa_logo
        "drawable://img_banner_studio" -> R.drawable.img_banner_studio
        "drawable://img_thumb_tech" -> R.drawable.img_thumb_tech
        "drawable://img_thumb_food" -> R.drawable.img_thumb_food
        "drawable://img_short_travel" -> R.drawable.img_short_travel
        else -> null
    }
    if (drawableRes != null) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else if (uri.isNotBlank()) {
        val imageLoader = remember(context) { AppImageLoader.get(context) }
        val request = remember(context, uri) {
            ImageRequest.Builder(context)
                .data(uri)
                .crossfade(150)
                .build()
        }
        val fallbackPainter = painterResource(id = R.drawable.img_banner_studio)
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = contentScale,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_banner_studio),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@Composable
fun CreatorAvatar(
    uri: String,
    contentDescription: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    MediaImage(
        uri = uri,
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
fun CreatorProfileOverlayBadge(
    creatorName: String,
    creatorAvatarUri: String,
    isFollowing: Boolean,
    showFollowButton: Boolean,
    badgeTestTag: String,
    creatorTestTag: String,
    followTestTag: String,
    onCreatorClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier.testTag(badgeTestTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCreatorClick)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag(creatorTestTag)
            ) {
                CreatorAvatar(
                    uri = creatorAvatarUri,
                    contentDescription = creatorName,
                    size = 22.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = creatorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp)
                )
            }

            if (showFollowButton) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onFollowClick)
                        .testTag(followTestTag)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isFollowing) {
                            Color.White.copy(alpha = 0.24f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ) {
                        Text(
                            text = if (isFollowing) strings.following else strings.follow,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedHeartIcon(
    isLiked: Boolean,
    modifier: Modifier = Modifier,
    animationKey: Any? = Unit,
    iconSize: Dp = 18.dp,
    likedTint: Color = MaterialTheme.colorScheme.primary,
    unlikedTint: Color = Color.White,
    contentDescription: String? = null
) {
    val scale = remember(animationKey) { Animatable(1f) }
    var previousLiked by remember(animationKey) { mutableStateOf(isLiked) }

    LaunchedEffect(animationKey, isLiked) {
        if (isLiked != previousLiked) {
            previousLiked = isLiked
            if (isLiked) {
                scale.snapTo(0.84f)
                scale.animateTo(
                    targetValue = 1.36f,
                    animationSpec = tween(durationMillis = 120)
                )
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            } else {
                scale.snapTo(1.06f)
                scale.animateTo(
                    targetValue = 0.85f,
                    animationSpec = tween(durationMillis = 90)
                )
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    val tint by animateColorAsState(
        targetValue = if (isLiked) likedTint else unlikedTint,
        animationSpec = tween(durationMillis = 200),
        label = "animated_heart_tint"
    )

    Icon(
        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(iconSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    )
}

@Composable
fun AnimatedOverlayLikeButton(
    isLiked: Boolean,
    likeCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    animationKey: Any? = Unit,
    compact: Boolean = false,
    buttonTestTag: String? = null,
    countTestTag: String? = null,
    iconTestTag: String? = null
) {
    val strings = LocalAppStrings.current
    val pillTint by animateColorAsState(
        targetValue = if (isLiked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "overlay_like_pill_tint"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isLiked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        } else {
            Color.White.copy(alpha = 0.20f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "overlay_like_border"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(
                onClickLabel = strings.like,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {}
            .then(if (buttonTestTag != null) Modifier.testTag(buttonTestTag) else Modifier)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.74f),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                modifier = Modifier
                    .background(pillTint)
                    .padding(
                        horizontal = if (compact) 8.dp else 10.dp,
                        vertical = if (compact) 4.dp else 5.dp
                    )
            ) {
                AnimatedHeartIcon(
                    isLiked = isLiked,
                    animationKey = animationKey,
                    iconSize = if (compact) 15.dp else 17.dp,
                    likedTint = MaterialTheme.colorScheme.primary,
                    unlikedTint = Color.White,
                    contentDescription = strings.like,
                    modifier = if (iconTestTag != null) Modifier.testTag(iconTestTag) else Modifier
                )
                AnimatedContent(
                    targetState = formatCompactCount(likeCount),
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                    },
                    label = "overlay_like_count"
                ) { formattedCount ->
                    Text(
                        text = formattedCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = if (countTestTag != null) Modifier.testTag(countTestTag) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedOverlayCommentButton(
    commentCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    buttonTestTag: String? = null,
    countTestTag: String? = null,
    iconTestTag: String? = null
) {
    val strings = LocalAppStrings.current
    val scale = remember { Animatable(1f) }
    var previousCount by remember { mutableStateOf(commentCount) }

    LaunchedEffect(commentCount) {
        if (commentCount > previousCount) {
            scale.snapTo(0.88f)
            scale.animateTo(
                targetValue = 1.24f,
                animationSpec = tween(durationMillis = 120)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        previousCount = commentCount
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(
                onClickLabel = strings.commentsLabel,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {}
            .then(if (buttonTestTag != null) Modifier.testTag(buttonTestTag) else Modifier)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.74f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                modifier = Modifier.padding(
                    horizontal = if (compact) 8.dp else 10.dp,
                    vertical = if (compact) 4.dp else 5.dp
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = strings.commentsLabel,
                    tint = Color.White,
                    modifier = Modifier
                        .size(if (compact) 15.dp else 17.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .then(if (iconTestTag != null) Modifier.testTag(iconTestTag) else Modifier)
                )
                AnimatedContent(
                    targetState = formatCompactCount(commentCount),
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                    },
                    label = "overlay_comment_count"
                ) { formattedCount ->
                    Text(
                        text = formattedCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = if (countTestTag != null) Modifier.testTag(countTestTag) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
fun ShortPreviewCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFollowingCreator: Boolean = false,
    showFollowButton: Boolean = true,
    onCreatorClick: () -> Unit = {},
    onFollowClick: () -> Unit = {},
    onLikeClick: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .width(156.dp)
            .height(256.dp)
            .testTag("short_card_${video.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MediaImage(
                uri = video.thumbnailUri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ShortCardGradientBrush)
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatDuration(video.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            if (onLikeClick != null) {
                AnimatedOverlayLikeButton(
                    isLiked = video.isLikedByCurrentUser,
                    likeCount = video.likeCount,
                    onClick = onLikeClick,
                    animationKey = video.id,
                    compact = true,
                    buttonTestTag = "short_preview_like_button_${video.id}",
                    countTestTag = "short_preview_like_count_${video.id}",
                    iconTestTag = "short_preview_like_icon_${video.id}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.68f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("short_preview_profile_badge_${video.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onCreatorClick)
                        ) {
                            CreatorAvatar(
                                uri = video.creatorAvatarUri,
                                contentDescription = video.creatorName,
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = video.creatorName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (showFollowButton) {
                            Surface(
                                onClick = onFollowClick,
                                shape = RoundedCornerShape(50),
                                color = if (isFollowingCreator) {
                                    Color.White.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.testTag("short_preview_follow_button_${video.id}")
                            ) {
                                Text(
                                    text = if (isFollowingCreator) strings.following else strings.follow,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatCompactCount(video.viewCount)} ${strings.viewsLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LongVideoCard(
    video: VideoItem,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    onVideoClick: () -> Unit,
    onCreatorClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFollowingCreator: Boolean = false,
    showFollowButton: Boolean = true,
    onFollowClick: () -> Unit = {},
    inlinePlayer: ExoPlayer? = null,
    isInlineActive: Boolean = false,
    hasRenderedFirstFrame: Boolean = false,
    isInlineBuffering: Boolean = false,
    isMuted: Boolean = true,
    playbackProgress: Float = 0f,
    onToggleMute: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onCommentsClick: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isInlineActive) 1.5.dp else 1.dp,
            color = if (isInlineActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isInlineActive) 5.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_card_${video.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (isInlineActive && inlinePlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                player = inlinePlayer
                            }
                        },
                        update = { pv ->
                            if (pv.player !== inlinePlayer) pv.player = inlinePlayer
                        },
                        onRelease = { pv ->
                            pv.player = null
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("inline_feed_player_${video.id}")
                    )
                }

                if (!isInlineActive || !hasRenderedFirstFrame) {
                    MediaImage(
                        uri = video.thumbnailUri,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LongCardGradientBrush)
                        .clickable(onClick = onVideoClick)
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = categoryLabel(video.category, currentLanguage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CreatorProfileOverlayBadge(
                        creatorName = video.creatorName,
                        creatorAvatarUri = video.creatorAvatarUri,
                        isFollowing = isFollowingCreator,
                        showFollowButton = showFollowButton,
                        badgeTestTag = "video_profile_badge_${video.id}",
                        creatorTestTag = "video_profile_creator_${video.id}",
                        followTestTag = "video_follow_button_${video.id}",
                        onCreatorClick = onCreatorClick,
                        onFollowClick = onFollowClick,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                if (isInlineActive && isInlineBuffering) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                    )
                } else if (!isInlineActive || !hasRenderedFirstFrame) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.68f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play ${video.title}",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                if (isInlineActive && inlinePlayer != null) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .testTag("feed_inline_mute_${video.id}")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute feed preview" else "Mute feed preview",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    AnimatedOverlayLikeButton(
                        isLiked = video.isLikedByCurrentUser,
                        likeCount = video.likeCount,
                        onClick = onLikeClick,
                        animationKey = video.id,
                        buttonTestTag = "video_overlay_like_button_${video.id}",
                        countTestTag = "video_overlay_like_count_${video.id}",
                        iconTestTag = "video_overlay_like_icon_${video.id}"
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier.testTag("video_duration_${video.id}")
                    ) {
                        Text(
                            text = if (video.format == VideoFormat.SHORT) {
                                "SHORT · ${formatDuration(video.durationSeconds)}"
                            } else {
                                formatDuration(video.durationSeconds)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isInlineActive && playbackProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { playbackProgress.coerceIn(0f, 1f) },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CreatorAvatar(
                        uri = video.creatorAvatarUri,
                        contentDescription = video.creatorName,
                        size = 42.dp,
                        modifier = Modifier.clickable(onClick = onCreatorClick)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onVideoClick)
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("video_meta_${video.id}")
                            )
                        }
                        if (video.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = video.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("video_description_${video.id}")
                            )
                        }
                        if (video.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("video_tags_row_${video.id}")
                            ) {
                                items(video.tags.take(4), key = { "${video.id}_$it" }) { tag ->
                                    Surface(
                                        onClick = { onTagClick(tag) },
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.testTag("video_tag_${video.id}_$tag")
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                .testTag("like_button_${video.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                AnimatedHeartIcon(
                                    isLiked = video.isLikedByCurrentUser,
                                    animationKey = video.id,
                                    iconSize = 16.dp,
                                    likedTint = MaterialTheme.colorScheme.primary,
                                    unlikedTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    contentDescription = strings.like
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatCompactCount(video.likeCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            onClick = { (onCommentsClick ?: onVideoClick)() },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("comment_button_${video.id}")
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("comment_count_${video.id}")
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier.testTag("share_button_${video.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = strings.share,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onReportClick,
                            modifier = Modifier.testTag("report_button_${video.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = strings.report,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

fun shareVideo(context: Context, video: VideoItem) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, video.title)
        putExtra(
            Intent.EXTRA_TEXT,
            "Watch \"${video.title}\" by ${video.creatorName} on Creator Paisa!\n${video.videoUri}"
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share video via"))
}
