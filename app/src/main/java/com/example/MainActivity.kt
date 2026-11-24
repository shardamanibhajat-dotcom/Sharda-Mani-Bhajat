package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.remote.FirebaseCreatorPaisaRepository
import com.example.data.remote.attemptAutoSignIn
import com.example.data.remote.authStateFlow
import com.example.data.remote.signOutWithCredentialManager
import com.example.data.repository.CreatorPaisaRepository
import com.example.localization.LocalAppStrings
import com.example.localization.stringsFor
import com.example.ui.navigation.AdminRoute
import com.example.ui.navigation.ChannelRoute
import com.example.ui.navigation.CreatorStudioRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.NetworkRoute
import com.example.ui.navigation.NotificationsRoute
import com.example.ui.navigation.PlayerRoute
import com.example.ui.navigation.ProfileRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.SetupGuideRoute
import com.example.ui.navigation.ShortsRoute
import com.example.ui.navigation.UploadRoute
import com.example.ui.navigation.VideoFeedRoute
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChannelScreen
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SetupGuideScreen
import com.example.ui.screens.ShortsFeedScreen
import com.example.ui.screens.VideoFeedScreen
import com.example.ui.screens.VideoUploadScreen
import com.example.ui.components.CreatorPaisaLogoBadge
import com.example.ui.components.LanguageTogglePill
import com.example.ui.components.NotificationBellButton
import com.example.ui.components.VideoUploadBottomSheet
import com.example.ui.theme.CreatorPaisaTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ChannelViewModel
import com.example.ui.viewmodel.CreatorStudioViewModel
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.NotificationsViewModel
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.UploadViewModel
import com.example.ui.viewmodel.VideoFeedViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = FirebaseCreatorPaisaRepository(applicationContext)

        setContent {
            CreatorPaisaApp(repository = repository)
        }
    }
}

