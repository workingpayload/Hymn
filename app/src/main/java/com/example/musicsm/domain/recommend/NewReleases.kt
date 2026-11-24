package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.model.Album

/** Recent releases (albums and singles) from the artists the listener follows. */
object NewReleases {

    /** Releases dated this year or up to this many years before still count as new. */
    const val MAX_AGE_YEARS = 1

    /**
     * Newest first. Within a year the artists take turns, so one prolific act can't fill the row
     * before the others appear; each artist's own releases keep their page order.
     *
     * @param perArtist each followed artist's releases, in the order the artists rank.
     */
    fun pick(perArtist: List<List<Album>>, currentYear: Int, limit: Int): List<Album> {
        val seen = HashSet<String>()
        val candidates = perArtist.flatMapIndexed { artistIndex, albums ->
            val turnInYear = HashMap<Int, Int>()
            albums.mapNotNull { album ->
                val year = album.year?.trim()?.toIntOrNull() ?: return@mapNotNull null
                if (year > currentYear || year < currentYear - MAX_AGE_YEARS) return@mapNotNull null
                if (album.id.isBlank() || album.title.isBlank() || !seen.add(album.id)) return@mapNotNull null
                val turn = turnInYear.getOrDefault(year, 0)
                turnInYear[year] = turn + 1
                Candidate(album, year, turn, artistIndex)
            }
        }
        return candidates
            .sortedWith(
                compareByDescending<Candidate> { it.year }
                    .thenBy { it.turn }
                    .thenBy { it.artistIndex },
            )
            .map { it.album }
            .take(limit)
    }

    private data class Candidate(val album: Album, val year: Int, val turn: Int, val artistIndex: Int)
}
