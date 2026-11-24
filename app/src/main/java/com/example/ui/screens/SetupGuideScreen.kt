package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.ui.components.MonetizationUnavailableBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(onBack: () -> Unit) {
    val strings = LocalAppStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.setupGuideTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                MonetizationUnavailableBanner(showDetail = true)

                GuideSectionCard(
                    title = "Active Cloud & Media Features (Firebase Auth + Firestore + Storage + Media3)",
                    isReady = true,
                    bullets = listOf(
                        "Firebase Authentication with Google Sign-In (Credential Manager) and Forgot Password email reset.",
                        "Cloud Firestore persistence for Creator Profiles, Videos (Shorts & Long), Likes, Comments/Replies, Subscriptions, Genuine 24h Views, Notifications, Watch History, Playlists, and Admin Moderation.",
                        "Firebase Storage integration via FirebaseMediaStorage uploading videos & thumbnails directly to cloud storage and saving download URLs to Firestore.",
                        "Zero-trust firestore.rules and storage.rules enforcing owner isolation, anti-fake-view cooldowns, duration/file limits, and disabled monetization.",
                        "Tuned Media3 ExoPlayer with fast-start LoadControl, 64MB HTTP LRU cache, buffering spinner, playback error overlay & retry.",
                        "Quick Video Upload Bottom Sheet + full Upload Studio, Shorts swipe feed, Creator Dashboard, and real-time Notifications."
                    )
                )

                GuideSectionCard(
                    title = "Firebase Console Verification Steps (Storage Bucket & Optional Email Auth)",
                    isReady = false,
                    bullets = listOf(
                        "1. App package com.aistudio.creatorpaisa.k7m9px and SHA-1/SHA-256 certificates are provisioned in Firebase project gen-lang-client-0192371297.",
                        "2. Ensure Firebase Storage is enabled for bucket gen-lang-client-0192371297.firebasestorage.app in Firebase Console and deploy storage.rules.",
                        "3. Enable Email/Password provider in Firebase Authentication Console if you want direct email/password sign-in in addition to Google Sign-In and password reset.",
                        "4. Optional: Enable Firebase App Check (Play Integrity) in Firebase Console for production release protection."
                    )
                )
            }
        }
    }
}

@Composable
private fun GuideSectionCard(
    title: String,
    isReady: Boolean,
    bullets: List<String>
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = if (isReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}
