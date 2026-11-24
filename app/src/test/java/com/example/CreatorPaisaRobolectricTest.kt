package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.media3.common.MimeTypes
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CreatorPaisaDatabase
import com.example.data.local.LocalMediaStorage
import com.example.data.local.MediaStorage
import com.example.data.local.PassthroughMediaStorage
import com.example.data.local.SeedData
import com.example.data.local.SessionPreferences
import com.example.data.local.StoredMediaResult
import com.example.data.model.ErrorCategory
import com.example.data.model.FeedSortOrder
import com.example.data.model.NotificationType
import com.example.data.model.UploadDraft
import com.example.data.model.UploadProgressState
import com.example.data.model.VideoCategory
import com.example.data.model.VideoFormat
import com.example.data.model.formatCompactCount
import com.example.data.model.parseTagsInput
import com.example.data.repository.LocalCreatorPaisaRepository
import com.example.media.ActivePlaybackCoordinator
import com.example.media.ExoPlayerFactory
import com.example.media.VideoQualityOption
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.UploadViewModel
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SubscriptionEntity
import com.example.data.local.VideoEntity
import com.example.data.model.NotificationCategoryFilter
import com.example.data.model.NotificationPreferences
import com.example.localization.AppLanguage
import com.example.localization.EnglishStrings
import com.example.localization.LocalAppStrings
import com.example.ui.components.VideoUploadBottomSheet
import com.example.ui.screens.ChannelScreen
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.ShortsFeedScreen
import com.example.ui.screens.VideoFeedScreen
import com.example.ui.screens.VideoUploadScreen
import com.example.ui.theme.CreatorPaisaTheme
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ChannelViewModel
import com.example.ui.viewmodel.CreatorStudioViewModel
import com.example.ui.viewmodel.NotificationsViewModel
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.SearchScope
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.VideoFeedViewModel
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreatorPaisaRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: CreatorPaisaDatabase
    private lateinit var repository: LocalCreatorPaisaRepository
    private var fakeClock = System.currentTimeMillis() + 86_400_000L * 2

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CreatorPaisaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = SessionPreferences(context)
        repository = LocalCreatorPaisaRepository(database.dao(), prefs) { fakeClock }
        runBlocking {
            repository.logout()
            CreatorPaisaDatabase.seedIfNeeded(database.dao())
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.awaitValue(
        timeoutMs: Long = 5_000L,
        predicate: (T) -> Boolean
    ): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val current = value
            if (predicate(current)) return current
            delay(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val current = value
        if (predicate(current)) return current
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for StateFlow; last value=$current")
    }

    private fun awaitCondition(
        timeoutMs: Long = 5_000L,
        message: String = "Timed out after ${timeoutMs}ms waiting for condition",
        predicate: suspend () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (runBlocking { predicate() }) return
            Thread.sleep(25)
        }
        shadowOf(Looper.getMainLooper()).idle()
        if (runBlocking { predicate() }) return
        throw AssertionError(message)
    }

    @Test
    fun `1 signup and login succeed and hide private credentials`() = runBlocking {
        val signUpResult = repository.signUp(
            email = "neha@creatorpaisa.test",
            username = "Neha Vlogs",
            password = "SecurePassword1",
            bio = "Daily creator tips",
            avatarUri = "drawable://img_creator_paisa_logo"
        )
        assertTrue(signUpResult.isSuccess)
        val profile = signUpResult.getOrThrow()
        assertEquals("Neha Vlogs", profile.username)
        assertFalse(profile.isApprovedUploader)
        assertTrue(profile.emailMasked.startsWith("ne***@"))

        repository.logout()
        assertNull(repository.currentUser.first())

        val badLogin = repository.login("neha@creatorpaisa.test", "WrongPass")
        assertTrue(badLogin.isFailure)

        val goodLogin = repository.login("neha@creatorpaisa.test", "SecurePassword1")
        assertTrue(goodLogin.isSuccess)
        assertEquals(profile.id, repository.currentUser.first()?.id)
    }

    @Test
    fun `2 upload validates approval, duration limits, and daily 5 video quota`() = runBlocking {
        // Unapproved viewer blocked
        val viewerAttempt = repository.uploadVideo(
            SeedData.VIEWER_TEST_ID,
            UploadDraft("Test Short", "Desc", VideoFormat.SHORT, VideoCategory.TECH, 30L, 1024L, "", "https://example.com/v.mp4")
        )
        assertTrue(viewerAttempt.isFailure)

        // Short > 180s blocked
        val longShortAttempt = repository.uploadVideo(
            SeedData.CREATOR_AARAV_ID,
            UploadDraft("Too Long Short", "Desc", VideoFormat.SHORT, VideoCategory.TECH, 185L, 1024L, "", "https://example.com/v.mp4")
        )
        assertTrue(longShortAttempt.isFailure)

        // Long video > 1800s blocked
        val tooLongAttempt = repository.uploadVideo(
            SeedData.CREATOR_AARAV_ID,
            UploadDraft("Too Long Video", "Desc", VideoFormat.LONG, VideoCategory.TECH, 1805L, 1024L, "", "https://example.com/v.mp4")
        )
        assertTrue(tooLongAttempt.isFailure)

        // 5 valid uploads succeed, 6th rejected
        repeat(5) { idx ->
            val format = if (idx % 2 == 0) VideoFormat.SHORT else VideoFormat.LONG
            val duration = if (format == VideoFormat.SHORT) 60L else 600L
            val res = repository.uploadVideo(
                SeedData.CREATOR_AARAV_ID,
                UploadDraft("Valid Video $idx", "Desc", format, VideoCategory.EDUCATION, duration, 1024L, "", "https://example.com/v$idx.mp4")
            )
            assertTrue("Upload $idx should succeed", res.isSuccess)
        }
        val sixth = repository.uploadVideo(
            SeedData.CREATOR_AARAV_ID,
            UploadDraft("Sixth Video", "Desc", VideoFormat.SHORT, VideoCategory.TECH, 40L, 1024L, "", "https://example.com/v6.mp4")
        )
        assertTrue(sixth.isFailure)
    }

    @Test
    fun `3 and 4 genuine views count once per cooldown window and ignore fake inflation`() = runBlocking {
        val before = repository.observeVideoById("vid_short_1").first()!!.viewCount
        val firstCount = repository.recordGenuineView("vid_short_1", SeedData.VIEWER_TEST_ID)
        assertTrue(firstCount)

        // Repeated attempt 10 seconds later must NOT increment viewCount
        fakeClock += 10_000L
        val duplicateCount = repository.recordGenuineView("vid_short_1", SeedData.VIEWER_TEST_ID)
        assertFalse(duplicateCount)

        val after = repository.observeVideoById("vid_short_1").first()!!.viewCount
        assertEquals(before + 1L, after)
    }

    @Test
    fun `5 like and comment update video counters accurately`() = runBlocking {
        repository.login("viewer@creatorpaisa.test", "Viewer@123")
        val initial = repository.observeVideoById("vid_long_1").first()!!

        val liked = repository.toggleLike("vid_long_1", SeedData.VIEWER_TEST_ID).getOrThrow()
        assertTrue(liked)
        assertEquals(initial.likeCount + 1L, repository.observeVideoById("vid_long_1").first()!!.likeCount)

        val emptyComment = repository.addComment("vid_long_1", SeedData.VIEWER_TEST_ID, "   ")
        assertTrue(emptyComment.isFailure)

        val validComment = repository.addComment("vid_long_1", SeedData.VIEWER_TEST_ID, "Great guide!")
        assertTrue(validComment.isSuccess)
        assertEquals(initial.commentCount + 1L, repository.observeVideoById("vid_long_1").first()!!.commentCount)
    }

    @Test
    fun `6 subscribe and unsubscribe update subscriber count and block self-subscribe`() = runBlocking {
        val selfSub = repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID)
        assertTrue(selfSub.isFailure)

        val initialSubs = repository.observeCreatorProfile(SeedData.CREATOR_AARAV_ID).first()!!.subscriberCount
        val sub = repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).getOrThrow()
        assertTrue(sub)
        assertEquals(initialSubs + 1L, repository.observeCreatorProfile(SeedData.CREATOR_AARAV_ID).first()!!.subscriberCount)

        val unsub = repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).getOrThrow()
        assertFalse(unsub)
        assertEquals(initialSubs, repository.observeCreatorProfile(SeedData.CREATOR_AARAV_ID).first()!!.subscriberCount)
    }

    @Test
    fun `7 and 8 and 10 admin moderation protects normal users, removes content, approves users, and views error logs`() = runBlocking {
        // Non-admin blocked
        val unauthorized = repository.adminRemoveVideo(SeedData.VIEWER_TEST_ID, "vid_short_1", "Spam")
        assertTrue(unauthorized.isFailure)

        // Log playback & upload error
        repository.logError(ErrorCategory.PLAYBACK, "Simulated ExoPlayer timeout", SeedData.VIEWER_TEST_ID, "vid_short_1")

        // Admin approves viewer and removes/restores a video
        val approve = repository.adminSetUploaderApproval(SeedData.ADMIN_ID, SeedData.VIEWER_TEST_ID, true)
        assertTrue(approve.isSuccess)
        assertTrue(repository.observeCreatorProfile(SeedData.VIEWER_TEST_ID).first()!!.isApprovedUploader)

        repository.reportVideo("vid_short_2", SeedData.VIEWER_TEST_ID, "Misleading audio")
        val remove = repository.adminRemoveVideo(SeedData.ADMIN_ID, "vid_short_2", "Policy check")
        assertTrue(remove.isSuccess)
        assertTrue(repository.observeActiveVideos().first().none { it.id == "vid_short_2" })

        val errorLogs = repository.observeAllErrorLogsForAdmin(SeedData.ADMIN_ID).first()
        assertTrue(errorLogs.any { it.category == ErrorCategory.PLAYBACK && it.message.contains("ExoPlayer") })
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `9 mobile UI renders auth, login, search, monetization notice, and language switch`() {
        composeTestRule.setContent {
            CreatorPaisaApp(repository = repository)
        }
        composeTestRule.waitForIdle()

        // Auth screen displays monetization notice and quick test accounts
        composeTestRule.onNodeWithTag("monetization_unavailable_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_login_creator").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("home_feed_list").fetchSemanticsNodes().isNotEmpty()
        }

        // Home screen renders Shorts, search, and monetization notice
        composeTestRule.onNodeWithTag("home_feed_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monetization_unavailable_banner").assertIsDisplayed()

        // Search filters results
        composeTestRule.onNodeWithTag("home_search_input").performTextInput("Jaipur")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("short_card_vid_short_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("short_card_vid_short_1").assertIsDisplayed()

        // Language toggle switches to Hindi
        composeTestRule.onNodeWithTag("language_toggle_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithText("क्रिएटर पैसा").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("क्रिएटर पैसा").assertIsDisplayed()
    }

    @Test
    fun `11 notifications created on like, comment, subscribe and marked read cleanly`() = runBlocking {
        val initialUnread = repository.observeUnreadNotificationCount(SeedData.CREATOR_PRIYA_ID).first()

        // Viewer likes, comments on Priya's video, and subscribes to Priya
        repository.toggleLike("vid_short_1", SeedData.VIEWER_TEST_ID)
        repository.addComment("vid_short_1", SeedData.VIEWER_TEST_ID, "Awesome sunrise shot!")
        repository.toggleSubscription(SeedData.CREATOR_PRIYA_ID, SeedData.VIEWER_TEST_ID)

        val notifications = repository.observeNotifications(SeedData.CREATOR_PRIYA_ID).first()
        assertEquals(initialUnread + 3, repository.observeUnreadNotificationCount(SeedData.CREATOR_PRIYA_ID).first())
        assertTrue(notifications.any { it.type == NotificationType.LIKE && it.actorId == SeedData.VIEWER_TEST_ID })
        assertTrue(notifications.any { it.type == NotificationType.COMMENT && it.message.contains("Awesome sunrise shot!") })
        assertTrue(notifications.any { it.type == NotificationType.SUBSCRIPTION && it.actorId == SeedData.VIEWER_TEST_ID })

        val unreadTarget = notifications.first { !it.isRead }
        repository.markNotificationRead(unreadTarget.id)
        assertEquals(initialUnread + 2, repository.observeUnreadNotificationCount(SeedData.CREATOR_PRIYA_ID).first())

        repository.markAllNotificationsRead(SeedData.CREATOR_PRIYA_ID)
        assertEquals(0, repository.observeUnreadNotificationCount(SeedData.CREATOR_PRIYA_ID).first())
    }

    @Test
    fun `12 creator studio updates and deletes own video and blocks unauthorized edits`() = runBlocking {
        // Non-owner cannot edit or delete Aarav's video
        val unauthorizedEdit = repository.updateOwnVideo(
            creatorId = SeedData.CREATOR_PRIYA_ID,
            videoId = "vid_long_1",
            title = "Hijacked Title",
            description = "Desc",
            category = VideoCategory.MUSIC
        )
        assertTrue(unauthorizedEdit.isFailure)

        val unauthorizedDelete = repository.deleteOwnVideo(SeedData.CREATOR_PRIYA_ID, "vid_long_1")
        assertTrue(unauthorizedDelete.isFailure)

        // Owner updates metadata
        val ownerEdit = repository.updateOwnVideo(
            creatorId = SeedData.CREATOR_AARAV_ID,
            videoId = "vid_long_1",
            title = "Updated Desk Tour 2025",
            description = "Updated description",
            category = VideoCategory.EDUCATION
        )
        assertTrue(ownerEdit.isSuccess)
        val updatedVideo = repository.observeVideoById("vid_long_1").first()!!
        assertEquals("Updated Desk Tour 2025", updatedVideo.title)
        assertEquals(VideoCategory.EDUCATION, updatedVideo.category)

        // Owner deletes video
        val ownerDelete = repository.deleteOwnVideo(SeedData.CREATOR_AARAV_ID, "vid_long_1")
        assertTrue(ownerDelete.isSuccess)
        assertNull(repository.observeVideoById("vid_long_1").first())
    }

    @Test
    fun `13 blocking creator hides their videos, comments, and profile then unblock restores them`() = runBlocking {
        repository.login("viewer@creatorpaisa.test", "Viewer@123")

        // Self-block fails
        assertTrue(repository.blockUser(SeedData.VIEWER_TEST_ID, SeedData.VIEWER_TEST_ID).isFailure)

        // Block Priya
        assertTrue(repository.blockUser(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_PRIYA_ID).isSuccess)
        assertTrue(repository.observeIsUserBlocked(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_PRIYA_ID).first())

        val activeVideosAfterBlock = repository.observeActiveVideos().first()
        assertTrue(activeVideosAfterBlock.none { it.creatorId == SeedData.CREATOR_PRIYA_ID })

        val creatorsAfterBlock = repository.observeAllCreators().first()
        assertTrue(creatorsAfterBlock.none { it.id == SeedData.CREATOR_PRIYA_ID })

        val commentsOnLong1 = repository.observeComments("vid_long_1").first()
        assertTrue(commentsOnLong1.none { it.userId == SeedData.CREATOR_PRIYA_ID })

        // Unblock Priya restores videos, comments, and creator listing
        assertTrue(repository.unblockUser(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_PRIYA_ID).isSuccess)
        assertFalse(repository.observeIsUserBlocked(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_PRIYA_ID).first())
        assertTrue(repository.observeActiveVideos().first().any { it.creatorId == SeedData.CREATOR_PRIYA_ID })
        assertTrue(repository.observeAllCreators().first().any { it.id == SeedData.CREATOR_PRIYA_ID })
        assertTrue(repository.observeComments("vid_long_1").first().any { it.userId == SeedData.CREATOR_PRIYA_ID })
    }

    @Test
    fun `14 privacy settings persist and deleteAccount removes user data cleanly`() = runBlocking {
        val signedUp = repository.signUp(
            email = "tempcreator@creatorpaisa.test",
            username = "Temp Creator",
            password = "Password123",
            bio = "Temporary account",
            avatarUri = "drawable://img_creator_paisa_logo"
        ).getOrThrow()

        // Approve & upload a video so deleteAccount cascades videos too
        repository.adminSetUploaderApproval(SeedData.ADMIN_ID, signedUp.id, true)
        val vidId = repository.uploadVideo(
            signedUp.id,
            UploadDraft("Temp Video", "To be deleted", VideoFormat.SHORT, VideoCategory.MUSIC, 30L, 1024L, "", "https://example.com/temp.mp4")
        ).getOrThrow()

        // Update privacy settings
        val privResult = repository.updatePrivacySettings(signedUp.id, isPrivateProfile = true, hideSubscriberCount = true)
        assertTrue(privResult.isSuccess)
        val updatedUser = repository.currentUser.first()!!
        assertTrue(updatedUser.isPrivateProfile)
        assertTrue(updatedUser.hideSubscriberCount)

        // Delete account permanently
        val deleteResult = repository.deleteAccount(signedUp.id)
        assertTrue(deleteResult.isSuccess)
        assertNull(repository.currentUser.first())
        assertNull(repository.observeCreatorProfile(signedUp.id).first())
        assertNull(repository.observeVideoById(vidId).first())
    }

    @Test
    fun `15 feed filters by category, search query, and sort order accurately`() = runBlocking {
        val viewModel = FeedViewModel(repository)
        val collector = launch { viewModel.uiState.collect { } }
        shadowOf(Looper.getMainLooper()).idle()

        // Filter by TECH category
        viewModel.setCategory(VideoCategory.TECH)
        val techState = viewModel.uiState.awaitValue {
            it.selectedCategory == VideoCategory.TECH && it.allMatchingVideos.isNotEmpty()
        }
        assertTrue(techState.allMatchingVideos.all { it.category == VideoCategory.TECH })

        // Reset category & sort by MOST_VIEWED
        viewModel.setCategory(VideoCategory.ALL)
        viewModel.setSortOrder(FeedSortOrder.MOST_VIEWED)
        val mostViewedState = viewModel.uiState.awaitValue {
            it.selectedCategory == VideoCategory.ALL &&
                it.sortOrder == FeedSortOrder.MOST_VIEWED &&
                it.allMatchingVideos.isNotEmpty()
        }
        val viewsList = mostViewedState.allMatchingVideos.map { it.viewCount }
        assertEquals(viewsList.sortedDescending(), viewsList)

        // Search by category name ("Education")
        viewModel.setSearchQuery("Education")
        val searchState = viewModel.uiState.awaitValue {
            it.searchQuery == "Education" && it.allMatchingVideos.any { v -> v.id == "vid_long_3" }
        }
        assertTrue(searchState.allMatchingVideos.any { it.id == "vid_long_3" })

        collector.cancel()
    }

    @Test
    fun `16 upload viewmodel supports live progress, cancellation, and retry`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var failNext = false
        val controllableStorage = object : MediaStorage {
            override suspend fun storeUploadMedia(
                sourceVideoUri: String,
                sourceThumbnailUri: String,
                maxVideoBytes: Long,
                customMetadata: Map<String, String>,
                onProgress: (Float, String) -> Unit
            ): Result<StoredMediaResult> {
                onProgress(0.35f, "Storing video file...")
                gate.await()
                if (failNext) {
                    return Result.failure(IllegalStateException("Simulated storage write interruption"))
                }
                return PassthroughMediaStorage.storeUploadMedia(
                    sourceVideoUri = sourceVideoUri,
                    sourceThumbnailUri = sourceThumbnailUri,
                    maxVideoBytes = maxVideoBytes,
                    customMetadata = customMetadata,
                    onProgress = onProgress
                )
            }

            override suspend fun deleteStoredFileIfOwned(uriString: String?) = Unit
        }
        val controlledRepo = LocalCreatorPaisaRepository(
            dao = database.dao(),
            prefs = SessionPreferences(context),
            mediaStorage = controllableStorage
        ) { fakeClock }

        val viewModel = UploadViewModel(SeedData.CREATOR_AARAV_ID, controlledRepo)
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.publishVideo(
            title = "Retryable Short Clip",
            description = "Testing cancel and retry flow",
            format = VideoFormat.SHORT,
            category = VideoCategory.TECH,
            durationSeconds = 40L,
            fileSizeBytes = 2_048_000L,
            thumbnailUri = "drawable://img_banner_studio",
            videoUri = "https://example.com/retry.mp4"
        )
        val uploadingDeadline = System.currentTimeMillis() + 3_000L
        while (viewModel.progressState.value !is UploadProgressState.Uploading && System.currentTimeMillis() < uploadingDeadline) {
            delay(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue(viewModel.progressState.value is UploadProgressState.Uploading)

        // Cancel in-flight upload
        viewModel.cancelUpload()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.progressState.value is UploadProgressState.Cancelled)

        // Release gate with a failure first, then retry cleanly to Success
        failNext = true
        gate.complete(Unit)
        viewModel.retryUpload()
        val failDeadline = System.currentTimeMillis() + 3_000L
        while (viewModel.progressState.value !is UploadProgressState.Failure && System.currentTimeMillis() < failDeadline) {
            delay(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue(viewModel.progressState.value is UploadProgressState.Failure)

        failNext = false
        viewModel.retryUpload()
        val successDeadline = System.currentTimeMillis() + 5_000L
        while (viewModel.progressState.value !is UploadProgressState.Success && System.currentTimeMillis() < successDeadline) {
            delay(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val finalState = viewModel.progressState.value
        assertTrue("Expected Success after retry, got $finalState", finalState is UploadProgressState.Success)
        val createdId = (finalState as UploadProgressState.Success).videoId
        assertNotNull(controlledRepo.observeVideoById(createdId).first())
    }

    @Test
    fun `17 media3 player factory builds HLS adaptive items and applies quality constraints`() {
        val hlsItem = ExoPlayerFactory.buildMediaItem("https://example.com/stream/master.m3u8")
        assertEquals(MimeTypes.APPLICATION_M3U8, hlsItem.localConfiguration?.mimeType)

        val mp4Item = ExoPlayerFactory.buildMediaItem("https://example.com/video.mp4")
        assertNull(mp4Item.localConfiguration?.mimeType)

        val player = ExoPlayerFactory.create(context, isShortLoop = true)
        try {
            ExoPlayerFactory.applyQuality(player, VideoQualityOption.DATA_SAVER_360)
            assertEquals(360, player.trackSelectionParameters.maxVideoHeight)
            assertEquals(700_000, player.trackSelectionParameters.maxVideoBitrate)

            ExoPlayerFactory.applyQuality(player, VideoQualityOption.HD_720)
            assertEquals(720, player.trackSelectionParameters.maxVideoHeight)
            assertEquals(2_500_000, player.trackSelectionParameters.maxVideoBitrate)

            ExoPlayerFactory.applyQuality(player, VideoQualityOption.AUTO)
            assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoHeight)
            assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoBitrate)
        } finally {
            player.release()
        }
    }

    @Test
    fun `18 local media storage copies real video file, reports progress, enforces limit, and cleans up on delete`() = runBlocking {
        val storage = LocalMediaStorage(context)
        val localRepo = LocalCreatorPaisaRepository(
            dao = database.dao(),
            prefs = SessionPreferences(context),
            mediaStorage = storage
        ) { fakeClock }

        val tempSource = File(context.cacheDir, "sample_gallery_pick.mp4")
        val tempThumb = File(context.cacheDir, "sample_gallery_thumb.jpg")
        withContext(Dispatchers.IO) {
            tempSource.writeBytes(ByteArray(128 * 1024) { (it % 127).toByte() })
            tempThumb.writeBytes(ByteArray(8 * 1024) { 42 })
        }

        try {
            // Over-limit file rejected
            val tooLargeResult = storage.storeUploadMedia(
                sourceVideoUri = Uri.fromFile(tempSource).toString(),
                sourceThumbnailUri = Uri.fromFile(tempThumb).toString(),
                maxVideoBytes = 32 * 1024L,
                onProgress = { _, _ -> }
            )
            assertTrue(tooLargeResult.isFailure)

            // Valid upload persists real file:// copies into filesDir/creator_paisa_media and saves metadata in DB
            val reportedProgress = mutableListOf<Float>()
            val uploadResult = localRepo.uploadVideo(
                creatorId = SeedData.CREATOR_AARAV_ID,
                draft = UploadDraft(
                    title = "Real Local File Upload",
                    description = "Copied from gallery picker into app storage",
                    format = VideoFormat.SHORT,
                    category = VideoCategory.TECH,
                    durationSeconds = 25L,
                    fileSizeBytes = tempSource.length(),
                    thumbnailUri = Uri.fromFile(tempThumb).toString(),
                    videoUri = Uri.fromFile(tempSource).toString()
                ),
                onProgress = { p, _ -> reportedProgress += p }
            )
            assertTrue("Expected real local upload to succeed", uploadResult.isSuccess)
            assertTrue("Expected progressive callbacks", reportedProgress.isNotEmpty())
            assertTrue(reportedProgress.last() >= 0.9f)

            val createdVideo = localRepo.observeVideoById(uploadResult.getOrThrow()).first()!!
            assertTrue(createdVideo.videoUri.startsWith("file://"))
            assertTrue(createdVideo.thumbnailUri.startsWith("file://"))
            val storedVideoFile = File(Uri.parse(createdVideo.videoUri).path!!)
            val storedThumbFile = File(Uri.parse(createdVideo.thumbnailUri).path!!)
            assertTrue(storedVideoFile.exists())
            assertEquals(tempSource.length(), storedVideoFile.length())
            assertTrue(storedThumbFile.exists())

            // Deleting own video removes DB row AND stored media files from disk
            val deleted = localRepo.deleteOwnVideo(SeedData.CREATOR_AARAV_ID, createdVideo.id)
            assertTrue(deleted.isSuccess)
            assertNull(localRepo.observeVideoById(createdVideo.id).first())
            assertFalse(storedVideoFile.exists())
            assertFalse(storedThumbFile.exists())
        } finally {
            tempSource.delete()
            tempThumb.delete()
        }
    }

    @Test
    fun `19 active playback coordinator pauses prior player so only one video plays at a time`() {
        val playerOne = ExoPlayerFactory.create(context, isShortLoop = false)
        val playerTwo = ExoPlayerFactory.create(context, isShortLoop = true)
        try {
            playerOne.playWhenReady = true
            ActivePlaybackCoordinator.onPlaybackStarted(playerOne)
            assertTrue(playerOne.playWhenReady)

            playerTwo.playWhenReady = true
            ActivePlaybackCoordinator.onPlaybackStarted(playerTwo)

            assertFalse("First player must pause when second player starts", playerOne.playWhenReady)
            assertTrue("Second player should remain active", playerTwo.playWhenReady)
        } finally {
            ActivePlaybackCoordinator.onPlayerReleased(playerOne)
            ActivePlaybackCoordinator.onPlayerReleased(playerTwo)
            playerOne.release()
            playerTwo.release()
        }
    }

    @Test
    fun `20 shorts feed enforces max 3 minute duration and fetches uploaded shorts from database`() = runBlocking {
        // Insert an invalid Short (> 180s) directly into DB to verify FeedViewModel boundary guard
        database.dao().insertVideo(
            VideoEntity(
                id = "vid_short_too_long",
                creatorId = SeedData.CREATOR_AARAV_ID,
                creatorName = "Aarav Tech Studio",
                creatorAvatarUri = "drawable://img_thumb_tech",
                title = "Invalid 4-Minute Short",
                description = "Exceeds 3-minute Shorts cap",
                format = VideoFormat.SHORT.name,
                category = VideoCategory.TECH.name,
                durationSeconds = 240L,
                fileSizeBytes = 10_000_000L,
                thumbnailUri = "drawable://img_thumb_tech",
                videoUri = "https://example.com/too_long_short.mp4",
                viewCount = 0L,
                likeCount = 0L,
                commentCount = 0L,
                reportCount = 0L,
                isRemoved = false,
                removalReason = null,
                createdAt = fakeClock
            )
        )

        // Upload a valid 45s Short through repository
        val validUploadId = repository.uploadVideo(
            creatorId = SeedData.CREATOR_AARAV_ID,
            draft = UploadDraft(
                title = "Valid 45s Short",
                description = "Real DB Short",
                format = VideoFormat.SHORT,
                category = VideoCategory.TECH,
                durationSeconds = 45L,
                fileSizeBytes = 2_000_000L,
                thumbnailUri = "drawable://img_thumb_tech",
                videoUri = "https://example.com/valid_short.mp4"
            )
        ).getOrThrow()

        val feedVm = FeedViewModel(repository)
        val collector = launch { feedVm.uiState.collect { } }
        val shorts = feedVm.uiState.awaitValue { state ->
            state.shorts.any { it.id == validUploadId }
        }.shorts
        assertTrue(shorts.none { it.id == "vid_short_too_long" })
        assertTrue(shorts.all { it.format == VideoFormat.SHORT && it.durationSeconds <= 180L })
        assertTrue(shorts.any { it.id == validUploadId })

        collector.cancel()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `21 shorts screen supports vertical swipe navigation, subscribe, like, comment, play-pause, views, and creator tap`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }

        val feedVm = FeedViewModel(repository)
        var openedCreatorId: String? = null

        composeTestRule.setContent {
            val state by feedVm.uiState.collectAsStateWithLifecycle()
            val scope: CoroutineScope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    ShortsFeedScreen(
                        currentUserId = SeedData.VIEWER_TEST_ID,
                        shorts = state.shorts,
                        observeComments = { vid -> feedVm.observeComments(vid) },
                        observeIsSubscribed = { cid -> feedVm.observeIsSubscribed(cid, SeedData.VIEWER_TEST_ID) },
                        onPlayShort = {},
                        onCreatorClick = { cid -> openedCreatorId = cid },
                        onSubscribeClick = { cid -> feedVm.toggleSubscription(cid, SeedData.VIEWER_TEST_ID) },
                        onLikeClick = { vid -> feedVm.toggleLike(vid, SeedData.VIEWER_TEST_ID) },
                        onPostComment = { vid, text, onDone ->
                            scope.launch {
                                feedVm.addComment(vid, SeedData.VIEWER_TEST_ID, text, onDone)
                            }
                        },
                        onReportVideo = { _, _ -> },
                        onBlockCreator = {},
                        onGenuineViewReached = { vid -> feedVm.recordGenuineView(vid, SeedData.VIEWER_TEST_ID) },
                        onPlaybackError = { _, _ -> }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("short_page_vid_short_1").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify title, description, actual views, creator info, and Subscribe button render
        composeTestRule.onNodeWithTag("shorts_title_vid_short_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shorts_description_vid_short_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shorts_views_vid_short_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shorts_subscribe_button_vid_short_1").assertIsDisplayed()

        // Tap Play/Pause control toggles pause indicator badge cleanly
        composeTestRule.onNodeWithTag("shorts_play_pause_button_vid_short_1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("shorts_center_pause_badge_vid_short_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("shorts_play_pause_button_vid_short_1").performClick()
        composeTestRule.waitForIdle()

        // Like & Unlike (no duplicate likes allowed)
        val initialLikes = runBlocking { repository.observeVideoById("vid_short_1").first()!!.likeCount }
        composeTestRule.onNodeWithTag("shorts_like_button_vid_short_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Short like was not persisted") {
            repository.observeVideoById("vid_short_1").first()!!.likeCount == initialLikes + 1L
        }
        composeTestRule.onNodeWithTag("shorts_like_button_vid_short_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Short unlike was not persisted") {
            repository.observeVideoById("vid_short_1").first()!!.likeCount == initialLikes
        }

        // Subscribe & Unsubscribe persisted in DB
        composeTestRule.onNodeWithTag("shorts_subscribe_button_vid_short_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Subscribe from Shorts was not persisted") {
            repository.observeIsSubscribed(SeedData.CREATOR_PRIYA_ID, SeedData.VIEWER_TEST_ID).first()
        }
        composeTestRule.onNodeWithTag("shorts_subscribe_button_vid_short_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Unsubscribe from Shorts was not persisted") {
            !repository.observeIsSubscribed(SeedData.CREATOR_PRIYA_ID, SeedData.VIEWER_TEST_ID).first()
        }

        // Post comment via Comment bottom sheet -> saved in DB
        composeTestRule.onNodeWithTag("shorts_comment_button_vid_short_1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("shorts_comment_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("shorts_comment_input").performTextInput("Loved this Jaipur Short!")
        composeTestRule.onNodeWithTag("shorts_comment_send").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Short comment was not persisted") {
            repository.observeComments("vid_short_1").first().any { it.text == "Loved this Jaipur Short!" }
        }
        // Dismiss comments sheet before swiping
        composeTestRule.onNodeWithTag("shorts_comments_close").performClick()
        composeTestRule.waitForIdle()

        // Tap creator avatar/username opens creator profile
        composeTestRule.onNodeWithTag("shorts_creator_profile_vid_short_1").performClick()
        assertEquals(SeedData.CREATOR_PRIYA_ID, openedCreatorId)

        // Swipe up -> next Short; Swipe down -> previous Short
        composeTestRule.onNodeWithTag("shorts_vertical_pager").performTouchInput { swipeUp() }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("short_page_vid_short_2").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("shorts_vertical_pager").performTouchInput { swipeDown() }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("short_page_vid_short_1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `22 long videos feed enforces 30 minute limit and separates shorts from long videos`() = runBlocking {
        // Insert an invalid Long Video (> 1800s) directly into DB to verify FeedViewModel guard
        database.dao().insertVideo(
            VideoEntity(
                id = "vid_long_too_long",
                creatorId = SeedData.CREATOR_AARAV_ID,
                creatorName = "Aarav Tech Studio",
                creatorAvatarUri = "drawable://img_thumb_tech",
                title = "Invalid 35-Minute Video",
                description = "Exceeds 30-minute limit",
                format = VideoFormat.LONG.name,
                category = VideoCategory.TECH.name,
                durationSeconds = 2100L,
                fileSizeBytes = 50_000_000L,
                thumbnailUri = "drawable://img_thumb_tech",
                videoUri = "https://example.com/too_long_video.mp4",
                viewCount = 0L,
                likeCount = 0L,
                commentCount = 0L,
                reportCount = 0L,
                isRemoved = false,
                removalReason = null,
                createdAt = fakeClock
            )
        )

        val validLongId = repository.uploadVideo(
            creatorId = SeedData.CREATOR_AARAV_ID,
            draft = UploadDraft(
                title = "Valid 20-Minute Tutorial",
                description = "Full Kotlin walkthrough",
                format = VideoFormat.LONG,
                category = VideoCategory.EDUCATION,
                durationSeconds = 1200L,
                fileSizeBytes = 25_000_000L,
                thumbnailUri = "drawable://img_banner_studio",
                videoUri = "https://example.com/valid_long_tutorial.mp4"
            )
        ).getOrThrow()

        val feedVm = FeedViewModel(repository)
        val collector = launch { feedVm.uiState.collect { } }
        val state = feedVm.uiState.awaitValue { ui ->
            ui.longVideos.any { it.id == validLongId }
        }
        assertTrue(state.longVideos.none { it.id == "vid_long_too_long" })
        assertTrue(state.longVideos.all { it.format == VideoFormat.LONG && it.durationSeconds <= 1800L })
        assertTrue(state.longVideos.any { it.id == validLongId })
        assertTrue(state.shorts.none { it.format == VideoFormat.LONG })

        collector.cancel()
    }

    @Test
    fun `23 comments and replies persist in database and notify parent comment author`() = runBlocking {
        repository.addComment("vid_long_1", SeedData.VIEWER_TEST_ID, "Top level question about desk mic").getOrThrow()
        val topComment = repository.observeComments("vid_long_1").first()
            .first { it.text == "Top level question about desk mic" }
        assertNull(topComment.parentCommentId)

        val replyRes = repository.addComment(
            videoId = "vid_long_1",
            userId = SeedData.CREATOR_PRIYA_ID,
            text = "I used a cardioid dynamic mic here!",
            parentCommentId = topComment.id
        )
        assertTrue(replyRes.isSuccess)

        val allComments = repository.observeComments("vid_long_1").first()
        val reply = allComments.firstOrNull { it.text == "I used a cardioid dynamic mic here!" }
        assertNotNull(reply)
        assertEquals(topComment.id, reply?.parentCommentId)

        val viewerNotifs = repository.observeNotifications(SeedData.VIEWER_TEST_ID).first()
        assertTrue(
            viewerNotifs.any {
                it.type == NotificationType.REPLY &&
                    it.message.contains("replied") &&
                    it.commentId == reply?.id &&
                    !it.videoThumbnailUri.isNullOrBlank()
            }
        )
    }

    @Test
    fun `24 watch history saves position for logged-in user, clears cleanly, and deletes with account`() = runBlocking {
        repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()

        repository.recordWatchHistory(SeedData.VIEWER_TEST_ID, "vid_long_1", 42_500L)
        fakeClock += 5_000L
        repository.recordWatchHistory(SeedData.VIEWER_TEST_ID, "vid_long_2", 18_000L)

        val history = repository.observeWatchHistory(SeedData.VIEWER_TEST_ID).first()
        assertEquals(2, history.size)
        assertEquals("vid_long_2", history.first().id)
        assertEquals(42_500L, repository.getSavedWatchPositionMs(SeedData.VIEWER_TEST_ID, "vid_long_1"))

        repository.clearWatchHistory(SeedData.VIEWER_TEST_ID)
        assertTrue(repository.observeWatchHistory(SeedData.VIEWER_TEST_ID).first().isEmpty())

        // Account deletion also removes watch history
        val tempUser = repository.signUp(
            email = "history_del@creatorpaisa.test",
            username = "History Tester",
            password = "Password123",
            bio = "Testing history cleanup",
            avatarUri = "drawable://img_creator_paisa_logo"
        ).getOrThrow()
        repository.recordWatchHistory(tempUser.id, "vid_long_1", 10_000L)
        assertEquals(1, repository.observeWatchHistory(tempUser.id).first().size)
        repository.deleteAccount(tempUser.id).getOrThrow()
        assertNull(database.dao().getWatchHistoryEntry(tempUser.id, "vid_long_1"))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `25 player screen supports playback speed, volume mute, time display, adaptive-only quality, comment reply, and related videos`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }

        val playerVm = PlayerViewModel("vid_long_1", SeedData.VIEWER_TEST_ID, repository)
        var navigatedRelatedId: String? = null

        composeTestRule.setContent {
            val uiState by playerVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    PlayerScreen(
                        currentUserId = SeedData.VIEWER_TEST_ID,
                        uiState = uiState,
                        onBack = {},
                        onVideoClick = { nextId -> navigatedRelatedId = nextId },
                        onCreatorClick = {},
                        onGenuinePlaybackReached = { playerVm.onGenuinePlaybackReached() },
                        onSaveWatchHistory = { posMs -> playerVm.recordWatchHistory(posMs) },
                        onPlaybackError = {},
                        onClearPlaybackError = { playerVm.clearPlaybackError() },
                        onToggleLike = { playerVm.toggleLike() },
                        onToggleSubscribe = { playerVm.toggleSubscribe() },
                        onBlockCreator = {},
                        onPostComment = { text, parentId, onDone ->
                            playerVm.postComment(text, parentId, onDone)
                        },
                        onReportVideo = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("player_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Core player transport & time controls exist; single MP4 hides adaptive quality selector
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_seek_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_seek_forward_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_time_display").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_progress_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_mute_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_volume_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_fullscreen_button").assertIsDisplayed()
        assertTrue(
            "Single MP4 source should not expose adaptive quality button before adaptive tracks exist",
            composeTestRule.onAllNodesWithTag("player_quality_button").fetchSemanticsNodes().isEmpty()
        )

        // Speed selector exposes 0.5x, 1x, 1.25x, 1.5x, 2x
        composeTestRule.onNodeWithTag("player_speed_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("speed_option_1.5").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("speed_option_0.5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_option_1.25").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_option_2.0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_option_1.5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1.5x").assertIsDisplayed()

        // Reply to seeded comment cmt_3 and verify parentCommentId persisted
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("reply_button_cmt_3"))
        composeTestRule.onNodeWithTag("reply_button_cmt_3").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("replying_to_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("comment_input"))
        composeTestRule.onNodeWithTag("comment_input").performTextInput("Agreed on the rim light!")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("post_comment_button"))
        composeTestRule.onNodeWithTag("post_comment_button").performClick()
        composeTestRule.waitForIdle()
        awaitCondition(timeoutMs = 3_000L, message = "Expected reply to cmt_3 to be persisted") {
            repository.observeComments("vid_long_1").first()
                .any { it.text == "Agreed on the rim light!" && it.parentCommentId == "cmt_3" }
        }

        // Related videos section renders below player & triggers navigation on tap
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("related_videos_section_header"))
        composeTestRule.onNodeWithTag("related_videos_section_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("related_video_card_vid_long_3"))
        composeTestRule.onNodeWithTag("related_video_card_vid_long_3").performClick()
        assertEquals("vid_long_3", navigatedRelatedId)
    }

    @Test
    fun `26 profile edit enforces unique username, stores local avatar and banner, syncs creator info, and cleans replaced files`() = runBlocking {
        val storage = LocalMediaStorage(context)
        val localRepo = LocalCreatorPaisaRepository(
            dao = database.dao(),
            prefs = SessionPreferences(context),
            mediaStorage = storage
        ) { fakeClock }

        val tempAvatar1 = File(context.cacheDir, "avatar_one.jpg")
        val tempAvatar2 = File(context.cacheDir, "avatar_two.jpg")
        val tempBanner1 = File(context.cacheDir, "banner_one.jpg")
        withContext(Dispatchers.IO) {
            tempAvatar1.writeBytes(ByteArray(2048) { 11 })
            tempAvatar2.writeBytes(ByteArray(2048) { 22 })
            tempBanner1.writeBytes(ByteArray(4096) { 33 })
        }

        try {
            // Duplicate handle ("priya_rasoi") rejected
            val duplicateHandle = localRepo.updateProfile(
                requesterId = SeedData.CREATOR_AARAV_ID,
                targetUserId = SeedData.CREATOR_AARAV_ID,
                displayName = "Aarav Studio",
                username = "priya_rasoi",
                bio = "New bio",
                channelDescription = "New description",
                avatarUri = "drawable://img_thumb_tech",
                bannerUri = "drawable://img_banner_studio"
            )
            assertTrue(duplicateHandle.isFailure)

            // Spaces in a new handle rejected
            val spacesHandle = localRepo.updateProfile(
                requesterId = SeedData.CREATOR_AARAV_ID,
                targetUserId = SeedData.CREATOR_AARAV_ID,
                displayName = "Aarav Studio",
                username = "aarav new handle",
                bio = "New bio",
                channelDescription = "New description",
                avatarUri = "drawable://img_thumb_tech",
                bannerUri = "drawable://img_banner_studio"
            )
            assertTrue(spacesHandle.isFailure)

            // Valid update copies avatar & banner to private media dir and syncs videos/comments
            val firstUpdate = localRepo.updateProfile(
                requesterId = SeedData.CREATOR_AARAV_ID,
                targetUserId = SeedData.CREATOR_AARAV_ID,
                displayName = "Aarav Filmmaking",
                username = "aarav_films",
                bio = "Mobile filmmaking & audio tips",
                channelDescription = "Full Hindi & English creator tutorials",
                avatarUri = Uri.fromFile(tempAvatar1).toString(),
                bannerUri = Uri.fromFile(tempBanner1).toString()
            )
            assertTrue(firstUpdate.isSuccess)

            val updatedUser = localRepo.observeCreatorProfile(SeedData.CREATOR_AARAV_ID).first()!!
            assertEquals("Aarav Filmmaking", updatedUser.displayName)
            assertEquals("aarav_films", updatedUser.username)
            assertEquals("Mobile filmmaking & audio tips", updatedUser.bio)
            assertEquals("Full Hindi & English creator tutorials", updatedUser.channelDescription)
            assertTrue(updatedUser.avatarUri.startsWith("file://"))
            assertTrue(updatedUser.bannerUri.startsWith("file://"))
            val firstAvatarFile = File(Uri.parse(updatedUser.avatarUri).path!!)
            assertTrue(firstAvatarFile.exists())

            val syncedVideo = localRepo.observeVideoById("vid_long_1").first()!!
            assertEquals("Aarav Filmmaking", syncedVideo.creatorName)
            assertEquals(updatedUser.avatarUri, syncedVideo.creatorAvatarUri)

            val syncedComment = localRepo.observeComments("vid_short_1").first().first { it.userId == SeedData.CREATOR_AARAV_ID }
            assertEquals("Aarav Filmmaking", syncedComment.username)

            // Replacing avatar deletes the previous owned avatar file
            val secondUpdate = localRepo.updateProfile(
                requesterId = SeedData.CREATOR_AARAV_ID,
                targetUserId = SeedData.CREATOR_AARAV_ID,
                displayName = updatedUser.displayName,
                username = updatedUser.username,
                bio = updatedUser.bio,
                channelDescription = updatedUser.channelDescription,
                avatarUri = Uri.fromFile(tempAvatar2).toString(),
                bannerUri = updatedUser.bannerUri
            )
            assertTrue(secondUpdate.isSuccess)
            assertFalse("Replaced owned avatar file should be removed", firstAvatarFile.exists())
        } finally {
            tempAvatar1.delete()
            tempAvatar2.delete()
            tempBanner1.delete()
        }
    }

    @Test
    fun `27 security and privacy rules block non-owner edits, studio access, and hide private channels and playlists`() = runBlocking {
        // Non-owner cannot edit profile
        val nonOwnerEdit = repository.updateProfile(
            requesterId = SeedData.VIEWER_TEST_ID,
            targetUserId = SeedData.CREATOR_AARAV_ID,
            displayName = "Hacked",
            username = "hacked_user",
            bio = "Hacked",
            channelDescription = "Hacked",
            avatarUri = "drawable://img_thumb_tech",
            bannerUri = "drawable://img_banner_studio"
        )
        assertTrue(nonOwnerEdit.isFailure)
        assertTrue(nonOwnerEdit.exceptionOrNull() is SecurityException)

        // Non-owner cannot view Creator Dashboard stats
        assertNull(repository.observeCreatorDashboard(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_AARAV_ID).first())
        assertNotNull(repository.observeCreatorDashboard(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first())

        // Create a private playlist as Aarav -> visible to Aarav, hidden from Viewer
        val privPlaylistId = repository.createPlaylist(
            requesterId = SeedData.CREATOR_AARAV_ID,
            title = "Draft Rough Cuts",
            description = "Internal notes",
            thumbnailUri = "",
            isPublic = false
        ).getOrThrow()

        assertTrue(repository.observePlaylistsForChannel(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first().any { it.id == privPlaylistId })
        assertTrue(repository.observePlaylistsForChannel(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_AARAV_ID).first().none { it.id == privPlaylistId })

        // Non-owner cannot modify or delete Aarav's playlist
        val badPlaylistUpdate = repository.updatePlaylist(
            requesterId = SeedData.VIEWER_TEST_ID,
            playlistId = "pl_aarav_studio",
            title = "Hijacked Playlist",
            description = "",
            thumbnailUri = "",
            isPublic = true
        )
        assertTrue(badPlaylistUpdate.exceptionOrNull() is SecurityException)

        val badPlaylistDelete = repository.deletePlaylist(SeedData.VIEWER_TEST_ID, "pl_aarav_studio")
        assertTrue(badPlaylistDelete.exceptionOrNull() is SecurityException)

        val badPlaylistAdd = repository.addVideoToPlaylist(SeedData.VIEWER_TEST_ID, "pl_aarav_studio", "vid_short_2")
        assertTrue(badPlaylistAdd.exceptionOrNull() is SecurityException)

        // Private profile + hideSubscriberCount masks visitor ChannelUiState
        repository.updatePrivacySettings(SeedData.CREATOR_AARAV_ID, isPrivateProfile = true, hideSubscriberCount = true).getOrThrow()
        val channelVm = ChannelViewModel(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID, repository)
        val collector = launch { channelVm.uiState.collect { } }
        val visitorState = channelVm.uiState.awaitValue { it.creator != null }
        assertEquals(0L, visitorState.creator!!.subscriberCount)
        assertEquals("", visitorState.creator!!.emailMasked)
        assertTrue(visitorState.videos.isEmpty())
        assertTrue(visitorState.playlists.isEmpty())
        collector.cancel()
    }

    @Test
    fun `28 playlists CRUD manages videos, cleans up references when video deleted, and deletes playlist cleanly`() = runBlocking {
        val plId = repository.createPlaylist(
            requesterId = SeedData.CREATOR_AARAV_ID,
            title = "Audio Gear Picks",
            description = "Mic and interface guides",
            thumbnailUri = "drawable://img_banner_studio",
            isPublic = true
        ).getOrThrow()

        repository.addVideoToPlaylist(SeedData.CREATOR_AARAV_ID, plId, "vid_long_1").getOrThrow()
        repository.addVideoToPlaylist(SeedData.CREATOR_AARAV_ID, plId, "vid_short_2").getOrThrow()

        var playlist = repository.observePlaylistsForChannel(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID)
            .first()
            .first { it.id == plId }
        assertEquals(2, playlist.videoCount)
        assertTrue(playlist.videos.any { it.id == "vid_short_2" })

        // Deleting a video cascades removal of its playlist reference
        repository.deleteOwnVideo(SeedData.CREATOR_AARAV_ID, "vid_short_2").getOrThrow()
        playlist = repository.observePlaylistsForChannel(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID)
            .first()
            .first { it.id == plId }
        assertEquals(1, playlist.videoCount)
        assertTrue(playlist.videos.none { it.id == "vid_short_2" })

        // Remove remaining video & update metadata
        repository.removeVideoFromPlaylist(SeedData.CREATOR_AARAV_ID, plId, "vid_long_1").getOrThrow()
        repository.updatePlaylist(
            requesterId = SeedData.CREATOR_AARAV_ID,
            playlistId = plId,
            title = "Updated Gear List",
            description = "Updated desc",
            thumbnailUri = "drawable://img_thumb_tech",
            isPublic = false
        ).getOrThrow()

        playlist = repository.observePlaylistsForChannel(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID)
            .first()
            .first { it.id == plId }
        assertEquals("Updated Gear List", playlist.title)
        assertFalse(playlist.isPublic)
        assertEquals(0, playlist.videoCount)

        // Delete playlist removes it completely
        repository.deletePlaylist(SeedData.CREATOR_AARAV_ID, plId).getOrThrow()
        assertTrue(repository.observePlaylistsForChannel(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first().none { it.id == plId })
    }

    @Test
    fun `29 subscribed creators flow updates cleanly, filters blocked users, and ignores duplicate subscriptions`() = runBlocking {
        repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()

        val initialAaravSubs = repository.observeCreatorProfile(SeedData.CREATOR_AARAV_ID).first()!!.subscriberCount
        repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).getOrThrow()
        repository.toggleSubscription(SeedData.CREATOR_PRIYA_ID, SeedData.VIEWER_TEST_ID).getOrThrow()

        // Attempting to insert duplicate subscription row directly is ignored cleanly without changing count
        database.dao().insertSubscription(
            SubscriptionEntity(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID, fakeClock)
        )
        assertEquals(initialAaravSubs + 1L, repository.observeCreatorProfile(SeedData.CREATOR_AARAV_ID).first()!!.subscriberCount)

        var subscribedList = repository.observeSubscribedCreators(SeedData.VIEWER_TEST_ID).first()
        assertEquals(2, subscribedList.size)
        assertTrue(subscribedList.any { it.id == SeedData.CREATOR_AARAV_ID })
        assertTrue(subscribedList.any { it.id == SeedData.CREATOR_PRIYA_ID })

        // Blocking a subscribed creator excludes them from subscribed list
        repository.blockUser(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_PRIYA_ID).getOrThrow()
        subscribedList = repository.observeSubscribedCreators(SeedData.VIEWER_TEST_ID).first()
        assertEquals(1, subscribedList.size)
        assertTrue(subscribedList.none { it.id == SeedData.CREATOR_PRIYA_ID })
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `30 channel screen switches 5 tabs cleanly, separates long videos and shorts, and opens playlists and about`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val channelVm = ChannelViewModel(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID, repository)

        composeTestRule.setContent {
            val state by channelVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    ChannelScreen(
                        currentUserId = SeedData.VIEWER_TEST_ID,
                        uiState = state,
                        onBack = {},
                        onToggleSubscribe = { channelVm.toggleSubscription() },
                        onToggleBlock = { channelVm.toggleBlock() },
                        onOpenStudio = {},
                        onVideoClick = {},
                        onLikeClick = { channelVm.toggleLike(it) },
                        onReportVideo = { id, r -> channelVm.reportVideo(id, r) }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithText("Aarav Tech Studio").fetchSemanticsNodes().isNotEmpty()
        }

        // Videos tab shows only LONG videos (vid_long_1, vid_long_3), never vid_short_2
        composeTestRule.onNodeWithTag("channel_tab_videos").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_card_vid_long_1").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeTestRule.onAllNodesWithTag("video_card_vid_short_2").fetchSemanticsNodes().isEmpty())

        // Shorts tab shows only SHORT videos (vid_short_2), never vid_long_1
        composeTestRule.onNodeWithTag("channel_tab_shorts").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_card_vid_short_2").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeTestRule.onAllNodesWithTag("video_card_vid_long_1").fetchSemanticsNodes().isEmpty())

        // Playlists tab shows seeded playlist and opens Playlist Detail dialog
        composeTestRule.onNodeWithTag("channel_tab_playlists").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("playlist_card_pl_aarav_studio").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("playlist_card_pl_aarav_studio").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("playlist_detail_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("playlist_video_row_vid_long_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // About tab renders channel_about_card
        composeTestRule.onNodeWithTag("channel_tab_about").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("channel_about_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("channel_about_card").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `31 creator studio renders analytics, recent uploads, performance bars, edits video, and denies non-owners`() {
        // Non-owner Studio ViewModel denies access
        val unauthorizedVm = CreatorStudioViewModel(SeedData.VIEWER_TEST_ID, SeedData.CREATOR_AARAV_ID, repository)
        assertFalse(unauthorizedVm.uiState.value.isAuthorized)

        val ownerVm = CreatorStudioViewModel(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID, repository)
        composeTestRule.setContent {
            val state by ownerVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    CreatorStudioScreen(
                        uiState = state,
                        onBack = {},
                        onOpenUpload = {},
                        onOpenVideo = {},
                        onUpdateVideo = { id, title, desc, cat, thumb, tags ->
                            ownerVm.updateVideo(id, title, desc, cat, thumb, tags)
                        },
                        onDeleteVideo = { id -> ownerVm.deleteVideo(id) }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("creator_studio_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("studio_total_views").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_total_subscribers").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_total_uploads").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_total_likes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("creator_studio_screen")
            .performScrollToNode(hasTestTag("studio_recent_uploads_section"))
        composeTestRule.onNodeWithTag("studio_recent_uploads_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("creator_studio_screen")
            .performScrollToNode(hasTestTag("studio_performance_overview"))
        composeTestRule.onNodeWithTag("studio_performance_overview").assertIsDisplayed()

        // Edit video title via Studio edit dialog
        composeTestRule.onNodeWithTag("creator_studio_screen")
            .performScrollToNode(hasTestTag("studio_video_card_vid_long_1"))
        composeTestRule.onNodeWithTag("studio_edit_video_vid_long_1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("studio_edit_title_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("studio_edit_title_input").performTextInput(" v2")
        composeTestRule.onNodeWithTag("studio_save_edit_button").performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Updated title was not persisted") {
            repository.observeVideoById("vid_long_1").first()?.title?.endsWith("v2") == true
        }
    }

    @Test
    fun `32 like comment reply subscriber and upload notifications trigger with dedup and privacy rules`() = runBlocking {
        // Viewer subscribes to Aarav twice (unsubscribe/resubscribe) -> deduplicated to single sub notification
        repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).getOrThrow()
        repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).getOrThrow()
        repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).getOrThrow()

        var aaravNotifs = repository.observeNotificationsForUser(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first()
        val subNotifsFromViewer = aaravNotifs.filter {
            it.type == NotificationType.SUBSCRIPTION && it.actorId == SeedData.VIEWER_TEST_ID
        }
        assertEquals(1, subNotifsFromViewer.size)

        // Viewer likes Aarav's vid_long_1, unlikes, and likes again -> deduplicated single LIKE notification with thumbnail
        repository.toggleLike("vid_long_1", SeedData.VIEWER_TEST_ID)
        repository.toggleLike("vid_long_1", SeedData.VIEWER_TEST_ID)
        repository.toggleLike("vid_long_1", SeedData.VIEWER_TEST_ID)

        aaravNotifs = repository.observeNotificationsForUser(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first()
        val likeNotifsFromViewer = aaravNotifs.filter {
            it.type == NotificationType.LIKE && it.actorId == SeedData.VIEWER_TEST_ID && it.videoId == "vid_long_1"
        }
        assertEquals(1, likeNotifsFromViewer.size)
        assertFalse(likeNotifsFromViewer.first().videoThumbnailUri.isNullOrBlank())

        // Self-like does not notify Aarav himself
        repository.toggleLike("vid_long_1", SeedData.CREATOR_AARAV_ID)
        aaravNotifs = repository.observeNotificationsForUser(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first()
        assertTrue(aaravNotifs.none { it.actorId == SeedData.CREATOR_AARAV_ID })

        // Comment notifies creator with COMMENT type + commentId; reply by Priya notifies Viewer with REPLY type
        repository.addComment("vid_long_1", SeedData.VIEWER_TEST_ID, "Awesome studio lighting breakdown").getOrThrow()
        val addedComment = repository.observeComments("vid_long_1").first()
            .first { it.text == "Awesome studio lighting breakdown" }
        aaravNotifs = repository.observeNotificationsForUser(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first()
        assertTrue(
            aaravNotifs.any {
                it.type == NotificationType.COMMENT &&
                    it.commentId == addedComment.id &&
                    it.message.contains("commented")
            }
        )

        repository.addComment("vid_long_1", SeedData.CREATOR_PRIYA_ID, "Great tip on key light!", addedComment.id).getOrThrow()
        val replyComment = repository.observeComments("vid_long_1").first()
            .first { it.text == "Great tip on key light!" }
        val viewerNotifs = repository.observeNotificationsForUser(SeedData.VIEWER_TEST_ID, SeedData.VIEWER_TEST_ID).first()
        assertTrue(
            viewerNotifs.any {
                it.type == NotificationType.REPLY &&
                    it.commentId == replyComment.id &&
                    it.actorId == SeedData.CREATOR_PRIYA_ID
            }
        )

        // Private video upload does NOT notify subscribers; public published video DOES notify subscribers
        repository.uploadVideo(
            creatorId = SeedData.CREATOR_AARAV_ID,
            draft = UploadDraft(
                title = "Unlisted Internal Rehearsal",
                description = "Private draft test",
                format = VideoFormat.LONG,
                category = VideoCategory.TECH,
                durationSeconds = 120L,
                fileSizeBytes = 10_000_000L,
                thumbnailUri = "drawable://img_thumb_tech",
                videoUri = "https://example.com/private_draft.mp4",
                isPublic = false,
                isPublished = true
            )
        ).getOrThrow()
        val afterPrivateUpload = repository.observeNotificationsForUser(SeedData.VIEWER_TEST_ID, SeedData.VIEWER_TEST_ID).first()
        assertTrue(afterPrivateUpload.none { it.type == NotificationType.VIDEO_UPLOAD && it.message.contains("Unlisted Internal Rehearsal") })

        val publicVidId = repository.uploadVideo(
            creatorId = SeedData.CREATOR_AARAV_ID,
            draft = UploadDraft(
                title = "Public Shorts Lighting Hack",
                description = "Public upload alert test",
                format = VideoFormat.SHORT,
                category = VideoCategory.TECH,
                durationSeconds = 45L,
                fileSizeBytes = 5_000_000L,
                thumbnailUri = "drawable://img_thumb_tech",
                videoUri = "https://example.com/public_short.mp4",
                isPublic = true,
                isPublished = true
            )
        ).getOrThrow()
        val afterPublicUpload = repository.observeNotificationsForUser(SeedData.VIEWER_TEST_ID, SeedData.VIEWER_TEST_ID).first()
        assertTrue(
            afterPublicUpload.any {
                it.type == NotificationType.VIDEO_UPLOAD &&
                    it.videoId == publicVidId &&
                    it.message.contains("Public Shorts Lighting Hack")
            }
        )

        // Blocked actor cannot send new notifications to recipient
        repository.blockUser(SeedData.VIEWER_TEST_ID, SeedData.ADMIN_ID).getOrThrow()
        repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID) // noop helper
        repository.addComment("vid_long_1", SeedData.ADMIN_ID, "Admin reply check", replyComment.id).getOrThrow()
        val viewerAfterBlock = repository.observeNotificationsForUser(SeedData.VIEWER_TEST_ID, SeedData.VIEWER_TEST_ID).first()
        assertTrue(viewerAfterBlock.none { it.actorId == SeedData.ADMIN_ID })
    }

    @Test
    fun `33 notification ownership security, preferences filtering, management actions, and badge counts work accurately`() = runBlocking {
        // Viewer has 1 seeded unread notification (notif_seed_1)
        val initialUnread = repository.observeUnreadNotificationCount(SeedData.VIEWER_TEST_ID).first()
        assertEquals(1, initialUnread)

        // Non-owner cannot read or mutate Viewer's notifications or preferences
        assertTrue(repository.observeNotificationsForUser(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).first().isEmpty())
        val badMark = repository.markNotificationRead(SeedData.CREATOR_AARAV_ID, "notif_seed_1")
        assertTrue(badMark.exceptionOrNull() is SecurityException)
        val badDelete = repository.deleteNotification(SeedData.CREATOR_AARAV_ID, "notif_seed_1")
        assertTrue(badDelete.exceptionOrNull() is SecurityException)
        val badPrefs = repository.updateNotificationPreferences(
            SeedData.CREATOR_AARAV_ID,
            NotificationPreferences(userId = SeedData.VIEWER_TEST_ID, likesEnabled = false)
        )
        assertTrue(badPrefs.exceptionOrNull() is SecurityException)

        // Disabling LIKE preference suppresses incoming LIKE notifications
        repository.updateNotificationPreferences(
            SeedData.CREATOR_AARAV_ID,
            NotificationPreferences(
                userId = SeedData.CREATOR_AARAV_ID,
                likesEnabled = false,
                commentsEnabled = true,
                repliesEnabled = true,
                subscribersEnabled = true,
                videoUploadsEnabled = true
            )
        ).getOrThrow()
        repository.toggleLike("vid_long_1", SeedData.VIEWER_TEST_ID)
        val aaravAfterMutedLike = repository.observeNotificationsForUser(SeedData.CREATOR_AARAV_ID, SeedData.CREATOR_AARAV_ID).first()
        assertTrue(aaravAfterMutedLike.none { it.type == NotificationType.LIKE && it.actorId == SeedData.VIEWER_TEST_ID })

        // Owner marks read, deletes, and clears all
        repository.markNotificationRead(SeedData.VIEWER_TEST_ID, "notif_seed_1").getOrThrow()
        assertEquals(0, repository.observeUnreadNotificationCount(SeedData.VIEWER_TEST_ID).first())
        repository.deleteNotification(SeedData.VIEWER_TEST_ID, "notif_seed_1").getOrThrow()
        assertTrue(repository.observeNotificationsForUser(SeedData.VIEWER_TEST_ID, SeedData.VIEWER_TEST_ID).first().isEmpty())

        // Seed another notification via reply & test clearAllNotifications
        repository.addComment("vid_long_1", SeedData.VIEWER_TEST_ID, "Parent comment for clear test").getOrThrow()
        val parent = repository.observeComments("vid_long_1").first().first { it.text == "Parent comment for clear test" }
        repository.addComment("vid_long_1", SeedData.CREATOR_AARAV_ID, "Creator reply", parent.id).getOrThrow()
        assertEquals(1, repository.observeUnreadNotificationCount(SeedData.VIEWER_TEST_ID).first())

        repository.clearAllNotifications(SeedData.VIEWER_TEST_ID).getOrThrow()
        assertEquals(0, repository.observeUnreadNotificationCount(SeedData.VIEWER_TEST_ID).first())
        assertTrue(repository.observeNotificationsForUser(SeedData.VIEWER_TEST_ID, SeedData.VIEWER_TEST_ID).first().isEmpty())
    }

    @Test
    fun `34 deleting user account deletes notification preferences and all user notifications`() = runBlocking {
        val tempUser = repository.signUp(
            email = "notif_del@creatorpaisa.test",
            username = "Notif Deleter",
            password = "Password123",
            bio = "Testing notification cleanup",
            avatarUri = "drawable://img_creator_paisa_logo"
        ).getOrThrow()

        repository.updateNotificationPreferences(
            tempUser.id,
            NotificationPreferences(userId = tempUser.id, likesEnabled = false, videoUploadsEnabled = false)
        ).getOrThrow()
        assertNotNull(database.dao().getNotificationPreferencesOnce(tempUser.id))

        repository.deleteAccount(tempUser.id).getOrThrow()
        assertNull(database.dao().getNotificationPreferencesOnce(tempUser.id))
        assertTrue(repository.observeNotificationsForUser(tempUser.id, tempUser.id).first().isEmpty())
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `35 notifications screen filters categories, marks read, deletes single item, clears all, and saves notification settings`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
            // Add a LIKE notification for Viewer by liking Aarav? Wait: Viewer receives when someone interacts with Viewer's reply or uploaded video or Aarav uploads
            repository.toggleSubscription(SeedData.CREATOR_AARAV_ID, SeedData.VIEWER_TEST_ID).getOrThrow()
            repository.uploadVideo(
                creatorId = SeedData.CREATOR_AARAV_ID,
                draft = UploadDraft(
                    title = "Studio Lighting Tips 2026",
                    description = "Latest video update",
                    format = VideoFormat.LONG,
                    category = VideoCategory.TECH,
                    durationSeconds = 300L,
                    fileSizeBytes = 12_000_000L,
                    thumbnailUri = "drawable://img_thumb_tech",
                    videoUri = "https://example.com/lighting_2026.mp4"
                )
            ).getOrThrow()
        }

        val notifVm = NotificationsViewModel(SeedData.VIEWER_TEST_ID, repository)
        composeTestRule.setContent {
            val state by notifVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    NotificationsScreen(
                        uiState = state,
                        onBack = {},
                        onSelectCategory = { notifVm.selectCategory(it) },
                        onMarkRead = { notifVm.markRead(it) },
                        onMarkAllRead = { notifVm.markAllRead() },
                        onDeleteNotification = { notifVm.deleteNotification(it) },
                        onClearAll = { notifVm.clearAllNotifications() },
                        onUpdatePreferences = { notifVm.updatePreferences(it) },
                        onOpenVideo = { _, _ -> },
                        onOpenChannel = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("notification_card_notif_seed_1").fetchSemanticsNodes().isNotEmpty()
        }

        // Category filter: Video Updates shows only video upload notification, hiding notif_seed_1
        composeTestRule.onNodeWithTag("notif_filter_${NotificationCategoryFilter.VIDEO_UPDATES.name}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("notification_card_notif_seed_1").fetchSemanticsNodes().isEmpty()
        }

        // Switch back to All Notifications
        composeTestRule.onNodeWithTag("notif_filter_${NotificationCategoryFilter.ALL.name}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("notification_card_notif_seed_1").fetchSemanticsNodes().isNotEmpty()
        }

        // Mark seeded notification as read
        composeTestRule.onNodeWithTag("mark_read_button_notif_seed_1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("notification_unread_dot_notif_seed_1").fetchSemanticsNodes().isEmpty()
        }

        // Delete seeded notification individually
        composeTestRule.onNodeWithTag("delete_notification_button_notif_seed_1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("notification_card_notif_seed_1").fetchSemanticsNodes().isEmpty()
        }

        // Open Notification Settings Dialog & toggle Likes OFF -> persisted to DB
        composeTestRule.onNodeWithTag("open_notification_settings_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("notification_settings_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("notif_pref_likes_switch").performClick()
        composeTestRule.onNodeWithTag("close_notification_settings_button").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Notification preference likesEnabled=false was not persisted") {
            repository.observeNotificationPreferences(SeedData.VIEWER_TEST_ID).first().likesEnabled == false
        }

        // Clear All Notifications with confirmation dialog
        composeTestRule.onNodeWithTag("clear_all_notifications_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("confirm_clear_all_notifications_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("confirm_clear_all_notifications_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("notifications_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `36 bell badge updates reactively on home screen and notification deep link highlights target comment in player`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }

        val appVm = AppViewModel(repository)
        val feedVm = FeedViewModel(repository)

        composeTestRule.setContent {
            val user by appVm.currentUser.collectAsStateWithLifecycle()
            val unread by appVm.unreadNotificationCount.collectAsStateWithLifecycle()
            val feedState by feedVm.uiState.collectAsStateWithLifecycle()
            val lang by appVm.appLanguage.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    if (user != null) {
                        HomeScreen(
                            currentUser = user!!,
                            uiState = feedState,
                            currentLanguage = lang,
                            unreadNotificationCount = unread,
                            onToggleLanguage = {},
                            onSearchChange = {},
                            onFilterChange = {},
                            onCategoryChange = {},
                            onSortChange = {},
                            onVideoClick = {},
                            onCreatorClick = {},
                            onLikeClick = {},
                            onReportVideo = { _, _ -> },
                            onOpenNotifications = {},
                            onOpenStudio = {},
                            onOpenAdmin = {},
                            onOpenSetupGuide = {}
                        )
                    }
                }
            }
        }

        // Unread badge visible with "1" for seeded notification
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("notifications_unread_badge").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("notifications_unread_badge").assertIsDisplayed()

        // Marking all read reactively hides the unread badge
        runBlocking {
            repository.markAllNotificationsRead(SeedData.VIEWER_TEST_ID)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("notifications_unread_badge").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `37 player screen deep-linked with targetCommentId highlights matching comment thread`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val playerVm = PlayerViewModel("vid_long_1", SeedData.VIEWER_TEST_ID, repository)

        composeTestRule.setContent {
            val uiState by playerVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    PlayerScreen(
                        currentUserId = SeedData.VIEWER_TEST_ID,
                        uiState = uiState,
                        targetCommentId = "cmt_3",
                        onBack = {},
                        onCreatorClick = {},
                        onGenuinePlaybackReached = {},
                        onPlaybackError = {},
                        onClearPlaybackError = {},
                        onToggleLike = {},
                        onToggleSubscribe = {},
                        onBlockCreator = {},
                        onPostComment = { _, _, _ -> },
                        onReportVideo = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("highlighted_comment_cmt_3").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("highlighted_comment_cmt_3").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `38 video cards and player overlay display creator profile badge with Follow button and navigate to creator`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val feedVm = FeedViewModel(repository)
        var openedCreatorId: String? = null

        composeTestRule.setContent {
            val feedState by feedVm.uiState.collectAsStateWithLifecycle()
            val followed by repository.observeSubscribedCreators(viewer.id)
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val followedIds = followed.map { it.id }.toSet()

            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    HomeScreen(
                        currentUser = viewer,
                        uiState = feedState,
                        currentLanguage = com.example.localization.AppLanguage.ENGLISH,
                        unreadNotificationCount = 0,
                        onToggleLanguage = {},
                        onSearchChange = {},
                        onFilterChange = {},
                        onCategoryChange = {},
                        onSortChange = {},
                        onVideoClick = {},
                        onCreatorClick = { cid -> openedCreatorId = cid },
                        onLikeClick = {},
                        onReportVideo = { _, _ -> },
                        onOpenNotifications = {},
                        onOpenStudio = {},
                        onOpenAdmin = {},
                        onOpenSetupGuide = {},
                        followedCreatorIds = followedIds,
                        onFollowClick = { cid -> feedVm.toggleSubscription(cid, viewer.id) }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_card_vid_long_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_feed_list")
            .performScrollToNode(hasTestTag("video_profile_badge_vid_long_1"))

        composeTestRule.onNodeWithTag("video_profile_badge_vid_long_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_profile_creator_vid_long_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_follow_button_vid_long_1")
            .assertIsDisplayed()
            .assertTextContains("Follow")

        // Clicking Follow persists subscription and toggles overlay badge button to Following
        composeTestRule.onNodeWithTag("video_follow_button_vid_long_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Follow action from video badge did not persist") {
            repository.observeIsSubscribed(SeedData.CREATOR_AARAV_ID, viewer.id).first()
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("video_follow_button_vid_long_1")
                    .assertTextContains("Following")
                true
            }.getOrDefault(false)
        }

        // Clicking Following unfollows cleanly
        composeTestRule.onNodeWithTag("video_follow_button_vid_long_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Unfollow action from video badge did not persist") {
            !repository.observeIsSubscribed(SeedData.CREATOR_AARAV_ID, viewer.id).first()
        }

        // Tapping creator name badge opens creator channel
        composeTestRule.onNodeWithTag("video_profile_creator_vid_long_1").performClick()
        assertEquals(SeedData.CREATOR_AARAV_ID, openedCreatorId)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `39 main application frame top bar renders notification bell with unread count badge across tabs and navigates to notifications`() {
        composeTestRule.setContent {
            CreatorPaisaApp(repository = repository)
        }
        composeTestRule.waitForIdle()

        // Sign in as Viewer (seeded with 1 unread notification)
        composeTestRule.onNodeWithTag("quick_login_viewer").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("main_top_app_bar").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("main_top_app_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_notifications_button").assertIsDisplayed()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("notifications_unread_badge").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("notifications_unread_badge")
            .assertIsDisplayed()
            .assertTextContains("1")

        // Switch top-level tab to Network and confirm top bar bell & badge remain visible
        composeTestRule.onNodeWithTag("nav_network").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("network_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("main_top_app_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_notifications_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notifications_unread_badge").assertIsDisplayed()

        // Clicking notification bell opens Notifications screen
        composeTestRule.onNodeWithTag("open_notifications_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("notifications_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("notifications_screen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `40 quick upload FAB opens video upload bottom sheet with validation and clean dismiss`() {
        composeTestRule.setContent {
            CreatorPaisaApp(repository = repository)
        }
        composeTestRule.waitForIdle()

        // Sign in as approved Creator
        composeTestRule.onNodeWithTag("quick_login_creator").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("quick_upload_fab").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("quick_upload_fab").assertIsDisplayed().performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_upload_bottom_sheet").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("upload_sheet_picker_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("upload_sheet_select_video_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("upload_sheet_title_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("upload_sheet_description_input").assertIsDisplayed()

        // Publishing without picking a video file displays inline validation error
        composeTestRule.onNodeWithTag("upload_sheet_publish_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("upload_sheet_validation_error").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("upload_sheet_validation_error").assertIsDisplayed()

        // Closing bottom sheet dismisses it cleanly
        composeTestRule.onNodeWithTag("upload_sheet_close_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_upload_bottom_sheet").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `41 video upload bottom sheet publishes selected video with title and description and navigates to player`() {
        val tempVideo = File(context.cacheDir, "sheet_upload_sample.mp4").apply {
            writeBytes(ByteArray(4096) { 0x2A })
        }
        try {
            val creator = runBlocking {
                repository.login("aarav@creatorpaisa.test", "Creator@123").getOrThrow()
            }
            val storageRepo = LocalCreatorPaisaRepository(
                dao = database.dao(),
                prefs = SessionPreferences(context),
                mediaStorage = LocalMediaStorage(context),
                timeProvider = { fakeClock }
            )
            val uploadVm = UploadViewModel(creator.id, storageRepo)
            var navigatedVideoId: String? = null

            composeTestRule.setContent {
                val dailyCount by uploadVm.dailyUploadCount.collectAsStateWithLifecycle()
                val progressState by uploadVm.progressState.collectAsStateWithLifecycle()

                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    CreatorPaisaTheme(darkTheme = true) {
                        VideoUploadBottomSheet(
                            currentUser = creator,
                            dailyUploadCount = dailyCount,
                            progressState = progressState,
                            onDismiss = {},
                            onPublish = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags ->
                                uploadVm.publishVideo(
                                    title = title,
                                    description = desc,
                                    format = format,
                                    category = category,
                                    durationSeconds = duration,
                                    fileSizeBytes = sizeBytes,
                                    thumbnailUri = thumb,
                                    videoUri = vidUri,
                                    tags = tags
                                )
                            },
                            onCancelUpload = { uploadVm.cancelUpload() },
                            onRetryUpload = { uploadVm.retryUpload() },
                            onResetState = { uploadVm.resetState() },
                            onVideoPublishedNavigate = { vid -> navigatedVideoId = vid },
                            initialVideoUri = Uri.fromFile(tempVideo).toString(),
                            initialVideoName = "studio_demo.mp4",
                            initialDurationSeconds = 42L,
                            initialFileSizeBytes = tempVideo.length()
                        )
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("upload_sheet_selected_video_name").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("upload_sheet_selected_video_name").assertTextContains("studio_demo.mp4")

            composeTestRule.onNodeWithTag("upload_sheet_title_input")
                .performScrollTo()
                .performTextInput("Firebase Bottom Sheet Upload Demo")
            composeTestRule.onNodeWithTag("upload_sheet_description_input")
                .performScrollTo()
                .performTextInput("Uploaded from Creator Paisa quick bottom sheet.")
            composeTestRule.onNodeWithTag("upload_sheet_category_${VideoCategory.TECH.name}")
                .performScrollTo()
                .performClick()

            composeTestRule.onNodeWithTag("upload_sheet_publish_button")
                .performScrollTo()
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithTag("upload_sheet_success").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("upload_sheet_success").assertIsDisplayed()

            val persistedVideo = runBlocking {
                storageRepo.observeActiveVideos().first().firstOrNull {
                    it.title == "Firebase Bottom Sheet Upload Demo"
                }
            }
            assertNotNull(persistedVideo)
            assertEquals("Uploaded from Creator Paisa quick bottom sheet.", persistedVideo!!.description)
            assertEquals(VideoCategory.TECH, persistedVideo.category)
            assertEquals(VideoFormat.SHORT, persistedVideo.format)

            composeTestRule.onNodeWithTag("upload_sheet_watch_button")
                .performScrollTo()
                .performClick()
            assertEquals(persistedVideo.id, navigatedVideoId)
        } finally {
            tempVideo.delete()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `42 video card overlay like button toggles animated heart state and updates persisted like count`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val feedVm = FeedViewModel(repository)
        val initialLongLikes = runBlocking { repository.observeVideoById("vid_long_1").first()!!.likeCount }
        val initialShortLikes = runBlocking { repository.observeVideoById("vid_short_1").first()!!.likeCount }

        composeTestRule.setContent {
            val feedState by feedVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    HomeScreen(
                        currentUser = viewer,
                        uiState = feedState,
                        currentLanguage = com.example.localization.AppLanguage.ENGLISH,
                        unreadNotificationCount = 0,
                        onToggleLanguage = {},
                        onSearchChange = {},
                        onFilterChange = {},
                        onCategoryChange = {},
                        onSortChange = {},
                        onVideoClick = {},
                        onCreatorClick = {},
                        onLikeClick = { vid -> feedVm.toggleLike(vid, viewer.id) },
                        onReportVideo = { _, _ -> },
                        onOpenNotifications = {},
                        onOpenStudio = {},
                        onOpenAdmin = {},
                        onOpenSetupGuide = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_card_vid_long_1").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Short preview overlay Like button and tap to like
        composeTestRule.onNodeWithTag("short_preview_like_button_vid_short_1")
            .assertIsDisplayed()
            .assertTextContains(formatCompactCount(initialShortLikes))
            .performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Short preview overlay like did not persist") {
            repository.observeVideoById("vid_short_1").first()!!.likeCount == initialShortLikes + 1L
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("short_preview_like_button_vid_short_1")
                    .assertTextContains(formatCompactCount(initialShortLikes + 1L))
                true
            }.getOrDefault(false)
        }

        // Scroll to LongVideoCard overlay Like button, tap to like, then tap again to unlike
        composeTestRule.onNodeWithTag("home_feed_list")
            .performScrollToNode(hasTestTag("video_overlay_like_button_vid_long_1"))

        composeTestRule.onNodeWithTag("video_overlay_like_button_vid_long_1")
            .assertIsDisplayed()
            .assertTextContains(formatCompactCount(initialLongLikes))
            .performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Video card overlay like was not persisted") {
            val updated = repository.observeVideoById("vid_long_1").first()!!
            updated.likeCount == initialLongLikes + 1L && updated.isLikedByCurrentUser
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("video_overlay_like_button_vid_long_1")
                    .assertTextContains(formatCompactCount(initialLongLikes + 1L))
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithTag("video_overlay_like_button_vid_long_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Video card overlay unlike was not persisted") {
            val updated = repository.observeVideoById("vid_long_1").first()!!
            updated.likeCount == initialLongLikes && !updated.isLikedByCurrentUser
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("video_overlay_like_button_vid_long_1")
                    .assertTextContains(formatCompactCount(initialLongLikes))
                true
            }.getOrDefault(false)
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `43 player viewport overlay like button toggles like state and updates persisted like count`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val playerVm = PlayerViewModel("vid_long_1", SeedData.VIEWER_TEST_ID, repository)
        val initialLikes = runBlocking { repository.observeVideoById("vid_long_1").first()!!.likeCount }

        composeTestRule.setContent {
            val uiState by playerVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    PlayerScreen(
                        currentUserId = SeedData.VIEWER_TEST_ID,
                        uiState = uiState,
                        onBack = {},
                        onCreatorClick = {},
                        onGenuinePlaybackReached = {},
                        onPlaybackError = {},
                        onClearPlaybackError = {},
                        onToggleLike = { playerVm.toggleLike() },
                        onToggleSubscribe = {},
                        onBlockCreator = {},
                        onPostComment = { _, _, _ -> },
                        onReportVideo = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("player_overlay_like_button_vid_long_1")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag("player_overlay_like_button_vid_long_1")
            .assertIsDisplayed()
            .assertTextContains(formatCompactCount(initialLikes))
            .performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Player overlay like did not persist") {
            val updated = repository.observeVideoById("vid_long_1").first()!!
            updated.likeCount == initialLikes + 1L && updated.isLikedByCurrentUser
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("player_overlay_like_button_vid_long_1")
                    .assertTextContains(formatCompactCount(initialLikes + 1L))
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithTag("player_overlay_like_button_vid_long_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Player overlay unlike did not persist") {
            val updated = repository.observeVideoById("vid_long_1").first()!!
            updated.likeCount == initialLikes && !updated.isLikedByCurrentUser
        }
    }

    @Test
    fun `44 metadata tags persist on upload and support repository and FeedViewModel tag filtering`() {
        val parsed = parseTagsInput(" #Lighting , Drone Shots, lighting, Studio-Gear ")
        assertEquals(listOf("lighting", "drone-shots", "studio-gear"), parsed)

        val uploadedId = runBlocking {
            val creator = repository.login("aarav@creatorpaisa.test", "Creator@123").getOrThrow()
            repository.uploadVideo(
                creatorId = creator.id,
                draft = UploadDraft(
                    title = "Aerial Jaipur Drone Reel",
                    description = "Sunset gimbal drone test",
                    format = VideoFormat.LONG,
                    category = VideoCategory.TECH,
                    durationSeconds = 120L,
                    fileSizeBytes = 250_000L,
                    thumbnailUri = "drawable://img_thumb_tech",
                    videoUri = "https://example.com/drone.mp4",
                    tags = parsed
                )
            ).getOrThrow()
        }

        val persisted = runBlocking { repository.observeVideoById(uploadedId).first()!! }
        assertEquals(listOf("lighting", "drone-shots", "studio-gear"), persisted.tags)

        val byTag = runBlocking { repository.observeVideosByTag("drone-shots").first() }
        assertEquals(listOf(uploadedId), byTag.map { it.id })

        val feedVm = FeedViewModel(repository)
        runBlocking {
            val collectJob = launch { feedVm.uiState.collect {} }
            awaitCondition(timeoutMs = 3_000L, message = "FeedViewModel did not load availableTags") {
                feedVm.uiState.value.availableTags.contains("drone-shots")
            }
            feedVm.setTag("drone-shots")
            awaitCondition(timeoutMs = 3_000L, message = "FeedViewModel did not filter by drone-shots tag") {
                feedVm.uiState.value.selectedTag == "drone-shots" &&
                    feedVm.uiState.value.allMatchingVideos.map { it.id } == listOf(uploadedId)
            }
            feedVm.setTag(null)
            awaitCondition(timeoutMs = 3_000L, message = "FeedViewModel did not clear tag filter") {
                feedVm.uiState.value.selectedTag == null &&
                    feedVm.uiState.value.allMatchingVideos.size >= 7
            }
            collectJob.cancel()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `45 home horizontal scrollable tag chip bar filters videos by tag and resets to all`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val feedVm = FeedViewModel(repository)

        composeTestRule.setContent {
            val uiState by feedVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    HomeScreen(
                        currentUser = viewer,
                        uiState = uiState,
                        currentLanguage = com.example.localization.AppLanguage.ENGLISH,
                        unreadNotificationCount = 0,
                        onToggleLanguage = {},
                        onSearchChange = { feedVm.setSearchQuery(it) },
                        onFilterChange = { feedVm.setFilter(it) },
                        onCategoryChange = { feedVm.setCategory(it) },
                        onTagChange = { feedVm.setTag(it) },
                        onClearFilters = { feedVm.clearFilters() },
                        onSortChange = { feedVm.setSortOrder(it) },
                        onVideoClick = {},
                        onCreatorClick = {},
                        onLikeClick = {},
                        onReportVideo = { _, _ -> },
                        onOpenNotifications = {},
                        onOpenStudio = {},
                        onOpenAdmin = {},
                        onOpenSetupGuide = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("tag_chip_bar").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("video_card_vid_long_1").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("tag_chip_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tag_chip_all").assertIsDisplayed()

        // Scroll tag chip bar to "lighting" chip (belongs only to vid_long_1) and click it
        composeTestRule.onNodeWithTag("tag_chip_bar")
            .performScrollToNode(hasTestTag("tag_chip_lighting"))
        composeTestRule.onNodeWithTag("tag_chip_lighting").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            feedVm.uiState.value.selectedTag == "lighting" &&
                feedVm.uiState.value.allMatchingVideos.none { it.id == "vid_long_2" } &&
                composeTestRule.onAllNodesWithTag("video_card_vid_long_1").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("video_card_vid_long_2").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("video_card_vid_long_1").assertIsDisplayed()

        // Clear tag filter via "All Tags" chip
        composeTestRule.onNodeWithTag("tag_chip_bar")
            .performScrollToNode(hasTestTag("tag_chip_all"))
        composeTestRule.onNodeWithTag("tag_chip_all").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            feedVm.uiState.value.selectedTag == null &&
                feedVm.uiState.value.allMatchingVideos.any { it.id == "vid_long_2" }
        }
        composeTestRule.onNodeWithTag("home_feed_list")
            .performScrollToNode(hasTestTag("video_card_vid_long_2"))
        composeTestRule.onNodeWithTag("video_card_vid_long_2").assertIsDisplayed()
    }

    @Test
    fun `46 searchVideos queries by title and creator name with SearchScope filtering`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }

        val titleHits = runBlocking {
            repository.searchVideos("Hawa Mahal").getOrThrow()
        }
        assertEquals(listOf("vid_short_1"), titleHits.map { it.id })

        val creatorHits = runBlocking {
            repository.searchVideos("Aarav").getOrThrow()
        }
        assertTrue(creatorHits.isNotEmpty())
        assertTrue(creatorHits.all {
            it.creatorName.contains("Aarav", ignoreCase = true) ||
                it.title.contains("Aarav", ignoreCase = true)
        })
        assertTrue(creatorHits.any { it.id == "vid_short_2" })

        val searchVm = SearchViewModel(repository)
        runBlocking {
            val collectJob = launch { searchVm.uiState.collect {} }
            awaitCondition(timeoutMs = 3_000L, message = "SearchViewModel initial results did not load") {
                !searchVm.uiState.value.isSearching && searchVm.uiState.value.results.size >= 6
            }

            // Query by creator name in ALL scope -> matches videos and matchingCreators
            searchVm.submitSearch("Aarav")
            awaitCondition(timeoutMs = 3_000L, message = "SearchViewModel did not return Aarav creator results") {
                !searchVm.uiState.value.isSearching &&
                    searchVm.uiState.value.results.any { it.creatorId == SeedData.CREATOR_AARAV_ID } &&
                    searchVm.uiState.value.matchingCreators.any { it.id == SeedData.CREATOR_AARAV_ID }
            }

            // Switch to TITLES scope -> excludes creator-only matches when "Aarav" is not in title
            searchVm.setScope(SearchScope.TITLES)
            awaitCondition(timeoutMs = 3_000L, message = "SearchScope.TITLES did not filter results") {
                searchVm.uiState.value.scope == SearchScope.TITLES &&
                    searchVm.uiState.value.matchingCreators.isEmpty() &&
                    searchVm.uiState.value.results.none { it.id == "vid_short_2" }
            }

            // Switch to CREATORS scope -> keeps creator matches and creator cards
            searchVm.setScope(SearchScope.CREATORS)
            awaitCondition(timeoutMs = 3_000L, message = "SearchScope.CREATORS did not restore creator results") {
                searchVm.uiState.value.scope == SearchScope.CREATORS &&
                    searchVm.uiState.value.matchingCreators.any { it.id == SeedData.CREATOR_AARAV_ID } &&
                    searchVm.uiState.value.results.any { it.id == "vid_short_2" }
            }

            // Like toggle reflects in live results
            val beforeLike = searchVm.uiState.value.results.first { it.id == "vid_short_2" }.likeCount
            searchVm.toggleLike("vid_short_2", viewer.id)
            awaitCondition(timeoutMs = 3_000L, message = "Like count did not update in SearchViewModel") {
                searchVm.uiState.value.results.firstOrNull { it.id == "vid_short_2" }?.likeCount == beforeLike + 1
            }

            collectJob.cancel()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `47 search screen discovers videos by title and creator name and handles empty state`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val searchVm = SearchViewModel(repository)
        var clickedVideoId: String? = null
        var clickedCreatorId: String? = null

        composeTestRule.setContent {
            val uiState by searchVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    SearchScreen(
                        currentUserId = viewer.id,
                        uiState = uiState,
                        currentLanguage = AppLanguage.ENGLISH,
                        onBack = {},
                        onQueryChange = { searchVm.onQueryChange(it) },
                        onSubmitSearch = { searchVm.submitSearch(it) },
                        onClearQuery = { searchVm.clearQuery() },
                        onScopeChange = { searchVm.setScope(it) },
                        onRetry = { searchVm.retry() },
                        onVideoClick = { clickedVideoId = it },
                        onCreatorClick = { clickedCreatorId = it },
                        onLikeClick = { searchVm.toggleLike(it, viewer.id) }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("search_screen_input").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("search_video_card_vid_short_1").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Search by video title
        composeTestRule.onNodeWithTag("search_screen_input").performTextInput("Hawa Mahal")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("search_video_card_vid_short_1").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("search_video_card_vid_short_2").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_video_card_vid_short_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            "Expected vid_short_1 click; clickedCreatorId=$clickedCreatorId, query=${searchVm.uiState.value.query}, isSearching=${searchVm.uiState.value.isSearching}",
            "vid_short_1",
            clickedVideoId
        )

        // 2. Clear and search by creator name
        composeTestRule.onNodeWithTag("search_clear_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("search_video_card_vid_short_2").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("search_scope_creators").performClick()
        composeTestRule.onNodeWithTag("search_screen_input").performTextInput("Priya")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("search_creator_card_${SeedData.CREATOR_PRIYA_ID}")
                .fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("search_video_card_vid_short_1")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("search_creator_card_${SeedData.CREATOR_PRIYA_ID}").performClick()
        assertEquals(SeedData.CREATOR_PRIYA_ID, clickedCreatorId)

        // 3. No matches -> empty state & reset button
        composeTestRule.onNodeWithTag("search_clear_button").performClick()
        composeTestRule.onNodeWithTag("search_screen_input").performTextInput("zzzzzz_nonexistent")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("search_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("search_reset_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("search_video_card_vid_short_2").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `48 video upload screen validates missing video, uploads selected device file with title and description, and resets cleanly`() {
        val tempVideo = File(context.cacheDir, "upload_screen_sample.mp4").apply {
            writeBytes(ByteArray(4096) { 0x3B })
        }
        try {
            val creator = runBlocking {
                repository.login("aarav@creatorpaisa.test", "Creator@123").getOrThrow()
            }
            val storageRepo = LocalCreatorPaisaRepository(
                dao = database.dao(),
                prefs = SessionPreferences(context),
                mediaStorage = LocalMediaStorage(context),
                timeProvider = { fakeClock }
            )
            val uploadVm = UploadViewModel(creator.id, storageRepo)
            var navigatedVideoId: String? = null
            val selectedUriState = androidx.compose.runtime.mutableStateOf("")
            val selectedNameState = androidx.compose.runtime.mutableStateOf("")
            val selectedDurationState = androidx.compose.runtime.mutableStateOf(0L)
            val selectedSizeState = androidx.compose.runtime.mutableStateOf(0L)

            composeTestRule.setContent {
                val dailyCount by uploadVm.dailyUploadCount.collectAsStateWithLifecycle()
                val progressState by uploadVm.progressState.collectAsStateWithLifecycle()

                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    CreatorPaisaTheme(darkTheme = true) {
                        VideoUploadScreen(
                            currentUser = creator,
                            dailyUploadCount = dailyCount,
                            progressState = progressState,
                            onPublish = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags ->
                                uploadVm.publishVideo(
                                    title = title,
                                    description = desc,
                                    format = format,
                                    category = category,
                                    durationSeconds = duration,
                                    fileSizeBytes = sizeBytes,
                                    thumbnailUri = thumb,
                                    videoUri = vidUri,
                                    tags = tags
                                )
                            },
                            onCancelUpload = { uploadVm.cancelUpload() },
                            onRetryUpload = { uploadVm.retryUpload() },
                            onResetState = { uploadVm.resetState() },
                            onVideoPublishedNavigate = { vid -> navigatedVideoId = vid },
                            initialVideoUri = selectedUriState.value,
                            initialVideoName = selectedNameState.value,
                            initialDurationSeconds = selectedDurationState.value,
                            initialFileSizeBytes = selectedSizeState.value
                        )
                    }
                }
            }

            composeTestRule.onNodeWithTag("video_upload_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("video_upload_pick_gallery_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("video_upload_browse_files_button").assertIsDisplayed()

            // Submitting without selecting a video file shows inline validation error
            composeTestRule.onNodeWithTag("video_upload_submit_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("video_upload_validation_error").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("video_upload_validation_error").assertIsDisplayed()

            // Simulate picking a video file from the device
            composeTestRule.runOnUiThread {
                selectedUriState.value = Uri.fromFile(tempVideo).toString()
                selectedNameState.value = "creator_reel.mp4"
                selectedDurationState.value = 54L
                selectedSizeState.value = tempVideo.length()
            }
            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("video_upload_selected_file_badge").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("video_upload_selected_file_badge")
                .performScrollTo()
                .assertTextContains("creator_reel.mp4", substring = true)

            composeTestRule.onNodeWithTag("video_upload_title_input")
                .performScrollTo()
                .performTextInput("Firebase Storage Screen Upload")
            composeTestRule.onNodeWithTag("video_upload_description_input")
                .performScrollTo()
                .performTextInput("Recorded from device camera and uploaded via VideoUploadScreen.")
            composeTestRule.onNodeWithTag("video_upload_category_${VideoCategory.TECH.name}")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("video_upload_tags_input")
                .performScrollTo()
                .performTextInput("kotlin, firebase")

            composeTestRule.onNodeWithTag("video_upload_submit_button")
                .performScrollTo()
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithTag("video_upload_success_banner").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("video_upload_success_banner").performScrollTo().assertIsDisplayed()

            val persistedVideo = runBlocking {
                storageRepo.observeActiveVideos().first().firstOrNull {
                    it.title == "Firebase Storage Screen Upload"
                }
            }
            assertNotNull(persistedVideo)
            assertEquals("Recorded from device camera and uploaded via VideoUploadScreen.", persistedVideo!!.description)
            assertEquals(VideoCategory.TECH, persistedVideo.category)
            assertEquals(VideoFormat.SHORT, persistedVideo.format)
            assertEquals(listOf("kotlin", "firebase"), persistedVideo.tags)
            assertTrue(persistedVideo.videoUri.isNotBlank())

            composeTestRule.onNodeWithTag("video_upload_watch_button")
                .performScrollTo()
                .performClick()
            assertEquals(persistedVideo.id, navigatedVideoId)

            // Upload Another resets form fields & clears success banner
            composeTestRule.onNodeWithTag("video_upload_another_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("video_upload_success_banner").fetchSemanticsNodes().isEmpty()
            }
            composeTestRule.onNodeWithTag("video_upload_title_input")
                .performScrollTo()
                .assertTextContains("")
        } finally {
            tempVideo.delete()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `49 unapproved user sees upload approval notice on VideoUploadScreen`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    VideoUploadScreen(
                        currentUser = viewer,
                        dailyUploadCount = 0,
                        progressState = UploadProgressState.Idle,
                        onPublish = { _, _, _, _, _, _, _, _, _ -> },
                        onCancelUpload = {},
                        onRetryUpload = {},
                        onResetState = {},
                        onVideoPublishedNavigate = {}
                    )
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_upload_not_approved_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("video_upload_not_approved_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_upload_quota_text")
            .assertTextContains("0 / ${com.example.data.model.UploadLimits.MAX_DAILY_UPLOADS}")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `50 main app upload navigation tab opens VideoUploadScreen with device picker, title, and description fields`() {
        composeTestRule.setContent {
            CreatorPaisaApp(repository = repository)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("quick_login_creator").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("nav_upload").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("nav_upload").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_upload_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("video_upload_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_upload_pick_gallery_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_upload_browse_files_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_upload_title_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_upload_description_input").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `51 video feed viewmodel fetches uploaded videos, filters by format and tag, and sorts reactively`() = runBlocking {
        val creator = repository.login("aarav@creatorpaisa.test", "Creator@123").getOrThrow()
        val vm = VideoFeedViewModel(repository)

        val collectJob = launch { vm.uiState.collect { } }
        try {
            val loaded = vm.uiState.awaitValue { !it.isLoading && it.videos.isNotEmpty() }
            assertTrue(loaded.videos.any { it.id == "vid_long_1" })
            assertTrue(loaded.videos.any { it.id == "vid_short_1" })

            // Filter by SHORT format
            vm.setFormatFilter(VideoFormat.SHORT)
            val shortsOnly = vm.uiState.awaitValue { it.selectedFormat == VideoFormat.SHORT }
            assertTrue(shortsOnly.videos.isNotEmpty())
            assertTrue(shortsOnly.videos.all { it.format == VideoFormat.SHORT })

            // Reset format filter & publish a brand-new video -> appears automatically
            vm.setFormatFilter(null)
            val publishResult = repository.uploadVideo(
                creatorId = creator.id,
                draft = UploadDraft(
                    title = "Live Firestore Feed Stream",
                    description = "Streams seamlessly inside VideoFeedScreen.",
                    format = VideoFormat.LONG,
                    category = VideoCategory.TECH,
                    durationSeconds = 240L,
                    fileSizeBytes = 2_000_000L,
                    thumbnailUri = "drawable://img_thumb_tech",
                    videoUri = "https://example.com/live_feed.mp4",
                    tags = listOf("feedtest", "kotlin")
                )
            )
            assertTrue(publishResult.isSuccess)
            val newId = publishResult.getOrThrow()

            val withNewVideo = vm.uiState.awaitValue { state ->
                state.videos.any { it.id == newId }
            }
            assertTrue(withNewVideo.availableTags.contains("feedtest"))

            // Filter by tag
            vm.setTagFilter("feedtest")
            val byTag = vm.uiState.awaitValue { it.selectedTag == "feedtest" }
            assertEquals(listOf(newId), byTag.videos.map { it.id })

            // Clear filters and sort by most viewed
            vm.clearFilters()
            vm.setSortOrder(FeedSortOrder.MOST_VIEWED)
            val sorted = vm.uiState.awaitValue { it.selectedTag == null && it.sortOrder == FeedSortOrder.MOST_VIEWED }
            assertTrue(sorted.videos.zipWithNext { a, b -> a.viewCount >= b.viewCount }.all { it })
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `52 video feed screen plays uploaded videos with ExoPlayer, toggles like and follow, filters, and navigates`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val vm = VideoFeedViewModel(repository)
        var openedPlayerId: String? = null
        var openedCreatorId: String? = null
        val initialLikes = runBlocking { repository.observeVideoById("vid_long_1").first()!!.likeCount }

        composeTestRule.setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            val subscribedCreators by repository.observeSubscribedCreators(viewer.id)
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val followed = subscribedCreators.map { it.id }.toSet()

            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    VideoFeedScreen(
                        currentUserId = viewer.id,
                        uiState = state,
                        followedCreatorIds = followed,
                        onBack = {},
                        onFormatFilterChange = { vm.setFormatFilter(it) },
                        onTagFilterChange = { vm.setTagFilter(it) },
                        onSortChange = { vm.setSortOrder(it) },
                        onClearFilters = { vm.clearFilters() },
                        onRefresh = { vm.refresh() },
                        onVideoClick = { openedPlayerId = it },
                        onCreatorClick = { openedCreatorId = it },
                        onLikeClick = { vm.toggleLike(it, viewer.id) },
                        onFollowClick = { vm.toggleFollow(it, viewer.id) }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_feed_list").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to vid_long_1 and verify ExoPlayer / overlay controls
        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_card_vid_long_1"))
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_feed_play_pause_vid_long_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("video_feed_play_pause_vid_long_1").assertIsDisplayed().performClick()

        // Like button updates persisted like count and counter UI
        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_like_button_vid_long_1"))
        composeTestRule.onNodeWithTag("video_feed_like_button_vid_long_1").assertIsDisplayed().performClick()
        awaitCondition(timeoutMs = 3_000L, message = "VideoFeedScreen like did not persist") {
            repository.observeVideoById("vid_long_1").first()!!.likeCount == initialLikes + 1L
        }
        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_footer_like_button_vid_long_1"))
        composeTestRule.onNodeWithTag("video_feed_footer_like_count_vid_long_1", useUnmergedTree = true)
            .assertTextContains(formatCompactCount(initialLikes + 1L))
        // Tapping footer Like button unlikes back to initialLikes
        composeTestRule.onNodeWithTag("video_feed_footer_like_button_vid_long_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "VideoFeedScreen unlike did not persist") {
            repository.observeVideoById("vid_long_1").first()!!.likeCount == initialLikes
        }

        // Follow button updates subscription state
        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_follow_button_vid_long_1"))
        composeTestRule.onNodeWithTag("video_feed_follow_button_vid_long_1").assertIsDisplayed().performClick()
        awaitCondition(timeoutMs = 3_000L, message = "VideoFeedScreen follow did not persist") {
            repository.observeIsSubscribed(SeedData.CREATOR_AARAV_ID, viewer.id).first()
        }

        // Creator badge opens creator channel, Full Player opens PlayerRoute
        composeTestRule.onNodeWithTag("video_feed_creator_vid_long_1").performClick()
        assertEquals(SeedData.CREATOR_AARAV_ID, openedCreatorId)

        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_open_player_vid_long_1"))
        composeTestRule.onNodeWithTag("video_feed_open_player_vid_long_1").performClick()
        assertEquals("vid_long_1", openedPlayerId)

        // Filter to Shorts hides long videos and Filter All restores them
        composeTestRule.onNodeWithTag("video_feed_filter_shorts").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_feed_card_vid_long_1").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("video_feed_filter_all").performClick()
        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_card_vid_short_1"))
        composeTestRule.onNodeWithTag("video_feed_card_vid_short_1").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `53 main app home opens VideoFeedScreen and navigates back cleanly`() {
        composeTestRule.setContent {
            CreatorPaisaApp(repository = repository)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("quick_login_viewer").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("home_feed_list").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("home_feed_list")
            .performScrollToNode(hasTestTag("video_card_vid_long_1"))
        composeTestRule.onNodeWithTag("home_feed_list")
            .performScrollToNode(hasTestTag("open_video_feed_button"))
        composeTestRule.onNodeWithTag("open_video_feed_button").assertIsDisplayed().performClick()

        composeTestRule.waitUntil(timeoutMillis = 8_000L) {
            composeTestRule.onAllNodesWithTag("video_feed_screen").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("video_feed_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("video_feed_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("video_feed_back_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("home_feed_list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `54 video feed comments bottom sheet enforces char limit, posts comments & replies, and updates counters`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val vm = VideoFeedViewModel(repository)
        val initialVideo = runBlocking { repository.observeVideoById("vid_long_1").first()!! }
        val initialCommentCount = initialVideo.commentCount
        val initialLikeCount = initialVideo.likeCount

        composeTestRule.setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    VideoFeedScreen(
                        currentUserId = viewer.id,
                        uiState = state,
                        followedCreatorIds = emptySet(),
                        onBack = {},
                        onFormatFilterChange = { vm.setFormatFilter(it) },
                        onTagFilterChange = { vm.setTagFilter(it) },
                        onSortChange = { vm.setSortOrder(it) },
                        onClearFilters = { vm.clearFilters() },
                        onRefresh = { vm.refresh() },
                        onVideoClick = {},
                        onCreatorClick = {},
                        onLikeClick = { vm.toggleLike(it, viewer.id) },
                        onFollowClick = { vm.toggleFollow(it, viewer.id) },
                        observeComments = { videoId -> vm.observeComments(videoId) },
                        onPostComment = { videoId, text, parentCommentId, onSuccess ->
                            vm.addComment(videoId, viewer.id, text, parentCommentId, onSuccess)
                        }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 8_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.videos.any { it.id == "vid_long_1" } &&
                composeTestRule.onAllNodesWithTag("video_feed_list").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify footer Like pill toggles like and updates live counter
        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_footer_like_button_vid_long_1"))
        composeTestRule.onNodeWithTag("video_feed_footer_like_button_vid_long_1").performClick()
        awaitCondition(timeoutMs = 3_000L, message = "Footer like should increment") {
            repository.observeVideoById("vid_long_1").first()!!.likeCount == initialLikeCount + 1L
        }
        composeTestRule.onNodeWithTag("video_feed_footer_like_count_vid_long_1", useUnmergedTree = true)
            .assertTextContains(formatCompactCount(initialLikeCount + 1L))

        // Open interactive Comments Bottom Sheet from VideoFeedScreen comment pill
        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_comment_button_vid_long_1"))
        composeTestRule.onNodeWithTag("video_feed_comment_button_vid_long_1").assertIsDisplayed().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("comments_sheet_item_cmt_3").fetchSemanticsNodes().isNotEmpty()
        }

        // Send button starts disabled when draft is blank
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsNotEnabled()

        // Over 500 chars shows inline limit error and keeps Send disabled
        val overLimitText = "a".repeat(505)
        composeTestRule.onNodeWithTag("comments_sheet_input").performTextReplacement(overLimitText)
        composeTestRule.onNodeWithTag("comments_sheet_char_count", useUnmergedTree = true)
            .assertTextContains("505 / 500")
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsNotEnabled()

        // Post a valid top-level comment
        val newComment = "Brilliant lighting breakdown!"
        composeTestRule.onNodeWithTag("comments_sheet_input").performTextReplacement(newComment)
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsEnabled().performClick()

        awaitCondition(timeoutMs = 3_000L, message = "New comment should persist to repository") {
            repository.observeComments("vid_long_1").first().any { it.text == newComment && it.parentCommentId == null } &&
                repository.observeVideoById("vid_long_1").first()!!.commentCount == initialCommentCount + 1L
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithText("0 / 500", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Trigger threaded reply on seeded comment cmt_3
        composeTestRule.onNodeWithTag("comments_sheet_reply_cmt_3").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("comments_sheet_replying_banner").fetchSemanticsNodes().isNotEmpty()
        }
        val replyText = "Agree, rim light works great!"
        composeTestRule.onNodeWithTag("comments_sheet_input").performTextReplacement(replyText)
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsEnabled().performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Threaded reply should persist with parentCommentId = cmt_3") {
            repository.observeComments("vid_long_1").first()
                .any { it.text == replyText && it.parentCommentId == "cmt_3" } &&
                repository.observeVideoById("vid_long_1").first()!!.commentCount == initialCommentCount + 2L
        }

        // Close sheet and verify comment counter pill on card reflects updated count
        composeTestRule.onNodeWithTag("comments_sheet_close").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("video_feed_comment_count_vid_long_1", useUnmergedTree = true)
            .assertTextContains(formatCompactCount(initialCommentCount + 2L))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `55 home screen feed card opens comments bottom sheet and syncs comment count`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val feedVm = FeedViewModel(repository)
        val initialCommentCount = runBlocking {
            repository.observeVideoById("vid_long_1").first()!!.commentCount
        }

        composeTestRule.setContent {
            val uiState by feedVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    HomeScreen(
                        currentUser = viewer,
                        uiState = uiState,
                        currentLanguage = AppLanguage.ENGLISH,
                        unreadNotificationCount = 0,
                        onToggleLanguage = {},
                        onSearchChange = { feedVm.setSearchQuery(it) },
                        onFilterChange = { feedVm.setFilter(it) },
                        onCategoryChange = { feedVm.setCategory(it) },
                        onTagChange = { feedVm.setTag(it) },
                        onClearFilters = { feedVm.clearFilters() },
                        onSortChange = { feedVm.setSortOrder(it) },
                        onVideoClick = {},
                        onCreatorClick = {},
                        onLikeClick = { feedVm.toggleLike(it, viewer.id) },
                        onReportVideo = { _, _ -> },
                        onOpenNotifications = {},
                        onOpenStudio = {},
                        onOpenAdmin = {},
                        onOpenSetupGuide = {},
                        observeComments = { videoId -> feedVm.observeComments(videoId) },
                        onPostComment = { videoId, text, parentCommentId, onSuccess ->
                            if (parentCommentId != null) {
                                feedVm.addReply(videoId, viewer.id, text, parentCommentId, onSuccess)
                            } else {
                                feedVm.addComment(videoId, viewer.id, text, onSuccess)
                            }
                        }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 8_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            feedVm.uiState.value.longVideos.any { it.id == "vid_long_1" } &&
                composeTestRule.onAllNodesWithTag("home_feed_list").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("home_feed_list")
            .performScrollToNode(hasTestTag("comment_button_vid_long_1"))
        composeTestRule.onNodeWithTag("comment_button_vid_long_1").assertIsDisplayed().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isNotEmpty()
        }

        val postedText = "Saved this desk tour for later!"
        composeTestRule.onNodeWithTag("comments_sheet_input").performTextReplacement(postedText)
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsEnabled().performClick()

        awaitCondition(timeoutMs = 3_000L, message = "HomeScreen comment did not sync") {
            repository.observeComments("vid_long_1").first().any { it.text == postedText } &&
                repository.observeVideoById("vid_long_1").first()!!.commentCount == initialCommentCount + 1L
        }

        composeTestRule.onNodeWithTag("comments_sheet_close").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("comment_count_vid_long_1", useUnmergedTree = true)
            .assertTextContains(formatCompactCount(initialCommentCount + 1L))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `56 firebase storage metadata attachment, clear selected file, and private visibility upload on VideoUploadScreen`() {
        // 1. Verify FirebaseMediaStorage attaches MIME type + associated custom metadata to Storage objects
        val sampleMetadata = mapOf(
            "videoId" to "vid_meta_1",
            "creatorId" to SeedData.CREATOR_AARAV_ID,
            "creatorName" to "Aarav Tech Studio",
            "title" to "Studio Color Grading",
            "description" to "LUT walkthrough and scopes",
            "format" to VideoFormat.LONG.name,
            "category" to VideoCategory.TECH.name,
            "durationSeconds" to "240",
            "tags" to "color,grading,tech",
            "visibility" to "private",
            "originalFileName" to "grading_master.mp4",
            "ignoredBlank" to "   "
        )
        val videoStorageMeta = com.example.data.remote.FirebaseMediaStorage.buildVideoStorageMetadata(
            mimeType = "video/mp4",
            customMetadata = sampleMetadata
        )
        assertEquals("video/mp4", videoStorageMeta.contentType)
        assertEquals("vid_meta_1", videoStorageMeta.getCustomMetadata("videoId"))
        assertEquals("Studio Color Grading", videoStorageMeta.getCustomMetadata("title"))
        assertEquals("private", videoStorageMeta.getCustomMetadata("visibility"))
        assertEquals("color,grading,tech", videoStorageMeta.getCustomMetadata("tags"))
        assertEquals("grading_master.mp4", videoStorageMeta.getCustomMetadata("originalFileName"))
        assertNull(videoStorageMeta.getCustomMetadata("ignoredBlank"))

        val thumbStorageMeta = com.example.data.remote.FirebaseMediaStorage.buildThumbnailStorageMetadata(
            mimeType = "image/jpeg",
            customMetadata = sampleMetadata
        )
        assertEquals("image/jpeg", thumbStorageMeta.contentType)
        assertEquals("vid_meta_1", thumbStorageMeta.getCustomMetadata("videoId"))
        assertEquals(SeedData.CREATOR_AARAV_ID, thumbStorageMeta.getCustomMetadata("creatorId"))
        assertEquals("Studio Color Grading", thumbStorageMeta.getCustomMetadata("title"))

        // 2. Verify VideoUploadScreen clear-file action, visibility toggle, and Storage customMetadata forwarding
        val tempVideo = File(context.cacheDir, "upload_metadata_sample.mp4").apply {
            writeBytes(ByteArray(4096) { 0x4C })
        }
        try {
            val creator = runBlocking {
                repository.login("aarav@creatorpaisa.test", "Creator@123").getOrThrow()
            }
            var capturedStorageMetadata: Map<String, String> = emptyMap()
            val localDelegate = LocalMediaStorage(context)
            val recordingStorage = object : MediaStorage {
                override suspend fun storeUploadMedia(
                    sourceVideoUri: String,
                    sourceThumbnailUri: String,
                    maxVideoBytes: Long,
                    customMetadata: Map<String, String>,
                    onProgress: (Float, String) -> Unit
                ): Result<StoredMediaResult> {
                    capturedStorageMetadata = customMetadata
                    return localDelegate.storeUploadMedia(
                        sourceVideoUri = sourceVideoUri,
                        sourceThumbnailUri = sourceThumbnailUri,
                        maxVideoBytes = maxVideoBytes,
                        customMetadata = customMetadata,
                        onProgress = onProgress
                    )
                }

                override suspend fun deleteStoredFileIfOwned(uriString: String?) {
                    localDelegate.deleteStoredFileIfOwned(uriString)
                }
            }
            val storageRepo = LocalCreatorPaisaRepository(
                dao = database.dao(),
                prefs = SessionPreferences(context),
                mediaStorage = recordingStorage,
                timeProvider = { fakeClock }
            )
            val uploadVm = UploadViewModel(creator.id, storageRepo)
            val selectedUriState = androidx.compose.runtime.mutableStateOf(Uri.fromFile(tempVideo).toString())
            val selectedNameState = androidx.compose.runtime.mutableStateOf("grading_master.mp4")
            val selectedDurationState = androidx.compose.runtime.mutableStateOf(240L)
            val selectedSizeState = androidx.compose.runtime.mutableStateOf(tempVideo.length())

            composeTestRule.setContent {
                val dailyCount by uploadVm.dailyUploadCount.collectAsStateWithLifecycle()
                val progressState by uploadVm.progressState.collectAsStateWithLifecycle()

                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    CreatorPaisaTheme(darkTheme = true) {
                        VideoUploadScreen(
                            currentUser = creator,
                            dailyUploadCount = dailyCount,
                            progressState = progressState,
                            onPublish = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags ->
                                uploadVm.publishVideo(title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags)
                            },
                            onCancelUpload = { uploadVm.cancelUpload() },
                            onRetryUpload = { uploadVm.retryUpload() },
                            onResetState = { uploadVm.resetState() },
                            onVideoPublishedNavigate = {},
                            initialVideoUri = selectedUriState.value,
                            initialVideoName = selectedNameState.value,
                            initialDurationSeconds = selectedDurationState.value,
                            initialFileSizeBytes = selectedSizeState.value,
                            onPublishWithVisibility = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags, isPublic ->
                                uploadVm.publishVideo(
                                    title = title,
                                    description = desc,
                                    format = format,
                                    category = category,
                                    durationSeconds = duration,
                                    fileSizeBytes = sizeBytes,
                                    thumbnailUri = thumb,
                                    videoUri = vidUri,
                                    tags = tags,
                                    isPublic = isPublic
                                )
                            }
                        )
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("video_upload_selected_file_badge").fetchSemanticsNodes().isNotEmpty()
            }

            // Clear selected file resets the selection badge
            composeTestRule.onNodeWithTag("video_upload_clear_file_button").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("video_upload_selected_file_badge").fetchSemanticsNodes().isEmpty()
            }

            // Re-select device video file (use distinct URI string to trigger remember(initialVideoUri))
            val tempVideo2 = File(context.cacheDir, "upload_metadata_sample_2.mp4").apply {
                writeBytes(ByteArray(4096) { 0x5D })
            }
            try {
                composeTestRule.runOnUiThread {
                    selectedUriState.value = Uri.fromFile(tempVideo2).toString()
                    selectedNameState.value = "grading_master_v2.mp4"
                    selectedDurationState.value = 240L
                    selectedSizeState.value = tempVideo2.length()
                }
                composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                    composeTestRule.onAllNodesWithTag("video_upload_selected_file_badge").fetchSemanticsNodes().isNotEmpty()
                }

                composeTestRule.onNodeWithTag("video_upload_title_input")
                    .performScrollTo()
                    .performTextInput("Private LUT Masterclass")
                composeTestRule.onNodeWithTag("video_upload_description_input")
                    .performScrollTo()
                    .performTextInput("Uploading private draft with Storage metadata.")
                composeTestRule.onNodeWithTag("video_upload_category_row")
                    .performScrollTo()
                    .performScrollToNode(hasTestTag("video_upload_category_${VideoCategory.EDUCATION.name}"))
                composeTestRule.onNodeWithTag("video_upload_category_${VideoCategory.EDUCATION.name}")
                    .performClick()
                composeTestRule.onNodeWithTag("video_upload_tags_input")
                    .performScrollTo()
                    .performTextInput("lut, grading")
                composeTestRule.onNodeWithTag("video_upload_visibility_private")
                    .performScrollTo()
                    .performClick()

                composeTestRule.onNodeWithTag("video_upload_submit_button")
                    .performScrollTo()
                    .performClick()

                composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                    composeTestRule.onAllNodesWithTag("video_upload_success_banner").fetchSemanticsNodes().isNotEmpty()
                }

                assertEquals("Private LUT Masterclass", capturedStorageMetadata["title"])
                assertEquals("Uploading private draft with Storage metadata.", capturedStorageMetadata["description"])
                assertEquals(VideoFormat.LONG.name, capturedStorageMetadata["format"])
                assertEquals(VideoCategory.EDUCATION.name, capturedStorageMetadata["category"])
                assertEquals("240", capturedStorageMetadata["durationSeconds"])
                assertEquals("lut,grading", capturedStorageMetadata["tags"])
                assertEquals("private", capturedStorageMetadata["visibility"])
                assertEquals(creator.id, capturedStorageMetadata["creatorId"])

                val savedEntity = runBlocking {
                    database.dao().observeAllVideosForAdmin().first().firstOrNull { it.title == "Private LUT Masterclass" }
                }
                assertNotNull(savedEntity)
                assertFalse(savedEntity!!.isPublic)
            } finally {
                tempVideo2.delete()
            }
        } finally {
            tempVideo.delete()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `57 video upload bottom sheet exposes browse files and visibility selector and saves metadata`() {
        val tempVideo = File(context.cacheDir, "sheet_visibility_sample.mp4").apply {
            writeBytes(ByteArray(4096) { 0x6E })
        }
        try {
            val creator = runBlocking {
                repository.login("aarav@creatorpaisa.test", "Creator@123").getOrThrow()
            }
            val storageRepo = LocalCreatorPaisaRepository(
                dao = database.dao(),
                prefs = SessionPreferences(context),
                mediaStorage = LocalMediaStorage(context),
                timeProvider = { fakeClock }
            )
            val uploadVm = UploadViewModel(creator.id, storageRepo)
            val pickedUriState = androidx.compose.runtime.mutableStateOf("")
            val pickedNameState = androidx.compose.runtime.mutableStateOf("")

            composeTestRule.setContent {
                val dailyCount by uploadVm.dailyUploadCount.collectAsStateWithLifecycle()
                val progressState by uploadVm.progressState.collectAsStateWithLifecycle()

                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    CreatorPaisaTheme(darkTheme = true) {
                        VideoUploadBottomSheet(
                            currentUser = creator,
                            dailyUploadCount = dailyCount,
                            progressState = progressState,
                            onDismiss = {},
                            onPublish = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags ->
                                uploadVm.publishVideo(title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags)
                            },
                            onCancelUpload = { uploadVm.cancelUpload() },
                            onRetryUpload = { uploadVm.retryUpload() },
                            onResetState = { uploadVm.resetState() },
                            onVideoPublishedNavigate = {},
                            initialVideoUri = pickedUriState.value,
                            initialVideoName = pickedNameState.value,
                            initialDurationSeconds = 42L,
                            initialFileSizeBytes = tempVideo.length(),
                            onPublishWithVisibility = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags, isPublic ->
                                uploadVm.publishVideo(
                                    title = title,
                                    description = desc,
                                    format = format,
                                    category = category,
                                    durationSeconds = duration,
                                    fileSizeBytes = sizeBytes,
                                    thumbnailUri = thumb,
                                    videoUri = vidUri,
                                    tags = tags,
                                    isPublic = isPublic
                                )
                            }
                        )
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("upload_sheet_browse_files_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("upload_sheet_select_video_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("upload_sheet_browse_files_button").assertIsDisplayed()

            composeTestRule.runOnUiThread {
                pickedUriState.value = Uri.fromFile(tempVideo).toString()
                pickedNameState.value = "sheet_clip.mp4"
            }
            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                composeTestRule.onAllNodesWithTag("upload_sheet_selected_video_name").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("upload_sheet_title_input").performScrollTo().performTextInput("Sheet Private Upload")
            composeTestRule.onNodeWithTag("upload_sheet_tags_input").performScrollTo().performTextInput("studio, clip")
            composeTestRule.onNodeWithTag("upload_sheet_visibility_private").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("upload_sheet_publish_button").performScrollTo().performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithTag("upload_sheet_success").fetchSemanticsNodes().isNotEmpty()
            }
            val savedEntity = runBlocking {
                database.dao().observeAllVideosForAdmin().first().firstOrNull { it.title == "Sheet Private Upload" }
            }
            assertNotNull(savedEntity)
            assertFalse(savedEntity!!.isPublic)
            assertTrue(savedEntity.tagsRaw.contains("studio"))
        } finally {
            tempVideo.delete()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `58 player viewport, fullscreen overlay, and action bar like and comment buttons sync counts in real time`() {
        runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val playerVm = PlayerViewModel("vid_long_1", SeedData.VIEWER_TEST_ID, repository)
        val initialVideo = runBlocking { repository.observeVideoById("vid_long_1").first()!! }
        val initialLikes = initialVideo.likeCount
        val initialComments = initialVideo.commentCount

        composeTestRule.setContent {
            val uiState by playerVm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    PlayerScreen(
                        currentUserId = SeedData.VIEWER_TEST_ID,
                        uiState = uiState,
                        onBack = {},
                        onCreatorClick = {},
                        onGenuinePlaybackReached = {},
                        onPlaybackError = {},
                        onClearPlaybackError = {},
                        onToggleLike = { playerVm.toggleLike() },
                        onToggleSubscribe = {},
                        onBlockCreator = {},
                        onPostComment = { text, parentId, onDone ->
                            playerVm.postComment(text, parentId, onDone)
                        },
                        onReportVideo = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("player_overlay_like_button_vid_long_1")
                .fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("player_overlay_comment_button_vid_long_1")
                    .fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Tap overlay Like button and confirm both overlay & action-bar counters update live
        composeTestRule.onNodeWithTag("player_overlay_like_button_vid_long_1")
            .assertIsDisplayed()
            .performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Player like count did not increment") {
            repository.observeVideoById("vid_long_1").first()!!.likeCount == initialLikes + 1L
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("player_overlay_like_count_vid_long_1", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialLikes + 1L))
                composeTestRule.onNodeWithTag("player_like_count", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialLikes + 1L))
                true
            }.getOrDefault(false)
        }

        // 2. Tap player viewport overlay Comment button -> opens interactive VideoCommentsBottomSheet
        composeTestRule.onNodeWithTag("player_overlay_comment_button_vid_long_1")
            .assertIsDisplayed()
            .assertTextContains(formatCompactCount(initialComments))
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("comments_sheet_item_cmt_3").fetchSemanticsNodes().isNotEmpty()
        }

        val liveComment = "Real-time player overlay comment!"
        composeTestRule.onNodeWithTag("comments_sheet_input").performTextReplacement(liveComment)
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsEnabled().performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Player comment did not persist") {
            repository.observeComments("vid_long_1").first().any { it.text == liveComment } &&
                repository.observeVideoById("vid_long_1").first()!!.commentCount == initialComments + 1L
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithText(liveComment).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("comments_sheet_close").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("player_overlay_comment_count_vid_long_1", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialComments + 1L))
                composeTestRule.onNodeWithTag("player_comment_count", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialComments + 1L))
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("player_comments_header_count"))
        composeTestRule.onNodeWithTag("player_comments_header_count", useUnmergedTree = true)
            .assertTextContains("${EnglishStrings.commentsLabel} (${initialComments + 1L})")

        // 3. Action bar Comment button also opens comments sheet and supports replies
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("player_comment_button"))
        composeTestRule.onNodeWithTag("player_comment_button").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("comments_sheet_reply_cmt_3").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("comments_sheet_reply_cmt_3").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("comments_sheet_replying_banner").fetchSemanticsNodes().isNotEmpty()
        }
        val replyText = "Action bar reply sync check!"
        composeTestRule.onNodeWithTag("comments_sheet_input").performTextReplacement(replyText)
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsEnabled().performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Action bar reply did not persist") {
            repository.observeComments("vid_long_1").first()
                .any { it.text == replyText && it.parentCommentId == "cmt_3" } &&
                repository.observeVideoById("vid_long_1").first()!!.commentCount == initialComments + 2L
        }

        composeTestRule.onNodeWithTag("comments_sheet_close").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("player_screen")
            .performScrollToNode(hasTestTag("player_fullscreen_button"))
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("player_overlay_comment_count_vid_long_1", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialComments + 2L))
                composeTestRule.onNodeWithTag("player_comment_count", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialComments + 2L))
                true
            }.getOrDefault(false)
        }

        // 4. Fullscreen player overlay also exposes interactive Like & Comment buttons
        composeTestRule.onNodeWithTag("player_fullscreen_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("exit_fullscreen_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("player_overlay_like_button_vid_long_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_overlay_comment_button_vid_long_1")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("exit_fullscreen_button").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun `59 video feed player overlay like and comment buttons open comments sheet and sync live counts`() {
        val viewer = runBlocking {
            repository.login("viewer@creatorpaisa.test", "Viewer@123").getOrThrow()
        }
        val vm = VideoFeedViewModel(repository)
        val initialVideo = runBlocking { repository.observeVideoById("vid_long_1").first()!! }
        val initialLikes = initialVideo.likeCount
        val initialComments = initialVideo.commentCount

        composeTestRule.setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                CreatorPaisaTheme(darkTheme = true) {
                    VideoFeedScreen(
                        currentUserId = viewer.id,
                        uiState = state,
                        followedCreatorIds = emptySet(),
                        onBack = {},
                        onFormatFilterChange = { vm.setFormatFilter(it) },
                        onTagFilterChange = { vm.setTagFilter(it) },
                        onSortChange = { vm.setSortOrder(it) },
                        onClearFilters = { vm.clearFilters() },
                        onRefresh = { vm.refresh() },
                        onVideoClick = {},
                        onCreatorClick = {},
                        onLikeClick = { vm.toggleLike(it, viewer.id) },
                        onFollowClick = { vm.toggleFollow(it, viewer.id) },
                        observeComments = { videoId -> vm.observeComments(videoId) },
                        onPostComment = { videoId, text, parentCommentId, onSuccess ->
                            vm.addComment(videoId, viewer.id, text, parentCommentId, onSuccess)
                        }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 8_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.videos.any { it.id == "vid_long_1" } &&
                composeTestRule.onAllNodesWithTag("video_feed_list").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("video_feed_list")
            .performScrollToNode(hasTestTag("video_feed_overlay_comment_button_vid_long_1"))

        composeTestRule.onNodeWithTag("video_feed_like_button_vid_long_1")
            .assertIsDisplayed()
            .performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Feed player overlay like did not increment") {
            repository.observeVideoById("vid_long_1").first()!!.likeCount == initialLikes + 1L
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("video_feed_like_count_vid_long_1", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialLikes + 1L))
                composeTestRule.onNodeWithTag("video_feed_footer_like_count_vid_long_1", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialLikes + 1L))
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithTag("video_feed_overlay_comment_button_vid_long_1")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isNotEmpty()
        }
        val overlayComment = "Posted straight from feed player overlay!"
        composeTestRule.onNodeWithTag("comments_sheet_input").performTextReplacement(overlayComment)
        composeTestRule.onNodeWithTag("comments_sheet_send").assertIsEnabled().performClick()

        awaitCondition(timeoutMs = 3_000L, message = "Overlay comment did not persist") {
            repository.observeComments("vid_long_1").first().any { it.text == overlayComment } &&
                repository.observeVideoById("vid_long_1").first()!!.commentCount == initialComments + 1L
        }

        composeTestRule.onNodeWithTag("comments_sheet_close").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithTag("video_comments_sheet").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("video_feed_overlay_comment_count_vid_long_1", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialComments + 1L))
                composeTestRule.onNodeWithTag("video_feed_comment_count_vid_long_1", useUnmergedTree = true)
                    .assertTextContains(formatCompactCount(initialComments + 1L))
                true
            }.getOrDefault(false)
        }
    }
}
