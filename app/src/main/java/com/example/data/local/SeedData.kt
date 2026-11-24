package com.example.data.local

import com.example.data.model.ErrorCategory
import com.example.data.model.NotificationType
import com.example.data.model.PasswordHasher
import com.example.data.model.UserRole
import com.example.data.model.VideoCategory
import com.example.data.model.VideoFormat

object SeedData {
    const val ADMIN_ID = "user_admin_1"
    const val CREATOR_AARAV_ID = "user_aarav_2"
    const val CREATOR_PRIYA_ID = "user_priya_3"
    const val VIEWER_TEST_ID = "user_viewer_4"

    private fun makeUser(
        id: String,
        email: String,
        username: String,
        displayName: String = username,
        bio: String,
        channelDescription: String = bio,
        avatarUri: String,
        bannerUri: String = "drawable://img_banner_studio",
        plainPassword: String,
        role: UserRole,
        isApprovedUploader: Boolean,
        subscriberCount: Long,
        createdAt: Long
    ): UserEntity {
        val salt = "cp_seed_salt_$id"
        return UserEntity(
            id = id,
            email = email,
            username = username,
            displayName = displayName,
            bio = bio,
            channelDescription = channelDescription,
            avatarUri = avatarUri,
            bannerUri = bannerUri,
            passwordHash = PasswordHasher.hash(plainPassword, salt),
            passwordSalt = salt,
            role = role.name,
            isApprovedUploader = isApprovedUploader,
            isSuspended = false,
            subscriberCount = subscriberCount,
            createdAt = createdAt,
            isPrivateProfile = false,
            hideSubscriberCount = false
        )
    }

    fun initialUsers(now: Long = System.currentTimeMillis()): List<UserEntity> = listOf(
        makeUser(
            id = ADMIN_ID,
            email = "admin@creatorpaisa.test",
            username = "creatorpaisa_admin",
            displayName = "CreatorPaisa Admin",
            bio = "Platform moderation & safety team for Creator Paisa testing prototype.",
            channelDescription = "Official Creator Paisa safety, creator guidelines, and moderation updates channel.",
            avatarUri = "drawable://img_creator_paisa_logo",
            bannerUri = "drawable://img_banner_studio",
            plainPassword = "Admin@123",
            role = UserRole.ADMIN,
            isApprovedUploader = true,
            subscriberCount = 1240L,
            createdAt = now - 86_400_000L * 14
        ),
        makeUser(
            id = CREATOR_AARAV_ID,
            email = "aarav@creatorpaisa.test",
            username = "aarav_tech",
            displayName = "Aarav Tech Studio",
            bio = "Hindi & English smartphone filmmaking, budget creator setups, and editing breakdowns.",
            channelDescription = "Welcome to Aarav Tech Studio! Weekly deep dives into lighting, crisp mic setups, and mobile video editing in Hindi and English.",
            avatarUri = "drawable://img_thumb_tech",
            bannerUri = "drawable://img_banner_studio",
            plainPassword = "Creator@123",
            role = UserRole.CREATOR,
            isApprovedUploader = true,
            subscriberCount = 8420L,
            createdAt = now - 86_400_000L * 10
        ),
        makeUser(
            id = CREATOR_PRIYA_ID,
            email = "priya@creatorpaisa.test",
            username = "priya_rasoi",
            displayName = "Priya Rasoi & Travel",
            bio = "Mumbai street food stories, Jaipur sunrise reels, and authentic regional recipes.",
            channelDescription = "Exploring India's vibrant food streets, traditional recipes, and golden hour travel stories.",
            avatarUri = "drawable://img_thumb_food",
            bannerUri = "drawable://img_thumb_food",
            plainPassword = "Creator@123",
            role = UserRole.CREATOR,
            isApprovedUploader = true,
            subscriberCount = 12950L,
            createdAt = now - 86_400_000L * 8
        ),
        makeUser(
            id = VIEWER_TEST_ID,
            email = "viewer@creatorpaisa.test",
            username = "rohan_viewer",
            displayName = "Rohan Viewer",
            bio = "Testing viewer account. Pending creator upload approval.",
            channelDescription = "Exploring Indian creators on Creator Paisa.",
            avatarUri = "drawable://img_short_travel",
            bannerUri = "drawable://img_short_travel",
            plainPassword = "Viewer@123",
            role = UserRole.USER,
            isApprovedUploader = false,
            subscriberCount = 4L,
            createdAt = now - 86_400_000L * 2
        )
    )

