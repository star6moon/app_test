package com.plantdex.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.plantdex.app.R
import com.plantdex.app.util.Formatters
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.achievements.BadgeCelebrationHost
import com.plantdex.app.ui.achievements.BadgesScreen
import com.plantdex.app.ui.capture.CaptureScreen
import com.plantdex.app.ui.collection.BookmarksScreen
import com.plantdex.app.ui.collection.CatalogDetailScreen
import com.plantdex.app.ui.collection.MyCollectionScreen
import com.plantdex.app.ui.collection.MyRecordsScreen
import com.plantdex.app.ui.collection.UserCollectionScreen
import com.plantdex.app.ui.entry.EntryDetailScreen
import com.plantdex.app.ui.feed.FeedScreen
import com.plantdex.app.ui.map.PlantMapScreen
import com.plantdex.app.ui.settings.BlockedUsersScreen
import com.plantdex.app.ui.settings.SettingsScreen
import com.plantdex.app.ui.social.NotificationsScreen
import com.plantdex.app.ui.social.RankingScreen
import com.plantdex.app.ui.species.SpeciesScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object FeedRoute
@Serializable data object MapRoute
@Serializable data object CaptureRoute
@Serializable data object MyCollectionRoute
@Serializable data object MyRecordsRoute
@Serializable data object BookmarksRoute
@Serializable data class CatalogRoute(val catalogId: String)
@Serializable data class EntryDetailRoute(val entryId: String)
@Serializable data class UserCollectionRoute(val userId: String, val userName: String)
@Serializable data class SpeciesRoute(val scientificName: String, val name: String, val family: String? = null)
@Serializable data object NotificationsRoute
@Serializable data object BadgesRoute
@Serializable data object RankingRoute
@Serializable data object SettingsRoute
@Serializable data object BlockedUsersRoute

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Feed(FeedRoute, FeedRoute::class, R.string.nav_feed, Icons.Filled.Explore),
    PlantMap(MapRoute, MapRoute::class, R.string.nav_map, Icons.Filled.Map),
    Capture(CaptureRoute, CaptureRoute::class, R.string.nav_capture, Icons.Filled.CameraAlt),
    MyCollection(MyCollectionRoute, MyCollectionRoute::class, R.string.nav_my_collection, Icons.AutoMirrored.Filled.MenuBook),
}

/** 서버의 데이터 구조 버전. 올리면 모든 계정이 migrateMyData 를 다시 한 번 호출합니다. */
private const val DATA_VERSION = 2

