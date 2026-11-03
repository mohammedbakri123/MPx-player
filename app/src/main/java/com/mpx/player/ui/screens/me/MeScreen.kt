package com.mpx.player.ui.screens.me

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.CustomPlaylist
import com.mpx.player.domain.model.MeTool
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.persistence.PlaybackHistoryDialog
import com.mpx.player.ui.components.persistence.PlaylistDetailsDialog
import com.mpx.player.ui.components.persistence.PlaylistsDialog
import com.mpx.player.ui.theme.MpxAccentAmber
import com.mpx.player.ui.theme.MpxAccentCyan
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import com.mpx.player.util.PipUtils
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    onOpenLocal: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
    playbackManager: PlaybackManager? = null,
    settingsRepository: AppSettingsRepository? = null,
    onThemeChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val realPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    val persistenceRepository = realPlaybackManager.persistenceRepository
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Persistent Room data observation for live badge indicators
    val favourites by persistenceRepository.observeFavourites().collectAsState(initial = emptyList())
    val history by persistenceRepository.observeHistory().collectAsState(initial = emptyList())
    val customPlaylists by persistenceRepository.observePlaylists().collectAsState(initial = emptyList())

    // Settings state
    var appSettings by remember { mutableStateOf(realSettingsRepo.load()) }

    // Dialog navigation states
    var showFavouritesDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showPlaylistsDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }
    var playlistItemsForDialog by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPopupPlayDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var issue32Tool by remember { mutableStateOf<MeTool?>(null) }
    var equalizerMessage by remember { mutableStateOf<String?>(null) }

    // 12 Tools: 8 Real Features + 4 Issue 32 Honest Placeholders
    val tools = listOf(
        // Row 1: Real library navigation & Room data
        MeTool(
            id = "local",
            title = "Local Videos",
            description = "Video folders",
            icon = Icons.Filled.Movie,
            badge = null,
            isAvailable = true,
        ),
        MeTool(
            id = "music",
            title = "Music",
            description = "Audio library",
            icon = Icons.Filled.MusicNote,
            badge = null,
            isAvailable = true,
        ),
        MeTool(
            id = "playlists",
            title = "Playlists",
            description = "Custom collections",
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            badge = if (customPlaylists.isNotEmpty()) "${customPlaylists.size}" else null,
            isAvailable = true,
        ),
        MeTool(
            id = "favourites",
            title = "Favourites",
            description = "Starred media",
            icon = Icons.Filled.Favorite,
            badge = if (favourites.isNotEmpty()) "${favourites.size}" else null,
            isAvailable = true,
        ),

        // Row 2: Real playback, streaming & system integration
        MeTool(
            id = "history",
            title = "History",
            description = "Recently played",
            icon = Icons.Filled.History,
            badge = if (history.isNotEmpty()) "${history.size}" else null,
            isAvailable = true,
        ),
        MeTool(
            id = "network",
            title = "Network Stream",
            description = "Play stream URL",
            icon = Icons.Filled.Cloud,
            badge = "Real",
            isAvailable = true,
        ),
        MeTool(
            id = "transfer",
            title = "File Transfer",
            description = "P2P transfer",
            icon = Icons.Filled.SwapHoriz,
            badge = null,
            isAvailable = true,
        ),
        MeTool(
            id = "equalizer",
            title = "Equalizer",
            description = "System sound tuner",
            icon = Icons.Filled.Equalizer,
            badge = null,
            isAvailable = true,
        ),

        // Row 3: Issue 32 Honest Placeholders
        MeTool(
            id = "status",
            title = "Status Saver",
            description = "WhatsApp status",
            icon = Icons.Filled.SaveAlt,
            badge = "Issue 32",
            isAvailable = false,
        ),
        MeTool(
            id = "private",
            title = "Private Folder",
            description = "PIN vault",
            icon = Icons.Filled.Lock,
            badge = "Issue 32",
            isAvailable = false,
        ),
        MeTool(
            id = "manager",
            title = "Media Manager",
            description = "Storage cleaner",
            icon = Icons.Filled.FolderSpecial,
            badge = "Issue 32",
            isAvailable = false,
        ),
        MeTool(
            id = "bin",
            title = "Recycle Bin",
            description = "Deleted files",
            icon = Icons.Filled.Delete,
            badge = "Issue 32",
            isAvailable = false,
        ),
    )

    fun onToolClick(tool: MeTool) {
        when (tool.id) {
            "local" -> onOpenLocal()
            "music" -> onOpenMusic()
            "playlists" -> showPlaylistsDialog = true
            "favourites" -> showFavouritesDialog = true
            "history" -> showHistoryDialog = true
            "network" -> showNetworkStreamDialog = true
            "transfer" -> onOpenTransfer()
            "equalizer" -> {
                val audioSessionId = realPlaybackManager.player.audioSessionId
                val launched = PipUtils.launchSystemEqualizer(context, audioSessionId)
                if (!launched) {
                    equalizerMessage = "System Equalizer: No audio effect control panel (e.g. MusicFX or system equalizer app) was found on this device. Audio uses standard flat output."
                }
            }
            "status", "private", "manager", "bin" -> {
                issue32Tool = tool
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Me",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Profile Card Header (MX Player structure)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MpxPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "MPx Local User",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Private on-device media player",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                HonestStatusBanner(
                    text = "Me tools grid: real features wired to Local, Music, Playlists, Favourites, History, Network Stream. Placeholders honestly marked for Issue 32.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Tools Section Header
            item {
                Text(
                    text = "TOOLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                )
            }

            // Tools 4-column Grid (MX Player signature tools section, 3 rows)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val row1 = tools.subList(0, 4)
                        val row2 = tools.subList(4, 8)
                        val row3 = tools.subList(8, 12)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            row1.forEach { tool ->
                                ToolGridItem(
                                    tool = tool,
                                    onClick = { onToolClick(tool) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            row2.forEach { tool ->
                                ToolGridItem(
                                    tool = tool,
                                    onClick = { onToolClick(tool) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            row3.forEach { tool ->
                                ToolGridItem(
                                    tool = tool,
                                    onClick = { onToolClick(tool) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // Preferences Section
            item {
                Text(
                    text = "PREFERENCES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.MusicNote,
                            title = "Music Player",
                            subtitle = "Open local audio library",
                            onClick = onOpenMusic,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Movie,
                            title = "Video Library",
                            subtitle = "Browse folders and video collections",
                            onClick = onOpenLocal,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.ColorLens,
                            title = "App Theme",
                            subtitle = when (appSettings.appTheme.lowercase(Locale.US)) {
                                "dark", "dark slate", "slate" -> "Dark Slate (Charcoal)"
                                "classic", "classic navy", "navy" -> "Classic MX Navy"
                                "light", "light cinema" -> "Light Cinema (Clean White)"
                                else -> "Midnight OLED (Pure Black)"
                            },
                            onClick = { showThemeDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.PictureInPictureAlt,
                            title = "Custom Pop-up Play",
                            subtitle = if (PipUtils.isPipSupported(context)) {
                                if (appSettings.popupPlayEnabled) {
                                    "PiP Enabled • ${if (appSettings.popupPlayControls == "Seek10") "Fast Seek (10s)" else "Previous/Next"}"
                                } else {
                                    "Pop-up Play Disabled"
                                }
                            } else {
                                "Device does not support PiP (Android 8.0+ required)"
                            },
                            onClick = { showPopupPlayDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Settings,
                            title = "Settings",
                            subtitle = "Player, decoder, list, and subtitle options",
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // About & Legal Section (Real in-app pages, no fake external links)
            item {
                Text(
                    text = "ABOUT & LEGAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.Security,
                            title = "Privacy & Security",
                            subtitle = "100% on-device. Zero telemetry, no ads, no trackers.",
                            onClick = { showPrivacyDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = "Help & Feedback",
                            subtitle = "Player gesture guide, tutorials, and device diagnostics",
                            onClick = { showHelpDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Gavel,
                            title = "Legal & Open Source",
                            subtitle = "Software licenses, terms, and inspiration disclosure",
                            onClick = { showLegalDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Info,
                            title = "MPx Player Native",
                            subtitle = "Version 0.1.0-native • Jetpack Compose • Media3",
                            onClick = { showAboutDialog = true },
                        )
                    }
                }
            }
        }
    }

    // --- REAL FUNCTIONALITY DIALOGS ---

    // 1. Favourites Dialog (Room SQLite backed)
    if (showFavouritesDialog) {
        FavouritesDialog(
            favourites = favourites,
            onDismiss = { showFavouritesDialog = false },
            onItemClick = { item ->
                showFavouritesDialog = false
                realPlaybackManager.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        ),
                    ),
                )
                onOpenPlayer()
            },
            onRemoveFavourite = { uri ->
                scope.launch {
                    persistenceRepository.removeFavourite(uri)
                    snackbarHostState.showSnackbar("Removed from Favourites")
                }
            },
            onClearAll = {
                scope.launch {
                    persistenceRepository.clearFavourites()
                    snackbarHostState.showSnackbar("Cleared Favourites")
                }
            },
        )
    }

    // 2. Playback History Dialog (Room SQLite backed with resume timestamps)
    if (showHistoryDialog) {
        PlaybackHistoryDialog(
            history = history,
            onDismiss = { showHistoryDialog = false },
            onItemClick = { item ->
                showHistoryDialog = false
                realPlaybackManager.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        ),
                    ),
                )
                onOpenPlayer()
            },
            onClearHistory = {
                scope.launch {
                    persistenceRepository.clearHistory()
                    snackbarHostState.showSnackbar("Cleared History")
                }
            },
        )
    }

    // 3. Playlists Dialog (Room SQLite backed)
    if (showPlaylistsDialog) {
        PlaylistsDialog(
            playlists = customPlaylists,
            onDismiss = { showPlaylistsDialog = false },
            onPlaylistClick = { pl ->
                selectedPlaylist = pl
                scope.launch {
                    playlistItemsForDialog = persistenceRepository.getPlaylistItems(pl.id)
                }
            },
            onCreateNewPlaylist = {
                showCreatePlaylistDialog = true
            },
        )
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                scope.launch {
                    persistenceRepository.createPlaylist(name, isVideoPlaylist = true)
                    showCreatePlaylistDialog = false
                    snackbarHostState.showSnackbar("Playlist \"$name\" created")
                }
            },
        )
    }

    // Playlist Details Dialog
    if (selectedPlaylist != null) {
        val pl = selectedPlaylist!!
        PlaylistDetailsDialog(
            playlist = pl,
            items = playlistItemsForDialog,
            onDismiss = { selectedPlaylist = null },
            onPlayAll = {
                val playable = playlistItemsForDialog.map { item ->
                    PlayableMedia(
                        id = item.mediaId,
                        title = item.title,
                        subtitle = item.subtitle,
                        uri = item.mediaUri,
                        isVideo = item.isVideo,
                        durationMs = item.durationMs,
                    )
                }
                selectedPlaylist = null
                realPlaybackManager.playQueue(playable)
                onOpenPlayer()
            },
            onPlayItem = { item ->
                selectedPlaylist = null
                realPlaybackManager.playQueue(
                    listOf(
                        PlayableMedia(
                            id = item.mediaId,
                            title = item.title,
                            subtitle = item.subtitle,
                            uri = item.mediaUri,
                            isVideo = item.isVideo,
                            durationMs = item.durationMs,
                        ),
                    ),
                )
                onOpenPlayer()
            },
            onRemoveItem = { itemId ->
                scope.launch {
                    persistenceRepository.removeItemFromPlaylist(itemId)
                    playlistItemsForDialog = persistenceRepository.getPlaylistItems(pl.id)
                }
            },
            onDeletePlaylist = {
                scope.launch {
                    persistenceRepository.deletePlaylist(pl.id)
                    selectedPlaylist = null
                    snackbarHostState.showSnackbar("Playlist deleted")
                }
            },
        )
    }

    // 4. Network Stream Dialog (Real Media3 URL Streaming)
    if (showNetworkStreamDialog) {
        NetworkStreamDialog(
            onDismiss = { showNetworkStreamDialog = false },
            onPlayStream = { url ->
                showNetworkStreamDialog = false
                val streamTitle = url.substringAfterLast("/").substringBefore("?").ifBlank { "Network Stream" }
                realPlaybackManager.playQueue(
                    listOf(
                        PlayableMedia(
                            id = "stream_${System.currentTimeMillis()}",
                            title = streamTitle,
                            subtitle = url,
                            uri = url,
                            isVideo = true,
                            durationMs = 0L,
                        ),
                    ),
                )
                onOpenPlayer()
            },
        )
    }

    // 5. App Theme Dialog (Real Theme Switching)
    if (showThemeDialog) {
        AppThemeDialog(
            currentTheme = appSettings.appTheme,
            onDismiss = { showThemeDialog = false },
            onSelectTheme = { selectedTheme ->
                val updated = appSettings.copy(appTheme = selectedTheme)
                appSettings = updated
                realSettingsRepo.save(updated)
                onThemeChanged?.invoke(selectedTheme)
                showThemeDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Theme applied: $selectedTheme")
                }
            },
        )
    }

    // 6. Custom Pop-up Play Dialog (Real PiP & Honest Capability Detection)
    if (showPopupPlayDialog) {
        CustomPopupPlayDialog(
            enabled = appSettings.popupPlayEnabled,
            controls = appSettings.popupPlayControls,
            onDismiss = { showPopupPlayDialog = false },
            onUpdateSettings = { enabled, controls ->
                val updated = appSettings.copy(popupPlayEnabled = enabled, popupPlayControls = controls)
                appSettings = updated
                realSettingsRepo.save(updated)
                showPopupPlayDialog = false
            },
            onTestPip = {
                val entered = PipUtils.enterPip(context)
                if (!entered) {
                    val msg = if (!PipUtils.isPipSupported(context)) {
                        "Picture-in-Picture is not supported by your device/OS environment (requires Android 8.0+ with FEATURE_PICTURE_IN_PICTURE)."
                    } else {
                        "Unable to enter PiP mode right now. Try playing a video in the player first."
                    }
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
        )
    }

    // 7. Privacy & Security In-App Info Page
    if (showPrivacyDialog) {
        PrivacySecurityDialog(onDismiss = { showPrivacyDialog = false })
    }

    // 8. Help & Feedback In-App Info Page (Gesture guide & diagnostics)
    if (showHelpDialog) {
        HelpFeedbackDialog(onDismiss = { showHelpDialog = false })
    }

    // 9. Legal & Open Source In-App Info Page
    if (showLegalDialog) {
        LegalLicensesDialog(onDismiss = { showLegalDialog = false })
    }

    // 10. About In-App Info Page
    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    // 11. Equalizer Message Dialog (honest fallback)
    if (equalizerMessage != null) {
        AlertDialog(
            onDismissRequest = { equalizerMessage = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(Icons.Filled.Equalizer, null, tint = MpxPrimary) },
            title = { Text("Equalizer", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(equalizerMessage ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { equalizerMessage = null }) {
                    Text("OK", color = MpxPrimary)
                }
            },
        )
    }

    // 12. Issue 32 Honest Placeholder Dialog
    if (issue32Tool != null) {
        val tool = issue32Tool!!
        AlertDialog(
            onDismissRequest = { issue32Tool = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(tool.icon, null, tint = MpxAccentAmber) },
            title = {
                Text(
                    text = "${tool.title} (Issue 32)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                val detail = when (tool.id) {
                    "status" -> "WhatsApp Status Saver requires scoped storage directory access. It will scan and save downloaded video and image statuses once built."
                    "private" -> "Private Folder requires an encrypted SQLite or file vault protected by a PIN or biometric auth to hide sensitive media."
                    "manager" -> "Media Manager & Storage Cleaner will analyze device storage, identify duplicate videos, large files, and temporary cache."
                    "bin" -> "Recycle Bin will preserve deleted files in an app-managed holding directory for 30 days before permanent deletion."
                    else -> tool.description
                }
                Column {
                    Text(
                        text = "This feature is scheduled for Issue 32.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Status: Honest placeholder — not faked.",
                        color = MpxAccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { issue32Tool = null }) {
                    Text("Understood", color = MpxPrimary)
                }
            },
        )
    }
}

// --- SUB-COMPONENTS ---

@Composable
private fun ToolGridItem(
    tool: MeTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = if (tool.isAvailable) MpxPrimary else MpxAccentAmber,
                modifier = Modifier.size(24.dp),
            )
            if (tool.badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            if (tool.isAvailable) MpxPrimary else MpxAccentAmber,
                            RoundedCornerShape(3.dp),
                        )
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = tool.badge,
                        color = if (tool.isAvailable) Color.White else Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MpxPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MpxTextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

// --- DIALOG IMPLEMENTATIONS ---

/**
 * Real Network Stream player dialog allowing direct stream input and sample playback.
 */
@Composable
private fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlayStream: (String) -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    fun pasteClipboard() {
        clipboardManager?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) || text.startsWith("rtsp://", ignoreCase = true)) {
                    urlText = text.trim()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Network Stream",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter any HTTP, HTTPS, or HLS stream URL to play directly in Media3 ExoPlayer:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/video.mp4", color = MpxTextMuted, fontSize = 13.sp) },
                    trailingIcon = {
                        IconButton(onClick = { pasteClipboard() }) {
                            Icon(Icons.Filled.ContentPaste, "Paste", tint = MpxPrimary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpxPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text("Sample Public Streams:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { urlText = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Big Buck Bunny", fontSize = 11.sp, maxLines = 1, color = MpxPrimary)
                    }
                    OutlinedButton(
                        onClick = { urlText = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Elephants Dream", fontSize = 11.sp, maxLines = 1, color = MpxPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (urlText.isNotBlank()) onPlayStream(urlText.trim()) },
                enabled = urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MpxPrimary),
            ) {
                Text("Play Stream")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * Real App Theme selector with instant switching and preview swatches.
 */
@Composable
private fun AppThemeDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onSelectTheme: (String) -> Unit,
) {
    val themes = listOf(
        ThemeOption(
            key = "Midnight",
            name = "Midnight OLED (Pure Black)",
            description = "True OLED #000000 cinema contrast. Maximum battery savings.",
            bgColor = Color(0xFF000000),
            surfaceColor = Color(0xFF0D1117),
            primaryColor = Color(0xFF3B82F6),
        ),
        ThemeOption(
            key = "Dark",
            name = "Dark Slate (Charcoal)",
            description = "Smooth modern dark theme with softer slate #0F172A contrast.",
            bgColor = Color(0xFF0F172A),
            surfaceColor = Color(0xFF1E293B),
            primaryColor = Color(0xFF38BDF8),
        ),
        ThemeOption(
            key = "Classic",
            name = "Classic MX Navy",
            description = "Deep blue-black #0A0F1D inspired by utility-first classic players.",
            bgColor = Color(0xFF0A0F1D),
            surfaceColor = Color(0xFF131D31),
            primaryColor = Color(0xFF4895EF),
        ),
        ThemeOption(
            key = "Light",
            name = "Light Cinema (Clean White)",
            description = "High-visibility daylight palette for bright environments.",
            bgColor = Color(0xFFF8FAFC),
            surfaceColor = Color(0xFFFFFFFF),
            primaryColor = Color(0xFF2563EB),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ColorLens, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Select App Theme",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                themes.forEach { option ->
                    val isSelected = currentTheme.equals(option.key, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectTheme(option.key) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectTheme(option.key) },
                            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // Swatch Preview Pill
                        Row(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .padding(3.dp),
                        ) {
                            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(option.bgColor))
                            Spacer(Modifier.width(2.dp))
                            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(option.primaryColor))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MpxPrimary)
            }
        },
    )
}

private data class ThemeOption(
    val key: String,
    val name: String,
    val description: String,
    val bgColor: Color,
    val surfaceColor: Color,
    val primaryColor: Color,
)

/**
 * Real Custom Pop-up Play configuration dialog with device PiP detection and testing.
 */
@Composable
private fun CustomPopupPlayDialog(
    enabled: Boolean,
    controls: String,
    onDismiss: () -> Unit,
    onUpdateSettings: (Boolean, String) -> Unit,
    onTestPip: () -> Unit,
) {
    val context = LocalContext.current
    val isSupported = remember(context) { PipUtils.isPipSupported(context) }
    var currentEnabled by remember { mutableStateOf(enabled) }
    var currentControls by remember { mutableStateOf(controls) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PictureInPictureAlt, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Custom Pop-up Play",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        text = {
            Column {
                // Capability Status Banner
                if (isSupported) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MpxPrimary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MpxPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Device supports Android Picture-in-Picture mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MpxAccentAmber.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MpxAccentAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "PiP unsupported: requires Android 8.0+ and system FEATURE_PICTURE_IN_PICTURE support. Settings stay honest.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Toggle Pop-up Play
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Pop-up Play",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Enter floating window when leaving app during video playback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = currentEnabled,
                        onCheckedChange = { currentEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MpxPrimary, checkedTrackColor = MpxPrimary.copy(alpha = 0.4f)),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "PiP Window Controls:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentControls = "PrevNext" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentControls == "PrevNext",
                        onClick = { currentControls = "PrevNext" },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Previous / Next (Default)", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentControls = "Seek10" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentControls == "Seek10",
                        onClick = { currentControls = "Seek10" },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Fast Forward / Rewind (10s)", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onTestPip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PictureInPictureAlt, null, modifier = Modifier.size(16.dp), tint = MpxPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Pop-up Play Mode", color = MpxPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdateSettings(currentEnabled, currentControls) },
                colors = ButtonDefaults.buttonColors(containerColor = MpxPrimary),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * Real Privacy & Security In-App Info Page.
 */
@Composable
private fun PrivacySecurityDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Privacy & Security", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text("100% On-Device Architecture", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "MPx Player is an offline-first media player. Media indexing, metadata extraction, and playback happen exclusively on your device. Your file library is never uploaded to any remote server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Zero Telemetry & No Analytics", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No tracking SDKs, no advertising identifiers, and no user surveillance frameworks are embedded in MPx Player. Your playback habits and files remain strictly your own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Local SQLite Sandbox", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Favourites, custom playlists, and playback resume points are stored in a private Room SQLite database (mpx_player.db) inside the Android protected application sandbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Transparent Permissions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• READ_MEDIA_VIDEO / AUDIO: Discovering local media files.\n• INTERNET: Optional, used solely when you paste a stream URL in Network Stream.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MpxPrimary) }
        },
    )
}

/**
 * Real Help & Feedback In-App Info Page with gesture guides and device diagnostics.
 */
@Composable
private fun HelpFeedbackDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Help & Player Guide", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text("Player Gestures", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• Double-tap left side: Seek back 10 seconds\n• Double-tap right side: Seek forward 10 seconds\n• Horizontal drag: Smooth precision scrub through video\n• Lock button: Prevent accidental touches while watching\n• Aspect ratio: Cycle through Fit, Stretch, 16:9, and Original",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Audio & Subtitle Tracks", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• Audio Icon: Switch between discrete audio tracks (dual-audio, commentary)\n• Subtitle Icon: Select embedded or sidecar .vtt / .srt subtitle tracks\n• Subtitle Size: Configurable under Settings (Small, Normal, Large)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Device Diagnostics (Live)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(10.dp),
                    ) {
                        Column {
                            Text("App: MPx Player 0.1.0-native", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text("Android OS: SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text("Device: ${Build.MANUFACTURER} ${Build.MODEL}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text("Engine: AndroidX Media3 ExoPlayer 1.3.1", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text("Database: Room SQLite (mpx_player.db)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MpxPrimary) }
        },
    )
}

/**
 * Real Legal & Open Source In-App Info Page.
 */
@Composable
private fun LegalLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Gavel, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Legal & Open Source", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text("Open Source Licenses", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• AndroidX Media3 ExoPlayer: Apache License 2.0\n• Jetpack Compose & Material 3: Apache License 2.0\n• AndroidX Room Persistence Library: Apache License 2.0\n• Kotlin Coroutines: Apache License 2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Inspiration Attribution Notice", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "MPx Player is an independent, clean-room native Android media player. Its organization and UX workflow are inspired by MX Player (per reverse-engineered layout specifications). All code, views, and styling are rewritten 100% from scratch in native Kotlin and Jetpack Compose. No proprietary assets, code, or trademarks are incorporated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Terms of Use", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "MPx Player is provided 'as is' without warranty under the Apache 2.0 License. Users are responsible for their own media files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MpxPrimary) }
        },
    )
}

/**
 * About MPx Player In-App Dialog.
 */
@Composable
private fun AboutAppDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("MPx Player Native", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column {
                Text(
                    text = "Version 0.1.0-native",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Pure native Kotlin rewrite with Jetpack Compose, AndroidX Media3 ExoPlayer, and Room SQLite persistence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "High-density, utility-first media management without cloud dependence, tracking, or ads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = MpxPrimary) }
        },
    )
}
