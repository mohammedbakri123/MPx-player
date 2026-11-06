package com.mpx.player.playback

import com.mpx.player.R

/**
 * Decoder modes. [label] is the stable persisted identifier (stored in
 * settings and matched on load) and is intentionally NOT localised; the
 * user-facing [titleRes]/[descriptionRes] are string resources.
 */
enum class DecoderMode(val label: String, val titleRes: Int, val descriptionRes: Int) {
    HW(
        label = "HW",
        titleRes = R.string.decoder_mode_hw_title,
        descriptionRes = R.string.decoder_mode_hw_desc,
    ),
    HW_PLUS(
        label = "HW+",
        titleRes = R.string.decoder_mode_hw_plus_title,
        descriptionRes = R.string.decoder_mode_hw_plus_desc,
    ),
    SW(
        label = "SW",
        titleRes = R.string.decoder_mode_sw_title,
        descriptionRes = R.string.decoder_mode_sw_desc,
    ),
}

/** Repeat mode for queue playback (issue #76). OFF = stop at the end of the
 * queue, ONE = replay the current item, ALL = wrap to the first item. */
enum class RepeatMode {
    OFF,
    ONE,
    ALL,
}

/**
 * Aspect ratio modes. [label] is the stable identifier; the displayed text
 * comes from [labelRes] so FIT/FILL/ZOOM localise (16:9, 4:3 and 100% are
 * symbols and stay identical in every locale).
 */
enum class AspectRatioMode(val label: String, val labelRes: Int) {
    FIT("FIT", R.string.aspect_fit),
    FILL("FILL", R.string.aspect_fill),
    ZOOM("ZOOM", R.string.aspect_zoom),
    SIXTEEN_NINE("16:9", R.string.aspect_16_9),
    FOUR_THREE("4:3", R.string.aspect_4_3),
    ORIGINAL("100%", R.string.aspect_original),
}

data class ExternalSubtitle(
    val uri: String,
    val name: String,
    val mimeType: String,
)

data class PlayableMedia(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val uri: String,
    val isVideo: Boolean = true,
    val durationMs: Long = 0L,
    val subtitleUri: String? = null,
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
)

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    /** Codec string from mpv's track-list (e.g. "aac", "ac3"), null when unknown. */
    val mimeType: String? = null,
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val isExternal: Boolean = false,
    val isDefault: Boolean = false,
)

/** Lightweight playback metrics for the MPV engine.
 * All values are measured, never estimated: null means "not observed yet". */
data class EnginePlaybackMetrics(
    /** Load call → FILE_LOADED (READY), last video item, ms. */
    val lastTimeToReadyMs: Long? = null,
    /** Load call → first VIDEO_RECONFIG (first video frame), last video, ms. */
    val lastTimeToFirstFrameMs: Long? = null,
    /** Last direct seek command → position reached, ms. */
    val lastSeekLatencyMs: Long? = null,
    /** HW→HW+ and HW+→SW decoder step-downs, session total. */
    val decoderFallbackCount: Int = 0,
    /** Hard playback errors reported (startup failures, load failures,
     * exhausted decoder chain), session total. */
    val hardErrorCount: Int = 0,
)

/** Step-level mpv load diagnostics (issue #96 follow-up): every field is a
 * directly observed fact, never inferred. Null means "no load observed yet". */
data class MpvLoadStage(
    /** libmpv created + initialized without a captured init failure. */
    val initialized: Boolean = false,
    /** A real Surface is currently attached to the engine. */
    val surfaceAttached: Boolean = false,
    /** A loadfile command was actually issued for the current item (false
     * while the load is still deferred waiting for a surface). */
    val loadIssued: Boolean = false,
    /** Last mpv event observed for the current item: "none", "START_FILE",
     * "FILE_LOADED", "VIDEO_RECONFIG", "END_FILE", "SHUTDOWN",
     * "loadfile-failed", or "init-failed". */
    val lastEvent: String = "none",
)

data class PlaybackState(
    val currentItem: PlayableMedia? = null,
    val queue: List<PlayableMedia> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playbackState: Int = 1, // PlayerEngine.STATE_IDLE
    /** MX-style preparing state: true from a video load until the engine reports
     * READY (FILE_LOADED) or an error. While true the player shows a loading
     * splash instead of dead 00:00 chrome — MX never presents an unloaded file
     * as if it were playing (screen.xml loadingSplash/loading). */
    val isPreparing: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val speed: Float = 1.0f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val decoderMode: DecoderMode = DecoderMode.HW,
    val activeDecoderName: String? = null,
    val decoderFallbackNote: String? = null,
    val userNotification: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val selectedAudioTrackIndex: Int = -1,
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedSubtitleTrackIndex: Int = -1, // -1 means subtitles off
    val isLocked: Boolean = false,
    val resumedFromMs: Long? = null,
    val isBackgroundPlaying: Boolean = false,
    val isVideoBackgroundActive: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val sleepTimerRemainingSeconds: Long? = null,
    /** Latest measured engine metrics (null when nothing observed yet). */
    val playbackMetrics: EnginePlaybackMetrics? = null,
    /** Latest observed mpv load stage (null before any load). */
    val mpvLoadStage: MpvLoadStage? = null,
    /**
     * Issue #260: display size of the current video (mpv dwidth/dheight,
     * observed — not polled). 0 until the video's dimensions are known;
     * the PiP params effect keys on these, so the armed auto-enter
     * aspect refreshes the moment they arrive instead of staying 16:9.
     */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)