@Composable
fun PlantDexNavHost(user: UserProfile) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val snackbarHostState = remember { SnackbarHostState() }

    // 예전 버전에서 만든 기록을 새 구조(지도 영역 검색, 통계)에 맞춥니다. 계정마다 한 번만.
    val context = LocalContext.current
    val container = appContainer()
    LaunchedEffect(user.uid) {
        val prefs = context.getSharedPreferences("migrations", Context.MODE_PRIVATE)
        val key = "v${DATA_VERSION}_${user.uid}"
        if (prefs.getBoolean(key, false)) return@LaunchedEffect
        runCatching { container.collectionRepository.migrateMyData() }
            .onSuccess { prefs.edit().putBoolean(key, true).apply() }
    }
    val coroutineScope = rememberCoroutineScope()

    fun openTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        // 지도 탭은 그 탭에서 열어 둔 기록·식물 화면이 아니라 마지막으로 보던 지도로 돌아갑니다.
        // (지도의 위치·확대는 저장된 화면 상태로 남고, 앱을 새로 켜면 내 위치에서 시작합니다)
        if (route == MapRoute) navController.popBackStack<MapRoute>(inclusive = false)
    }
    fun openEntry(entryId: String) = navController.navigate(EntryDetailRoute(entryId))
    fun openSpecies(scientificName: String, name: String, family: String?) =
        navController.navigate(SpeciesRoute(scientificName, name, family))
    fun openUser(userId: String, userName: String) {
        if (userId == user.uid) {
            openTab(MyCollectionRoute)
        } else {
            navController.navigate(UserCollectionRoute(userId, userName))
        }
    }

    // 푸시 알림: 권한 요청(Android 13+) 후 이 기기의 토큰을 등록합니다.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(user.uid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = context.getSharedPreferences("migrations", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("asked_notifications", false)) {
                prefs.edit().putBoolean("asked_notifications", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            container.socialRepository.registerPushToken(token)
        }
    }

    // 알림을 눌러 앱을 열었으면 해당 기록이나 사람으로 이동합니다.
    val deepLink by container.pendingDeepLink.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        container.pendingDeepLink.value = null
        when {
            link.entryId != null -> openEntry(link.entryId)
            link.actorUid != null -> openUser(link.actorUid, "")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PlantDexBottomBar(
                isSelected = { destination ->
                    currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                },
                onSelect = { openTab(it.route) },
            )
        },
    ) { padding ->
      val suspension by container.moderationRepository.suspension.collectAsStateWithLifecycle()
      // 새 배지는 어느 화면에서든 축하합니다.
      BadgeCelebrationHost(user.uid)
      val online by container.networkMonitor.online.collectAsStateWithLifecycle()
      Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
        if (!online) OfflineBanner()
        if (suspension.isSuspended) SuspensionBanner(suspension.untilMillis ?: 0L)
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.weight(1f),
        ) {
            composable<FeedRoute> {
                FeedScreen(
                    onEntryClick = ::openEntry,
                    onUserClick = ::openUser,
                    onNotificationsClick = { navController.navigate(NotificationsRoute) },
                )
            }
            composable<MapRoute> {
                PlantMapScreen(user = user, onEntryClick = ::openEntry)
            }
            composable<CaptureRoute> {
                CaptureScreen(
                    onSaved = { entryId, message ->
                        navController.navigate(MyCollectionRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                        openEntry(entryId)
                        if (message != null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(message, withDismissAction = true) }
                        }
                    },
                    onOpenEntry = ::openEntry,
                )
            }
            composable<MyCollectionRoute> {
                MyCollectionScreen(
                    user = user,
                    onCatalogClick = { navController.navigate(CatalogRoute(it)) },
                    onAllRecordsClick = { navController.navigate(MyRecordsRoute) },
                    onBookmarksClick = { navController.navigate(BookmarksRoute) },
                    onSettingsClick = { navController.navigate(SettingsRoute) },
                    onBadgesClick = { navController.navigate(BadgesRoute) },
                    onRankingClick = { navController.navigate(RankingRoute) },
                )
            }
            composable<NotificationsRoute> {
                NotificationsScreen(
                    onEntryClick = ::openEntry,
                    onUserClick = ::openUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<BadgesRoute> {
                BadgesScreen(uid = user.uid, onBack = { navController.popBackStack() })
            }
            composable<RankingRoute> {
                RankingScreen(uid = user.uid, onUserClick = ::openUser, onBack = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBlockedUsersClick = { navController.navigate(BlockedUsersRoute) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<BlockedUsersRoute> {
                BlockedUsersScreen(onBack = { navController.popBackStack() })
            }
            composable<BookmarksRoute> {
                BookmarksScreen(onEntryClick = ::openEntry, onBack = { navController.popBackStack() })
            }
            composable<CatalogRoute> { entry ->
                CatalogDetailScreen(
                    user = user,
                    catalogId = entry.toRoute<CatalogRoute>().catalogId,
                    onSpeciesClick = { scientificName, name -> openSpecies(scientificName, name, null) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SpeciesRoute> { entry ->
                val route = entry.toRoute<SpeciesRoute>()
                SpeciesScreen(
                    uid = user.uid,
                    scientificName = route.scientificName,
                    name = route.name,
                    family = route.family,
                    onEntryClick = ::openEntry,
                    onCaptureClick = { openTab(CaptureRoute) },
                    onBack = { navController.popBackStack() },
                    onSpeciesClick = ::openSpecies,
                )
            }
            composable<MyRecordsRoute> {
                MyRecordsScreen(user = user, onEntryClick = ::openEntry, onBack = { navController.popBackStack() })
            }
            composable<EntryDetailRoute> { entry ->
                val route = entry.toRoute<EntryDetailRoute>()
                EntryDetailScreen(
                    entryId = route.entryId,
                    onBack = { navController.popBackStack() },
                    onUserClick = ::openUser,
                    onSpeciesClick = ::openSpecies,
                )
            }
            composable<UserCollectionRoute> { entry ->
                val route = entry.toRoute<UserCollectionRoute>()
                UserCollectionScreen(
                    userId = route.userId,
                    userName = route.userName,
                    onEntryClick = ::openEntry,
                    onBack = { navController.popBackStack() },
                )
            }
        }
      }
    }
}

/** 인터넷이 끊겼을 때 모든 화면 위에 띄우는 안내. 저장된 기록은 계속 볼 수 있습니다. */
@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.common_offline_banner), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 신고 누적으로 활동이 정지됐을 때 모든 화면 위에 띄우는 안내 */
@Composable
private fun SuspensionBanner(untilMillis: Long) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Text(
            stringResource(R.string.moderation_suspended_banner, Formatters.dateTime(untilMillis)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 화면 아래에 떠 있는 알약 모양 탭 막대. 가운데 촬영 버튼은 초록 원으로 강조합니다. */
@Composable
private fun PlantDexBottomBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val isCapture = destination == TopLevelDestination.Capture
                val label = stringResource(destination.labelRes)
                val labelContent: (@Composable () -> Unit)? = if (isCapture) null else { { Text(label) } }
                NavigationBarItem(
                    selected = isSelected(destination),
                    onClick = { onSelect(destination) },
                    icon = {
                        if (isCapture) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(destination.icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            Icon(destination.icon, contentDescription = null)
                        }
                    },
                    label = labelContent,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = if (isCapture) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
