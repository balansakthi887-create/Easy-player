package com.maxter.player.player

import android.content.Context
import android.media.MediaCodecList
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.maxter.player.data.model.DecoderMode
import com.maxter.player.data.model.SmartEnhanceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
class MaxterPlayerController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val liveEnhanceState = AtomicReference(SmartEnhanceRenderState())
    private val persistentGlEffect = SmartEnhanceGlEffect(liveEnhanceState)
    private var effectsAttachedToPlayer = false

    private var audioBoostPercent = 100
    private var bassStrength: Short = 0

    private val _playerState = MutableStateFlow<ExoPlayer?>(null)
    val playerState: StateFlow<ExoPlayer?> = _playerState.asStateFlow()

    private val _activeDecoder = MutableStateFlow(DecoderMode.HW_PLUS)
    val activeDecoder: StateFlow<DecoderMode> = _activeDecoder.asStateFlow()

    private val _decoderStatusMessage = MutableStateFlow("Hardware MediaCodec ready")
    val decoderStatusMessage: StateFlow<String> = _decoderStatusMessage.asStateFlow()

    private val _subtitlesEnabled = MutableStateFlow(true)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    fun setInitialDecoderMode(mode: DecoderMode) {
        if (exoPlayer == null) {
            _activeDecoder.value = mode
        }
    }

    fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: buildOrRecreatePlayer(_activeDecoder.value)
    }

    fun buildOrRecreatePlayer(mode: DecoderMode): ExoPlayer {
        val previousPos = exoPlayer?.currentPosition ?: 0L
        val previousItem = exoPlayer?.currentMediaItem
        val playWhenReady = exoPlayer?.playWhenReady ?: true
        val currentSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f

        releaseAudioEffects()
        exoPlayer?.release()
        effectsAttachedToPlayer = false

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                when (mode) {
                    DecoderMode.HW -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderMode.HW_PLUS -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderMode.SW -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )
            setEnableDecoderFallback(true)
        }

        // Tuned buffer thresholds for fast video opening and low RAM footprint on 4 GB devices
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 12_000,
                /* maxBufferMs = */ 28_000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()

        player.playbackParameters = PlaybackParameters(currentSpeed)
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachAudioEffects(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (exoPlayer !== player) return

                if (isDecoderError(error)) {
                    when (_activeDecoder.value) {
                        DecoderMode.HW -> {
                            _decoderStatusMessage.value =
                                "HW decoder unsupported; auto-switched to HW+ fallback."
                            mainHandler.post { buildOrRecreatePlayer(DecoderMode.HW_PLUS) }
                        }
                        DecoderMode.HW_PLUS -> {
                            _decoderStatusMessage.value =
                                "HW+ decoder fallback triggered; switched to SW mode."
                            mainHandler.post { buildOrRecreatePlayer(DecoderMode.SW) }
                        }
                        DecoderMode.SW -> {
                            _decoderStatusMessage.value =
                                "Playback error (${error.errorCodeName}): ${error.localizedMessage ?: "Unsupported media stream"}"
                        }
                    }
                } else {
                    _decoderStatusMessage.value =
                        "Playback error (${error.errorCodeName}): ${error.localizedMessage ?: "Cannot play this media"}"
                }
            }
        })

        exoPlayer = player
        _playerState.value = player
        _activeDecoder.value = mode
        _decoderStatusMessage.value = "${mode.label} active"

        attachAudioEffects(player.audioSessionId)
        setSubtitlesEnabled(_subtitlesEnabled.value)

        val currentRenderState = liveEnhanceState.get()
        applyVideoEffects(
            enhanceConfig = currentRenderState.config,
            mirrorHorizontal = currentRenderState.mirrorHorizontal,
            flipVertical = currentRenderState.flipVertical,
            nightMode = currentRenderState.nightModeActive,
            dividerColor = Color(
                red = currentRenderState.dividerR,
                green = currentRenderState.dividerG,
                blue = currentRenderState.dividerB
            )
        )

        if (previousItem != null) {
            player.setMediaItem(previousItem, previousPos)
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        return player
    }

    private fun isDecoderError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> true
        else -> false
    }

    fun playVideoUri(uri: Uri, title: String, startPositionMs: Long = 0L, subtitleUri: Uri? = null) {
        val player = getOrCreatePlayer()
        val itemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())

        if (subtitleUri != null) {
            val mimeType = inferSubtitleMimeType(subtitleUri.toString())
            val subConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            itemBuilder.setSubtitleConfigurations(listOf(subConfig))
        }

        player.setMediaItem(itemBuilder.build(), startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
        _decoderStatusMessage.value = "Playing: $title (${_activeDecoder.value.label})"
    }

    fun pauseIfPlaying() {
        exoPlayer?.let { if (it.isPlaying) it.pause() }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(safeSpeed)
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        _subtitlesEnabled.value = enabled
        val player = exoPlayer ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    fun applyVideoEffects(
        enhanceConfig: SmartEnhanceConfig,
        mirrorHorizontal: Boolean,
        flipVertical: Boolean,
        nightMode: Boolean,
        dividerColor: Color = Color(0xFFFF6700)
    ) {
        liveEnhanceState.set(
            SmartEnhanceRenderState(
                config = enhanceConfig,
                mirrorHorizontal = mirrorHorizontal,
                flipVertical = flipVertical,
                nightModeActive = nightMode,
                dividerR = dividerColor.red,
                dividerG = dividerColor.green,
                dividerB = dividerColor.blue
            )
        )

        val player = exoPlayer ?: return
        val anyActive = enhanceConfig.enabled || mirrorHorizontal || flipVertical || nightMode
        if (anyActive == effectsAttachedToPlayer) {
            // Live uniform values already updated via AtomicReference; no pipeline rebuild needed!
            return
        }

        runCatching {
            if (anyActive) {
                player.setVideoEffects(listOf(persistentGlEffect))
                effectsAttachedToPlayer = true
            } else {
                player.setVideoEffects(emptyList())
                effectsAttachedToPlayer = false
            }
        }
    }

    fun attachExternalSubtitle(videoUri: Uri, subtitleUri: Uri) {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val playReady = player.playWhenReady
        val mimeType = inferSubtitleMimeType(subtitleUri.toString())
        val subConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(mimeType)
            .setLanguage("en")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val item = MediaItem.Builder()
            .setUri(videoUri)
            .setMediaId(videoUri.toString())
            .setSubtitleConfigurations(listOf(subConfig))
            .build()

        setSubtitlesEnabled(true)
        player.setMediaItem(item, currentPos)
        player.prepare()
        player.playWhenReady = playReady
        _decoderStatusMessage.value = "External subtitle loaded (${mimeType.substringAfterLast('/')})"
    }

    private fun inferSubtitleMimeType(uriString: String): String {
        val lower = uriString.lowercase()
        return when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ssa") || lower.endsWith(".ass") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        releaseAudioEffects()
        runCatching { equalizer = Equalizer(0, audioSessionId).apply { enabled = true } }
        runCatching {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(bassStrength)
            }
        }
        runCatching { virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true } }
        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = true
                setTargetGain(boostGainMillibels())
            }
        }
    }

    private fun boostGainMillibels(): Int = (audioBoostPercent - 100).coerceAtLeast(0) * 15

    fun setEqualizerBandLevel(band: Short, levelMillibels: Short) {
        runCatching { equalizer?.setBandLevel(band, levelMillibels) }
    }

    fun setBassBoostStrength(strengthPermille: Short) {
        bassStrength = strengthPermille.coerceIn(0.toShort(), 1000.toShort())
        runCatching { bassBoost?.setStrength(bassStrength) }
    }

    fun setAudioBoostPercent(percent: Int) {
        audioBoostPercent = percent
        runCatching { loudnessEnhancer?.setTargetGain(boostGainMillibels()) }
    }

    fun probeHardwareDecoders(): List<String> {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        return list.codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.startsWith("video/") } }
            .map { "${it.name} (${it.supportedTypes.joinToString()})" }
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        releaseAudioEffects()
        exoPlayer?.release()
        exoPlayer = null
        effectsAttachedToPlayer = false
        _playerState.value = null
    }
}
