package com.hungryears.music.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hungryears.music.ui.home.HomeScreen
import com.hungryears.music.ui.library.LibraryScreen
import com.hungryears.music.ui.navigation.TopDestination
import com.hungryears.music.ui.navigation.IconContent
import com.hungryears.music.ui.playerbar.MiniPlayer
import com.hungryears.music.ui.playlist.PlaylistDetailScreen
import com.hungryears.music.ui.playlist.PlaylistsScreen
import com.hungryears.music.ui.search.SearchScreen

private const val PLAYLIST_DETAIL_ROUTE = "playlist/{playlistId}"
private const val PLAYLIST_ID_ARG = "playlistId"

@Composable
fun HungryEarsApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                MiniPlayer(onOpenNowPlaying = {})
                NavigationBar(windowInsets = WindowInsets(0)) {
                    TopDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { destination.IconContent() },
                            label = { Text(stringResource(destination.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(TopDestination.Home.route) { HomeScreen(modifier = Modifier) }
            composable(TopDestination.Library.route) { LibraryScreen(modifier = Modifier) }
            composable(TopDestination.Search.route) {
                SearchScreen(
                    modifier = Modifier,
                    onOpenPlaylist = { playlistId ->
                        navController.navigate("playlist/$playlistId")
                    },
                )
            }
            composable(TopDestination.Playlists.route) {
                PlaylistsScreen(
                    modifier = Modifier,
                    onOpenPlaylist = { playlistId ->
                        navController.navigate("playlist/$playlistId")
                    },
                )
            }
            composable(
                route = PLAYLIST_DETAIL_ROUTE,
                arguments = listOf(navArgument(PLAYLIST_ID_ARG) { type = NavType.LongType }),
            ) { entry ->
                val playlistId = entry.arguments?.getLong(PLAYLIST_ID_ARG) ?: return@composable
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}