private data class BottomTabItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun CreatorPaisaApp(repository: CreatorPaisaRepository) {
    val isFirebaseCloudMode = repository is FirebaseCreatorPaisaRepository
    val appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(repository))
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()
    val currentLanguage by appViewModel.appLanguage.collectAsStateWithLifecycle()
    val isDarkTheme by appViewModel.isDarkTheme.collectAsStateWithLifecycle()
    val authState by appViewModel.authState.collectAsStateWithLifecycle()
    val statusBanner by appViewModel.statusBanner.collectAsStateWithLifecycle()
    val unreadNotificationCount by appViewModel.unreadNotificationCount.collectAsStateWithLifecycle()

    val localizedStrings = remember(currentLanguage) { stringsFor(currentLanguage) }

    CompositionLocalProvider(LocalAppStrings provides localizedStrings) {
        CreatorPaisaTheme(darkTheme = isDarkTheme) {
            if (isFirebaseCloudMode) {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val credentialManager = remember(context) { CredentialManager.create(context) }
                val authFlow = remember { Firebase.auth.authStateFlow() }
                val firebaseUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
                var autoSignInAttempted by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(firebaseUser) {
                    if (firebaseUser == null && !autoSignInAttempted) {
                        autoSignInAttempted = true
                        attemptAutoSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = { appViewModel.clearAuthError() },
                            onUnauthenticated = {},
                            scope = coroutineScope
                        )
                    }
                }

                val fbUser = firebaseUser
                if (fbUser == null) {
                    AuthScreen(
                        authState = authState,
                        currentLanguage = currentLanguage,
                        onToggleLanguage = { appViewModel.toggleLanguage() },
                        onLogin = { email, password -> appViewModel.login(email, password) },
                        onSignUp = { email, username, password, bio, avatar ->
                            appViewModel.signUp(email, username, password, bio, avatar)
                        },
                        onClearError = { appViewModel.clearAuthError() },
                        isFirebaseCloudMode = true,
                        onGoogleSignInSuccess = { appViewModel.clearAuthError() },
                        onGoogleSignInError = { msg -> appViewModel.setAuthError(msg) },
                        onForgotPassword = { email, cb -> appViewModel.sendPasswordReset(email, cb) }
                    )
                } else {
                    key(fbUser.uid) {
                        val user = currentUser
                        when {
                            user == null -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        CreatorPaisaLogoBadge(size = 56.dp)
                                        CircularProgressIndicator()
                                        Text(
                                            text = "Loading your Creator Paisa channel...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            user.isSuspended -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Text(
                                            text = "Account Suspended",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "This Creator Paisa account has been suspended by moderation.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                signOutWithCredentialManager(
                                                    credentialManager = credentialManager,
                                                    scope = coroutineScope,
                                                    onSignOutComplete = { appViewModel.logout() }
                                                )
                                            }
                                        ) {
                                            Text(localizedStrings.logout)
                                        }
                                    }
                                }
                            }
                            else -> {
                                MainAuthenticatedShell(
                                    currentUser = user,
                                    unreadNotificationCount = unreadNotificationCount,
                                    repository = repository,
                                    appViewModel = appViewModel,
                                    statusBanner = statusBanner,
                                    onSignOut = {
                                        signOutWithCredentialManager(
                                            credentialManager = credentialManager,
                                            scope = coroutineScope,
                                            onSignOutComplete = { appViewModel.logout() }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                val user = currentUser
                if (user == null) {
                    AuthScreen(
                        authState = authState,
                        currentLanguage = currentLanguage,
                        onToggleLanguage = { appViewModel.toggleLanguage() },
                        onLogin = { email, password -> appViewModel.login(email, password) },
                        onSignUp = { email, username, password, bio, avatar ->
                            appViewModel.signUp(email, username, password, bio, avatar)
                        },
                        onClearError = { appViewModel.clearAuthError() }
                    )
                } else {
                    key(user.id) {
                        MainAuthenticatedShell(
                            currentUser = user,
                            unreadNotificationCount = unreadNotificationCount,
                            repository = repository,
                            appViewModel = appViewModel,
                            statusBanner = statusBanner,
                            onSignOut = { appViewModel.logout() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAuthenticatedShell(
    currentUser: UserProfile,
    unreadNotificationCount: Int,
    repository: CreatorPaisaRepository,
    appViewModel: AppViewModel,
    statusBanner: String?,
    onSignOut: () -> Unit
) {
    val strings = LocalAppStrings.current
    val navController = rememberNavController()
    val currentLanguage by appViewModel.appLanguage.collectAsStateWithLifecycle()
    val isDarkTheme by appViewModel.isDarkTheme.collectAsStateWithLifecycle()

    val feedViewModel: FeedViewModel = viewModel(
        key = "feed_${currentUser.id}",
        factory = FeedViewModel.Factory(repository)
    )
    val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val creators by feedViewModel.creators.collectAsStateWithLifecycle()
    val feedMessage by feedViewModel.actionMessage.collectAsStateWithLifecycle()
    val subscribedCreators by appViewModel.subscribedCreators.collectAsStateWithLifecycle()
    val followedCreatorIds = remember(subscribedCreators) {
        subscribedCreators.map { it.id }.toSet()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            snackbarHostState.showSnackbar(statusBanner)
            appViewModel.clearStatusBanner()
        }
    }
    LaunchedEffect(feedMessage) {
        if (feedMessage != null) {
            snackbarHostState.showSnackbar(feedMessage!!)
            feedViewModel.clearMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val topLevelTabs = remember(strings) {
        listOf(
            BottomTabItem(HomeRoute, strings.tabHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            BottomTabItem(ShortsRoute, strings.tabShorts, Icons.Filled.Explore, Icons.Outlined.Explore, "nav_shorts"),
            BottomTabItem(UploadRoute, strings.tabUpload, Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_upload"),
            BottomTabItem(NetworkRoute, strings.tabNetwork, Icons.Filled.Groups, Icons.Outlined.Groups, "nav_network"),
            BottomTabItem(ProfileRoute, strings.tabProfile, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
        )
    }

    val showChrome = currentDestination?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<ShortsRoute>() ||
            dest.hasRoute<UploadRoute>() ||
            dest.hasRoute<NetworkRoute>() ||
            dest.hasRoute<ProfileRoute>()
    } ?: true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val uploadViewModel: UploadViewModel = viewModel(
        key = "upload_${currentUser.id}",
        factory = UploadViewModel.Factory(currentUser.id, repository)
    )
    val dailyUploadCount by uploadViewModel.dailyUploadCount.collectAsStateWithLifecycle()
    val uploadProgressState by uploadViewModel.progressState.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 720.dp
        var showTopOverflowMenu by remember { mutableStateOf(false) }
        var showUploadBottomSheet by rememberSaveable { mutableStateOf(false) }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            floatingActionButton = {
                val showQuickUploadFab = currentDestination?.let { dest ->
                    dest.hasRoute<HomeRoute>() || dest.hasRoute<NetworkRoute>()
                } ?: true
                if (showQuickUploadFab) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            uploadViewModel.refreshQuota()
                            uploadViewModel.resetState()
                            showUploadBottomSheet = true
                        },
                        icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                        text = { Text(strings.quickUploadAction) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("quick_upload_fab")
                    )
                }
            },
            topBar = {
                if (showChrome) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CreatorPaisaLogoBadge(size = 30.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.appName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { navController.navigate(SearchRoute) },
                                modifier = Modifier.testTag("open_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = strings.searchVideosTitle,
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            LanguageTogglePill(
                                currentLanguage = currentLanguage,
                                onToggle = { appViewModel.toggleLanguage() }
                            )
                            NotificationBellButton(
                                unreadCount = unreadNotificationCount,
                                onClick = { navController.navigate(NotificationsRoute) }
                            )
                            IconButton(
                                onClick = { navController.navigate(CreatorStudioRoute) },
                                modifier = Modifier.testTag("open_studio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = strings.creatorStudioTitle,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { showTopOverflowMenu = true },
                                    modifier = Modifier.testTag("top_bar_more_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopOverflowMenu,
                                    onDismissRequest = { showTopOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.videoFeedTitle) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.OndemandVideo,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            navController.navigate(VideoFeedRoute)
                                        },
                                        modifier = Modifier.testTag("open_video_feed_menu_item")
                                    )
                                    if (currentUser.role == UserRole.ADMIN) {
                                        DropdownMenuItem(
                                            text = { Text(strings.adminPanelTitle) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.AdminPanelSettings,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            },
                                            onClick = {
                                                showTopOverflowMenu = false
                                                navController.navigate(AdminRoute)
                                            },
                                            modifier = Modifier.testTag("open_admin_button")
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(strings.setupGuideTitle) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.SettingsSuggest,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            navController.navigate(SetupGuideRoute)
                                        },
                                        modifier = Modifier.testTag("open_setup_guide_button")
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.testTag("main_top_app_bar")
                    )
                }
            },
            bottomBar = {
                if (showChrome && !useNavigationRail) {
                    NavigationBar {
                        topLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(tab.route::class)
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    if (tab.route == ProfileRoute && unreadNotificationCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useNavigationRail) {
                    NavigationRail {
                        topLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(tab.route::class)
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    if (tab.route == ProfileRoute && unreadNotificationCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                currentUser = currentUser,
                                uiState = feedState,
                                currentLanguage = currentLanguage,
                                unreadNotificationCount = unreadNotificationCount,
                                onToggleLanguage = { appViewModel.toggleLanguage() },
                                onSearchChange = { feedViewModel.setSearchQuery(it) },
                                onFilterChange = { feedViewModel.setFilter(it) },
                                onCategoryChange = { feedViewModel.setCategory(it) },
                                onTagChange = { feedViewModel.setTag(it) },
                                onClearFilters = { feedViewModel.clearFilters() },
                                onSortChange = { feedViewModel.setSortOrder(it) },
                                onVideoClick = { videoId -> navController.navigate(PlayerRoute(videoId)) },
                                onCreatorClick = { creatorId -> navController.navigate(ChannelRoute(creatorId)) },
                                onLikeClick = { videoId -> feedViewModel.toggleLike(videoId, currentUser.id) },
                                onReportVideo = { videoId, reason ->
                                    feedViewModel.reportVideo(videoId, currentUser.id, reason)
                                },
                                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                                onOpenStudio = { navController.navigate(CreatorStudioRoute) },
                                onOpenAdmin = { navController.navigate(AdminRoute) },
                                onOpenSetupGuide = { navController.navigate(SetupGuideRoute) },
                                onOpenUpload = { navigateTopLevel(UploadRoute) },
                                onOpenVideoFeed = { navController.navigate(VideoFeedRoute) },
                                followedCreatorIds = followedCreatorIds,
                                onFollowClick = { cid -> feedViewModel.toggleSubscription(cid, currentUser.id) },
                                showHeader = false,
                                observeComments = { vid -> feedViewModel.observeComments(vid) },
                                onPostComment = { vid, text, parentId, onDone ->
                                    if (parentId != null) {
                                        feedViewModel.addReply(vid, currentUser.id, text, parentId, onDone)
                                    } else {
                                        feedViewModel.addComment(vid, currentUser.id, text, onDone)
                                    }
                                }
                            )
                        }

                        composable<ShortsRoute> {
                            ShortsFeedScreen(
                                currentUserId = currentUser.id,
                                currentLanguage = currentLanguage,
                                shorts = feedState.shorts,
                                isLoading = feedState.isLoading,
                                observeComments = { vid -> feedViewModel.observeComments(vid) },
                                observeIsSubscribed = { cid ->
                                    feedViewModel.observeIsSubscribed(cid, currentUser.id)
                                },
                                onPlayShort = { videoId -> navController.navigate(PlayerRoute(videoId)) },
                                onCreatorClick = { creatorId -> navController.navigate(ChannelRoute(creatorId)) },
                                onSubscribeClick = { cid ->
                                    feedViewModel.toggleSubscription(cid, currentUser.id)
                                },
                                onLikeClick = { videoId -> feedViewModel.toggleLike(videoId, currentUser.id) },
                                onPostComment = { vid, text, onDone ->
                                    feedViewModel.addComment(vid, currentUser.id, text, onDone)
                                },
                                onPostReply = { vid, text, parentId, onDone ->
                                    feedViewModel.addReply(vid, currentUser.id, text, parentId, onDone)
                                },
                                onReportVideo = { vid, reason ->
                                    feedViewModel.reportVideo(vid, currentUser.id, reason)
                                },
                                onBlockCreator = { cid ->
                                    feedViewModel.blockUser(currentUser.id, cid)
                                },
                                onGenuineViewReached = { vid ->
                                    feedViewModel.recordGenuineView(vid, currentUser.id)
                                },
                                onPlaybackError = { vid, err ->
                                    feedViewModel.logPlaybackError(vid, currentUser.id, err)
                                },
                                onOpenUpload = { navigateTopLevel(UploadRoute) }
                            )
                        }

                        composable<UploadRoute> {
                            VideoUploadScreen(
                                currentUser = currentUser,
                                currentLanguage = currentLanguage,
                                dailyUploadCount = dailyUploadCount,
                                progressState = uploadProgressState,
                                onPublish = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags ->
                                    uploadViewModel.publishVideo(
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
                                onCancelUpload = { uploadViewModel.cancelUpload() },
                                onRetryUpload = { uploadViewModel.retryUpload() },
                                onResetState = { uploadViewModel.resetState() },
                                onVideoPublishedNavigate = { videoId ->
                                    uploadViewModel.resetState()
                                    navController.navigate(PlayerRoute(videoId))
                                },
                                onPublishWithVisibility = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags, isPublic ->
                                    uploadViewModel.publishVideo(
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

                        composable<NetworkRoute> {
                            NetworkScreen(
                                currentUserId = currentUser.id,
                                creators = creators,
                                subscribedCreators = subscribedCreators,
                                onCreatorClick = { creatorId -> navController.navigate(ChannelRoute(creatorId)) }
                            )
                        }

                        composable<ProfileRoute> {
                            val blockedUsers by appViewModel.blockedUsers.collectAsStateWithLifecycle()
                            val notificationPreferences by appViewModel.notificationPreferences.collectAsStateWithLifecycle()
                            val coroutineScope = rememberCoroutineScope()
                            val userVideosFlow = remember(currentUser.id) {
                                repository.observeAllVideosForCreatorStudio(currentUser.id)
                            }
                            val watchHistoryFlow = remember(currentUser.id) {
                                repository.observeWatchHistory(currentUser.id)
                            }
                            val userVideos by userVideosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                            val watchHistoryVideos by watchHistoryFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                            ProfileScreen(
                                currentUser = currentUser,
                                currentLanguage = currentLanguage,
                                isDarkTheme = isDarkTheme,
                                unreadNotificationCount = unreadNotificationCount,
                                blockedUsers = blockedUsers,
                                subscribedCreators = subscribedCreators,
                                userVideos = userVideos,
                                watchHistoryVideos = watchHistoryVideos,
                                notificationPreferences = notificationPreferences,
                                onUpdateNotificationPreferences = { updated ->
                                    appViewModel.updateNotificationPreferences(updated)
                                },
                                onClearWatchHistory = {
                                    coroutineScope.launch {
                                        repository.clearWatchHistory(currentUser.id)
                                    }
                                },
                                onToggleLanguage = { appViewModel.toggleLanguage() },
                                onToggleTheme = { appViewModel.setDarkTheme(it) },
                                onUpdateChannelProfile = { displayName, username, bio, desc, avatar, banner, onOk, onErr ->
                                    appViewModel.updateChannelProfile(
                                        displayName = displayName,
                                        username = username,
                                        bio = bio,
                                        channelDescription = desc,
                                        avatarUri = avatar,
                                        bannerUri = banner,
                                        onSuccess = onOk,
                                        onError = onErr
                                    )
                                },
                                onUpdatePrivacy = { isPrivate, hideSubs ->
                                    appViewModel.updatePrivacySettings(isPrivate, hideSubs)
                                },
                                onUnblockUser = { targetId -> appViewModel.unblockUser(targetId) },
                                onVideoClick = { videoId -> navController.navigate(PlayerRoute(videoId)) },
                                onOpenUpload = { navigateTopLevel(UploadRoute) },
                                onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) },
                                onOpenStudio = { navController.navigate(CreatorStudioRoute) },
                                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                                onOpenAdminPanel = { navController.navigate(AdminRoute) },
                                onOpenSetupGuide = { navController.navigate(SetupGuideRoute) },
                                onLogout = onSignOut,
                                onDeleteAccount = { appViewModel.deleteAccount() }
                            )
                        }

                        composable<CreatorStudioRoute> {
                            val studioViewModel: CreatorStudioViewModel = viewModel(
                                key = "studio_${currentUser.id}",
                                factory = CreatorStudioViewModel.Factory(currentUser.id, currentUser.id, repository)
                            )
                            val studioState by studioViewModel.uiState.collectAsStateWithLifecycle()
                            val studioMessage by studioViewModel.message.collectAsStateWithLifecycle()

                            LaunchedEffect(studioMessage) {
                                if (studioMessage != null) {
                                    snackbarHostState.showSnackbar(studioMessage!!)
                                    studioViewModel.clearMessage()
                                }
                            }

                            CreatorStudioScreen(
                                uiState = studioState,
                                currentLanguage = currentLanguage,
                                onBack = { navController.popBackStack() },
                                onOpenUpload = { navigateTopLevel(UploadRoute) },
                                onOpenVideo = { vid -> navController.navigate(PlayerRoute(vid)) },
                                onUpdateVideo = { vid, title, desc, cat, thumb, tags ->
                                    studioViewModel.updateVideo(vid, title, desc, cat, thumb, tags)
                                },
                                onDeleteVideo = { vid -> studioViewModel.deleteVideo(vid) }
                            )
                        }

                        composable<NotificationsRoute> {
                            val notificationsViewModel: NotificationsViewModel = viewModel(
                                key = "notif_${currentUser.id}",
                                factory = NotificationsViewModel.Factory(currentUser.id, repository)
                            )
                            val notifState by notificationsViewModel.uiState.collectAsStateWithLifecycle()
                            val notifMessage by notificationsViewModel.message.collectAsStateWithLifecycle()

                            LaunchedEffect(notifMessage) {
                                if (notifMessage != null) {
                                    snackbarHostState.showSnackbar(notifMessage!!)
                                    notificationsViewModel.clearMessage()
                                }
                            }

                            NotificationsScreen(
                                uiState = notifState,
                                currentLanguage = currentLanguage,
                                onBack = { navController.popBackStack() },
                                onSelectCategory = { cat -> notificationsViewModel.selectCategory(cat) },
                                onMarkRead = { nid -> notificationsViewModel.markRead(nid) },
                                onMarkAllRead = { notificationsViewModel.markAllRead() },
                                onDeleteNotification = { nid -> notificationsViewModel.deleteNotification(nid) },
                                onClearAll = { notificationsViewModel.clearAllNotifications() },
                                onUpdatePreferences = { updated -> notificationsViewModel.updatePreferences(updated) },
                                onOpenVideo = { vid, commentId ->
                                    navController.navigate(PlayerRoute(vid, commentId))
                                },
                                onOpenChannel = { cid -> navController.navigate(ChannelRoute(cid)) }
                            )
                        }

                        composable<PlayerRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<PlayerRoute>()
                            val playerViewModel: PlayerViewModel = viewModel(
                                key = "player_${route.videoId}_${currentUser.id}",
                                factory = PlayerViewModel.Factory(route.videoId, currentUser.id, repository)
                            )
                            val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
                            val feedback by playerViewModel.feedbackMessage.collectAsStateWithLifecycle()
                            val coroutineScope = rememberCoroutineScope()
                            val ownPlaylistsFlow = remember(currentUser.id) {
                                repository.observePlaylistsForChannel(currentUser.id, currentUser.id)
                            }
                            val ownPlaylists by ownPlaylistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                            LaunchedEffect(feedback) {
                                if (feedback != null) {
                                    snackbarHostState.showSnackbar(feedback!!)
                                    playerViewModel.clearFeedback()
                                }
                            }

                            PlayerScreen(
                                currentUserId = currentUser.id,
                                currentLanguage = currentLanguage,
                                uiState = playerUiState,
                                targetCommentId = route.targetCommentId,
                                ownPlaylists = ownPlaylists,
                                onAddToPlaylist = { playlistId ->
                                    coroutineScope.launch {
                                        val res = repository.addVideoToPlaylist(currentUser.id, playlistId, route.videoId)
                                        snackbarHostState.showSnackbar(
                                            if (res.isSuccess) "Saved to playlist." else res.exceptionOrNull()?.message ?: "Failed"
                                        )
                                    }
                                },
                                onCreatePlaylistAndAdd = { title ->
                                    coroutineScope.launch {
                                        val created = repository.createPlaylist(
                                            requesterId = currentUser.id,
                                            title = title,
                                            description = "",
                                            thumbnailUri = playerUiState.video?.thumbnailUri ?: "",
                                            isPublic = true
                                        )
                                        created.onSuccess { pid ->
                                            repository.addVideoToPlaylist(currentUser.id, pid, route.videoId)
                                            snackbarHostState.showSnackbar("Playlist created & video saved.")
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar(err.message ?: "Failed to create playlist")
                                        }
                                    }
                                },
                                onBack = { navController.popBackStack() },
                                onVideoClick = { nextId -> navController.navigate(PlayerRoute(nextId)) },
                                onCreatorClick = { cid -> navController.navigate(ChannelRoute(cid)) },
                                onGenuinePlaybackReached = { playerViewModel.onGenuinePlaybackReached() },
                                onSaveWatchHistory = { posMs -> playerViewModel.recordWatchHistory(posMs) },
                                onPlaybackError = { err -> playerViewModel.onPlaybackError(err) },
                                onClearPlaybackError = { playerViewModel.clearPlaybackError() },
                                onToggleLike = { playerViewModel.toggleLike() },
                                onToggleSubscribe = { playerViewModel.toggleSubscribe() },
                                onBlockCreator = {
                                    playerViewModel.blockCreator { navController.popBackStack() }
                                },
                                onPostComment = { text, parentId, onDone ->
                                    playerViewModel.postComment(text, parentId, onDone)
                                },
                                onReportVideo = { reason -> playerViewModel.reportVideo(reason) }
                            )
                        }

                        composable<ChannelRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<ChannelRoute>()
                            val channelViewModel: ChannelViewModel = viewModel(
                                key = "channel_${route.creatorId}_${currentUser.id}",
                                factory = ChannelViewModel.Factory(route.creatorId, currentUser.id, repository)
                            )
                            val channelState by channelViewModel.uiState.collectAsStateWithLifecycle()
                            val channelMessage by channelViewModel.message.collectAsStateWithLifecycle()

                            LaunchedEffect(channelMessage) {
                                if (channelMessage != null) {
                                    snackbarHostState.showSnackbar(channelMessage!!)
                                    channelViewModel.clearMessage()
                                }
                            }

                            ChannelScreen(
                                currentUserId = currentUser.id,
                                currentLanguage = currentLanguage,
                                uiState = channelState,
                                onBack = { navController.popBackStack() },
                                onToggleSubscribe = { channelViewModel.toggleSubscription() },
                                onToggleBlock = { channelViewModel.toggleBlock() },
                                onOpenStudio = { navController.navigate(CreatorStudioRoute) },
                                onOpenUpload = { navigateTopLevel(UploadRoute) },
                                onOpenChannel = { nextCid -> navController.navigate(ChannelRoute(nextCid)) },
                                onUpdateChannelProfile = { displayName, username, bio, desc, avatar, banner, onOk, onErr ->
                                    appViewModel.updateChannelProfile(
                                        displayName = displayName,
                                        username = username,
                                        bio = bio,
                                        channelDescription = desc,
                                        avatarUri = avatar,
                                        bannerUri = banner,
                                        onSuccess = onOk,
                                        onError = onErr
                                    )
                                },
                                onCreatePlaylist = { title, desc, thumb, isPub, onDone ->
                                    channelViewModel.createPlaylist(title, desc, thumb, isPub, onDone)
                                },
                                onUpdatePlaylist = { pid, title, desc, thumb, isPub, onDone ->
                                    channelViewModel.updatePlaylist(pid, title, desc, thumb, isPub, onDone)
                                },
                                onDeletePlaylist = { pid -> channelViewModel.deletePlaylist(pid) },
                                onAddVideoToPlaylist = { pid, vid -> channelViewModel.addVideoToPlaylist(pid, vid) },
                                onRemoveVideoFromPlaylist = { pid, vid ->
                                    channelViewModel.removeVideoFromPlaylist(pid, vid)
                                },
                                onVideoClick = { videoId -> navController.navigate(PlayerRoute(videoId)) },
                                onLikeClick = { videoId -> channelViewModel.toggleLike(videoId) },
                                onReportVideo = { videoId, reason ->
                                    channelViewModel.reportVideo(videoId, reason)
                                }
                            )
                        }

                        composable<AdminRoute> {
                            val adminViewModel: AdminViewModel = viewModel(
                                key = "admin_${currentUser.id}",
                                factory = AdminViewModel.Factory(currentUser.id, repository)
                            )
                            val stats by adminViewModel.statistics.collectAsStateWithLifecycle()
                            val adminVideos by adminViewModel.allVideos.collectAsStateWithLifecycle()
                            val adminUsers by adminViewModel.allUsers.collectAsStateWithLifecycle()
                            val adminReports by adminViewModel.reports.collectAsStateWithLifecycle()
                            val adminErrors by adminViewModel.errorLogs.collectAsStateWithLifecycle()
                            val adminMsg by adminViewModel.adminMessage.collectAsStateWithLifecycle()

                            LaunchedEffect(adminMsg) {
                                if (adminMsg != null) {
                                    snackbarHostState.showSnackbar(adminMsg!!)
                                    adminViewModel.clearMessage()
                                }
                            }

                            AdminScreen(
                                currentUser = currentUser,
                                statistics = stats,
                                videos = adminVideos,
                                users = adminUsers,
                                reports = adminReports,
                                errorLogs = adminErrors,
                                onBack = { navController.popBackStack() },
                                onRemoveVideo = { vid, reason -> adminViewModel.removeVideo(vid, reason) },
                                onRestoreVideo = { vid -> adminViewModel.restoreVideo(vid) },
                                onToggleApproval = { uid, approved ->
                                    adminViewModel.setUploaderApproval(uid, approved)
                                },
                                onToggleSuspend = { uid, suspended ->
                                    adminViewModel.setUserSuspended(uid, suspended)
                                },
                                onResolveReport = { rid -> adminViewModel.resolveReport(rid) }
                            )
                        }

                        composable<SetupGuideRoute> {
                            SetupGuideScreen(onBack = { navController.popBackStack() })
                        }

                        composable<SearchRoute> {
                            val searchViewModel: SearchViewModel = viewModel(
                                key = "search_${currentUser.id}",
                                factory = SearchViewModel.Factory(repository)
                            )
                            val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()

                            SearchScreen(
                                currentUserId = currentUser.id,
                                uiState = searchState,
                                currentLanguage = currentLanguage,
                                onBack = { navController.popBackStack() },
                                onQueryChange = { searchViewModel.onQueryChange(it) },
                                onSubmitSearch = { searchViewModel.submitSearch(it) },
                                onClearQuery = { searchViewModel.clearQuery() },
                                onScopeChange = { searchViewModel.setScope(it) },
                                onRetry = { searchViewModel.retry() },
                                onVideoClick = { videoId -> navController.navigate(PlayerRoute(videoId)) },
                                onCreatorClick = { creatorId -> navController.navigate(ChannelRoute(creatorId)) },
                                onLikeClick = { videoId -> searchViewModel.toggleLike(videoId, currentUser.id) }
                            )
                        }

                        composable<VideoFeedRoute> {
                            val videoFeedViewModel: VideoFeedViewModel = viewModel(
                                key = "video_feed_${currentUser.id}",
                                factory = VideoFeedViewModel.Factory(repository)
                            )
                            val videoFeedState by videoFeedViewModel.uiState.collectAsStateWithLifecycle()
                            val videoFeedMessage by videoFeedViewModel.actionMessage.collectAsStateWithLifecycle()

                            LaunchedEffect(videoFeedMessage) {
                                if (videoFeedMessage != null) {
                                    snackbarHostState.showSnackbar(videoFeedMessage!!)
                                    videoFeedViewModel.clearMessage()
                                }
                            }

                            VideoFeedScreen(
                                currentUserId = currentUser.id,
                                uiState = videoFeedState,
                                currentLanguage = currentLanguage,
                                followedCreatorIds = followedCreatorIds,
                                onBack = { navController.popBackStack() },
                                onFormatFilterChange = { videoFeedViewModel.setFormatFilter(it) },
                                onTagFilterChange = { videoFeedViewModel.setTagFilter(it) },
                                onSortChange = { videoFeedViewModel.setSortOrder(it) },
                                onClearFilters = { videoFeedViewModel.clearFilters() },
                                onRefresh = { videoFeedViewModel.refresh() },
                                onVideoClick = { videoId -> navController.navigate(PlayerRoute(videoId)) },
                                onCreatorClick = { creatorId -> navController.navigate(ChannelRoute(creatorId)) },
                                onLikeClick = { videoId -> videoFeedViewModel.toggleLike(videoId, currentUser.id) },
                                onFollowClick = { cid -> videoFeedViewModel.toggleFollow(cid, currentUser.id) },
                                onOpenUpload = { navigateTopLevel(UploadRoute) },
                                onGenuineViewReached = { vid ->
                                    videoFeedViewModel.recordGenuineView(vid, currentUser.id)
                                },
                                onPlaybackError = { vid, msg ->
                                    videoFeedViewModel.logPlaybackError(vid, currentUser.id, msg)
                                },
                                observeComments = { vid -> videoFeedViewModel.observeComments(vid) },
                                onPostComment = { vid, text, parentId, onDone ->
                                    videoFeedViewModel.addComment(vid, currentUser.id, text, parentId, onDone)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showUploadBottomSheet) {
            VideoUploadBottomSheet(
                currentUser = currentUser,
                currentLanguage = currentLanguage,
                dailyUploadCount = dailyUploadCount,
                progressState = uploadProgressState,
                onDismiss = {
                    uploadViewModel.resetState()
                    showUploadBottomSheet = false
                },
                onPublish = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags ->
                    uploadViewModel.publishVideo(
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
                onCancelUpload = { uploadViewModel.cancelUpload() },
                onRetryUpload = { uploadViewModel.retryUpload() },
                onResetState = { uploadViewModel.resetState() },
                onVideoPublishedNavigate = { videoId ->
                    uploadViewModel.resetState()
                    showUploadBottomSheet = false
                    navController.navigate(PlayerRoute(videoId))
                },
                onPublishWithVisibility = { title, desc, format, category, duration, sizeBytes, thumb, vidUri, tags, isPublic ->
                    uploadViewModel.publishVideo(
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
