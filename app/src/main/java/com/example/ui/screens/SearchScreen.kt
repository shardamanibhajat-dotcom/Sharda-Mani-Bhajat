package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.model.VideoFormat
import com.example.data.model.VideoItem
import com.example.data.model.formatCompactCount
import com.example.data.model.formatDuration
import com.example.data.model.formatUploadDate
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel
import com.example.ui.components.AnimatedOverlayLikeButton
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.MediaImage
import com.example.ui.viewmodel.SearchScope
import com.example.ui.viewmodel.SearchUiState

private val QUICK_SUGGESTED_QUERIES = listOf(
    "Aarav",
    "Priya",
    "Jaipur",
    "Desk",
    "Chai",
    "Storytelling"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    currentUserId: String,
    uiState: SearchUiState,
    currentLanguage: AppLanguage,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String?) -> Unit,
    onClearQuery: () -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (String) -> Unit,
    onCreatorClick: (String) -> Unit,
    onLikeClick: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.searchVideosTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("search_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("search_screen")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(strings.searchVideosPlaceholder) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearQuery,
                                    modifier = Modifier.testTag("search_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSubmitSearch(null)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_screen_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = uiState.scope == SearchScope.ALL,
                            onClick = { onScopeChange(SearchScope.ALL) },
                            label = { Text(strings.searchScopeAll) },
                            modifier = Modifier.testTag("search_scope_all")
                        )
                        FilterChip(
                            selected = uiState.scope == SearchScope.TITLES,
                            onClick = { onScopeChange(SearchScope.TITLES) },
                            label = { Text(strings.searchScopeTitles) },
                            modifier = Modifier.testTag("search_scope_titles")
                        )
                        FilterChip(
                            selected = uiState.scope == SearchScope.CREATORS,
                            onClick = { onScopeChange(SearchScope.CREATORS) },
                            label = { Text(strings.searchScopeCreators) },
                            modifier = Modifier.testTag("search_scope_creators")
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    if (uiState.isSearching) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_loading_indicator")
                        )
                    }
                }

                uiState.errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("search_error_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.testTag("search_retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.retry)
                            }
                        }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_results_list")
                ) {
                    if (uiState.query.isBlank()) {
                        val suggestions = (uiState.recentQueries + QUICK_SUGGESTED_QUERIES)
                            .distinctBy { it.lowercase() }
                            .take(8)
                        item(key = "search_suggestions") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = strings.searchPopularQueries,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    suggestions.forEach { suggestion ->
                                        val isRecent = uiState.recentQueries.any {
                                            it.equals(suggestion, ignoreCase = true)
                                        }
                                        AssistChip(
                                            onClick = { onSubmitSearch(suggestion) },
                                            label = { Text(suggestion) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isRecent) Icons.Default.History else Icons.Default.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("search_suggestion_${suggestion.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.matchingCreators.isNotEmpty()) {
                        item(key = "matching_creators_header") {
                            Text(
                                text = strings.searchMatchingCreators,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        item(key = "matching_creators_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_creators_row")
                            ) {
                                items(uiState.matchingCreators, key = { it.id }) { creator ->
                                    SearchCreatorChipCard(
                                        creator = creator,
                                        subscribersSuffix = strings.subscribersLabel,
                                        onClick = { onCreatorClick(creator.id) }
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.results.isNotEmpty()) {
                        item(key = "videos_section_header") {
                            Text(
                                text = if (uiState.query.isBlank()) {
                                    strings.searchDiscoverVideos
                                } else {
                                    "${strings.searchMatchingVideos} (${uiState.results.size})"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(uiState.results, key = { it.id }) { video ->
                            SearchVideoResultCard(
                                video = video,
                                currentLanguage = currentLanguage,
                                onVideoClick = { onVideoClick(video.id) },
                                onCreatorClick = { onCreatorClick(video.creatorId) },
                                onLikeClick = { onLikeClick(video.id) }
                            )
                        }
                    } else if (!uiState.isSearching && uiState.matchingCreators.isEmpty()) {
                        item(key = "search_empty_state") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 42.dp)
                                    .testTag("search_empty_state")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = strings.noVideosFound,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.searchNoResultsHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.query.isNotBlank() || uiState.scope != SearchScope.ALL) {
                                    Button(
                                        onClick = {
                                            onScopeChange(SearchScope.ALL)
                                            onClearQuery()
                                        },
                                        modifier = Modifier.testTag("search_reset_button")
                                    ) {
                                        Text(strings.clearFiltersLabel)
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

@Composable
private fun SearchCreatorChipCard(
    creator: UserProfile,
    subscribersSuffix: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .width(200.dp)
            .testTag("search_creator_card_${creator.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            CreatorAvatar(
                uri = creator.avatarUri,
                contentDescription = creator.displayName,
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = creator.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "@${creator.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!creator.hideSubscriberCount) {
                    Text(
                        text = "${formatCompactCount(creator.subscriberCount)} $subscribersSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchVideoResultCard(
    video: VideoItem,
    currentLanguage: AppLanguage,
    onVideoClick: () -> Unit,
    onCreatorClick: () -> Unit,
    onLikeClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        onClick = onVideoClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_video_card_${video.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .height(86.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    MediaImage(
                        uri = video.thumbnailUri,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                    ) {
                        Text(
                            text = if (video.format == VideoFormat.SHORT) {
                                "SHORT · ${formatDuration(video.durationSeconds)}"
                            } else {
                                formatDuration(video.durationSeconds)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("search_video_title_${video.id}")
                    )
                    Text(
                        text = "${categoryLabel(video.category, currentLanguage)} · ${formatCompactCount(video.viewCount)} ${strings.viewsLabel} · ${
                            formatUploadDate(video.createdAt, currentLanguage == AppLanguage.HINDI)
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onCreatorClick)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag("search_video_creator_${video.id}")
                ) {
                    CreatorAvatar(
                        uri = video.creatorAvatarUri,
                        contentDescription = video.creatorName,
                        size = 20.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = video.creatorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedOverlayLikeButton(
                    isLiked = video.isLikedByCurrentUser,
                    likeCount = video.likeCount,
                    onClick = onLikeClick,
                    animationKey = "search_${video.id}",
                    compact = true,
                    buttonTestTag = "search_like_button_${video.id}"
                )
            }
        }
    }
}
