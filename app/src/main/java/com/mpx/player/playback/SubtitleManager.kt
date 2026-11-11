package com.mpx.player.playback

import android.net.Uri
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.engine.MpvPlayerEngine
import com.mpx.player.playback.engine.PlayerEngine
import com.mpx.player.playback.engine.ReplaceMediaItemResult
import com.mpx.player.util.SubtitleSiblingFinder
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What [SubtitleManager] needs from the playback coordinator that owns
 * the live queue and the published [PlaybackState]. The manager decides
 * what happens to a subtitle; the host applies the queue/state effects
 * (replacing a queue item, publishing a notification, refreshing the
 * track list) so the manager never touches the coordinator's state
 * directly.
 */
interface SubtitleHost {
    /** The item currently playing, or null when nothing is loaded. */
    val currentItem: PlayableMedia?

    /** The live queue, as the coordinator tracks it. */
    val liveQueue: List<PlayableMedia>

    /** Subtitle tracks as last published in state. */
    val subtitleTracks: List<SubtitleTrackInfo>

    /**
     * Commit an updated queue item after a subtitle add: replace the
     * item at [index] in the live queue, publish it as the current item
     * (with [notification] when non-null), and persist the queue.
     */
    fun commitSubtitleItem(index: Int, updatedItem: PlayableMedia, notification: String?)

    /** The "Loaded subtitle: <name>" banner text for [displayName]. */
    fun subtitleLoadedMessage(displayName: String): String

    /** The "Failed to load subtitle: <name>" banner text for [displayName]. */
    fun subtitleFailedMessage(displayName: String): String

    /** Publish [message] as the current user notification. */
    fun showNotification(message: String)

    /** Re-read the engine's track lists into published state. */
    fun refreshTracksState()
}

/**
 * External-subtitle responsibility, extracted from [PlaybackManager]
 * (migration Phase 3, step 1). Owns subtitle discovery (same-name
 * siblings in the video's folder), the manual-pick load path, the
 * pending-confirmation state for an add the engine could not pin yet,
 * and handing subtitle style settings to the engine.
 *
 * No behaviour change from the [PlaybackManager] code this was moved
 * from — the bodies are verbatim except for the seams below.
 *
 * The Android-dependent pieces arrive through [fileAccess] so the
 * class is unit-testable with fakes and no Android runtime (the repo
 * has no Robolectric): its `detectCodepage` reads a subtitle file's
 * encoding, `hasAllFilesAccess` gates the folder scan, `findSiblings`
 * lists same-name files, and `validateSibling` validates one sibling
 * file, returning its (uri, displayName, mimeType) or null when
 * unsupported. Production wiring passes [FileAccess] from
 * [productionFileAccess]; tests pass fakes. The
 * manager itself works in plain `String` URIs — the coordinator
 * adapts `android.net.Uri` at the boundary.
 *
 * What unit tests cover: pending-state handling, the auto-detect
 * guards, and selection/confirmation delegation. What they cannot
 * cover, and can only be verified on device: the actual mpv sub-add
 * (the fake engine answers synchronously), the real folder scan
 * against device storage, and codepage detection against real files.
 */
