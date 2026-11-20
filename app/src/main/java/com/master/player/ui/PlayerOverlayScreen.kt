package com.maxter.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.maxter.player.data.model.AspectRatioMode
import com.maxter.player.data.model.DecoderMode
import com.maxter.player.data.model.SmartEnhanceLevel
import com.maxter.player.data.model.SmartEnhancePreset
import com.maxter.player.data.model.VideoItem
import com.maxter.player.ui.theme.MaxterTheme
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureAxis { NONE, HORIZONTAL, VERTICAL }

@UnstableApi
@Composable
fun FullScreenVideoPlayerOverlay(
    viewModel: MaxterViewModel,
    video: VideoItem,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onShowVideoInfo: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val theme = MaxterTheme.colors
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val viewConfig = LocalViewConfiguration.current

    val exoPlayer by viewModel.playerState.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val activeDecoder by viewModel.activeDecoder.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val smartEnhance by viewModel.smartEnhanceConfig.collectAsState()
    val gestureSettings by viewModel.gestureSettings.collectAsState()
    val audioBoost by viewModel.audioBoostPercent.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isLongPressSpeedActive by remember { mutableStateOf(false) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSmartEnhanceSheet by remember { mutableStateOf(false) }

    var hudLabel by remember { mutableStateOf<String?>(null) }
    var hudProgress by remember { mutableFloatStateOf(-1f) }
    var previewSeekMs by remember { mutableStateOf<Long?>(null) }

    var brightnessLevel by remember {
        val currentAttr = activity?.window?.attributes?.screenBrightness ?: -1f
        val initial = if (currentAttr in 0.01f..1.0f) {
            currentAttr
        } else {
            runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            }.getOrDefault(0.55f).coerceIn(0.05f, 1.0f)
        }
        mutableFloatStateOf(initial)
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.attachSubtitleUri(uri)
        }
    }

    BackHandler {
        when {
            showSmartEnhanceSheet -> showSmartEnhanceSheet = false
            isScreenLocked -> {
                hudLabel = "Screen Locked — Tap Lock Icon to Unlock"
                hudProgress = -1f
            }
            else -> {
                if (isLandscapeFullscreen) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    isLandscapeFullscreen = false
                }
                onClose()
            }
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Track playing state & periodically persist resume progress without triggering full-tree recomposition
    LaunchedEffect(exoPlayer, video.id) {
        val player = exoPlayer ?: return@LaunchedEffect
        isPlaying = player.isPlaying
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                delay(5_000L)
                if (player.isPlaying && player.currentMediaItem?.mediaId == video.uri) {
                    viewModel.savePlaybackProgress(video.id, player.currentPosition.coerceAtLeast(0L))
                }
            }
        } finally {
            player.removeListener(listener)
        }
    }

    // Auto-hide controls after 3.5 seconds while playing
    LaunchedEffect(controlsVisible, isPlaying, isScreenLocked, showSmartEnhanceSheet) {
        if (controlsVisible && isPlaying && !isScreenLocked && !showSmartEnhanceSheet) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(hudLabel) {
        if (hudLabel != null && previewSeekMs == null && !isLongPressSpeedActive) {
            delay(1_000L)
            hudLabel = null
            hudProgress = -1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap, Double-Tap, and Long-Press Speed gestures
            .pointerInput(isScreenLocked, gestureSettings, exoPlayer) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (isScreenLocked || !gestureSettings.gesturesEnabled || !gestureSettings.doubleTapSeekEnabled) {
                            return@detectTapGestures
                        }
                        val player = exoPlayer ?: return@detectTapGestures
                        val deltaMs = gestureSettings.doubleTapSeekSeconds * 1000L
                        val maxDur = player.duration.takeIf { it > 0 } ?: video.durationMs.coerceAtLeast(1L)
                        when {
                            offset.x < size.width * 0.42f -> {
                                val target = (player.currentPosition - deltaMs).coerceAtLeast(0L)
                                player.seekTo(target)
                                hudLabel = "⏪ -${gestureSettings.doubleTapSeekSeconds}s (${formatPlayerMillis(target)})"
                                hudProgress = -1f
                            }
                            offset.x > size.width * 0.58f -> {
                                val target = (player.currentPosition + deltaMs).coerceAtMost(maxDur)
                                player.seekTo(target)
                                hudLabel = "⏩ +${gestureSettings.doubleTapSeekSeconds}s (${formatPlayerMillis(target)})"
                                hudProgress = -1f
                            }
                            else -> {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        }
                    },
                    onLongPress = {
                        if (isScreenLocked || !gestureSettings.gesturesEnabled || !gestureSettings.longPressSpeedEnabled) {
                            return@detectTapGestures
                        }
                        val player = exoPlayer ?: return@detectTapGestures
                        val prevSpeed = playbackSpeed
                        isLongPressSpeedActive = true
                        player.setPlaybackSpeed(gestureSettings.longPressSpeed)
                        hudLabel = "⏩ ${gestureSettings.longPressSpeed}x Speed"
                        hudProgress = -1f
                        tryAwaitRelease()
                        isLongPressSpeedActive = false
                        player.setPlaybackSpeed(prevSpeed)
                        hudLabel = null
                    }
                )
            }
            // Unified directional-locked drag gesture (no conflict between horizontal seek and vertical vol/brightness)
            .pointerInput(isScreenLocked, gestureSettings, exoPlayer) {
                if (isScreenLocked || !gestureSettings.gesturesEnabled) return@pointerInput
                val touchSlop = viewConfig.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var axis = GestureAxis.NONE
                    var totalDx = 0f
                    var totalDy = 0f
                    var volumeRemainder = 0f
                    val startSeekPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    val maxDur = exoPlayer?.duration?.takeIf { it > 0 } ?: video.durationMs.coerceAtLeast(1L)
                    var targetSeekPos = startSeekPos
                    val isLeftHalf = down.position.x < size.width * 0.5f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (axis == GestureAxis.HORIZONTAL && previewSeekMs != null) {
                                exoPlayer?.seekTo(targetSeekPos)
                                previewSeekMs = null
                                hudLabel = null
                            }
                            break
                        }

                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        totalDx += dx
                        totalDy += dy

                        if (axis == GestureAxis.NONE) {
                            if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * 1.2f) {
                                if (gestureSettings.seekGestureEnabled) {
                                    axis = GestureAxis.HORIZONTAL
                                }
                            } else if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.2f) {
                                val allowed = if (isLeftHalf) gestureSettings.brightnessGestureEnabled else gestureSettings.volumeGestureEnabled
                                if (allowed) {
                                    axis = GestureAxis.VERTICAL
                                }
                            }
                        }

                        when (axis) {
                            GestureAxis.HORIZONTAL -> {
                                change.consume()
                                // Full-width swipe = 90 seconds of seek for precise control
                                val fraction = totalDx / size.width.toFloat().coerceAtLeast(1f)
                                val deltaMs = (fraction * 90_000f).toLong()
                                targetSeekPos = (startSeekPos + deltaMs).coerceIn(0L, maxDur)
                                previewSeekMs = targetSeekPos
                                val sign = if (deltaMs >= 0) "+" else "-"
                                hudLabel = "⏱ ${formatPlayerMillis(targetSeekPos)}  ($sign${abs(deltaMs) / 1000}s)"
                                hudProgress = (targetSeekPos.toFloat() / maxDur.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            }
                            GestureAxis.VERTICAL -> {
                                change.consume()
                                val deltaFraction = -dy / size.height.toFloat().coerceAtLeast(1f)
                                if (isLeftHalf) {
                                    brightnessLevel = (brightnessLevel + deltaFraction * 1.25f).coerceIn(0.02f, 1.0f)
                                    activity?.window?.let { win ->
                                        val lp = win.attributes
                                        lp.screenBrightness = brightnessLevel
                                        win.attributes = lp
                                    }
                                    hudLabel = "☀ Brightness: ${(brightnessLevel * 100).roundToInt()}%"
                                    hudProgress = brightnessLevel
                                } else {
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    volumeRemainder += deltaFraction * maxVol * 1.4f
                                    val steps = volumeRemainder.toInt()
                                    if (steps != 0) {
                                        volumeRemainder -= steps
                                        val newVol = (curVol + steps).coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        val pct = (newVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                                        hudLabel = "🔊 Volume: ${(pct * 100).roundToInt()}%"
                                        hudProgress = pct
                                    } else if (hudLabel == null) {
                                        val pct = (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                                        hudLabel = "🔊 Volume: ${(pct * 100).roundToInt()}%"
                                        hudProgress = pct
                                    }
                                }
                            }
                            GestureAxis.NONE -> Unit
                        }
                    }
                }
            }
    ) {
        // Isolated PlayerView composable so timeline ticks never recompose it
        PlayerSurfaceView(
            exoPlayer = exoPlayer,
            aspectRatio = aspectRatio,
            modifier = Modifier.align(Alignment.Center)
        )

        // Before / After Split Screen Overlay Badges
        if (smartEnhance.enabled && smartEnhance.splitComparisonActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "BEFORE (Original)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = theme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "AFTER (Smart Enhance)",
                        color = theme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Gesture / Seek / Speed HUD
        AnimatedVisibility(
            visible = hudLabel != null,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, theme.primary),
                modifier = Modifier.widthIn(min = 165.dp, max = 270.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = hudLabel.orEmpty(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (hudProgress >= 0f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { hudProgress },
                            color = theme.progressBar,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isScreenLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            isScreenLocked = false
                            hudLabel = "Controls Unlocked"
                            hudProgress = -1f
                        },
                        containerColor = theme.playerControl,
                        contentColor = theme.onPrimary,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock Screen")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.78f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.86f)
                                )
                            )
                        )
                ) {
                    // Top Bar Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${video.resolutionLabel} • ${activeDecoder.label} • ${playbackSpeed}x",
                                color = theme.highlight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = {
                                val nextDecoder = when (activeDecoder) {
                                    DecoderMode.HW -> DecoderMode.HW_PLUS
                                    DecoderMode.HW_PLUS -> DecoderMode.SW
                                    DecoderMode.SW -> DecoderMode.HW
                                }
                                viewModel.setDecoderMode(nextDecoder)
                            }
                        ) {
                            Text(activeDecoder.label, color = theme.playerControl, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showSmartEnhanceSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Smart Enhance",
                                tint = if (smartEnhance.enabled) theme.playerControl else Color.White
                            )
                        }
                        IconButton(onClick = { onShowVideoInfo(video) }) {
                            Icon(Icons.Default.Info, contentDescription = "Video Info", tint = Color.White)
                        }
                        IconButton(onClick = onEnterPip) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                        }
                    }

                    // Center Transport Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.playPreviousVideo() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                val player = exoPlayer ?: return@FloatingActionButton
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            containerColor = theme.playerControl,
                            contentColor = theme.onPrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.playNextVideo() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Bottom Bar Controls (Isolated Timeline + Action Buttons)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        PlayerTimelineBar(
                            exoPlayer = exoPlayer,
                            video = video,
                            previewSeekMs = previewSeekMs,
                            progressBarColor = theme.progressBar
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isScreenLocked = true
                                    hudLabel = "Screen Controls Locked"
                                    hudProgress = -1f
                                }
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
                            }

                            TextButton(onClick = { showSpeedDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = theme.playerControl,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            TextButton(onClick = { showSubtitleSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitles",
                                    tint = if (subtitlesEnabled) theme.playerControl else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("CC", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = {
                                    val modes = AspectRatioMode.values()
                                    val next = modes[(aspectRatio.ordinal + 1) % modes.size]
                                    viewModel.setAspectRatio(next)
                                    hudLabel = "Aspect Ratio: ${next.label}"
                                    hudProgress = -1f
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = theme.playerControl,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(aspectRatio.label, color = Color.White, fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = {
                                    isLandscapeFullscreen = !isLandscapeFullscreen
                                    activity?.requestedOrientation = if (isLandscapeFullscreen) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isLandscapeFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen Orientation",
                                    tint = theme.playerControl
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = theme.cardSurface,
            titleContentColor = theme.text,
            textContentColor = theme.text,
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    speeds.forEach { spd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackSpeed == spd,
                                onClick = {
                                    viewModel.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = theme.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = theme.primary)
                }
            }
        )
    }

    if (showSubtitleSheet) {
        AlertDialog(
            onDismissRequest = { showSubtitleSheet = false },
            containerColor = theme.cardSurface,
            titleContentColor = theme.text,
            textContentColor = theme.text,
            title = { Text("Subtitles & Audio Boost", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Subtitle Tracks")
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = { viewModel.toggleSubtitles(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = theme.onPrimary,
                                checkedTrackColor = theme.primary
                            )
                        )
                    }
                    Button(
                        onClick = {
                            showSubtitleSheet = false
                            subtitlePickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Load External SRT / VTT / ASS File", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Volume Boost: ${audioBoost}%", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = audioBoost.toFloat(),
                        onValueChange = { viewModel.setAudioBoostPercent(it.roundToInt()) },
                        valueRange = 100f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.primary,
                            activeTrackColor = theme.progressBar
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleSheet = false }) {
                    Text("Done", color = theme.primary)
                }
            }
        )
    }

    if (showSmartEnhanceSheet) {
        SmartEnhanceControlDialog(
            viewModel = viewModel,
            onDismiss = { showSmartEnhanceSheet = false }
        )
    }
}

@UnstableApi
@Composable
private fun PlayerSurfaceView(
    exoPlayer: ExoPlayer?,
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier
) {
    if (exoPlayer == null) return
    val forcedRatio: Float? = when (aspectRatio) {
        AspectRatioMode.RATIO_16_9 -> 16f / 9f
        AspectRatioMode.RATIO_4_3 -> 4f / 3f
        else -> null
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                keepScreenOn = true
                player = exoPlayer
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) {
                view.player = exoPlayer
            }
            val targetResize = when (aspectRatio) {
                AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioMode.RATIO_16_9,
                AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            }
            if (view.resizeMode != targetResize) {
                view.resizeMode = targetResize
            }
        },
        modifier = if (forcedRatio != null) {
            modifier.aspectRatio(forcedRatio)
        } else {
            Modifier.fillMaxSize()
        }
    )
}

@UnstableApi
@Composable
private fun PlayerTimelineBar(
    exoPlayer: ExoPlayer?,
    video: VideoItem,
    previewSeekMs: Long?,
    progressBarColor: Color
) {
    var currentPosMs by remember(video.id) { mutableLongStateOf(0L) }
    var durationMs by remember(video.id) { mutableLongStateOf(video.durationMs.coerceAtLeast(1L)) }
    var sliderDraggingPos by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(exoPlayer, video.id) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            if (sliderDraggingPos == null) {
                currentPosMs = player.currentPosition.coerceAtLeast(0L)
            }
            if (player.duration > 0) {
                durationMs = player.duration
            }
            delay(250L)
        }
    }

    val displayedPos = previewSeekMs ?: sliderDraggingPos ?: currentPosMs

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatPlayerMillis(displayedPos),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = displayedPos.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { newPos ->
                sliderDraggingPos = newPos.toLong()
            },
            onValueChangeFinished = {
                sliderDraggingPos?.let { target ->
                    exoPlayer?.seekTo(target)
                    currentPosMs = target
                }
                sliderDraggingPos = null
            },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = progressBarColor,
                activeTrackColor = progressBarColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        Text(
            text = formatPlayerMillis(durationMs),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@UnstableApi
@Composable
private fun SmartEnhanceControlDialog(
    viewModel: MaxterViewModel,
    onDismiss: () -> Unit
) {
    val theme = MaxterTheme.colors
    val smartEnhance by viewModel.smartEnhanceConfig.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        titleContentColor = theme.text,
        textContentColor = theme.text,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Smart Enhance GPU", fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.resetSmartEnhance() }) {
                    Text("Reset", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Level Selector: OFF / LOW / MEDIUM / HIGH / CUSTOM
                Text("Enhancement Level", fontWeight = FontWeight.SemiBold, color = theme.primary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmartEnhanceLevel.values().forEach { lvl ->
                        val selected = smartEnhance.level == lvl
                        Surface(
                            color = if (selected) theme.primary else theme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) theme.primary else theme.mutedText.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.updateSmartEnhance(smartEnhance.withLevel(lvl)) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = lvl.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) theme.onPrimary else theme.text,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Before / After Split Comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Before / After Split Comparison", fontSize = 13.sp)
                    Switch(
                        checked = smartEnhance.splitComparisonActive,
                        onCheckedChange = { active ->
                            viewModel.updateSmartEnhance(
                                smartEnhance.copy(
                                    enabled = if (active) true else smartEnhance.enabled,
                                    level = if (active && smartEnhance.level == SmartEnhanceLevel.OFF) SmartEnhanceLevel.MEDIUM else smartEnhance.level,
                                    splitComparisonActive = active
                                )
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.onPrimary,
                            checkedTrackColor = theme.primary
                        )
                    )
                }

                if (smartEnhance.splitComparisonActive) {
                    Text("Split Divider Position: ${(smartEnhance.splitPosition * 100).roundToInt()}%", fontSize = 12.sp)
                    Slider(
                        value = smartEnhance.splitPosition,
                        onValueChange = { viewModel.updateSmartEnhance(smartEnhance.copy(splitPosition = it)) },
                        valueRange = 0.1f..0.9f,
                        colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                    )
                }

                // Preset Modes
                Text("Preset Mode", fontWeight = FontWeight.SemiBold, color = theme.primary, fontSize = 12.sp)
                SmartEnhancePreset.values().forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateSmartEnhance(smartEnhance.withPreset(preset)) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = smartEnhance.preset == preset,
                            onClick = { viewModel.updateSmartEnhance(smartEnhance.withPreset(preset)) },
                            colors = RadioButtonDefaults.colors(selectedColor = theme.primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(preset.label, fontSize = 13.sp)
                    }
                }

                // Live Fine-Tuning Sliders
                Text("Intensity: ${(smartEnhance.intensity * 100).roundToInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = smartEnhance.intensity,
                    onValueChange = {
                        viewModel.updateSmartEnhance(
                            smartEnhance.copy(
                                enabled = it > 0.01f,
                                level = if (it <= 0.01f) SmartEnhanceLevel.OFF else SmartEnhanceLevel.CUSTOM,
                                intensity = it
                            )
                        )
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )

                Text("Sharpness (Detail): ${(smartEnhance.sharpness * 100).roundToInt()}%", fontSize = 12.sp)
                Slider(
                    value = smartEnhance.sharpness,
                    onValueChange = {
                        viewModel.updateSmartEnhance(
                            smartEnhance.copy(enabled = true, level = SmartEnhanceLevel.CUSTOM, preset = SmartEnhancePreset.CUSTOM, sharpness = it)
                        )
                    },
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )

                Text("Contrast: ${String.format(Locale.US, "%.2f", smartEnhance.contrast)}x", fontSize = 12.sp)
                Slider(
                    value = smartEnhance.contrast,
                    onValueChange = {
                        viewModel.updateSmartEnhance(
                            smartEnhance.copy(enabled = true, level = SmartEnhanceLevel.CUSTOM, preset = SmartEnhancePreset.CUSTOM, contrast = it)
                        )
                    },
                    valueRange = 0.7f..1.6f,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )

                Text("Brightness: ${if (smartEnhance.brightness >= 0) "+" else ""}${(smartEnhance.brightness * 100).roundToInt()}%", fontSize = 12.sp)
                Slider(
                    value = smartEnhance.brightness,
                    onValueChange = {
                        viewModel.updateSmartEnhance(
                            smartEnhance.copy(enabled = true, level = SmartEnhanceLevel.CUSTOM, preset = SmartEnhancePreset.CUSTOM, brightness = it)
                        )
                    },
                    valueRange = -0.25f..0.25f,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )

                Text("Colour Vibrance: ${String.format(Locale.US, "%.2f", smartEnhance.saturation)}x", fontSize = 12.sp)
                Slider(
                    value = smartEnhance.saturation,
                    onValueChange = {
                        viewModel.updateSmartEnhance(
                            smartEnhance.copy(enabled = true, level = SmartEnhanceLevel.CUSTOM, preset = SmartEnhancePreset.CUSTOM, saturation = it)
                        )
                    },
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )

                Text("Noise Reduction: ${(smartEnhance.denoise * 100).roundToInt()}%", fontSize = 12.sp)
                Slider(
                    value = smartEnhance.denoise,
                    onValueChange = {
                        viewModel.updateSmartEnhance(
                            smartEnhance.copy(enabled = true, level = SmartEnhanceLevel.CUSTOM, preset = SmartEnhancePreset.CUSTOM, denoise = it)
                        )
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun formatPlayerMillis(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
