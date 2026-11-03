package com.mpx.player.ui.screens.me

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material3.AlertDialogDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
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
import com.mpx.player.util.LocaleHelper
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
    onOpenAboutBeta: (() -> Unit)? = null,
    playbackManager: PlaybackManager? = null,
    settingsRepository: AppSettingsRepository? = null,
    onThemeChanged: ((String) -> Unit)? = null,
    currentLanguage: String = "system",
    onLanguageChanged: ((String) -> Unit)? = null,
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
    val activeLanguage = currentLanguage.ifBlank { appSettings.appLanguage }

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
    var showLanguageDialog by remember { mutableStateOf(false) }

    var issue32Tool by remember { mutableStateOf<MeTool?>(null) }
    var equalizerMessage by remember { mutableStateOf<String?>(null) }

    // 12 Tools: 8 Real Features + 4 Issue 32 Honest Placeholders
    val tools = listOf(
        // Row 1: Real library navigation & Room data
        MeTool(
            id = "local",
            title = stringResource(R.string.tool_local_title),
            description = stringResource(R.string.tool_local_desc),
            icon = Icons.Filled.Movie,
            badge = null,
            isAvailable = true,
        ),
        MeTool(
            id = "music",
            title = stringResource(R.string.tool_music_title),
            description = stringResource(R.string.tool_music_desc),
            icon = Icons.Filled.MusicNote,
            badge = null,
            isAvailable = true,
        ),
        MeTool(
            id = "playlists",
            title = stringResource(R.string.tool_playlists_title),
            description = stringResource(R.string.tool_playlists_desc),
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            badge = if (customPlaylists.isNotEmpty()) "${customPlaylists.size}" else null,
            isAvailable = true,
        ),
        MeTool(
            id = "favourites",
            title = stringResource(R.string.tool_favourites_title),
            description = stringResource(R.string.tool_favourites_desc),
            icon = Icons.Filled.Favorite,
            badge = if (favourites.isNotEmpty()) "${favourites.size}" else null,
            isAvailable = true,
        ),

        // Row 2: Real playback, streaming & system integration
        MeTool(
            id = "history",
            title = stringResource(R.string.tool_history_title),
            description = stringResource(R.string.tool_history_desc),
            icon = Icons.Filled.History,
            badge = if (history.isNotEmpty()) "${history.size}" else null,
            isAvailable = true,
        ),
        MeTool(
            id = "network",
            title = stringResource(R.string.tool_network_title),
            description = stringResource(R.string.tool_network_desc),
            icon = Icons.Filled.Cloud,
            badge = "Real",
            isAvailable = true,
        ),
        MeTool(
            id = "transfer",
            title = stringResource(R.string.tool_transfer_title),
            description = stringResource(R.string.tool_transfer_desc),
            icon = Icons.Filled.SwapHoriz,
            badge = null,
            isAvailable = true,
        ),
        MeTool(
            id = "equalizer",
            title = stringResource(R.string.tool_equalizer_title),
            description = stringResource(R.string.tool_equalizer_desc),
            icon = Icons.Filled.Equalizer,
            badge = null,
            isAvailable = true,
        ),

        // Row 3: Issue 32 Honest Placeholders
        MeTool(
            id = "status",
            title = stringResource(R.string.tool_status_title),
            description = stringResource(R.string.tool_status_desc),
            icon = Icons.Filled.SaveAlt,
            badge = "Issue 32",
            isAvailable = false,
        ),
        MeTool(
            id = "private",
            title = stringResource(R.string.tool_private_title),
            description = stringResource(R.string.tool_private_desc),
            icon = Icons.Filled.Lock,
            badge = "Issue 32",
            isAvailable = false,
        ),
        MeTool(
            id = "manager",
            title = stringResource(R.string.tool_manager_title),
            description = stringResource(R.string.tool_manager_desc),
            icon = Icons.Filled.FolderSpecial,
            badge = "Issue 32",
            isAvailable = false,
        ),
        MeTool(
            id = "bin",
            title = stringResource(R.string.tool_bin_title),
            description = stringResource(R.string.tool_bin_desc),
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
                    equalizerMessage = context.getString(R.string.equalizer_dialog_msg)
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
                        text = stringResource(R.string.me_title),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
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
                                text = stringResource(R.string.me_profile_user),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.me_profile_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                HonestStatusBanner(
                    text = stringResource(R.string.me_status_banner),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Tools Section Header
            item {
                Text(
                    text = stringResource(R.string.section_tools),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    text = stringResource(R.string.section_preferences),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.MusicNote,
                            title = stringResource(R.string.pref_music_player),
                            subtitle = stringResource(R.string.pref_music_player_desc),
                            onClick = onOpenMusic,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Movie,
                            title = stringResource(R.string.pref_video_library),
                            subtitle = stringResource(R.string.pref_video_library_desc),
                            onClick = onOpenLocal,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.ColorLens,
                            title = stringResource(R.string.pref_app_theme),
                            subtitle = when (appSettings.appTheme.lowercase(Locale.US)) {
                                "dark", "dark slate", "slate" -> stringResource(R.string.theme_dark)
                                "classic", "classic navy", "navy" -> stringResource(R.string.theme_classic)
                                "light", "light cinema" -> stringResource(R.string.theme_light)
                                else -> stringResource(R.string.theme_midnight)
                            },
                            onClick = { showThemeDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Language,
                            title = stringResource(R.string.pref_app_language),
                            subtitle = when (activeLanguage.lowercase()) {
                                LocaleHelper.LANGUAGE_ARABIC -> stringResource(R.string.lang_arabic)
                                LocaleHelper.LANGUAGE_ENGLISH -> stringResource(R.string.lang_english)
                                else -> stringResource(R.string.lang_system_default)
                            },
                            onClick = { showLanguageDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.PictureInPictureAlt,
                            title = stringResource(R.string.pref_popup_play),
                            subtitle = if (PipUtils.isPipSupported(context)) {
                                if (appSettings.popupPlayEnabled) {
                                    stringResource(
                                        R.string.pip_status_enabled,
                                        if (appSettings.popupPlayControls == "Seek10") stringResource(R.string.pip_controls_seek10) else stringResource(R.string.pip_controls_prev_next),
                                    )
                                } else {
                                    stringResource(R.string.pip_status_disabled)
                                }
                            } else {
                                stringResource(R.string.pip_status_unsupported)
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
                            title = stringResource(R.string.pref_settings),
                            subtitle = stringResource(R.string.pref_settings_desc),
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // About & Legal Section (Real in-app pages, no fake external links)
            item {
                Text(
                    text = stringResource(R.string.section_about_legal),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.Security,
                            title = stringResource(R.string.pref_privacy),
                            subtitle = stringResource(R.string.pref_privacy_desc),
                            onClick = { showPrivacyDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = stringResource(R.string.pref_help),
                            subtitle = stringResource(R.string.pref_help_desc),
                            onClick = { showHelpDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Gavel,
                            title = stringResource(R.string.pref_legal),
                            subtitle = stringResource(R.string.pref_legal_desc),
                            onClick = { showLegalDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.pref_about_mpx),
                            subtitle = stringResource(R.string.pref_about_mpx_desc),
                            onClick = {
                                if (onOpenAboutBeta != null) {
                                    onOpenAboutBeta()
                                } else {
                                    showAboutDialog = true
                                }
                            },
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
                    snackbarHostState.showSnackbar(context.getString(R.string.theme_applied_snackbar, selectedTheme))
                }
            },
        )
    }

    // 5b. App Language Dialog (Real Immediate Language Switching)
    if (showLanguageDialog) {
        AppLanguageDialog(
            currentLanguage = activeLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelectLanguage = { selectedLang ->
                val updated = appSettings.copy(appLanguage = selectedLang)
                appSettings = updated
                realSettingsRepo.save(updated)
                onLanguageChanged?.invoke(selectedLang)
                showLanguageDialog = false
                scope.launch {
                    val langName = when (selectedLang.lowercase()) {
                        LocaleHelper.LANGUAGE_ARABIC -> "العربية"
                        LocaleHelper.LANGUAGE_ENGLISH -> "English"
                        else -> context.getString(R.string.lang_system_default)
                    }
                    snackbarHostState.showSnackbar(context.getString(R.string.lang_applied_msg, langName))
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
                        context.getString(R.string.pip_not_supported)
                    } else {
                        context.getString(R.string.pip_test_fail_playing)
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
        AboutAppDialog(
            onDismiss = { showAboutDialog = false },
            onOpenAboutBeta = onOpenAboutBeta,
        )
    }

    // 11. Equalizer Message Dialog (honest fallback)
    if (equalizerMessage != null) {
        AlertDialog(
            onDismissRequest = { equalizerMessage = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
            icon = { Icon(Icons.Filled.Equalizer, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.tool_equalizer_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(equalizerMessage ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { equalizerMessage = null }) {
                    Text(stringResource(R.string.action_ok), color = MaterialTheme.colorScheme.primary)
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
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
            icon = { Icon(tool.icon, null, tint = MaterialTheme.colorScheme.tertiary) },
            title = {
                Text(
                    text = stringResource(R.string.issue32_dialog_header, tool.title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                val detail = when (tool.id) {
                    "status" -> stringResource(R.string.issue32_status_detail)
                    "private" -> stringResource(R.string.issue32_private_detail)
                    "manager" -> stringResource(R.string.issue32_manager_detail)
                    "bin" -> stringResource(R.string.issue32_bin_detail)
                    else -> tool.description
                }
                Column {
                    Text(
                        text = stringResource(R.string.issue32_status_notice),
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
                        text = stringResource(R.string.issue32_status_honest),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { issue32Tool = null }) {
                    Text(stringResource(R.string.action_understood), color = MaterialTheme.colorScheme.primary)
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = if (tool.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp),
            )
            if (tool.badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            if (tool.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_network_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_network_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/video.mp4", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp) },
                    trailingIcon = {
                        IconButton(onClick = { pasteClipboard() }) {
                            Icon(Icons.Filled.ContentPaste, stringResource(R.string.action_paste), tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.dialog_network_samples), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { urlText = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Big Buck Bunny", fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(
                        onClick = { urlText = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Elephants Dream", fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (urlText.isNotBlank()) onPlayStream(urlText.trim()) },
                enabled = urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.dialog_network_play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            name = stringResource(R.string.theme_midnight),
            description = stringResource(R.string.theme_midnight_desc),
            bgColor = Color(0xFF000000),
            surfaceColor = Color(0xFF000000),
            primaryColor = Color(0xFF3B82F6),
        ),
        ThemeOption(
            key = "Dark",
            name = stringResource(R.string.theme_dark),
            description = stringResource(R.string.theme_dark_desc),
            bgColor = Color(0xFF0F172A),
            surfaceColor = Color(0xFF1E293B),
            primaryColor = Color(0xFF38BDF8),
        ),
        ThemeOption(
            key = "Classic",
            name = stringResource(R.string.theme_classic),
            description = stringResource(R.string.theme_classic_desc),
            bgColor = Color(0xFF0A0F1D),
            surfaceColor = Color(0xFF131D31),
            primaryColor = Color(0xFF4895EF),
        ),
        ThemeOption(
            key = "Light",
            name = stringResource(R.string.theme_light),
            description = stringResource(R.string.theme_light_desc),
            bgColor = Color(0xFFF8FAFC),
            surfaceColor = Color(0xFFFFFFFF),
            primaryColor = Color(0xFF2563EB),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ColorLens, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_theme_title),
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
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
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
                Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

/**
 * Real In-App Language selector with instant switching and RTL preview.
 */
@Composable
private fun AppLanguageDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    val languages = listOf(
        Triple(
            LocaleHelper.LANGUAGE_SYSTEM,
            stringResource(R.string.lang_system_default),
            stringResource(R.string.lang_system_default_desc),
        ),
        Triple(
            LocaleHelper.LANGUAGE_ENGLISH,
            stringResource(R.string.lang_english),
            stringResource(R.string.lang_english_desc),
        ),
        Triple(
            LocaleHelper.LANGUAGE_ARABIC,
            stringResource(R.string.lang_arabic),
            stringResource(R.string.lang_arabic_desc),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lang_dialog_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                languages.forEach { (langKey, langTitle, langDesc) ->
                    val isSelected = currentLanguage.equals(langKey, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLanguage(langKey) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectLanguage(langKey) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = langTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = langDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.lang_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary)
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
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PictureInPictureAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_popup_play_title),
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pip_banner_supported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pip_banner_unsupported),
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
                            text = stringResource(R.string.pip_auto_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.pip_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = currentEnabled,
                        onCheckedChange = { currentEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.pip_window_controls_header),
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
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_prev_next), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
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
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_seek10), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onTestPip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PictureInPictureAlt, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_test_button), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdateSettings(currentEnabled, currentControls) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_privacy_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.privacy_sec1_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.privacy_sec2_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.privacy_sec3_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec3_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.privacy_sec4_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec4_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary) }
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
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_help_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.help_sec1_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.help_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.help_sec2_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.help_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.help_sec3_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(stringResource(R.string.help_diag_app), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.help_diag_os, Build.VERSION.SDK_INT, Build.VERSION.RELEASE), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.help_diag_device, Build.MANUFACTURER, Build.MODEL), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.help_diag_engine), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.help_diag_db), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary) }
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
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Gavel, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_legal_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.legal_sec1_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.legal_sec2_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.legal_sec3_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec3_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary) }
        },
    )
}

/**
 * About MPx Player In-App Dialog.
 */
@Composable
private fun AboutAppDialog(
    onDismiss: () -> Unit,
    onOpenAboutBeta: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pref_about_mpx), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_version_name),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dialog_about_body1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_about_body2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row {
                if (onOpenAboutBeta != null) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenAboutBeta()
                    }) {
                        Text(stringResource(R.string.about_beta_open_page_button), color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok), color = MaterialTheme.colorScheme.primary) }
            }
        },
    )
}
