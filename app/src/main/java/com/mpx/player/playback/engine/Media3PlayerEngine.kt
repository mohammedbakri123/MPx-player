package com.mpx.player.playback.engine

import android.content.Context
import android.view.Surface
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.mpx.player.playback.AudioTrackInfo
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.SubtitleTrackInfo

/**
 * Media3/ExoPlayer implementation of [PlayerEngine].
 * Wraps ExoPlayer instance management, hardware/software codec pipeline configuration,
 * track selections, and surface rendering bindings.
 */
class Media3PlayerEngine(
    private val context: Context,
    initialDecoderMode: DecoderMode = DecoderMode.HW,
) : PlayerEngine {

    private val appContext: Context = context.applicationContext
    private var engineListener: PlayerEngine.Listener? = null
    private var currentQueue: List<PlayableMedia> = emptyList()

    var decoderMode: DecoderMode = initialDecoderMode
        private set

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            engineListener?.onIsPlayingChanged(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            engineListener?.onPlaybackStateChanged(playbackState)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            engineListener?.onMediaItemTransition(
                index = player.currentMediaItemIndex,
                isAuto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            )
        }

        override fun onTracksChanged(tracks: Tracks) {
            engineListener?.onTracksChanged()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            engineListener?.onPlaybackParametersChanged(playbackParameters.speed)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            engineListener?.onVideoDecoderInitialized(decoderName)
        }
    }

    // player MUST be initialized after playerListener and analyticsListener (issue #42)
    var player: ExoPlayer = createPlayer(initialDecoderMode)
        private set

    private fun createPlayer(mode: DecoderMode): ExoPlayer {
        val selector = MpxMediaCodecSelector(mode)
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
            setMediaCodecSelector(selector)
            when (mode) {
                DecoderMode.HW -> {
                    forceDisableMediaCodecAsynchronousQueueing()
                }
                DecoderMode.HW_PLUS -> {
                    forceEnableMediaCodecAsynchronousQueueing()
                    setAllowedVideoJoiningTimeMs(0)
                }
                DecoderMode.SW -> {
                    forceDisableMediaCodecAsynchronousQueueing()
                }
            }
        }
        val newPlayer = ExoPlayer.Builder(appContext, renderersFactory).build()
        newPlayer.addListener(playerListener)
        newPlayer.addAnalyticsListener(analyticsListener)
        return newPlayer
    }

    override fun setListener(listener: PlayerEngine.Listener?) {
        this.engineListener = listener
    }

    override fun attachSurface(surface: Surface) {
        player.setVideoSurface(surface)
    }

    fun attachSurface(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    override fun detachSurface() {
        player.clearVideoSurface()
    }

    override fun load(media: PlayableMedia, resumeMs: Long) {
        load(listOf(media), 0, resumeMs)
    }

    override fun load(items: List<PlayableMedia>, startIndex: Int, resumeMs: Long) {
        currentQueue = items
        if (items.isEmpty()) return
        val mediaItems = items.map { it.toMediaItem() }
        val targetIndex = startIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(mediaItems, targetIndex, resumeMs)
        player.prepare()
    }

    override fun play() {
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        val dur = player.duration.coerceAtLeast(0L)
        val target = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        player.seekTo(target)
    }

    override fun seekTo(index: Int, positionMs: Long) {
        player.seekTo(index, positionMs)
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun selectTrack(type: TrackType, id: String?) {
        when (type) {
            TrackType.AUDIO -> {
                if (id == null || id == "-1") {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .build()
                } else {
                    val parts = id.split(":")
                    if (parts.size == 2) {
                        val groupIndex = parts[0].toIntOrNull() ?: return
                        val trackIndex = parts[1].toIntOrNull() ?: return
                        selectAudioTrack(groupIndex, trackIndex)
                    } else {
                        val trackIdx = id.toIntOrNull() ?: return
                        val tracks = getAudioTracks()
                        val target = tracks.getOrNull(trackIdx) ?: return
                        selectAudioTrack(target.groupIndex, target.trackIndex)
                    }
                }
            }
            TrackType.SUBTITLE -> {
                if (id == null || id == "-1" || id.isBlank()) {
                    selectSubtitleTrack(null, null)
                } else {
                    val parts = id.split(":")
                    if (parts.size == 2) {
                        val groupIndex = parts[0].toIntOrNull()
                        val trackIndex = parts[1].toIntOrNull()
                        selectSubtitleTrack(groupIndex, trackIndex)
                    } else {
                        val trackIdx = id.toIntOrNull()
                        if (trackIdx == null || trackIdx < 0) {
                            selectSubtitleTrack(null, null)
                        } else {
                            val tracks = getSubtitleTracks()
                            val target = tracks.getOrNull(trackIdx)
                            if (target != null) {
                                selectSubtitleTrack(target.groupIndex, target.trackIndex)
                            } else {
                                selectSubtitleTrack(null, null)
                            }
                        }
                    }
                }
            }
            TrackType.VIDEO -> {
                // Video track selection placeholder
            }
        }
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
            )
            .build()
    }

    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        if (groupIndex == null || trackIndex == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
        } else {
            val tracks = player.currentTracks
            val group = tracks.groups.getOrNull(groupIndex) ?: return
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                )
                .build()
        }
    }

    override fun replaceMediaItem(index: Int, media: PlayableMedia) {
        if (index in 0 until player.mediaItemCount) {
            player.replaceMediaItem(index, media.toMediaItem())
        }
    }

    override fun stop() {
        player.stop()
    }

    override fun release() {
        player.removeListener(playerListener)
        player.removeAnalyticsListener(analyticsListener)
        player.stop()
        player.release()
    }

    override fun setDecoderMode(mode: DecoderMode) {
        if (decoderMode == mode) return

        val wasPlaying = player.isPlaying
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val currentIndex = player.currentMediaItemIndex
        val currentSpeed = player.playbackParameters.speed
        val hasQueue = currentQueue.isNotEmpty()

        // 1. Release previous player instance
        player.removeListener(playerListener)
        player.removeAnalyticsListener(analyticsListener)
        player.stop()
        player.release()

        // 2. Build new player with new decoder configuration
        decoderMode = mode
        val newPlayer = createPlayer(mode)
        player = newPlayer
        newPlayer.setPlaybackSpeed(currentSpeed)

        // 3. Restore active queue and timeline position
        if (hasQueue) {
            val mediaItems = currentQueue.map { it.toMediaItem() }
            val targetIndex = currentIndex.coerceIn(0, currentQueue.lastIndex)
            newPlayer.setMediaItems(mediaItems, targetIndex, currentPos)
            newPlayer.prepare()
            if (wasPlaying) {
                newPlayer.play()
            }
        }
    }

    override val currentPosition: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    override val duration: Long
        get() = player.duration.coerceAtLeast(0L)

    override val bufferedPosition: Long
        get() = player.bufferedPosition.coerceAtLeast(0L)

    override val isPlaying: Boolean
        get() = player.isPlaying

    override val playbackState: Int
        get() = player.playbackState

    override val currentMediaItemIndex: Int
        get() = player.currentMediaItemIndex

    override val hasNextMediaItem: Boolean
        get() = player.hasNextMediaItem()

    override val hasPreviousMediaItem: Boolean
        get() = player.hasPreviousMediaItem()

    override fun seekToNextMediaItem() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    override fun getAudioTracks(): List<AudioTrackInfo> {
        val tracks = player.currentTracks
        val audioList = mutableListOf<AudioTrackInfo>()
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (trackIndex in 0 until group.length) {
                val isSelected = group.isTrackSelected(trackIndex)
                val format = group.getTrackFormat(trackIndex)
                val label = format.label ?: format.language ?: "Track ${trackIndex + 1}"
                val language = format.language ?: "und"
                audioList.add(
                    AudioTrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                        label = label,
                        language = language,
                        isSelected = isSelected,
                    )
                )
            }
        }
        return audioList
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val tracks = player.currentTracks
        val subtitleList = mutableListOf<SubtitleTrackInfo>()
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (trackIndex in 0 until group.length) {
                val isSelected = group.isTrackSelected(trackIndex)
                val format = group.getTrackFormat(trackIndex)
                val label = format.label ?: format.language ?: "Track ${trackIndex + 1}"
                val language = format.language ?: "und"
                subtitleList.add(
                    SubtitleTrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                        label = label,
                        language = language,
                        isSelected = isSelected,
                        isExternal = false,
                    )
                )
            }
        }
        return subtitleList
    }
}
