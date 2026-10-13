package com.example.musicsm.navigation

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.musicsm.ui.components.isLowEndDevice
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.ui.util.isOnline
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicsm.ui.components.GlassPanel
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.LocalHazeState
import com.example.musicsm.ui.components.LocalSongNavigator
import com.example.musicsm.ui.components.MiniPlayer
import com.example.musicsm.ui.components.rememberMixArtState
import com.example.musicsm.ui.components.SongNavigator
import com.example.musicsm.ui.components.glassBackdrop
import com.example.musicsm.ui.components.rememberHazeState
import com.example.musicsm.ui.components.LocalLiquidBackdrop
import com.example.musicsm.ui.components.LocalPlayerExpanded
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.example.musicsm.ui.player.DownloadStatusBar
import com.example.musicsm.ui.player.AmbientScreen
import com.example.musicsm.ui.player.ExpandedPlayer
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.update.UpdateDialog
import com.example.musicsm.ui.update.UpdateState
import com.example.musicsm.ui.update.UpdateViewModel
import com.example.musicsm.ui.rename.HymnIntroOverlay
import com.example.musicsm.ui.rename.HymnIntroViewModel
import com.example.musicsm.ui.account.GoogleSignInDialog
import com.example.musicsm.ui.account.YouTubeAccountViewModel
import com.example.musicsm.ui.account.YouTubeSignInPromptDialog
import com.example.musicsm.ui.share.rememberQrScanner
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.GlassFillStrong
import com.example.musicsm.ui.theme.OnDarkVariant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Short-lived feedback for link handling; the app has no snackbar host at the root. */
private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@Composable
fun MusicSmRoot(
    appIntents: StateFlow<AppIntent?>? = null,
    onIntentHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val intentViewModel: AppIntentViewModel = hiltViewModel()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val accountViewModel: YouTubeAccountViewModel = hiltViewModel()
    val hymnIntroViewModel: HymnIntroViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val signInPrompt by accountViewModel.prompt.collectAsStateWithLifecycle()
    val showSignIn by accountViewModel.showSignIn.collectAsStateWithLifecycle()
    val showHymnIntro by hymnIntroViewModel.visible.collectAsStateWithLifecycle()

    // Quietly look for a newer GitHub release once per launch; the popup only shows a version the
    // user hasn't already tapped "Later" on.
    LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val hazeState = rememberHazeState()
    val liquidBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val context = LocalContext.current
    var barHeight by remember { mutableStateOf(0.dp) }
    // Ambient mode is an overlay, not a destination: it has to cover the player sheet, which is
    // itself drawn above the nav host.
    var ambientMode by remember { mutableStateOf(false) }
    // Hoisted out of the intent handler below: resolving a string from a raw Context inside a
    // composable bypasses Compose's configuration tracking.
    val badLinkMessage = stringResource(R.string.shared_playlist_bad_link)
    val unknownCodeMessage = stringResource(R.string.scan_unknown_code)
    val scannerUnavailableMessage = stringResource(R.string.scan_unavailable)
    val signedInMessage = stringResource(R.string.youtube_signed_in)

    // Nothing to screensaver once playback is gone.
    LaunchedEffect(playerState.currentSong == null) {
        if (playerState.currentSong == null) ambientMode = false
    }

    // No internet on launch → open the Library (offline downloads) instead of an empty Home.
    val startDestination = remember { if (isOnline(context)) Routes.HOME else Routes.LIBRARY }

    val scope = rememberCoroutineScope()
    // 0 = collapsed (mini player), 1 = fully expanded Now-Playing sheet.
    val expand = remember { Animatable(0f) }
    val hasSong = playerState.currentSong != null
    // Derived so screens only recompose when the sheet opens/closes, not on every animation frame.
    val playerExpanded by remember { derivedStateOf { expand.value > 0.01f } }
    // The mini player's cover follows a crossfade/Mix into the next track.
    val mixBlend by playerViewModel.mixBlend.collectAsStateWithLifecycle()
    val miniMixArt = rememberMixArtState(
        blend = mixBlend,
        currentSongId = playerState.currentSong?.id,
        positionFlow = playerViewModel.position,
        isPlaying = playerState.isPlaying && !playerExpanded,
    )
    // Compose turns a Back key press into "move focus out" whenever something holds focus, and
    // swallows it. A text field left focused behind the sheet (e.g. the search box after tapping a
    // result) would eat the first back press, so focus is dropped as soon as the sheet opens.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(playerExpanded) {
        if (playerExpanded) focusManager.clearFocus(force = true)
    }

    // Bottom bar minimises while content scrolls down and comes back on scroll up (Apple Music
    // style). Watched from above the nav host, so every scrolling screen drives it for free.
    var barCollapsed by remember { mutableStateOf(false) }
    val barCollapse = remember { Animatable(0f) }
    val cheapDevice = isLowEndDevice()
    val minimizeBarEnabled by intentViewModel.minimizeBarOnScroll.collectAsStateWithLifecycle()
    val currentMinimizeBarEnabled by rememberUpdatedState(minimizeBarEnabled)
    // Turning the setting off mid-scroll must not leave the bar stuck minimised.
    LaunchedEffect(minimizeBarEnabled) {
        if (!minimizeBarEnabled) barCollapsed = false
    }
    LaunchedEffect(barCollapsed, cheapDevice) {
        val target = if (barCollapsed) 1f else 0f
        if (cheapDevice) {
            barCollapse.snapTo(target)
        } else {
            barCollapse.animateTo(
                target,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }
    // The tab whose icon the minimised pill shows; a detail screen keeps the tab it was opened from.
    var lastTab by remember { mutableStateOf(TopLevelDestination.HOME) }
    LaunchedEffect(currentRoute) {
        TopLevelDestination.entries.firstOrNull { it.route == currentRoute }?.let { lastTab = it }
        // Every new screen starts with the full bar, as nothing on it has been scrolled yet.
        barCollapsed = false
    }
    val barScrollConnection = remember(density) {
        val threshold = with(density) { BAR_COLLAPSE_THRESHOLD.toPx() }
        object : NestedScrollConnection {
            // Travel since the last direction change, so a small wobble doesn't flip the bar.
            private var travel = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Consumed only: overscroll at either end moves nothing and should decide nothing.
                val dy = consumed.y
                if (dy == 0f || !currentMinimizeBarEnabled) return Offset.Zero
                if (travel != 0f && (dy < 0f) != (travel < 0f)) travel = 0f
                travel += dy
                if (travel <= -threshold) {
                    barCollapsed = true
                    travel = 0f
                } else if (travel >= threshold) {
                    barCollapsed = false
                    travel = 0f
                }
                return Offset.Zero
            }
        }
    }

    val sheetSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        val fullPx = constraints.maxHeight.toFloat()
        // Landscape swaps the bottom bar for a dock on the start edge (iPad Apple Music style).
        val dockMode = useSideDock(maxWidth, maxHeight)
        // Width the dock takes from the content, insets included; measured, so it tracks font scale.
        var dockSpace by remember { mutableStateOf(0.dp) }
        val contentStart = if (dockMode) dockSpace else 0.dp

        val expandNow = { scope.launch { expand.animateTo(1f, sheetSpring) } }
        val collapse = { scope.launch { expand.animateTo(0f, sheetSpring) } }
        // Drag up on the mini bar (dy < 0) grows the sheet; drag down shrinks it.
        val onDragDelta: (Float) -> Unit = { dy ->
            scope.launch { expand.snapTo((expand.value - dy / fullPx).coerceIn(0f, 1f)) }
        }
        val onDragFinished = {
            scope.launch { expand.animateTo(if (expand.value > 0.35f) 1f else 0f, sheetSpring) }
        }

        // Lets the song options sheet (and the player) jump to a detail page from anywhere;
        // the player sheet slides away first so the destination isn't hidden behind it.
        val songNavigator = remember(navController) {
            SongNavigator(
                openAlbum = { id ->
                    collapse()
                    navController.navigate(Routes.album(id))
                },
                openArtist = { id ->
                    collapse()
                    navController.navigate(Routes.artist(id))
                },
            )
        }

        // Switching bottom-nav tabs: single-top, restoring each tab's own back stack.
        val navigateToTab: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // Launcher shortcuts, deep links, shared YouTube links, widget/tile taps, voice search —
        // and QR codes scanned in-app, which deliberately run through this same handler so a
        // scanned playlist behaves exactly like a tapped link.
        val handleAppIntent: suspend (AppIntent) -> Unit = { appIntent ->
            when (appIntent) {
                AppIntent.Resume -> {
                    if (playerViewModel.resumePlayback()) expandNow()
                }
                is AppIntent.OpenTab -> navigateToTab(appIntent.route)
                is AppIntent.Search -> {
                    intentViewModel.prefillSearch(appIntent.query)
                    navigateToTab(Routes.SEARCH)
                    if (appIntent.playFirst && appIntent.query.isNotBlank()) {
                        if (playerViewModel.searchAndPlay(appIntent.query)) expandNow()
                    }
                }
                is AppIntent.PlaySong -> {
                    if (playerViewModel.playSongId(appIntent.songId)) {
                        expandNow()
                    } else {
                        toast(context, context.getString(R.string.error_open_track))
                    }
                }
                is AppIntent.OpenAlbum -> navController.navigate(Routes.album(appIntent.albumId))
                is AppIntent.OpenArtist -> navController.navigate(Routes.artist(appIntent.artistId))
                is AppIntent.OpenPlaylist ->
                    navController.navigate(Routes.localPlaylist(appIntent.playlistId))
                AppIntent.OpenLiked -> navController.navigate(Routes.liked())
                AppIntent.OpenDownloads -> navController.navigate(Routes.DOWNLOADS)
                is AppIntent.ImportSharedPlaylist -> {
                    if (intentViewModel.offerSharedPlaylist(appIntent.payload)) {
                        collapse()
                        navController.navigate(Routes.SHARED_PLAYLIST)
                    } else {
                        toast(context, badLinkMessage)
                    }
                }
                is AppIntent.Unsupported -> toast(context, context.getString(appIntent.messageRes))
            }
        }
        val currentIntentHandler by rememberUpdatedState(handleAppIntent)

        LaunchedEffect(appIntents) {
            appIntents?.filterNotNull()?.collect { appIntent ->
                onIntentHandled()
                currentIntentHandler(appIntent)
            }
        }

        // Reading a playlist QR. Phone cameras ignore `musicsm://` codes, so this is the only way
        // a scanned playlist can actually open.
        val startScan = rememberQrScanner(
            onScanned = { raw ->
                val scanned = appIntentFromLink(raw)
                if (scanned == null) {
                    toast(context, unknownCodeMessage)
                } else {
                    scope.launch { currentIntentHandler(scanned) }
                }
            },
            onUnavailable = { toast(context, scannerUnavailableMessage) },
        )

        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalBottomBarPadding provides barHeight,
            LocalSongNavigator provides songNavigator,
            LocalPlayerExpanded provides playerExpanded,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val scrimColor = AppBackground
                // Content fills the whole screen and registers as the blur source. The background is
                // painted *inside* the source node — after glassBackdrop, so hazeSource records it —
                // because during a nav transition the incoming screen's first frames are near-empty,
                // and a source with no opaque fill of its own lets the glass sample transparency and
                // drop its blur for a frame. Ordering matters: hazeSource only captures what is drawn
                // inner to it, so a background applied before glassBackdrop would not be recorded.
                MusicSmNavHost(
                    navController = navController,
                    playerViewModel = playerViewModel,
                    onExpandPlayer = { expandNow() },
                    onScanCode = startScan,
                    startDestination = startDestination,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(barScrollConnection)
                        .layerBackdrop(liquidBackdrop)
                        .glassBackdrop(hazeState)
                        .background(AppBackground)
                        // Inside the recorded layer, so the strip under the dock is still painted.
                        .padding(start = contentStart),
                )

                // The content does not simply end at the glass: it fades into the background over
                // the strip above the bars. A hard cut at the blur's edge announces where the
                // panel stops, which is the one thing a pane of glass should not do.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(barHeight + SCRIM_OVERHANG)
                        .drawWithCache {
                            val fade = Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                0.35f to scrimColor.copy(alpha = 0.15f),
                                0.60f to scrimColor.copy(alpha = 0.40f),
                                0.85f to scrimColor.copy(alpha = 0.70f),
                                1.00f to scrimColor,
                            )
                            onDrawBehind { drawRect(fade) }
                        },
                )

                // Stitch bottom: a floating frosted mini-card above a full-width frosted tab bar.
                // The bars are outside the recorded nav host, so their Liquid Glass can refract it.
                CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
                val miniPlayer: (@Composable (compact: Boolean, modifier: Modifier) -> Unit)? =
                    if (hasSong) {
                        { compact, placement ->
                            MiniPlayer(
                                state = playerState,
                                onClick = { expandNow() },
                                onTogglePlay = playerViewModel::togglePlayPause,
                                onNext = playerViewModel::next,
                                compact = compact,
                                mixArt = miniMixArt,
                                modifier = placement
                                    // Fade out as the full sheet takes over.
                                    .graphicsLayer {
                                        alpha = (1f - expand.value * 1.5f).coerceIn(0f, 1f)
                                    }
                                    // Drag up to expand into the full player.
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { _, dy -> onDragDelta(dy) },
                                            onDragEnd = { onDragFinished() },
                                            onDragCancel = { onDragFinished() },
                                        )
                                    },
                            )
                        }
                    } else {
                        null
                    }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = contentStart)
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                ) {
                    // Wavy download progress + completion toast, above the mini player.
                    DownloadStatusBar(
                        onClick = { navController.navigate(Routes.DOWNLOADS) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    val searchSelected = currentRoute == Routes.SEARCH
                    if (dockMode) {
                        // Tabs live in the dock, so only the mini player floats at the bottom,
                        // centred over the content rather than the whole screen.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .widthIn(max = DOCK_MINI_PLAYER_MAX_WIDTH)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                        ) {
                            miniPlayer?.invoke(false, Modifier)
                        }
                    } else FloatingBottomBar(
                        collapse = { barCollapse.value },
                        miniPlayerCompact = barCollapsed,
                        onExpandBar = { barCollapsed = false },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp),
                        tabs = {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigate = { dest -> navigateToTab(dest.route) },
                            )
                        },
                        collapsedTab = {
                            val active = currentRoute == lastTab.route
                            Icon(
                                imageVector = if (active) lastTab.selectedIcon else lastTab.unselectedIcon,
                                contentDescription = stringResource(lastTab.labelRes),
                                tint = if (active) MaterialTheme.colorScheme.primary else OnDarkVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        },
                        // The main tab pill and a detached search pill beside it: two separate
                        // panes of glass, so search reads as its own control rather than a tab.
                        search = {
                            GlassPanel(
                                shape = CircleShape,
                                tint = GlassFillStrong,
                                liquid = true,
                            ) {
                                IconButton(
                                    onClick = { navigateToTab(Routes.SEARCH) },
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Icon(
                                        imageVector = if (searchSelected) Icons.Filled.Search else Icons.Outlined.Search,
                                        contentDescription = stringResource(R.string.nav_search),
                                        tint = if (searchSelected) MaterialTheme.colorScheme.primary else OnDarkVariant,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        },
                        miniPlayer = miniPlayer,
                    )
                }

                if (dockMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .onSizeChanged { dockSpace = with(density) { it.width.toDp() } }
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Start + WindowInsetsSides.Vertical,
                                ),
                            )
                            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SideDock(
                            currentRoute = currentRoute,
                            onNavigate = navigateToTab,
                        )
                    }
                }
                }

                // Full-screen expanding player — always composed, slides over everything.
                // Composed BEFORE the player so the player's own back handlers (lyrics/queue)
                // take priority; this one only collapses when nothing inner is showing. Kept
                // unconditional so its registration order never shifts when a song comes and goes.
                BackHandler(enabled = hasSong && playerExpanded) { collapse() }
                if (hasSong) {
                    ExpandedPlayer(
                        viewModel = playerViewModel,
                        progress = { expand.value },
                        onCollapse = { collapse() },
                        onDragDelta = onDragDelta,
                        onDragFinished = { onDragFinished() },
                        onEnterAmbient = { ambientMode = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Ambient mode sits above even the player sheet — it is a screensaver, so nothing
                // else may draw over it.
                if (ambientMode && hasSong) {
                    AmbientScreen(
                        viewModel = playerViewModel,
                        onExit = { ambientMode = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // One-time "MusicSM is now Hymn" intro for people updating from MusicSM. Popups
                // (which open in their own windows) wait until it's gone.
                if (showHymnIntro) {
                    HymnIntroOverlay(
                        onFinished = hymnIntroViewModel::dismiss,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // "A new version is available" popup (once per launch, per non-dismissed version).
                (updateState as? UpdateState.Available)?.takeIf { !showHymnIntro }?.let { available ->
                    val appVersion = remember {
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull().orEmpty()
                    }
                    UpdateDialog(
                        info = available.info,
                        currentVersion = appVersion,
                        onUpdate = { updateViewModel.install(available.info) },
                        onLater = { updateViewModel.dismissVersion(available.info) },
                        onDismiss = { updateViewModel.dismissVersion(available.info) },
                    )
                }

                // "YouTube wants you to sign in", raised by the player when anonymous playback is
                // blocked, and the Google sign-in page it opens.
                if (showSignIn) {
                    GoogleSignInDialog(
                        onSignedIn = { cookie ->
                            accountViewModel.completeSignIn(cookie).also { ok ->
                                if (ok) toast(context, signedInMessage)
                            }
                        },
                        onClose = accountViewModel::cancelSignIn,
                    )
                } else if (!showHymnIntro) {
                    signInPrompt?.let { reason ->
                        YouTubeSignInPromptDialog(
                            reason = reason,
                            onSignIn = accountViewModel::openSignIn,
                            onDismiss = accountViewModel::dismissPrompt,
                        )
                    }
                }
            }
        }
    }
}

/**
 * How far the fade reaches above the bars.
 *
 * Long enough that the transition happens over several rows of a list rather than across one,
 * which is what keeps it reading as depth instead of as a band drawn over the content.
 */
private val SCRIM_OVERHANG = 56.dp

/** Same cap as the bottom bar, so the landscape mini player stays a card, not a banner. */
private val DOCK_MINI_PLAYER_MAX_WIDTH = 520.dp

/** How far content has to scroll one way before the bottom bar minimises or comes back. */
private val BAR_COLLAPSE_THRESHOLD = 24.dp
