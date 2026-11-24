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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppErrorLog
import com.example.data.model.AppStatistics
import com.example.data.model.ContentReport
import com.example.data.model.ReportStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoItem
import com.example.localization.LocalAppStrings
import com.example.ui.components.CreatorAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    currentUser: UserProfile,
    statistics: AppStatistics?,
    videos: List<VideoItem>,
    users: List<UserProfile>,
    reports: List<ContentReport>,
    errorLogs: List<AppErrorLog>,
    onBack: () -> Unit,
    onRemoveVideo: (videoId: String, reason: String) -> Unit,
    onRestoreVideo: (videoId: String) -> Unit,
    onToggleApproval: (userId: String, approved: Boolean) -> Unit,
    onToggleSuspend: (userId: String, suspended: Boolean) -> Unit,
    onResolveReport: (reportId: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.adminPanelTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentUser.role != UserRole.ADMIN) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .testTag("admin_access_denied")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.accessDenied,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            return@Scaffold
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .testTag("admin_screen")
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.adminStatsTab) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.adminVideosTab) },
                        modifier = Modifier.testTag("admin_tab_videos")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(strings.adminUsersTab) },
                        modifier = Modifier.testTag("admin_tab_users")
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text(strings.adminErrorsTab) },
                        modifier = Modifier.testTag("admin_tab_errors")
                    )
                }

                when (selectedTab) {
                    0 -> AdminOverviewTab(statistics = statistics, reports = reports, onResolveReport = onResolveReport)
                    1 -> AdminVideosTab(videos = videos, onRemoveVideo = onRemoveVideo, onRestoreVideo = onRestoreVideo)
                    2 -> AdminUsersTab(
                        adminId = currentUser.id,
                        users = users,
                        onToggleApproval = onToggleApproval,
                        onToggleSuspend = onToggleSuspend
                    )
                    3 -> AdminErrorsTab(errorLogs = errorLogs)
                }
            }
        }
    }
}

@Composable
private fun AdminOverviewTab(
    statistics: AppStatistics?,
    reports: List<ContentReport>,
    onResolveReport: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (statistics != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminStatCard("Users", statistics.totalUsers.toString(), Modifier.weight(1f))
                    AdminStatCard("Approved", statistics.approvedCreators.toString(), Modifier.weight(1f))
                    AdminStatCard("Videos", "${statistics.activeVideos}/${statistics.totalVideos}", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminStatCard("Total Views", statistics.totalViews.toString(), Modifier.weight(1f))
                    AdminStatCard("Open Reports", statistics.openReports.toString(), Modifier.weight(1f))
                    AdminStatCard("Error Logs", statistics.totalErrors.toString(), Modifier.weight(1f))
                }
            }
        }

        item {
            Text(
                text = "Content Reports (${reports.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(reports, key = { it.id }) { report ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(report.videoTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(report.reason, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Status: ${report.status.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (report.status == ReportStatus.OPEN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (report.status == ReportStatus.OPEN) {
                        OutlinedButton(onClick = { onResolveReport(report.id) }) {
                            Text(strings.resolveReport)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminVideosTab(
    videos: List<VideoItem>,
    onRemoveVideo: (String, String) -> Unit,
    onRestoreVideo: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { it.id }) { video ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${video.creatorName} · ${video.format.name} · Reports: ${video.reportCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (video.isRemoved) {
                            Text(
                                text = "REMOVED: ${video.removalReason ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (video.isRemoved) {
                        OutlinedButton(
                            onClick = { onRestoreVideo(video.id) },
                            modifier = Modifier.testTag("admin_restore_video_${video.id}")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.restoreVideo)
                        }
                    } else {
                        Button(
                            onClick = { onRemoveVideo(video.id, "Violates community guidelines") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("admin_remove_video_${video.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.removeVideo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    adminId: String,
    users: List<UserProfile>,
    onToggleApproval: (String, Boolean) -> Unit,
    onToggleSuspend: (String, Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(users, key = { it.id }) { user ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CreatorAvatar(uri = user.avatarUri, contentDescription = user.username, size = 40.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${user.role.name} · ${user.emailMasked}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (user.id != adminId) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onToggleApproval(user.id, !user.isApprovedUploader) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_toggle_approval_${user.id}")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (user.isApprovedUploader) strings.revokeUploader else strings.approveUploader)
                            }
                            OutlinedButton(
                                onClick = { onToggleSuspend(user.id, !user.isSuspended) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_toggle_suspend_${user.id}")
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (user.isSuspended) strings.activateUser else strings.suspendUser)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminErrorsTab(errorLogs: List<AppErrorLog>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(errorLogs, key = { it.id }) { log ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = log.category.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = log.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