    fun initialVideos(now: Long = System.currentTimeMillis()): List<VideoEntity> = listOf(
        VideoEntity(
            id = "vid_short_1",
            creatorId = CREATOR_PRIYA_ID,
            creatorName = "Priya Rasoi & Travel",
            creatorAvatarUri = "drawable://img_thumb_food",
            title = "Jaipur Sunrise at Hawa Mahal in 45 Seconds",
            description = "Morning pink-hour walk through Old Jaipur before the city wakes up. Shot vertically for Shorts.",
            format = VideoFormat.SHORT.name,
            category = VideoCategory.TRAVEL.name,
            durationSeconds = 45L,
            fileSizeBytes = 12_400_000L,
            thumbnailUri = "drawable://img_short_travel",
            videoUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            viewCount = 4120L,
            likeCount = 389L,
            commentCount = 2L,
            reportCount = 0L,
            isRemoved = false,
            removalReason = null,
            createdAt = now - 3_600_000L * 5,
            tagsRaw = "travel,jaipur,sunrise,vlog"
        ),
        VideoEntity(
            id = "vid_short_2",
            creatorId = CREATOR_AARAV_ID,
            creatorName = "Aarav Tech Studio",
            creatorAvatarUri = "drawable://img_thumb_tech",
            title = "1-Minute Mic Placement Trick for Clear Hindi Voiceovers",
            description = "Avoid room echo on a budget desk setup using simple angle and gain tweaks.",
            format = VideoFormat.SHORT.name,
            category = VideoCategory.TECH.name,
            durationSeconds = 62L,
            fileSizeBytes = 16_800_000L,
            thumbnailUri = "drawable://img_banner_studio",
            videoUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            viewCount = 2875L,
            likeCount = 241L,
            commentCount = 1L,
            reportCount = 0L,
            isRemoved = false,
            removalReason = null,
            createdAt = now - 3_600_000L * 9,
            tagsRaw = "tech,audio,tips"
        ),
        VideoEntity(
            id = "vid_long_1",
            creatorId = CREATOR_AARAV_ID,
            creatorName = "Aarav Tech Studio",
            creatorAvatarUri = "drawable://img_thumb_tech",
            title = "Complete Creator Desk Tour & Lighting Guide (Hindi + English)",
            description = "Full walkthrough of our dual-key warm/teal desk setup, mic routing, and mobile-first framing workflow.",
            format = VideoFormat.LONG.name,
            category = VideoCategory.TECH.name,
            durationSeconds = 540L,
            fileSizeBytes = 94_000_000L,
            thumbnailUri = "drawable://img_thumb_tech",
            videoUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            viewCount = 14320L,
            likeCount = 1120L,
            commentCount = 2L,
            reportCount = 0L,
            isRemoved = false,
            removalReason = null,
            createdAt = now - 3_600_000L * 18,
            tagsRaw = "tech,lighting,studio"
        ),
        VideoEntity(
            id = "vid_long_2",
            creatorId = CREATOR_PRIYA_ID,
            creatorName = "Priya Rasoi & Travel",
            creatorAvatarUri = "drawable://img_thumb_food",
            title = "Mumbai Street Tawa Masala & Crispy Paratha Recipe",
            description = "Step-by-step evening street food documentary filmed at golden hour with authentic spice ratios.",
            format = VideoFormat.LONG.name,
            category = VideoCategory.FOOD.name,
            durationSeconds = 785L,
            fileSizeBytes = 128_000_000L,
            thumbnailUri = "drawable://img_thumb_food",
            videoUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoy.mp4",
            viewCount = 19840L,
            likeCount = 1765L,
            commentCount = 1L,
            reportCount = 0L,
            isRemoved = false,
            removalReason = null,
            createdAt = now - 3_600_000L * 26,
            tagsRaw = "food,streetfood,recipe"
        ),
        VideoEntity(
            id = "vid_short_3",
            creatorId = CREATOR_PRIYA_ID,
            creatorName = "Priya Rasoi & Travel",
            creatorAvatarUri = "drawable://img_thumb_food",
            title = "Golden Hour Chai Boil ASMR · Jaipur Courtyard",
            description = "Quick 35-second relaxing chai pour and cardamom aroma clip.",
            format = VideoFormat.SHORT.name,
            category = VideoCategory.FOOD.name,
            durationSeconds = 35L,
            fileSizeBytes = 9_500_000L,
            thumbnailUri = "drawable://img_thumb_food",
            videoUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            viewCount = 5210L,
            likeCount = 490L,
            commentCount = 0L,
            reportCount = 0L,
            isRemoved = false,
            removalReason = null,
            createdAt = now - 3_600_000L * 34,
            tagsRaw = "food,chai,asmr"
        ),
        VideoEntity(
            id = "vid_long_3",
            creatorId = CREATOR_AARAV_ID,
            creatorName = "Aarav Tech Studio",
            creatorAvatarUri = "drawable://img_thumb_tech",
            title = "Mobile Storytelling & Retention Structure for New Creators",
            description = "How to structure hooks, pacing, and bilingual captions for higher viewer retention without expensive gear.",
            format = VideoFormat.LONG.name,
            category = VideoCategory.EDUCATION.name,
            durationSeconds = 420L,
            fileSizeBytes = 76_000_000L,
            thumbnailUri = "drawable://img_banner_studio",
            videoUri = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
            viewCount = 6740L,
            likeCount = 615L,
            commentCount = 0L,
            reportCount = 0L,
            isRemoved = false,
            removalReason = null,
            createdAt = now - 3_600_000L * 42,
            tagsRaw = "education,storytelling,editing"
        )
    )

