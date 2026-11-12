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
import com.mpx.player.domain.model.MeTool
import com.mpx.player.network.isSupportedStreamUrl
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.components.me.AboutAppDialog
import com.mpx.player.ui.components.me.AppLanguageDialog
import com.mpx.player.ui.components.me.CustomPopupPlayDialog
import com.mpx.player.ui.components.me.HelpFeedbackDialog
import com.mpx.player.ui.components.me.LegalLicensesDialog
import com.mpx.player.ui.components.me.NetworkStreamDialog
import com.mpx.player.ui.components.me.PreferenceRow
import com.mpx.player.ui.components.me.PrivacySecurityDialog
import com.mpx.player.ui.components.me.ToolGridItem
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.MpxCard
import com.mpx.player.ui.components.MpxCompactTopBar
import com.mpx.player.ui.components.MpxPrimaryButton
import com.mpx.player.ui.components.MpxSecondaryButton
import com.mpx.player.ui.components.MpxSectionHeader
import com.mpx.player.ui.components.MpxTextButton
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.PipUtils
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
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
    onOpenNetwork: (() -> Unit)? = null,
    playbackCoordinator: PlaybackCoordinator,
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
    // Migration Phase 2e: screen state lives in MeViewModel now —
    // settings, the badge flows, and the favourites actions. The
    // screen still takes PlaybackCoordinator directly for transport and
    // the PiP test; those move behind interfaces in later phases.
    // Settings come from the ViewModel (Koin's AppSettingsRepository
    // single), so the old settingsRepository parameter is gone.
    val viewModel: MeViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Persistent Room data observation for live badge indicators
    val favourites by viewModel.favourites.collectAsState()
    // #265: the badge shows the real history total. The old code observed
    // the 100-row-capped list and rendered history.size, freezing the
    // badge at 100. The list itself is not needed here at all.
    val historyTotal by viewModel.historyTotal.collectAsState()
    val customPlaylists by viewModel.playlists.collectAsState()

    // Settings state
    val appSettings by viewModel.settings.collectAsState()
    val activeLanguage = viewModel.activeLanguage(currentLanguage)

    // Issue #231: reload settings on resume (same pattern as LocalScreen) —
    // a PiP/language/last-SMB-host change made in Settings must show up
    // here without the tab being recreated.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadSettings()
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
            "local_network" -> onOpenNetwork?.invoke()
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
            MpxCompactTopBar(
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
                playbackCoordinator.playQueue(
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
                    viewModel.removeFavourite(uri)
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_removed_favourites))
                }
            },
            onClearAll = {
                scope.launch {
                    viewModel.clearFavourites()
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
                playbackCoordinator.playQueue(
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
                viewModel.updateLanguage(selectedLang)
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
                viewModel.updatePopupPlay(enabled, controls)
                showPopupPlayDialog = false
            },
            onTestPip = {
                // Issue #225: pass the real playback state and video size —
                // the old bare call defaulted to playing with 0x0 dims
                // (wrong action label, 16:9 window) and armed auto-enter
                // for whatever session followed.
                val pipState = playbackCoordinator.playbackState.value
                val entered = PipUtils.enterPip(
                    activity,
                    appSettings,
                    pipState.isPlaying,
                    playbackCoordinator.videoWidth,
                    playbackCoordinator.videoHeight,
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

