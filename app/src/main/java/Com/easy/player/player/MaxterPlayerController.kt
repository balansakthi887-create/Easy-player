package com.maxter.player.player

import android.content.Context
import android.media.MediaCodecList
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.maxter.player.data.model.DecoderMode
import com.maxter.player.data.model.SmartEnhanceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@UnstableApi
class MaxterPlayerController(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _activeDecoder = MutableStateFlow(DecoderMode.HW_PLUS)
    val activeDecoder: StateFlow<DecoderMode> = _activeDecoder

    private val _decoderStatusMessage = MutableStateFlow("Hardware MediaCodec ready")
    val decoderStatusMessage: StateFlow<String> = _decoderStatusMessage

    fun buildOrRecreatePlayer(mode: DecoderMode): ExoPlayer {
        val previousPos = exoPlayer?.currentPosition ?: 0L
        val previousItem = exoPlayer?.currentMediaItem
        val playWhenReady = exoPlayer?.playWhenReady ?: true
        exoPlayer?.release()

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

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachAudioEffects(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Automatic decoder fallback if HW fails
                if (_activeDecoder.value == DecoderMode.HW) {
                    _activeDecoder.value = DecoderMode.HW_PLUS
                    _decoderStatusMessage.value = "HW decoder unsupported for stream; auto-switched to HW+ fallback."
                    buildOrRecreatePlayer(DecoderMode.HW_PLUS)
                } else {
                    _decoderStatusMessage.value = "Playback error (${error.errorCodeName}): ${error.localizedMessage}"
                }
            }
        })

        _activeDecoder.value = mode
        if (previousItem != null) {
            player.setMediaItem(previousItem, previousPos)
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        exoPlayer = player
        return player
    }

    fun applyVideoEffects(
        enhanceConfig: SmartEnhanceConfig,
        mirrorHorizontal: Boolean,
        flipVertical: Boolean,
        nightMode: Boolean
    ) {
        exoPlayer?.setVideoEffects(
            listOf(
                SmartEnhanceGlEffect(
                    config = enhanceConfig,
                    mirrorHorizontal = mirrorHorizontal,
                    flipVertical = flipVertical,
                    nightModeActive = nightMode
                )
            )
        )
    }

    fun attachExternalSubtitle(videoUri: Uri, subtitleUri: Uri, mimeType: String = MimeTypes.APPLICATION_SUBRIP) {
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val subConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(mimeType)
            .setLanguage("en")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val item = MediaItem.Builder()
            .setUri(videoUri)
            .setSubtitleConfigurations(listOf(subConfig))
            .build()

        exoPlayer?.setMediaItem(item, currentPos)
        exoPlayer?.prepare()
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoost?.release()
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            virtualizer?.release()
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply { enabled = true }
        }
    }

    fun setEqualizerBandLevel(band: Short, levelMillibels: Short) {
        runCatching { equalizer?.setBandLevel(band, levelMillibels) }
    }

    fun setAudioBoostPercent(percent: Int) {
        // Above 100% (up to 200% MX-style volume boost) uses LoudnessEnhancer
        val gainMb = ((percent - 100).coerceAtLeast(0)) * 15
        runCatching { loudnessEnhancer?.setTargetGain(gainMb) }
    }

    fun probeHardwareDecoders(): List<String> {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        return list.codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.startsWith("video/") } }
            .map { "${it.name} (${it.supportedTypes.joinToString()})" }
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        loudnessEnhancer?.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
