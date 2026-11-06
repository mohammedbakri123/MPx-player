package com.mpx.player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.mpx.player.MainActivity
import com.mpx.player.R
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Real MediaSession foreground service for background audio playback.
 * Keeps mpv playback alive when the app is backgrounded or screen is turned off,
 * provides rich lockscreen & notification media controls with play/pause/next/previous,
 * and maintains honest reporting for video audio-only continuation.
 */
class PlaybackService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaSession: MediaSessionCompat
    private var isForegroundActive = false

    // Issue #284: the values the notification / MediaSession were last
    // rebuilt from. updateSessionAndNotification runs on every
    // playbackState emission — including the 250 ms position ticker —
    // but the notification shows no position at all and the MediaSession
    // only needs a 1-second position granularity (the system
    // extrapolates the seek bar from the update time + speed), so a
    // rebuild is skipped unless something displayed actually changed.
    // Play/pause and track changes still rebuild immediately (they
    // change the snapshot), and seeks land within one 1-second bucket.
    private var lastNotificationSnapshot: NotificationSnapshot? = null

    override fun onCreate() {
        super.onCreate()
        playbackManager = PlaybackManager.getInstance(applicationContext)

        createNotificationChannel()
        initMediaSession()
        observePlaybackState()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MPx Media Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "MPx Background Media Playback and Controls"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MPxMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    playbackManager.play()
                }

                override fun onPause() {
                    playbackManager.pause()
                }

                override fun onSkipToNext() {
                    playbackManager.skipNext()
                }

                override fun onSkipToPrevious() {
                    playbackManager.skipPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    playbackManager.seekTo(pos)
                }

                override fun onStop() {
                    playbackManager.pause()
                    playbackManager.setVideoBackgroundActive(false)
                    playbackManager.setBackgroundPlaying(false)
                    stopForegroundInternal(removeNotification = true)
                    stopSelf()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            isActive = true
        }
    }

    /** Issue #351: the PiP seek duration is the double-tap skip setting, read fresh on every tap. */
    private fun skipDurationMs(): Long =
        playbackManager.settingsRepository.load().doubleTapSeekSeconds * 1000L

    private fun observePlaybackState() {
        serviceScope.launch {
            playbackManager.playbackState.collectLatest { state ->
                updateSessionAndNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> playbackManager.play()
            ACTION_PAUSE -> playbackManager.pause()
            ACTION_TOGGLE -> playbackManager.togglePlayPause()
            ACTION_NEXT -> playbackManager.skipNext()
            ACTION_PREVIOUS -> playbackManager.skipPrevious()
            // Issue #351: PiP seek follows the double-tap skip setting
            // (5/10/15/30 s, default 10), read live here so a changed
            // setting applies even to PiP params built earlier. The
            // ACTION_SEEK_*_10 names are legacy identifiers only — the
            // duration is no longer 10 s by definition.
            ACTION_SEEK_FORWARD_10 -> playbackManager.skipForward(skipDurationMs())
            ACTION_SEEK_BACKWARD_10 -> playbackManager.skipBackward(skipDurationMs())
            ACTION_STOP -> {
                playbackManager.pause()
                playbackManager.setVideoBackgroundActive(false)
                playbackManager.setBackgroundPlaying(false)
                stopForegroundInternal(removeNotification = true)
                stopSelf()
            }
            ACTION_START -> {
                val settings = playbackManager.settingsRepository.load()
                if (settings.backgroundPlay) {
                    updateSessionAndNotification(playbackManager.playbackState.value)
                } else {
                    stopForegroundInternal(removeNotification = true)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun updateSessionAndNotification(state: PlaybackState) {
        val settings = playbackManager.settingsRepository.load()
        if (!settings.backgroundPlay) {
            stopForegroundInternal(removeNotification = true)
            stopSelf()
            return
        }

        val currentItem = state.currentItem
        if (currentItem == null) {
            if (!state.isPlaying) {
                stopForegroundInternal(removeNotification = true)
                stopSelf()
            }
            return
        }

        // Issue #284: rebuild only when something the notification or
        // MediaSession shows actually changed — track (title/subtitle/
        // video-vs-audio), play state, speed, duration, or the 1-second
        // position bucket. The 250 ms ticker otherwise emits an identical
        // snapshot 4x/second and each one used to rebuild the full
        // notification (4 PendingIntents), MediaSession state, and
        // metadata. The stop paths above still run on every emission.
        val snapshot = NotificationSnapshot(
            item = currentItem,
            isPlaying = state.isPlaying,
            speed = state.speed,
            durationMs = state.durationMs,
            positionBucketMs = state.currentPositionMs / 1_000L,
        )
        if (snapshot == lastNotificationSnapshot) return
        lastNotificationSnapshot = snapshot

        // 1. Update MediaSession PlaybackState
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        val playbackStateCompat = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                state.currentPositionMs,
                state.speed,
                SystemClock.elapsedRealtime(),
            )
            .build()
        mediaSession.setPlaybackState(playbackStateCompat)

        // 2. Update MediaSession Metadata
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentItem.title)
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                currentItem.subtitle.ifBlank { getString(R.string.app_name) },
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ALBUM,
                if (currentItem.isVideo) getString(R.string.media_session_album_video) else getString(R.string.media_session_album_music),
            )
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
            .build()
        mediaSession.setMetadata(metadata)

        // 3. Build Notification
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).apply {
                action = if (state.isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Localised notification text (issue #99): action titles and
        // subtexts were hardcoded English before.
        val honestSubtext = if (currentItem.isVideo) {
            getString(R.string.notification_subtext_bg_video)
        } else {
            getString(R.string.notification_subtext_music)
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(currentItem.title)
            .setContentText(currentItem.subtitle.ifBlank { if (currentItem.isVideo) getString(R.string.notification_content_video_bg) else getString(R.string.notification_content_track) })
            .setSubText(honestSubtext)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_prev,
                    getString(R.string.mini_player_prev),
                    prevPendingIntent,
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    if (state.isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
                    if (state.isPlaying) getString(R.string.mini_player_pause) else getString(R.string.mini_player_play),
                    togglePendingIntent,
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_next,
                    getString(R.string.mini_player_next),
                    nextPendingIntent,
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_close,
                    getString(R.string.mini_player_close),
                    stopPendingIntent,
                ),
            )
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopPendingIntent),
            )

        val notification = notificationBuilder.build()

        if (state.isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundActive = true
            } catch (_: Exception) {
            }
        } else {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            if (isForegroundActive) {
                stopForegroundInternal(removeNotification = false)
            }
        }
    }

    private fun stopForegroundInternal(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH,
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
        if (removeNotification) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        }
        isForegroundActive = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val state = playbackManager.playbackState.value
        val settings = playbackManager.settingsRepository.load()
        if (!settings.backgroundPlay || !state.isPlaying) {
            playbackManager.pause()
            playbackManager.setVideoBackgroundActive(false)
            playbackManager.setBackgroundPlaying(false)
            stopForegroundInternal(removeNotification = true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        stopForegroundInternal(removeNotification = true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Issue #284: the values the notification / MediaSession actually
     * display. [PlayableMedia] is a data class, so equality covers
     * title/subtitle/video-vs-audio; [positionBucketMs] is the position
     * in whole seconds — the MediaSession seek bar is extrapolated by
     * the system from the update time + speed, so 1-second granularity
     * is smooth while the 250 ms ticker no longer triggers a rebuild.
     */
    private data class NotificationSnapshot(
        val item: PlayableMedia?,
        val isPlaying: Boolean,
        val speed: Float,
        val durationMs: Long,
        val positionBucketMs: Long,
    )

    companion object {
        const val CHANNEL_ID = "mpx_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.mpx.player.playback.ACTION_START"
        const val ACTION_PLAY = "com.mpx.player.playback.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mpx.player.playback.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.mpx.player.playback.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.mpx.player.playback.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.mpx.player.playback.ACTION_PREVIOUS"
        const val ACTION_SEEK_FORWARD_10 = "com.mpx.player.playback.ACTION_SEEK_FORWARD_10"
        const val ACTION_SEEK_BACKWARD_10 = "com.mpx.player.playback.ACTION_SEEK_BACKWARD_10"
        const val ACTION_STOP = "com.mpx.player.playback.ACTION_STOP"
        const val ACTION_OPEN_PLAYER = "com.mpx.player.ACTION_OPEN_PLAYER"
        const val EXTRA_OPEN_PLAYER = "open_player"

        fun start(context: Context) {
            val settings = SharedPrefsAppSettingsRepository(context).load()
            if (!settings.backgroundPlay) {
                return
            }
            try {
                val intent = Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
