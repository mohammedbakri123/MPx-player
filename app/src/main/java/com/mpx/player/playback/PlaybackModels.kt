package com.mpx.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

enum class DecoderMode(val label: String, val title: String, val description: String) {
    HW(
        label = "HW",
        title = "Hardware (HW)",
        description = "Standard device hardware acceleration via Android MediaCodec chipset decoders.",
    ),
    HW_PLUS(
        label = "HW+",
        title = "Hardware Plus (HW+)",
        description = "Enhanced hardware decoding pipeline with asynchronous queuing and low-latency joining.",
    ),
    SW(
        label = "SW",
        title = "Software (SW)",
        description = "Direct CPU software decoding (Google/Android software codecs) for maximum compatibility.",
    ),
}

enum class AspectRatioMode(val label: String) {
    FIT("FIT"),
    FILL("FILL"),
    ZOOM("ZOOM"),
    SIXTEEN_NINE("16:9"),
    FOUR_THREE("4:3"),
    ORIGINAL("100%"),
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
                    .setLabel("Sample Subtitles (VTT)")
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
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val isExternal: Boolean = false,
)

data class PlaybackState(
    val currentItem: PlayableMedia? = null,
    val queue: List<PlayableMedia> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playbackState: Int = 1, // Player.STATE_IDLE
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val speed: Float = 1.0f,
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
)
