package com.plantdex.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.capture.CaptureScreen
import com.plantdex.app.ui.collection.CatalogDetailScreen
import com.plantdex.app.ui.collection.MyCollectionScreen
import com.plantdex.app.ui.collection.MyRecordsScreen
import com.plantdex.app.ui.collection.UserCollectionScreen
import com.plantdex.app.ui.entry.EntryDetailScreen
import com.plantdex.app.ui.feed.FeedScreen
import com.plantdex.app.ui.map.PlantMapScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object FeedRoute
@Serializable data object MapRoute
@Serializable data object CaptureRoute
@Serializable data object MyCollectionRoute
@Serializable data object MyRecordsRoute
@Serializable data class CatalogRoute(val catalogId: String)
@Serializable data class EntryDetailRoute(val entryId: String)
@Serializable data class UserCollectionRoute(val userId: String, val userName: String)

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
) {
    Feed(FeedRoute, FeedRoute::class, "둘러보기", Icons.Filled.Explore),
    PlantMap(MapRoute, MapRoute::class, "지도", Icons.Filled.Map),
    Capture(CaptureRoute, CaptureRoute::class, "촬영", Icons.Filled.CameraAlt),
    MyCollection(MyCollectionRoute, MyCollectionRoute::class, "내 도감", Icons.AutoMirrored.Filled.MenuBook),
}

@Composable
fun PlantDexNavHost(user: UserProfile) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun openTab(route: Any) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun openEntry(entryId: String) = navController.navigate(EntryDetailRoute(entryId))
    fun openUser(userId: String, userName: String) {
        if (userId == user.uid) {
            openTab(MyCollectionRoute)
        } else {
            navController.navigate(UserCollectionRoute(userId, userName))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { openTab(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<FeedRoute> {
                FeedScreen(onEntryClick = ::openEntry, onUserClick = ::openUser)
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
                )
            }
            composable<MyCollectionRoute> {
                MyCollectionScreen(
                    user = user,
                    onCatalogClick = { navController.navigate(CatalogRoute(it)) },
                    onAllRecordsClick = { navController.navigate(MyRecordsRoute) },
                )
            }
            composable<CatalogRoute> { entry ->
                CatalogDetailScreen(
                    user = user,
                    catalogId = entry.toRoute<CatalogRoute>().catalogId,
                    onEntryClick = ::openEntry,
                    onCaptureClick = { openTab(CaptureRoute) },
                    onBack = { navController.popBackStack() },
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
