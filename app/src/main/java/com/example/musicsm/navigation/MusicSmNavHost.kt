package com.example.musicsm.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.musicsm.ui.album.AlbumDetailScreen
import com.example.musicsm.ui.artist.ArtistDetailScreen
import com.example.musicsm.ui.components.isLowEndDevice
import com.example.musicsm.ui.home.HomeScreen
import com.example.musicsm.ui.theme.WithoutBounce
import com.example.musicsm.ui.importer.ImportPlaylistScreen
import com.example.musicsm.ui.library.CachedSongsScreen
import com.example.musicsm.ui.library.LibraryScreen
import com.example.musicsm.ui.library.LocalMusicScreen
import com.example.musicsm.ui.library.PlaylistDetailScreen
import com.example.musicsm.ui.player.DownloadsScreen
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.search.SearchScreen
import com.example.musicsm.ui.settings.EqualizerScreen
import com.example.musicsm.ui.settings.SettingsScreen
import com.example.musicsm.ui.share.SharedPlaylistScreen
import com.example.musicsm.ui.stats.StatsScreen

@Composable
fun MusicSmNavHost(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    onExpandPlayer: () -> Unit,
    onScanCode: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.HOME,
) {
    // A push transition slides two whole screens past each other, so for its entire duration the
    // device is drawing both. That is a reasonable trade on hardware that can also blur them, and
    // a bad one on hardware that cannot, so the cheap devices get the destination immediately
    // instead of getting it late and stuttering on the way.
    val dur = if (isLowEndDevice()) 0 else 300
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur))
        },
    ) {
        composable(Routes.HOME) {
            // Home keeps Android's stretch; the iOS-style bounce is for every other screen.
            WithoutBounce {
                HomeScreen(
                    onPlaySongs = { songs, index -> playerViewModel.play(songs, index) },
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    // A remote playlist is a cover plus a track list, which is exactly what the album
                    // detail screen renders; the source resolves either kind of id.
                    onOpenPlaylist = { navController.navigate(Routes.album(it)) },
                    onOpenCached = { navController.navigate(Routes.CACHED) },
                )
            }
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                playerViewModel = playerViewModel,
                onPlaySong = { playerViewModel.playWithRadio(it) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                playerViewModel = playerViewModel,
                onOpenLiked = { navController.navigate(Routes.liked()) },
                onOpenPlaylist = { navController.navigate(Routes.localPlaylist(it)) },
                onImportPlaylist = { navController.navigate(Routes.IMPORT) },
                onScanCode = onScanCode,
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenLocal = { navController.navigate(Routes.LOCAL) },
                onOpenCached = { navController.navigate(Routes.CACHED) },
                // A YouTube Music playlist opens on the album/playlist detail screen.
                onOpenRemotePlaylist = { navController.navigate(Routes.album(it)) },
            )
        }
        composable(
            route = Routes.LOCAL,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            LocalMusicScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.STATS,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            StatsScreen(
                onBack = { navController.popBackStack() },
                onPlaySongs = { songs, index -> playerViewModel.play(songs, index) },
                onOpenSearch = {
                    navController.navigate(Routes.SEARCH) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.SHARED_PLAYLIST,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            SharedPlaylistScreen(
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { id ->
                    // Replace the preview: backing out should not offer to import again.
                    navController.navigate(Routes.localPlaylist(id)) {
                        popUpTo(Routes.SHARED_PLAYLIST) { inclusive = true }
                    }
                },
                onPlay = { songs, index -> playerViewModel.play(songs, index, isPlaylist = true) },
            )
        }
        composable(
            route = Routes.SETTINGS,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            )
        }
        composable(
            route = Routes.EQUALIZER,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.IMPORT,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            ImportPlaylistScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DOWNLOADS,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            DownloadsScreen(playerViewModel = playerViewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CACHED,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            CachedSongsScreen(playerViewModel = playerViewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.ALBUM,
            arguments = listOf(navArgument(Routes.ARG_ALBUM_ID) { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            AlbumDetailScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ARTIST,
            arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            ArtistDetailScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
            )
        }
        composable(
            route = Routes.LOCAL_PLAYLIST,
            arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.LongType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(dur)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(dur)) },
        ) {
            PlaylistDetailScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
