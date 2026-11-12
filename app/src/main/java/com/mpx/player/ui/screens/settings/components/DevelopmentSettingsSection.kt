package com.mpx.player.ui.screens.settings.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mpx.player.BuildConfig
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.buildDiagnosticsReport
import com.mpx.player.playback.buildDiagnosticsSummary
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.ui.components.MpvLogShareButton
import com.mpx.player.ui.screens.settings.DiagnosticsMediaCounts
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.MediaPermissions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DevDiagnosticsData(
    val appId: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val buildType: String = "",
    val targetSdk: String = "",
    val minSdk: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val dbPath: String = "",
    val dbSizeFormatted: String = "",
    val dbWalFormatted: String = "",
    val dbRecordStats: String = "",
    val hasVideoPerm: Boolean = false,
    val hasAudioPerm: Boolean = false,
    val storageVolumes: String = "",
    val videoFoldersCount: Int = 0,
    val videosCount: Int = 0,
    val audioTracksCount: Int = 0,
    val audioFoldersCount: Int = 0,
    val audioAlbumsCount: Int = 0,
    val audioArtistsCount: Int = 0,
    val playlistsCount: Int = 0,
    val lastScannedTime: String = "",
)

@Composable
fun DevelopmentSettingsSection(
    playbackState: PlaybackState,
    settings: AppSettings,
    // Issue #299: the loaded stats live in SettingsScreen, not here —
    // this section is a LazyColumn item whose remember was dropped
    // whenever it left composition, re-running the five MediaStore scans
    // on every return. The cache is null until the first load (or a
    // fresh Settings visit), non-null afterwards.
    cachedDiagnostics: DevDiagnosticsData?,
    onDiagnosticsLoaded: (DevDiagnosticsData) -> Unit,
    onLoadRecordCounts: suspend () -> List<Int>,
    onLoadMediaCounts: suspend (hasVideo: Boolean, hasAudio: Boolean) -> DiagnosticsMediaCounts,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshStats() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            val recordCounts = runCatching { onLoadRecordCounts() }
                .getOrElse { listOf(0, 0, 0, 0) }
            val stats = loadDevDiagnostics(context, recordCounts, onLoadMediaCounts)
            onDiagnosticsLoaded(stats)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        // Issue #299: only load when there is nothing cached — scrolling
        // the section out of composition and back, or switching categories
        // inside Settings, reuses the cached stats instead of re-scanning.
        // The Refresh button below still calls refreshStats() directly.
        if (cachedDiagnostics == null) refreshStats()
    }

    val stats = cachedDiagnostics

    Column(modifier = modifier) {
        // Section 1: App & Build Information
        DiagnosticSection(title = stringResource(R.string.dev_section_app_build)) {
            DiagnosticRow(
                label = stringResource(R.string.dev_label_package),
                value = stats?.appId ?: context.packageName,
                isMonospace = true,
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_version),
                value = stats?.versionName ?: BuildConfig.VERSION_NAME,
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_build_code),
                value = stats?.versionCode ?: BuildConfig.VERSION_CODE.toString(),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_build_type),
                value = stats?.buildType ?: "Debug",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_target_sdk),
                value = stats?.targetSdk ?: "34",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_min_sdk),
                value = stats?.minSdk ?: "24",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_device),
                value = stats?.deviceModel ?: "${Build.MANUFACTURER} ${Build.MODEL}",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_os_version),
                value = stats?.osVersion ?: "Android ${Build.VERSION.RELEASE}",
            )
        }

        Spacer(Modifier.height(8.dp))

        // Section 2: Room SQLite Database
        DiagnosticSection(title = stringResource(R.string.dev_section_database)) {
            DiagnosticRow(
                label = stringResource(R.string.dev_label_db_name),
                value = "mpx_player.db (Room v1)",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_db_path),
                value = stats?.dbPath ?: context.getDatabasePath("mpx_player.db").absolutePath,
                isMonospace = true,
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_db_size),
                value = stats?.dbSizeFormatted ?: stringResource(R.string.dev_val_refreshing),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_db_wal_size),
                value = stats?.dbWalFormatted ?: stringResource(R.string.dev_val_refreshing),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_db_records),
                value = stats?.dbRecordStats ?: stringResource(R.string.dev_val_refreshing),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Section 3: MediaStore Library Scan
        DiagnosticSection(title = stringResource(R.string.dev_section_media_scan)) {
            val permVideo = if (stats?.hasVideoPerm == true) {
                stringResource(R.string.dev_val_perm_granted)
            } else {
                stringResource(R.string.dev_val_perm_denied)
            }
            val permAudio = if (stats?.hasAudioPerm == true) {
                stringResource(R.string.dev_val_perm_granted)
            } else {
                stringResource(R.string.dev_val_perm_denied)
            }
            DiagnosticRow(
                label = stringResource(R.string.dev_label_permissions),
                value = stringResource(R.string.dev_val_perm_video, permVideo, permAudio),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_scan_volumes),
                value = stats?.storageVolumes ?: "primary",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_video_folders),
                value = stringResource(R.string.dev_count_folders, stats?.videoFoldersCount ?: 0),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_scanned_videos),
                value = stringResource(R.string.dev_count_videos, stats?.videosCount ?: 0),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_audio_tracks),
                value = stringResource(R.string.dev_count_tracks, stats?.audioTracksCount ?: 0),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_audio_folders),
                value = stringResource(R.string.dev_count_folders, stats?.audioFoldersCount ?: 0),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_audio_albums),
                value = stringResource(R.string.dev_count_albums, stats?.audioAlbumsCount ?: 0),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_audio_artists),
                value = stringResource(R.string.dev_count_artists, stats?.audioArtistsCount ?: 0),
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_playlists),
                value = stringResource(R.string.dev_count_playlists, stats?.playlistsCount ?: 0),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Section 4: Decoder & Playback Engine
        DiagnosticSection(title = stringResource(R.string.dev_section_playback_engine)) {
            val configuredDecoderDesc = when (settings.decoderMode) {
                "HW" -> stringResource(R.string.dev_decoder_hw)
                "HW+" -> stringResource(R.string.dev_decoder_hwplus)
                "SW" -> stringResource(R.string.dev_decoder_sw)
                else -> settings.decoderMode
            }
            val playerStateText = when (playbackState.playbackState) {
                PlayerEngine.STATE_READY -> if (playbackState.isPlaying) stringResource(R.string.dev_state_playing) else stringResource(R.string.dev_state_paused)
                PlayerEngine.STATE_BUFFERING -> stringResource(R.string.dev_state_buffering)
                PlayerEngine.STATE_ENDED -> stringResource(R.string.dev_state_ended)
                PlayerEngine.STATE_IDLE -> stringResource(R.string.dev_state_idle)
                else -> stringResource(R.string.dev_state_idle)
            }
            val activeMediaText = playbackState.currentItem?.let {
                "${it.title} (${if (it.isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_audio)})"
            } ?: stringResource(R.string.dev_val_none_idle)

            val activeCodec = playbackState.activeDecoderName ?: stringResource(R.string.dev_val_none_idle)
            val isSwCodec = playbackState.activeDecoderName?.let { MpxMediaCodecSelector.isSoftwareCodecName(it) }
            val codecType = when {
                playbackState.activeDecoderName == null -> stringResource(R.string.dev_configured_for, settings.decoderMode)
                isSwCodec == true -> stringResource(R.string.dev_val_codec_sw)
                else -> stringResource(R.string.dev_val_codec_hw)
            }
            val fallbackNote = playbackState.decoderFallbackNote ?: stringResource(R.string.dev_val_direct_codec)

            DiagnosticRow(
                label = stringResource(R.string.dev_label_engine_name),
                value = "libmpv (mpv-android-lib 0.1.12)",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_engine_type),
                value = "C/C++ Native libmpv Core",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_decoder_mode),
                value = "${settings.decoderMode} ($configuredDecoderDesc)",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_player_state),
                value = playerStateText,
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_active_media),
                value = activeMediaText,
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_active_decoder),
                value = activeCodec,
                isMonospace = playbackState.activeDecoderName != null,
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_codec_type),
                value = codecType,
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_decoder_fallback),
                value = fallbackNote,
            )
            val metrics = playbackState.playbackMetrics
            DiagnosticRow(
                label = stringResource(R.string.dev_label_metrics_ready),
                value = metrics?.lastTimeToReadyMs?.let { "$it ms" } ?: "—",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_metrics_first_frame),
                value = metrics?.lastTimeToFirstFrameMs?.let { "$it ms" } ?: "—",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_metrics_seek),
                value = metrics?.lastSeekLatencyMs?.let { "$it ms" } ?: "—",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_metrics_fallbacks),
                value = metrics?.let { "${it.decoderFallbackCount}" } ?: "—",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_metrics_errors),
                value = metrics?.let { "${it.hardErrorCount}" } ?: "—",
            )
            val stage = playbackState.mpvLoadStage
            val yes = stringResource(R.string.dev_val_yes)
            val no = stringResource(R.string.dev_val_no)
            DiagnosticRow(
                label = stringResource(R.string.dev_label_mpv_initialized),
                value = stage?.let { if (it.initialized) yes else no } ?: "—",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_mpv_surface),
                value = stage?.let { if (it.surfaceAttached) yes else no } ?: "—",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_mpv_load_issued),
                value = stage?.let { if (it.loadIssued) yes else no } ?: "—",
            )
            DiagnosticRow(
                label = stringResource(R.string.dev_label_mpv_last_event),
                value = stage?.lastEvent ?: "—",
                isMonospace = stage != null,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Section 5: Diagnostics export
        DiagnosticSection(title = stringResource(R.string.dev_section_diagnostics_export)) {
            OutlinedButton(
                onClick = {
                    val text = buildDiagnosticsSummary(playbackState, settings)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("MPx diagnostics", text))
                    Toast.makeText(context, context.getString(R.string.dev_val_copied), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MpxShapes.small,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MpxTheme.colors.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dev_btn_copy_summary),
                    color = MpxTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    try {
                        val text = buildDiagnosticsReport(playbackState, settings, context.packageName)
                        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        val file = File(dir, "mpx-diagnostics-$stamp.txt")
                        file.writeText(text)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "MPx diagnostics")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.dev_val_share_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MpxShapes.small,
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MpxTheme.colors.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dev_btn_share_file),
                    color = MpxTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        MpvLogShareButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.dev_btn_share_mpv_log),
        )

        Spacer(Modifier.height(10.dp))

        // Refresh Diagnostics Button
        OutlinedButton(
            onClick = { refreshStats() },
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
            shape = MpxShapes.small,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MpxTheme.colors.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRefreshing) stringResource(R.string.dev_val_refreshing) else stringResource(R.string.dev_btn_refresh_diagnostics),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        stats?.lastScannedTime?.takeIf { it.isNotBlank() }?.let { time ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dev_label_last_updated, time),
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
fun DiagnosticSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MpxTheme.colors.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MpxShapes.small)
                .background(MpxTheme.colors.surfaceElevated)
                .border(0.5.dp, MpxTheme.colors.border.copy(alpha = 0.5f), MpxShapes.small)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun DiagnosticRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = if (isMonospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MpxTheme.colors.textPrimary,
            modifier = Modifier.weight(0.58f),
        )
    }
}

