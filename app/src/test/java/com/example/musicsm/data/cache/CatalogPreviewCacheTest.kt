package com.example.musicsm.data.cache

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogPreviewCacheTest {

    private val cache = CatalogPreviewCache()

    @Test
    fun `cards on a home shelf are remembered by id`() {
        cache.record(
            HomeFeed(
                listOf(
                    HomeSection(
                        "New releases",
                        listOf(HomeItem.AlbumItem(Album("MPREb_1", "Jailer 2", "Anirudh", "https://art/1"))),
                    ),
                ),
            ),
        )

        assertEquals(CatalogPreview("Jailer 2", "Anirudh", "https://art/1"), cache.get("MPREb_1"))
        assertNull(cache.get("MPREb_2"))
    }

    @Test
    fun `a later sighting without a cover keeps the cover already known`() {
        cache.record(Album("MPREb_1", "Jailer 2", "Anirudh", "https://art/1"))
        cache.record(Album("MPREb_1", "Jailer 2 (Original Motion Picture Soundtrack)", "", null))

        assertEquals(
            CatalogPreview("Jailer 2 (Original Motion Picture Soundtrack)", "Anirudh", "https://art/1"),
            cache.get("MPREb_1"),
        )
    }

    @Test
    fun `an artist can be found by name, and their page's albums by id`() {
        cache.record(
            Artist(
                id = "UC123",
                name = "Lady Gaga",
                artworkUrl = "https://art/gaga",
                albums = listOf(Album("MPREb_9", "Mayhem", "Lady Gaga", "https://art/mayhem")),
            ),
        )

        assertEquals("https://art/gaga", cache.artistNamed(" lady gaga ")?.artworkUrl)
        assertEquals("https://art/mayhem", cache.get("MPREb_9")?.artworkUrl)
    }

    @Test
    fun `the least recently used previews make room past the cap`() {
        repeat(CatalogPreviewCache.MAX_ENTRIES + 1) { i -> cache.record(Album("id$i", "Album $i", "", "https://art/$i")) }

        assertNull(cache.get("id0"))
        assertEquals("Album 1", cache.get("id1")?.title)
    }
}
