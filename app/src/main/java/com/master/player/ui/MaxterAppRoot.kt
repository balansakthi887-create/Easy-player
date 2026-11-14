package com.maxter.player.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.maxter.player.R
import com.maxter.player.data.model.*
import com.maxter.player.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class MainNavTab(val label: String) {
    LIBRARY("Library"),
    FOLDERS("Folders"),
    HISTORY("History"),
    SETTINGS("Settings")
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxterAppRoot(
    viewModel: MaxterViewModel,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val allVideos by viewModel.allVideos.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val activeVideo by viewModel.activeVideo.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val activeDecoder by viewModel.activeDecoder.collectAsState()
    val decoderStatus by viewModel.decoderStatusMessage.collectAsState()
    val smartEnhance by viewModel.smartEnhanceConfig.collectAsState()

    var currentTab by remember { mutableStateOf(MainNavTab.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var infoDialogVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showStreamUrlDialog by remember { mutableStateOf(false) }

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted
        if (granted) {
            viewModel.refreshDeviceLibrary()
        }
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            viewModel.refreshDeviceLibrary()
        }
    }

    val filteredVideos = remember(allVideos, searchQuery, selectedSort, showFavoritesOnly) {
        allVideos
            .filter { video ->
                val matchesQuery = searchQuery.isBlank() ||
                    video.title.contains(searchQuery, ignoreCase = true) ||
                    video.folderName.contains(searchQuery, ignoreCase = true)
                val matchesFav = !showFavoritesOnly || video.isFavorite
                matchesQuery && matchesFav
            }
            .let { list ->
                when (selectedSort) {
                    SortOption.DATE_DESC -> list.sortedByDescending { it.dateAddedSec }
                    SortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase(Locale.US) }
                    SortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
                    SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                }
            }
    }

    if (activeVideo != null) {
        FullScreenVideoPlayerOverlay(
            viewModel = viewModel,
            video = activeVideo!!,
            onClose = { viewModel.closePlayer() },
            onEnterPip = onEnterPip,
            onShowVideoInfo = { infoDialogVideo = it }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.maxter_logo),
                                contentDescription = "MAXTER Logo",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, MaxterOrange, RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MAXTER PLAYER",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Your Videos. Your Control.",
                                    fontSize = 11.sp,
                                    color = MaxterOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showStreamUrlDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = "Open Network Stream",
                                tint = MaxterOrange
                            )
                        }
                        IconButton(
                            onClick = {
                                if (hasStoragePermission) {
                                    viewModel.refreshDeviceLibrary()
                                } else {
                                    permissionLauncher.launch(requiredPermission)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Library",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.LIBRARY,
                        onClick = {
                            currentTab = MainNavTab.LIBRARY
                            selectedFolder = null
                        },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = MaxterOrange,
                            indicatorColor = MaxterOrange
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.FOLDERS,
                        onClick = { currentTab = MainNavTab.FOLDERS },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                        label = { Text("Folders") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = MaxterOrange,
                            indicatorColor = MaxterOrange
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.HISTORY,
                        onClick = {
                            currentTab = MainNavTab.HISTORY
                            selectedFolder = null
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = MaxterOrange,
                            indicatorColor = MaxterOrange
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.SETTINGS,
                        onClick = {
                            currentTab = MainNavTab.SETTINGS
                            selectedFolder = null
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = MaxterOrange,
                            indicatorColor = MaxterOrange
                        )
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                StatusBannerStrip(
                    decoderMode = activeDecoder,
                    smartEnhance = smartEnhance,
                    videoCount = allVideos.size,
                    isScanning = isScanning
                )

                if (!hasStoragePermission && currentTab != MainNavTab.SETTINGS) {
                    PermissionPromptCard(
                        onGrantClick = { permissionLauncher.launch(requiredPermission) },
                        onOpenStreamClick = { showStreamUrlDialog = true }
                    )
                } else {
                    Crossfade(targetState = currentTab, label = "MainTabTransition") { tab ->
                        when (tab) {
                            MainNavTab.LIBRARY -> LibraryTabContent(
                                videos = filteredVideos,
                                continueWatching = continueWatching,
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                selectedSort = selectedSort,
                                onSortChange = { selectedSort = it },
                                showFavoritesOnly = showFavoritesOnly,
                                onToggleFavoritesOnly = { showFavoritesOnly = !showFavoritesOnly },
                                onPlayVideo = { video, queue ->
                                    viewModel.startVideoPlayback(video, queue)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onShowInfo = { infoDialogVideo = it },
                                onRescan = { viewModel.refreshDeviceLibrary() }
                            )

                            MainNavTab.FOLDERS -> FoldersTabContent(
                                allVideos = allVideos,
                                selectedFolder = selectedFolder,
                                onSelectFolder = { selectedFolder = it },
                                onPlayVideo = { video, queue ->
                                    viewModel.startVideoPlayback(video, queue)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onShowInfo = { infoDialogVideo = it }
                            )

                            MainNavTab.HISTORY -> HistoryTabContent(
                                continueWatching = continueWatching,
                                recentlyPlayed = recentlyPlayed,
                                favorites = favorites,
                                onPlayVideo = { video, queue ->
                                    viewModel.startVideoPlayback(video, queue)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onShowInfo = { infoDialogVideo = it },
                                onClearHistory = { viewModel.clearAllHistory() }
                            )

                            MainNavTab.SETTINGS -> SettingsTabContent(
                                viewModel = viewModel,
                                themeMode = themeMode,
                                onThemeChange = onThemeChange,
                                decoderStatus = decoderStatus
                            )
                        }
                    }
                }
            }
        }
    }

    if (infoDialogVideo != null) {
        VideoInformationDialog(
            video = infoDialogVideo!!,
            onDismiss = { infoDialogVideo = null },
            onPlayClick = {
                val target = infoDialogVideo!!
                infoDialogVideo = null
                viewModel.startVideoPlayback(target, allVideos)
            }
        )
    }

    if (showStreamUrlDialog) {
        NetworkStreamDialog(
            onDismiss = { showStreamUrlDialog = false },
            onPlayUrl = { urlText ->
                showStreamUrlDialog = false
                if (urlText.isNotBlank()) {
                    viewModel.playExternalUri(Uri.parse(urlText.trim()))
                }
            }
        )
    }
}

@Composable
private fun StatusBannerStrip(
    decoderMode: DecoderMode,
    smartEnhance: SmartEnhanceConfig,
    videoCount: Int,
    isScanning: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaxterOrange.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaxterOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${decoderMode.label} • Smart Enhance: ${if (smartEnhance.enabled) smartEnhance.preset.label else "OFF"}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = if (isScanning) "Scanning..." else "$videoCount Videos",
                color = MaxterOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionPromptCard(
    onGrantClick: () -> Unit,
    onOpenStreamClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaxterOrange.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaxterOrange,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Allow Video Library Access",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "MAXTER PLAYER needs media permission to automatically discover videos and folders on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxterOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Media Permission", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenStreamClick,
                    border = BorderStroke(1.dp, MaxterOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null, tint = MaxterOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Play Network Stream / URL", color = MaxterOrange)
                }
            }
        }
    }
}

@Composable
private fun LibraryTabContent(
    videos: List<VideoItem>,
    continueWatching: List<VideoItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedSort: SortOption,
    onSortChange: (SortOption) -> Unit,
    showFavoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onShowInfo: (VideoItem) -> Unit,
    onRescan: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search videos or folders...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaxterOrange) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !showFavoritesOnly,
                    onClick = { if (showFavoritesOnly) onToggleFavoritesOnly() },
                    label = { Text("All Videos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaxterOrange,
                        selectedLabelColor = Color.Black
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = showFavoritesOnly,
                    onClick = onToggleFavoritesOnly,
                    label = { Text("Favorites") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaxterOrange,
                        selectedLabelColor = Color.Black
                    )
                )
            }

            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = MaxterOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(selectedSort.label, color = MaxterOrange, fontSize = 12.sp)
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSortChange(option)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (videos.isEmpty()) {
            EmptyLibraryState(onRescan = onRescan)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (continueWatching.isNotEmpty() && searchQuery.isBlank() && !showFavoritesOnly) {
                    item {
                        Text(
                            text = "Continue Watching",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaxterOrange,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(continueWatching, key = { "cw_${it.id}" }) { cwVideo ->
                                ContinueWatchingCard(
                                    video = cwVideo,
                                    onClick = { onPlayVideo(cwVideo, continueWatching) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "All Device Videos (${videos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                items(videos, key = { it.id }) { video ->
                    VideoListItemCard(
                        video = video,
                        onPlay = { onPlayVideo(video, videos) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onShowInfo = { onShowInfo(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    video: VideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaxterOrange.copy(alpha = 0.3f)),
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(video.uri))
                        .videoFrameMillis(video.lastPositionMs.coerceAtLeast(1000L))
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "${formatMillis(video.lastPositionMs)} / ${video.formattedDuration}",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { video.progressFraction },
                color = MaxterOrange,
                trackColor = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun VideoListItemCard(
    video: VideoItem,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(video.uri))
                        .videoFrameMillis((video.durationMs / 4).coerceAtLeast(1000L))
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = MaxterOrange.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (video.progressFraction > 0.02f) {
                    LinearProgressIndicator(
                        progress = { video.progressFraction },
                        color = MaxterOrange,
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaxterOrange.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = video.resolutionLabel,
                            color = MaxterOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${video.formattedSize} • ${video.folderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (video.isFavorite) MaxterOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShowInfo) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Video Info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FoldersTabContent(
    allVideos: List<VideoItem>,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onShowInfo: (VideoItem) -> Unit
) {
    val groupedFolders = remember(allVideos) {
        allVideos.groupBy { it.folderName.ifBlank { "Internal Storage" } }
            .entries
            .sortedByDescending { it.value.size }
    }

    if (selectedFolder != null) {
        BackHandler { onSelectFolder(null) }
        val folderVideos = remember(allVideos, selectedFolder) {
            allVideos.filter { it.folderName == selectedFolder }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                IconButton(onClick = { onSelectFolder(null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to Folders", tint = MaxterOrange)
                }
                Column {
                    Text(
                        text = selectedFolder,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${folderVideos.size} videos in folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaxterOrange
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folderVideos, key = { it.id }) { video ->
                    VideoListItemCard(
                        video = video,
                        onPlay = { onPlayVideo(video, folderVideos) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onShowInfo = { onShowInfo(video) }
                    )
                }
            }
        }
    } else {
        if (groupedFolders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No video folders found on device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groupedFolders, key = { it.key }) { entry ->
                    val folderName = entry.key
                    val list = entry.value
                    val totalSizeBytes = list.sumOf { it.sizeBytes }
                    val sizeMb = totalSizeBytes.toDouble() / (1024.0 * 1024.0)
                    val sizeText = if (sizeMb >= 1024.0) {
                        String.format(Locale.US, "%.2f GB", sizeMb / 1024.0)
                    } else {
                        String.format(Locale.US, "%.1f MB", sizeMb)
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFolder(folderName) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaxterOrange.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = folderName,
                                    tint = MaxterOrange,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${list.size} videos • $sizeText",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaxterOrange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTabContent(
    continueWatching: List<VideoItem>,
    recentlyPlayed: List<VideoItem>,
    favorites: List<VideoItem>,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onShowInfo: (VideoItem) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playback History & Resume",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (recentlyPlayed.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaxterOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Clear History", color = MaxterOrange, fontSize = 12.sp)
                }
            }
        }

        if (recentlyPlayed.isEmpty() && favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaxterOrange.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No playback history yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (continueWatching.isNotEmpty()) {
                    item {
                        Text(
                            text = "Resume Where You Left Off (${continueWatching.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaxterOrange
                        )
                    }
                    items(continueWatching, key = { "hist_cw_${it.id}" }) { video ->
                        VideoListItemCard(
                            video = video,
                            onPlay = { onPlayVideo(video, continueWatching) },
                            onToggleFavorite = { onToggleFavorite(video) },
                            onShowInfo = { onShowInfo(video) }
                        )
                    }
                }

                if (recentlyPlayed.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Recently Played (${recentlyPlayed.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaxterOrange
                        )
                    }
                    items(recentlyPlayed, key = { "hist_rp_${it.id}" }) { video ->
                        VideoListItemCard(
                            video = video,
                            onPlay = { onPlayVideo(video, recentlyPlayed) },
                            onToggleFavorite = { onToggleFavorite(video) },
                            onShowInfo = { onShowInfo(video) }
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun SettingsTabContent(
    viewModel: MaxterViewModel,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    decoderStatus: String
) {
    val activeDecoder by viewModel.activeDecoder.collectAsState()
    val autoResume by viewModel.autoResumeEnabled.collectAsState()
    val gesturesEnabled by viewModel.gestureControlsEnabled.collectAsState()
    val doubleTapSec by viewModel.doubleTapSeekSeconds.collectAsState()
    val audioBoost by viewModel.audioBoostPercent.collectAsState()
    val nightMode by viewModel.nightMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Appearance & Theme",
            style = MaterialTheme.typography.titleMedium,
            color = MaxterOrange
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                AppThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaxterOrange)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = mode.label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Text(
            text = "Decoder & Hardware Engine",
            style = MaterialTheme.typography.titleMedium,
            color = MaxterOrange
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                DecoderMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDecoderMode(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeDecoder == mode,
                            onClick = { viewModel.setDecoderMode(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaxterOrange)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = mode.label,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Engine Status: $decoderStatus",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaxterOrange
                )
            }
        }

        Text(
            text = "Playback & Gesture Controls",
            style = MaterialTheme.typography.titleMedium,
            color = MaxterOrange
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Resume Playback", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Continue videos from last watched timestamp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoResume,
                        onCheckedChange = { viewModel.setAutoResumeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = MaxterOrange)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Swipe Gesture Controls", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Left swipe for Brightness, Right swipe for Volume, Horizontal drag to Seek",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = gesturesEnabled,
                        onCheckedChange = { viewModel.setGestureControlsEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = MaxterOrange)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Eye-Care Night Filter", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Warm amber GPU shader reducing blue light in dark rooms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = nightMode,
                        onCheckedChange = { viewModel.setNightMode(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = MaxterOrange)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Double-Tap Fast Seek: ${doubleTapSec}s",
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = doubleTapSec.toFloat(),
                    onValueChange = { viewModel.setDoubleTapSeekSeconds(it.roundToInt()) },
                    valueRange = 5f..30f,
                    steps = 4,
                    colors = SliderDefaults.colors(thumbColor = MaxterOrange, activeTrackColor = MaxterOrange)
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hardware Volume Boost: ${audioBoost}%",
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = audioBoost.toFloat(),
                    onValueChange = { viewModel.setAudioBoostPercent(it.roundToInt()) },
                    valueRange = 100f..200f,
                    colors = SliderDefaults.colors(thumbColor = MaxterOrange, activeTrackColor = MaxterOrange)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(onRescan: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = MaxterOrange.copy(alpha = 0.65f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No Videos Found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap Rescan to search your device storage or use the top link button to open a video stream.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRescan,
                colors = ButtonDefaults.buttonColors(containerColor = MaxterOrange, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rescan Device Storage", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenVideoPlayerOverlay(
    viewModel: MaxterViewModel,
    video: VideoItem,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onShowVideoInfo: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val exoPlayer by viewModel.playerState.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val activeDecoder by viewModel.activeDecoder.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val smartEnhance by viewModel.smartEnhanceConfig.collectAsState()
    val gesturesEnabled by viewModel.gestureControlsEnabled.collectAsState()
    val doubleTapSeconds by viewModel.doubleTapSeekSeconds.collectAsState()
    val audioBoost by viewModel.audioBoostPercent.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(video.durationMs.coerceAtLeast(1L)) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSmartEnhanceSheet by remember { mutableStateOf(false) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }

    var brightnessLevel by remember {
        val currentAttr = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (currentAttr in 0.01f..1.0f) currentAttr else 0.55f)
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.attachSubtitleUri(uri)
        }
    }

    BackHandler {
        if (isScreenLocked) {
            gestureHudText = "Screen Locked — Tap Lock Icon to Unlock"
        } else {
            if (isLandscapeFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                isLandscapeFullscreen = false
            }
            onClose()
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

    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player.duration > 0) {
                    durationMs = player.duration
                }
            }
        }
        player.addListener(listener)
        while (true) {
            currentPosMs = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) {
                durationMs = player.duration
            }
            isPlaying = player.isPlaying
            viewModel.savePlaybackProgress(video.id, currentPosMs)
            delay(500L)
        }
    }

    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(1100L)
            gestureHudText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isScreenLocked, gesturesEnabled, doubleTapSeconds) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (isScreenLocked) return@detectTapGestures
                        val player = exoPlayer ?: return@detectTapGestures
                        val deltaMs = doubleTapSeconds * 1000L
                        if (offset.x < size.width * 0.45f) {
                            val target = (player.currentPosition - deltaMs).coerceAtLeast(0L)
                            player.seekTo(target)
                            currentPosMs = target
                            gestureHudText = "⏪ -${doubleTapSeconds}s (${formatMillis(target)})"
                        } else if (offset.x > size.width * 0.55f) {
                            val maxDur = player.duration.takeIf { it > 0 } ?: durationMs
                            val target = (player.currentPosition + deltaMs).coerceAtMost(maxDur)
                            player.seekTo(target)
                            currentPosMs = target
                            gestureHudText = "⏩ +${doubleTapSeconds}s (${formatMillis(target)})"
                        } else {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    }
                )
            }
            .pointerInput(isScreenLocked, gesturesEnabled) {
                if (isScreenLocked || !gesturesEnabled) return@pointerInput
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val isLeftSide = change.position.x < size.width * 0.5f
                    val deltaFraction = -dragAmount / size.height.toFloat().coerceAtLeast(1f)
                    if (isLeftSide) {
                        brightnessLevel = (brightnessLevel + deltaFraction * 1.2f).coerceIn(0.05f, 1.0f)
                        activity?.window?.let { win ->
                            val lp = win.attributes
                            lp.screenBrightness = brightnessLevel
                            win.attributes = lp
                        }
                        gestureHudText = "☀ Brightness: ${(brightnessLevel * 100).roundToInt()}%"
                    } else {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val step = if (deltaFraction > 0.015f) 1 else if (deltaFraction < -0.015f) -1 else 0
                        if (step != 0) {
                            val newVol = (curVol + step).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            val pct = ((newVol.toFloat() / maxVol.toFloat()) * 100).roundToInt()
                            gestureHudText = "🔊 Volume: $pct%"
                        }
                    }
                }
            }
            .pointerInput(isScreenLocked, gesturesEnabled) {
                if (isScreenLocked || !gesturesEnabled) return@pointerInput
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val player = exoPlayer ?: return@detectHorizontalDragGestures
                    val seekDelta = (dragAmount * 120f).toLong()
                    val maxDur = player.duration.takeIf { it > 0 } ?: durationMs
                    val target = (player.currentPosition + seekDelta).coerceIn(0L, maxDur)
                    player.seekTo(target)
                    currentPosMs = target
                    gestureHudText = "⏱ Seek: ${formatMillis(target)} / ${formatMillis(maxDur)}"
                }
            }
    ) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        player = exoPlayer
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = when (aspectRatio) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (gestureHudText != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaxterOrange),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = gestureHudText!!,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

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
                            gestureHudText = "Controls Unlocked"
                        },
                        containerColor = MaxterOrange,
                        contentColor = Color.Black,
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
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
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
                                color = MaxterOrange,
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
                            Text(activeDecoder.label, color = MaxterOrange, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showSmartEnhanceSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Smart Enhance",
                                tint = if (smartEnhance.enabled) MaxterOrange else Color.White
                            )
                        }
                        IconButton(onClick = { onShowVideoInfo(video) }) {
                            Icon(Icons.Default.Info, contentDescription = "Video Info", tint = Color.White)
                        }
                        IconButton(onClick = onEnterPip) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                        }
                    }

                    // Center Transport Controls (Previous / Play-Pause / Next)
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
                            containerColor = MaxterOrange,
                            contentColor = Color.Black,
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

                    // Bottom Bar Controls (Timeline, Lock, Speed, Subtitles, Aspect Ratio, Fullscreen Orientation)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMillis(currentPosMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                                onValueChange = { newPos ->
                                    currentPosMs = newPos.toLong()
                                    exoPlayer?.seekTo(currentPosMs)
                                },
                                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaxterOrange,
                                    activeTrackColor = MaxterOrange,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            )
                            Text(
                                text = formatMillis(durationMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isScreenLocked = true
                                    gestureHudText = "Screen Controls Locked"
                                }
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
                            }

                            TextButton(onClick = { showSpeedDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = MaxterOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            TextButton(onClick = { showSubtitleSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitles",
                                    tint = if (subtitlesEnabled) MaxterOrange else Color.White,
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
                                    gestureHudText = "Aspect Ratio: ${next.label}"
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = MaxterOrange,
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
                                    tint = MaxterOrange
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
                                colors = RadioButtonDefaults.colors(selectedColor = MaxterOrange)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = MaxterOrange)
                }
            }
        )
    }

    if (showSubtitleSheet) {
        AlertDialog(
            onDismissRequest = { showSubtitleSheet = false },
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
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = MaxterOrange)
                        )
                    }
                    Button(
                        onClick = {
                            showSubtitleSheet = false
                            subtitlePickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaxterOrange, contentColor = Color.Black),
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
                        colors = SliderDefaults.colors(thumbColor = MaxterOrange, activeTrackColor = MaxterOrange)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleSheet = false }) {
                    Text("Done", color = MaxterOrange)
                }
            }
        )
    }

    if (showSmartEnhanceSheet) {
        AlertDialog(
            onDismissRequest = { showSmartEnhanceSheet = false },
            title = { Text("Smart Enhance GPU Shader", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Smart Enhance", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = smartEnhance.enabled,
                            onCheckedChange = {
                                viewModel.updateSmartEnhance(smartEnhance.copy(enabled = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = MaxterOrange)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Before / After Split Comparison")
                        Switch(
                            checked = smartEnhance.splitComparisonActive,
                            onCheckedChange = {
                                viewModel.updateSmartEnhance(smartEnhance.copy(splitComparisonActive = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = MaxterOrange)
                        )
                    }
                    Text("Preset Mode:", fontWeight = FontWeight.SemiBold, color = MaxterOrange)
                    SmartEnhancePreset.values().forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSmartEnhance(
                                        smartEnhance.withPreset(preset).copy(enabled = true)
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = smartEnhance.preset == preset,
                                onClick = {
                                    viewModel.updateSmartEnhance(
                                        smartEnhance.withPreset(preset).copy(enabled = true)
                                    )
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaxterOrange)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(preset.label)
                        }
                    }
                    Text("Effect Intensity: ${(smartEnhance.intensity * 100).roundToInt()}%")
                    Slider(
                        value = smartEnhance.intensity,
                        onValueChange = {
                            viewModel.updateSmartEnhance(smartEnhance.copy(intensity = it))
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = MaxterOrange, activeTrackColor = MaxterOrange)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSmartEnhanceSheet = false }) {
                    Text("Apply", color = MaxterOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun VideoInformationDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onPlayClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Video Information",
                fontWeight = FontWeight.ExtraBold,
                color = MaxterOrange
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Title", video.title)
                InfoRow("Folder", video.folderName)
                InfoRow("Resolution", "${video.width} × ${video.height} (${video.resolutionLabel})")
                InfoRow("Duration", video.formattedDuration)
                InfoRow("File Size", "${video.formattedSize} (${video.sizeBytes} bytes)")
                InfoRow("MIME Type", video.mimeType)
                InfoRow("Resume Position", formatMillis(video.lastPositionMs))
                InfoRow("Added Date", Date(video.dateAddedSec * 1000L).toString())
                InfoRow("Content URI", video.uri)
            }
        },
        confirmButton = {
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaxterOrange, contentColor = Color.Black)
            ) {
                Text("Play Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaxterOrange
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlayUrl: (String) -> Unit
) {
    var streamUrl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Network Video Stream", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Enter a direct MP4, MKV, HLS (.m3u8), or DASH (.mpd) video URL:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    placeholder = { Text("https://example.com/video.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPlayUrl(streamUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = MaxterOrange, contentColor = Color.Black)
            ) {
                Text("Play Stream", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatMillis(ms: Long): String {
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
