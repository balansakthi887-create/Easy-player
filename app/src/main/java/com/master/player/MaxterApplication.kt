package com.maxter.player

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MaxterApplication : Application(), ImageLoaderFactory {

    val isLowRamDevice: Boolean by lazy {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        am.isLowRamDevice || (memInfo.totalMem / (1024 * 1024)) <= 3072L
    }

    override fun newImageLoader(): ImageLoader {
        val memoryPercent = if (isLowRamDevice) 0.12 else 0.22
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("video_thumbs"))
                    .maxSizeBytes(64L * 1024 * 1024) // 64 MB cap for low-storage/low-end devices
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
