package com.example.musicsm.data.repository

import com.example.musicsm.data.cache.CatalogPreviewCache
import com.example.musicsm.domain.model.AccountLibrary
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.AlbumAudio
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.recommend.ShelfRanker
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.source.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val source: MusicSource,
    private val previews: CatalogPreviewCache,
) : MusicRepository {

    private val streamCache = ConcurrentHashMap<String, PlayableStream>()

    /** Found once per track: it doesn't change, and it costs two requests. Unknown isn't kept. */
    private val albumAudioCache = ConcurrentHashMap<String, AlbumAudio>()

    override suspend fun albumAudio(songId: String): AlbumAudio = withContext(Dispatchers.IO) {
        albumAudioCache[songId] ?: source.albumAudio(songId).also {
            if (it != AlbumAudio.Unknown) albumAudioCache[songId] = it
        }
    }

    // Everything that returns albums, artists or playlists is noted in [previews], so their pages
    // can open on the cover and title already seen rather than on a spinner.

    override suspend fun homeFeed(): HomeFeed =
        withContext(Dispatchers.IO) { source.homeFeed() }.also { previews.record(it) }

    override suspend fun moreHomeShelves(continuation: String): HomeFeed =
        withContext(Dispatchers.IO) { source.moreHomeShelves(continuation) }.also { previews.record(it) }

    override suspend fun search(query: String, includeVideos: Boolean): SearchResults =
        withContext(Dispatchers.IO) { source.search(query, includeVideos) }.also { previews.record(it) }

    override suspend fun searchSongs(query: String): List<Song> =
        withContext(Dispatchers.IO) { source.searchSongs(query) }

    override suspend fun album(id: String): Album =
        withContext(Dispatchers.IO) { source.album(id) }.also { previews.record(it) }

    override suspend fun artist(id: String): Artist =
        withContext(Dispatchers.IO) { source.artist(id) }.also { previews.record(it) }

    override suspend fun playlist(id: String): Playlist =
        withContext(Dispatchers.IO) { source.playlist(id) }.also { previews.record(it) }

    override suspend fun fullPlaylist(id: String, maxTracks: Int): Playlist =
        withContext(Dispatchers.IO) { source.fullPlaylist(id, maxTracks) }.also { previews.record(it) }

    override suspend fun relatedTo(songId: String): List<Song> =
        withContext(Dispatchers.IO) { source.relatedTo(songId) }

    override suspend fun song(songId: String): Song =
        withContext(Dispatchers.IO) { source.song(songId) }

    override suspend fun trending(): List<Song> =
        withContext(Dispatchers.IO) { source.trending(20) }

    override suspend fun recommendations(seeds: List<Song>, limit: Int): List<Song> =
        withContext(Dispatchers.IO) {
            if (seeds.isEmpty()) return@withContext emptyList()
            val seedIds = seeds.mapTo(HashSet()) { it.id }
            // Fan out to relatedTo for each seed in parallel, then merge round-robin so that each
            // seed contributes evenly and the same history always produces the same shelf.
            val perSeed = coroutineScope {
                seeds.map { seed ->
                    async { runCatching { source.relatedTo(seed.id) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }
            ShelfRanker.interleave(
                perSeed.map { list -> list.filter { it.id !in seedIds } },
                limit,
            )
        }

    /**
     * Grows a radio queue outward from [seed] in two hops.
     *
     * The first hop is what YouTube Music considers adjacent to the seed. That alone is a short,
     * very tight list, so the strongest few of those results are then expanded in turn and the
     * batches merged round-robin: the queue stays anchored to the seed near the front and widens
     * as it plays, which is what makes it last rather than loop.
     *
     * The hops are sequential because the second depends on the first, but each hop fans out in
     * parallel, so the whole thing costs about two requests' worth of waiting.
     */
    override suspend fun radio(seed: Song, limit: Int, exclude: Set<String>): List<Song> =
        withContext(Dispatchers.IO) {
            val blocked = exclude + seed.id
            // The signed-in listener's own radio for the seed leads when there is one; it is
            // already a long, personal queue, and the branches below widen it as usual.
            val first = runCatching { source.personalRadio(seed.id) }
                .getOrDefault(emptyList())
                .ifEmpty { runCatching { source.relatedTo(seed.id) }.getOrDefault(emptyList()) }
                .filter { it.id !in blocked }
                .distinctBy { it.id }
            if (first.isEmpty()) return@withContext emptyList()
            if (first.size >= limit) return@withContext first.take(limit)

            val branchSeeds = first.take(RADIO_BRANCHES)
            val branchIds = branchSeeds.mapTo(HashSet(blocked)) { it.id }
            val branches = coroutineScope {
                branchSeeds.map { branch ->
                    async {
                        runCatching { source.relatedTo(branch.id) }
                            .getOrDefault(emptyList())
                            .filter { it.id !in branchIds }
                    }
                }.awaitAll()
            }

            // The seed's own results lead; the wider material is merged in behind them.
            ShelfRanker.interleave(listOf(first) + branches, limit)
        }

    override suspend fun resolveStream(songId: String): PlayableStream = withContext(Dispatchers.IO) {
        val cached = streamCache[songId]
        if (cached != null && isStreamUsable(cached.expiresAtMs, System.currentTimeMillis(), REFRESH_MARGIN_MS)) {
            cached
        } else {
            source.resolveStream(songId).also { streamCache[songId] = it }
        }
    }

    override fun invalidateStream(songId: String) {
        streamCache.remove(songId)
    }

    override fun browseTiles(): List<BrowseTile> = BROWSE_TILES

    override suspend fun accountLibrary(): AccountLibrary =
        withContext(Dispatchers.IO) { source.accountLibrary() }.also { previews.record(it) }

    override suspend fun accountHistory(): List<Song> =
        withContext(Dispatchers.IO) { source.accountHistory() }

    companion object {
        private const val REFRESH_MARGIN_MS = 60_000L

        /**
         * How many early results get expanded when building a radio queue. Each one is a request,
         * so this trades a wider queue against how long the first track waits to start.
         */
        private const val RADIO_BRANCHES = 4

        private val BROWSE_TILES = listOf(
            BrowseTile("pop", "Pop", 0xFF1E3264, "pop hits"),
            BrowseTile("hiphop", "Hip-Hop", 0xFFBA5D07, "hip hop rap hits"),
            BrowseTile("rock", "Rock", 0xFFE13300, "rock anthems"),
            BrowseTile("lofi", "Lo-Fi", 0xFF8D67AB, "lofi hip hop beats"),
            BrowseTile("workout", "Workout", 0xFF777777, "workout gym music"),
            BrowseTile("chill", "Chill", 0xFF27856A, "chill relax music"),
            BrowseTile("focus", "Focus", 0xFF503750, "focus study instrumental"),
            BrowseTile("party", "Party", 0xFFDC148C, "party dance hits"),
            BrowseTile("jazz", "Jazz", 0xFF477D95, "smooth jazz"),
            BrowseTile("classical", "Classical", 0xFF7358FF, "classical music"),
            BrowseTile("indie", "Indie", 0xFF608108, "indie hits"),
            BrowseTile("throwback", "Throwback", 0xFF9CF0E1, "2000s throwback hits"),
        )
    }
}

/**
 * Whether a cached stream URL is still worth reusing. googlevideo URLs are time-limited, so a
 * [marginMs] safety window means playback never starts with a URL that is about to expire.
 *
 * Top-level and internal so the expiry rule can be unit-tested without a real extractor.
 */
internal fun isStreamUsable(expiresAtMs: Long, nowMs: Long, marginMs: Long): Boolean =
    expiresAtMs > nowMs + marginMs