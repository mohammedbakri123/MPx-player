package com.mpx.player.ui.screens.me
import com.mpx.player.ui.theme.MpxTheme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.components.me.AboutAppDialog
import com.mpx.player.ui.components.me.AppLanguageDialog
import com.mpx.player.ui.components.me.CustomPopupPlayDialog
import com.mpx.player.ui.components.me.HelpFeedbackDialog
import com.mpx.player.ui.components.me.LegalLicensesDialog
import com.mpx.player.ui.components.me.PreferenceRow
import com.mpx.player.ui.components.me.PrivacySecurityDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.components.MpxCard
import com.mpx.player.ui.components.MpxCompactTopBar
import com.mpx.player.ui.components.MpxSectionHeader
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.PipUtils
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.mpx.player.ui.theme.MpxHairline

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

    var showPopupPlayDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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

            // Collection Section
            item {
                MpxSectionHeader(text = stringResource(R.string.section_collection))
            }

            item {
                MpxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
) {
                    Column {
                        PreferenceRow(
                            icon = Icons.Filled.History,
                            title = stringResource(R.string.tool_history_title),
                            subtitle = stringResource(R.string.tool_history_desc),
                            onClick = onOpenHistory,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
                        )
                        PreferenceRow(
                            icon = Icons.Filled.Favorite,
                            title = stringResource(R.string.tool_favourites_title),
                            subtitle = stringResource(R.string.tool_favourites_desc),
                            onClick = { showFavouritesDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MpxTheme.colors.border,
                            thickness = MpxHairline,
                        )
                        PreferenceRow(
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            title = stringResource(R.string.tool_playlists_title),
                            subtitle = stringResource(R.string.tool_playlists_desc),
                            onClick = onOpenPlaylists,
                        )
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

}

