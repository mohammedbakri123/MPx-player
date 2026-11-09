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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.download.ActiveDownload
import com.mpx.player.download.CookieImportOutcome
import com.mpx.player.download.DownloadPhase
import com.mpx.player.download.DownloadStatus
import com.mpx.player.download.DownloaderInitState
import com.mpx.player.download.MpxDownloader
import com.mpx.player.download.PlaylistInfo
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Download tab (issue #324): paste a video link, download it with yt-dlp,
 * watch the progress. Finished files live on the Downloaded screen
 * (issue #379), opened from the button below the URL field. The tab
 * sits between Music and Me, where Transfer used to be.
 *
 * The engine is [MpxDownloader] (application-scoped singleton), so a
 * download keeps running when the user leaves the tab. This screen only
 * observes its flows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onOpenDownloaded: () -> Unit,
    modifier: Modifier = Modifier,
    // Issue #416: a link shared to the app — pre-fills the URL field.
    // Consumed here, not in MpxApp: the field is filled first, then
    // the callback clears the activity's copy, so the URL can never
    // be cleared before this screen sees it. The link is NOT
    // recorded as a recent URL until the user actually downloads it.
    sharedUrl: String? = null,
    onSharedUrlConsumed: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val downloader = remember(context) { MpxDownloader.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val initState by downloader.initState.collectAsState()
    val activeDownloads by downloader.activeDownloads.collectAsState()

    // DONE rows are redundant with the Downloaded screen (a DONE item
    // always has its file there), and CANCELED rows have no action —
    // only live and retryable rows stay in the active section. The
    // snackbar effect above still watches the full list, so completions
    // announce once.
    val visibleActive = activeDownloads.filter {
        it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.FAILED
    }

    // Issue #379: the download defaults (audio-only, subtitles) live in
    // Settings > Downloader settings now — read fresh from the repo at
    // each download start, so a change there applies immediately.
    val settingsRepository = remember(context) { SharedPrefsAppSettingsRepository(context) }
    // Issue #406: the field pre-fills from the most recent download
    // link, so re-downloading a channel or a series is one tap. The
    // list below refreshes whenever a download starts (or one is
    // removed) — the downloader records every start.
    // Issue #407: when a custom command is on, the field accepts
    // several URLs (one per line or space-separated), so it must
    // stay multi-line — a single-line field strips newlines from
    // pasted text, and a pasted list would glue into one URL.
    // Read fresh on every composition (a cheap in-memory read), so
    // toggling the command in Settings applies when coming back.
    val customCommandOn = settingsRepository.load().let {
        it.customCommandEnabled && it.customCommand.isNotBlank()
    }
    var urlText by rememberSaveable {
        mutableStateOf(downloader.loadRecentUrls().firstOrNull().orEmpty())
    }
    // Issue #416: a shared link lands in the field when it arrives
    // (cold start or a share while the app is alive). The effect
    // keys on the URL itself, so a second share fills the field
    // again even while this screen is already showing.
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            urlText = sharedUrl
            onSharedUrlConsumed?.invoke()
        }
    }
    var recentUrls by remember { mutableStateOf(downloader.loadRecentUrls()) }
    LaunchedEffect(activeDownloads.map { it.id to it.status }) {
        recentUrls = downloader.loadRecentUrls()
    }
    // Issue #337: the in-app sign-in WebView, opened from the button
    // below. The User-Agent is read once — there is no settings UI for
    // it, so it cannot change while this screen is alive.
    var showSignIn by rememberSaveable { mutableStateOf(false) }
    val signInUserAgent = remember { downloader.loadUserAgent() }
    // Issue #345: playlist probe state. The Download button first
    // asks the engine whether the URL resolves to a playlist; a
    // playlist with several entries opens the picker below, a single
    // entry (or a non-playlist / failed probe) downloads directly.
    // Not saveable: a probe in flight dies with the screen, and the
    // picker is re-opened by tapping Download again.
    var fetchingPlaylist by remember { mutableStateOf(false) }
    var playlistToPick by remember { mutableStateOf<PlaylistInfo?>(null) }
    // Issue #383: a single video whose format info was fetched — the
    // sheet opens with the video URL (to start the download from) and
    // its info (title + formats). Null while no sheet is open.
    var formatToPick by remember { mutableStateOf<Pair<String, VideoInfo>?>(null) }

    // Issue #380: metered-network gate. Every download start goes through
    // guardMetered: on an unmetered network (or when the user allowed
    // metered downloads) the action runs at once; on a metered network
    // with the setting off, the action waits for the dialog — Download
    // runs it once, the "always allow" box saves the setting first, and
    // Cancel drops it (the picker / sign-in dialog that opened it stays
    // open, because the guarded action only runs after confirmation).
    // Not saveable: a waiting download dies with the screen, and the user
    // re-taps Download.
    var pendingMeteredAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun guardMetered(action: () -> Unit) {
        if (downloader.isActiveNetworkMetered() && !settingsRepository.load().downloadAllowMetered) {
            pendingMeteredAction = action
        } else {
            action()
        }
    }

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
    // Issue #337: cookie import outcome labels.
    val noCookiesLabel = stringResource(R.string.download_no_cookies)
    val cookiesFailedLabel = stringResource(R.string.download_cookies_failed)
    // Issue #409: saved cookie profiles, observed for the picker
    // button below. The list is tiny (one row per saved site).
    val cookieProfiles by downloader.getCookieProfiles().collectAsState(initial = emptyList())
    var showCookiePicker by remember { mutableStateOf(false) }
    var showSaveCookies by remember { mutableStateOf(false) }
    var pendingCookiesUrl by remember { mutableStateOf("") }
    val cookiesSavedLabel = stringResource(R.string.download_cookie_profile_saved)
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
                    // Issue #359: Paste lives in the field now, not as
                    // its own button — the row is field + Download only.
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                                urlText = it
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.download_paste),
                                tint = MpxTheme.colors.textSecondary,
                            )
                        }
                    },
                    singleLine = !customCommandOn,
                    // Issue #407: multi-line only while a custom
                    // command is on (up to 4 lines for a pasted list);
                    // singleLine above forces 1 line otherwise.
                    minLines = 1,
                    maxLines = 4,
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
                        // Issue #380: the metered gate runs first — the
                        // probe itself costs data, so nothing starts (not
                        // even the probe) before the user confirms.
                        guardMetered {
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
                            val downloadPrefs = settingsRepository.load()
                            // Issue #383: a single video opens the
                            // format sheet instead of downloading
                            // immediately — fetch its info (the formats
                            // ride along) and show the sheet when it
                            // has any. Audio-only downloads never open
                            // the sheet (their path is `-x`, no `-f`).
                            // Any info failure falls back to the plain
                            // single download, exactly as before.
                            val startSingleVideo: suspend (String) -> Unit = { videoUrl ->
                                // Issue #384: with a custom command on,
                                // the template IS the command — the
                                // format sheet's choices would be
                                // ignored, so the download starts
                                // immediately instead of opening it.
                                if (downloadPrefs.customCommandEnabled && downloadPrefs.customCommand.isNotBlank()) {
                                    downloader.startDownload(videoUrl, audioOnly = downloadPrefs.downloadAudioOnly, subtitles = downloadPrefs.downloadSubtitles)
                                    urlText = ""
                                } else if (downloadPrefs.downloadFormatSelection == "auto") {
                                    // Issue #408: Auto format selection
                                    // skips the sheet (and the info
                                    // fetch that feeds it) and starts
                                    // immediately — runDownload applies
                                    // the global codec/resolution/sorter
                                    // when no formatId is set, so the
                                    // preset above is what downloads.
                                    downloader.startDownload(videoUrl, audioOnly = false, subtitles = downloadPrefs.downloadSubtitles)
                                    urlText = ""
                                } else if (!downloadPrefs.downloadAudioOnly) {
                                    val info = downloader.fetchVideoInfoForFormat(videoUrl)
                                    if (info != null && info.formats.orEmpty().isNotEmpty()) {
                                        formatToPick = videoUrl to info
                                    } else {
                                        downloader.startDownload(videoUrl, audioOnly = false, subtitles = downloadPrefs.downloadSubtitles)
                                        urlText = ""
                                    }
                                } else {
                                    downloader.startDownload(videoUrl, audioOnly = true, subtitles = downloadPrefs.downloadSubtitles)
                                    urlText = ""
                                }
                            }
                            try {
                                // Issue #407: with a custom command on,
                                // the field accepts several URLs
                                // (newline- or space-separated — Seal's
                                // executeCustomCommandTask splits on
                                // [\n ]): one row per URL, same template,
                                // no playlist probe — the probe is
                                // meaningless for several links at once.
                                // A single URL takes today's path below.
                                val customUrls = if (downloadPrefs.customCommandEnabled && downloadPrefs.customCommand.isNotBlank()) {
                                    url.split(Regex("[\\n ]")).map { it.trim() }.filter { it.isNotBlank() }
                                } else {
                                    emptyList()
                                }
                                if (customUrls.size > 1) {
                                    customUrls.forEach {
                                        downloader.startDownload(it, audioOnly = downloadPrefs.downloadAudioOnly, subtitles = downloadPrefs.downloadSubtitles)
                                    }
                                    urlText = ""
                                } else {
                                    val playlist = downloader.fetchPlaylistInfo(url)
                                    when {
                                        playlist == null -> startSingleVideo(url)
                                        playlist.entries.size == 1 -> startSingleVideo(playlist.entries[0].url)
                                        else -> playlistToPick = playlist
                                    }
                                }
                            } finally {
                                fetchingPlaylist = false
                            }
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

            // Issue #406: recent links — tap one to fill the field,
            // X removes it. Shown under the URL row whenever the list
            // is non-empty.
            if (recentUrls.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.download_recent_links_title),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
                recentUrls.forEach { recent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { urlText = recent },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = recent,
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            downloader.removeRecentUrl(recent)
                            recentUrls = downloader.loadRecentUrls()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
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
                        // Issue #380: the whole pick waits on the gate —
                        // if the user cancels the metered dialog, the
                        // picker stays open and the URL stays.
                        guardMetered {
                        playlistToPick = null
                        val downloadPrefs = settingsRepository.load()
                        // Issue #406: record the playlist URL itself —
                        // the entries ride through startDownload with a
                        // playlistTitle and are not recorded there, or
                        // one playlist would flood the recent list.
                        downloader.recordRecentUrl(urlText)
                        downloader.startDownloads(
                            entries.map { it.url },
                            audioOnly = downloadPrefs.downloadAudioOnly,
                            subtitles = downloadPrefs.downloadSubtitles,
                            // Issue #389: the playlist title rides down
                            // with the entries, so grouped downloads
                            // land in a folder named after it.
                            playlistTitle = playlist.title,
                        )
                        urlText = ""
                        }
                    },
                )
            }

            // Issue #383: per-video format sheet. The chosen format
            // id starts the download; the URL field clears only once
            // it starts, so dismissing the sheet keeps the URL.
            formatToPick?.let { (videoUrl, info) ->
                FormatSelectionDialog(
                    videoInfo = info,
                    onDismiss = { formatToPick = null },
                    onDownload = { formatId, clips, splitByChapter, newTitle ->
                        formatToPick = null
                        val downloadPrefs = settingsRepository.load()
                        downloader.startDownload(
                            videoUrl,
                            audioOnly = downloadPrefs.downloadAudioOnly,
                            subtitles = downloadPrefs.downloadSubtitles,
                            formatId = formatId,
                            clips = clips,
                            splitByChapter = splitByChapter,
                            newTitle = newTitle,
                        )
                        urlText = ""
                    },
                )
            }

            // Issue #380: the metered dialog, shown while an action waits
            // for the user's answer.
            pendingMeteredAction?.let { action ->
                MeteredDownloadDialog(
                    settingsRepository = settingsRepository,
                    onDismiss = { pendingMeteredAction = null },
                    onDownload = { action() },
                )
            }

            // Issue #379: the settings rows (audio-only, subtitles, yt-dlp
            // version, quality, options, SponsorBlock) moved to Settings >
            // Downloader settings. This page is the download flow only.

            // The init-failed note used to sit inside the collapsed options
            // section — it now shows under the URL field, where it is
            // always visible when the engine failed to start.
            if (initState == DownloaderInitState.FAILED) {
                Text(
                    text = stringResource(R.string.download_init_failed),
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Issue #379: Downloaded button — the finished files and
            // history live on their own screen now (Seal's task-list
            // page model), opened from here.
            OutlinedButton(
                onClick = onOpenDownloaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = MpxShapes.extraLarge,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.download_downloaded_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(color = MpxTheme.colors.border, thickness = MpxHairline)

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
                    // Issue #409: use a saved profile instead of
                    // signing in again — only when profiles exist.
                    if (cookieProfiles.isNotEmpty()) {
                        TextButton(
                            onClick = { showCookiePicker = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.download_use_saved_cookies),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MpxTheme.colors.primary,
                                fontSize = 13.sp,
                            )
                        }
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
                        // Issue #380: the whole sign-in handoff waits on
                        // the gate — cancel keeps the WebView open.
                        guardMetered {
                        showSignIn = false
                        scope.launch {
                            when (downloader.importCookiesAndStart(urlText, audioOnly = settingsRepository.load().downloadAudioOnly, subtitles = settingsRepository.load().downloadSubtitles)) {
                                // Issue #409: offer to save these
                                // cookies as a named profile, so the
                                // next restricted download from this
                                // site does not need another sign-in.
                                CookieImportOutcome.IMPORTED -> {
                                    pendingCookiesUrl = urlText
                                    urlText = ""
                                    showSaveCookies = true
                                }
                                CookieImportOutcome.NO_COOKIES ->
                                    snackbarHostState.showSnackbar(noCookiesLabel)
                                CookieImportOutcome.FAILED ->
                                    snackbarHostState.showSnackbar(cookiesFailedLabel)
                            }
                        }
                        }
                    },
                )
            }

            // Issue #409: save the just-imported cookies as a named
            // profile (default name: the site's host). Dismissing
            // keeps the one-shot download — nothing is lost.
            if (showSaveCookies) {
                SaveCookieProfileDialog(
                    defaultName = runCatching { java.net.URL(pendingCookiesUrl).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: pendingCookiesUrl,
                    onSave = { name ->
                        showSaveCookies = false
                        scope.launch {
                            if (downloader.saveCookieProfile(name)) {
                                snackbarHostState.showSnackbar(cookiesSavedLabel)
                            }
                        }
                    },
                    onDismiss = { showSaveCookies = false },
                )
            }

            // Issue #409: pick a saved profile for the URL in the
            // field. The pick starts the download with the profile's
            // cookies — the metered gate waits first, like every
            // other start on this tab.
            if (showCookiePicker) {
                CookieProfilePickerDialog(
                    profiles = cookieProfiles,
                    onPick = { profile ->
                        showCookiePicker = false
                        guardMetered {
                            scope.launch {
                                downloader.startDownloadWithCookieProfile(urlText, profile.id, audioOnly = settingsRepository.load().downloadAudioOnly, subtitles = settingsRepository.load().downloadSubtitles)
                                urlText = ""
                            }
                        }
                    },
                    onDismiss = { showCookiePicker = false },
                )
            }


            if (visibleActive.isEmpty()) {
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
                                guardMetered {
                                    val downloadPrefs = settingsRepository.load()
                                    downloader.startDownload(
                                        item.url,
                                        audioOnly = downloadPrefs.downloadAudioOnly,
                                        subtitles = downloadPrefs.downloadSubtitles,
                                    )
                                }
                            },
                        )
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
            .padding(vertical = 12.dp),
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
            // Issue #350: a thicker bar (8dp, rounded) so the current
            // download's progress is readable at a glance. progress < 0
            // means the size is still unknown (indeterminate).
            if (item.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MpxTheme.colors.primary,
                    trackColor = MpxTheme.colors.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
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
            // Issue #350: the row shows live size / speed / % / ETA on
            // one line, and the current phase on a second line while
            // merging or post-processing, so a stalled-looking bar is
            // explained. With an unknown total the bar is indeterminate
            // but the downloaded size and speed still show. The ETA is
            // only meaningful while bytes are moving.
            val numbers = buildList {
                if (item.downloadedBytes >= 0 && item.totalBytes > 0) {
                    add("${formatSize(item.downloadedBytes)} / ${formatSize(item.totalBytes)}")
                } else if (item.downloadedBytes >= 0) {
                    add(formatSize(item.downloadedBytes))
                }
                if (item.speedBytesPerSec > 0) {
                    add(stringResource(R.string.download_speed, formatSize(item.speedBytesPerSec)))
                }
                if (item.progress >= 0f) add("${item.progress.toInt()}%")
                if (item.phase == DownloadPhase.DOWNLOADING && item.etaSeconds > 0) {
                    add(stringResource(R.string.download_eta, formatEta(item.etaSeconds)))
                }
            }.joinToString(" · ")
            val phaseLabel = stringResource(
                when (item.phase) {
                    DownloadPhase.FETCHING_INFO -> R.string.download_phase_fetching_info
                    DownloadPhase.DOWNLOADING -> R.string.download_phase_downloading
                    DownloadPhase.MERGING -> R.string.download_phase_merging
                    DownloadPhase.POST_PROCESSING -> R.string.download_phase_post_processing
                },
            )
            when (item.phase) {
                DownloadPhase.FETCHING_INFO -> phaseLabel
                DownloadPhase.DOWNLOADING -> numbers.ifBlank { phaseLabel }
                DownloadPhase.MERGING, DownloadPhase.POST_PROCESSING ->
                    if (numbers.isBlank()) phaseLabel else "$numbers\n$phaseLabel"
            }
        }
        DownloadStatus.DONE -> stringResource(R.string.download_complete)
        DownloadStatus.FAILED -> {
            val detail = item.error?.takeIf { it.isNotBlank() }
            if (detail != null) "${stringResource(R.string.download_failed)}: $detail" else stringResource(R.string.download_failed)
        }
        DownloadStatus.CANCELED -> stringResource(R.string.download_canceled)
    }
}


private fun formatEta(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

