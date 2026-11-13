package com.mpx.player.playback.engine

import android.net.Uri
import android.util.Log
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.ExternalSubtitle
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo
import com.mpx.player.playback.subtitleMpvOptions
import com.mpx.player.util.SubtitleUtils
import java.io.File

/**
 * Subtitle rendering, codepage application, and track confirmation under libmpv.
 *
 * Owns the subtitle style options ([applySubtitleSettings], via the [subtitleMpvOptions] mapping),
 * the subtitle codepage configuration ([applySubtitleCodepage]), pending subtitle queue dispatch
 * upon `FILE_LOADED`, remembered external subtitle reapplication ([reapplyRememberedPick]),
 * external subtitle addition confirmation (#332), and dynamic subtitle replacement ([replaceMediaItem]).
 *
 * Collaborators:
 * - [MpvCoreEngine]: provides access to the native port ([MpvCoreEngine.port]), main thread handler
 *   ([MpvCoreEngine.mainHandler]), and current queue items.
 * - [MpvTrackSelection]: queries and updates subtitle track selections.
 *
 * Invariants:
 * - Subtitle confirmation timeout: adding an external subtitle (`sub-add`) arms a 5000 ms timeout
 *   via `core.mainHandler.postDelayed`. If the subtitle track fails to appear in mpv's track-list
 *   within 5000 ms, the timeout fires, resets pending pin state, and notifies confirmation failure.
 * - Codepage isolation (#441): applying a codepage sets mpv's `"sub-codepage"` property; when a
 *   subsequent subtitle has no explicit codepage, it is reset to `"auto"` so earlier codepages do
 *   not bleed into subsequent media loads.
 * - Embedded vs external precedence: selecting an embedded subtitle or "Off" clears remembered
 *   external picks; remembered picks are restored on file load or track changes only when the
 *   track appears and no conflicting manual pick is active.
 * - Unit testing: thoroughly verified by JVM unit tests using `FakeMpvPort` and `DirectEngineHandler`
 *   (see `MpvSubtitleRendererTest`).
 */