private suspend fun loadDevDiagnostics(
    context: Context,
    recordCounts: List<Int>,
    onLoadMediaCounts: suspend (Boolean, Boolean) -> DiagnosticsMediaCounts,
): DevDiagnosticsData = withContext(Dispatchers.IO) {
    val pInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: Exception) {
        null
    }
    val versionName = pInfo?.versionName ?: BuildConfig.VERSION_NAME
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pInfo?.longVersionCode?.toString() ?: BuildConfig.VERSION_CODE.toString()
    } else {
        @Suppress("DEPRECATION")
        pInfo?.versionCode?.toString() ?: BuildConfig.VERSION_CODE.toString()
    }
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val buildType = if (isDebug) "Debug" else "Release"
    val targetSdk = context.applicationInfo.targetSdkVersion.toString()
    val minSdk = "24 (Android 7.0 Nougat)"
    val device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
    val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    val dbFile = context.getDatabasePath("mpx_player.db")
    val walFile = File(dbFile.path + "-wal")
    val shmFile = File(dbFile.path + "-shm")
    val dbExists = dbFile.exists()
    val dbSizeBytes = if (dbExists) dbFile.length() else 0L
    val walSizeBytes = if (walFile.exists()) walFile.length() else 0L
    val shmSizeBytes = if (shmFile.exists()) shmFile.length() else 0L
    val totalDbBytes = dbSizeBytes + walSizeBytes + shmSizeBytes
    val formattedSize = if (dbExists) {
        val formatted = Formatter.formatFileSize(context, totalDbBytes)
        "$formatted ($totalDbBytes bytes)"
    } else {
        context.getString(R.string.dev_label_db_not_created)
    }
    val walFormatted = if (walFile.exists()) {
        val formatted = Formatter.formatFileSize(context, walSizeBytes)
        "$formatted ($walSizeBytes bytes)"
    } else {
        "0 B (no active WAL log)"
    }

    val recordStats = context.getString(
        R.string.dev_db_records_summary,
        recordCounts.getOrElse(0) { 0 },
        recordCounts.getOrElse(1) { 0 },
        recordCounts.getOrElse(2) { 0 },
        recordCounts.getOrElse(3) { 0 },
    )

    val hasVideoPerm = MediaPermissions.hasVideoPermission(context)
    val hasAudioPerm = MediaPermissions.hasAudioPermission(context)
    val mediaCounts = onLoadMediaCounts(hasVideoPerm, hasAudioPerm)
    val volumes = mediaCounts.volumes
    val videoFolders = mediaCounts.videoFolders
    val videoCount = mediaCounts.videoCount
    val audioTracks = mediaCounts.audioTracks
    val audioFolders = mediaCounts.audioFolders
    val audioAlbums = mediaCounts.audioAlbums
    val audioArtists = mediaCounts.audioArtists
    val playlists = mediaCounts.playlists

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    DevDiagnosticsData(
        appId = context.packageName,
        versionName = versionName,
        versionCode = versionCode,
        buildType = buildType,
        targetSdk = targetSdk,
        minSdk = minSdk,
        deviceModel = device,
        osVersion = osVersion,
        dbPath = dbFile.absolutePath,
        dbSizeFormatted = formattedSize,
        dbWalFormatted = walFormatted,
        dbRecordStats = recordStats,
        hasVideoPerm = hasVideoPerm,
        hasAudioPerm = hasAudioPerm,
        storageVolumes = volumes,
        videoFoldersCount = videoFolders,
        videosCount = videoCount,
        audioTracksCount = audioTracks,
        audioFoldersCount = audioFolders,
        audioAlbumsCount = audioAlbums,
        audioArtistsCount = audioArtists,
        playlistsCount = playlists,
        lastScannedTime = timeFormat.format(Date()),
    )
}
