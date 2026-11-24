@file:OptIn(ExperimentalFoundationApi::class)

package com.example.musicsm.ui.home

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.recommend.DailyMix
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ArtworkSize
import com.example.musicsm.ui.components.ErrorState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SkeletonBlock
import com.example.musicsm.ui.components.rememberArtworkPx
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.components.rememberShimmerProgress
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.GlassStrokeSoft
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.LocalMusicSmPalette
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDarkMuted
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.SurfaceLow
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenCached: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val moodLoading by viewModel.moodLoading.collectAsStateWithLifecycle()
    val quickTiles by viewModel.quickTiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Shelves are composed up to a screen ahead (and kept half a screen behind) in idle frame
    // time, so a shelf is already built when it scrolls in rather than built in that frame.
    val cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f) }
    val listState = rememberLazyListState(cacheWindow = cacheWindow)
    // Cover of the hero page in view: the page's ambient glow is tinted from it.
    var heroArtwork by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(
            artworkUrl = heroArtwork,
            listState = listState,
            modifier = Modifier.matchParentSize(),
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Keyed on the state's kind, so feed updates (paging, the network phase landing) keep
            // the list in place and only Loading → Content → Error transitions fade.
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
                label = "homeState",
            ) { s ->
                when (s) {
                    is HomeUiState.Loading -> HomeSkeleton()

                    is HomeUiState.Error -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ErrorState(message = s.message, onRetry = viewModel::load)
                    }

                    is HomeUiState.Content -> HomeContent(
                        sections = s.feed.sections,
                        mixes = s.mixes,
                        offline = s.offline,
                        loadingMore = s.loadingMore,
                        canLoadMore = s.canLoadMore,
                        pages = s.pages,
                        listState = listState,
                        moods = viewModel.moods,
                        moodLoading = moodLoading,
                        quickTiles = quickTiles,
                        onPlaySongs = onPlaySongs,
                        onShuffleSongs = onShuffleSongs,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        onOpenPlaylist = onOpenPlaylist,
                        onSaveShelf = viewModel::saveShelf,
                        onLoadMore = viewModel::loadMore,
                        onOpenCached = onOpenCached,
                        onOpenStats = onOpenStats,
                        onMood = { tile ->
                            viewModel.playMood(
                                tile = tile,
                                onReady = { songs -> onPlaySongs(songs, 0) },
                                onFailed = {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.home_mood_failed, tile.title),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        },
                        onOpenLiked = onOpenLiked,
                        onOpenDownloads = onOpenDownloads,
                        onShuffleLiked = { viewModel.shuffleLiked(onShuffleSongs) },
                        onHeroArtwork = { heroArtwork = it },
                    )
                }
            }
        }
    }
}

/**
 * The page's backdrop: two soft blooms, the larger tinted from the hero cover in view so the page
 * takes on the colour of what it is featuring. Both fade back as the page scrolls, leaving the
 * lower shelves on a calm surface.
 */
@Composable
private fun AmbientBackdrop(
    artworkUrl: String?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    // Palette tokens are composable reads, so they are hoisted out of the draw lambda.
    val secondary = Lavender
    val peak = if (LocalMusicSmPalette.current.isLight) 0.20f else 0.36f
    val glow = rememberDominantColorState(artworkUrl, fallback = Coral)
    // No base fill of its own: the nav host already paints the same background behind every
    // screen, and a second full-screen fill is pure overdraw on every scroll frame.
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scrolling only fades this layer. Its gradients are recorded once per colour, so a
                // scroll frame changes a layer property instead of redrawing the blooms.
                .graphicsLayer {
                    val scrolled = if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        (listState.firstVisibleItemScrollOffset / (size.height * 0.5f)).coerceIn(0f, 1f)
                    }
                    alpha = 1f - 0.6f * scrolled
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawWithCache {
                    val primaryCenter = Offset(size.width * 0.15f, 0f)
                    val primaryRadius = size.width * 1.1f
                    val secondCenter = Offset(size.width, size.height * 0.22f)
                    val secondRadius = size.width * 0.8f
                    val primary = Brush.radialGradient(
                        colors = listOf(glow.value.copy(alpha = peak), Color.Transparent),
                        center = primaryCenter,
                        radius = primaryRadius,
                    )
                    val second = Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = peak * 0.45f), Color.Transparent),
                        center = secondCenter,
                        radius = secondRadius,
                    )
                    // Each bloom is transparent past its radius, so only the band it reaches is
                    // filled: the lower part of a tall screen would otherwise be shaded for nothing.
                    val primarySize = Size(size.width, (primaryCenter.y + primaryRadius).coerceAtMost(size.height))
                    val secondSize = Size(size.width, (secondCenter.y + secondRadius).coerceAtMost(size.height))
                    onDrawBehind {
                        drawRect(primary, size = primarySize)
                        drawRect(second, size = secondSize)
                    }
                },
        )
    }
}

