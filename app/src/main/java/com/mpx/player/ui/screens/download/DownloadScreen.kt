package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.local.entity.DownloadHistoryEntity
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.download.ActiveDownload
import com.mpx.player.download.CookieImportOutcome
import com.mpx.player.download.DownloadStatus
import com.mpx.player.download.DownloadedFile
import com.mpx.player.download.DownloaderInitState
import com.mpx.player.download.MpxDownloader
import com.mpx.player.download.PlaylistInfo
import com.mpx.player.download.YtDlpUpdateOutcome
import com.mpx.player.download.YtDlpUpdateState
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Download tab (issue #324): paste a video link, download it with yt-dlp,
 * watch the progress, and play the finished file in the player. The tab
 * sits between Music and Me, where Transfer used to be.
 *
 * The engine is [MpxDownloader] (application-scoped singleton), so a
 * download keeps running when the user leaves the tab. This screen only
 * observes its flows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onOpenPlayer: () -> Unit,
    playbackManager: PlaybackManager? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val realPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    val downloader = remember(context) { MpxDownloader.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    val initState by downloader.initState.collectAsState()
    val activeDownloads by downloader.activeDownloads.collectAsState()
    val downloadedFiles by downloader.downloadedFiles.collectAsState()
    // Issue #346: the download history, newest first — the History
    // section below the completed list.
    val downloadHistory by downloader.downloadHistory.collectAsState()
    // Issue #333: yt-dlp version + update state for the manual update row.
    val updateState by downloader.updateState.collectAsState()
    val ytdlpVersion by downloader.ytdlpVersion.collectAsState()

    // DONE rows are redundant with the file list below (a DONE item always
    // has its file there), and CANCELED rows have no action — only live
    // and retryable rows stay in the active section. The snackbar effect
    // above still watches the full list, so completions announce once.
    val visibleActive = activeDownloads.filter {
        it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.FAILED
    }

    var urlText by rememberSaveable { mutableStateOf("") }
    // Issue #340: audio-only toggle — a per-download choice, applied to
    // the next download started from this tab (plain Download button
    // or the sign-in flow below). Not a persisted setting.
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    // Issue #341: download subtitles toggle — same per-download
    // choice as audio-only, applied to the next download started
    // from this tab (plain button or sign-in). Not a persisted setting.
    var subtitles by rememberSaveable { mutableStateOf(false) }
    // Issue #337: the in-app sign-in WebView, opened from the button
    // below. The User-Agent is read once — there is no settings UI for
    // it, so it cannot change while this screen is alive.
    var showSignIn by rememberSaveable { mutableStateOf(false) }
    val signInUserAgent = remember { downloader.loadUserAgent() }
    // Issue #338: download quality settings. The row below shows the
    // saved choices; the dialog edits them, and the summary is re-read
    // when the dialog closes so it always shows the saved value.
    val settingsRepository = remember(context) { SharedPrefsAppSettingsRepository(context) }
    var qualitySettings by remember { mutableStateOf(settingsRepository.load()) }
    var showQuality by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var showSponsorBlock by rememberSaveable { mutableStateOf(false) }
    // Issue #345: playlist probe state. The Download button first
    // asks the engine whether the URL resolves to a playlist; a
    // playlist with several entries opens the picker below, a single
    // entry (or a non-playlist / failed probe) downloads directly.
    // Not saveable: a probe in flight dies with the screen, and the
    // picker is re-opened by tapping Download again.
    var fetchingPlaylist by remember { mutableStateOf(false) }
    var playlistToPick by remember { mutableStateOf<PlaylistInfo?>(null) }

    // Warm up the engine (extracts Python + yt-dlp on first run) so the
    // Download button works the moment the user pastes a link.
    LaunchedEffect(Unit) {
        downloader.ensureInitialized()
    }

    // One snackbar per finished download: DONE and FAILED rows announce
    // themselves once, by id.
    val announcedIds = remember { mutableSetOf<String>() }
    val failedLabel = stringResource(R.string.download_failed)
    val completeLabel = stringResource(R.string.download_complete)
    // Issue #346: a row that finished because the video was already in
    // the download archive announces that, not a plain completion.
    val alreadyDownloadedLabel = stringResource(R.string.download_already_downloaded)
    // Issue #333: manual yt-dlp update outcome labels.
    val ytdlpUpdatedLabel = stringResource(R.string.download_ytdlp_updated)
    val ytdlpUpToDateLabel = stringResource(R.string.download_ytdlp_up_to_date)
    val ytdlpUpdateFailedLabel = stringResource(R.string.download_ytdlp_update_failed)
    val ytdlpUpdateBusyLabel = stringResource(R.string.download_ytdlp_update_busy)
    // Issue #337: cookie import outcome labels.
    val noCookiesLabel = stringResource(R.string.download_no_cookies)
    val cookiesFailedLabel = stringResource(R.string.download_cookies_failed)
    LaunchedEffect(activeDownloads) {
        activeDownloads.forEach { item ->
            if (item.id !in announcedIds && (item.status == DownloadStatus.DONE || item.status == DownloadStatus.FAILED)) {
                announcedIds.add(item.id)
                val message = when {
                    item.status == DownloadStatus.FAILED -> failedLabel
                    item.alreadyDownloaded -> alreadyDownloadedLabel
                    else -> completeLabel
                }
                scope.launch { snackbarHostState.showSnackbar(message) }
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
                        text = stringResource(R.string.download_screen_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // URL entry: field + Paste + Download, Local/Music header style.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(R.string.download_url_placeholder),
                            color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    },
                    singleLine = true,
                    shape = MpxShapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MpxTheme.colors.surfaceVariant,
                        unfocusedContainerColor = MpxTheme.colors.surfaceVariant,
                        focusedBorderColor = MpxTheme.colors.primary,
                        unfocusedBorderColor = MpxTheme.colors.border,
                        focusedTextColor = MpxTheme.colors.textPrimary,
                        unfocusedTextColor = MpxTheme.colors.textPrimary,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        clipboardManager.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                            urlText = it
                        }
                    },
                    shape = MpxShapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MpxTheme.colors.surfaceVariant,
                        contentColor = MpxTheme.colors.textPrimary,
                    ),
                ) {
                    Text(
                        stringResource(R.string.download_paste),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        // Issue #345: probe for a playlist first. A
                        // playlist with several entries opens the
                        // picker; a single entry downloads directly;
                        // a non-playlist URL or a failed probe falls
                        // back to the plain single download (the
                        // probe never fails the row). The probe is one
                        // extra yt-dlp run for single videos too —
                        // the cost of detection, same as Seal.
                        val url = urlText
                        scope.launch {
                            fetchingPlaylist = true
                            try {
                                val playlist = downloader.fetchPlaylistInfo(url)
                                when {
                                    playlist == null -> {
                                        downloader.startDownload(url, audioOnly = audioOnly, subtitles = subtitles)
                                        urlText = ""
                                    }
                                    playlist.entries.size == 1 -> {
                                        downloader.startDownload(
                                            playlist.entries[0].url,
                                            audioOnly = audioOnly,
                                            subtitles = subtitles,
                                        )
                                        urlText = ""
                                    }
                                    else -> playlistToPick = playlist
                                }
                            } finally {
                                fetchingPlaylist = false
                            }
                        }
                    },
                    enabled = urlText.isNotBlank() && !fetchingPlaylist,
                    shape = MpxShapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MpxTheme.colors.primary,
                        contentColor = MpxTheme.colors.textPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (fetchingPlaylist) {
                            stringResource(R.string.download_playlist_fetching)
                        } else {
                            stringResource(R.string.download_button)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Issue #345: playlist entry picker. The selected entries
            // enqueue as their own rows (the #342 queue cap applies);
            // the URL field clears only once the downloads start.
            playlistToPick?.let { playlist ->
                PlaylistPickerDialog(
                    playlist = playlist,
                    onDismiss = { playlistToPick = null },
                    onDownload = { entries ->
                        playlistToPick = null
                        downloader.startDownloads(
                            entries.map { it.url },
                            audioOnly = audioOnly,
                            subtitles = subtitles,
                        )
                        urlText = ""
                    },
                )
            }

            // Issue #340: audio-only toggle, right under the URL field
            // (same ToggleRow shape as SettingsScreen). The mode applies
            // to the next download started — plain button or sign-in.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { audioOnly = !audioOnly }
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.download_audio_only),
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = audioOnly,
                    onCheckedChange = { audioOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MpxTheme.colors.primary,
                        checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
                    ),
                )
            }

            // Issue #341: download subtitles toggle, right under the
            // audio-only toggle (same row style). Video downloads only —
            // an mp3 has no use for a sidecar subtitle file.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { subtitles = !subtitles }
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.download_subtitles),
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = subtitles,
                    onCheckedChange = { subtitles = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MpxTheme.colors.primary,
                        checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
                    ),
                )
            }

            // Issue #337 (rework): sign in inside MPx's own WebView so
            // the cookies it sets can be sent to yt-dlp for this one
            // download (see SignInWebViewDialog). A login in Chrome is
            // invisible to MPx, so the first attempt's "Import cookies
            // from browser" button could never work and is gone. Shown
            // only once a URL is typed.
            if (urlText.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showSignIn = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.download_sign_in),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MpxTheme.colors.primary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // Issue #337: the sign-in WebView. Done reads the cookies
            // this WebView set and starts the download with them; the
            // import is per-download (see MpxDownloader) and the cookies
            // only ever apply to that one download.
            if (showSignIn) {
                SignInWebViewDialog(
                    url = urlText,
                    userAgent = signInUserAgent,
                    onDismiss = { showSignIn = false },
                    onUseCookies = {
                        showSignIn = false
                        scope.launch {
                            when (downloader.importCookiesAndStart(urlText, audioOnly = audioOnly, subtitles = subtitles)) {
                                CookieImportOutcome.IMPORTED -> urlText = ""
                                CookieImportOutcome.NO_COOKIES ->
                                    snackbarHostState.showSnackbar(noCookiesLabel)
                                CookieImportOutcome.FAILED ->
                                    snackbarHostState.showSnackbar(cookiesFailedLabel)
                            }
                        }
                    },
                )
            }

            if (initState == DownloaderInitState.FAILED) {
                Text(
                    text = stringResource(R.string.download_init_failed),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Issue #333: installed yt-dlp version + manual update.
            // The version is "" until the first update check records it
            // (auto-update on launch, or this button).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (ytdlpVersion.isNotBlank()) {
                        stringResource(R.string.download_ytdlp_version, ytdlpVersion)
                    } else {
                        "yt-dlp"
                    },
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            val message = when (downloader.updateYtDlpNow()) {
                                YtDlpUpdateOutcome.UPDATED -> ytdlpUpdatedLabel
                                YtDlpUpdateOutcome.UP_TO_DATE -> ytdlpUpToDateLabel
                                YtDlpUpdateOutcome.FAILED -> ytdlpUpdateFailedLabel
                                YtDlpUpdateOutcome.BUSY -> ytdlpUpdateBusyLabel
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    enabled = updateState != YtDlpUpdateState.UPDATING,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (updateState == YtDlpUpdateState.UPDATING) {
                            stringResource(R.string.download_updating_ytdlp)
                        } else {
                            stringResource(R.string.download_update_ytdlp)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MpxTheme.colors.primary,
                        fontSize = 13.sp,
                    )
                }
            }

            // Issue #338: download quality row. The summary is the
            // custom sorter when one is set (it replaces the built
            // sorter), otherwise resolution + codec. Tapping opens the
            // quality dialog; the summary is re-read on dismiss.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showQuality = true }
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.download_quality_title),
                        color = MpxTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (qualitySettings.downloadCustomSorter.isNotBlank()) {
                            qualitySettings.downloadCustomSorter
                        } else {
                            "${downloadResolutionLabel(qualitySettings.downloadResolution)} · " +
                                downloadCodecLabel(qualitySettings.downloadCodec)
                        },
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MpxTheme.colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (showQuality) {
                DownloadQualityDialog(
                    settingsRepository = settingsRepository,
                    onDismiss = {
                        showQuality = false
                        qualitySettings = settingsRepository.load()
                    },
                )
            }

            // Issue #347: download options row. The summary lists what
            // is actually set (rate limit value, proxy URL, IPv4, safe
            // filenames) or "No limits set". Tapping opens the options
            // dialog; the summary is re-read on dismiss.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOptions = true }
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.download_options_title),
                        color = MpxTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = downloadOptionsSummary(qualitySettings),
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MpxTheme.colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (showOptions) {
                DownloadOptionsDialog(
                    settingsRepository = settingsRepository,
                    onDismiss = {
                        showOptions = false
                        qualitySettings = settingsRepository.load()
                    },
                )
            }

            // Issue #348: SponsorBlock row. The summary is the chosen
            // categories, or "Off" when removal is disabled (or no
            // categories are chosen, which passes no flag). Tapping
            // opens the SponsorBlock dialog; the summary is re-read
            // on dismiss.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSponsorBlock = true }
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.download_sponsorblock_title),
                        color = MpxTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (qualitySettings.downloadSponsorBlock &&
                            qualitySettings.downloadSponsorBlockCategories.isNotBlank()
                        ) {
                            qualitySettings.downloadSponsorBlockCategories
                        } else {
                            stringResource(R.string.player_state_off)
                        },
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MpxTheme.colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (showSponsorBlock) {
                DownloadSponsorBlockDialog(
                    settingsRepository = settingsRepository,
                    onDismiss = {
                        showSponsorBlock = false
                        qualitySettings = settingsRepository.load()
                    },
                )
            }

            if (visibleActive.isEmpty() && downloadedFiles.isEmpty() && downloadHistory.isEmpty()) {
                EmptyStateView(
                    title = stringResource(R.string.download_empty_title),
                    message = stringResource(R.string.download_empty_message),
                    icon = Icons.Filled.Download,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(visibleActive, key = { it.id }) { item ->
                        ActiveDownloadRow(
                            item = item,
                            onCancel = { downloader.cancelDownload(item.id) },
                            onRetry = {
                                downloader.startDownload(
                                    item.url,
                                    audioOnly = item.audioOnly,
                                    subtitles = item.subtitles,
                                )
                            },
                        )
                    }
                    items(downloadedFiles, key = { it.uri }) { file ->
                        DownloadedFileRow(
                            file = file,
                            dateText = dateFormat.format(Date(file.lastModified)),
                            onPlay = {
                                val isAudio = file.extension in AUDIO_EXTENSIONS
                                realPlaybackManager.load(
                                    PlayableMedia(
                                        id = file.uri,
                                        title = file.nameWithoutExtension,
                                        uri = file.uri,
                                        isVideo = !isAudio,
                                        // Issue #341/#344: sidecar subtitles
                                        // ride along as external subtitles
                                        // — the engine sub-adds and pins
                                        // them via the #332 flow. A public
                                        // file resolves them from
                                        // MediaStore, a private leftover
                                        // from the private dir (see
                                        // MpxDownloader.findSiblingSubtitles).
                                        // content:// URIs play through
                                        // MpvFdOpener (#150), file paths
                                        // directly.
                                        externalSubtitles = downloader.findSiblingSubtitles(file),
                                    ),
                                )
                                onOpenPlayer()
                            },
                            onDelete = {
                                downloader.deleteFile(file)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.download_deleted),
                                    )
                                }
                            },
                        )
                    }
                    // Issue #346: the download history, below the
                    // completed list — the completed list is a scan of
                    // files that exist now, history is the permanent
                    // record (it survives the file being deleted, and
                    // carries the title/uploader the scan cannot know).
                    // Tap plays when the file is still there, else a
                    // snackbar; the delete button forgets the entry
                    // only — it never deletes the file.
                    if (downloadHistory.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.download_history),
                                color = MpxTheme.colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(downloadHistory, key = { it.id }) { entry ->
                            DownloadHistoryRow(
                                entry = entry,
                                dateText = dateFormat.format(Date(entry.completedAt)),
                                onPlay = {
                                    scope.launch {
                                        val file = withContext(Dispatchers.IO) {
                                            downloader.historyFileIfExists(entry)
                                        }
                                        if (file != null) {
                                            val isAudio = file.extension in AUDIO_EXTENSIONS
                                            realPlaybackManager.load(
                                                PlayableMedia(
                                                    id = file.uri,
                                                    title = file.nameWithoutExtension,
                                                    uri = file.uri,
                                                    isVideo = !isAudio,
                                                    externalSubtitles = downloader.findSiblingSubtitles(file),
                                                ),
                                            )
                                            onOpenPlayer()
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.download_history_file_missing),
                                            )
                                        }
                                    }
                                },
                                onDelete = { downloader.deleteHistoryEntry(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadRow(
    item: ActiveDownload,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = MpxTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = downloadStatusText(item),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (item.status) {
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = MpxTheme.colors.textSecondary,
                        )
                    }
                }
                DownloadStatus.FAILED -> {
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.download_retry),
                            tint = MpxTheme.colors.primary,
                        )
                    }
                }
                else -> Unit
            }
        }
        if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
            Spacer(Modifier.height(8.dp))
            // progress < 0 means the size is still unknown (indeterminate).
            if (item.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.primary,
                    trackColor = MpxTheme.colors.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.primary,
                    trackColor = MpxTheme.colors.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun downloadStatusText(item: ActiveDownload): String {
    return when (item.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.download_queued)
        DownloadStatus.DOWNLOADING -> {
            val pct = if (item.progress >= 0f) "${item.progress.toInt()}%" else "…"
            val eta = if (item.etaSeconds > 0) {
                stringResource(R.string.download_eta, formatEta(item.etaSeconds))
            } else {
                ""
            }
            listOf(pct, eta).filter { it.isNotBlank() }.joinToString(" · ")
        }
        DownloadStatus.DONE -> stringResource(R.string.download_complete)
        DownloadStatus.FAILED -> {
            val detail = item.error?.takeIf { it.isNotBlank() }
            if (detail != null) "${stringResource(R.string.download_failed)}: $detail" else stringResource(R.string.download_failed)
        }
        DownloadStatus.CANCELED -> stringResource(R.string.download_canceled)
    }
}

