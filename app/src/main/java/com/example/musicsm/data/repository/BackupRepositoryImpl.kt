package com.example.musicsm.data.repository

import com.example.musicsm.data.local.dao.BackupDao
import com.example.musicsm.data.local.dao.PlaylistDao
import com.example.musicsm.data.local.entity.LikedArtistEntity
import com.example.musicsm.data.local.entity.LikedSongEntity
import com.example.musicsm.data.local.entity.PlayEventEntity
import com.example.musicsm.data.local.entity.PlayHistoryEntity
import com.example.musicsm.data.local.entity.PlaylistEntity
import com.example.musicsm.data.local.entity.PlaylistSongCrossRef
import com.example.musicsm.data.local.entity.SongEntity
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.repository.BackupRepository
import com.example.musicsm.domain.repository.BackupSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val backupDao: BackupDao,
    private val playlistDao: PlaylistDao,
    private val preferences: AppPreferences,
) : BackupRepository {

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", preferences.exportSettings())

        root.put("songs", backupDao.allSongs().toJsonArray { song ->
            JSONObject()
                .put("songId", song.songId)
                .put("title", song.title)
                .put("artist", song.artist)
                .put("album", song.album ?: JSONObject.NULL)
                .put("artworkUrl", song.artworkUrl ?: JSONObject.NULL)
                .put("durationMs", song.durationMs)
        })

        root.put("likedSongs", backupDao.allLikedSongs().toJsonArray {
            JSONObject().put("songId", it.songId).put("likedAt", it.likedAt)
        })

        root.put("likedArtists", backupDao.allLikedArtists().toJsonArray {
            JSONObject()
                .put("artistId", it.artistId)
                .put("name", it.name)
                .put("artworkUrl", it.artworkUrl ?: JSONObject.NULL)
                .put("likedAt", it.likedAt)
        })

        root.put("playHistory", backupDao.allPlayHistory().toJsonArray {
            JSONObject().put("songId", it.songId).put("playedAt", it.playedAt)
        })

        root.put("playEvents", backupDao.allPlayEvents().toJsonArray {
            JSONObject().put("songId", it.songId).put("playedAt", it.playedAt)
        })

        // Playlists carry their ordered song ids inline; the auto-generated playlistId is not
        // exported because it is remapped on import.
        val songsByPlaylist = backupDao.allPlaylistSongs().groupBy { it.playlistId }
        root.put("playlists", backupDao.allPlaylists().toJsonArray { playlist ->
            val ids = songsByPlaylist[playlist.playlistId].orEmpty()
                .sortedBy { it.position }
                .map { it.songId }
            JSONObject()
                .put("name", playlist.name)
                .put("createdAt", playlist.createdAt)
                .put("artworkUrl", playlist.artworkUrl ?: JSONObject.NULL)
                .put("songs", JSONArray(ids))
        })

        // Downloads are deliberately omitted: their file paths are device-specific and the audio
        // does not travel in the backup, so restoring them would show tracks as "downloaded" when
        // the files don't exist. They re-download on demand instead.
        root.toString()
    }

    override suspend fun import(json: String): BackupSummary = withContext(Dispatchers.IO) {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: throw IllegalArgumentException("Not a valid backup file")
        require(root.optString("format") == FORMAT) { "Not a Hymn backup file" }

        // 1) Songs first — they are the foreign-key parent of everything else. IGNORE preserves any
        //    metadata already on disk.
        val songs = root.optJSONArray("songs").mapObjects { obj ->
            val id = obj.optString("songId").ifEmpty { return@mapObjects null }
            SongEntity(
                songId = id,
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                album = obj.optStringOrNull("album"),
                artworkUrl = obj.optStringOrNull("artworkUrl"),
                durationMs = obj.optLong("durationMs"),
            )
        }
        backupDao.insertSongs(songs)

        // Only rows whose song survived (present in the DB now) can satisfy the foreign key.
        val knownSongIds = backupDao.allSongs().mapTo(HashSet()) { it.songId }

        // 2) Settings.
        root.optJSONObject("settings")?.let { preferences.importSettings(it) }

        // 3) Liked songs / artists / history — IGNORE keeps existing timestamps.
        val likedSongs = root.optJSONArray("likedSongs").mapObjects { obj ->
            val id = obj.optString("songId").ifEmpty { return@mapObjects null }
            if (id !in knownSongIds) return@mapObjects null
            LikedSongEntity(songId = id, likedAt = obj.optLong("likedAt"))
        }
        backupDao.insertLikedSongs(likedSongs)

        val likedArtists = root.optJSONArray("likedArtists").mapObjects { obj ->
            val id = obj.optString("artistId").ifEmpty { return@mapObjects null }
            LikedArtistEntity(
                artistId = id,
                name = obj.optString("name"),
                artworkUrl = obj.optStringOrNull("artworkUrl"),
                likedAt = obj.optLong("likedAt"),
            )
        }
        backupDao.insertLikedArtists(likedArtists)

        val history = root.optJSONArray("playHistory").mapObjects { obj ->
            val id = obj.optString("songId").ifEmpty { return@mapObjects null }
            if (id !in knownSongIds) return@mapObjects null
            PlayHistoryEntity(songId = id, playedAt = obj.optLong("playedAt"))
        }
        backupDao.insertPlayHistory(history)

        // 4) Play events — dedup on (songId, playedAt) so restoring the same backup twice doesn't
        //    inflate listening stats. New rows get fresh auto-generated ids.
        val existingEvents = backupDao.allPlayEvents().mapTo(HashSet()) { it.songId to it.playedAt }
        val newEvents = root.optJSONArray("playEvents").mapObjects { obj ->
            val id = obj.optString("songId").ifEmpty { return@mapObjects null }
            if (id !in knownSongIds) return@mapObjects null
            val at = obj.optLong("playedAt")
            if ((id to at) in existingEvents) return@mapObjects null
            existingEvents.add(id to at)
            PlayEventEntity(songId = id, playedAt = at)
        }
        backupDao.insertPlayEvents(newEvents)

        // 5) Playlists — merge by (case-insensitive) name: append into an existing playlist of the
        //    same name, otherwise create a fresh one with a new id. Songs already in the target are
        //    skipped, so a re-restore is a no-op.
        val byName = backupDao.allPlaylists().associateByTo(HashMap()) { it.name.lowercase() }
        var importedPlaylists = 0
        val playlistsArray = root.optJSONArray("playlists")
        if (playlistsArray != null) {
            for (i in 0 until playlistsArray.length()) {
                val obj = playlistsArray.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue
                val songIds = obj.optJSONArray("songs").toStringList().filter { it in knownSongIds }

                val existing = byName[name.lowercase()]
                val targetId = if (existing != null) {
                    existing.playlistId
                } else {
                    val created = PlaylistEntity(
                        name = name,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        artworkUrl = obj.optStringOrNull("artworkUrl"),
                    )
                    val newId = playlistDao.insertPlaylist(created)
                    byName[name.lowercase()] = created.copy(playlistId = newId)
                    importedPlaylists++
                    newId
                }

                val already = playlistDao.crossRefs(targetId).mapTo(HashSet()) { it.songId }
                var position = playlistDao.maxPosition(targetId)
                songIds.forEach { sid ->
                    if (!already.add(sid)) return@forEach
                    position++
                    playlistDao.addCrossRef(PlaylistSongCrossRef(targetId, sid, position))
                }
            }
        }

        BackupSummary(
            songs = songs.size,
            likedSongs = likedSongs.size,
            playlists = importedPlaylists,
            playEvents = newEvents.size,
        )
    }

    private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(transform(it)) }
        return array
    }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        val result = ArrayList<T>(length())
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            transform(obj)?.let { result.add(it) }
        }
        return result
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private companion object {
        const val FORMAT = "musicsm-backup"
        const val VERSION = 1
    }
}
