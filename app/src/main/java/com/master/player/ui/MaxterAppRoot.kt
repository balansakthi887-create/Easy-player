package com.maxter.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxter.player.data.model.*
import com.maxter.player.ui.theme.AppThemeMode
import com.maxter.player.ui.theme.MaxterOrange

enum class MainNavTab { HOME, FOLDERS, PLAYLISTS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxterAppRoot(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onEnterPip: () -> Unit
) {
    var currentTab by remember { mutableStateOf(MainNavTab.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var activeVideo by remember { mutableStateOf<VideoItem?>(null) }
    var smartEnhance by remember { mutableStateOf(SmartEnhanceConfig()) }
    var decoderMode by remember { mutableStateOf(DecoderMode.HW_PLUS) }
    var aspectRatio by remember { mutableStateOf(AspectRatioMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showSmartEnhanceSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaxterOrange,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "MAXTER Logo",
                                tint = Color.Black,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MAXTER PLAYER", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("Your Videos. Your Control.", fontSize = 11.sp, color = MaxterOrange)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentTab == MainNavTab.HOME,
                    onClick = { currentTab = MainNavTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == MainNavTab.FOLDERS,
                    onClick = { currentTab = MainNavTab.FOLDERS },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                    label = { Text("Folders") }
                )
                NavigationBarItem(
                    selected = currentTab == MainNavTab.PLAYLISTS,
                    onClick = { currentTab = MainNavTab.PLAYLISTS },
                    icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlists") },
                    label = { Text("Playlists") }
                )
                NavigationBarItem(
                    selected = currentTab == MainNavTab.SETTINGS,
                    onClick = { currentTab = MainNavTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search videos, folders, or paste stream URL...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaxterOrange) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Active Decoder: ${decoderMode.label} • Smart Enhance: ${if (smartEnhance.enabled) smartEnhance.preset.label else "OFF"}",
                color = MaxterOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
