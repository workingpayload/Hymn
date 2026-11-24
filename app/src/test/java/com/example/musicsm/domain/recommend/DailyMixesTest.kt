package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMixesTest {

    private fun song(id: String, artist: String) = Song(id = id, title = "Song $id", artist = artist, artworkUrl = "https://art/$id")

    private val profile = TasteProfile(
        heavyRotation = listOf(
            song("a1", "Arijit Singh"),
            song("a2", "Arijit Singh"),
            song("b1", "Atif Aslam"),
            song("c1", "Lady Gaga"),
            song("d1", "Taylor Swift"),
            song("e1", "The Weeknd"),
        ),
        seeds = listOf(song("f1", "Dua Lipa"), song("a1", "Arijit Singh")),
    )

    @Test
    fun `each day's mixes start from different artists and hold for the whole day`() {
        val today = DailyMixes.anchors(profile, count = 3, epochDay = 20_000)

        assertEquals(3, today.size)
        assertEquals(3, today.map { it.artist }.toSet().size)
        assertEquals(today, DailyMixes.anchors(profile, count = 3, epochDay = 20_000))
        assertNotEquals(today, DailyMixes.anchors(profile, count = 3, epochDay = 20_001))
    }

    @Test
    fun `a mix opens on its seed, has no repeats and caps any one artist`() {
        val seed = song("s", "Arijit Singh")
        val related = List(10) { song("x$it", "Arijit Singh") } + List(10) { song("y$it", "Atif Aslam, Shreya Ghoshal") } +
            song("z", "AC/DC") + seed

        val mix = DailyMixes.assemble(number = 1, seed = seed, related = related, limit = 30)!!

        assertEquals("s", mix.songs.first().id)
        assertEquals(mix.songs.size, mix.songs.map { it.id }.toSet().size)
        assertTrue(mix.songs.count { it.artist == "Arijit Singh" } <= DailyMixes.MAX_PER_ARTIST)
        assertEquals(listOf("Arijit Singh", "Atif Aslam", "AC/DC"), mix.artists)
        assertEquals(4, mix.covers.size)
    }

    @Test
    fun `too few tracks make no mix`() {
        val seed = song("s", "Arijit Singh")
        assertNull(DailyMixes.assemble(number = 1, seed = seed, related = listOf(song("x", "Atif Aslam")), limit = 30))
    }
}
