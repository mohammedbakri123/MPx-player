package com.mpx.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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
) {
    fun toMediaItem(): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .build()
            )

        val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

        subtitleUri?.let { subUri ->
            subConfigs.add(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUri))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("en")
                    .setLabel("Subtitles (VTT)")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        }

        externalSubtitles.forEach { ext ->
            val label = if (ext.name.isNotBlank()) ext.name else "External Subtitle"
            subConfigs.add(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(ext.uri))
                    .setMimeType(ext.mimeType)
                    .setLanguage("und")
                    .setLabel(label)
                    .build()
            )
        }

        if (subConfigs.isNotEmpty()) {
            builder.setSubtitleConfigurations(subConfigs)
        }

        return builder.build()
    }
}

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    /** ExoPlayer sample MIME type (e.g. audio/ac4), null on mpv where only the codec string is known. */
    val mimeType: String? = null,
    /** False when the current engine reports this track as undecodable (Media3 track support). */
    val isSupported: Boolean = true,
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val isExternal: Boolean = false,
)

/** Lightweight playback metrics for the MPV engine only (comparison use).
 * All values are measured, never estimated: null means "not observed yet".
 * Media3 returns null (no metrics) — the comparison is MPV-vs-MX-app, and
 * these numbers describe MPx's mpv path only. */
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

data class PlaybackState(
    val currentItem: PlayableMedia? = null,
    val queue: List<PlayableMedia> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playbackState: Int = 1, // Player.STATE_IDLE
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
    /** True when the current userNotification is a codec failure on Media3 and the banner
     * should offer a one-tap switch to the MPV engine. Cleared with the notification. */
    val showMpvSwitchOffer: Boolean = false,
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
    /** Latest measured engine metrics (MPV only; null for Media3 / nothing yet). */
    val playbackMetrics: EnginePlaybackMetrics? = null,
)