    fun initialComments(now: Long = System.currentTimeMillis()): List<CommentEntity> = listOf(
        CommentEntity(
            id = "cmt_1",
            videoId = "vid_short_1",
            userId = CREATOR_AARAV_ID,
            username = "Aarav Tech Studio",
            avatarUri = "drawable://img_thumb_tech",
            text = "Sunrise colors on Hawa Mahal look super crisp!",
            createdAt = now - 1_800_000L
        ),
        CommentEntity(
            id = "cmt_2",
            videoId = "vid_short_1",
            userId = VIEWER_TEST_ID,
            username = "Rohan Viewer",
            avatarUri = "drawable://img_short_travel",
            text = "बहुत सुंदर वीडियो! जयपुर जाने का मन कर गया।",
            createdAt = now - 900_000L
        ),
        CommentEntity(
            id = "cmt_3",
            videoId = "vid_long_1",
            userId = CREATOR_PRIYA_ID,
            username = "Priya Rasoi & Travel",
            avatarUri = "drawable://img_thumb_food",
            text = "That teal rim light separation really makes the desk pop.",
            createdAt = now - 7_200_000L
        ),
        CommentEntity(
            id = "cmt_4",
            videoId = "vid_long_1",
            userId = VIEWER_TEST_ID,
            username = "Rohan Viewer",
            avatarUri = "drawable://img_short_travel",
            text = "Clear explanation in both Hindi and English, thank you!",
            createdAt = now - 3_200_000L
        ),
        CommentEntity(
            id = "cmt_5",
            videoId = "vid_short_2",
            userId = VIEWER_TEST_ID,
            username = "Rohan Viewer",
            avatarUri = "drawable://img_short_travel",
            text = "Tried this mic angle today and echo dropped immediately.",
            createdAt = now - 5_400_000L
        ),
        CommentEntity(
            id = "cmt_6",
            videoId = "vid_long_2",
            userId = CREATOR_AARAV_ID,
            username = "Aarav Tech Studio",
            avatarUri = "drawable://img_thumb_tech",
            text = "The steam close-ups at 2:10 look cinema grade!",
            createdAt = now - 10_000_000L
        )
    )

