package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.model.Album
import org.junit.Assert.assertEquals
import org.junit.Test

class NewReleasesTest {

    private fun album(id: String, year: String?, artist: String = "A") = Album(id = id, title = "Album $id", artist = artist, year = year)

    @Test
    fun `only this year's and last year's releases count, newest first`() {
        val picked = NewReleases.pick(
            listOf(listOf(album("old", "2023"), album("last", "2025"), album("now", "2026"), album("undated", null), album("soon", "2027"))),
            currentYear = 2026,
            limit = 10,
        )

        assertEquals(listOf("now", "last"), picked.map { it.id })
    }

    @Test
    fun `artists take turns within a year and duplicates appear once`() {
        val gaga = listOf(album("g1", "2026", "Gaga"), album("g2", "2026", "Gaga"), album("g3", "2026", "Gaga"))
        val arijit = listOf(album("a1", "2026", "Arijit"), album("g1", "2026", "Gaga"))
        val weeknd = listOf(album("w1", "2025", "Weeknd"))

        val picked = NewReleases.pick(listOf(gaga, arijit, weeknd), currentYear = 2026, limit = 10)

        assertEquals(listOf("g1", "a1", "g2", "g3", "w1"), picked.map { it.id })
    }
}
