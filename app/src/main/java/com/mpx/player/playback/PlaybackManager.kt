package com.mpx.player.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.domain.model.AudioTrack
import com.mpx.player.domain.model.PlaybackHistoryItem
import com.mpx.player.domain.repository.MediaPersistenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Central controller for Media3 ExoPlayer playback in MPx Player.
 * Manages the ExoPlayer instance, queue state, real position ticker,
 * Room database-backed persistent resume tracking, playback history recording,
 * and audio/subtitle stream track switching.
 */
class PlaybackManager private constructor(context: Context) {

    val database: MpxDatabase = MpxDatabase.getInstance(context)
    val persistenceRepository: MediaPersistenceRepository = RoomMediaPersistenceRepository(database)
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    val resumeManager = PlaybackResumeManager(persistenceRepository)

    private val scope = CoroutineScope(Dispatchers.Main)
    private var tickerJob: Job? = null
    private var currentQueue: List<PlayableMedia> = emptyList()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
                updatePosition()
                _playbackState.value.currentItem?.let { item ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    if (pos > 1_500L) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                persistenceRepository.recordHistory(
                                    PlaybackHistoryItem(
                                        mediaUri = item.uri,
                                        mediaId = item.id,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        isVideo = item.isVideo,
                                        lastPlayedAt = System.currentTimeMillis(),
                                        playbackPositionMs = pos,
                                        durationMs = if (dur > 0L) dur else item.durationMs,
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState.update { it.copy(playbackState = playbackState) }
            updatePosition()
            updateTracksState()
            if (playbackState == Player.STATE_ENDED) {
                _playbackState.value.currentItem?.let {
                    resumeManager.clearResumePosition(it.id)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player.currentMediaItemIndex
            val current = currentQueue.getOrNull(index)

            val savedPos = current?.let { resumeManager.getResumePosition(it.id) }
            val shouldResume = savedPos != null && savedPos > 2_000L

            if (shouldResume && savedPos != null) {
                player.seekTo(savedPos)
            }

            current?.let { item ->
                scope.launch(Dispatchers.IO) {
                    try {
                        persistenceRepository.recordHistory(
                            PlaybackHistoryItem(
                                mediaUri = item.uri,
                                mediaId = item.id,
                                title = item.title,
                                subtitle = item.subtitle,
                                isVideo = item.isVideo,
                                lastPlayedAt = System.currentTimeMillis(),
                                playbackPositionMs = savedPos ?: 0L,
                                durationMs = item.durationMs,
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            _playbackState.update {
                it.copy(
                    currentItem = current,
                    currentIndex = index,
                    hasPrevious = player.hasPreviousMediaItem(),
                    hasNext = player.hasNextMediaItem(),
                    resumedFromMs = if (shouldResume) savedPos else null,
                )
            }
            updateTracksState()
            updatePosition()
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTracksState()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackState.update { it.copy(speed = playbackParameters.speed) }
        }
    }

    init {
        player.addListener(listener)
    }

    fun playQueue(items: List<PlayableMedia>, startIndex: Int = 0, autoPlay: Boolean = true) {
        if (items.isEmpty()) return
        currentQueue = items

        val targetIndex = startIndex.coerceIn(0, items.lastIndex)
        val targetItem = items[targetIndex]
        val savedPos = resumeManager.getResumePosition(targetItem.id) ?: 0L
        val shouldResume = savedPos > 2_000L

        val mediaItems = items.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, targetIndex, if (shouldResume) savedPos else 0L)
        player.prepare()
        if (autoPlay) {
            player.play()
        }

        targetItem.let { item ->
            scope.launch(Dispatchers.IO) {
                try {
                    persistenceRepository.recordHistory(
                        PlaybackHistoryItem(
                            mediaUri = item.uri,
                            mediaId = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            isVideo = item.isVideo,
                            lastPlayedAt = System.currentTimeMillis(),
                            playbackPositionMs = if (shouldResume) savedPos else 0L,
                            durationMs = item.durationMs,
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }

        _playbackState.update {
            it.copy(
                currentItem = targetItem,
                queue = items,
                currentIndex = targetIndex,
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
                resumedFromMs = if (shouldResume) savedPos else null,
            )
        }
        updatePosition()
        updateTracksState()
    }

    fun playAudioQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        val items = tracks.map { track ->
            val uri = if (track.path.isNotBlank()) track.path else when (track.id) {
                "1" -> "asset:///media/midnight_drive.mp3"
                "2" -> "asset:///media/electric_echoes.mp3"
                "3" -> "asset:///media/acoustic_morning.mp3"
                "4" -> "asset:///media/midnight_drive.mp3"
                else -> "asset:///media/electric_echoes.mp3"
            }
            PlayableMedia(
                id = track.id,
                title = track.title,
                subtitle = "${track.artist} • ${track.album}",
                uri = uri,
                isVideo = false,
                durationMs = 15_000L,
            )
        }
        playQueue(items, startIndex, autoPlay = true)
    }

    fun playVideoQueue(folderId: String = "camera") {
        playQueue(getDefaultVideoQueue(), startIndex = 0, autoPlay = true)
    }

    /** Play real video files from a MediaStore folder. Empty list = no-op, caller shows an honest empty state. */
    fun playVideoFiles(videos: List<com.mpx.player.domain.model.VideoFile>, startIndex: Int = 0) {
        if (videos.isEmpty()) return
        val items = videos.map { video ->
            PlayableMedia(
                id = video.id,
                title = video.title,
                subtitle = "",
                uri = video.path,
                isVideo = true,
                durationMs = video.durationMs,
            )
        }
        playQueue(items, startIndex, autoPlay = true)
    }

    fun getDefaultVideoQueue(): List<PlayableMedia> = listOf(
        PlayableMedia(
            id = "sample_video_1080p",
            title = "Big Buck Bunny Preview (720p Dual-Audio)",
            subtitle = "Camera • 720p H.264",
            uri = "asset:///media/sample_video.mp4",
            isVideo = true,
            durationMs = 30_000L,
            subtitleUri = "asset:///media/sample_video.vtt",
        ),
        PlayableMedia(
            id = "sample_video_clip2",
            title = "MPx Engine Benchmark Clip",
            subtitle = "Download • 720p H.264",
            uri = "asset:///media/sample_video.mp4",
            isVideo = true,
            durationMs = 30_000L,
            subtitleUri = "asset:///media/sample_video.vtt",
        ),
    )

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val duration = player.duration.coerceAtLeast(0L)
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        player.seekTo(target)
        updatePosition()
    }

    fun skipForward(deltaMs: Long = 10_000L) {
        seekTo(player.currentPosition + deltaMs)
    }

    fun skipBackward(deltaMs: Long = 10_000L) {
        seekTo(player.currentPosition - deltaMs)
    }

    fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            seekTo(0L)
        }
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatio = mode) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        _playbackState.update { it.copy(decoderMode = mode) }
    }

    fun setLocked(locked: Boolean) {
        _playbackState.update { it.copy(isLocked = locked) }
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
            )
            .build()
        updateTracksState()
    }

    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
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
        updateTracksState()
    }

    fun stop() {
        player.stop()
        stopTicker()
        _playbackState.update {
            it.copy(
                currentItem = null,
                queue = emptyList(),
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L,
            )
        }
    }

    fun clearResumedNotification() {
        _playbackState.update { it.copy(resumedFromMs = null) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                updatePosition()
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updatePosition() {
        val current = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(0L)
        val buffered = player.bufferedPosition.coerceAtLeast(0L)

        // Save position into persistent resume store
        _playbackState.value.currentItem?.let { item ->
            if (current > 1_500L && (duration <= 0L || current < duration - 2_000L)) {
                resumeManager.saveResumePosition(
                    id = item.id,
                    positionMs = current,
                    mediaUri = item.uri,
                    durationMs = duration,
                )
            }
        }

        _playbackState.update {
            it.copy(
                currentPositionMs = current,
                durationMs = duration,
                bufferedPositionMs = buffered,
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
            )
        }
    }

    private fun updateTracksState() {
        val tracks = player.currentTracks
        val audioList = mutableListOf<AudioTrackInfo>()
        val subtitleList = mutableListOf<SubtitleTrackInfo>()

        var selectedAudioIdx = -1
        var selectedSubtitleIdx = -1

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            for (trackIndex in 0 until group.length) {
                val isSelected = group.isTrackSelected(trackIndex)
                val format = group.getTrackFormat(trackIndex)
                val label = format.label ?: format.language ?: "Track ${trackIndex + 1}"
                val language = format.language ?: "und"

                if (trackType == C.TRACK_TYPE_AUDIO) {
                    if (isSelected) selectedAudioIdx = audioList.size
                    audioList.add(
                        AudioTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            language = language,
                            isSelected = isSelected,
                        )
                    )
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    if (isSelected) selectedSubtitleIdx = subtitleList.size
                    subtitleList.add(
                        SubtitleTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            language = language,
                            isSelected = isSelected,
                        )
                    )
                }
            }
        }

        _playbackState.update {
            it.copy(
                audioTracks = audioList,
                selectedAudioTrackIndex = selectedAudioIdx,
                subtitleTracks = subtitleList,
                selectedSubtitleTrackIndex = selectedSubtitleIdx,
            )
        }
    }

    companion object {
        @Volatile
        private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
