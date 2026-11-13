package com.maxter.player.data.local

import androidx.room.*
import com.maxter.player.data.model.PlaylistEntity
import com.maxter.player.data.model.VideoBookmark
import com.maxter.player.data.model.VideoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MaxterDao {
    @Query("SELECT * FROM videos WHERE isPrivate = 0 ORDER BY dateAddedSec DESC")
    fun observeAllPublicVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE isPrivate = 1 ORDER BY dateAddedSec DESC")
    fun observePrivateVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE lastPlayedEpochMs > 0 AND isPrivate = 0 ORDER BY lastPlayedEpochMs DESC LIMIT 20")
    fun observeRecentlyPlayed(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE lastPositionMs > 5000 AND lastPositionMs < (durationMs - 10000) AND isPrivate = 0 ORDER BY lastPlayedEpochMs DESC")
    fun observeContinueWatching(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE isFavorite = 1 AND isPrivate = 0 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<VideoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<VideoItem>)

    @Query("UPDATE videos SET lastPositionMs = :positionMs, lastPlayedEpochMs = :playedAt WHERE id = :videoId")
    suspend fun updatePlaybackProgress(videoId: String, positionMs: Long, playedAt: Long = System.currentTimeMillis())

    @Query("UPDATE videos SET isFavorite = :favorite WHERE id = :videoId")
    suspend fun setFavorite(videoId: String, favorite: Boolean)

    @Query("UPDATE videos SET isPrivate = :isPrivate WHERE id = :videoId")
    suspend fun setPrivate(videoId: String, isPrivate: Boolean)

    @Query("SELECT * FROM bookmarks WHERE videoId = :videoId ORDER BY timestampMs ASC")
    fun observeBookmarksForVideo(videoId: String): Flow<List<VideoBookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: VideoBookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: VideoBookmark)

    @Query("SELECT * FROM playlists ORDER BY createdAtMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
}

@Database(
    entities = [VideoItem::class, VideoBookmark::class, PlaylistEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MaxterDatabase : RoomDatabase() {
    abstract fun dao(): MaxterDao
}
