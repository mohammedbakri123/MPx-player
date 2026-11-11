package com.mpx.player.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/**
 * Audio focus and headphone unplug (AUDIO_BECOMING_NOISY) controller (Finding D-14).
 * Coordinates system audio focus changes, phone call interruptions, and unplugged
 * headset events at the PlaybackCoordinator/PlaybackService boundary.
 */
interface AudioFocusManager {
    /**
     * Request audio focus before starting playback.
     * Returns true if audio focus was granted (playback may proceed).
     */
    fun requestAudioFocus(): Boolean

    /**
     * Abandon audio focus when playback is paused, stopped, or session ends.
     */
    fun abandonAudioFocus()

    /**
     * Set a listener to receive focus changes and noisy events.
     */
    fun setListener(listener: Listener?)

    interface Listener {
        /**
         * Called when audio focus is lost.
         * @param isTransient true if focus loss is temporary (e.g. phone call, notification).
         * @param canDuck true if the app may lower volume instead of fully pausing.
         */
        fun onAudioFocusLost(isTransient: Boolean, canDuck: Boolean)

        /**
         * Called when audio focus is regained after transient loss.
         */
        fun onAudioFocusGained()

        /**
         * Called when audio becomes noisy (e.g. headphones unplugged).
         */
        fun onAudioBecomingNoisy()
    }

    object NoOp : AudioFocusManager {
        override fun requestAudioFocus(): Boolean = true
        override fun abandonAudioFocus() {}
        override fun setListener(listener: Listener?) {}
    }
}

/**
 * Production implementation using [AudioManagerCompat] and [AudioFocusRequestCompat]
 * backed by system [AudioManager] and dynamic [BroadcastReceiver] for [AudioManager.ACTION_AUDIO_BECOMING_NOISY].
 */
class ProductionAudioFocusManager(
    private val context: Context,
) : AudioFocusManager {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var listener: AudioFocusManager.Listener? = null
    private var hasFocus = false
    private var noisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                listener?.onAudioBecomingNoisy()
            }
        }
    }

    private val focusRequest: AudioFocusRequestCompat by lazy {
        val attributes = AudioAttributesCompat.Builder()
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .build()

        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        hasFocus = false
                        unregisterNoisyReceiver()
                        listener?.onAudioFocusLost(isTransient = false, canDuck = false)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        listener?.onAudioFocusLost(isTransient = true, canDuck = false)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        listener?.onAudioFocusLost(isTransient = true, canDuck = true)
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasFocus = true
                        registerNoisyReceiver()
                        listener?.onAudioFocusGained()
                    }
                }
            }
            .build()
    }

    override fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        if (hasFocus) return true

        val result = AudioManagerCompat.requestAudioFocus(am, focusRequest)
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            hasFocus = true
            registerNoisyReceiver()
            true
        } else {
            false
        }
    }

    override fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (hasFocus) {
            AudioManagerCompat.abandonAudioFocusRequest(am, focusRequest)
            hasFocus = false
        }
        unregisterNoisyReceiver()
    }

    override fun setListener(listener: AudioFocusManager.Listener?) {
        this.listener = listener
    }

    private fun registerNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                ContextCompat.registerReceiver(
                    context,
                    noisyReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED,
                )
                noisyReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (noisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {
            }
            noisyReceiverRegistered = false
        }
    }
}