@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    mixes: List<DailyMix>,
    offline: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    pages: Int,
    listState: LazyListState,
    moods: List<BrowseTile>,
    moodLoading: String?,
    quickTiles: QuickTiles,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSaveShelf: (String, List<Song>) -> Unit,
    onLoadMore: () -> Unit,
    onOpenCached: () -> Unit,
    onOpenStats: () -> Unit,
    onMood: (BrowseTile) -> Unit,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShuffleLiked: () -> Unit,
    onHeroArtwork: (String?) -> Unit,
) {
    // The hero features the first shelf with something to play or open (artists are left to their
    // own row of avatars). The rest of that shelf follows further down rather than repeating the
    // hero's items in a second row.
    val featuredIndex = remember(sections) {
        sections.indexOfFirst { section -> section.items.any { it !is HomeItem.ArtistItem } }
    }
    val featured = sections.getOrNull(featuredIndex)
    val featuredSongs = remember(featured) { featured?.songs().orEmpty() }
    val heroItems = remember(featured) {
        featured?.items?.filter { it !is HomeItem.ArtistItem }?.take(HERO_COUNT).orEmpty()
    }
    val heroKeys = remember(heroItems) { heroItems.mapTo(HashSet()) { it.key() } }
    // The first shelf of tracks is laid out as quick picks; the rest as cards, for some rhythm.
    val quickPicksIndex = remember(sections) {
        sections.indexOfFirst { section ->
            section.items.isNotEmpty() && section.items.all { it is HomeItem.SongItem }
        }
    }
    val trendingTitle = stringResource(R.string.shelf_trending)

    if (heroItems.isEmpty()) {
        LaunchedEffect(Unit) { onHeroArtwork(null) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
    ) {
        item(key = HEADER_KEY) {
            HomeHeader(offline = offline, onOpenStats = onOpenStats)
        }

        if (!offline && moods.isNotEmpty()) {
            item(key = MOODS_KEY) {
                MoodChips(moods = moods, loadingId = moodLoading, onClick = onMood)
            }
        }

        item(key = TILES_KEY) {
            QuickTilesRow(
                tiles = quickTiles,
                onOpenLiked = onOpenLiked,
                onOpenDownloads = onOpenDownloads,
                onShuffleLiked = onShuffleLiked,
            )
        }

        if (offline) {
            item(key = OFFLINE_KEY) {
                OfflineCard(empty = sections.isEmpty(), onOpenCached = onOpenCached)
            }
        }

        if (heroItems.isNotEmpty()) {
            item(key = HERO_KEY) {
                HeroCarousel(
                    entries = heroItems.mapNotNull { item ->
                        item.toHeroEntry(
                            onPlay = { song ->
                                val index = featuredSongs.indexOfFirst { it.id == song.id }
                                if (index >= 0) onPlaySongs(featuredSongs, index)
                            },
                            onOpenAlbum = onOpenAlbum,
                            onOpenPlaylist = onOpenPlaylist,
                        )
                    },
                    onShuffle = { onShuffleSongs(featuredSongs) },
                    onArtwork = onHeroArtwork,
                )
            }
        }

        if (mixes.isNotEmpty()) {
            item(key = MIXES_KEY) {
                Column(modifier = Modifier.animateItem()) {
                    MixesRow(mixes = mixes, onPlay = { mix -> onPlaySongs(mix.songs, 0) })
                }
            }
        }

        itemsIndexed(sections, key = { _, section -> section.title }) { index, section ->
            Column(modifier = Modifier.animateItem()) {
                val ranked = section.title == trendingTitle
                when {
                    index == featuredIndex -> {
                        val rest = remember(section, heroKeys) { section.items.filter { it.key() !in heroKeys } }
                        when {
                            rest.isEmpty() -> Unit

                            rest.all { it is HomeItem.SongItem } -> {
                                val entries = remember(featuredSongs, heroKeys) {
                                    featuredSongs.withIndex().filter { "song:${it.value.id}" !in heroKeys }
                                }
                                ShelfHeader(
                                    title = section.title,
                                    onSave = { onSaveShelf(section.title, featuredSongs) },
                                )
                                SongGrid(
                                    entries = entries,
                                    ranked = ranked,
                                    onPlay = { i -> onPlaySongs(featuredSongs, i) },
                                )
                            }

                            else -> ShelfRow(
                                section = remember(section, rest) { section.copy(items = rest) },
                                onPlaySongs = onPlaySongs,
                                onOpenAlbum = onOpenAlbum,
                                onOpenArtist = onOpenArtist,
                                onOpenPlaylist = onOpenPlaylist,
                                onSaveShelf = onSaveShelf,
                            )
                        }
                    }

                    ranked || index == quickPicksIndex -> {
                        val songs = remember(section) { section.songs() }
                        val entries = remember(songs) { songs.withIndex().toList() }
                        ShelfHeader(title = section.title, onSave = { onSaveShelf(section.title, songs) })
                        SongGrid(
                            entries = entries,
                            ranked = ranked,
                            onPlay = { i -> onPlaySongs(songs, i) },
                        )
                    }

                    else -> ShelfRow(
                        section = section,
                        onPlaySongs = onPlaySongs,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        onOpenPlaylist = onOpenPlaylist,
                        onSaveShelf = onSaveShelf,
                    )
                }
            }
        }

        if (loadingMore) {
            item { ShelfSkeleton(count = 2) }
        }

        // Paging trigger. Composing this row means the bottom of the list is on screen, which is
        // the cue to fetch the next batch; it doubles as the placeholder while that arrives.
        if (canLoadMore) {
            item(key = LOAD_MORE_KEY) {
                LaunchedEffect(pages) { onLoadMore() }
                ShelfSkeleton(count = 1)
            }
        }
    }
}

// ---- Header ------------------------------------------------------------------------------------

@Composable
private fun HomeHeader(offline: Boolean, onOpenStats: () -> Unit) {
    val context = LocalContext.current
    // Read once per visit: leaving Home and coming back picks up a new day or time of day.
    val now = remember { System.currentTimeMillis() }
    val date = remember(now) {
        DateUtils.formatDateTime(
            context,
            now,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
        ).uppercase(Locale.getDefault())
    }
    val greeting = remember(now) {
        when (dayPartAt(Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY))) {
            DayPart.MORNING -> R.string.home_greeting_morning
            DayPart.AFTERNOON -> R.string.home_greeting_afternoon
            DayPart.EVENING -> R.string.home_greeting_evening
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = SCREEN_PADDING, top = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.4.sp,
                color = Coral,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(if (offline) R.string.home_title_offline else greeting),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderButton(
            icon = Icons.Rounded.Insights,
            label = stringResource(R.string.stats_open),
            onClick = onOpenStats,
        )
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(OverlayTint.copy(alpha = 0.07f))
            .border(1.dp, GlassStrokeSoft, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun OfflineCard(empty: Boolean, onOpenCached: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = SCREEN_PADDING, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceLow)
            .border(1.dp, OverlayTint.copy(alpha = 0.06f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Coral.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
            }
            Text(
                text = stringResource(R.string.home_offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
        }
        if (empty) {
            Text(
                text = stringResource(R.string.home_offline_empty),
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .clip(CircleShape)
                .background(Coral)
                .clickable(role = Role.Button, onClick = onOpenCached)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.OfflineBolt, contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.cached_browse),
                color = OnAccent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---- Moods and daily mixes ------------------------------------------------------------------------

/** One-tap moods under the greeting; the tapped chip spins until its queue starts. */
@Composable
private fun MoodChips(moods: List<BrowseTile>, loadingId: String?, onClick: (BrowseTile) -> Unit) {
    // Some tile colours are deep (navy, plum); lifted on dark themes so the dot stays visible.
    val lift = if (LocalMusicSmPalette.current.isLight) 0f else 0.3f
    LazyRow(
        contentPadding = PaddingValues(horizontal = SCREEN_PADDING),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(moods, key = { it.id }) { tile ->
            val color = lerp(Color(tile.accentColor), Color.White, lift)
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OverlayTint.copy(alpha = 0.07f))
                    .border(1.dp, GlassStrokeSoft, CircleShape)
                    .clickable(enabled = loadingId == null, role = Role.Button) { onClick(tile) }
                    .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                    if (loadingId == tile.id) {
                        CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

/**
 * Shortcuts to what the listener already has: liked songs, downloads, and a shuffle of the likes.
 *
 * One frosted panel split by hairlines, with line icons in the accent, so it reads as part of the
 * glass chrome rather than as a row of app icons.
 */
@Composable
private fun QuickTilesRow(
    tiles: QuickTiles,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShuffleLiked: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = SCREEN_PADDING, vertical = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(OverlayTint.copy(alpha = 0.05f))
            .border(1.dp, GlassStrokeSoft, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickTile(
            icon = Icons.Outlined.FavoriteBorder,
            title = stringResource(R.string.library_liked_songs),
            detail = pluralStringResource(R.plurals.song_count, tiles.likedCount, tiles.likedCount),
            onClick = onOpenLiked,
            modifier = Modifier.weight(1f),
        )
        TileDivider()
        QuickTile(
            icon = Icons.Outlined.FileDownload,
            title = stringResource(R.string.downloads_title),
            detail = pluralStringResource(R.plurals.song_count, tiles.downloadCount, tiles.downloadCount),
            onClick = onOpenDownloads,
            modifier = Modifier.weight(1f),
        )
        TileDivider()
        QuickTile(
            icon = Icons.Rounded.Shuffle,
            title = stringResource(R.string.library_shuffle_all),
            detail = stringResource(
                if (tiles.likedCount > 0) R.string.home_tile_shuffle_subtitle else R.string.home_tile_shuffle_empty,
            ),
            onClick = onShuffleLiked,
            // Nothing liked yet means nothing to shuffle; the tile stays, quieter, to say so.
            modifier = Modifier
                .weight(1f)
                .alpha(if (tiles.likedCount > 0) 1f else 0.5f),
        )
    }
}

@Composable
private fun TileDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(OverlayTint.copy(alpha = 0.08f)),
    )
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Coral, modifier = Modifier.size(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = OnDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun MixesRow(mixes: List<DailyMix>, onPlay: (DailyMix) -> Unit) {
    val prefetch = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = ROW_PREFETCH_COUNT) }
    val rowState = rememberLazyListState(prefetchStrategy = prefetch)
    ShelfHeader(title = stringResource(R.string.home_mixes_title), onSave = null)
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = SCREEN_PADDING - CARD_INSET),
    ) {
        items(mixes, key = { it.number }) { mix ->
            MixCard(mix = mix, onClick = { onPlay(mix) })
        }
    }
}

/** A daily mix: a collage of its covers washed in the mix's colour, and who's in it. */
@Composable
private fun MixCard(mix: DailyMix, onClick: () -> Unit) {
    val tint = MixTints[(mix.number - 1).mod(MixTints.size)]
    val covers = mix.covers
    val tilePx = rememberArtworkPx(80.dp)
    val artists = when (mix.artists.size) {
        0 -> ""
        1 -> stringResource(R.string.home_mix_artists_one, mix.artists[0])
        else -> stringResource(R.string.home_mix_artists_two, mix.artists[0], mix.artists[1])
    }
    Column(
        modifier = Modifier
            .width(160.dp)
            .pressable(shape = RoundedCornerShape(20.dp), onClick = onClick)
            .padding(CARD_INSET),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(tint),
        ) {
            if (covers.size >= 4) {
                Column(modifier = Modifier.fillMaxSize()) {
                    covers.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.weight(1f)) {
                            pair.forEach { url ->
                                ArtworkImage(
                                    url = url,
                                    shape = RectangleShape,
                                    targetSizePx = tilePx,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            } else {
                ArtworkImage(
                    url = covers.firstOrNull(),
                    shape = RectangleShape,
                    targetSizePx = ArtworkSize.TILE,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to tint.copy(alpha = 0.95f))),
            )
            Text(
                text = stringResource(R.string.home_mix_title, mix.number),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }
        Text(
            text = artists,
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ---- Hero --------------------------------------------------------------------------------------

@Composable
private fun HeroCarousel(
    entries: List<HeroEntry>,
    onShuffle: () -> Unit,
    onArtwork: (String?) -> Unit,
) {
    val pagerState = rememberPagerState { entries.size }
    val currentOnArtwork by rememberUpdatedState(onArtwork)
    val artwork by rememberUpdatedState(entries.map { it.artworkUrl })
    LaunchedEffect(pagerState) {
        snapshotFlow { artwork.getOrNull(pagerState.settledPage) }
            .collect { currentOnArtwork(it) }
    }

    Column(modifier = Modifier.padding(top = 4.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = SCREEN_PADDING),
            pageSize = HeroPageSize,
            pageSpacing = 12.dp,
        ) { page ->
            val entry = entries[page]
            HeroCard(
                entry = entry,
                onShuffle = if (entry.playable) onShuffle else null,
                modifier = Modifier.graphicsLayer {
                    // Pages ease back as they slide away from the centre.
                    val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceAtMost(1f)
                    val scale = 1f - 0.08f * distance
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.4f * distance
                },
            )
        }
        if (entries.size > 1) {
            PagerDots(
                count = entries.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 14.dp),
            )
        }
    }
}

/** What a hero page shows, whatever kind of item it features. */
private class HeroEntry(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    /** Plays a track; otherwise the page opens an album or playlist. */
    val playable: Boolean,
    val onOpen: () -> Unit,
)

private fun HomeItem.toHeroEntry(
    onPlay: (Song) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
): HeroEntry? = when (this) {
    is HomeItem.SongItem -> HeroEntry(song.title, song.artist, song.artworkUrl, playable = true) { onPlay(song) }
    is HomeItem.AlbumItem -> HeroEntry(album.title, album.artist, album.artworkUrl, playable = false) { onOpenAlbum(album.id) }
    is HomeItem.PlaylistItem -> HeroEntry(playlist.name, "", playlist.artworkUrl, playable = false) { onOpenPlaylist(playlist.id) }
    is HomeItem.ArtistItem -> null
}

@Composable
private fun HeroCard(
    entry: HeroEntry,
    onShuffle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tint = rememberDominantColorState(entry.artworkUrl, fallback = HeroFallbackTint)
    val eyebrow = stringResource(R.string.home_featured).uppercase(Locale.getDefault())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT)
            .pressable(shape = RoundedCornerShape(28.dp), onClick = entry.onOpen),
    ) {
        ArtworkImage(
            url = entry.artworkUrl,
            shape = RectangleShape,
            targetSizePx = ArtworkSize.HERO,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim tinted from the cover, so the title sits on a wash of the artwork's own colour.
        // Text over artwork stays white on every theme because this scrim always darkens it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val deep = lerp(tint.value, Color.Black, 0.55f)
                    val scrim = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.28f to Color.Transparent,
                        0.5f to deep.copy(alpha = 0.25f),
                        1f to deep.copy(alpha = 0.95f),
                    )
                    onDrawBehind { drawRect(scrim) }
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.32f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                color = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(role = Role.Button, onClick = entry.onOpen)
                        .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.playable) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = HeroInk,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(if (entry.playable) R.string.action_play else R.string.home_open),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = HeroInk,
                    )
                }
                if (onShuffle != null) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .clickable(role = Role.Button, onClick = onShuffle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.action_shuffle),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val active = Coral
    val idle = OverlayTint.copy(alpha = 0.22f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 18.dp else 6.dp, label = "dotWidth")
            val color by animateColorAsState(if (selected) active else idle, label = "dotColor")
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// ---- Shelves -----------------------------------------------------------------------------------

/**
 * One horizontal shelf of cards. A section is all one kind of card in practice, but the model
 * allows a mix, so each item is rendered by its own type rather than by a per-shelf mode.
 */
@Composable
private fun ShelfRow(
    section: HomeSection,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSaveShelf: (String, List<Song>) -> Unit,
) {
    if (section.items.isEmpty()) return

    // Playing a card has to enqueue the whole shelf, so indices are taken against the songs
    // alone — a mixed shelf would otherwise start playback at the wrong track.
    val songs = remember(section) { section.songs() }
    // When the page prefetches this shelf, build its first cards with it: a phone shows two and a
    // half, so the default of two would leave the third to be built in the frame it scrolls in.
    val prefetch = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = ROW_PREFETCH_COUNT) }
    val rowState = rememberLazyListState(prefetchStrategy = prefetch)

    ShelfHeader(
        title = section.title,
        // Only a shelf of tracks can become a playlist.
        onSave = if (songs.isNotEmpty()) ({ onSaveShelf(section.title, songs) }) else null,
    )
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = SCREEN_PADDING - CARD_INSET),
    ) {
        items(section.items, key = { it.key() }) { item ->
            when (item) {
                is HomeItem.SongItem -> MediaCard(
                    title = item.song.title,
                    subtitle = item.song.artist,
                    artworkUrl = item.song.artworkUrl,
                    onClick = {
                        val index = songs.indexOfFirst { it.id == item.song.id }
                        if (index >= 0) onPlaySongs(songs, index)
                    },
                )

                is HomeItem.AlbumItem -> MediaCard(
                    title = item.album.title,
                    subtitle = item.album.artist,
                    artworkUrl = item.album.artworkUrl,
                    onClick = { onOpenAlbum(item.album.id) },
                )

                is HomeItem.ArtistItem -> ArtistAvatar(
                    artist = item.artist,
                    onClick = { onOpenArtist(item.artist.id) },
                )

                is HomeItem.PlaylistItem -> MediaCard(
                    title = item.playlist.name,
                    subtitle = "",
                    artworkUrl = item.playlist.artworkUrl,
                    // Shelf cards carry no track list, so the playlist has to be opened rather
                    // than played — tapping one otherwise does nothing at all.
                    onClick = { onOpenPlaylist(item.playlist.id) },
                )
            }
        }
    }
}

/**
 * Tracks as compact rows, stacked [QUICK_PICK_ROWS] high and paged sideways — a denser shelf that
 * breaks up the run of card rows. [ranked] numbers them like a chart.
 *
 * @param entries the tracks shown, each with its index in the queue [onPlay] starts.
 */
@Composable
private fun SongGrid(
    entries: List<IndexedValue<Song>>,
    ranked: Boolean,
    onPlay: (Int) -> Unit,
) {
    val columns = remember(entries) { entries.chunked(QUICK_PICK_ROWS) }
    val pagerState = rememberPagerState { columns.size }
    val artworkPx = rememberArtworkPx(QUICK_PICK_ART)
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = SCREEN_PADDING - ROW_INSET),
        pageSize = QuickPickPageSize,
        pageSpacing = 4.dp,
        verticalAlignment = Alignment.Top,
    ) { column ->
        Column {
            columns[column].forEach { (index, song) ->
                QuickPickRow(
                    song = song,
                    rank = if (ranked) index + 1 else null,
                    artworkPx = artworkPx,
                    onClick = { onPlay(index) },
                )
            }
        }
    }
}