    fun initialNotifications(now: Long = System.currentTimeMillis()): List<NotificationEntity> = listOf(
        NotificationEntity(
            id = "notif_seed_1",
            recipientUserId = VIEWER_TEST_ID,
            actorId = CREATOR_AARAV_ID,
            actorName = "Aarav Tech Studio",
            actorAvatarUri = "drawable://img_thumb_tech",
            type = NotificationType.REPLY.name,
            videoId = "vid_long_1",
            videoThumbnailUri = "drawable://img_thumb_tech",
            commentId = "cmt_4",
            dedupKey = "reply:seed_cmt_4:$VIEWER_TEST_ID",
            message = "Aarav Tech Studio replied to your comment on \"Complete Creator Desk Tour & Lighting Guide\"",
            isRead = false,
            createdAt = now - 7_200_000L
        ),
        NotificationEntity(
            id = "notif_seed_2",
            recipientUserId = CREATOR_AARAV_ID,
            actorId = VIEWER_TEST_ID,
            actorName = "Rohan Viewer",
            actorAvatarUri = "drawable://img_short_travel",
            type = NotificationType.SUBSCRIPTION.name,
            videoId = null,
            videoThumbnailUri = null,
            commentId = null,
            dedupKey = "sub:$CREATOR_AARAV_ID:$VIEWER_TEST_ID",
            message = "Rohan Viewer subscribed to your channel",
            isRead = false,
            createdAt = now - 5_400_000L
        ),
        NotificationEntity(
            id = "notif_seed_3",
            recipientUserId = CREATOR_PRIYA_ID,
            actorId = CREATOR_AARAV_ID,
            actorName = "Aarav Tech Studio",
            actorAvatarUri = "drawable://img_thumb_tech",
            type = NotificationType.LIKE.name,
            videoId = "vid_short_1",
            videoThumbnailUri = "drawable://img_short_travel",
            commentId = null,
            dedupKey = "like:vid_short_1:$CREATOR_AARAV_ID",
            message = "Aarav Tech Studio liked \"Jaipur Sunrise at Hawa Mahal in 45 Seconds\"",
            isRead = false,
            createdAt = now - 1_800_000L
        )
    )

    fun initialErrorLogs(now: Long = System.currentTimeMillis()): List<ErrorLogEntity> = listOf(
        ErrorLogEntity(
            id = "err_seed_1",
            category = ErrorCategory.UPLOAD.name,
            message = "Sample diagnostic: Rejected Short exceeding 180s limit (215s).",
            userId = CREATOR_AARAV_ID,
            videoId = null,
            createdAt = now - 43_200_000L
        )
    )

    fun initialPlaylists(now: Long = System.currentTimeMillis()): List<PlaylistEntity> = listOf(
        PlaylistEntity(
            id = "pl_aarav_studio",
            creatorId = CREATOR_AARAV_ID,
            title = "Creator Desk Setup Masterclass",
            description = "Lighting, audio routing, and storytelling structure for mobile creators.",
            thumbnailUri = "drawable://img_thumb_tech",
            isPublic = true,
            createdAt = now - 86_400_000L * 4,
            updatedAt = now - 86_400_000L * 2
        ),
        PlaylistEntity(
            id = "pl_priya_india",
            creatorId = CREATOR_PRIYA_ID,
            title = "Jaipur & Mumbai Food Diaries",
            description = "Street tawa recipes and sunrise travel reels across India.",
            thumbnailUri = "drawable://img_thumb_food",
            isPublic = true,
            createdAt = now - 86_400_000L * 3,
            updatedAt = now - 86_400_000L * 1
        )
    )

    fun initialPlaylistVideos(now: Long = System.currentTimeMillis()): List<PlaylistVideoEntity> = listOf(
        PlaylistVideoEntity(
            playlistId = "pl_aarav_studio",
            videoId = "vid_long_1",
            position = 0,
            addedAt = now - 86_400_000L * 3
        ),
        PlaylistVideoEntity(
            playlistId = "pl_aarav_studio",
            videoId = "vid_long_3",
            position = 1,
            addedAt = now - 86_400_000L * 2
        ),
        PlaylistVideoEntity(
            playlistId = "pl_priya_india",
            videoId = "vid_long_2",
            position = 0,
            addedAt = now - 86_400_000L * 2
        ),
        PlaylistVideoEntity(
            playlistId = "pl_priya_india",
            videoId = "vid_short_1",
            position = 1,
            addedAt = now - 86_400_000L * 1
        )
    )
}
