package com.maxter.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.maxter.player.data.local.MaxterDao
import com.maxter.player.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScannerRepository(
    private val context: Context,
    private val dao: MaxterDao
) {
    suspend fun scanDeviceVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            @Suppress("DEPRECATION") MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val title = cursor.getString(nameCol) ?: "Untitled Video"
                    val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val folder = when {
                        !bucketName.isNullOrBlank() -> bucketName
                        path.isNotBlank() -> File(path).parentFile?.name ?: "Internal Storage"
                        else -> "Movies"
                    }
                    val duration = cursor.getLong(durCol).coerceAtLeast(0L)
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val width = cursor.getInt(widthCol).let { if (it > 0) it else 1280 }
                    val height = cursor.getInt(heightCol).let { if (it > 0) it else 720 }
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val dateAdded = cursor.getLong(dateCol)

                    discovered.add(
                        VideoItem(
                            id = id.toString(),
                            uri = contentUri,
                            title = title,
                            folderName = folder,
                            durationMs = duration,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            mimeType = mime,
                            dateAddedSec = dateAdded
                        )
                    )
                }
            }
        }

        if (discovered.isNotEmpty()) {
            dao.syncScannedVideosPreservingHistory(discovered)
        }
        discovered
    }
}