@Composable
private fun QuickPickRow(song: Song, rank: Int?, artworkPx: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(horizontal = ROW_INSET, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (rank <= 3) Coral else OnDarkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(28.dp)
                    .padding(end = 6.dp),
            )
        }
        ArtworkImage(
            url = song.artworkUrl,
            shape = RoundedCornerShape(10.dp),
            targetSizePx = artworkPx,
            modifier = Modifier.size(QUICK_PICK_ART),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
) {
    val artShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(160.dp)
            .pressable(shape = RoundedCornerShape(20.dp), onClick = onClick)
            .padding(CARD_INSET),
    ) {
        ArtworkImage(
            url = artworkUrl,
            shape = artShape,
            targetSizePx = ArtworkSize.TILE,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // A hairline keeps dark covers from dissolving into a dark page.
                .border(0.5.dp, OverlayTint.copy(alpha = 0.10f), artShape),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        // Always one subtitle line (even if blank) so every card is the same height and the
        // artwork stays aligned across a shelf.
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistAvatar(artist: Artist, onClick: () -> Unit) {
    val ringStart = Coral
    val ringEnd = Lavender
    val ring = remember(ringStart, ringEnd) { Brush.linearGradient(listOf(ringStart, ringEnd)) }
    Column(
        modifier = Modifier
            .width(128.dp)
            .pressable(shape = RoundedCornerShape(20.dp), onClick = onClick)
            .padding(CARD_INSET),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .border(2.dp, ring, CircleShape)
                .padding(5.dp),
        ) {
            ArtworkImage(
                url = artist.artworkUrl,
                shape = CircleShape,
                targetSizePx = rememberArtworkPx(100.dp),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ShelfHeader(title: String, onSave: (() -> Unit)?) {
    var saved by remember(title) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SCREEN_PADDING, end = 14.dp, top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSave != null) {
            SaveChip(
                saved = saved,
                onClick = {
                    onSave()
                    saved = true
                },
            )
        }
    }
}

@Composable
private fun SaveChip(saved: Boolean, onClick: () -> Unit) {
    val background = if (saved) Coral.copy(alpha = 0.16f) else OverlayTint.copy(alpha = 0.07f)
    val content = if (saved) Coral else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(
                enabled = !saved,
                onClickLabel = stringResource(R.string.home_save_as_playlist),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (saved) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(if (saved) R.string.home_saved_shelf else R.string.home_save_shelf),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

// ---- Placeholders ------------------------------------------------------------------------------

@Composable
private fun HomeSkeleton() {
    val progress = rememberShimmerProgress()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = SCREEN_PADDING, top = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                SkeletonBlock(progress = progress, modifier = Modifier.width(130.dp).height(12.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(progress = progress, modifier = Modifier.width(220.dp).height(32.dp))
            }
        }
        item {
            Row(
                modifier = Modifier.padding(start = SCREEN_PADDING, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(64.dp, 88.dp, 72.dp, 96.dp).forEach { width ->
                    SkeletonBlock(progress = progress, shape = CircleShape, modifier = Modifier.width(width).height(36.dp))
                }
            }
        }
        item {
            SkeletonBlock(
                progress = progress,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .padding(horizontal = SCREEN_PADDING, vertical = 4.dp)
                    .widthIn(max = HERO_MAX_WIDTH)
                    .fillMaxWidth()
                    .aspectRatio(HERO_ASPECT),
            )
        }
        item {
            Column(modifier = Modifier.padding(start = SCREEN_PADDING, end = SCREEN_PADDING, top = 32.dp)) {
                SkeletonBlock(progress = progress, modifier = Modifier.width(150.dp).height(22.dp))
                Spacer(Modifier.height(14.dp))
                repeat(3) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBlock(
                            progress = progress,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(52.dp),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            SkeletonBlock(progress = progress, modifier = Modifier.width(170.dp).height(14.dp))
                            Spacer(Modifier.height(6.dp))
                            SkeletonBlock(progress = progress, modifier = Modifier.width(110.dp).height(12.dp))
                        }
                    }
                }
            }
        }
        item { ShelfSkeleton(count = 1) }
    }
}

/** Placeholder card shelves, used both for a cold start and while more shelves load. */
@Composable
private fun ShelfSkeleton(count: Int) {
    val progress = rememberShimmerProgress()
    Column {
        repeat(count) {
            SkeletonBlock(
                progress = progress,
                modifier = Modifier
                    .padding(start = SCREEN_PADDING, top = 28.dp, bottom = 14.dp)
                    .width(150.dp)
                    .height(22.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = SCREEN_PADDING - CARD_INSET),
                userScrollEnabled = false,
            ) {
                items(4) {
                    Column(modifier = Modifier.width(160.dp).padding(CARD_INSET)) {
                        SkeletonBlock(
                            progress = progress,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Spacer(Modifier.height(10.dp))
                        SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth().height(14.dp))
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(progress = progress, modifier = Modifier.fillMaxWidth(0.6f).height(12.dp))
                    }
                }
            }
        }
    }
}

// ---- Helpers -----------------------------------------------------------------------------------

/** Clickable with a slight shrink and highlight while held, so taps feel physical. */
private fun Modifier.pressable(shape: Shape, onClick: () -> Unit): Modifier = this
    .clip(shape)
    // No interaction source of our own: `clickable` then builds the feedback node on first touch,
    // so the many cards that are only ever scrolled past never pay for it.
    .clickable(
        interactionSource = null,
        indication = PressFeedback,
        role = Role.Button,
        onClick = onClick,
    )

/**
 * Press feedback as a modifier node rather than per-card animation state: no coroutine, animator
 * or recomposition per card, and the shrink is applied at draw time.
 */
private object PressFeedback : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressFeedbackNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class PressFeedbackNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val pressed = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val target = when (interaction) {
                    is PressInteraction.Press -> 1f
                    is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                    else -> return@collect
                }
                launch { pressed.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val amount = pressed.value
        if (amount == 0f) {
            drawContent()
            return
        }
        scale(1f - (1f - PRESSED_SCALE) * amount) { this@draw.drawContent() }
        drawRect(currentValueOf(LocalMusicSmPalette).overlayTint.copy(alpha = 0.06f * amount))
    }
}

/** Pages as wide as the space allows less [peek], capped at [max] so wide screens show several. */
private class CappedPageSize(private val peek: Dp, private val max: Dp) : PageSize {
    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
        (availableSpace - peek.roundToPx()).coerceAtMost(max.roundToPx()).coerceAtLeast(1)
}

private fun HomeSection.songs(): List<Song> = items.mapNotNull { (it as? HomeItem.SongItem)?.song }

/** Stable, type-qualified list key — a song and an album can share an id across providers. */
private fun HomeItem.key(): String = when (this) {
    is HomeItem.SongItem -> "song:${song.id}"
    is HomeItem.AlbumItem -> "album:${album.id}"
    is HomeItem.ArtistItem -> "artist:${artist.id}"
    is HomeItem.PlaylistItem -> "playlist:${playlist.id}"
}

/** Part of the day the Home greeting is pitched at. */
internal enum class DayPart { MORNING, AFTERNOON, EVENING }

/** Morning from 5:00, afternoon from 12:00, evening from 17:00 through the small hours. */
internal fun dayPartAt(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}

private const val HERO_COUNT = 5
private const val HERO_ASPECT = 1.2f
private val HERO_MAX_WIDTH = 480.dp
private const val QUICK_PICK_ROWS = 4
private val QUICK_PICK_ART = 52.dp
private const val ROW_PREFETCH_COUNT = 4
private const val PRESSED_SCALE = 0.96f
private val SCREEN_PADDING = 20.dp
private val CARD_INSET = 6.dp
private val ROW_INSET = 8.dp
private val HeroPageSize = CappedPageSize(peek = 0.dp, max = HERO_MAX_WIDTH)
private val QuickPickPageSize = CappedPageSize(peek = 44.dp, max = 380.dp)
private val HeroFallbackTint = Color(0xFF15161B)
private val HeroInk = Color(0xFF111114)

/** Fixed, vivid washes for the mix cards, so each mix keeps its colour whatever the theme. */
private val MixTints = listOf(
    Color(0xFFE8115B),
    Color(0xFF7358FF),
    Color(0xFF1E8C6E),
    Color(0xFFC2560A),
)

private const val MOODS_KEY = "home:moods"
private const val TILES_KEY = "home:tiles"
private const val MIXES_KEY = "home:mixes"

private const val HEADER_KEY = "home:header"
private const val OFFLINE_KEY = "home:offline"
private const val HERO_KEY = "home:hero"
private const val LOAD_MORE_KEY = "home:load-more"
