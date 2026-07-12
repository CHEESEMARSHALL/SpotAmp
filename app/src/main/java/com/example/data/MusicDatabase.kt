package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_tracks")
data class CachedTrack(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val year: Int? = null,
    val addedAt: Long? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val genres: String = "",
    val collections: String = ""
)

@Entity(tableName = "recently_played")
data class RecentTrack(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_tracks")
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val fileSize: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val likedAt: Long = System.currentTimeMillis()
)

@Dao
interface MusicDao {
    // Cached tracks for AI indexing
    @Query("SELECT * FROM cached_tracks")
    fun getAllCachedTracks(): Flow<List<CachedTrack>>

    @Query("SELECT * FROM cached_tracks")
    suspend fun getCachedTracksList(): List<CachedTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTracks(tracks: List<CachedTrack>)

    @Query("DELETE FROM cached_tracks")
    suspend fun clearCachedTracks()

    @Query("SELECT COUNT(*) FROM cached_tracks")
    suspend fun getCachedTracksCount(): Int

    // Recently played history
    @Query("SELECT * FROM recently_played ORDER BY timestamp DESC LIMIT 30")
    fun getRecentlyPlayed(): Flow<List<RecentTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentTrack(track: RecentTrack)

    @Query("DELETE FROM recently_played WHERE ratingKey = :ratingKey")
    suspend fun deleteRecentTrackByKey(ratingKey: String)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getTracksForPlaylist(playlistId: Int): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getTracksForPlaylistList(playlistId: Int): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND ratingKey = :ratingKey")
    suspend fun removeTrackFromPlaylist(playlistId: Int, ratingKey: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Int)

    // Downloads
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks")
    suspend fun getDownloadedTracksList(): List<DownloadedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedTrack(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE ratingKey = :ratingKey")
    suspend fun deleteDownloadedTrack(ratingKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE ratingKey = :ratingKey)")
    fun isTrackDownloadedFlow(ratingKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE ratingKey = :ratingKey)")
    suspend fun isTrackDownloaded(ratingKey: String): Boolean

    // Liked tracks
    @Query("SELECT * FROM liked_tracks ORDER BY likedAt DESC")
    fun getAllLikedTracks(): Flow<List<LikedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedTrack(track: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE ratingKey = :ratingKey")
    suspend fun deleteLikedTrack(ratingKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE ratingKey = :ratingKey)")
    fun isTrackLikedFlow(ratingKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE ratingKey = :ratingKey)")
    suspend fun isTrackLiked(ratingKey: String): Boolean
}

@Database(
    entities = [
        CachedTrack::class,
        RecentTrack::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        DownloadedTrackEntity::class,
        LikedTrackEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

fun PlaylistTrackEntity.toTrackItem(): com.example.playback.TrackItem {
    return com.example.playback.TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration
    )
}

fun DownloadedTrackEntity.toTrackItem(): com.example.playback.TrackItem {
    return com.example.playback.TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration
    )
}

fun LikedTrackEntity.toTrackItem(): com.example.playback.TrackItem {
    return com.example.playback.TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration
    )
}
