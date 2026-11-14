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

    @Query("SELECT * FROM videos WHERE lastPlayedEpochMs > 0 AND isPrivate = 0 ORDER BY lastPlayedEpochMs DESC LIMIT 30")
    fun observeRecentlyPlayed(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE lastPositionMs > 5000 AND lastPositionMs < (durationMs - 10000) AND isPrivate = 0 ORDER BY lastPlayedEpochMs DESC")
    fun observeContinueWatching(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE isFavorite = 1 AND isPrivate = 0 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos")
    suspend fun getAllVideosSnapshot(): List<VideoItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewVideosIgnore(videos: List<VideoItem>)

    @Query(
        """
        UPDATE videos 
        SET uri = :uri, title = :title, folderName = :folderName, durationMs = :durationMs,
            sizeBytes = :sizeBytes, width = :width, height = :height, mimeType = :mimeType, dateAddedSec = :dateAddedSec
        WHERE id = :id
        """
    )
    suspend fun updateVideoMetadataPreservingState(
        id: String,
        uri: String,
        title: String,
        folderName: String,
        durationMs: Long,
        sizeBytes: Long,
        width: Int,
        height: Int,
        mimeType: String,
        dateAddedSec: Long
    )

    @Transaction
    suspend fun syncScannedVideosPreservingHistory(scanned: List<VideoItem>) {
        insertNewVideosIgnore(scanned)
        for (v in scanned) {
            updateVideoMetadataPreservingState(
                id = v.id,
                uri = v.uri,
                title = v.title,
                folderName = v.folderName,
                durationMs = v.durationMs,
                sizeBytes = v.sizeBytes,
                width = v.width,
                height = v.height,
                mimeType = v.mimeType,
                dateAddedSec = v.dateAddedSec
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleVideo(video: VideoItem)

    @Query("UPDATE videos SET lastPositionMs = :positionMs, lastPlayedEpochMs = :playedAt WHERE id = :videoId")
    suspend fun updatePlaybackProgress(videoId: String, positionMs: Long, playedAt: Long = System.currentTimeMillis())

    @Query("UPDATE videos SET lastPositionMs = 0, lastPlayedEpochMs = 0")
    suspend fun clearPlaybackHistory()

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