class SubtitleManager(
    private val engine: PlayerEngine,
    private val host: SubtitleHost,
    private val scope: CoroutineScope,
    private val fileAccess: FileAccess,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Issue #192: a manual external-subtitle pick the engine could not
    // pin yet (track not visible in track-list when sub-add returned).
    // Re-applied on the next track-list change by picking the newest
    // external track — NOT by display-name label: mpv reports the added
    // track's title as "srt" for fd:// subs, so label matching could
    // never fire (user's v0.3.1 log). Scoped to the item it was picked
    // for, so it can never select a track in a different video.
    private var pendingExternalSubtitleMediaId: String? = null
    /**
     * Issue #332: the display name of the subtitle the pending
     * confirmation (above) is waiting for, so the async success/failure
     * banner can name it. Always set and cleared together with the id.
     */
    private var pendingExternalSubtitleDisplayName: String? = null
    /**
     * Subtitle auto-detect: media ids already scanned for same-name
     * subtitle files this session. A scan is attempted once per item
     * (a folder scan that finds nothing stays attempted — re-scanning
     * on every READY re-fire would be pure disk churn). Cleared on a
     * new session (playQueue) and on stop(), so playing the video
     * again later re-scans — a subtitle added to the folder since is
     * then found. The item's own externalSubtitles list is the second
     * guard: an item that already has external subtitles (manual pick,
     * restore, or an earlier auto-detect) is never scanned again.
     */
    private val autoDetectAttemptedIds = mutableSetOf<String>()

    /**
     * Applies subtitle style settings to the mpv engine, which renders
     * subtitles itself.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        (engine as? MpvPlayerEngine)?.applySubtitleSettings(settings)
    }

    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        // Issue #192: a manual pick (sheet tap / Off) supersedes any
        // pending external pick — never let a stale pending re-select
        // over the user's explicit choice.
        pendingExternalSubtitleMediaId = null
        pendingExternalSubtitleDisplayName = null
        engine.selectSubtitleTrack(groupIndex, trackIndex)
        host.refreshTracksState()
    }

    /**
     * Loads an external subtitle file (SRT / VTT) into the currently playing media item.
     * Replaces the media item in the engine so the new sidecar subtitle track is loaded,
     * maintaining the current playback position and state, and marks the track to be auto-selected.
     *
     * Issue #332: returns whether the subtitle was ACCEPTED for loading —
     * never an unconditional success any more. The old code returned true
     * and showed "External subtitle loaded" even when every sub-add had
     * failed, because `mpv.command()` never throws for a rejected file.
     * ISSUED/QUEUED are accepted and their real outcome arrives
     * asynchronously via [onExternalSubtitleConfirmed], which shows the
     * success/failure banner; FAILED (nothing could be attempted) shows
     * the failure banner here and returns false.
     *
     * @param uriString the subtitle location as a string
     * (the coordinator passes `Uri.toString()` — the stored
     * [ExternalSubtitle.uri] is a string too).
     */
    fun loadExternalSubtitle(uriString: String, displayName: String, mimeType: String): Boolean {
        val currentItem = host.currentItem ?: return false
        val index = engine.currentMediaItemIndex
        if (index < 0 || index >= host.liveQueue.size) return false

        val ext = ExternalSubtitle(
            uri = uriString,
            name = displayName,
            mimeType = mimeType,
            // Issue #441: detect a legacy Arabic encoding (Windows-1256)
            // so the engine can tell mpv the right codepage before the
            // sub-add. Null means mpv's default (valid UTF-8 files).
            codepage = fileAccess.detectCodepage(uriString),
        )

        // Avoid duplicate entry of the same URI
        val existingSubs = currentItem.externalSubtitles.filter { it.uri != ext.uri }
        val updatedSubs = existingSubs + ext
        val updatedItem = currentItem.copy(externalSubtitles = updatedSubs)

        val wasPlaying = engine.isPlaying

        // Replace current media item in engine timeline. The raw item is
        // handed over — the engine resolves only the subtitle URIs itself
        // (content:// subtitles need the same treatment as content://
        // media); the media URI is never resolved here, because
        // replaceMediaItem never reloads the file and the fd would leak
        // (issue #219). The stored item keeps the original URI so the
        // persisted queue stays restorable.
        return when (val result = engine.replaceMediaItem(index, updatedItem)) {
            ReplaceMediaItemResult.ISSUED, ReplaceMediaItemResult.QUEUED -> {
                // Issue #192: the engine pins sid to the new track; keep
                // this as the second chance in case the track was not in
                // track-list yet when sub-add returned. Issue #332: the
                // success/failure banner is NOT shown here — it arrives
                // with the async confirmation, once the track actually
                // appears (or the timeout reports it never did).
                pendingExternalSubtitleMediaId = currentItem.id
                pendingExternalSubtitleDisplayName = displayName
                commitSubtitleAdd(index, updatedItem, wasPlaying, notification = null)
                true
            }
            ReplaceMediaItemResult.ALREADY_ADDED -> {
                // No sub-add was issued — the subtitle was already in the
                // item. It is loaded iff mpv's track-list currently holds
                // at least as many external tracks as the item lists
                // external subtitles (checked live on the engine, not the
                // cached state, so a track that only just appeared still
                // counts). A re-pick of a subtitle that never loaded
                // reports failure instead of the old lying success.
                val loadedExternalCount = engine.getSubtitleTracks().count { it.isExternal }
                if (loadedExternalCount >= updatedItem.externalSubtitles.size) {
                    pendingExternalSubtitleMediaId = null
                    pendingExternalSubtitleDisplayName = null
                    commitSubtitleAdd(
                        index, updatedItem, wasPlaying,
                        notification = host.subtitleLoadedMessage(displayName),
                    )
                    true
                } else {
                    commitSubtitleAdd(index, updatedItem, wasPlaying, notification = null)
                    host.showNotification(host.subtitleFailedMessage(displayName))
                    false
                }
            }
            ReplaceMediaItemResult.FAILED -> {
                // Nothing was attempted (unreadable content://, missing
                // file, or no new subtitles). Re-sync the engine's queue
                // with the original item — replaceMediaItem stored the
                // updated item even though it added nothing — and report
                // the failure instead of the old unconditional success.
                engine.replaceMediaItem(index, currentItem)
                restorePlaybackAfterSubtitleAdd(wasPlaying)
                host.showNotification(host.subtitleFailedMessage(displayName))
                false
            }
        }
    }

    /**
     * Subtitle auto-detect (MX behaviour): when a local video's file
     * has loaded, look in its folder for subtitle files with the same
     * name (`movie.srt`, `movie.en.srt`, `movie.ar.srt`) and load them
     * through the same [loadExternalSubtitle] path a manual pick uses
     * — the engine sub-adds them and selects the last one added, and
     * the #332 confirmation shows the usual "Loaded subtitle" banner
     * for it. Matches are added worst-first so the best match (the
     * exact stem) is added last and ends up selected.
     *
     * Runs only for local videos ([PlayableMedia.filePath] present)
     * with no external subtitles yet. A manual pick made while the
     * folder scan is running wins: the state is re-checked on Main
     * before loading, and an item that gained external subtitles in
     * the meantime is skipped. Network streams have no folder, so
     * nothing is attempted for them. A folder the app cannot list
     * (no All files access on Android 11+) simply yields no matches
     * — the subtitle page explains that and offers the grant.
     */
    fun maybeAutoDetectSubtitles() {
        val item = host.currentItem ?: return
        if (!item.isVideo) return
        val videoPath = item.filePath?.takeIf { it.isNotBlank() } ?: return
        if (item.externalSubtitles.isNotEmpty()) return
        // Without All files access the folder scan cannot see subtitle
        // files on Android 11+ and mpv cannot open a raw path either
        // (issue #444) — attempting would only produce a "failed to
        // load" banner for a file the user never asked for. Checked
        // before marking the id attempted, so granting the permission
        // later in the session still allows a scan on the next video.
        if (!fileAccess.hasAllFilesAccess()) return
        if (!autoDetectAttemptedIds.add(item.id)) return
        scope.launch(ioDispatcher) {
            val siblings = fileAccess.findSiblings(videoPath)
            if (siblings.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                val current = host.currentItem ?: return@withContext
                if (current.id != item.id) return@withContext
                if (current.externalSubtitles.isNotEmpty()) return@withContext
                // Worst-first: the engine selects the last sub-added
                // track, so the best match must be added last.
                for (file in siblings.asReversed()) {
                    val validated = fileAccess.validateSibling(file) ?: continue
                    loadExternalSubtitle(
                        uriString = validated.first,
                        displayName = validated.second,
                        mimeType = validated.third,
                    )
                }
            }
        }
    }

    /**
     * Issue #332: commits a subtitle add to the coordinator's queue and state
     * (issue #149: the queue item itself changed), restoring the playback
     * position/state afterwards. [notification] is shown only when
     * non-null — an accepted (ISSUED/QUEUED) add shows nothing yet; its
     * banner arrives with the async confirmation.
     */
    private fun commitSubtitleAdd(
        index: Int,
        updatedItem: PlayableMedia,
        wasPlaying: Boolean,
        notification: String?,
    ) {
        restorePlaybackAfterSubtitleAdd(wasPlaying)
        host.commitSubtitleItem(index, updatedItem, notification)
    }

    /**
     * Issue #332: plain seek, NOT seekTo(index, pos): the mpv engine's
     * replaceMediaItem never reloads the file (sub-add only), so the
     * position is already where it was — no reload is needed at all.
     *
     * Issue #357: there is deliberately NO seek here at all any more.
     * A seek makes mpv re-evaluate track selection, and with `sid`
     * still at "auto" it falls back to the default-flagged embedded
     * track — unselecting the external subtitle that was just sub-added
     * (user's v0.3.1 log: sid=2 selected, sid=1 back two seconds
     * later). Issue #372 fixed the deeper half of that: the engine now
     * remembers the picked external subtitle by identity and re-applies
     * `sid` to it on every track-list refresh, so a re-evaluation can no
     * longer strand the pick — but the seek is still gone, because
     * there is nothing for it to do here at all.
     */
    private fun restorePlaybackAfterSubtitleAdd(wasPlaying: Boolean) {
        if (wasPlaying && !engine.isPlaying) {
            engine.play()
        }
    }

    /**
     * Issue #192: second chance for a manual external pick the engine
     * could not pin synchronously — select the newest external track
     * (mpv appends added tracks, so the last one is the one just
     * picked). Only for the item it was picked for; a manual pick in
     * the sheet clears it first, and a different video clears it here
     * instead of mis-selecting. Called from the coordinator's
     * `onTracksChanged`, after it has refreshed the track state.
     */
    fun onTracksChanged() {
        pendingExternalSubtitleMediaId?.let { mediaId ->
            if (host.currentItem?.id == mediaId) {
                val newestExternal = host.subtitleTracks.lastOrNull { it.isExternal }
                if (newestExternal != null) {
                    selectSubtitleTrack(newestExternal.groupIndex, newestExternal.trackIndex)
                    pendingExternalSubtitleMediaId = null
                    pendingExternalSubtitleDisplayName = null
                }
            } else {
                pendingExternalSubtitleMediaId = null
                pendingExternalSubtitleDisplayName = null
            }
        }
    }

    /**
     * Issue #332: the async verdict on a manually added external
     * subtitle (see [PlayerEngine.Listener.onExternalSubtitleConfirmed]).
     * Ignores confirmations the manager is not waiting for (queue
     * restores confirm the same way) and confirmations for an item
     * that is no longer current — the user has moved on, so no banner
     * should arrive for the old pick.
     */
    fun onExternalSubtitleConfirmed(mediaId: String, success: Boolean) {
        if (pendingExternalSubtitleMediaId != mediaId) return
        val displayName = pendingExternalSubtitleDisplayName ?: return
        pendingExternalSubtitleMediaId = null
        pendingExternalSubtitleDisplayName = null
        if (host.currentItem?.id != mediaId) return
        host.showNotification(
            if (success) {
                host.subtitleLoadedMessage(displayName)
            } else {
                host.subtitleFailedMessage(displayName)
            },
        )
    }

    /**
     * Forget which items have been auto-detect scanned this session.
     * Called on a new session (playQueue) and on stop(), so playing a
     * video again later re-scans — a subtitle added to the folder
     * since is then found. The pending pick is deliberately NOT
     * cleared here: stop() never cleared it, and a new session's
     * manual pick replaces it through [selectSubtitleTrack] /
     * [loadExternalSubtitle] instead.
     */
    fun clearAutoDetectAttempts() {
        autoDetectAttemptedIds.clear()
    }

    companion object {
        /**
         * Production wiring for the Android-dependent seams, from the
         * coordinator's application context. Kept here so
         * [PlaybackManager] stays a one-line construction call.
         */
        fun productionFileAccess(context: android.content.Context): FileAccess {
            return FileAccess(
                detectCodepage = { uriString ->
                    SubtitleUtils.detectSubtitleCodepage(context, Uri.parse(uriString))
                },
                hasAllFilesAccess = { SubtitleSiblingFinder.hasAllFilesAccess(context) },
                findSiblings = SubtitleSiblingFinder::findSiblings,
                validateSibling = { file ->
                    val uri = Uri.fromFile(file)
                    when (val validation = SubtitleUtils.validateSubtitleFile(context, uri)) {
                        is SubtitleValidationResult.Supported -> Triple(
                            validation.uri.toString(),
                            validation.displayName,
                            validation.mimeType,
                        )
                        is SubtitleValidationResult.Unsupported -> null
                    }
                },
            )
        }
    }

    /**
     * The Android-dependent seams, grouped so the constructor stays
     * readable. See [productionFileAccess] for the real
     * implementation; tests build this struct from fakes.
     */
    data class FileAccess(
        val detectCodepage: (uriString: String) -> String?,
        val hasAllFilesAccess: () -> Boolean,
        val findSiblings: (videoPath: String) -> List<File>,
        val validateSibling: (file: File) -> Triple<String, String, String>?,
    )
}
