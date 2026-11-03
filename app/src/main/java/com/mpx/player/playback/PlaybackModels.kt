package com.mpx.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

enum class DecoderMode(val label: String) {
    HW("HW"),
    HW_PLUS("HW+"),
    SW("SW"),
}

enum class AspectRatioMode(val label: String) {
    FIT("FIT"),
    FILL("FILL"),
    ZOOM("ZOOM"),
    SIXTEEN_NINE("16:9"),
    FOUR_THREE("4:3"),
    ORIGINAL("100%"),
}

data class PlayableMedia(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val uri: String,
    val isVideo: Boolean = true,
    val durationMs: Long = 0L,
    val subtitleUri: String? = null,
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

        subtitleUri?.let { subUri ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUri))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setLabel("English (CC)")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
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
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val selectedAudioTrackIndex: Int = -1,
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedSubtitleTrackIndex: Int = -1, // -1 means subtitles off
    val isLocked: Boolean = false,
    val resumedFromMs: Long? = null,
)