@Composable
private fun DownloadedFileRow(
    file: DownloadedFile,
    dateText: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isAudio = file.extension in AUDIO_EXTENSIONS
        Icon(
            imageVector = if (isAudio) Icons.Filled.MusicNote else Icons.Filled.Movie,
            contentDescription = null,
            tint = MpxTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.nameWithoutExtension,
                color = MpxTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatSize(file.size)} · $dateText",
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.download_play),
                tint = MpxTheme.colors.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.download_delete),
                tint = MpxTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * One history row (issue #346): the recorded title, the uploader when
 * known + the completion date, play and forget-entry buttons. Same row
 * shape as DownloadedFileRow above; the delete button only removes the
 * history entry, never the file.
 */
@Composable
private fun DownloadHistoryRow(
    entry: DownloadHistoryEntity,
    dateText: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isAudio = entry.fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
        Icon(
            imageVector = if (isAudio) Icons.Filled.MusicNote else Icons.Filled.Movie,
            contentDescription = null,
            tint = MpxTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = MpxTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (entry.uploader != null) "${entry.uploader} · $dateText" else dateText,
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.download_play),
                tint = MpxTheme.colors.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.download_delete),
                tint = MpxTheme.colors.textSecondary,
            )
        }
    }
}

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

private fun formatEta(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** Mirrors LocalScreen.formatSize exactly (same thresholds, same units). */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
