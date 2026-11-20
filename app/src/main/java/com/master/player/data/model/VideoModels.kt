package com.maxter.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

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
            height >= 2160 || width >= 3840 -> "4K UHD"
            height >= 1440 || width >= 2560 -> "2K QHD"
            height >= 1080 || width >= 1920 -> "1080p FHD"
            height >= 720 || width >= 1280 -> "720p HD"
            height >= 480 -> "480p SD"
            else -> "${width}x${height}"
        }

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0L) return "Stream"
            val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }

    val progressFraction: Float
        get() = if (durationMs > 0L && lastPositionMs > 0L) {
            (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
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
) {
    val videoIds: List<String>
        get() = videoIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

enum class DecoderMode(val label: String, val description: String) {
    HW("HW Decoder", "Native Android MediaCodec hardware decoding for lowest battery and RAM usage."),
    HW_PLUS("HW+ Decoder", "Extended MediaCodec hardware pipeline with multi-format container demuxing and fallback."),
    SW("SW Decoder", "CPU software decoding fallback for legacy or non-standard profiles; higher battery use.")
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit Screen"),
    FILL("Fill / Stretch"),
    ZOOM("Crop / Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    ORIGINAL("Original")
}

enum class SortOption(val label: String) {
    DATE_DESC("Newest First"),
    TITLE_ASC("Title (A–Z)"),
    DURATION_DESC("Longest First"),
    SIZE_DESC("Largest Size")
}

enum class SmartEnhanceLevel(val label: String, val intensity: Float) {
    OFF("OFF", 0.0f),
    LOW("LOW", 0.42f),
    MEDIUM("MEDIUM", 0.72f),
    HIGH("HIGH", 1.0f),
    CUSTOM("CUSTOM", 0.80f)
}

enum class SmartEnhancePreset(
    val label: String,
    val defaultBrightness: Float,
    val defaultContrast: Float,
    val defaultSharpness: Float,
    val defaultSaturation: Float,
    val defaultDenoise: Float
) {
    AUTO("Auto Mode", 0.03f, 1.18f, 0.75f, 1.22f, 0.25f),
    LOW_LIGHT("Low-Light Boost", 0.14f, 1.24f, 0.55f, 1.16f, 0.45f),
    DETAIL("Detail & Crisp", 0.01f, 1.20f, 1.15f, 1.14f, 0.15f),
    COLOUR("Cinema Vivid", 0.02f, 1.22f, 0.65f, 1.38f, 0.20f),
    NOISE_REDUCTION("Clean Denoise", 0.01f, 1.10f, 0.40f, 1.10f, 0.78f),
    CUSTOM("Custom Tuning", 0.02f, 1.16f, 0.65f, 1.20f, 0.25f)
}

data class SmartEnhanceConfig(
    val enabled: Boolean = false,
    val level: SmartEnhanceLevel = SmartEnhanceLevel.OFF,
    val isAutoMode: Boolean = true,
    val preset: SmartEnhancePreset = SmartEnhancePreset.AUTO,
    val splitComparisonActive: Boolean = false,
    val splitPosition: Float = 0.5f,
    val intensity: Float = 0.72f,
    val brightness: Float = 0.03f,    // -0.4 to +0.4
    val contrast: Float = 1.18f,      // 0.6 to 1.8
    val sharpness: Float = 0.75f,     // 0.0 to 1.5 (CAS / unsharp mask)
    val saturation: Float = 1.22f,    // 0.5 to 1.8 (vibrance-weighted)
    val denoise: Float = 0.25f        // 0.0 to 1.0 (bilateral spatial)
) {
    fun withLevel(newLevel: SmartEnhanceLevel): SmartEnhanceConfig {
        return when (newLevel) {
            SmartEnhanceLevel.OFF -> copy(
                enabled = false,
                level = SmartEnhanceLevel.OFF,
                splitComparisonActive = false
            )
            SmartEnhanceLevel.CUSTOM -> copy(
                enabled = true,
                level = SmartEnhanceLevel.CUSTOM,
                preset = SmartEnhancePreset.CUSTOM
            )
            else -> copy(
                enabled = true,
                level = newLevel,
                intensity = newLevel.intensity
            )
        }
    }

    fun withPreset(newPreset: SmartEnhancePreset): SmartEnhanceConfig {
        val nextLevel = when {
            !enabled -> SmartEnhanceLevel.MEDIUM
            newPreset == SmartEnhancePreset.CUSTOM -> SmartEnhanceLevel.CUSTOM
            level == SmartEnhanceLevel.OFF -> SmartEnhanceLevel.MEDIUM
            else -> level
        }
        return copy(
            enabled = true,
            level = nextLevel,
            preset = newPreset,
            isAutoMode = newPreset == SmartEnhancePreset.AUTO,
            intensity = if (nextLevel == SmartEnhanceLevel.CUSTOM) intensity else nextLevel.intensity,
            brightness = newPreset.defaultBrightness,
            contrast = newPreset.defaultContrast,
            sharpness = newPreset.defaultSharpness,
            saturation = newPreset.defaultSaturation,
            denoise = newPreset.defaultDenoise
        )
    }

    fun resetToDefault(): SmartEnhanceConfig = SmartEnhanceConfig()
}
