package com.example.musicsm.domain.recommend

import com.example.musicsm.domain.match.ArtistMatching
import com.example.musicsm.domain.model.Song

/**
 * A personal mix built out from one track the listener has on rotation.
 *
 * @param artists the acts heard most in it, for the card's "A, B and more" line.
 */
data class DailyMix(
    val number: Int,
    val songs: List<Song>,
    val artists: List<String>,
) {
    /** Up to four distinct covers for the card's collage. */
    val covers: List<String> get() = songs.mapNotNull { it.artworkUrl }.distinct().take(4)
}

/**
 * Daily mixes: a few queues, each grown from a different favourite of the listener's, that change
 * from one day to the next and stay put within a day.
 */
object DailyMixes {

    /** How many of the listener's top artists the day's mixes rotate through. */
    const val ANCHOR_POOL = 8

    /** A mix shorter than this isn't worth a card. */
    const val MIN_SONGS = 8

    /** One act may not take over a mix built around them. */
    const val MAX_PER_ARTIST = 4

    /**
     * The tracks today's mixes start from: the listener's most-played, one per artist so every mix
     * has its own centre, with the day picking which of the top [ANCHOR_POOL] get a mix.
     */
    fun anchors(profile: TasteProfile, count: Int, epochDay: Long = DailyRotation.today()): List<Song> {
        val onePerArtist = (profile.heavyRotation + profile.seeds)
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .distinctBy { ArtistMatching.normalize(it.artist).ifEmpty { it.id } }
            .take(ANCHOR_POOL)
        return DailyRotation.pick(onePerArtist, count, epochDay)
    }

    /** Mix [number]: [seed] first, then what plays well after it, capped per artist. */
    fun assemble(number: Int, seed: Song, related: List<Song>, limit: Int): DailyMix? {
        val songs = TasteProfiles.capPerArtist(
            (listOf(seed) + related).filter { it.id.isNotBlank() }.distinctBy { it.id },
            MAX_PER_ARTIST,
        ).take(limit)
        if (songs.size < MIN_SONGS) return null
        // Most-heard first; ties keep the order they first appear in, which puts the seed's act first.
        // Only commas split a credit here: names such as "AC/DC" or "Simon & Garfunkel" stay whole.
        val artists = songs
            .mapNotNull { song ->
                ArtistMatching.displayName(song.artist).split(',').first().trim().takeIf { it.isNotEmpty() }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(3)
        return DailyMix(number, songs, artists)
    }
}