internal class MpvSubtitleRenderer(
    private val core: MpvCoreEngine,
    private val trackSelection: MpvTrackSelection,
) {

    companion object {
        private const val TAG = "MpvPlayerEngine"

        /**
         * Issue #332: how long to wait for a sub-added external subtitle
         * track to appear in track-list before reporting the add as
         * failed. Local .srt/.vtt files appear in well under a second;
         * 5s leaves room for slow storage. Issue #372: the timeout also
         * clears the armed pin state, so a track that lands after it
         * still shows (sub-add "select" selected it) but is no longer
         * remembered — the next re-evaluation can lose it, and the
         * failure banner stands as the honest verdict for a 5s-late
         * track.
         */
        private const val SUBTITLE_CONFIRMATION_TIMEOUT_MS = 5_000L

        // Phase 4 step 1: the core variant — takes the core explicitly
        // so the core's init can apply the saved settings before the
        // facade's `core` property is assigned (see the Host above).
        // Kept as a companion function for exactly that call: the
        // instance [applySubtitleSettings] below delegates to it.
        internal fun applySubtitleSettingsTo(core: MpvCoreEngine, settings: AppSettings) {
            if (!core.mpvInitialized || core.isReleased) return
            try {
                val options = subtitleMpvOptions(settings)
                options.forEach { (key, value) ->
                    core.setRuntimeProperty(key, value)
                }
                Log.d(TAG, "Applied subtitle settings: sub-visibility=${options["sub-visibility"]}, " +
                    "sub-font=${options["sub-font"]}, sub-font-size=${options["sub-font-size"]}, " +
                    "sub-color=${options["sub-color"]}, sub-border-color=${options["sub-border-color"]}, " +
                    "sub-border-size=${options["sub-border-size"]}, sub-back-color=${options["sub-back-color"]}, " +
                    "sub-ass-override=${options["sub-ass-override"] ?: "default"}, " +
                    "sub-delay=${options["sub-delay"]}, sub-speed=${options["sub-speed"]}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed applying subtitle settings to mpv", e)
            }
        }

        /**
         * Issue #332: whether a resolved subtitle URI is one mpv can actually
         * open. A `content://` URI that survived resolution unchanged means
         * [com.mpx.player.playback.MpvFdOpener] could not open it (permission revoked, file gone) —
         * mpv cannot open `content://` at all, so sub-adding it can only
         * fail. A plain filesystem path must exist. `fd://` (resolution
         * succeeded) and http(s) (mpv tries the network) are usable; their
         * real outcome is confirmed asynchronously via the track-list pin.
         *
         * Pure string/file logic, kept in the companion object so it is
         * unit-testable without mpv (see MpvSubtitleRendererTest).
         */
        internal fun isUsableSubtitleUri(uri: String): Boolean = when {
            uri.startsWith("fd://") -> true
            uri.startsWith("content://") -> false
            uri.startsWith("http://") || uri.startsWith("https://") -> true
            else -> File(uri.removePrefix("file://")).exists()
        }

        /**
         * Issue #197: are two ExternalSubtitle entries the same file? The
         * display name is the stable identity — but only the stable one for
         * content:// subtitles, whose engine-side URI is a fresh `fd://<n>`
         * on every resolve (MpvFdOpener opens a new fd per call), so the URI
         * can never match across calls for them. File-path subtitles keep a
         * stable URI, so those must match too: two different files that
         * happen to share a display name (different folders) are NOT the
         * same sub. Honest limit: two same-named content:// files cannot be
         * told apart here — the engine never sees their original URI — so
         * the second one is skipped. The manager still dedupes the item's
         * list by original URI, so the list itself stays correct.
         *
         * Pure logic, kept in the companion object so it is unit-testable
         * without mpv (see MpvSubtitleRendererTest).
         */
        internal fun isSameExternalSubtitle(a: ExternalSubtitle, b: ExternalSubtitle): Boolean {
            if (a.name != b.name) return false
            return a.uri == b.uri || a.uri.startsWith("fd://") || b.uri.startsWith("fd://")
        }
    }


    /** Issue #372: whether the #332 confirmation is still awaiting the
     * remembered external pick landing (see [rememberedExternalPick]).
     * Set together with the remembered pick whenever sub-adds are issued
     * (replaceMediaItem's loaded branch, or the FILE_LOADED dispatch),
     * cleared the moment a track-list refresh confirms sid is pinned to
     * the remembered track — or by the #332 timeout when the track never
     * appears, so a failed add can never leave a stale armed pin
     * retrying forever. Also cleared on a manual embedded/Off pick
     * (selectSubtitleTrack) and on load/stop/release. Armed on Main
     * (replaceMediaItem) and on the event thread (FILE_LOADED dispatch);
     * read/cleared on the event thread: volatile. */
    @Volatile
    private var pendingExternalPin: Boolean = false


    /** Issue #372: the external subtitle the user picked for the current
     * item, remembered by identity (display name + the URI handed to
     * mpv) so it can be re-applied after mpv unselects it. The #192
     * pin only pinned a track that was currently selected AND external,
     * so once a seek, VO switch or decoder re-init unselected the track
     * the pin could never land (user's 2026-11-08 01:15 log: vo=null
     * surface destroy + decoder re-init + seeks, pin precondition dead).
     * mpv has no sticky pick of its own — sub-add returns no track id
     * and sid resets to auto at playback init — so the app owns the pick
     * (mpvEx's TrackSelector Pass 00 does the same: select ANY external
     * track by explicit id on every file load). [reapplyRememberedPick]
     * sets sid to the remembered track's id on every track-list refresh
     * where the track is present but sid is not already on it, matched
     * by title (the only identity track-list carries — the sub-add title
     * argument sets the track title, per the mpv manual), never by
     * isSelected — so the pin lands the moment the track appears and
     * survives later re-evaluations. Guarded to the current track-list,
     * so a failed sub-add can never resurrect a non-existent track.
     * Cleared when the user picks an embedded track or turns subtitles
     * Off (selectSubtitleTrack), on load() of a different item, on the
     * #332 timeout (track never appeared), and on stop/release. Survives
     * a successful pin: that is the point — a later VO change or reload
     * (fresh track ids) re-applies it by title again. */
    @Volatile
    private var rememberedExternalPick: RememberedExternalPick? = null


    /**
     * Issue #332: outcome confirmation for a sub-add. `mpv.command()`
     * never throws for a rejected subtitle file — the native JNI command
     * only logs `mpv_error_string` (libplayer.so contains no Java
     * exception class at all) — so the issued command proves nothing and
     * the old unconditional success toast lied. The only real signal is
     * the new track appearing in track-list, which the #192 pin already
     * watches: a confirmation is armed whenever a sub-add is issued
     * (replaceMediaItem's loaded branch, or the FILE_LOADED dispatch),
     * resolved the moment the pin lands for the current item, and
     * reported as failure if the track never appears within
     * [SUBTITLE_CONFIRMATION_TIMEOUT_MS]. The listener is notified for
     * every armed confirmation; the manager ignores ids it did not arm
     * (queue restores confirm the same way). Armed on Main
     * (replaceMediaItem) and on the event thread (FILE_LOADED dispatch);
     * read/cleared on the event thread (track-list handler) and Main
     * (timeout): volatile. Cleared on stop/release.
     */
    @Volatile
    private var pendingSubtitleConfirmation: PendingSubtitleConfirmation? = null

    // Issue #470 step 3: absolute paths of UTF-8 temp copies created
    // when mpv rejects the sub-codepage set. Deleted in
    // cleanupForStopOrRelease (and when a subtitle is replaced), so
    // one temp file per legacy subtitle never outlives its item.
    private val transcodedTempFiles = mutableSetOf<String>()


    private data class PendingSubtitleConfirmation(val mediaId: String)


    /** Issue #372: identity of a picked external subtitle (see
     * [rememberedExternalPick]). [uri] is the URI actually handed to mpv
     * (resolved: fd:// for content:// picks), [name] the display name
     * passed as the sub-add title — the title is what track-list
     * carries, so it is the match key in [reapplyRememberedPick]. */
    private data class RememberedExternalPick(val mediaId: String, val name: String, val uri: String)


    private val subtitleConfirmationTimeout = Runnable {
        val pending = pendingSubtitleConfirmation ?: return@Runnable
        pendingSubtitleConfirmation = null
        // Issue #372: the track never appeared within the timeout — the
        // sub-add failed. Clear the armed pin state too: the old code
        // reported the failure but left pendingExternalPin armed, so the
        // pin kept retrying on every track-list refresh forever. The
        // remembered pick goes with it — there is no track to remember.
        pendingExternalPin = false
        rememberedExternalPick = null
        Log.d(TAG, "External subtitle confirmation timed out for ${pending.mediaId}: track never appeared, remembered pick cleared")
        core.listener?.onExternalSubtitleConfirmed(pending.mediaId, false)
    }


    /**
     * Applies the subtitle style options (issue #75) to a running mpv instance.
     * All sub-* options used here are runtime-changeable in mpv, so this is
     * safe to call whenever settings change mid-playback — but only through
     * the property API, since setOptionString is pre-init only. No-op before
     * init or after release.
     */
    fun applySubtitleSettings(settings: AppSettings) {
        applySubtitleSettingsTo(core, settings)
    }


    /**
     * Issue #441: set mpv's subtitle decoding for the file about to be
     * sub-added. A detected codepage ("cp1256" for Windows-1256 Arabic
     * subtitles) is set explicitly; otherwise the codepage is reset to
     * mpv's default ("auto") so a previous file's cp1256 cannot leak
     * into the next file. Must run immediately before the sub-add —
     * mpv decodes the file when it is loaded.
     *
     * Issue #470 step 2: returns whether the set actually took effect,
     * verified by reading the property back — setRuntimeProperty
     * swallows failures, so without the read-back a rejected set is
     * invisible (which is why the v0.4.54 regression shipped looking
     * green). The caller transcodes to a UTF-8 copy when this returns
     * false for a detected codepage.
     */
    private fun applySubtitleCodepage(codepage: String?, name: String): Boolean {
        val target = codepage ?: "auto"
        core.setRuntimeProperty("sub-codepage", target)
        val actual = try {
            core.port.getPropertyString("sub-codepage")
        } catch (e: Exception) {
            Log.w(TAG, "sub-codepage read-back failed for $name", e)
            null
        }
        val ok = actual == target
        if (ok) {
            Log.i(TAG, "sub-codepage set to $target for $name")
        } else {
            Log.w(TAG, "sub-codepage set to $target rejected for $name (reads back as $actual)")
        }
        return ok
    }

    /**
     * Issue #470: resolve the codepage for a subtitle and return the
     * URI to sub-add. A subtitle picked before v0.4.54 was persisted
     * WITHOUT a codepage, so a null stored value is detected fresh
     * from the RAW uri instead of defaulting to "auto" (which garbles
     * Windows-1256 Arabic).
     *
     * Follow-up: for Windows-1256, mpv's sub-codepage is unreliable
     * in this libmpv build — the property set verifies by read-back,
     * yet the subtitle still renders as mojibake (the Antipasto .srt
     * proves it: detected cp1256, set verified, still garbled). The
     * read-back proves the property was SET, not that mpv USES it to
     * decode the subtitle. So a detected cp1256 subtitle is ALWAYS
     * transcoded to a UTF-8 copy and the copy is sub-added instead
     * (mpv always handles UTF-8, and the copy needs no codepage, so
     * it is reset to "auto" first). The resolved fd is closed when
     * transcoding, since mpv never received it. For any other
     * codepage the old path remains: apply and verify, transcode only
     * if the set is rejected. Always returns a URI to sub-add (the
     * resolved one on any failure) — never throws, never breaks a load.
     */
    private fun uriToSubAdd(
        rawUri: String?,
        resolvedUri: String,
        name: String,
        storedCodepage: String?,
    ): String {
        val codepage = storedCodepage
            ?: rawUri?.let { SubtitleUtils.codepageFor(core.context, it, null) }
        // Issue #470 follow-up: always transcode Windows-1256. The
        // sub-codepage set verifies by read-back but mpv still renders
        // the subtitle as mojibake, so the set alone cannot be trusted
        // for this encoding. mpv always handles UTF-8, so the copy is
        // the guaranteed path. Only if transcoding fails do we fall
        // back to the sub-codepage attempt below.
        if (codepage == "cp1256") {
            val tempPath = rawUri?.let { transcodeToUtf8IfNeeded(it, name, codepage) }
            if (tempPath != null) {
                applySubtitleCodepage(null, name)
                core.fdOpener.closeFds(listOf(resolvedUri))
                return "file://$tempPath"
            }
        }
        val setOk = applySubtitleCodepage(codepage, name)
        if (codepage != null && !setOk) {
            val tempPath = rawUri?.let { transcodeToUtf8IfNeeded(it, name, codepage) }
            if (tempPath != null) {
                applySubtitleCodepage(null, name)
                core.fdOpener.closeFds(listOf(resolvedUri))
                return "file://$tempPath"
            }
            // Transcode failed — fall through and sub-add the
            // original; the (rejected) codepage attempt was already
            // made, so this is no worse than before.
        }
        return resolvedUri
    }

    /**
     * Issue #470 step 3: transcode a detected Windows-1256 subtitle to
     * a UTF-8 copy in the app cache and return the copy's path, for
     * when mpv rejects the sub-codepage set. Returns null when there is
     * nothing to transcode (no detected codepage, or the file is
     * already UTF-8) or on any failure — the caller then sub-adds the
     * original. The copy is tracked in [transcodedTempFiles] and deleted
     * on stop/release.
     */
    private fun transcodeToUtf8IfNeeded(rawUri: String?, name: String, codepage: String?): String? {
        if (codepage == null || rawUri == null) return null
        val path = SubtitleUtils.transcodeSubtitleToUtf8(core.context, rawUri, name) ?: return null
        transcodedTempFiles.add(path)
        Log.i(TAG, "Transcoded $name to UTF-8 copy at $path (sub-codepage set was rejected)")
        return path
    }

    /**
     * Issue #470 step 3: delete every UTF-8 temp copy created by
     * [transcodeToUtf8IfNeeded]. Best-effort — a failed delete is
     * logged, never thrown.
     */
    private fun deleteTranscodedTempFiles() {
        if (transcodedTempFiles.isEmpty()) return
        for (path in transcodedTempFiles) {
            try {
                if (!File(path).delete()) Log.w(TAG, "Could not delete transcoded subtitle temp file $path")
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete transcoded subtitle temp file $path", e)
            }
        }
        transcodedTempFiles.clear()
    }


    /**
     * Issue #332: arm the sub-add outcome confirmation for [mediaId]
     * (replacing any earlier one — the manager only ever waits for the
     * latest pick per item). The timeout reports failure if the track
     * never appears; [confirmPendingSubtitle] resolves it early when the
     * pin lands.
     */
    private fun armSubtitleConfirmation(mediaId: String) {
        core.mainHandler.removeCallbacks(subtitleConfirmationTimeout)
        pendingSubtitleConfirmation = PendingSubtitleConfirmation(mediaId)
        core.mainHandler.postDelayed(subtitleConfirmationTimeout, SUBTITLE_CONFIRMATION_TIMEOUT_MS)
    }


    /**
     * Issue #332: resolve an armed confirmation. Only confirms when the
     * confirmation belongs to the currently loaded item — a pin landing
     * after a queue advance belongs to the new item, not the old pick.
     * A failure after the timeout has already fired is a no-op (the
     * field is null by then).
     */
    private fun confirmPendingSubtitle(success: Boolean) {
        val pending = pendingSubtitleConfirmation ?: return
        if (pending.mediaId != core.activeMediaId) return
        pendingSubtitleConfirmation = null
        core.mainHandler.removeCallbacks(subtitleConfirmationTimeout)
        core.mainHandler.post {
            core.listener?.onExternalSubtitleConfirmed(pending.mediaId, success)
        }
    }


    /**
     * A load started: clear the subtitle pin state for the old item
     * (called from [MpvCoreEngine.Host.onLoadStarted], at exactly the
     * point the old monolith ran this).
     */
    fun onLoadStarted() {
        pendingExternalPin = false
    }


    /**
     * The load target resolved: drop a remembered pick for another item
     * (called from [MpvCoreEngine.Host.onLoadTargetResolved]).
     */
    fun onLoadTargetResolved(mediaId: String) {
        if (rememberedExternalPick?.mediaId != mediaId) {
            rememberedExternalPick = null
        }
    }


    /**
     * The user picked Off or an embedded subtitle track: the remembered
     * external pick is superseded, and the armed pin must be cleared
     * too, or the track-list handler could re-select the external track
     * over the explicit pick (issue #372). Called from the
     * [MpvTrackSelection.Host] seam (via the facade).
     */
    fun clearRememberedExternalPick() {
        rememberedExternalPick = null
        pendingExternalPin = false
    }


    /**
     * The mpv track-list property changed: re-apply the remembered pick
     * (called from [MpvCoreEngine.Host.onTrackListChanged]).
     *
     * Issue #372: re-apply the remembered external pick on
     * every track-list refresh — sub-add is async, so the pin
     * right after sub-add usually runs before the new track
     * is in track-list, and a later seek, VO switch or
     * decoder re-init can unselect the track again (user's
     * 2026-11-08 01:15 log). The re-apply matches by title,
     * not by isSelected, so it lands the moment the track
     * appears and re-lands it after any re-evaluation.
     * Issue #332: a pin landing is the only proof the sub-add
     * actually worked, so it resolves an armed confirmation.
     * Only a pin that WAS pending and just landed counts.
     */
    fun onTrackListChanged() {
        val hadPendingPin = pendingExternalPin
        val result = reapplyRememberedPick()
        if (hadPendingPin && result.confirms) {
            pendingExternalPin = false
            confirmPendingSubtitle(true)
        }
        core.mainHandler.post {
            core.listener?.onTracksChanged()
        }
    }


    /**
     * FILE_LOADED arrived: dispatch queued subtitles or auto-select
     * (called from [MpvCoreEngine.Host.onFileLoaded]).
     */
    fun onFileLoaded() {
        // Issue #102: Dispatch queued external subtitles for the current item now that file context is active
        val pending = core.pendingSubtitles
        core.pendingSubtitles = null
        if (pending != null) {
            // Issue #213/#219: the match also accepts the raw URI
            // of the current queue item. Subtitles queued while the
            // load was still deferred for lack of surface carry the
            // raw URI, and the fresh fd opened when the load flushed
            // can never equal it — without this the subtitles queued
            // in replaceMediaItem would be dropped as stale.
            val matches = core.activeMediaUri == null ||
                MpvCoreEngine.isPathMatch(pending.mediaUri, core.activeMediaUri) ||
                pending.mediaUri == core.getCurrentQueue().getOrNull(core.getCurrentMediaIndex())?.uri
            if (matches) {
                var issuedAny = false
                var lastIssuedExternal: PendingSubtitle? = null
                val subs = pending.subtitles
                for ((i, sub) in subs.withIndex()) {
                    // Issue #332: skip a URI mpv provably cannot
                    // open — a content:// URI whose fd resolution
                    // failed, or a file deleted since it was
                    // queued — instead of sub-adding it and
                    // letting mpv fail silently. mpv.command()
                    // never throws for a rejected file, so without
                    // this the caller could never know it failed.
                    if (!isUsableSubtitleUri(sub.uri)) {
                        Log.w(TAG, "Skipping unusable pending subtitle ${sub.uri}")
                        continue
                    }
                    try {
                        // Issue #470: the PendingSubtitle already
                        // carries a load-time-detected codepage (set
                        // at queue time); resolve it against the raw
                        // uri and sub-add the URI it returns (a UTF-8
                        // temp copy when mpv rejects the codepage
                        // set, else the resolved URI).
                        val subAddUri = uriToSubAdd(sub.rawUri, sub.uri, sub.title ?: sub.uri, sub.codepage)
                        if (sub.title != null) {
                            core.port.command("sub-add", subAddUri, sub.flag, sub.title)
                        } else {
                            core.port.command("sub-add", subAddUri, sub.flag)
                        }
                        issuedAny = true
                        // Issue #372: remember the last external
                        // subtitle issued — each "select" sub-add
                        // selects that track, so the last one is
                        // the pick. The legacy subtitleUri (title
                        // null) has no identity to remember. The URI
                        // stored is the one actually handed to mpv
                        // (the temp copy when transcoded).
                        if (sub.title != null) lastIssuedExternal = sub.copy(uri = subAddUri)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed adding pending subtitle ${sub.uri}", e)
                        // Issue #332: the remaining queued entries
                        // were never handed to mpv, so their
                        // descriptors are still ours — close them.
                        // The failed entry's descriptor is NOT
                        // closed: mpv.command is synchronous, so
                        // by the time it throws libmpv has already
                        // run the open attempt and owns (and
                        // closes) it; closing here would
                        // double-close.
                        core.fdOpener.closeFds(subs.drop(i + 1).map { it.uri })
                        break
                    }
                }
                // Issue #372: remember the picked external
                // subtitle and pin sid to it — sub-add "select"
                // alone does not stick. Arm the remembered pick
                // first: sub-add is async, so the immediate
                // attempt usually runs before the new track is in
                // track-list, and the track-list handler
                // re-applies it until it lands — and again after
                // any later re-evaluation unselects it. A failed
                // add (issuedAny false) never arms anything.
                if (issuedAny) {
                    val lastExternal = lastIssuedExternal
                    if (lastExternal != null && lastExternal.title != null) {
                        rememberedExternalPick = RememberedExternalPick(
                            pending.mediaId, lastExternal.title, lastExternal.uri,
                        )
                    }
                    pendingExternalPin = true
                    // Issue #332: arm the outcome confirmation
                    // BEFORE the immediate pin attempt — the
                    // track may already be in track-list, and a
                    // synchronous pin landing must resolve it.
                    armSubtitleConfirmation(pending.mediaId)
                    if (reapplyRememberedPick().confirms) {
                        pendingExternalPin = false
                        confirmPendingSubtitle(true)
                    }
                }
            } else {
                Log.w(TAG, "Dropping stale pending subtitles for ${pending.mediaUri} (active: ${core.activeMediaUri})")
                // Issue #332: the dropped subtitles were never
                // handed to mpv, so their descriptors are still
                // ours — close them instead of leaking one fd per
                // dropped subtitle until the process dies.
                core.fdOpener.closeFds(pending.subtitles.map { it.uri })
            }
        } else {
            // Issue #189: auto-select an embedded subtitle track
            // when none is selected. MPx never sets mpv's 'slang'
            // and never sets 'sid' at load, so mpv's default
            // sid=auto had no language to match against and
            // embedded subtitles never appeared unless the user
            // picked one by hand every time. Prefer the
            // default-flagged track, else the first embedded track.
            // Skipped when external subtitles were just added with
            // 'select' above (they become the selected track), and
            // when mpv already selected a track itself.
            try {
                val tracks = trackSelection.getSubtitleTracks()
                if (tracks.none { it.isSelected }) {
                    val candidate = tracks.firstOrNull { it.isDefault && !it.isExternal }
                        ?: tracks.firstOrNull { !it.isExternal }
                    if (candidate != null) {
                        trackSelection.selectSubtitleTrack(candidate.groupIndex, candidate.trackIndex)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed auto-selecting a subtitle track", e)
            }
        }
    }


    /**
     * Issue #372: outcome of one [reapplyRememberedPick] attempt.
     * [confirms] is true when the desired end state holds — the picked
     * external subtitle is present in track-list and sid selects it —
     * so an armed #332 confirmation may resolve as success.
     * APPLIED — sid was set to the remembered track (title match) this
     * refresh. ALREADY_PINNED — the remembered track is present,
     * selected, and sid is already its id. COUNT_CONFIRMED — no title
     * match, but every external subtitle the current item carries has
     * produced a track, so the remembered one is necessarily present
     * (see [allExpectedExternalTracksPresent]) and sid was set to the
     * selected external track. FALLBACK_APPLIED — sid was set to the
     * selected external track with no title match, but the count is
     * short, so the pick is not confirmed yet. NOT_PRESENT — the
     * remembered track is not in the current track-list and no external
     * track is selected. BLOCKED — a title match exists but a different
     * external track is currently selected, so the remembered pick must
     * not override that manual pick. NOTHING_PENDING — no remembered
     * pick and no armed pin (steady state).
     */
    private enum class ReapplyResult(val confirms: Boolean) {
        APPLIED(true),
        ALREADY_PINNED(true),
        COUNT_CONFIRMED(true),
        FALLBACK_APPLIED(false),
        NOT_PRESENT(false),
        BLOCKED(false),
        NOTHING_PENDING(false),
    }


    /**
     * Issue #372: re-apply the remembered external pick — the replacement
     * for the #192 pin. `sub-add` with the "select" flag only changes the
     * current selection; it leaves `sid` at its default "auto", so mpv's
     * next track-selection re-evaluation (seek, decoder reinit, VO
     * switch) falls back to the default-flagged embedded track and the
     * external subtitle the user just picked silently disappears (user's
     * v0.3.1 log: sid=2 selected, sid=1 back two seconds later; user's
     * 2026-11-08 01:15 log: vo=null surface destroy + decoder re-init +
     * seeks). Setting `sid` to the track id explicitly makes the pick
     * sticky — but the old pin only pinned a track that was currently
     * selected AND external, so once a re-evaluation unselected the track
     * the pin could never land. This matches by identity instead: the
     * remembered track is found in track-list by title (the only identity
     * track-list carries) and `sid` is set to its id whenever `sid` is
     * not already on it — so it lands the moment the track appears after
     * sub-add AND re-lands after any later re-evaluation unselects it.
     * Safe to call on every track-list refresh: it only ever sets `sid`
     * to the remembered track's id, only when that track is present in
     * the current track-list (a failed sub-add can never resurrect a
     * non-existent track), and never while a different external track is
     * selected (BLOCKED — a manual pick of another external track wins).
     * A manual embedded/Off pick clears the remembered pick first
     * (selectSubtitleTrack), so this can never override one either.
     * Callers clear [pendingExternalPin] and resolve the #332
     * confirmation only when the result [ReapplyResult.confirms].
     *
     * Title-mismatch fallback: the v0.3.1 log showed mpv reporting the
     * added track's title as "srt" for fd:// subs, so a title match can
     * never be assumed — when no title matches, this falls back to the
     * old #192 rule and pins the currently selected external track
     * (that track is either mpv's own "select" choice or the user's
     * manual external pick, so it cannot override anything). The
     * confirmation then resolves only via [allExpectedExternalTracksPresent].
     */
    private fun reapplyRememberedPick(): ReapplyResult {
        val remembered = rememberedExternalPick
        if (remembered == null && !pendingExternalPin) return ReapplyResult.NOTHING_PENDING
        val tracks = try {
            trackSelection.getSubtitleTracks()
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading subtitle tracks for remembered pick", e)
            return ReapplyResult.NOT_PRESENT
        }
        // Primary: match the remembered pick by title. mpv appends added
        // tracks, so the just-added one is last; prefer the selected
        // match when there is one (sub-add "select" selects it) — two
        // same-named files from different folders share a title, and the
        // selected/last one is the pick.
        if (remembered != null) {
            val candidates = tracks.filter { it.isExternal && it.label == remembered.name }
            val track = candidates.lastOrNull { it.isSelected } ?: candidates.lastOrNull()
            if (track != null) {
                if (tracks.any { it.isExternal && it.isSelected && it.trackIndex != track.trackIndex }) {
                    Log.d(TAG, "Remembered external subtitle '${remembered.name}' not re-applied: a different external track is selected")
                    return ReapplyResult.BLOCKED
                }
                val currentSid = try { core.port.getPropertyString("sid") } catch (_: Exception) { null }
                if (track.isSelected && currentSid == track.trackIndex.toString()) {
                    return ReapplyResult.ALREADY_PINNED
                }
                return try {
                    core.port.setPropertyString("sid", track.trackIndex.toString())
                    Log.d(TAG, "Remembered external subtitle '${remembered.name}': sid=${track.trackIndex} (was sid=$currentSid, selected=${track.isSelected})")
                    ReapplyResult.APPLIED
                } catch (e: Exception) {
                    Log.w(TAG, "Failed re-applying remembered external subtitle '${remembered.name}'", e)
                    ReapplyResult.NOT_PRESENT
                }
            }
            Log.d(TAG, "Remembered external subtitle '${remembered.name}' not in track-list by title yet (external tracks: ${tracks.count { it.isExternal }})")
        }
        // Fallback: no title match (or a legacy-only batch, which has no
        // identity to match — a restored queue's single subtitleUri was
        // sub-added without a title). Pin the currently selected external
        // track, if any — the old #192 rule, kept for this path only.
        val selected = tracks.firstOrNull { it.isExternal && it.isSelected }
            ?: return ReapplyResult.NOT_PRESENT
        val currentSid = try { core.port.getPropertyString("sid") } catch (_: Exception) { null }
        if (currentSid != selected.trackIndex.toString()) {
            try {
                core.port.setPropertyString("sid", selected.trackIndex.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed pinning external subtitle selection", e)
                return ReapplyResult.NOT_PRESENT
            }
            Log.d(TAG, "External subtitle pin (fallback, no title match for '${remembered?.name}'): sid=${selected.trackIndex} (was sid=$currentSid, selected=${selected.isSelected})")
        }
        val mediaId = remembered?.mediaId ?: core.activeMediaId
        return if (allExpectedExternalTracksPresent(tracks, mediaId)) {
            Log.d(TAG, "External subtitle confirmed by count for $mediaId: ${tracks.count { it.isExternal }} external tracks present")
            ReapplyResult.COUNT_CONFIRMED
        } else {
            ReapplyResult.FALLBACK_APPLIED
        }
    }


    /** Issue #372: have all external subtitles the item [mediaId] carries
     * produced a track in [tracks]? One track per sub-add, no dedupe — so
     * an external-track count at least the item's external-subtitle count
     * (plus one for a legacy subtitleUri) means the remembered pick is
     * necessarily present, even when its title didn't match (mpv
     * reporting "srt" for fd:// subs, v0.3.1 log). Same count the
     * manager's ALREADY_ADDED branch uses. */
    private fun allExpectedExternalTracksPresent(tracks: List<SubtitleTrackInfo>, mediaId: String?): Boolean {
        if (mediaId == null) return false
        val item = core.getCurrentQueue().firstOrNull { it.id == mediaId } ?: return false
        val expected = item.externalSubtitles.size + if (item.subtitleUri != null) 1 else 0
        if (expected == 0) return false
        return tracks.count { it.isExternal } >= expected
    }


    /**
     * Dynamically replaces the media item at [index] (see
     * [PlayerEngine.replaceMediaItem]): its body is sub-add + #332
     * confirmation, so it lives here. The queue bookkeeping it needs
     * (current queue, pending load, loaded flag) lives in the core and
     * is reached through it; the stored queue keeps the RAW item
     * (original URIs) so a later load() re-resolves it fresh.
     */
    fun replaceMediaItem(index: Int, media: PlayableMedia): ReplaceMediaItemResult {
        val currentQueue = core.getCurrentQueue()
        if (index !in currentQueue.indices) {
            return ReplaceMediaItemResult.FAILED
        }
        // Issue #197: remember the item mpv already has, so only
        // genuinely NEW external subtitles are sub-added below.
        val previous = currentQueue.getOrNull(index)
        val updated = currentQueue.toMutableList()
        updated[index] = media

        val pending = core.pendingLoad
        if (pending != null && index == pending.second) {
            core.pendingLoad = Triple(updated, pending.second, pending.third)
        }

        if (index != core.getCurrentMediaIndex()) {
            // Not the live item: the subtitles are stored in the queue
            // and will be attempted when the item loads (issue #332:
            // same outcome as QUEUED — the FILE_LOADED dispatch arms
            // the confirmation then).
            return ReplaceMediaItemResult.QUEUED
        }

        // Issue #213/#219: the stored queue keeps the RAW item
        // (original URIs) so a later load() re-resolves it fresh.
        // Only subtitle URIs are resolved here — replaceMediaItem
        // never reloads the media (sub-add only), so resolving the
        // media URI would open an fd nobody uses. The dedupe below
        // still compares RAW lists on both sides, so name+URI
        // identity keeps working (and, unlike before, two different
        // same-named content:// files can now be told apart by URI).
        if (core.fileLoadedForCurrentItem) {
            // Issue #197: sub-add ONLY the external subtitles
            // that are new since the previous item. The manager
            // hands replaceMediaItem the item's FULL external
            // list (existing + newly added), and mpv creates a
            // brand-new track for every sub-add call with no
            // dedupe — so re-adding the whole list duplicated
            // the first .srt the moment a second one was added.
            // The not-loaded branch below intentionally keeps
            // the full list: pendingSubtitles is overwritten,
            // not appended, and dispatched exactly once on
            // FILE_LOADED, so it cannot duplicate. Only the new
            // subtitles' URIs are resolved — the legacy single
            // subtitleUri is never sub-added on this branch, so
            // opening an fd for it would leak it.
            val newExts = media.externalSubtitles.filter { rawExt ->
                previous?.externalSubtitles?.none { isSameExternalSubtitle(it, rawExt) } ?: true
            }
            val resolved = core.fdOpener.resolveExternalSubtitles(newExts)
            var issuedAny = false
            var lastIssued: Pair<ExternalSubtitle, ExternalSubtitle>? = null
            for ((i, pair) in newExts.zip(resolved).withIndex()) {
                val (rawExt, resolvedExt) = pair
                // Issue #332: skip a URI mpv provably cannot open — a
                // content:// URI whose fd resolution failed, or a
                // missing file — instead of sub-adding it and letting
                // mpv fail silently. mpv.command() never throws for a
                // rejected file, so without this the caller could never
                // know the add failed.
                if (!isUsableSubtitleUri(resolvedExt.uri)) {
                    Log.w(TAG, "Skipping unusable external subtitle ${rawExt.name} (${resolvedExt.uri})")
                    continue
                }
                try {
                    // Issue #470: resolve the codepage at load time
                    // (a subtitle picked before v0.4.54 has none
                    // stored) and sub-add the URI it returns — a
                    // UTF-8 temp copy when mpv rejects the codepage
                    // set, else the resolved URI.
                    val subAddUri = uriToSubAdd(rawExt.uri, resolvedExt.uri, rawExt.name, rawExt.codepage)
                    core.port.command("sub-add", subAddUri, "select", rawExt.name)
                    issuedAny = true
                    // Issue #372: each "select" sub-add selects that
                    // track, so the last issued one is the pick. The
                    // URI stored is the one actually handed to mpv
                    // (the temp copy when transcoded).
                    lastIssued = rawExt to resolvedExt.copy(uri = subAddUri)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed adding external subtitle ${rawExt.name}", e)
                    // Issue #332: the remaining resolved entries were
                    // never handed to mpv, so their descriptors are
                    // still ours — close them. The failed entry's
                    // descriptor is NOT closed: mpv.command is
                    // synchronous, so by the time it throws libmpv has
                    // already run the open attempt and owns (and
                    // closes) it; closing here would double-close.
                    core.fdOpener.closeFds(resolved.drop(i + 1).map { it.uri })
                    break
                }
            }
            // Issue #372: remember the picked external subtitle and pin
            // sid to it — sub-add "select" alone does not stick. Arm the
            // remembered pick first: sub-add is async, so the immediate
            // attempt usually runs before the new track is in track-list,
            // and the track-list handler re-applies it until it lands —
            // and again after any later re-evaluation unselects it. A
            // failed add (issuedAny false) never arms anything.
            if (issuedAny) {
                lastIssued?.let { (rawExt, resolvedExt) ->
                    rememberedExternalPick = RememberedExternalPick(media.id, rawExt.name, resolvedExt.uri)
                }
                pendingExternalPin = true
                // Issue #332: arm the outcome confirmation BEFORE
                // the immediate pin attempt — the track may already
                // be in track-list, and a synchronous pin landing
                // must resolve it.
                armSubtitleConfirmation(media.id)
                if (reapplyRememberedPick().confirms) {
                    pendingExternalPin = false
                    confirmPendingSubtitle(true)
                }
                return ReplaceMediaItemResult.ISSUED
            }
            // Issue #332: nothing was issued — either every new
            // subtitle was unusable, or there was nothing new at all
            // (the subtitles are already in the item).
            return if (newExts.isEmpty()) {
                ReplaceMediaItemResult.ALREADY_ADDED
            } else {
                ReplaceMediaItemResult.FAILED
            }
        } else {
            // Queue for dispatch when FILE_LOADED arrives. The
            // subtitle URIs are the resolved ones (mpv cannot open
            // content://); mediaUri is the currently-loading item's
            // resolved URI so the FILE_LOADED match succeeds, falling
            // back to the raw URI — the FILE_LOADED match also
            // accepts the raw URI of the current item, which covers
            // a load still deferred for lack of surface.
            val resolved = core.fdOpener.resolveSubtitlesOnly(media)
            val subs = mutableListOf<PendingSubtitle>()
            resolved.subtitleUri?.let { subUri ->
                subs.add(PendingSubtitle(uri = subUri, flag = "select"))
            }
            // Issue #470: resolveExternalSubtitles maps in order, so
            // zipping the raw list with the resolved list pairs each
            // raw uri with its resolved uri. Detect the codepage here
            // at queue time (a subtitle picked before v0.4.54 has
            // none stored) and keep the raw uri — the resolved fd uri
            // cannot be opened for detection/transcoding at
            // FILE_LOADED dispatch time.
            media.externalSubtitles.zip(resolved.externalSubtitles).forEach { (rawExt, ext) ->
                subs.add(
                    PendingSubtitle(
                        uri = ext.uri,
                        flag = "select",
                        title = ext.name,
                        codepage = SubtitleUtils.codepageFor(core.context, rawExt.uri, rawExt.codepage),
                        rawUri = rawExt.uri,
                    )
                )
            }
            // Issue #332: close the previous pending subtitles'
            // descriptors before overwriting — they were never
            // dispatched, so they are still ours.
            core.pendingSubtitles?.let { core.fdOpener.closeFds(it.subtitles.map { sub -> sub.uri }) }
            core.pendingSubtitles = if (subs.isNotEmpty()) {
                PendingSubtitles(
                    mediaUri = core.activeMediaUri ?: media.uri,
                    mediaId = media.id,
                    subtitles = subs,
                )
            } else {
                null
            }
            return if (subs.isNotEmpty()) {
                ReplaceMediaItemResult.QUEUED
            } else {
                ReplaceMediaItemResult.FAILED
            }
        }
    }


    /**
     * Subtitle cleanup for stop()/release() — called by the facade
     * BEFORE core.stop()/core.release(), in the same order as the old
     * monolith. Issue #332: the pending subtitles were never
     * dispatched, so their descriptors are still ours — close them
     * instead of leaking one fd per abandoned subtitle. The
     * confirmation is dropped too: the stop itself is the outcome, and
     * no banner should arrive for an item that no longer plays. (On
     * release the timeout runnable is removed with every other
     * callback by core.release() as well; removing it here too is
     * harmless.)
     */
    fun cleanupForStopOrRelease() {
        core.pendingSubtitles?.let { core.fdOpener.closeFds(it.subtitles.map { sub -> sub.uri }) }
        core.pendingSubtitles = null
        pendingExternalPin = false
        rememberedExternalPick = null
        pendingSubtitleConfirmation = null
        core.mainHandler.removeCallbacks(subtitleConfirmationTimeout)
        // Issue #470 step 3: the item stopped, so no temp copy is
        // in use any more — delete every UTF-8 copy created for it.
        deleteTranscodedTempFiles()
    }
}

/**
 * Phase 4 step 3: queued external subtitles for the item being loaded,
 * dispatched on FILE_LOADED by [MpvSubtitleRenderer]. Built by the core
 * in `load()` and stored in [MpvCoreEngine.pendingSubtitles]; moved
 * here from [MpvCoreEngine.kt] with the dispatch in step 3 (same
 * package, so the core keeps constructing them).
 */
internal data class PendingSubtitle(
    val uri: String,
    val flag: String,
    val title: String? = null,
    // Issue #441: the detected mpv sub-codepage for this file
    // ("cp1256" for Windows-1256 Arabic subtitles); null means
    // mpv's default. Copied from the ExternalSubtitle it came from.
    val codepage: String? = null,
    // Issue #470: the RAW uri before fd resolution. The queued
    // subtitle's `uri` is the resolved fd uri, which ContentResolver
    // cannot open for detection/transcoding — the raw uri is what
    // detect + transcode read from at FILE_LOADED dispatch time.
    // Null for the legacy single subtitleUri (no raw uri kept).
    val rawUri: String? = null,
)

internal data class PendingSubtitles(
    val mediaUri: String,
    val mediaId: String,
    val subtitles: List<PendingSubtitle>,
)
