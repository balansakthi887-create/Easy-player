package com.maxter.player.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.maxter.player.data.local.MaxterDao
import com.maxter.player.data.model.*
import com.maxter.player.data.repository.MediaScannerRepository
import com.maxter.player.player.MaxterPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class MaxterViewModel @Inject constructor(
    private val dao: MaxterDao,
    private val scannerRepository: MediaScannerRepository,
    val playerController: MaxterPlayerController
) : ViewModel() {

    val allVideos: StateFlow<List<VideoItem>> = dao.observeAllPublicVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<VideoItem>> = dao.observeRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<VideoItem>> = dao.observeContinueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<VideoItem>> = dao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = dao.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState: StateFlow<ExoPlayer?> = playerController.playerState
    val activeDecoder: StateFlow<DecoderMode> = playerController.activeDecoder
    val decoderStatusMessage: StateFlow<String> = playerController.decoderStatusMessage
    val subtitlesEnabled: StateFlow<Boolean> = playerController.subtitlesEnabled

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _activeVideo = MutableStateFlow<VideoItem?>(null)
    val activeVideo: StateFlow<VideoItem?> = _activeVideo.asStateFlow()

    private val _activeQueue = MutableStateFlow<List<VideoItem>>(emptyList())
    val activeQueue: StateFlow<List<VideoItem>> = _activeQueue.asStateFlow()

    private val _smartEnhanceConfig = MutableStateFlow(SmartEnhanceConfig())
    val smartEnhanceConfig: StateFlow<SmartEnhanceConfig> = _smartEnhanceConfig.asStateFlow()

    private val _mirrorHorizontal = MutableStateFlow(false)
    val mirrorHorizontal: StateFlow<Boolean> = _mirrorHorizontal.asStateFlow()

    private val _flipVertical = MutableStateFlow(false)
    val flipVertical: StateFlow<Boolean> = _flipVertical.asStateFlow()

    private val _nightMode = MutableStateFlow(false)
    val nightMode: StateFlow<Boolean> = _nightMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatio: StateFlow<AspectRatioMode> = _aspectRatio.asStateFlow()

    private val _audioBoostPercent = MutableStateFlow(100)
    val audioBoostPercent: StateFlow<Int> = _audioBoostPercent.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _autoResumeEnabled = MutableStateFlow(true)
    val autoResumeEnabled: StateFlow<Boolean> = _autoResumeEnabled.asStateFlow()

    private val _gestureControlsEnabled = MutableStateFlow(true)
    val gestureControlsEnabled: StateFlow<Boolean> = _gestureControlsEnabled.asStateFlow()

    private val _doubleTapSeekSeconds = MutableStateFlow(10)
    val doubleTapSeekSeconds: StateFlow<Int> = _doubleTapSeekSeconds.asStateFlow()

    fun refreshDeviceLibrary() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scannerRepository.scanDeviceVideos()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun startVideoPlayback(
        video: VideoItem,
        queue: List<VideoItem> = allVideos.value,
        resumeFromSavedPosition: Boolean = _autoResumeEnabled.value
    ) {
        val current = _activeVideo.value
        if (current != null) {
            val pos = playerController.playerState.value?.currentPosition ?: 0L
            savePlaybackProgress(current.id, pos)
        }
        val effectiveQueue = if (queue.isNotEmpty()) queue else listOf(video)
        _activeQueue.value = effectiveQueue
        _activeVideo.value = video
        val startPos = if (resumeFromSavedPosition && video.lastPositionMs > 3000L &&
            (video.durationMs <= 0L || video.lastPositionMs < video.durationMs - 5000L)
        ) {
            video.lastPositionMs
        } else {
            0L
        }
        playerController.playVideoUri(
            uri = Uri.parse(video.uri),
            title = video.title,
            startPositionMs = startPos
        )
        syncEffects()
        savePlaybackProgress(video.id, startPos)
    }

    fun playExternalUri(uri: Uri) {
        val rawName = uri.lastPathSegment?.substringAfterLast('/') ?: "External Video Stream"
        val externalItem = VideoItem(
            id = "ext_${uri.toString().hashCode()}",
            uri = uri.toString(),
            title = rawName,
            folderName = "External / Stream",
            durationMs = 0L,
            sizeBytes = 0L,
            width = 1920,
            height = 1080,
            mimeType = "video/*",
            dateAddedSec = System.currentTimeMillis() / 1000L
        )
        viewModelScope.launch {
            dao.upsertSingleVideo(externalItem)
        }
        startVideoPlayback(externalItem, listOf(externalItem), resumeFromSavedPosition = false)
    }

    fun playNextVideo() {
        val current = _activeVideo.value ?: return
        val queue = _activeQueue.value
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.id == current.id }
        val nextIndex = if (idx in 0 until queue.lastIndex) idx + 1 else 0
        startVideoPlayback(queue[nextIndex], queue)
    }

    fun playPreviousVideo() {
        val player = playerController.playerState.value
        if (player != null && player.currentPosition > 5000L) {
            player.seekTo(0L)
            return
        }
        val current = _activeVideo.value ?: return
        val queue = _activeQueue.value
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.id == current.id }
        val prevIndex = if (idx > 0) idx - 1 else queue.lastIndex
        startVideoPlayback(queue[prevIndex], queue)
    }

    fun closePlayer() {
        val current = _activeVideo.value
        val pos = playerController.playerState.value?.currentPosition ?: 0L
        if (current != null) {
            savePlaybackProgress(current.id, pos)
        }
        playerController.playerState.value?.pause()
        _activeVideo.value = null
    }

    fun savePlaybackProgress(videoId: String, positionMs: Long) {
        viewModelScope.launch {
            dao.updatePlaybackProgress(videoId, positionMs.coerceAtLeast(0L))
        }
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            val updated = !video.isFavorite
            dao.setFavorite(video.id, updated)
            if (_activeVideo.value?.id == video.id) {
                _activeVideo.value = _activeVideo.value?.copy(isFavorite = updated)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.clearPlaybackHistory()
        }
    }

    fun createPlaylist(name: String, selectedVideoIds: List<String> = emptyList()) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dao.upsertPlaylist(
                PlaylistEntity(
                    name = trimmed,
                    videoIdsCsv = selectedVideoIds.joinToString(",")
                )
            )
        }
    }

    fun addVideoToPlaylist(playlist: PlaylistEntity, videoId: String) {
        viewModelScope.launch {
            val currentIds = playlist.videoIds.toMutableList()
            if (!currentIds.contains(videoId)) {
                currentIds.add(videoId)
                dao.upsertPlaylist(playlist.copy(videoIdsCsv = currentIds.joinToString(",")))
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            dao.deletePlaylist(playlist)
        }
    }

    fun setDecoderMode(mode: DecoderMode) {
        playerController.buildOrRecreatePlayer(mode)
        syncEffects()
    }

    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 4.0f)
        _playbackSpeed.value = safe
        playerController.setPlaybackSpeed(safe)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _aspectRatio.value = mode
    }

    fun toggleSubtitles(enabled: Boolean) {
        playerController.setSubtitlesEnabled(enabled)
    }

    fun attachSubtitleUri(subtitleUri: Uri) {
        val current = _activeVideo.value ?: return
        playerController.attachExternalSubtitle(Uri.parse(current.uri), subtitleUri)
    }

    fun updateSmartEnhance(config: SmartEnhanceConfig) {
        _smartEnhanceConfig.value = config
        syncEffects()
    }

    fun setMirrorHorizontal(enabled: Boolean) {
        _mirrorHorizontal.value = enabled
        syncEffects()
    }

    fun setFlipVertical(enabled: Boolean) {
        _flipVertical.value = enabled
        syncEffects()
    }

    fun setNightMode(enabled: Boolean) {
        _nightMode.value = enabled
        syncEffects()
    }

    fun setAudioBoostPercent(percent: Int) {
        val clamped = percent.coerceIn(100, 200)
        _audioBoostPercent.value = clamped
        playerController.setAudioBoostPercent(clamped)
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        playerController.setBassBoostStrength(clamped.toShort())
    }

    fun setAutoResumeEnabled(enabled: Boolean) {
        _autoResumeEnabled.value = enabled
    }

    fun setGestureControlsEnabled(enabled: Boolean) {
        _gestureControlsEnabled.value = enabled
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        _doubleTapSeekSeconds.value = seconds.coerceIn(5, 30)
    }

    private fun syncEffects() {
        playerController.applyVideoEffects(
            enhanceConfig = _smartEnhanceConfig.value,
            mirrorHorizontal = _mirrorHorizontal.value,
            flipVertical = _flipVertical.value,
            nightMode = _nightMode.value
        )
    }

    override fun onCleared() {
        val current = _activeVideo.value
        val pos = playerController.playerState.value?.currentPosition ?: 0L
        if (current != null) {
            savePlaybackProgress(current.id, pos)
        }
        playerController.release()
        super.onCleared()
    }
}
