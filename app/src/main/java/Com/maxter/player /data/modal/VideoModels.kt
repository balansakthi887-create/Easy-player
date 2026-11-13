package com.maxter.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoItem(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val folderName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAddedSec: Long,
    val lastPositionMs: Long = 0L,
    val lastPlayedEpochMs: Long = 0L,
    val isFavorite: Boolean = false,
    val isPrivate: Boolean = false
) {
    val resolutionLabel: String
        get() = when {
            height >= 2160 -> "4K UHD"
            height >= 1440 -> "2K QHD"
            height >= 1080 -> "1080p FHD"
            height >= 720 -> "720p HD"
            height >= 480 -> "480p SD"
            else -> "${width}x${height}"
        }
}

@Entity(tableName = "bookmarks")
data class VideoBookmark(
    @PrimaryKey(autoGenerate = true) val bookmarkId: Long = 0,
    val videoId: String,
    val label: String,
    val timestampMs: Long,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val videoIdsCsv: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class DecoderMode(val label: String, val description: String) {
    HW("HW Decoder", "Native Android MediaCodec hardware decoding for lowest battery and RAM usage."),
    HW_PLUS("HW+ Decoder", "Extended MediaCodec hardware pipeline with multi-format container demuxing and fallback."),
    SW("SW Decoder", "CPU software decoding fallback for legacy or non-standard profiles; higher battery use.")
}

enum class AspectRatioMode(val label: String) {
    ORIGINAL("Original"),
    FIT("Fit Screen"),
    STRETCH("Stretch"),
    CROP("Crop / Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3")
}

enum class SmartEnhancePreset(val label: String) {
    AUTO("Auto Mode"),
    LOW_LIGHT("Low-Light Mode"),
    DETAIL("Detail Mode"),
    COLOUR("Colour Mode"),
    NOISE_REDUCTION("Noise Reduction Mode"),
    CUSTOM("Custom Mode")
}

data class SmartEnhanceConfig(
    val enabled: Boolean = false,
    val isAutoMode: Boolean = true,
    val preset: SmartEnhancePreset = SmartEnhancePreset.AUTO,
    val splitComparisonActive: Boolean = false,
    val splitPosition: Float = 0.5f,
    val intensity: Float = 0.65f,
    val brightness: Float = 0.0f,     // -0.5 to +0.5
    val contrast: Float = 1.12f,      // 0.5 to 2.0
    val sharpness: Float = 0.45f,     // 0.0 to 1.5 (Unsharp mask / CAS shader kernel)
    val saturation: Float = 1.15f,    // 0.0 to 2.0
    val denoise: Float = 0.25f        // 0.0 to 1.0 (Bilateral spatial kernel)
)
