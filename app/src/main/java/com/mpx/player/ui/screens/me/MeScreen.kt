package com.mpx.player.ui.screens.me
import com.mpx.player.ui.theme.MpxTheme

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.mpx.player.BuildConfig
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.MeTool
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.MpxCard
import com.mpx.player.ui.components.MpxPrimaryButton
import com.mpx.player.ui.components.MpxSecondaryButton
import com.mpx.player.ui.components.MpxSectionHeader
import com.mpx.player.ui.components.MpxTextButton
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.PipUtils
import java.util.Locale
import kotlinx.coroutines.launch
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    onOpenLocal: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAboutBeta: (() -> Unit)? = null,
    onOpenHistory: () -> Unit,
    onOpenPlaylists: () -> Unit,
    playbackManager: PlaybackManager? = null,
    settingsRepository: AppSettingsRepository? = null,
    currentLanguage: String = "system",
    onLanguageChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Issue #198: do NOT resolve the Activity from LocalContext (see #179)
    // — it is a detached localized configuration context, so
    // context.findActivity() returns null and PiP entry silently fails.
    // The host view's context IS the real Activity. Resolved here, in the
    // screen body, so the dialog's onTestPip lambda captures the real one.
    val activity = LocalView.current.context as? Activity
    val realPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    val persistenceRepository = realPlaybackManager.persistenceRepository
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Persistent Room data observation for live badge indicators
    val favourites by persistenceRepository.observeFavourites().collectAsState(initial = emptyList())
    // #265: the badge shows the real history total. The old code observed
    // the 100-row-capped list and rendered history.size, freezing the
    // badge at 100. The list itself is not needed here at all.
    val historyTotal by persistenceRepository.observeHistoryCount().collectAsState(initial = 0)
    val customPlaylists by persistenceRepository.observePlaylists().collectAsState(initial = emptyList())

    // Settings state
    var appSettings by remember { mutableStateOf(realSettingsRepo.load()) }
    val activeLanguage = currentLanguage.ifBlank { appSettings.appLanguage }

    // Issue #231: reload settings on resume (same pattern as LocalScreen) —
    // a PiP/language/last-SMB-host change made in Settings must show up
    // here without the tab being recreated.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appSettings = realSettingsRepo.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dialog navigation states
    var showFavouritesDialog by remember { mutableStateOf(false) }

    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showPopupPlayDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLocalNetworkDialog by remember { mutableStateOf(false) }
    var showCloudDriveDialog by remember { mutableStateOf(false) }

    var issue32Tool by remember { mutableStateOf<MeTool?>(null) }
    var equalizerMessage by remember { mutableStateOf<String?>(null) }

    // 14 Tools: 8 Real Features + 2 New Honest Entries (Local Network, Cloud Drive) + 4 Issue 32 Honest Placeholders
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
            badge = if (historyTotal > 0) "$historyTotal" else null,
            isAvailable = true,
        ),
        MeTool(
            id = "network",
            title = stringResource(R.string.tool_network_title),
            description = stringResource(R.string.tool_network_desc),
            icon = Icons.Filled.Cloud,
            badge = stringResource(R.string.tool_badge_real),
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
        MeTool(
            id = "local_network",
            title = stringResource(R.string.tool_local_network_title),
            description = stringResource(R.string.tool_local_network_desc),
            icon = Icons.Filled.Wifi,
            // Issue #231: the SMB browser works — the tile is real, not
            // "Soon", and must not look greyed out.
            badge = null,
            isAvailable = true,
        ),
        MeTool(
            id = "cloud_drive",
            title = stringResource(R.string.tool_cloud_title),
            description = stringResource(R.string.tool_cloud_desc),
            icon = Icons.Filled.CloudQueue,
            badge = stringResource(R.string.tool_badge_plugin),
            isAvailable = false,
        ),

        // Row 3: Issue 32 Honest Placeholders
        MeTool(
            id = "status",
            title = stringResource(R.string.tool_status_title),
            description = stringResource(R.string.tool_status_desc),
            icon = Icons.Filled.SaveAlt,
            // Honest state, not a count: Status Saver is planned, not built.
            // (MX's own badge here is a NEW tip, not a saved-count badge.)
            badge = stringResource(R.string.tool_badge_planned),
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
            // #157: History and Playlists are full pages now, opened from
            // these Me tiles (they used to open dialogs).
            "playlists" -> onOpenPlaylists()
            "favourites" -> showFavouritesDialog = true
            "history" -> onOpenHistory()
            "network" -> showNetworkStreamDialog = true
            "equalizer" -> {
                // mpv-only (issue #150): there is no ExoPlayer audio session
                // any more, so the system equalizer opens against the global
                // output mix (session 0) instead of the player's session.
                val launched = PipUtils.launchSystemEqualizer(context, 0)
                if (!launched) {
                    equalizerMessage = context.getString(R.string.equalizer_dialog_msg)
                }
            }
            "local_network" -> showLocalNetworkDialog = true
            "cloud_drive" -> showCloudDriveDialog = true
            "status", "private", "manager", "bin" -> {
                issue32Tool = tool
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
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
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
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
                MpxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MpxTheme.colors.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MpxTheme.colors.textPrimary,
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
                                color = MpxTheme.colors.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.me_profile_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // Tools Section Header
            item {
                MpxSectionHeader(text = stringResource(R.string.section_tools))
            }

            // Tools 4-column Grid (MX Player signature tools section, chunked rows)
            item {
                MpxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 4-column rows, chunked so the grid grows with the tool list
                        tools.chunked(4).forEachIndexed { rowIndex, row ->
                            if (rowIndex > 0) Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                            ) {
                                row.forEach { tool ->
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
            }

            item { Spacer(Modifier.height(16.dp)) }

            // Preferences Section
            item {
                MpxSectionHeader(text = stringResource(R.string.section_preferences))
            }

            item {
                MpxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.MusicNote,
                            title = stringResource(R.string.pref_music_player),
                            subtitle = stringResource(R.string.pref_music_player_desc),
                            onClick = onOpenMusic,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Movie,
                            title = stringResource(R.string.pref_video_library),
                            subtitle = stringResource(R.string.pref_video_library_desc),
                            onClick = onOpenLocal,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Language,
                            title = stringResource(R.string.pref_app_language),
                            // Issue #231: the display name comes from the
                            // shared LocaleHelper mapping, so every supported
                            // language names itself instead of falling back
                            // to "System default".
                            subtitle = stringResource(LocaleHelper.languageTitleRes(activeLanguage)),
                            onClick = { showLanguageDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.PictureInPictureAlt,
                            title = stringResource(R.string.pref_popup_play),
                            subtitle = if (PipUtils.isPipSupported(context)) {
                                if (appSettings.popupPlayEnabled) {
                                    stringResource(
                                        R.string.pip_status_enabled,
                                        // Issue #231: map all three control
                                        // values explicitly — PlayPauseOnly
                                        // used to render as "Prev/Next".
                                        when (appSettings.popupPlayControls) {
                                            "Seek10" -> stringResource(R.string.pip_controls_seek10)
                                            "PlayPauseOnly" -> stringResource(R.string.pip_controls_play_pause_only)
                                            else -> stringResource(R.string.pip_controls_prev_next) // "PrevNext" (Default)
                                        },
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
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
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
                MpxSectionHeader(text = stringResource(R.string.section_about_legal))
            }

            item {
                MpxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.Security,
                            title = stringResource(R.string.pref_privacy),
                            subtitle = stringResource(R.string.pref_privacy_desc),
                            onClick = { showPrivacyDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
                        )
                        PreferenceRow(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = stringResource(R.string.pref_help),
                            subtitle = stringResource(R.string.pref_help_desc),
                            onClick = { showHelpDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Gavel,
                            title = stringResource(R.string.pref_legal),
                            subtitle = stringResource(R.string.pref_legal_desc),
                            onClick = { showLegalDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
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
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_removed_favourites))
                }
            },
            onClearAll = {
                scope.launch {
                    persistenceRepository.clearFavourites()
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_cleared_favourites))
                }
            },
        )
    }

    // 4. Network Stream Dialog (real mpv URL streaming)
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
                    // Issue #231: the shared mapping names every supported
                    // language (the old when only knew Arabic and English).
                    val langName = context.getString(LocaleHelper.languageTitleRes(selectedLang))
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
                // Issue #225: pass the real playback state and video size —
                // the old bare call defaulted to playing with 0x0 dims
                // (wrong action label, 16:9 window) and armed auto-enter
                // for whatever session followed.
                val pipState = realPlaybackManager.playbackState.value
                val entered = PipUtils.enterPip(
                    activity,
                    null,
                    pipState.isPlaying,
                    realPlaybackManager.videoWidth,
                    realPlaybackManager.videoHeight,
                    pipState.currentItem?.isVideo == true,
                )
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
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            icon = { Icon(Icons.Filled.Equalizer, null, tint = MpxTheme.colors.primary) },
            title = { Text(stringResource(R.string.tool_equalizer_title), color = MpxTheme.colors.textPrimary) },
            text = { Text(equalizerMessage ?: "", color = MpxTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { equalizerMessage = null }) {
                    Text(stringResource(R.string.action_ok), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // 12. Local Network SMB browser (issue #78): real share/folder browsing
    // via smbj; tapping a media file plays it through PlaybackManager.
    if (showLocalNetworkDialog) {
        SmbBrowserDialog(
            initialHost = appSettings.lastSmbHost,
            onHostRemembered = { host ->
                val updated = appSettings.copy(lastSmbHost = host)
                appSettings = updated
                realSettingsRepo.save(updated)
            },
            onPlayMedia = { title, proxyUrl, isVideo, smbId ->
                showLocalNetworkDialog = false
                realPlaybackManager.playQueue(
                    listOf(
                        PlayableMedia(
                            id = smbId,
                            title = title,
                            subtitle = proxyUrl,
                            uri = proxyUrl,
                            isVideo = isVideo,
                            durationMs = 0L,
                        ),
                    ),
                    startIndex = 0,
                    autoPlay = true,
                )
                onOpenPlayer()
            },
            onDismiss = { showLocalNetworkDialog = false },
        )
    }

    // 13. Cloud Drive Plugin Point (no plugin installed — no account or files shown)
    if (showCloudDriveDialog) {
        AlertDialog(
            onDismissRequest = { showCloudDriveDialog = false },
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            icon = { Icon(Icons.Filled.CloudQueue, null, tint = MpxTheme.colors.primary) },
            title = {
                Text(
                    text = stringResource(R.string.tool_cloud_title),
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.cloud_dialog_body),
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.issue32_status_honest),
                        color = MpxTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCloudDriveDialog = false }) {
                    Text(stringResource(R.string.action_understood), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // 14. Issue 32 Honest Placeholder Dialog
    if (issue32Tool != null) {
        val tool = issue32Tool!!
        AlertDialog(
            onDismissRequest = { issue32Tool = null },
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            icon = { Icon(tool.icon, null, tint = MpxTheme.colors.primary) },
            title = {
                Text(
                    text = stringResource(R.string.issue32_dialog_header, tool.title),
                    color = MpxTheme.colors.textPrimary,
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
                        color = MpxTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.issue32_status_honest),
                        color = MpxTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { issue32Tool = null }) {
                    Text(stringResource(R.string.action_understood), color = MpxTheme.colors.primary)
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
            .clip(MpxShapes.small)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icon + badge: no tile background or border behind the glyph (issue
        // #162) — the icon sits bare in the 52dp zone, matching the quiet
        // treatment of the preference rows. The badge sits on the unclipped
        // outer box, overlapping the icon zone's top-end corner cleanly.
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = if (tool.isAvailable) MpxTheme.colors.primary else MpxTheme.colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
            if (tool.badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .heightIn(min = 14.dp)
                        .background(
                            if (tool.isAvailable) MpxTheme.colors.primary else MpxTheme.colors.textMuted,
                            MpxShapes.small,
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tool.badge,
                        color = if (tool.isAvailable) MpxTheme.colors.textPrimary else MpxTheme.colors.black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodySmall,
            color = MpxTheme.colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            // Single-line labels truncated mid-word in the 4-column grid
            // ("Network Stre…"); two centered lines fit every tool name.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
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
        // Bare 24dp icon, no tile background or border (issue #162) — the
        // same quiet treatment as the Preferences rows. 12dp gap matches
        // the shared PreferenceRow; dividers start at 52dp (16 + 24 + 12).
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MpxTheme.colors.primary,
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

// --- DIALOG IMPLEMENTATIONS ---

/**
 * Issue #297: the scheme rule for a network stream URL — the same rule
 * pasteClipboard() applies to clipboard text (http://, https://,
 * rtsp://). Typed text is trimmed first; paste checks the raw text and
 * trims on accept, so the two paths share the scheme list but keep
 * their own trimming behaviour.
 */
private fun isSupportedStreamUrl(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("rtsp://", ignoreCase = true)
}

/**
 * Real Network Stream player dialog allowing direct stream input.
 */
@Composable
private fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlayStream: (String) -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    // Issue #297: typed text is validated with the same scheme rule as
    // pasteClipboard() below — Play is only enabled for a supported
    // stream URL, so "hello" can no longer reach the player.
    val isValidUrl = isSupportedStreamUrl(urlText)
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
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_network_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_network_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/video.mp4", color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f), fontSize = 13.sp) },
                    trailingIcon = {
                        IconButton(onClick = { pasteClipboard() }) {
                            Icon(Icons.Filled.ContentPaste, stringResource(R.string.action_paste), tint = MpxTheme.colors.primary)
                        }
                    },
                    singleLine = true,
                    // Issue #297: non-blank text without a supported
                    // scheme is an error — the field shows why Play is
                    // disabled instead of failing in the player.
                    isError = urlText.isNotBlank() && !isValidUrl,
                    supportingText = {
                        if (urlText.isNotBlank() && !isValidUrl) {
                            Text(stringResource(R.string.dialog_network_invalid_url))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                // Issue #297: Play only for a supported stream URL —
                // the same scheme rule as pasteClipboard(), so typed
                // "hello" can no longer open the player.
                onClick = { if (isValidUrl) onPlayStream(urlText.trim()) },
                enabled = isValidUrl,
                colors = ButtonDefaults.buttonColors(containerColor = MpxTheme.colors.primary),
            ) {
                Text(stringResource(R.string.dialog_network_play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MpxTheme.colors.textSecondary)
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
    // Issue #231: one shared language list — the Me dialog offers the same
    // 10 options, in the same order, as the Settings language picker.
    val languages = LocaleHelper.SUPPORTED_LANGUAGES.map { option ->
        Triple(
            option.code,
            stringResource(option.titleRes),
            stringResource(option.descRes),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lang_dialog_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                )
            }
        },
        text = {
            // Issue #231: the list grew from 3 to 10 languages, so it
            // scrolls instead of overflowing the dialog on short screens.
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                languages.forEach { (langKey, langTitle, langDesc) ->
                    val isSelected = currentLanguage.equals(langKey, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .clickable { onSelectLanguage(langKey) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectLanguage(langKey) },
                            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = langTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MpxTheme.colors.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = langDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
                    HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.lang_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary)
            }
        },
    )
}

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
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PictureInPictureAlt, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_popup_play_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
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
                            .clip(MpxShapes.small)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pip_banner_supported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textPrimary,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pip_banner_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textPrimary,
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
                            color = MpxTheme.colors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.pip_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = currentEnabled,
                        onCheckedChange = { currentEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MpxTheme.colors.primary, checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f)),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.pip_window_controls_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MpxTheme.colors.textPrimary,
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
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_prev_next), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
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
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_seek10), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }

                // Issue #231: the third controls mode — Play/Pause only —
                // was a real RemoteAction set in PipUtils but had no radio.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentControls = "PlayPauseOnly" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentControls == "PlayPauseOnly",
                        onClick = { currentControls = "PlayPauseOnly" },
                        colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pip_controls_play_pause_only), color = MpxTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(10.dp))

                MpxSecondaryButton(
                    text = stringResource(R.string.pip_test_button),
                    icon = Icons.Filled.PictureInPictureAlt,
                    onClick = onTestPip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            MpxPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onUpdateSettings(currentEnabled, currentControls) },
            )
        },
        dismissButton = {
            MpxTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
            )
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
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_privacy_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.privacy_sec1_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.privacy_sec2_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.privacy_sec3_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec3_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.privacy_sec4_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.privacy_sec4_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary) }
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
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_help_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.help_sec1_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.help_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.help_sec2_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.help_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.help_sec3_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.small)
                            .background(MpxTheme.colors.surfaceElevated)
                            .border(1.dp, MpxTheme.colors.border, MpxShapes.small)
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(stringResource(R.string.help_diag_app, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                            Text(stringResource(R.string.help_diag_os, Build.VERSION.SDK_INT, Build.VERSION.RELEASE), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                            Text(stringResource(R.string.help_diag_device, Build.MANUFACTURER, Build.MODEL), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                            Text(
                                stringResource(
                                    R.string.help_diag_engine,
                                    "libmpv (mpv-android-lib 0.1.12)",
                                ),
                                fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary,
                            )
                            Text(stringResource(R.string.help_diag_db), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MpxTheme.colors.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary) }
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
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Gavel, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_legal_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item {
                    Text(stringResource(R.string.legal_sec1_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec1_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.legal_sec2_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec2_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.legal_sec3_title), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.legal_sec3_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxTheme.colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MpxTheme.colors.primary) }
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
        containerColor = MpxTheme.colors.black,
        tonalElevation = 0.dp,
        modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MpxTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pref_about_mpx), fontWeight = FontWeight.Bold, color = MpxTheme.colors.textPrimary)
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_version_name),
                    fontWeight = FontWeight.Bold,
                    color = MpxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dialog_about_body1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_about_body2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
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
                        Text(stringResource(R.string.about_beta_open_page_button), color = MpxTheme.colors.primary)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok), color = MpxTheme.colors.primary) }
            }
        },
    )
}
