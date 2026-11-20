package com.maxter.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Scale
import com.maxter.player.R
import com.maxter.player.data.model.*
import com.maxter.player.ui.theme.MaxterTheme
import com.maxter.player.ui.theme.ThemeAppearanceStudioSection
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
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val theme = MaxterTheme.colors

    val allVideos by viewModel.allVideos.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val activeVideo by viewModel.activeVideo.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val activeDecoder by viewModel.activeDecoder.collectAsState()
    val decoderStatus by viewModel.decoderStatusMessage.collectAsState()
    val smartEnhance by viewModel.smartEnhanceConfig.collectAsState()
    val isGridLayout by viewModel.isGridLayout.collectAsState()
    val selectedSort by viewModel.sortOption.collectAsState()

    var currentTab by remember { mutableStateOf(MainNavTab.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = theme.background,
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
                                    .border(1.dp, theme.primary, RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MAXTER PLAYER",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = theme.text
                                )
                                Text(
                                    text = "Your Videos. Your Control.",
                                    fontSize = 11.sp,
                                    color = theme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleGridLayout() }) {
                            Icon(
                                imageVector = if (isGridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle Grid/List Layout",
                                tint = theme.buttonIcon
                            )
                        }
                        IconButton(onClick = { showStreamUrlDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = "Open Network Stream",
                                tint = theme.accent
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
                                tint = theme.buttonIcon
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = theme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = theme.navigationBar,
                    tonalElevation = 8.dp
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = theme.onPrimary,
                        selectedTextColor = theme.primary,
                        indicatorColor = theme.primary,
                        unselectedIconColor = theme.buttonIcon.copy(alpha = 0.65f),
                        unselectedTextColor = theme.mutedText
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.LIBRARY,
                        onClick = {
                            currentTab = MainNavTab.LIBRARY
                            selectedFolder = null
                        },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                        label = { Text("Library") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.FOLDERS,
                        onClick = { currentTab = MainNavTab.FOLDERS },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                        label = { Text("Folders") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.HISTORY,
                        onClick = {
                            currentTab = MainNavTab.HISTORY
                            selectedFolder = null
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.SETTINGS,
                        onClick = {
                            currentTab = MainNavTab.SETTINGS
                            selectedFolder = null
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = navColors
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
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
                                onSortChange = { viewModel.setSortOption(it) },
                                showFavoritesOnly = showFavoritesOnly,
                                onToggleFavoritesOnly = { showFavoritesOnly = !showFavoritesOnly },
                                isGridLayout = isGridLayout,
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
                                isGridLayout = isGridLayout,
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
                                decoderStatus = decoderStatus
                            )
                        }
                    }
                }
            }
        }

        // Smooth opening/closing animation for FullScreenVideoPlayerOverlay
        AnimatedVisibility(
            visible = activeVideo != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 12 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 12 })
        ) {
            activeVideo?.let { video ->
                FullScreenVideoPlayerOverlay(
                    viewModel = viewModel,
                    video = video,
                    onClose = { viewModel.closePlayer() },
                    onEnterPip = onEnterPip,
                    onShowVideoInfo = { infoDialogVideo = it }
                )
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
    val theme = MaxterTheme.colors
    Surface(
        color = theme.cardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.28f))
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
                    tint = theme.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                val seText = if (smartEnhance.enabled) {
                    "${smartEnhance.level.label} (${smartEnhance.preset.label})"
                } else {
                    "OFF"
                }
                Text(
                    text = "${decoderMode.label} • Smart Enhance: $seText",
                    color = theme.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (isScanning) "Scanning..." else "$videoCount Videos",
                color = theme.highlight,
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
    val theme = MaxterTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = theme.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Allow Video Library Access",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "MAXTER PLAYER needs media permission to automatically discover videos and folders on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.mutedText
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
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
                    border = BorderStroke(1.dp, theme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null, tint = theme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Play Network Stream / URL", color = theme.primary)
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
    isGridLayout: Boolean,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onShowInfo: (VideoItem) -> Unit,
    onRescan: () -> Unit
) {
    val theme = MaxterTheme.colors
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
            placeholder = { Text("Search videos or folders...", color = theme.mutedText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = theme.buttonIcon)
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
                        selectedContainerColor = theme.primary,
                        selectedLabelColor = theme.onPrimary
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
                        selectedContainerColor = theme.primary,
                        selectedLabelColor = theme.onPrimary
                    )
                )
            }

            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(selectedSort.label, color = theme.accent, fontSize = 12.sp)
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
        } else if (isGridLayout) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoGridItemCard(
                        video = video,
                        onPlay = { onPlayVideo(video, videos) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onShowInfo = { onShowInfo(video) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (continueWatching.isNotEmpty() && searchQuery.isBlank() && !showFavoritesOnly) {
                    item(key = "continue_watching_header") {
                        Text(
                            text = "Continue Watching",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.primary,
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
                            color = theme.text
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
    val theme = MaxterTheme.colors
    val imageRequest = remember(video.id, video.lastPositionMs) {
        ImageRequest.Builder(context)
            .data(Uri.parse(video.uri))
            .size(360, 200)
            .scale(Scale.FILL)
            .videoFrameMillis(video.lastPositionMs.coerceAtLeast(1000L))
            .crossfade(true)
            .build()
    }
    Surface(
        color = theme.cardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.3f)),
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
                    model = imageRequest,
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
                color = theme.progressBar,
                trackColor = theme.mutedText.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.mutedText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun VideoGridItemCard(
    video: VideoItem,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current
    val theme = MaxterTheme.colors
    val imageRequest = remember(video.id, video.durationMs) {
        ImageRequest.Builder(context)
            .data(Uri.parse(video.uri))
            .size(320, 180)
            .scale(Scale.FILL)
            .videoFrameMillis((video.durationMs / 4).coerceAtLeast(1000L))
            .crossfade(true)
            .build()
    }
    Surface(
        color = theme.cardSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = theme.primary.copy(alpha = 0.92f),
                    modifier = Modifier.size(30.dp)
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
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
                        color = theme.progressBar,
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    color = theme.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${video.resolutionLabel} • ${video.formattedSize}",
                        color = theme.highlight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (video.isFavorite) theme.primary else theme.mutedText,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onToggleFavorite)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = theme.mutedText,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onShowInfo)
                    )
                }
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
    val theme = MaxterTheme.colors
    val imageRequest = remember(video.id, video.durationMs) {
        ImageRequest.Builder(context)
            .data(Uri.parse(video.uri))
            .size(320, 180)
            .scale(Scale.FILL)
            .videoFrameMillis((video.durationMs / 4).coerceAtLeast(1000L))
            .crossfade(true)
            .build()
    }
    Surface(
        color = theme.cardSurface,
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
                    model = imageRequest,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = theme.primary.copy(alpha = 0.9f),
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
                        color = theme.progressBar,
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
                    color = theme.text
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = theme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = video.resolutionLabel,
                            color = theme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${video.formattedSize} • ${video.folderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (video.isFavorite) theme.primary else theme.mutedText
                )
            }
            IconButton(onClick = onShowInfo) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Video Info",
                    tint = theme.mutedText
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
    isGridLayout: Boolean,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onShowInfo: (VideoItem) -> Unit
) {
    val theme = MaxterTheme.colors
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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to Folders", tint = theme.primary)
                }
                Column {
                    Text(
                        text = selectedFolder,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text
                    )
                    Text(
                        text = "${folderVideos.size} videos in folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.primary
                    )
                }
            }
            if (isGridLayout) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(folderVideos, key = { it.id }) { video ->
                        VideoGridItemCard(
                            video = video,
                            onPlay = { onPlayVideo(video, folderVideos) },
                            onToggleFavorite = { onToggleFavorite(video) },
                            onShowInfo = { onShowInfo(video) }
                        )
                    }
                }
            } else {
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
        }
    } else {
        if (groupedFolders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No video folders found on device.", color = theme.mutedText)
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
                        color = theme.cardSurface,
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
                                color = theme.primary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = folderName,
                                    tint = theme.primary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.text
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${list.size} videos • $sizeText",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.mutedText
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = theme.accent
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
    val theme = MaxterTheme.colors
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
                color = theme.text
            )
            if (recentlyPlayed.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Clear History", color = theme.primary, fontSize = 12.sp)
                }
            }
        }

        if (recentlyPlayed.isEmpty() && favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = theme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No playback history yet.",
                        color = theme.mutedText
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
                            color = theme.primary
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
                            color = theme.primary
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
    decoderStatus: String
) {
    val theme = MaxterTheme.colors
    val activeDecoder by viewModel.activeDecoder.collectAsState()
    val autoResume by viewModel.autoResumeEnabled.collectAsState()
    val gestureSettings by viewModel.gestureSettings.collectAsState()
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
            text = "Appearance & Colour Customization",
            style = MaterialTheme.typography.titleMedium,
            color = theme.primary
        )
        ThemeAppearanceStudioSection(
            themeConfig = theme,
            onSelectPreset = { viewModel.selectThemePreset(it) },
            onSelectBaseMode = { viewModel.selectThemeBaseMode(it) },
            onUpdateSlotColor = { slot, color -> viewModel.updateThemeColorSlot(slot, color) },
            onRestoreDefault = { viewModel.restoreDefaultTheme() }
        )

        Text(
            text = "Decoder & Hardware Engine",
            style = MaterialTheme.typography.titleMedium,
            color = theme.primary
        )
        Surface(
            color = theme.cardSurface,
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
                            colors = RadioButtonDefaults.colors(selectedColor = theme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = mode.label,
                                fontWeight = FontWeight.Bold,
                                color = theme.text
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.mutedText
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Engine Status: $decoderStatus",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent
                )
            }
        }

        Text(
            text = "Playback & Gesture Controls",
            style = MaterialTheme.typography.titleMedium,
            color = theme.primary
        )
        Surface(
            color = theme.cardSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSwitchRow(
                    title = "Auto-Resume Playback",
                    subtitle = "Continue videos from last watched timestamp",
                    checked = autoResume,
                    onCheckedChange = { viewModel.setAutoResumeEnabled(it) }
                )
                SettingsSwitchRow(
                    title = "Enable All Player Gestures",
                    subtitle = "Master switch for swipe, double-tap, and long-press",
                    checked = gestureSettings.gesturesEnabled,
                    onCheckedChange = {
                        viewModel.updateGestureSettings(gestureSettings.copy(gesturesEnabled = it))
                    }
                )
                SettingsSwitchRow(
                    title = "Left Vertical Swipe (Brightness)",
                    subtitle = "Adjust screen brightness smoothly on left half",
                    checked = gestureSettings.brightnessGestureEnabled,
                    onCheckedChange = {
                        viewModel.updateGestureSettings(gestureSettings.copy(brightnessGestureEnabled = it))
                    }
                )
                SettingsSwitchRow(
                    title = "Right Vertical Swipe (Volume)",
                    subtitle = "Adjust media volume smoothly on right half",
                    checked = gestureSettings.volumeGestureEnabled,
                    onCheckedChange = {
                        viewModel.updateGestureSettings(gestureSettings.copy(volumeGestureEnabled = it))
                    }
                )
                SettingsSwitchRow(
                    title = "Horizontal Swipe to Seek",
                    subtitle = "Preview timestamp while dragging, commit on release",
                    checked = gestureSettings.seekGestureEnabled,
                    onCheckedChange = {
                        viewModel.updateGestureSettings(gestureSettings.copy(seekGestureEnabled = it))
                    }
                )
                SettingsSwitchRow(
                    title = "Double-Tap Seek / Play-Pause",
                    subtitle = "Tap left/right to seek, center to play/pause",
                    checked = gestureSettings.doubleTapSeekEnabled,
                    onCheckedChange = {
                        viewModel.updateGestureSettings(gestureSettings.copy(doubleTapSeekEnabled = it))
                    }
                )
                SettingsSwitchRow(
                    title = "Long-Press Fast Speed",
                    subtitle = "Hold anywhere to play at ${gestureSettings.longPressSpeed}x speed",
                    checked = gestureSettings.longPressSpeedEnabled,
                    onCheckedChange = {
                        viewModel.updateGestureSettings(gestureSettings.copy(longPressSpeedEnabled = it))
                    }
                )
                SettingsSwitchRow(
                    title = "Eye-Care Night Filter",
                    subtitle = "Warm amber GPU shader reducing blue light in dark rooms",
                    checked = nightMode,
                    onCheckedChange = { viewModel.setNightMode(it) }
                )

                Text(
                    text = "Double-Tap Fast Seek: ${gestureSettings.doubleTapSeekSeconds}s",
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text
                )
                Slider(
                    value = gestureSettings.doubleTapSeekSeconds.toFloat(),
                    onValueChange = {
                        viewModel.updateGestureSettings(gestureSettings.copy(doubleTapSeekSeconds = it.roundToInt()))
                    },
                    valueRange = 5f..30f,
                    steps = 4,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )

                Text(
                    text = "Long-Press Speed: ${gestureSettings.longPressSpeed}x",
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text
                )
                Slider(
                    value = gestureSettings.longPressSpeed,
                    onValueChange = {
                        val snapped = (it * 4f).roundToInt() / 4f
                        viewModel.updateGestureSettings(gestureSettings.copy(longPressSpeed = snapped))
                    },
                    valueRange = 1.25f..3.0f,
                    steps = 6,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )

                Text(
                    text = "Hardware Volume Boost: ${audioBoost}%",
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text
                )
                Slider(
                    value = audioBoost.toFloat(),
                    onValueChange = { viewModel.setAudioBoostPercent(it.roundToInt()) },
                    valueRange = 100f..200f,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.progressBar)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = MaxterTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = theme.text)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.mutedText
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.onPrimary,
                checkedTrackColor = theme.primary
            )
        )
    }
}

@Composable
private fun EmptyLibraryState(onRescan: () -> Unit) {
    val theme = MaxterTheme.colors
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
                tint = theme.primary.copy(alpha = 0.65f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No Videos Found",
                style = MaterialTheme.typography.titleMedium,
                color = theme.text
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap Rescan to search your device storage or use the top link button to open a video stream.",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.mutedText
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRescan,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rescan Device Storage", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VideoInformationDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onPlayClick: () -> Unit
) {
    val theme = MaxterTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        titleContentColor = theme.text,
        textContentColor = theme.text,
        title = {
            Text(
                text = "Video Information",
                fontWeight = FontWeight.ExtraBold,
                color = theme.primary
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
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
            ) {
                Text("Play Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = theme.text)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val theme = MaxterTheme.colors
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.text
        )
    }
}

@Composable
private fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlayUrl: (String) -> Unit
) {
    val theme = MaxterTheme.colors
    var streamUrl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        titleContentColor = theme.text,
        textContentColor = theme.text,
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
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
            ) {
                Text("Play Stream", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.text)
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
