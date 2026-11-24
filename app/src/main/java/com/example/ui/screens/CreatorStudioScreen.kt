package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UploadLimits
import com.example.data.model.VideoCategory
import com.example.data.model.VideoItem
import com.example.data.model.formatCompactCount
import com.example.data.model.formatDuration
import com.example.data.model.formatUploadDate
import com.example.data.model.parseTagsInput
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.categoryLabel
import com.example.ui.components.MediaImage
import com.example.ui.components.MonetizationUnavailableBanner
import com.example.ui.viewmodel.CreatorStudioUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatorStudioScreen(
    uiState: CreatorStudioUiState,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    onBack: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onUpdateVideo: (videoId: String, title: String, description: String, category: VideoCategory, thumbnailUri: String?, tags: List<String>?) -> Unit,
    onDeleteVideo: (videoId: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var editingVideo by remember { mutableStateOf<VideoItem?>(null) }
    var deletingVideo by remember { mutableStateOf<VideoItem?>(null) }
    var onPickedEditThumb by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val thumbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickedEditThumb?.invoke(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.creatorStudioTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("studio_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!uiState.isAuthorized) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.accessDenied, style = MaterialTheme.typography.bodyMedium)
                    }
                }
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .testTag("creator_studio_screen")
            ) {
                item(key = "studio_subtitle") {
                    Text(
                        text = strings.creatorStudioSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item(key = "studio_monetization") {
                    MonetizationUnavailableBanner(showDetail = true)
                }

                item(key = "studio_kpis_row1") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StudioKpiCard(
                            icon = Icons.Default.Visibility,
                            label = strings.totalViewsLabel,
                            value = formatCompactCount(uiState.totalViews),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_total_views")
                        )
                        StudioKpiCard(
                            icon = Icons.Default.People,
                            label = strings.subscribersLabel.replaceFirstChar { it.uppercase() },
                            value = formatCompactCount(uiState.subscriberCount),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_total_subscribers")
                        )
                    }
                }

                item(key = "studio_kpis_row2") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StudioKpiCard(
                            icon = Icons.Default.VideoLibrary,
                            label = "${strings.totalUploadsLabel} (${uiState.shortVideoCount} ${strings.shortsCountLabel})",
                            value = uiState.videos.size.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_total_uploads")
                        )
                        StudioKpiCard(
                            icon = Icons.Default.Favorite,
                            label = strings.totalLikesLabel,
                            value = formatCompactCount(uiState.totalLikes),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_total_likes")
                        )
                    }
                }

                item(key = "studio_quota_card") {
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
                                Text(strings.dailyQuotaLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${uiState.dailyUploadCount} / ${UploadLimits.MAX_DAILY_UPLOADS}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = {
                                    (uiState.dailyUploadCount.toFloat() / UploadLimits.MAX_DAILY_UPLOADS).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onOpenUpload,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.uploadTitle)
                            }
                        }
                    }
                }

                if (uiState.recentUploads.isNotEmpty()) {
                    item(key = "studio_recent_uploads") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("studio_recent_uploads_section")
                        ) {
                            Text(
                                text = strings.recentUploadsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.recentUploads, key = { "rec_${it.id}" }) { vid ->
                                    Card(
                                        onClick = { onOpenVideo(vid.id) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.width(196.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            MediaImage(
                                                uri = vid.thumbnailUri,
                                                contentDescription = vid.title,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(92.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = vid.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatCompactCount(vid.viewCount)} ${strings.viewsLabel} · ${formatCompactCount(vid.likeCount)} ${strings.likesLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.videos.isNotEmpty()) {
                    item(key = "studio_performance_overview") {
                        val maxViews = remember(uiState.videos) {
                            uiState.videos.maxOfOrNull { it.viewCount }?.coerceAtLeast(1L) ?: 1L
                        }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_performance_overview")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = strings.performanceOverviewTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                uiState.videos.take(5).forEach { vid ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = vid.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Text(
                                                text = "${formatCompactCount(vid.viewCount)} ${strings.viewsLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { (vid.viewCount.toFloat() / maxViews.toFloat()).coerceIn(0.04f, 1f) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "studio_videos_header") {
                    Text(
                        text = "${strings.uploadedVideosLabel} (${uiState.videos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (uiState.videos.isEmpty()) {
                    item(key = "studio_empty_videos") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.noVideosFound,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.videos, key = { it.id }, contentType = { "studio_video" }) { video ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_video_card_${video.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenVideo(video.id) }
                                ) {
                                    MediaImage(
                                        uri = video.thumbnailUri,
                                        contentDescription = video.title,
                                        modifier = Modifier
                                            .width(112.dp)
                                            .height(68.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${video.format.name} · ${categoryLabel(video.category, currentLanguage)} · ${formatDuration(video.durationSeconds)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${formatCompactCount(video.viewCount)} ${strings.viewsLabel} · ${formatCompactCount(video.likeCount)} ${strings.likesLabel} · ${formatUploadDate(video.createdAt, currentLanguage == AppLanguage.HINDI)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (video.isRemoved) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = if (video.isRemoved) {
                                                    "${strings.statusRemoved}: ${video.removalReason ?: "Moderation"}"
                                                } else {
                                                    strings.statusPublished
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (video.isRemoved) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onTertiaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { editingVideo = video },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("studio_edit_video_${video.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.editLabel)
                                    }
                                    OutlinedButton(
                                        onClick = { deletingVideo = video },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("studio_delete_video_${video.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.deleteVideo)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingVideo?.let { target ->
        var editTitle by remember(target.id) {
            mutableStateOf(
                TextFieldValue(
                    text = target.title,
                    selection = TextRange(target.title.length)
                )
            )
        }
        var editDesc by remember(target.id) { mutableStateOf(target.description) }
        var editCat by remember(target.id) { mutableStateOf(target.category) }
        var editTagsInput by remember(target.id) { mutableStateOf(target.tags.joinToString(", ")) }
        var editThumbUri by remember(target.id) { mutableStateOf(target.thumbnailUri) }
        val selectable = remember { VideoCategory.entries.filter { it != VideoCategory.ALL } }

        Dialog(
            onDismissRequest = { editingVideo = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = strings.editVideoTitle,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MediaImage(
                                uri = editThumbUri,
                                contentDescription = editTitle.text,
                                modifier = Modifier
                                    .width(92.dp)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            OutlinedButton(
                                onClick = {
                                    onPickedEditThumb = { pickedUri -> editThumbUri = pickedUri }
                                    thumbPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("studio_pick_thumbnail_button")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.changeThumbnail)
                            }
                        }

                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text(strings.videoTitleLabel) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_edit_title_input")
                        )
                        OutlinedTextField(
                            value = editDesc,
                            onValueChange = { editDesc = it },
                            label = { Text(strings.videoDescriptionLabel) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editTagsInput,
                            onValueChange = { if (it.length <= 160) editTagsInput = it },
                            label = { Text(strings.videoTagsLabel) },
                            placeholder = { Text(strings.videoTagsHint) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_edit_tags_input")
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            selectable.forEach { cat ->
                                FilterChip(
                                    selected = editCat == cat,
                                    onClick = { editCat = cat },
                                    label = { Text(categoryLabel(cat, currentLanguage)) }
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { editingVideo = null }) {
                            Text(strings.cancel)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onUpdateVideo(
                                    target.id,
                                    editTitle.text,
                                    editDesc,
                                    editCat,
                                    editThumbUri,
                                    parseTagsInput(editTagsInput)
                                )
                                editingVideo = null
                            },
                            modifier = Modifier.testTag("studio_save_edit_button")
                        ) {
                            Text(strings.saveProfile)
                        }
                    }
                }
            }
        }
    }

    deletingVideo?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingVideo = null },
            title = { Text(strings.deleteVideo) },
            text = { Text("${strings.confirmDeleteVideo}\n\n\"${target.title}\"") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVideo(target.id)
                        deletingVideo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("studio_confirm_delete_button")
                ) {
                    Text(strings.deleteVideo)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVideo = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun StudioKpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
