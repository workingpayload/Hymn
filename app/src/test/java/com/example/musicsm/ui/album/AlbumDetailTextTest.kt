package com.example.musicsm.ui.album

import com.example.musicsm.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumDetailTextTest {

    @Test
    fun `hides internal album ids from visible text`() {
        assertNull("MPREb_fixture01".visibleAlbumText())
        assertNull("https://music.youtube.com/playlist?list=OLAK5uy_fixture".visibleAlbumText())
        assertEquals("Night Drive", " Night Drive ".visibleAlbumText())
    }

    @Test
    fun `album artist falls back without exposing ids`() {
        val songs = listOf(Song(id = "vid00000001", title = "Midnight Signal", artist = "Neon Fields"))

        assertEquals("Neon Fields", resolvedAlbumDetailArtist("UCfixtureArtist", songs, "Night Drive"))
        assertEquals("Night Drive", resolvedAlbumDetailArtist("MPREb_fixture01", emptyList(), "Night Drive"))
    }

    @Test
    fun `only real playlists on the album route count as playlists`() {
        assertEquals(false, isRemotePlaylistId("MPREb_fixture01"))
        assertEquals(false, isRemotePlaylistId("OLAK5uy_fixture000"))
        assertEquals(false, isRemotePlaylistId("https://music.youtube.com/playlist?list=OLAK5uy_fixture"))
        assertEquals(true, isRemotePlaylistId("VLPLfixturePlaylist"))
        assertEquals(true, isRemotePlaylistId("RDCLAK5uy_fixture"))
        assertEquals(false, isRemotePlaylistId(""))
    }
}
