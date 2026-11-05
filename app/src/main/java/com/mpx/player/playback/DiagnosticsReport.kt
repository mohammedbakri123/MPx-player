package com.mpx.player.playback

import android.os.Build
import androidx.media3.common.Player
import com.mpx.player.BuildConfig
import com.mpx.player.domain.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics report builder (report-builder worktree).
 *
 * Pure function: assembles the full diagnostics report text from already
 * observed state only. It performs no I/O, touches no clipboard, and opens
 * no share sheet — the sibling UI worktree owns the buttons, the file
 * writing, and the share/clipboard actions, and calls [buildDiagnosticsReport].
 *
 * Sources, and nothing else:
 * - [PlaybackState]: current item, queue, player state, active decoder,
 *   decoder fallback note, [PlaybackState.mpvLoadStage],
 *   [PlaybackState.playbackMetrics], and [PlaybackState.userNotification]
 *   as the last stored message (there is no separate stored error history
 *   in state; the mpv error-log lines are the sibling mpv-log worktree's
 *   job, not this builder's).
 * - [AppSettings]: the settings snapshot (engine, decoder mode, language,
 *   theme, playback flags).
 * - [Build]/[BuildConfig]: device and app version header.
 *
 * Privacy rules enforced here, not left to the caller:
 * - Only the current item's file NAME and extension are emitted, never the
 *   full device path (uris can be real filesystem paths).
 * - Nothing is ever invented: unmeasured values render as "not observed".
 * - Labels are fixed English: the report is a diagnostic artifact pasted
 *   into chat, like logcat. No new user-facing UI strings are added here,
 *   so no locale work is needed for this file.
 */
fun buildDiagnosticsReport(
    state: PlaybackState,
    settings: AppSettings,
    packageName: String,
): String {
    val notObserved = "not observed"
    val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())

    val playerStateText = when (state.playbackState) {
        Player.STATE_READY -> if (state.isPlaying) "Playing" else "Paused"
        Player.STATE_BUFFERING -> "Buffering"
        Player.STATE_ENDED -> "Ended"
        else -> "Idle"
    }
    val decoderDesc = when (settings.decoderMode) {
        "HW" -> "Hardware (MediaCodec)"
        "HW+" -> "Hardware Plus (Async Queue)"
        "SW" -> "Software (CPU Codecs)"
        else -> settings.decoderMode
    }
    val currentItem = state.currentItem
    val currentFileName = currentItem?.let {
        val rawName = it.uri.substringAfterLast('/')
        if (it.uri.startsWith("content://")) it.title else rawName.ifBlank { it.title }
    }
    val currentExtension = currentFileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }

    val metrics = state.playbackMetrics
    val stage = state.mpvLoadStage
    val lastMessage = state.userNotification?.takeIf { it.isNotBlank() }

    return buildList {
        add("MPx Player diagnostics")
        add("Generated: $generatedAt")
        add("")
        add("== App & device ==")
        add("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        add("Package: $packageName")
        add("Device: ${Build.MODEL}")
        add("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        add("")
        add("== Settings ==")
        add("Playback engine: ${settings.playerEngine}")
        add("Decoder mode: ${settings.decoderMode} ($decoderDesc)")
        add("Language: ${settings.appLanguage}")
        add("Theme: ${settings.appTheme}")
        add("Accent palette: ${settings.colorPalette}")
        add("Background play: ${if (settings.backgroundPlay) "yes" else "no"}")
        add("Popup play: ${if (settings.popupPlayEnabled) "yes" else "no"}")
        add("Resume playback: ${if (settings.resumePlayback) "yes" else "no"}")
        add("Gestures: ${if (settings.gesturesEnabled) "yes" else "no"}")
        add("Default speed: ${settings.defaultPlaybackSpeed}x")
        add("Auto rotation: ${if (settings.autoScreenRotation) "yes" else "no"}")
        add("")
        add("== Playback now ==")
        add("Player state: $playerStateText")
        if (currentItem != null) {
            add("Current item: ${currentFileName ?: notObserved}${currentExtension?.let { " (.$it)" } ?: ""} (${if (currentItem.isVideo) "Video" else "Audio"})")
        } else {
            add("Current item: $notObserved")
        }
        add("Queue size: ${state.queue.size}")
        add("Active decoder: ${state.activeDecoderName ?: notObserved}")
        add("Decoder fallback: ${state.decoderFallbackNote ?: notObserved}")
        add("")
        if (settings.playerEngine == "MPV") {
            add("== mpv stage ==")
            add("MPV initialized: ${stage?.let { if (it.initialized) "yes" else "no" } ?: notObserved}")
            add("MPV surface attached: ${stage?.let { if (it.surfaceAttached) "yes" else "no" } ?: notObserved}")
            add("MPV load issued: ${stage?.let { if (it.loadIssued) "yes" else "no" } ?: notObserved}")
            add("MPV last event: ${stage?.lastEvent ?: notObserved}")
            add("")
        }
        add("== Metrics ==")
        add("Time to ready: ${metrics?.lastTimeToReadyMs?.let { "$it ms" } ?: notObserved}")
        add("Time to first frame: ${metrics?.lastTimeToFirstFrameMs?.let { "$it ms" } ?: notObserved}")
        add("Seek latency: ${metrics?.lastSeekLatencyMs?.let { "$it ms" } ?: notObserved}")
        add("Decoder fallbacks: ${metrics?.decoderFallbackCount?.toString() ?: notObserved}")
        add("Playback errors: ${metrics?.hardErrorCount?.toString() ?: notObserved}")
        add("")
        add("== Session messages ==")
        if (lastMessage != null) {
            add("Last message: $lastMessage")
        } else {
            add("Last message: $notObserved")
        }
    }.joinToString("\n")
}

/** Short plain-text summary for the clipboard button (same observed values
 * as the full report, condensed). Kept here so the UI never ships a stub. */
fun buildDiagnosticsSummary(
    state: PlaybackState,
    settings: AppSettings,
    isMpv: Boolean,
): String {
    val notObserved = "not observed"
    val metrics = state.playbackMetrics
    val stage = state.mpvLoadStage
    val lines = mutableListOf<String>()
    lines += "MPx diagnostics summary"
    lines += "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    lines += "Engine: ${if (isMpv) "mpv" else "Media3"} (setting: ${settings.playerEngine})"
    lines += "Decoder mode: ${settings.decoderMode}"
    lines += "Current item: ${state.currentItem?.let { "${it.title} (${if (it.isVideo) "Video" else "Audio"})" } ?: notObserved}"
    lines += "Active decoder: ${state.activeDecoderName ?: notObserved}"
    if (isMpv) {
        lines += "MPV initialized: ${stage?.let { if (it.initialized) "yes" else "no" } ?: notObserved}"
        lines += "MPV surface attached: ${stage?.let { if (it.surfaceAttached) "yes" else "no" } ?: notObserved}"
        lines += "MPV load issued: ${stage?.let { if (it.loadIssued) "yes" else "no" } ?: notObserved}"
        lines += "MPV last event: ${stage?.lastEvent ?: notObserved}"
    }
    lines += "Time to ready: ${metrics?.lastTimeToReadyMs?.let { "$it ms" } ?: notObserved}"
    lines += "Time to first frame: ${metrics?.lastTimeToFirstFrameMs?.let { "$it ms" } ?: notObserved}"
    lines += "Seek latency: ${metrics?.lastSeekLatencyMs?.let { "$it ms" } ?: notObserved}"
    lines += "Decoder fallbacks: ${metrics?.decoderFallbackCount?.toString() ?: notObserved}"
    lines += "Playback errors: ${metrics?.hardErrorCount?.toString() ?: notObserved}"
    return lines.joinToString("\n")
}
