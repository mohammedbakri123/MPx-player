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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Real MediaSession foreground service for background audio playback.
 * Keeps ExoPlayer playback alive when the app is backgrounded or screen is turned off,
 * provides rich lockscreen & notification media controls with play/pause/next/previous,
 * and maintains honest reporting for video audio-only continuation.
 */
class PlaybackService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaSession: MediaSessionCompat
    private var isForegroundActive = false

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
            ACTION_STOP -> {
                playbackManager.pause()
                stopForegroundInternal(removeNotification = true)
                stopSelf()
            }
            ACTION_START -> {
                updateSessionAndNotification(playbackManager.playbackState.value)
            }
        }
        return START_NOT_STICKY
    }

    private fun updateSessionAndNotification(state: PlaybackState) {
        val currentItem = state.currentItem
        if (currentItem == null) {
            if (!state.isPlaying) {
                stopForegroundInternal(removeNotification = true)
                stopSelf()
            }
            return
        }

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
                currentItem.subtitle.ifBlank { "MPx Player" },
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ALBUM,
                if (currentItem.isVideo) "Video Playback" else "Music Track",
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

        val honestSubtext = if (currentItem.isVideo) {
            "Background Audio • Video rendering paused"
        } else {
            "MPx Music"
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(currentItem.title)
            .setContentText(currentItem.subtitle.ifBlank { if (currentItem.isVideo) "Video (Audio only in background)" else "MPx Track" })
            .setSubText(honestSubtext)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_prev,
                    "Previous",
                    prevPendingIntent,
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    if (state.isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
                    if (state.isPlaying) "Pause" else "Play",
                    togglePendingIntent,
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_next,
                    "Next",
                    nextPendingIntent,
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_close,
                    "Stop",
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
        isForegroundActive = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val state = playbackManager.playbackState.value
        val settings = playbackManager.settingsRepository.load()
        if (!settings.backgroundPlay || !state.isPlaying) {
            playbackManager.pause()
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

    companion object {
        const val CHANNEL_ID = "mpx_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.mpx.player.playback.ACTION_START"
        const val ACTION_PLAY = "com.mpx.player.playback.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mpx.player.playback.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.mpx.player.playback.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.mpx.player.playback.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.mpx.player.playback.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.mpx.player.playback.ACTION_STOP"
        const val ACTION_OPEN_PLAYER = "com.mpx.player.ACTION_OPEN_PLAYER"
        const val EXTRA_OPEN_PLAYER = "open_player"

        fun start(context: Context) {
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
