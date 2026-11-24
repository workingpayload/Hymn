package com.example.musicsm.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.R
import com.example.musicsm.data.auth.YouTubePersonalization
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.recommend.DailyMix
import com.example.musicsm.domain.recommend.DailyMixes
import com.example.musicsm.domain.recommend.DailyRotation
import com.example.musicsm.domain.recommend.NewReleases
import com.example.musicsm.domain.recommend.ShelfRanker
import com.example.musicsm.domain.recommend.TasteProfile
import com.example.musicsm.domain.recommend.TasteProfiles
import com.example.musicsm.domain.repository.CachedSongsRepository
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * @param loadingMore the locally-derived shelves are on screen and the network ones are still
     *   in flight, so the page is usable but not yet complete.
     * @param canLoadMore more shelves can still be appended by scrolling to the bottom.
     */
    data class Content(
        val feed: HomeFeed,
        val offline: Boolean = false,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        /** Today's personal mixes, shown as their own row under the hero; empty hides it. */
        val mixes: List<DailyMix> = emptyList(),
        /**
         * Scroll-paging attempts made so far.
         *
         * The list's paging trigger keys off this rather than the section count, so a page that
         * happens to add nothing (every shelf a title we already show) still arms the next attempt
         * instead of stalling the feed permanently.
         */
        val pages: Int = 0,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

/** What the quick tiles count: liked songs (also what "Shuffle all" plays) and downloads. */
data class QuickTiles(
    val likedCount: Int = 0,
    val downloadCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val statsRepository: StatsRepository,
    private val cachedSongsRepository: CachedSongsRepository,
    personalization: YouTubePersonalization,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Mood chips under the greeting; each plays its mood in one tap. */
    val moods: List<BrowseTile> = repository.browseTiles()

    /** The mood chip whose queue is being fetched, by id. */
    private val _moodLoading = MutableStateFlow<String?>(null)
    val moodLoading = _moodLoading.asStateFlow()

    /** The latest taste profile, so a mood can lead with the listener's favourite acts. */
    private var profile = TasteProfile()

    /** The day's mixes, by the anchor tracks they were grown from. */
    private var mixCache: Pair<List<String>, List<DailyMix>>? = null

    /** Followed artists' pages fetched for this build, by id, reused when paging. */
    private val artistPageCache = HashMap<String, Artist>()

    /** Counts for the quick tiles under the moods; live, since likes and downloads change. */
    val quickTiles: StateFlow<QuickTiles> = combine(
        libraryRepository.likedSongs(),
        downloadRepository.downloads(),
    ) { liked, downloads -> QuickTiles(likedCount = liked.size, downloadCount = downloads.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickTiles())

    /** Shuffle-play everything the listener has liked. */
    fun shuffleLiked(onReady: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val liked = runCatching { libraryRepository.likedSongs().first() }.getOrDefault(emptyList())
            if (liked.isNotEmpty()) onReady(liked)
        }
    }

    /** Cursor into the provider's feed, and the artists not yet given a shelf of their own. */
    private var continuation: String? = null
    private var pendingArtists: List<Artist> = emptyList()
    private var paging = false

    init {
        load()
        // Turning YouTube personalisation on or off (or signing in or out) changes what Home is
        // built from, so rebuild it rather than leave the old feed up.
        personalization.enabled
            .drop(1)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            build(showLocalFirst = true)
        }
    }

    /** Pull-to-refresh: rebuild the feed while keeping current content on screen. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            // The page is already populated, so publishing a local-only feed here would visibly
            // tear the shelves down and rebuild them. Swap in the finished feed instead.
            build(showLocalFirst = false)
            _refreshing.value = false
        }
    }

    /**
     * Append the next batch of shelves, called as the bottom of the page comes into view.
     *
     * Two sources feed this, in order. The provider's own feed is paged first, since it is
     * effectively endless; once that is exhausted the remaining followed artists each get a
     * shelf, which keeps the page growing with material the listener actually cares about rather
     * than stopping dead.
     */
    fun loadMore() {
        val current = _state.value as? HomeUiState.Content ?: return
        if (paging || !current.canLoadMore) return
        paging = true
        viewModelScope.launch {
            try {
                val existing = current.feed.sections
                val shown = existing.songIds().toMutableSet()
                val next = nextShelves(shown)
                val merged = merge(existing, next)
                val pages = current.pages + 1
                _state.value = current.copy(
                    feed = HomeFeed(merged, continuation),
                    // Purely cursor-driven: a page that adds nothing is not the end of the road,
                    // but a feed with no cursor left and no artists queued definitely is.
                    canLoadMore = pages < MAX_PAGES &&
                        (continuation != null || pendingArtists.isNotEmpty()),
                    pages = pages,
                )
            } finally {
                paging = false
            }
        }
    }

    /** One more batch of shelves, from whichever source still has something to give. */
    private suspend fun nextShelves(shown: MutableSet<String>): List<HomeSection> {
        continuation?.let { token ->
            val feed = runCatching { repository.moreHomeShelves(token) }.getOrNull()
            continuation = feed?.continuation
            val sections = feed?.sections.orEmpty()
            if (sections.isNotEmpty()) return sections
        }
        return nextArtistShelves(shown)
    }

    /** A shelf per remaining followed artist, fetched a few at a time. */
    private suspend fun nextArtistShelves(shown: MutableSet<String>): List<HomeSection> =
        coroutineScope {
            val batch = pendingArtists.take(ARTIST_PAGE_SIZE)
            if (batch.isEmpty()) return@coroutineScope emptyList()
            pendingArtists = pendingArtists.drop(batch.size)

            batch
                .map { artist ->
                    async {
                        artistPageCache[artist.id] ?: runCatching { repository.artist(artist.id) }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
                .mapNotNull { page ->
                    val songs = page.topSongs.filter { it.id !in shown }.take(SHELF_SIZE)
                    if (songs.size < MIN_SONG_SHELF) return@mapNotNull null
                    shown += songs.map { it.id }
                    HomeSection(
                        context.getString(R.string.shelf_more_from_artist, page.name),
                        songs.map { HomeItem.SongItem(it) },
                    )
                }
        }

    /**
     * Builds the feed in two phases.
     *
     * Everything derived from the listener's own history is computed first and, on a cold start,
     * published straight away: it needs no network, so there is no reason to make someone watch a
     * skeleton while catalog requests they may not even scroll to complete. The network shelves
     * are then fetched concurrently and merged in.
     */
    private suspend fun build(showLocalFirst: Boolean) {
        continuation = null
        pendingArtists = emptyList()
        artistPageCache.clear()

        val local = runCatching { buildLocal() }.getOrNull()
        if (local == null) {
            _state.value = HomeUiState.Error(context.getString(R.string.home_error))
            return
        }

        if (showLocalFirst && local.sections.isNotEmpty()) {
            _state.value = HomeUiState.Content(
                feed = HomeFeed(local.sections),
                loadingMore = true,
            )
        }

        val shown = local.sections.songIds().toMutableSet()
        profile = local.profile
        val network = runCatching { buildNetwork(local.profile, local.followed, shown) }
            .getOrDefault(NetworkFeed())

        // Artists already given a shelf on the first page are not offered again while paging.
        pendingArtists = local.followed.drop(SEED_COUNT)

        _state.value = if (network.sections.isEmpty()) {
            // Nothing came back from the catalog. Pad the local shelves with anything else that
            // is playable without a connection rather than leaving a near-empty page.
            HomeUiState.Content(
                feed = HomeFeed(merge(local.sections, buildOfflineSections())),
                offline = true,
            )
        } else {
            HomeUiState.Content(
                feed = HomeFeed(merge(local.sections, network.sections), continuation),
                mixes = network.mixes,
                canLoadMore = continuation != null || pendingArtists.isNotEmpty(),
            )
        }
    }

    /** Shelves that come entirely from Room, so they render offline and instantly. */
    private suspend fun buildLocal(): LocalFeed {
        val followed = runCatching { libraryRepository.likedArtists().first() }
            .getOrDefault(emptyList())
        val profile = buildProfile(followed)
        val sections = mutableListOf<HomeSection>()
        val shown = mutableSetOf<String>()

        fun addSongs(title: String, songs: List<Song>) {
            val fresh = songs.filter { it.id !in shown }
            if (fresh.isEmpty()) return
            shown += fresh.map { it.id }
            sections += HomeSection(title, fresh.map { HomeItem.SongItem(it) })
        }

        addSongs(context.getString(R.string.shelf_listen_again), profile.heavyRotation)

        // Tracks that were once on repeat and then went quiet. Worth resurfacing precisely
        // because every other shelf is biased towards what is already in rotation.
        val forgotten = runCatching { statsRepository.forgottenFavorites(SHELF_SIZE) }
            .getOrDefault(emptyList())
            .map { it.song }
        addSongs(context.getString(R.string.shelf_forgotten_favorites), forgotten)

        // Follows ranked by what is actually played, not by whichever was saved first.
        val topFollowed = followed.sortedByDescending { profile.affinity(it.name) }
        if (topFollowed.size >= MIN_ARTIST_SHELF) {
            sections += HomeSection(
                context.getString(R.string.shelf_your_artists),
                topFollowed.take(ARTIST_SHELF_SIZE).map { HomeItem.ArtistItem(it) },
            )
        }

        return LocalFeed(sections, profile, topFollowed)
    }

    /**
     * Every catalog request the page needs, issued at once.
     *
     * These used to run one after another — including a per-artist fetch inside a `flatMap`, which
     * made the wait scale with the number of followed artists. They are independent, so the page
     * now takes about as long as its slowest single request instead of the sum of all of them.
     */
    private suspend fun buildNetwork(
        localProfile: TasteProfile,
        followed: List<Artist>,
        shown: MutableSet<String>,
    ): NetworkFeed = coroutineScope {
        // With YouTube personalisation on, the account's play history sharpens the profile; it is
        // fetched here rather than in the local phase so the instant offline shelves don't wait.
        val accountHistory = runCatching { repository.accountHistory() }.getOrDefault(emptyList())
        val profile = if (accountHistory.isEmpty()) {
            localProfile
        } else {
            runCatching { buildProfile(followed, accountHistory) }.getOrDefault(localProfile)
        }
        this@HomeViewModel.profile = profile
        val seedArtists = followed.take(SEED_COUNT)
        // More follows are looked up than get a song shelf, so new releases cover a wider circle.
        val releaseArtists = followed.take(RELEASE_ARTIST_COUNT)

        val mixesAsync = async { buildMixes(profile) }

        val discoverAsync = async {
            val seeds = DailyRotation.pick(profile.discoveryPool(), DISCOVER_SEEDS)
            if (seeds.isEmpty()) {
                emptyList()
            } else {
                runCatching { repository.recommendations(seeds, SHELF_SIZE * 2) }
                    .getOrDefault(emptyList())
            }
        }
        val recommendedAsync = async {
            if (profile.seeds.isEmpty()) {
                emptyList()
            } else {
                // Ask for more than fits: ranking and de-duplication both discard candidates.
                runCatching { repository.recommendations(profile.seeds, SHELF_SIZE * 2) }
                    .getOrDefault(emptyList())
            }
        }
        val relatedAsync = async {
            val seed = profile.topSeed
            if (seed == null) {
                emptyList()
            } else {
                runCatching { repository.relatedTo(seed.id) }.getOrDefault(emptyList())
            }
        }
        val artistPagesAsync = async {
            releaseArtists
                .map { artist -> async { artist.id to runCatching { repository.artist(artist.id) }.getOrNull() } }
                .awaitAll()
        }
        val trendingAsync = async { runCatching { repository.trending() }.getOrDefault(emptyList()) }
        val genericAsync = async {
            runCatching { repository.homeFeed() }.getOrDefault(HomeFeed())
        }

        val sections = mutableListOf<HomeSection>()

        fun addSongs(title: String, songs: List<Song>) {
            if (songs.isEmpty()) return
            shown += songs.map { it.id }
            sections += HomeSection(title, songs.map { HomeItem.SongItem(it) })
        }

        // The listener's own YouTube Music home (Quick picks, Listen again, their mixes) is the
        // strongest material there is, so it leads and is kept whole; the editorial feed shared by
        // everyone goes last instead (below).
        val generic = genericAsync.await()
        continuation = generic.continuation
        if (generic.personalized) {
            sections += generic.sections
            shown += generic.sections.songIds()
        }

        // Kept so scrolling to "More from <artist>" later doesn't fetch the same pages again.
        val fetchedPages = artistPagesAsync.await()
        fetchedPages.forEach { (id, page) -> if (page != null) artistPageCache[id] = page }
        val seedIds = seedArtists.mapTo(HashSet()) { it.id }
        val allPages = fetchedPages.mapNotNull { it.second }
        val artistPages = fetchedPages.filter { it.first in seedIds }.mapNotNull { it.second }

        // Timely, and only ever from people the listener chose to follow, so it sits high up.
        val pageAlbums = allPages.map { it.creditedAlbums() }
        val releases = NewReleases.pick(pageAlbums, LocalDate.now().year, SHELF_SIZE)
        if (releases.size >= MIN_RELEASE_SHELF) {
            sections += HomeSection(
                context.getString(R.string.shelf_new_from_followed),
                releases.map { HomeItem.AlbumItem(it) },
            )
        }
        val releaseIds = releases.mapTo(HashSet()) { it.id }

        addSongs(
            context.getString(R.string.shelf_daily_discover),
            ShelfRanker.rank(
                discoverAsync.await(),
                profile,
                SHELF_SIZE,
                exclude = shown,
                excludeKnown = true,
            ),
        )

        addSongs(
            context.getString(R.string.shelf_recommended),
            ShelfRanker.rank(
                recommendedAsync.await(),
                profile,
                SHELF_SIZE,
                exclude = shown,
                excludeKnown = true,
            ),
        )

        profile.topSeed?.let { seed ->
            val titleRes =
                if (profile.hasHistory) R.string.home_more_like else R.string.home_because_you_liked
            addSongs(
                context.getString(titleRes, seed.title),
                ShelfRanker.rank(
                    relatedAsync.await(),
                    profile,
                    SHELF_SIZE,
                    exclude = shown + seed.id,
                    excludeKnown = true,
                ),
            )
        }

        addSongs(
            context.getString(R.string.shelf_from_followed_artists),
            ShelfRanker.rank(
                artistPages.flatMap { it.topSongs.take(ARTIST_PICKS) },
                profile,
                SHELF_SIZE,
                exclude = shown,
                maxPerArtist = ARTIST_PICKS,
            ),
        )

        // The artist pages have already been fetched for the shelves above, so an album row costs
        // nothing extra. Anything already offered as a new release isn't offered twice.
        val albums = pageAlbums
            .flatten()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.id !in releaseIds }
            .distinctBy { it.id }
            .take(SHELF_SIZE)
        if (albums.size >= MIN_ALBUM_SHELF) {
            sections += HomeSection(
                context.getString(R.string.shelf_albums_from_artists),
                albums.map { HomeItem.AlbumItem(it) },
            )
        }

        addSongs(
            context.getString(R.string.shelf_trending),
            ShelfRanker.dedupe(trendingAsync.await(), SHELF_SIZE, shown),
        )

        // The provider's generic feed is the same for everybody, so it goes last — and once there
        // is real history to personalize from, it is trimmed so it can't dominate the page.
        if (!generic.personalized) {
            sections += if (profile.hasHistory) {
                generic.sections.take(MAX_GENERIC_SECTIONS)
            } else {
                generic.sections
            }
        }

        NetworkFeed(sections, mixesAsync.await())
    }

    /**
     * Today's daily mixes, each grown from a different favourite. One request per mix; the result
     * is kept while the day's anchors stay the same, so a refresh doesn't fetch them again.
     */
    private suspend fun buildMixes(profile: TasteProfile): List<DailyMix> = coroutineScope {
        val anchors = DailyMixes.anchors(profile, MIX_COUNT)
        if (anchors.size < MIN_MIXES) return@coroutineScope emptyList()
        val key = anchors.map { it.id }
        mixCache?.takeIf { it.first == key }?.let { return@coroutineScope it.second }

        val mixes = anchors
            .map { seed -> async { runCatching { repository.relatedTo(seed.id) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .mapIndexedNotNull { index, related ->
                DailyMixes.assemble(number = index + 1, seed = anchors[index], related = related, limit = MIX_SIZE)
            }
            .takeIf { it.size >= MIN_MIXES }
            .orEmpty()
        if (mixes.isNotEmpty()) mixCache = key to mixes
        mixes
    }

    /**
     * Play a mood straight from its chip: the catalog's tracks for it, the listener's favourite
     * acts first. [onReady] gets the queue; [onFailed] runs when nothing came back.
     */
    fun playMood(tile: BrowseTile, onReady: (List<Song>) -> Unit, onFailed: () -> Unit) {
        if (_moodLoading.value != null) return
        _moodLoading.value = tile.id
        viewModelScope.launch {
            try {
                val candidates = runCatching { repository.searchSongs(tile.query) }.getOrDefault(emptyList())
                // Searches for a mood turn up hour-long compilations; a mood queue is for songs.
                val songs = candidates.filterNot { it.durationMs > MOOD_MAX_DURATION_MS }
                val queue = ShelfRanker.rank(songs, profile, MOOD_SIZE, maxPerArtist = MOOD_MAX_PER_ARTIST)
                if (queue.isEmpty()) onFailed() else onReady(queue)
            } finally {
                _moodLoading.value = null
            }
        }
    }

    /** Downloads / recently played / liked — playable without a connection. */
    private suspend fun buildOfflineSections(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        val downloads = runCatching { downloadRepository.downloads().first() }
            .getOrDefault(emptyList())
        if (downloads.isNotEmpty()) sections += shelf(R.string.shelf_downloaded, downloads)
        val cached = runCatching { cachedSongsRepository.cachedSongs() }.getOrDefault(emptyList())
            .take(SHELF_SIZE * 2)
        if (cached.isNotEmpty()) sections += shelf(R.string.shelf_cached, cached)
        val recent = runCatching { libraryRepository.recentlyPlayed().first() }
            .getOrDefault(emptyList())
        if (recent.isNotEmpty()) sections += shelf(R.string.shelf_recently_played, recent)
        val liked = runCatching { libraryRepository.likedSongs().first() }
            .getOrDefault(emptyList())
        if (liked.isNotEmpty()) sections += shelf(R.string.shelf_liked_songs, liked)
        return sections
    }

    /** Fold history, likes and follows into one deterministic picture of the listener's taste. */
    private suspend fun buildProfile(
        followed: List<Artist>,
        accountHistory: List<Song> = emptyList(),
    ): TasteProfile = coroutineScope {
        val recentAsync = async { statsOrEmpty(StatsRange.LAST_4_WEEKS) }
        val lifetimeAsync = async { statsOrEmpty(StatsRange.ALL_TIME) }
        val likedAsync = async {
            runCatching { libraryRepository.likedSongs().first() }.getOrDefault(emptyList())
        }
        val recentlyPlayedAsync = async {
            runCatching { libraryRepository.recentlyPlayed().first() }.getOrDefault(emptyList())
        }
        TasteProfiles.build(
            recent = recentAsync.await(),
            lifetime = lifetimeAsync.await(),
            liked = likedAsync.await(),
            followedArtists = followed,
            // Plays from YouTube Music on other devices count too, after this device's own.
            recentlyPlayed = (recentlyPlayedAsync.await() + accountHistory).distinctBy { it.id },
            seedCount = SEED_COUNT,
            rotationSize = SHELF_SIZE,
        )
    }

    private suspend fun statsOrEmpty(range: StatsRange): ListeningStats =
        runCatching { statsRepository.stats(range).first() }.getOrDefault(ListeningStats())

    /** Save a home shelf as a new local playlist. */
    fun saveShelf(title: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val id = libraryRepository.createPlaylist(title)
            songs.forEach { libraryRepository.addToPlaylist(id, it) }
        }
    }

    private fun shelf(@StringRes titleRes: Int, songs: List<Song>) =
        HomeSection(context.getString(titleRes), songs.map { HomeItem.SongItem(it) })

    /** The locally-computed part of the page, plus what the network phase needs to continue. */
    private data class LocalFeed(
        val sections: List<HomeSection>,
        val profile: TasteProfile,
        val followed: List<Artist>,
    )

    /** What the catalog phase adds to the page. */
    private data class NetworkFeed(
        val sections: List<HomeSection> = emptyList(),
        val mixes: List<DailyMix> = emptyList(),
    )

    companion object {
        private const val SEED_COUNT = 4
        private const val SHELF_SIZE = 12

        /** Daily mixes per day, and the fewest worth a row; each one is a request. */
        private const val MIX_COUNT = 4
        private const val MIN_MIXES = 2
        private const val MIX_SIZE = 30

        /** A mood queue's length, and how much of it one act may take. */
        private const val MOOD_SIZE = 30
        private const val MOOD_MAX_PER_ARTIST = 2
        private const val MOOD_MAX_DURATION_MS = 15 * 60_000L

        /** Followed artists whose pages are fetched for new releases (the first [SEED_COUNT] also
         *  feed the song shelves), and the fewest releases worth a row. */
        private const val RELEASE_ARTIST_COUNT = 8
        private const val MIN_RELEASE_SHELF = 2

        /** Tracks that seed "Daily discover"; kept small because each one is a request. */
        private const val DISCOVER_SEEDS = 3

        /** Tracks pulled from each followed artist, and the per-artist cap on that shelf. */
        private const val ARTIST_PICKS = 4

        /** A row of one or two avatars looks like a mistake, so don't show it at all. */
        private const val MIN_ARTIST_SHELF = 3
        private const val ARTIST_SHELF_SIZE = 12

        /** Same reasoning for the album row. */
        private const val MIN_ALBUM_SHELF = 3

        /** How much of the provider's one-size-fits-all feed survives once we know the listener. */
        private const val MAX_GENERIC_SECTIONS = 4

        /** Followed artists given a shelf per scroll-triggered page. Each one is a request. */
        private const val ARTIST_PAGE_SIZE = 3

        /** Below this, a shelf is too thin to be worth a row of its own. */
        private const val MIN_SONG_SHELF = 4

        /** A backstop on scroll paging, in case a provider hands back cursors forever. */
        private const val MAX_PAGES = 12
    }
}

/** Every song id on these shelves, used to stop later shelves repeating earlier ones. */
private fun List<HomeSection>.songIds(): Set<String> =
    flatMap { section -> section.items.mapNotNull { (it as? HomeItem.SongItem)?.song?.id } }.toSet()

/**
 * The page's releases, credited to the artist. Cards on an artist page don't name the artist (the
 * page already does), so the source falls back to repeating the title as the byline.
 */
private fun Artist.creditedAlbums(): List<Album> = albums.map { album ->
    if (album.artist.isBlank() || album.artist == album.title) album.copy(artist = name) else album
}

/**
 * Concatenate shelves, dropping any later one that reuses an earlier title.
 *
 * The provider names its own rows, so its generic feed can collide with ours ("Trending now"
 * appearing twice). Titles key the Home list, and duplicate keys are a hard crash in Compose.
 */
private fun merge(first: List<HomeSection>, second: List<HomeSection>): List<HomeSection> {
    val seen = first.mapTo(mutableSetOf()) { it.title }
    return first + second.filter { it.items.isNotEmpty() && seen.add(it.title) }
}

/**
 * Tracks to seed discovery from, preferring things actually listened to over untouched likes.
 *
 * Heavy rotation is what the listener is into *now*, which is the better jumping-off point for
 * something new; seeds stand in for a listener with no real history yet.
 */
private fun TasteProfile.discoveryPool(): List<Song> = heavyRotation.ifEmpty { seeds }
