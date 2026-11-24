package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.VideoComment
import com.example.data.model.VideoItem
import com.example.data.model.formatUploadDate
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import kotlinx.coroutines.flow.Flow

private const val MAX_COMMENT_LENGTH = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCommentsBottomSheet(
    video: VideoItem,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    observeComments: (String) -> Flow<List<VideoComment>>,
    onPostComment: (videoId: String, text: String, parentCommentId: String?, onSuccess: () -> Unit) -> Unit,
    onCreatorClick: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val commentsFlow = remember(video.id) { observeComments(video.id) }
    val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val topLevelComments = remember(comments) {
        comments.filter { it.parentCommentId == null }
    }
    val repliesByParent = remember(comments) {
        comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId!! }
    }

    var commentDraft by remember(video.id) { mutableStateOf("") }
    var replyingTo by remember(video.id) { mutableStateOf<VideoComment?>(null) }
    var isSubmitting by remember(video.id) { mutableStateOf(false) }

    val trimmedLength = commentDraft.trim().length
    val isOverLimit = trimmedLength > MAX_COMMENT_LENGTH
    val canSubmit = trimmedLength in 1..MAX_COMMENT_LENGTH && !isSubmitting

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("video_comments_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 280.dp, max = 560.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${strings.commentsLabel} (${comments.size.coerceAtLeast(video.commentCount.toInt())})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("comments_sheet_title")
                    )
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("comments_sheet_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.cancel
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            replyingTo?.let { target ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("comments_sheet_replying_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${strings.replyingTo} @${target.username}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { replyingTo = null },
                            modifier = Modifier.testTag("comments_sheet_cancel_reply")
                        ) {
                            Text(strings.cancel)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    placeholder = {
                        Text(
                            if (replyingTo != null) {
                                "${strings.replyingTo} @${replyingTo!!.username}..."
                            } else {
                                strings.addCommentHint
                            }
                        )
                    },
                    isError = isOverLimit,
                    supportingText = {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isOverLimit) strings.commentTooLongError else "",
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "$trimmedLength / $MAX_COMMENT_LENGTH",
                                modifier = Modifier.testTag("comments_sheet_char_count")
                            )
                        }
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comments_sheet_input")
                )

                Button(
                    enabled = canSubmit,
                    onClick = {
                        val submittedDraft = commentDraft
                        val submittedParentId = replyingTo?.id
                        isSubmitting = true
                        onPostComment(video.id, submittedDraft, submittedParentId) {
                            if (commentDraft == submittedDraft) {
                                commentDraft = ""
                            }
                            if (replyingTo?.id == submittedParentId) {
                                replyingTo = null
                            }
                            isSubmitting = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("comments_sheet_send")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = strings.postComment
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (topLevelComments.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag("comments_sheet_empty")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = strings.noCommentsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .testTag("comments_sheet_list")
                ) {
                    items(topLevelComments, key = { it.id }) { comment ->
                        val replies = repliesByParent[comment.id].orEmpty()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("comments_sheet_item_${comment.id}")
                        ) {
                            SheetCommentRow(
                                comment = comment,
                                currentLanguage = currentLanguage,
                                replyLabel = strings.reply,
                                isReply = false,
                                onCreatorClick = { onCreatorClick(comment.userId) },
                                onReplyClick = { replyingTo = comment }
                            )
                            replies.forEach { replyItem ->
                                SheetCommentRow(
                                    comment = replyItem,
                                    currentLanguage = currentLanguage,
                                    replyLabel = strings.reply,
                                    isReply = true,
                                    onCreatorClick = { onCreatorClick(replyItem.userId) },
                                    onReplyClick = { replyingTo = comment }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCommentRow(
    comment: VideoComment,
    currentLanguage: AppLanguage,
    replyLabel: String,
    isReply: Boolean,
    onCreatorClick: () -> Unit,
    onReplyClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isReply) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 28.dp else 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            CreatorAvatar(
                uri = comment.avatarUri,
                contentDescription = comment.username,
                size = if (isReply) 28.dp else 34.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCreatorClick)
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onCreatorClick)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(
                onClick = onReplyClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("comments_sheet_reply_${comment.id}")
            ) {
                Text(
                    text = replyLabel,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
