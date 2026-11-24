package com.example.musicsm.data.cache

import com.example.musicsm.domain.model.AccountLibrary
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the app already knows about an album, artist or playlist before its page has loaded. */
data class CatalogPreview(
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
)

/**
 * The title, byline and cover of every album, artist and playlist the catalog has returned, by id,
 * so a detail page can show its header the moment it opens rather than a bare spinner.
 *
 * Filled by the music repository as responses pass through it; memory only, least recently used
 * entries dropped past [MAX_ENTRIES].
 */
@Singleton
class CatalogPreviewCache @Inject constructor() {

    private val entries = object : LinkedHashMap<String, CatalogPreview>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CatalogPreview>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(id: String): CatalogPreview? = entries[id.trim()]

    /** An artist page opened by name (a song's "Go to artist") rather than by channel id. */
    @Synchronized
    fun artistNamed(name: String): CatalogPreview? = entries[nameKey(name)]

    fun record(feed: HomeFeed) {
        feed.sections.forEach { section ->
            section.items.forEach { item ->
                when (item) {
                    is HomeItem.AlbumItem -> record(item.album)
                    is HomeItem.ArtistItem -> record(item.artist)
                    is HomeItem.PlaylistItem -> record(item.playlist)
                    is HomeItem.SongItem -> Unit
                }
            }
        }
    }

    fun record(results: SearchResults) {
        results.albums.forEach(::record)
        results.artists.forEach(::record)
        results.playlists.forEach(::record)
    }

    fun record(library: AccountLibrary) {
        library.playlists.forEach(::record)
        library.artists.forEach(::record)
    }

    fun record(album: Album) = put(album.id, CatalogPreview(album.title, album.artist, album.artworkUrl))

    fun record(playlist: Playlist) = put(playlist.id, CatalogPreview(playlist.name, "", playlist.artworkUrl))

    fun record(artist: Artist) {
        val preview = CatalogPreview(artist.name, "", artist.artworkUrl)
        put(artist.id, preview)
        if (artist.name.isNotBlank()) put(nameKey(artist.name), preview)
        // Cards on an artist page don't name the artist, so the byline falls back to the title.
        artist.albums.forEach { album ->
            record(if (album.artist.isBlank() || album.artist == album.title) album.copy(artist = artist.name) else album)
        }
    }

    @Synchronized
    private fun put(id: String, preview: CatalogPreview) {
        val key = id.trim()
        if (key.isEmpty() || (preview.title.isBlank() && preview.artworkUrl.isNullOrBlank())) return
        val known = entries[key]
        // A thinner sighting (a name with no cover, say) never erases what a richer one taught us.
        entries[key] = if (known == null) {
            preview
        } else {
            CatalogPreview(
                title = preview.title.ifBlank { known.title },
                subtitle = preview.subtitle.ifBlank { known.subtitle },
                artworkUrl = preview.artworkUrl?.takeIf { it.isNotBlank() } ?: known.artworkUrl,
            )
        }
    }

    private fun nameKey(name: String) = "name:" + name.trim().lowercase(Locale.ROOT)

    companion object {
        const val MAX_ENTRIES = 600
    }
}
