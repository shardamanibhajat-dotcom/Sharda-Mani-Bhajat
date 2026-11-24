package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ShortsRoute

@Serializable
object UploadRoute

@Serializable
object NetworkRoute

@Serializable
object ProfileRoute

@Serializable
data class PlayerRoute(val videoId: String, val targetCommentId: String? = null)

@Serializable
data class ChannelRoute(val creatorId: String)

@Serializable
object CreatorStudioRoute

@Serializable
object NotificationsRoute

@Serializable
object AdminRoute

@Serializable
object SetupGuideRoute

@Serializable
object SearchRoute

@Serializable
object VideoFeedRoute
