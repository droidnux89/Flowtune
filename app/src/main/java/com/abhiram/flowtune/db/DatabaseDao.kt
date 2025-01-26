package com.abhiram.flowtune.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.abhiram.flowtune.db.daos.AlbumsDao
import com.abhiram.flowtune.db.daos.ArtistsDao
import com.abhiram.flowtune.db.daos.PlaylistsDao
import com.abhiram.flowtune.db.daos.QueueDao
import com.abhiram.flowtune.db.daos.SongsDao
import com.abhiram.flowtune.db.entities.AlbumArtistMap
import com.abhiram.flowtune.db.entities.AlbumEntity
import com.abhiram.flowtune.db.entities.ArtistEntity
import com.abhiram.flowtune.db.entities.Event
import com.abhiram.flowtune.db.entities.EventWithSong
import com.abhiram.flowtune.db.entities.FormatEntity
import com.abhiram.flowtune.db.entities.GenreEntity
import com.abhiram.flowtune.db.entities.LyricsEntity
import com.abhiram.flowtune.db.entities.QueueEntity
import com.abhiram.flowtune.db.entities.QueueSongMap
import com.abhiram.flowtune.db.entities.RelatedSongMap
import com.abhiram.flowtune.db.entities.SearchHistory
import com.abhiram.flowtune.db.entities.Song
import com.abhiram.flowtune.db.entities.SongAlbumMap
import com.abhiram.flowtune.db.entities.SongArtistMap
import com.abhiram.flowtune.db.entities.SongEntity
import com.abhiram.flowtune.db.entities.SongGenreMap
import com.abhiram.flowtune.extensions.toSQLiteQuery
import com.abhiram.flowtune.models.MediaMetadata
import com.abhiram.flowtune.models.MultiQueueObject
import com.abhiram.flowtune.models.toMediaMetadata
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.pages.AlbumPage
import kotlinx.coroutines.flow.Flow

@Dao
interface DatabaseDao : SongsDao, AlbumsDao, ArtistsDao, PlaylistsDao, QueueDao {

    @Transaction
    @Query("""
        SELECT song.*
        FROM (SELECT *, COUNT(1) AS referredCount
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN song ON song.id = map.relatedSongId
        WHERE songId IN (SELECT songId
                         FROM (SELECT songId
                               FROM event
                               ORDER BY ROWID DESC
                               LIMIT 5)
                         UNION
                         SELECT songId
                         FROM (SELECT songId
                               FROM event
                               WHERE timestamp > :now - 86400000 * 7
                               GROUP BY songId
                               ORDER BY SUM(playTime) DESC
                               LIMIT 5)
                         UNION
                         SELECT id
                         FROM (SELECT id
                               FROM song
                               ORDER BY totalPlayTime DESC
                               LIMIT 10))
        ORDER BY referredCount DESC
        LIMIT 100
    """)
    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC")
    fun events(): Flow<List<EventWithSong>>

    @Query("DELETE FROM event")
    fun clearListenHistory()

    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    @Query("SELECT COUNT(1) FROM related_song_map WHERE songId = :songId LIMIT 1")
    fun hasRelatedSongs(songId: String): Boolean

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN
             song
             ON song.id = map.relatedSongId
        WHERE songId = :songId
        """
    )
    fun relatedSongs(songId: String): List<Song>

    @Query("SELECT * FROM genre WHERE title = :name")
    fun genreByName(name: String): GenreEntity?

    @Query("SELECT * FROM genre WHERE title LIKE '%' || :query || '%' LIMIT :previewSize")
    fun genreByAproxName(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<GenreEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(genre: GenreEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongGenreMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: RelatedSongMap)

    @Transaction
    fun insert(mediaMetadata: MediaMetadata, block: (SongEntity) -> SongEntity = { it }) {
        if (insert(mediaMetadata.toSongEntity().let(block)) == -1L) return
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()
            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    isLocal = artist.isLocal
                )
            )
            insert(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index
                )
            )
        }
        mediaMetadata.genre?.forEachIndexed { index, genre ->
            val genreId = genreByName(genre.title)?.id ?: GenreEntity.generateGenreId()
            insert(
                GenreEntity(
                    id = genreId,
                    title = genre.title,
                    isLocal = genre.isLocal
                )
            )
            insert(
                SongGenreMap(
                    songId = mediaMetadata.id,
                    genreId = genreId,
                    index = index
                )
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insert(AlbumEntity(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 }
            )) == -1L
        ) return
        albumPage.songs.map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index
                )
            }
            .forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId(),
                    name = artist.name
                )
            }
            ?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index
                )
            }
            ?.forEach(::insert)
    }

    @Transaction
    fun update(album: AlbumEntity, albumPage: AlbumPage) {
        update(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 }
            )
        )
        albumPage.songs.map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index
                )
            }
            .forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId(),
                    name = artist.name
                )
            }
            ?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index
                )
            }
            ?.forEach(::insert)
    }

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Delete
    fun delete(event: Event)

    @Transaction
    @Query("DELETE FROM genre WHERE isLocal = 1")
    fun nukeLocalGenre()

    @Transaction
    @Query("DELETE FROM format")
    fun nukeFormatEntities()

    @Transaction
    fun nukeLocalData() {
        nukeLocalSongs()
        nukeLocalArtists()
        nukeLocalAlbums()
        nukeLocalGenre()
    }

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }

    /**
     * Queueboard
     */
    @Transaction
    fun saveQueue(mq: MultiQueueObject) {
        if (mq.queue.isEmpty() || mq.unShuffled.isEmpty()) {
            return
        }

        insert(
            QueueEntity(
                id = mq.id,
                title = mq.title,
                shuffled = mq.shuffled,
                queuePos = mq.queuePos,
                index = mq.index
            )
        )

        deleteAllQueueSongs(mq.id)
        // insert songs
        mq.unShuffled.forEach {
            insert(it)
            insert(
                QueueSongMap(
                    queueId = mq.id,
                    songId = it.id,
                    shuffled = false
                )
            )
        }

        mq.queue.forEach {
            insert(
                QueueSongMap(
                    queueId = mq.id,
                    songId = it.id,
                    shuffled = true
                )
            )
        }
    }

    /**
     * WARNING: This removes all queue data and re-adds the queue. Did you mean to use updateQueue()?
     */
    @Transaction
    fun rewriteQueue(mq: MultiQueueObject) {
        delete(
            QueueEntity(
                id = mq.id,
                title = mq.title,
                shuffled = mq.shuffled,
                queuePos = mq.queuePos,
                index = mq.index,
                playlistId = mq.playlistId
            )
        )

        saveQueue(mq)
    }

    /**
     * WARNING: This removes ALL queues and their data, and re-adds them. Did you mean to use rewriteQueue()?
     */
    @Transaction
    fun rewriteAllQueues(queues: List<MultiQueueObject>) {
        deleteAllQueues()

        queues.forEach { mq ->
            saveQueue(mq)
        }
    }
}
