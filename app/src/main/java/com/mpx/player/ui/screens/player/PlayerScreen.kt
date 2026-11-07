package com.mpx.player.ui.screens.player
import com.mpx.player.ui.theme.MpxTheme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.model.FavouriteItem
import com.mpx.player.domain.model.PlaylistItem
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.AspectRatioMode
import com.mpx.player.playback.DecoderMode
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.playback.PlaybackService
import com.mpx.player.playback.PlaybackState
import com.mpx.player.playback.RepeatMode
import com.mpx.player.ui.components.persistence.AddToPlaylistDialog
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.screens.settings.parsePlayerMenuLayout
import com.mpx.player.ui.screens.settings.playerMenuRowLabelRes
import com.mpx.player.util.PipUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.mpx.player.ui.components.MpxLoadingIndicator
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

private enum class DragGestureMode {
    NONE,
    HORIZONTAL_SEEK,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME
}

private enum class SideSeekDirection {
    BACKWARD,
    FORWARD
}

private fun getScreenBrightness(context: Context, activity: Activity?): Float {
    val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (windowBrightness in 0f..1f) {
        windowBrightness
    } else {
        try {
            val systemBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128,
            )
            (systemBrightness / 255f).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0.5f
        }
    }
}

private fun setScreenBrightness(activity: Activity?, brightness: Float): Float {
    val target = brightness.coerceIn(0f, 1f)
    activity?.window?.let { window ->
        val lp = window.attributes
        lp.screenBrightness = target
        window.attributes = lp
    }
    return activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: target
}

private fun setMediaVolume(audioManager: AudioManager?, targetVolume: Int, minVolume: Int, maxVolume: Int): Int {
    if (audioManager == null) return 0
    val clamped = targetVolume.coerceIn(minVolume, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
}

@Composable
private fun VerticalIndicatorBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    width: Dp = 6.dp,
    trackColor: Color = MpxTheme.colors.textPrimary.copy(alpha = 0.25f),
    progressColor: Color = MpxTheme.colors.primary,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(height)
            .width(width)
            .clip(RoundedCornerShape(width / 2))
            .background(trackColor),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height * clamped)
                    .clip(RoundedCornerShape(width / 2))
                    .background(progressColor),
            )
        }
    }
}

// Slim gesture HUD modelled on MX's brightbar (issue #94): a narrow vertical
// strip — percent text on top, a slim 4dp bar, a small icon at the bottom —
// over a subtle vertical gradient. No heavy black box, no colored border.
// Issue #190: the first slimming still read too big on the phone, so the
// HUD shrinks again — 40dp wide, 64dp bar, 16dp icon, 11sp text — closer
// to MX's brightbar proportions while staying readable at a glance.

// "1×" / "1.5×" / "0.75×" — rounds to 2 decimals (pinch produces
// long floats) and trims the trailing ".0" off whole values (#172).
private fun formatMultiplier(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100f
    return rounded.toString().removeSuffix(".0") + "×"
}

@Composable
private fun GestureHud(
    percentText: String,
    fraction: Float,
    icon: ImageVector,
    iconDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(40.dp)
            .clip(MpxShapes.large)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MpxTheme.colors.black.copy(alpha = 0.45f),
                        MpxTheme.colors.black.copy(alpha = 0.65f),
                    ),
                ),
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentText,
                color = MpxTheme.colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            VerticalIndicatorBar(
                fraction = fraction,
                height = 64.dp,
                width = 4.dp,
                trackColor = MpxTheme.colors.textPrimary.copy(alpha = 0.25f),
                progressColor = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = MpxTheme.colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// Modern blur for player dialogs (issue #85): on Android 12+ the dialog
// window blurs whatever is behind it, so the dialog reads as frosted
// glass instead of a flat panel. On older Android the flag is a no-op
// and the dialog keeps its solid surface, so readability never depends
// on blur. Call this from inside a dialog's content (AlertDialog title
// or text block), where LocalView resolves to the dialog's own window.
@Composable
private fun PlayerDialogBackdropBlur() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                it.attributes = it.attributes.apply {
                    blurBehindRadius = (16 * view.resources.displayMetrics.density).toInt()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    subtitleResultHandler: ActivityResultHandler<android.net.Uri?>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager = PlaybackManager.getInstance(LocalContext.current),
    settingsRepository: AppSettingsRepository? = null,
    // True while the activity is in system PiP mode: hide every overlay so
    // the PiP window shows video only (issue #98).
    isInPipMode: Boolean = false,
) {
    val context = LocalContext.current
    // Issue #179: do NOT resolve the activity from LocalContext. MainActivity
    // provides a localized configuration context as LocalContext
    // (LocaleHelper.createLocalizedContext = createConfigurationContext),
    // a detached ContextImpl whose base chain never reaches the Activity —
    // so context.findActivity() returned null and every
    // activity?.requestedOrientation set below was a silent no-op (the
    // toast still showed and the setting still saved, which is why the
    // chain "read correct"). The host view's context IS the real Activity
    // (LocalActivity does not exist in activity-compose 1.9.2, verified
    // against the AAR). This also fixes #194 for free: the brightness
    // get/set below uses this same activity reference.
    val activity = LocalView.current.context as? Activity
    val realSettingsRepo = settingsRepository ?: remember(context) { SharedPrefsAppSettingsRepository(context) }
    var currentSettings by remember { mutableStateOf(realSettingsRepo.load()) }
    // Last subtitle settings pushed to the mpv engine. Plain holder (not state)
    // so pushing options does not schedule extra recompositions.
    val lastMpvSubtitleSettings = remember { arrayOfNulls<AppSettings>(1) }

    // Issue #241: Settings can change while this screen sits in the back
    // stack (user opens Settings, changes subtitle size/delay/speed, audio
    // delay or zoom, then comes back). currentSettings was loaded once at
    // entry, so the playing video kept the old values until the player was
    // recreated. Reload on ON_RESUME — the same shape as LocalScreen's
    // reload — and the AndroidView update block re-pushes to mpv only when
    // the values actually changed (structural AppSettings equality), so an
    // unchanged reload is a no-op. Settings only: no folder/permission
    // refresh here, the player has none. A changed rotation setting also
    // flows through the existing targetOrientation remember + effect below,
    // which is the point: the user changed it in Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentSettings = realSettingsRepo.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val state by playbackManager.playbackState.collectAsState()

    // Issue #149: after a process death in the background, Android restores
    // this destination but the PlaybackManager singleton is fresh with an
    // empty queue, which used to render the "No video queued" empty state.
    // Restore the last persisted queue instead (paused, at the saved
    // position). Deliberate stops clear the persisted queue, so this only
    // fires for sessions the user never ended; with nothing persisted it is
    // a no-op and the empty state stays.
    LaunchedEffect(Unit) {
        if (playbackManager.playbackState.value.queue.isEmpty()) {
            playbackManager.restorePersistedQueueIfEmpty()
        }
    }

    // Issue #363: MX-style rotation — every video starts in portrait,
    // and the stripe's rotation button toggles landscape directly, no
    // menu. In Auto mode the target is SENSOR_PORTRAIT whatever the
    // video's aspect (the old #329 aspect rule is gone: a wide video no
    // longer flips the phone on load). The toggle is a per-video session
    // override: keyed to the current item id, so the next video starts
    // in portrait again. An explicit Portrait/Landscape lock in
    // Settings still wins as the default the override starts from.
    var rotationOverride by remember(state.currentItem?.id) { mutableStateOf<String?>(null) }
    // The stripe toggle and caption read the effective orientation
    // (override, else the persisted setting; Auto means portrait).
    val isLandscapeOrientation = rotationOverride?.equals("landscape", ignoreCase = true) == true ||
        (rotationOverride == null && !currentSettings.autoScreenRotation &&
            currentSettings.screenOrientation.equals("landscape", ignoreCase = true))
    val targetOrientation = remember(
        currentSettings.autoScreenRotation, currentSettings.screenOrientation,
        rotationOverride,
    ) {
        when {
            rotationOverride.equals("landscape", ignoreCase = true) ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            rotationOverride.equals("portrait", ignoreCase = true) ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            currentSettings.autoScreenRotation || currentSettings.screenOrientation == "auto" ||
                currentSettings.screenOrientation == "sensor" ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            currentSettings.screenOrientation.equals("portrait", ignoreCase = true) ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    DisposableEffect(targetOrientation, activity) {
        activity?.requestedOrientation = targetOrientation
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Issue #204: the window-brightness reset used to live in the
    // orientation effect's onDispose above. That effect is keyed on
    // targetOrientation, so its onDispose fired not only when leaving
    // the player but every time the user picked Auto / Landscape /
    // Portrait in the sheet or toggled auto-rotate — silently wiping
    // the brightness just set with the vertical-drag gesture back to
    // the system value. The reset belongs here instead: a
    // DisposableEffect(Unit) disposes only when this PlayerScreen
    // composition actually leaves (back navigation, activity finish).
    // Rotation and PiP entry do NOT dispose it — MainActivity declares
    // orientation and the screen-size changes in configChanges, so
    // those only recompose. Unconditional on purpose, unlike the #92
    // stop() below: with background play on, leaving still pops this
    // route, and the override must not leak onto the Home screen.
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    var subtitleErrorDialogText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(subtitleResultHandler) {
        onDispose { subtitleResultHandler.callback = null }
    }

    // Issue #92: leaving the player screen stops playback and clears the
    // mini-player preview — unless background play is explicitly enabled.
    // This fires for both the top-bar back action and system back, because
    // both pop this destination and dispose the screen. Rotation does NOT
    // trigger it: MainActivity declares orientation in configChanges, so
    // rotation recomposes without disposing. Settings are re-read at
    // dispose time, so toggling background play in the player menu and
    // then leaving is honoured. PiP is excluded as a guard (PiP entry
    // itself already requires background play). stop() is the established
    // full-stop path (same as the mini-player close button): it stops the
    // engine and service, clears currentItem (which hides the mini-player)
    // and the queue, and cancels a running sleep timer — which is moot
    // once playback has stopped. With background play on, nothing here
    // runs, so the timer and background playback continue untouched.
    DisposableEffect(Unit) {
        onDispose {
            val activityInPip = activity?.isInPictureInPictureMode == true
            if (!activityInPip && !realSettingsRepo.load().backgroundPlay) {
                playbackManager.stop()
            }
        }
    }
    SideEffect {
        subtitleResultHandler.callback = { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }

            when (val validation = SubtitleUtils.validateSubtitleFile(context, uri)) {
                is SubtitleValidationResult.Supported -> {
                    // Issue #332: no toast here any more. The old toast
                    // fired from the synchronous return value and lied
                    // whenever mpv rejected the file (mpv.command()
                    // never throws for a rejected subtitle), so the
                    // success/failure banner is now shown by
                    // PlaybackManager once the sub-add outcome is
                    // actually known (track in track-list, or timeout).
                    playbackManager.loadExternalSubtitle(
                        uri = validation.uri,
                        displayName = validation.displayName,
                        mimeType = validation.mimeType,
                    )
                }
                is SubtitleValidationResult.Unsupported -> {
                    subtitleErrorDialogText = context.getString(R.string.player_subtitle_load_error, validation.fileName, validation.reason)
                }
            }
        }
        }
    }

    // Empty-player state: no auto-play when opened empty (issue #57)

    var controlsVisible by remember { mutableStateOf(true) }
    // Issue #153: the Player Menu dialog and the whole activeDialog chain
    // (Sleep Timer / Audio Tracks / Subtitles / Decoder / Screen Rotation)
    // are replaced by one MpxBottomSheet (PlayerPlaybackSheet). Null = closed.
    var playbackSheetPage by remember { mutableStateOf<PlaybackSheetPage?>(null) }
    val playbackSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // First-run gesture tutorial (#151): shown once, the first time the
    // player opens with gestures enabled; replayable from the Playback sheet.
    var showTutorial by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val settings = realSettingsRepo.load()
        if (!settings.playerTutorialSeen && settings.gesturesEnabled) {
            showTutorial = true
        }
    }

    // Dismissing (Skip, X, or finishing) marks the tutorial seen, so it
    // never auto-shows again; the Playback sheet entry can still replay it.
    val dismissTutorial = {
        val updated = currentSettings.copy(playerTutorialSeen = true)
        currentSettings = updated
        realSettingsRepo.save(updated)
        showTutorial = false
    }

    val scope = rememberCoroutineScope()

    // Shared player actions (issue #177): the Action Stripe and the
    // Playback sheet trigger the same favourite / PiP / background-play
    // actions, so they are defined once here and passed to both. The
    // lambdas read state/currentSettings when invoked, never when created,
    // so they always see the latest values.
    val toggleFavourite: () -> Unit = {
        state.currentItem?.let { item ->
            scope.launch {
                playbackManager.persistenceRepository.toggleFavourite(
                    FavouriteItem(
                        mediaUri = item.uri,
                        mediaId = item.id,
                        title = item.title,
                        subtitle = item.subtitle,
                        isVideo = item.isVideo,
                        durationMs = item.durationMs,
                    ),
                )
            }
        }
    }
    // Issue #225: PiP is video-only, and entry must carry the REAL playback
    // state — the old bare enterPip(activity) defaulted to playing with 0x0
    // dims, so a paused entry showed a "Pause" action, a portrait video got
    // a 16:9 window, and an audio item opened a black PiP window.
    val enterPip = {
        val item = state.currentItem
        if (item?.isVideo != true) {
            Toast.makeText(context, context.getString(R.string.player_pip_unable), Toast.LENGTH_SHORT).show()
        } else {
            val entered = PipUtils.enterPip(
                activity,
                currentSettings,
                state.isPlaying,
                playbackManager.videoWidth,
                playbackManager.videoHeight,
                true,
            )
            if (!entered) {
                val msg = if (!PipUtils.isPipSupported(context)) {
                    context.getString(R.string.player_pip_unsupported)
                } else {
                    context.getString(R.string.player_pip_unable)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Issue #225: keep the activity's PiP params in sync with reality.
    // The Play/Pause RemoteAction label and the armed auto-enter flag are
    // baked into the params at entry time and never refresh themselves —
    // without this, entering PiP paused showed "Pause" forever, and params
    // armed while playing stayed armed after a pause, so Home auto-entered
    // PiP past the #206 gate. This runs on every play/pause toggle and
    // item change (and on first composition), in PiP and out of it: in PiP
    // it refreshes the action label; out of PiP it re-arms/disarms
    // auto-enter to match the current session.
    // Issue #260: the dimensions are observed state now, not plain
    // getters — at item-change time they are still 0, so the params were
    // armed with the 16:9 fallback and no later trigger corrected them
    // (a portrait video auto-entered as a 16:9 window unless the user
    // toggled play/pause first). Keying on state.videoWidth/Height
    // re-runs this the moment mpv reports the real size, and the body
    // reads the state values so the effect is a pure function of its
    // keys. Manual PiP (enterPip above) and onUserLeaveHint keep reading
    // the live getters — at tap time the video is already playing, so
    // they were never stale.
    LaunchedEffect(state.isPlaying, state.currentItem?.id, isInPipMode, state.videoWidth, state.videoHeight) {
        val act = activity ?: return@LaunchedEffect
        val item = state.currentItem
        PipUtils.updatePipParams(
            act,
            currentSettings,
            state.isPlaying,
            state.videoWidth,
            state.videoHeight,
            item?.isVideo == true,
        )
    }
    val toggleBackgroundPlay = {
        val nextBackgroundPlay = !currentSettings.backgroundPlay
        val updated = currentSettings.copy(backgroundPlay = nextBackgroundPlay)
        currentSettings = updated
        realSettingsRepo.save(updated)
        if (!nextBackgroundPlay) {
            PlaybackService.stop(context)
            playbackManager.setVideoBackgroundActive(false)
            playbackManager.setBackgroundPlaying(false)
        }
        val note = if (nextBackgroundPlay) {
            if (state.currentItem?.isVideo == true) {
                context.getString(R.string.player_bg_enabled_video)
            } else {
                context.getString(R.string.player_bg_enabled_audio)
            }
        } else {
            context.getString(R.string.player_bg_disabled)
        }
        Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
    }

    val customPlaylists by playbackManager.persistenceRepository.observePlaylists().collectAsState(initial = emptyList())
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val currentUri = state.currentItem?.uri.orEmpty()
    val isFavourite by produceState(initialValue = false, currentUri) {
        if (currentUri.isNotBlank()) {
            playbackManager.persistenceRepository.observeIsFavourite(currentUri).collect {
                value = it
            }
        } else {
            value = false
        }
    }

    // Slider scrubbing state
    var isScrubbingSlider by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Horizontal drag seek gesture state
    var isDraggingSeek by remember { mutableStateOf(false) }
    var seekStartMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    // Issue #259: whether playback was running when a horizontal seek
    // drag paused it, so the drag can resume it on release.
    var wasPlayingBeforeSeekDrag by remember { mutableStateOf(false) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }

    // Gesture states for seek, brightness, and volume
    var currentDragMode by remember { mutableStateOf(DragGestureMode.NONE) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    // Brightness gesture state
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var appliedBrightness by remember {
        mutableFloatStateOf(getScreenBrightness(context, activity))
    }
    var dragStartBrightness by remember { mutableFloatStateOf(0.5f) }
    var hideBrightnessJob by remember { mutableStateOf<Job?>(null) }

    // Volume gesture state
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var appliedVolume by remember {
        mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }
    var dragStartVolume by remember { mutableIntStateOf(0) }
    var volumeMin by remember {
        mutableIntStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } else {
                0
            }
        )
    }
    var volumeMax by remember {
        mutableIntStateOf(audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15)
    }
    var hideVolumeJob by remember { mutableStateOf<Job?>(null) }

    // Long-press 2x state (issue #166): while the user holds the video
    // area, playback runs at 2x; releasing restores the previous speed.
    var isLongPress2x by remember { mutableStateOf(false) }

    // Pinch-to-zoom state (issue #172): two fingers on the video area
    // zoom the picture. The zoom is applied live to mpv and saved to
    // settings when the gesture ends, so it survives the next video.
    var showZoomIndicator by remember { mutableStateOf(false) }
    var zoomDisplayText by remember { mutableStateOf("") }
    var hideZoomJob by remember { mutableStateOf<Job?>(null) }
    var isPinching by remember { mutableStateOf(false) }
    var pinchStartDistance by remember { mutableFloatStateOf(0f) }
    var pinchStartZoom by remember { mutableFloatStateOf(1f) }
    var pinchCurrentZoom by remember { mutableFloatStateOf(1f) }

    // Double-tap side seek gesture state
    var sideSeekDirection by remember { mutableStateOf<SideSeekDirection?>(null) }
    var hideSideSeekJob by remember { mutableStateOf<Job?>(null) }

    // Issue #328: the stripe's scroll state, owned here so the auto-hide
    // timer below can see it. LazyListState.isScrollInProgress is true
    // for the whole drag AND the fling until it settles, so keying the
    // timer on it keeps the chrome up for the entire scroll.
    val stripeListState = rememberLazyListState()

    // MX-style auto-hide (issue #85): MX's bottom controller in screen.xml
    // is visibility=gone by default and only appears on interaction. Mirror
    // that here: while playing, hide the chrome a few seconds after it
    // appears, so the video is not permanently covered. Stay visible while
    // paused, while scrubbing/dragging, while scrolling the action stripe
    // (#328), or while a dialog is open, so no interaction is ever cut off
    // mid-use. Tap the video to bring it back.
    LaunchedEffect(controlsVisible, state.isPlaying, playbackSheetPage, isScrubbingSlider, isDraggingSeek, showBrightnessIndicator, showVolumeIndicator, showZoomIndicator, stripeListState.isScrollInProgress) {
        if (controlsVisible && state.isPlaying && playbackSheetPage == null && !isScrubbingSlider && !isDraggingSeek && !showBrightnessIndicator && !showVolumeIndicator && !showZoomIndicator && !stripeListState.isScrollInProgress) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Issue #195: hide the system bars with the player chrome. The status
    // bar never disappeared during playback — the player hid its own chrome
    // but never told the window to hide the bars. Tied to controlsVisible,
    // so the bars hide when the chrome auto-hides (or the user taps it
    // away) and come back with the chrome. Scoped to the Player route
    // only: edge-to-edge stays off for the rest of the app (M2.5
    // decision). WindowInsetsControllerCompat needs no
    // decorFitsSystemWindows change — hiding the bars lets the window
    // expand to full screen, which resizes the SurfaceView (1080x2356
    // <-> 1080x1934). That resize runs through surfaceChanged, where the
    // #193 restore covers the vo side, so it cannot re-trigger the
    // black-video bug. Not hidden in PiP: the PiP window has no system
    // bars to hide. BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE lets the user
    // swipe the bars back temporarily; onDispose restores them, so
    // leaving the player always brings them back.
    DisposableEffect(controlsVisible, isInPipMode, activity) {
        val window = activity?.window
        if (window != null && !controlsVisible && !isInPipMode) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val currentDuration = state.durationMs.coerceAtLeast(0L)
    val displayedCurrentTime = if (isScrubbingSlider && currentDuration > 0) {
        (scrubFraction * currentDuration).toLong()
    } else {
        state.currentPositionMs
    }

    val sliderProgress = if (isScrubbingSlider) {
        scrubFraction
    } else if (currentDuration > 0) {
        (state.currentPositionMs.toFloat() / currentDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Issue #259: resume playback paused for a seek drag, if it was
    // playing when the drag started. Called on every exit path of a
    // seek drag (clean release, cancellation, pinch takeover) so a
    // paused-for-seek video can never get stuck paused. A no-op when
    // the drag never paused anything (already-paused video, or a
    // vertical drag that never became a seek).
    fun resumeAfterSeekDrag() {
        if (wasPlayingBeforeSeekDrag) {
            wasPlayingBeforeSeekDrag = false
            playbackManager.play()
        }
    }

    // Shared drag-delta handling for the single gesture detector below
    // (issue #166): mode detection (horizontal seek vs left-vertical
    // brightness vs right-vertical volume) plus the per-mode delta math.
    // Kept in one place so the touch-slop change and every later drag
    // change go through exactly the same path. scopeWidth/scopeHeight are
    // the PointerInputScope size, passed in because this runs outside it.
    fun applyDragDelta(dragAmount: Offset, scopeWidth: Float, scopeHeight: Float) {
        totalDragX += dragAmount.x
        totalDragY += dragAmount.y

        if (currentDragMode == DragGestureMode.NONE) {
            val absX = kotlin.math.abs(totalDragX)
            val absY = kotlin.math.abs(totalDragY)
            if (absX > 0f || absY > 0f) {
                if (absX >= absY) {
                    if (currentDuration > 0) {
                        currentDragMode = DragGestureMode.HORIZONTAL_SEEK
                        isDraggingSeek = true
                        // Issue #259: pause while the user drag-seeks.
                        // The anchor was captured at finger-down and
                        // playback keeps advancing under it, so a slow
                        // drag released seconds later seeks back by the
                        // drag duration instead of landing where the HUD
                        // showed. Pausing freezes the position, and
                        // re-anchoring here — at the moment the drag is
                        // recognised as a seek — drops any hold time
                        // before the finger started moving. Only pause
                        // when actually playing; a drag on a paused
                        // video seeks without touching playback, and
                        // resumeAfterSeekDrag restores it on release.
                        if (state.isPlaying) {
                            wasPlayingBeforeSeekDrag = true
                            playbackManager.pause()
                        }
                        seekStartMs = state.currentPositionMs
                    }
                } else {
                    val isLeftHalf = dragStartOffset.x < scopeWidth / 2f
                    if (isLeftHalf) {
                        currentDragMode = DragGestureMode.VERTICAL_BRIGHTNESS
                        hideBrightnessJob?.cancel()
                        showBrightnessIndicator = true
                    } else {
                        currentDragMode = DragGestureMode.VERTICAL_VOLUME
                        hideVolumeJob?.cancel()
                        showVolumeIndicator = true
                    }
                }
            }
        }

        when (currentDragMode) {
            DragGestureMode.HORIZONTAL_SEEK -> {
                val minTotalX = -(seekStartMs.toFloat() / 120f)
                val maxTotalX = ((currentDuration - seekStartMs).toFloat() / 120f)
                totalDragX = totalDragX.coerceIn(minTotalX, maxTotalX)
                val delta = (totalDragX * 120f).toLong()
                val target = (seekStartMs + delta).coerceIn(0L, currentDuration)
                seekTargetMs = target
                seekDeltaMs = target - seekStartMs
            }
            DragGestureMode.VERTICAL_BRIGHTNESS -> {
                val sensitivity = (scopeHeight * 0.75f).coerceAtLeast(200f)
                totalDragY = totalDragY.coerceIn(
                    -sensitivity * (1.0f - dragStartBrightness),
                    sensitivity * dragStartBrightness,
                )
                val deltaFraction = -totalDragY / sensitivity
                val target = (dragStartBrightness + deltaFraction).coerceIn(0f, 1f)
                appliedBrightness = setScreenBrightness(activity, target)
                hideBrightnessJob?.cancel()
                showBrightnessIndicator = true
            }
            DragGestureMode.VERTICAL_VOLUME -> {
                val sensitivity = (scopeHeight * 0.75f).coerceAtLeast(200f)
                val volumeRange = (volumeMax - volumeMin).coerceAtLeast(1)
                val minTotalY = -sensitivity * ((volumeMax - dragStartVolume).toFloat() / volumeRange.toFloat())
                val maxTotalY = sensitivity * ((dragStartVolume - volumeMin).toFloat() / volumeRange.toFloat())
                totalDragY = totalDragY.coerceIn(minTotalY, maxTotalY)
                val deltaSteps = (-totalDragY / sensitivity * volumeRange).toInt()
                val target = (dragStartVolume + deltaSteps).coerceIn(volumeMin, volumeMax)
                appliedVolume = setMediaVolume(audioManager, target, volumeMin, volumeMax)
                hideVolumeJob?.cancel()
                showVolumeIndicator = true
            }
            DragGestureMode.NONE -> {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MpxTheme.colors.black)
            // One gesture detector for the whole video area (issue #166):
            // tap, double-tap, long-press 2x and drags share a single
            // pointerInput block. Two separate blocks (detectTapGestures +
            // detectDragGestures) cannot coexist on the same Box:
            // detectTapGestures consumes the initial down (down.consume()),
            // while detectDragGestures only accepts an UNCONSUMED down
            // (awaitFirstDown(requireUnconsumed = true)), so the two
            // detectors race for the same down — and on this screen the tap
            // detector wins, which is why no drag ever started and the
            // brightness/volume/seek previews never showed even though the
            // HUD code itself was correct. A single awaitEachGesture loop
            // removes the race: one down, one decision (tap / double-tap /
            // long-press 2x / drag).
            .pointerInput(state.isLocked, currentDuration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    dragStartOffset = down.position
                    // Per-gesture reset (was detectDragGestures' onDragStart).
                    hideSideSeekJob?.cancel()
                    sideSeekDirection = null
                    currentDragMode = DragGestureMode.NONE
                    totalDragX = 0f
                    totalDragY = 0f
                    accumulatedDragX = 0f
                    seekStartMs = state.currentPositionMs
                    seekTargetMs = seekStartMs
                    seekDeltaMs = 0L

                    if (state.isLocked) {
                        // Locked: only a tap may toggle the chrome.
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            controlsVisible = !controlsVisible
                            if (sideSeekDirection != null) {
                                hideSideSeekJob?.cancel()
                                sideSeekDirection = null
                            }
                        }
                        return@awaitEachGesture
                    }

                    // Race the four outcomes: touch slop (drag), second
                    // pointer (pinch from a standstill, #214), finger up
                    // (tap / double-tap), long-press timeout (hold for 2x).
                    // awaitTouchSlopOrCancellation tracked only the first
                    // finger, so a stationary two-finger spread never
                    // passed slop and the timeout fired long-press 2x
                    // instead of zooming. Watch the pointer count in an
                    // awaitPointerEvent loop instead, and hand a second
                    // pointer straight to the pinch path below before the
                    // timeout can fire. The loop ends with slopChange set
                    // (drag), pinchFromStandstill set (pinch), or neither
                    // (up or timeout — told apart below by whether the
                    // pointer is still down in the latest event).
                    var slopChange: PointerInputChange? = null
                    var pinchFromStandstill = false
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2 && realSettingsRepo.load().gesturesEnabled) {
                                // Second finger down before slop and before
                                // the long-press timeout: enter pinch mode
                                // now, using this event's finger distance as
                                // the baseline. The drag loop below sees
                                // isPinching and zooms from here instead of
                                // re-initialising; a two-finger tap (no
                                // spread) ends in the pinch-over branch with
                                // the zoom unchanged and no 2x fired.
                                pinchFromStandstill = true
                                pinchStartDistance = (pressed[0].position - pressed[1].position).getDistance()
                                pinchStartZoom = currentSettings.videoZoom
                                pinchCurrentZoom = pinchStartZoom
                                isPinching = true
                                hideZoomJob?.cancel()
                                showZoomIndicator = true
                                zoomDisplayText = formatMultiplier(pinchStartZoom)
                                pressed.forEach { it.consume() }
                                break
                            }
                            val first = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!first.pressed && first.previousPressed) {
                                // Finger lifted: tap / double-tap path below.
                                break
                            }
                            if ((first.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                first.consume()
                                slopChange = first
                                break
                            }
                        }
                    }
                    if (slopChange == null && !pinchFromStandstill) {
                        val stillDown = currentEvent.changes.any { it.id == down.id && it.pressed }
                        if (stillDown && realSettingsRepo.load().gesturesEnabled) {
                            // Long-press 2x: hold at 2x until release, then
                            // restore the speed that was playing before.
                            // The finally also covers the detector restarting
                            // mid-hold, so 2x can never get stuck on.
                            // Issue #289: restore only if the same track is
                            // still current — if the track ended and
                            // auto-advanced mid-hold, startPlayback already
                            // reset the new track to defaultPlaybackSpeed,
                            // and restoring the old track's speed here would
                            // overwrite it. The URI is the stable item
                            // identity (issue #227).
                            val previousSpeed = state.speed
                            val heldItemUri = state.currentItem?.uri
                            playbackManager.setSpeed(2f)
                            isLongPress2x = true
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                if (state.currentItem?.uri == heldItemUri) {
                                    playbackManager.setSpeed(previousSpeed)
                                }
                                isLongPress2x = false
                            }
                        } else {
                            // Tap candidate: wait briefly for a second tap.
                            val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                awaitFirstDown(requireUnconsumed = true)
                            }
                            if (secondDown == null) {
                                controlsVisible = !controlsVisible
                                if (sideSeekDirection != null) {
                                    hideSideSeekJob?.cancel()
                                    sideSeekDirection = null
                                }
                            } else {
                                waitForUpOrCancellation()
                                if (realSettingsRepo.load().gesturesEnabled) {
                                    val oneThird = size.width / 3f
                                    val twoThirds = size.width * (2f / 3f)
                                    when {
                                        secondDown.position.x < oneThird -> {
                                            playbackManager.skipBackward(realSettingsRepo.load().doubleTapSeekSeconds * 1000L)
                                            sideSeekDirection = SideSeekDirection.BACKWARD
                                            hideBrightnessJob?.cancel()
                                            showBrightnessIndicator = false
                                            hideVolumeJob?.cancel()
                                            showVolumeIndicator = false
                                            hideSideSeekJob?.cancel()
                                            hideSideSeekJob = scope.launch {
                                                delay(1000)
                                                sideSeekDirection = null
                                            }
                                        }
                                        secondDown.position.x > twoThirds -> {
                                            playbackManager.skipForward(realSettingsRepo.load().doubleTapSeekSeconds * 1000L)
                                            sideSeekDirection = SideSeekDirection.FORWARD
                                            hideBrightnessJob?.cancel()
                                            showBrightnessIndicator = false
                                            hideVolumeJob?.cancel()
                                            showVolumeIndicator = false
                                            hideSideSeekJob?.cancel()
                                            hideSideSeekJob = scope.launch {
                                                delay(1000)
                                                sideSeekDirection = null
                                            }
                                        }
                                        else -> {
                                            playbackManager.togglePlayPause()
                                            if (sideSeekDirection != null) {
                                                hideSideSeekJob?.cancel()
                                                sideSeekDirection = null
                                            }
                                        }
                                    }
                                } else {
                                    // Issue #288: gestures off — the first
                                    // tap's chrome toggle is still pending
                                    // (it only fires when no second tap
                                    // arrives), so a double-tap must toggle
                                    // the chrome once instead of swallowing
                                    // it. Fires exactly once: the deferred
                                    // toggle above never ran for this pair.
                                    controlsVisible = !controlsVisible
                                    if (sideSeekDirection != null) {
                                        hideSideSeekJob?.cancel()
                                        sideSeekDirection = null
                                    }
                                }
                            }
                        }
                        return@awaitEachGesture
                    }

                    // Drag: capture the drag-start values (was onDragStart),
                    // then feed the slop change and every later move through
                    // the shared delta path.
                    dragStartBrightness = getScreenBrightness(context, activity)
                    dragStartVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                    volumeMin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
                        audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                    } else {
                        0
                    }
                    volumeMax = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    if (!realSettingsRepo.load().gesturesEnabled) {
                        // Gestures off: ignore the drag entirely. (Before,
                        // the unconsumed moves fell through to the tap
                        // detector and a swipe toggled the chrome.)
                        return@awaitEachGesture
                    }
                    // A pinch from a standstill has no slop change to feed
                    // in — the drag loop below owns the gesture from here.
                    if (slopChange != null) {
                        applyDragDelta(slopChange.position - slopChange.previousPosition, size.width.toFloat(), size.height.toFloat())
                    }
                    // Stream the drag: awaitDragOrCancellation takes no delta
                    // callback, so loop on awaitPointerEvent until the finger
                    // lifts or the gesture is cancelled, feeding every move
                    // through the shared delta path.
                    var endedCleanly = false
                    var dragLoopDone = false
                    while (!dragLoopDone) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // Pinch-to-zoom (#172): a second finger joined
                            // the drag. Stop any single-finger drag in its
                            // tracks (a mid-seek drag is abandoned, not
                            // committed) and zoom from the finger distance.
                            if (!isPinching) {
                                if (isDraggingSeek) {
                                    isDraggingSeek = false
                                    // Issue #259: the abandoned seek drag
                                    // had paused playback — resume it, the
                                    // pinch must not leave the video paused.
                                    resumeAfterSeekDrag()
                                }
                                currentDragMode = DragGestureMode.NONE
                                pinchStartDistance = (pressed[0].position - pressed[1].position).getDistance()
                                pinchStartZoom = currentSettings.videoZoom
                                pinchCurrentZoom = pinchStartZoom
                                isPinching = true
                                hideZoomJob?.cancel()
                                showZoomIndicator = true
                                zoomDisplayText = formatMultiplier(pinchStartZoom)
                            } else {
                                val distance = (pressed[0].position - pressed[1].position).getDistance()
                                if (pinchStartDistance > 0f && distance > 0f) {
                                    val newZoom = (pinchStartZoom * (distance / pinchStartDistance)).coerceIn(0.5f, 2.0f)
                                    if (newZoom != pinchCurrentZoom) {
                                        pinchCurrentZoom = newZoom
                                        playbackManager.setVideoZoom(newZoom)
                                        zoomDisplayText = formatMultiplier(newZoom)
                                    }
                                }
                            }
                            // The pinch owns both pointers for this event.
                            pressed.forEach { it.consume() }
                            continue
                        }
                        if (isPinching) {
                            // Pinch over (a finger lifted): save the final
                            // zoom so it survives the next video, then end
                            // this gesture — the remaining finger does not
                            // resume a single-finger drag.
                            isPinching = false
                            val updated = currentSettings.copy(videoZoom = pinchCurrentZoom)
                            currentSettings = updated
                            realSettingsRepo.save(updated)
                            hideZoomJob?.cancel()
                            hideZoomJob = scope.launch {
                                delay(1000)
                                showZoomIndicator = false
                            }
                            dragLoopDone = true
                            continue
                        }
                        val dragChange = event.changes.firstOrNull { it.id == down.id }
                        if (dragChange == null) {
                            dragLoopDone = true
                        } else if (!dragChange.pressed && dragChange.previousPressed) {
                            // Finger lifted: the drag ended cleanly.
                            endedCleanly = true
                            dragLoopDone = true
                        } else {
                            val delta = dragChange.position - dragChange.previousPosition
                            if (delta != Offset.Zero) {
                                dragChange.consume()
                                applyDragDelta(delta, size.width.toFloat(), size.height.toFloat())
                            }
                        }
                    }
                    if (endedCleanly) {
                        when (currentDragMode) {
                            DragGestureMode.HORIZONTAL_SEEK -> {
                                if (isDraggingSeek) {
                                    playbackManager.seekTo(seekTargetMs)
                                    isDraggingSeek = false
                                    // Issue #259: seek first, then resume —
                                    // play() must see the post-seek state, and
                                    // the video was playing before the drag.
                                    resumeAfterSeekDrag()
                                }
                            }
                            DragGestureMode.VERTICAL_BRIGHTNESS -> {
                                hideBrightnessJob?.cancel()
                                hideBrightnessJob = scope.launch {
                                    delay(1000)
                                    showBrightnessIndicator = false
                                }
                            }
                            DragGestureMode.VERTICAL_VOLUME -> {
                                hideVolumeJob?.cancel()
                                hideVolumeJob = scope.launch {
                                    delay(1000)
                                    showVolumeIndicator = false
                                }
                            }
                            DragGestureMode.NONE -> {}
                        }
                    } else {
                        if (isDraggingSeek) {
                            isDraggingSeek = false
                            // Issue #259: the drag was cancelled, so no
                            // seek — but restore playback if the drag had
                            // paused it, the user never asked to pause.
                            resumeAfterSeekDrag()
                        }
                        if (showBrightnessIndicator) {
                            hideBrightnessJob?.cancel()
                            hideBrightnessJob = scope.launch {
                                delay(1000)
                                showBrightnessIndicator = false
                            }
                        }
                        if (showVolumeIndicator) {
                            hideVolumeJob?.cancel()
                            hideVolumeJob = scope.launch {
                                delay(1000)
                                showVolumeIndicator = false
                            }
                        }
                    }
                    currentDragMode = DragGestureMode.NONE
                }
            }
    ) {
        // Video Surface: mpv renders into a plain SurfaceView (issue #150:
        // Media3 PlayerView is gone with Media3).
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            playbackManager.attachSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            playbackManager.surfaceChanged(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // SYNCHRONOUS barrier:
                            // Set vo=null and detach before surfaceDestroyed returns.
                            playbackManager.detachSurface()
                        }
                    })
                }
            },
            update = {
                // Push subtitle style changes to a running mpv engine (issue #75),
                // plus audio delay + video zoom (issue #153).
                if (lastMpvSubtitleSettings[0] != currentSettings) {
                    playbackManager.applySubtitleSettings(currentSettings)
                    playbackManager.applyPlaybackTweaks(currentSettings)
                    lastMpvSubtitleSettings[0] = currentSettings
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Empty-player state when opened without a queued video (issue #57)
        // Issue #293: hidden in PiP like every other overlay — if the
        // player is empty while in PiP, the window must not show this
        // placeholder column.
        val currentMedia = state.currentItem
        if (currentMedia == null) {
            if (!isInPipMode) Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MpxTheme.colors.textPrimary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MpxTheme.colors.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.player_empty_no_video),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MpxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.player_empty_select_video),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MpxTheme.colors.textSecondary,
                )
            }
        } else if (!currentMedia.isVideo) {
            // Audio-only mode visual representation
            // Issue #293: hidden in PiP like every other overlay — if the
            // queue advances to an audio item while in PiP, the window
            // must not show a music-note placeholder.
            if (!isInPipMode) Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MpxTheme.colors.primary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = stringResource(R.string.player_cd_audio_track),
                        tint = MpxTheme.colors.primary,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = currentMedia.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentMedia.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MpxTheme.colors.textSecondary,
                )
            }
        }

        // MX-style loading splash (screen.xml loadingSplash/loading): while a
        // video is preparing, show a spinner + text over black instead of dead
        // 00:00 chrome. The full controls overlay stays hidden until READY.
        if (state.isPreparing && currentMedia?.isVideo == true) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MpxLoadingIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.player_loading),
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 14.sp,
                )
            }
        }

        // Horizontal Scrubbing HUD — compact gradient pill, no heavy box
        if (!isInPipMode && isDraggingSeek) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MpxShapes.large)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MpxTheme.colors.black.copy(alpha = 0.45f),
                                MpxTheme.colors.black.copy(alpha = 0.65f),
                            ),
                        ),
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val sign = if (seekDeltaMs >= 0) "+" else "-"
                    val absSeconds = (kotlin.math.abs(seekDeltaMs) / 1000).toInt()
                    val deltaStr = String.format(Locale.US, "%s%02d:%02d", sign, absSeconds / 60, absSeconds % 60)

                    Text(
                        text = stringResource(R.string.player_hud_scrub, deltaStr),
                        color = MpxTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatTimeMs(seekTargetMs)} / ${formatTimeMs(currentDuration)}",
                        color = MpxTheme.colors.textPrimary.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Brightness HUD (Left half vertical drag) — slim MX-brightbar strip.
        // Shown on the RIGHT, opposite the dragging hand, so the finger never
        // covers the preview (issue #166). The gesture side itself is unchanged.
        if (!isInPipMode && showBrightnessIndicator) {
            val brightnessPercent = (appliedBrightness * 100).roundToInt().coerceIn(0, 100)
            val icon = when {
                brightnessPercent < 33 -> Icons.Filled.BrightnessLow
                brightnessPercent < 67 -> Icons.Filled.BrightnessMedium
                else -> Icons.Filled.BrightnessHigh
            }
            GestureHud(
                percentText = "$brightnessPercent%",
                fraction = appliedBrightness,
                icon = icon,
                iconDescription = stringResource(R.string.player_cd_brightness),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp),
            )
        }

        // Volume HUD (Right half vertical drag) — slim MX-brightbar strip.
        // Shown on the LEFT, opposite the dragging hand (issue #166).
        if (!isInPipMode && showVolumeIndicator) {
            val volumeRange = (volumeMax - volumeMin).coerceAtLeast(1)
            val volumePercent = (((appliedVolume - volumeMin).toFloat() / volumeRange.toFloat()) * 100).roundToInt().coerceIn(0, 100)
            val icon = when {
                appliedVolume <= volumeMin -> Icons.AutoMirrored.Filled.VolumeMute
                volumePercent < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            GestureHud(
                percentText = "$volumePercent%",
                fraction = (appliedVolume - volumeMin).toFloat() / volumeRange.toFloat(),
                icon = icon,
                iconDescription = stringResource(R.string.player_cd_volume),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp),
            )
        }

        // Long-press 2x indicator — small centered pill while the hold
        // lasts (issue #166). Same gradient-pill language as the scrub HUD.
        if (!isInPipMode && isLongPress2x) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MpxShapes.large)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MpxTheme.colors.black.copy(alpha = 0.45f),
                                MpxTheme.colors.black.copy(alpha = 0.65f),
                            ),
                        ),
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "2×",
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
        }

        // Pinch-zoom indicator — small centered pill while pinching and
        // for a second after release (issue #172). Same gradient-pill
        // language as the 2x pill above it.
        if (!isInPipMode && showZoomIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MpxShapes.large)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MpxTheme.colors.black.copy(alpha = 0.45f),
                                MpxTheme.colors.black.copy(alpha = 0.65f),
                            ),
                        ),
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = zoomDisplayText,
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
        }

        // Side Seek HUD (double-tap left/right seek feedback — the seconds
        // follow the doubleTapSeekSeconds setting, #237, same source as
        // the seek itself) — same slim gradient-strip language as the
        // volume/brightness HUDs
        if (!isInPipMode) sideSeekDirection?.let { direction ->
            val isBackward = direction == SideSeekDirection.BACKWARD
            // Issue #237: read the setting fresh here, as the seek does in
            // the gesture handler — the HUD composes right after the tap,
            // so this is the value that was just jumped by. (currentSettings
            // is a snapshot that only updates on some actions; see #241.)
            val seekSeconds = realSettingsRepo.load().doubleTapSeekSeconds
            Box(
                modifier = Modifier
                    .align(if (isBackward) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(start = if (isBackward) 32.dp else 0.dp, end = if (!isBackward) 32.dp else 0.dp)
                    .clip(MpxShapes.large)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MpxTheme.colors.black.copy(alpha = 0.45f),
                                MpxTheme.colors.black.copy(alpha = 0.65f),
                            ),
                        ),
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (isBackward) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                        contentDescription = if (isBackward) stringResource(R.string.player_cd_seek_back) else stringResource(R.string.player_cd_seek_forward),
                        tint = MpxTheme.colors.textPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isBackward) "-${seekSeconds}s" else "+${seekSeconds}s",
                        color = MpxTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (currentDuration > 0) {
                            "${formatTimeMs(state.currentPositionMs)} / ${formatTimeMs(currentDuration)}"
                        } else {
                            formatTimeMs(state.currentPositionMs)
                        },
                        color = MpxTheme.colors.textPrimary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Banner stack — resume / decoder-fallback / user notification.
        // One flowing Column anchored below the top bar, so a banner
        // whose text wraps to 2-3 lines can never overlap the banner
        // below it (#263). The old hardcoded top offsets (72/124/160dp)
        // assumed ~52dp slots; the fallback banner is at least 48dp
        // tall and taller when its note wraps, so the notification
        // banner overlapped it whenever both were visible — and whenever
        // all three were, even unwrapped.
        if (!isInPipMode && (state.resumedFromMs != null || state.decoderFallbackNote != null || state.userNotification != null)) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Issue #327: banners start below the top chrome now
                    // that the action stripe lives under the top bar
                    // (top bar 64dp + 8dp gap + ~48dp stripe + 8dp gap).
                    .padding(top = 128.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // In-memory Resume Notice Banner (theme surface, no heavy box)
            state.resumedFromMs?.let { resumeMs ->
                Surface(
                    color = MpxTheme.colors.surfaceVariant,
                    shape = MpxShapes.extraLarge,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_resumed_from, formatTimeMs(resumeMs)),
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.player_start_over),
                            color = MpxTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                playbackManager.seekTo(0L)
                                playbackManager.clearResumedNotification()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { playbackManager.clearResumedNotification() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.player_cd_dismiss),
                                tint = MpxTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Platform Decoder Fallback Notice Banner (theme surface, no orange box)
            state.decoderFallbackNote?.let { fallbackMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.surfaceVariant,
                    shape = MpxShapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = fallbackMsg,
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { playbackManager.clearDecoderFallbackNote() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.player_cd_dismiss),
                                tint = MpxTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Honest User Notification Banner (theme surface, compact)
            state.userNotification?.let { userMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.surfaceVariant,
                    shape = MpxShapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = userMsg,
                            color = MpxTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { playbackManager.clearUserNotification() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.player_cd_dismiss),
                                tint = MpxTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            }
        }

        // Single floating unlock button when screen is locked
        // (hidden in PiP: the window shows video only)
        if (!isInPipMode && state.isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp),
            ) {
                IconButton(
                    onClick = { playbackManager.setLocked(false) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MpxTheme.colors.black.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.player_cd_unlock),
                        tint = MpxTheme.colors.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            return@Box
        }

        // Full Controls Overlay (hidden in PiP: the window shows video only;
        // hidden while preparing: MX's controller is gone while its loading
        // splash shows).
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode && !state.isPreparing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Overlay Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(MpxTheme.colors.black.copy(alpha = 0.75f), Color.Transparent),
                            ),
                        )
                        .padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 24.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.player_cd_back),
                                    tint = MpxTheme.colors.textPrimary,
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            Text(
                                text = state.currentItem?.title ?: stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                ),
                                color = MpxTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            Spacer(Modifier.width(8.dp))

                            // Decoder Pill Badge: configured mode + real active codec
                            // (e.g. "HW • H.264") once the engine reports a decoder name;
                            // falls back to the mode label alone while initializing.
                            // Issue #188: read the short codec label from the
                            // shared helper instead of parsing the display name —
                            // the old substring fallback mangled unknown names
                            // ("software (unknown)" -> "HW • software (un…").
                            val decoderPillText = state.activeDecoderName?.let { name ->
                                val codec = MpxMediaCodecSelector.shortCodecLabel(name)
                                if (codec != null) "${state.decoderMode.label} • $codec" else state.decoderMode.label
                            } ?: state.decoderMode.label
                            // Issue #293: the decoder pill is video-only —
                            // for audio-only or empty-player states it would
                            // cycle HW/HW+/SW and toast a decoder change
                            // that affects nothing, so it is not shown. (It
                            // is already inside the controls overlay, which
                            // is hidden in PiP.)
                            if (state.currentItem?.isVideo == true) Surface(
                                modifier = Modifier
                                    .clip(MpxShapes.small)
                                    .clickable {
                                        val nextDecoder = when (state.decoderMode) {
                                            DecoderMode.HW -> DecoderMode.HW_PLUS
                                            DecoderMode.HW_PLUS -> DecoderMode.SW
                                            DecoderMode.SW -> DecoderMode.HW
                                        }
                                        playbackManager.setDecoderMode(nextDecoder)
                                        realSettingsRepo.save(realSettingsRepo.load().copy(decoderMode = nextDecoder.label))
                                        currentSettings = realSettingsRepo.load()
                                        Toast.makeText(context, context.getString(R.string.player_toast_decoder, context.getString(nextDecoder.titleRes)), Toast.LENGTH_SHORT).show()
                                    },
                                color = Color.Transparent,
                                shape = MpxShapes.small,
                            ) {
                                Text(
                                    text = decoderPillText,
                                    color = MpxTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }

                            // (The aspect control moved to the Player Action Stripe
                            // (#177) at the top of the screen — tap cycles, same as this
                            // pill did; the full radio list stays in the sheet's
                            // Aspect page. Favourite, audio tracks, subtitles, PiP
                            // and rotation all live in the Playback sheet now,
                            // MX-overflow style, so the top bar is back + title +
                            // decoder pill + menu. See PlayerPlaybackSheet.)
                            IconButton(onClick = { playbackSheetPage = PlaybackSheetPage.Main }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.player_cd_more),
                                    tint = MpxTheme.colors.textPrimary,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Player Action Stripe (issue #177, moved to the
                        // top in #327): the user's chosen actions from
                        // the Player Layout editor, in their order, as a
                        // horizontally scrollable strip under the top
                        // bar. Hides with the chrome.
                        PlayerActionStripe(
                            listState = stripeListState,
                            state = state,
                            currentSettings = currentSettings,
                            isFavourite = isFavourite,
                            onOpenSheet = { playbackSheetPage = it },
                            onToggleFavourite = toggleFavourite,
                            onEnterPip = enterPip,
                            onToggleBackgroundPlay = toggleBackgroundPlay,
                            onAddToPlaylist = { showAddToPlaylistDialog = true },
                            playbackManager = playbackManager,
                        )
                    }
                }

                // (Screen lock lives in the transport row now, like MX's
                // subnavibar lock, instead of floating mid-video.)
                // Bottom Overlay Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MpxTheme.colors.black.copy(alpha = 0.8f)),
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Time & Slider Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTimeMs(displayedCurrentTime),
                                color = MpxTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            Slider(
                                value = sliderProgress,
                                onValueChange = {
                                    isScrubbingSlider = true
                                    scrubFraction = it
                                },
                                onValueChangeFinished = {
                                    val target = (scrubFraction * currentDuration).toLong()
                                    playbackManager.seekTo(target)
                                    isScrubbingSlider = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MpxTheme.colors.primary,
                                    activeTrackColor = MpxTheme.colors.primary,
                                    inactiveTrackColor = MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                                ),
                            )

                            Text(
                                text = formatTimeMs(currentDuration),
                                color = MpxTheme.colors.textPrimary.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Controls Row: Lock, Prev, 10s Replay, Play/Pause Hero,
                        // 10s Forward, Next — MX's subnavibar transport set.
                        // Speed lives in the Playback sheet now (aspect is in
                        // the action stripe at the top); shuffle and
                        // repeat moved to the stripe too.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Screen lock, in-row instead of floating mid-video.
                            // MX-faithful transparent button (issue #94): no
                            // glass fill, no border — white icon on the scrim.
                            IconButton(
                                onClick = { playbackManager.setLocked(true) },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = stringResource(R.string.player_cd_lock),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Previous Track
                            IconButton(
                                onClick = { playbackManager.skipPrevious() },
                                enabled = state.hasPrevious,
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_cd_previous),
                                    tint = if (state.hasPrevious) MpxTheme.colors.textPrimary else MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Replay 10s
                            IconButton(
                                onClick = { playbackManager.skipBackward(10_000L) },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = stringResource(R.string.player_cd_seek_back),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Hero Play/Pause Button
                            // MX-faithful: transparent play/pause, larger white
                            // icon instead of a filled hero circle.
                            IconButton(
                                onClick = { playbackManager.togglePlayPause() },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) stringResource(R.string.player_cd_pause) else stringResource(R.string.player_cd_play),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(34.dp),
                                )
                            }

                            // Forward 10s
                            IconButton(
                                onClick = { playbackManager.skipForward(10_000L) },
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = stringResource(R.string.player_cd_seek_forward),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            // Next Track
                            IconButton(
                                onClick = { playbackManager.skipNext() },
                                enabled = state.hasNext,
                                modifier = Modifier
                                    .size(50.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.player_cd_next),
                                    tint = if (state.hasNext) MpxTheme.colors.textPrimary else MpxTheme.colors.textPrimary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                        }
                        // (Speed lives in the Playback sheet now.)

                        // Sleep Timer active chip near controls
                        if (state.sleepTimerRemainingSeconds != null) {
                            val remaining = state.sleepTimerRemainingSeconds ?: 0L
                            val hours = remaining / 3600
                            val mins = (remaining % 3600) / 60
                            val secs = remaining % 60
                            val timeFormatted = if (hours > 0) {
                                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs)
                            } else {
                                String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .clip(MpxShapes.medium)
                                        .clickable { playbackSheetPage = PlaybackSheetPage.SleepTimer },
                                    color = MpxTheme.colors.primaryVariant.copy(alpha = 0.9f),
                                    shape = MpxShapes.medium,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Timer,
                                            contentDescription = null,
                                            tint = MpxTheme.colors.textPrimary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.player_sleep_timer_chip, timeFormatted),
                                            color = MpxTheme.colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        // Issue #286: the cancel X needs a
                                        // real touch target — it was a bare
                                        // 14dp icon, so taps just outside it
                                        // hit the chip and opened the Sleep
                                        // sheet instead of cancelling. The
                                        // Box keeps the icon visually 14dp
                                        // but pads the clickable out to
                                        // 48dp wide x the chip's full
                                        // content height (22dp — a true
                                        // 48dp-tall target would need a
                                        // 48dp-tall chip). clickable comes
                                        // before padding so the hit area
                                        // includes the padding. The Box is
                                        // nested inside the Surface's
                                        // clickable, so taps inside it are
                                        // consumed here and never open the
                                        // sheet.
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .clickable {
                                                    playbackManager.cancelSleepTimer()
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.player_sleep_timer_canceled_toast),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                                .padding(horizontal = 17.dp, vertical = 4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.player_sleep_timer_cancel),
                                                tint = MpxTheme.colors.textPrimary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Unsupported Subtitle Format Dialog
    if (subtitleErrorDialogText != null) {
        AlertDialog(
            onDismissRequest = { subtitleErrorDialogText = null },
            containerColor = MpxTheme.colors.black,
            tonalElevation = 0.dp,
            modifier = Modifier.border(MpxHairline, MpxTheme.colors.border, AlertDialogDefaults.shape),
            title = {
                Text(
                    text = stringResource(R.string.player_dialog_unsupported_subtitle),
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = subtitleErrorDialogText!!,
                    color = MpxTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { subtitleErrorDialogText = null }) {
                    Text(stringResource(R.string.action_ok), color = MpxTheme.colors.primary)
                }
            },
        )
    }

    // Playback sheet (issue #153): one bottom sheet replacing the Player Menu
    // dialog and the whole activeDialog chain. Always dark (issue #152).
    playbackSheetPage?.let { sheetPage ->
        PlayerPlaybackSheet(
            page = sheetPage,
            onPageChange = { playbackSheetPage = it },
            state = state,
            currentSettings = currentSettings,
            onSettingsChanged = { updated ->
                currentSettings = updated
                realSettingsRepo.save(updated)
            },
            playbackManager = playbackManager,
            subtitleLauncher = subtitleLauncher,
            isFavourite = isFavourite,
            onToggleFavourite = toggleFavourite,
            onEnterPip = enterPip,
            onToggleBackgroundPlay = toggleBackgroundPlay,
            onReplayTutorial = {
                tutorialStep = 0
                showTutorial = true
            },
            onAddToPlaylist = { showAddToPlaylistDialog = true },
            sheetState = playbackSheetState,
        )
    }

    if (showAddToPlaylistDialog && state.currentItem != null) {
        val current = state.currentItem!!
        AddToPlaylistDialog(
            playlists = customPlaylists,
            mediaTitle = current.title,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { pl ->
                scope.launch {
                    // Issue #230: -1 means the item is already in the
                    // playlist — say so instead of "added". This screen
                    // has no snackbar host, so feedback is a Toast like
                    // the rest of the player screen.
                    val rowId = playbackManager.persistenceRepository.addItemToPlaylist(
                        PlaylistItem(
                            playlistId = pl.id,
                            mediaUri = current.uri,
                            mediaId = current.id,
                            title = current.title,
                            subtitle = current.subtitle,
                            isVideo = current.isVideo,
                            durationMs = current.durationMs,
                        )
                    )
                    showAddToPlaylistDialog = false
                    val msg = if (rowId != -1L) {
                        context.getString(R.string.music_added_to_playlist_msg, pl.name)
                    } else {
                        context.getString(R.string.music_already_in_playlist_msg, pl.name)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onCreateNewPlaylist = {
                showAddToPlaylistDialog = false
                showCreatePlaylistDialog = true
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            // #268: the new playlist matches the playing item's type;
            // the fixed type is passed through (no selector here).
            initialIsVideoPlaylist = state.currentItem?.isVideo ?: false,
            onConfirm = { name, isVideo ->
                scope.launch {
                    val newId = playbackManager.persistenceRepository.createPlaylist(
                        name = name,
                        isVideoPlaylist = isVideo,
                    )
                    state.currentItem?.let { current ->
                        playbackManager.persistenceRepository.addItemToPlaylist(
                            PlaylistItem(
                                playlistId = newId,
                                mediaUri = current.uri,
                                mediaId = current.id,
                                title = current.title,
                                subtitle = current.subtitle,
                                isVideo = current.isVideo,
                                durationMs = current.durationMs,
                            )
                        )
                    }
                    showCreatePlaylistDialog = false
                }
            },
        )
    }

    // First-run gesture tutorial overlay (#151): drawn last so it sits
    // above every other player layer, and independent of controlsVisible.
    // Hidden in PiP like every other overlay.
    if (showTutorial && !isInPipMode) {
        PlayerTutorialOverlay(
            step = tutorialStep,
            onStepChange = { tutorialStep = it },
            onDismiss = dismissTutorial,
        )
    }
}

/**
 * Player Action Stripe (issue #177, moved to the top of the screen
 * in #327): the user's chosen actions from the
 * Player Layout editor (#158), in their order, as a horizontally
 * scrollable strip of compact buttons under the top bar. Each button
 * shows its icon and its short current value where it has one (speed,
 * decoder, aspect, ...); actions without a value show their short label.
 * Tapping opens the matching sheet page or toggles directly, exactly as
 * the Playback sheet's Main page does — except the aspect button, which
 * cycles FIT -> FILL -> ZOOM -> 16:9 -> 4:3 -> 100% like the #167 pill
 * it replaces, instead of opening the sheet. Toggles tint with the
 * identity-blue accent while on. The sheet's own order is untouched
 * (issue #160 stays separate).
 *
 * No new strings: every caption reuses an existing one. The rotation
 * "Adaptive" caption reuses player_rotation_auto — same displayed word
 * as the sheet's renamed mode (#329).
 */
@Composable
private fun PlayerActionStripe(
    listState: LazyListState,
    state: PlaybackState,
    currentSettings: AppSettings,
    isFavourite: Boolean,
    onOpenSheet: (PlaybackSheetPage) -> Unit,
    onToggleFavourite: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    playbackManager: PlaybackManager,
) {
    val context = LocalContext.current
    val layoutIds = remember(currentSettings.playerMenuLayout, currentSettings.hiddenPlayerMenuIds) {
        parsePlayerMenuLayout(currentSettings.playerMenuLayout, currentSettings.hiddenPlayerMenuIds)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(layoutIds, key = { it }) { id ->
            when (id) {
                "decoder" -> PlayerActionStripeButton(
                    icon = Icons.Filled.Memory,
                    caption = state.decoderMode.label,
                    contentDescription = stringResource(R.string.playback_sheet_decoder),
                    active = false,
                    onClick = { onOpenSheet(PlaybackSheetPage.Decoder) },
                )
                "audio" -> PlayerActionStripeButton(
                    icon = Icons.Filled.Audiotrack,
                    // Issue #262: an empty list (still preparing, or no
                    // discrete tracks) is not "audio off" — the video's
                    // audio is playing via the default output. Show no
                    // caption until a track list exists; "Off" is only
                    // for tracks present with none selected.
                    caption = if (state.audioTracks.isEmpty()) {
                        null
                    } else {
                        state.audioTracks.firstOrNull { it.isSelected }?.label
                            ?: stringResource(R.string.player_state_off)
                    },
                    contentDescription = stringResource(R.string.playback_sheet_audio_track),
                    active = false,
                    onClick = { onOpenSheet(PlaybackSheetPage.AudioTracks) },
                )
                "subtitles" -> PlayerActionStripeButton(
                    icon = Icons.Filled.Subtitles,
                    caption = state.subtitleTracks.firstOrNull { it.isSelected }?.label
                        ?: stringResource(R.string.player_state_off),
                    contentDescription = stringResource(R.string.playback_sheet_subtitle_track),
                    active = false,
                    onClick = { onOpenSheet(PlaybackSheetPage.SubtitleTracks) },
                )
                "favourite" -> PlayerActionStripeButton(
                    icon = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    caption = stringResource(playerMenuRowLabelRes("favourite")),
                    contentDescription = stringResource(
                        if (isFavourite) R.string.player_cd_favourite_remove else R.string.player_cd_favourite_add,
                    ),
                    active = isFavourite,
                    onClick = onToggleFavourite,
                )
                "shuffle" -> PlayerActionStripeButton(
                    icon = Icons.Filled.Shuffle,
                    caption = stringResource(
                        if (state.shuffleEnabled) R.string.player_state_on else R.string.player_state_off,
                    ),
                    contentDescription = stringResource(R.string.player_cd_shuffle),
                    active = state.shuffleEnabled,
                    onClick = { playbackManager.setShuffleEnabled(!state.shuffleEnabled) },
                )
                "repeat" -> PlayerActionStripeButton(
                    icon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    caption = stringResource(
                        when (state.repeatMode) {
                            RepeatMode.ONE -> R.string.player_cd_repeat_one
                            RepeatMode.ALL -> R.string.player_cd_repeat_all
                            else -> R.string.player_cd_repeat_off
                        },
                    ),
                    contentDescription = stringResource(R.string.playback_sheet_repeat),
                    active = state.repeatMode != RepeatMode.OFF,
                    onClick = { playbackManager.cycleRepeatMode() },
                )
                // Issue #225: PiP is video-only — hide the entry for audio
                // items (the enterPip lambda also refuses them, so an audio
                // item can never reach PiP from here or from the sheet).
                "pip" -> if (state.currentItem?.isVideo == true) PlayerActionStripeButton(
                    icon = Icons.Filled.PictureInPictureAlt,
                    caption = stringResource(playerMenuRowLabelRes("pip")),
                    contentDescription = stringResource(R.string.player_menu_popup_play),
                    active = false,
                    onClick = onEnterPip,
                )
                "background" -> PlayerActionStripeButton(
                    icon = Icons.Filled.PlayArrow,
                    caption = stringResource(
                        if (currentSettings.backgroundPlay) R.string.player_state_on else R.string.player_state_off,
                    ),
                    contentDescription = stringResource(R.string.playback_sheet_background_play),
                    active = currentSettings.backgroundPlay,
                    onClick = onToggleBackgroundPlay,
                )
                "speed" -> PlayerActionStripeButton(
                    icon = Icons.Filled.Speed,
                    caption = formatMultiplier(state.speed),
                    contentDescription = stringResource(R.string.playback_sheet_speed_title),
                    active = false,
                    onClick = { onOpenSheet(PlaybackSheetPage.Speed) },
                )
                "aspect" -> PlayerActionStripeButton(
                    icon = Icons.Filled.AspectRatio,
                    caption = stringResource(state.aspectRatio.labelRes),
                    contentDescription = stringResource(R.string.playback_sheet_aspect),
                    active = false,
                    onClick = {
                        // Same cycle the #167 top-bar pill did.
                        val modes = AspectRatioMode.values()
                        val next = modes[(modes.indexOf(state.aspectRatio) + 1) % modes.size]
                        playbackManager.setAspectRatio(next)
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_menu_aspect, context.getString(next.labelRes)),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
                "rotation" -> PlayerActionStripeButton(
                    icon = Icons.Filled.ScreenRotation,
                    // Issue #363: MX-style — a direct toggle, no menu.
                    // Caption is the current orientation; active while
                    // landscape. The override resets with the next video
                    // (see targetOrientation above).
                    caption = stringResource(
                        if (isLandscapeOrientation) R.string.player_orientation_landscape
                        else R.string.player_orientation_portrait,
                    ),
                    contentDescription = stringResource(R.string.playback_sheet_rotation),
                    active = isLandscapeOrientation,
                    onClick = {
                        rotationOverride = if (isLandscapeOrientation) "portrait" else "landscape"
                    },
                )
                "sleep" -> PlayerActionStripeButton(
                    icon = Icons.Filled.Timer,
                    caption = state.sleepTimerRemainingSeconds?.let { formatTimeMs(it * 1000L) }
                        ?: stringResource(R.string.player_sleep_timer_off),
                    contentDescription = stringResource(R.string.player_sleep_timer),
                    active = state.sleepTimerRemainingSeconds != null,
                    onClick = { onOpenSheet(PlaybackSheetPage.SleepTimer) },
                )
                "playlist" -> PlayerActionStripeButton(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    caption = stringResource(playerMenuRowLabelRes("playlist")),
                    contentDescription = stringResource(R.string.player_menu_add_playlist),
                    active = false,
                    onClick = onAddToPlaylist,
                )
                else -> {
                    // parsePlayerMenuLayout already drops unknown ids.
                }
            }
        }
    }
}

/** One compact stripe button: a 20dp icon over a 10sp caption. */
@Composable
private fun PlayerActionStripeButton(
    icon: ImageVector,
    caption: String?,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MpxShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MpxTheme.colors.primary else MpxTheme.colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        // Issue #262: a null caption (audio with no tracks known yet)
        // renders icon-only instead of a wrong "Off".
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = if (active) MpxTheme.colors.primary else MpxTheme.colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 76.dp),
            )
        }
    }
}

/**
 * First-run gesture tutorial overlay (#151). One overlay, four tappable
 * steps: double-tap zones, horizontal seek, left-side brightness,
 * right-side volume. Tapping the card advances a step; Skip and the X
 * dismiss at any time; the last step's button finishes. Dismissing in
 * any way marks the tutorial seen (the caller persists the flag).
 * Playback underneath is untouched: the scrim consumes all touches so
 * the root gesture detector never fires while the tutorial is up.
 */
@Composable
private fun PlayerTutorialOverlay(
    step: Int,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastStep = 3
    val isLast = step >= lastStep
    val advance = { if (isLast) onDismiss() else onStepChange(step + 1) }

    val titleRes = when (step) {
        0 -> R.string.player_tutorial_step_double_tap_title
        1 -> R.string.player_tutorial_step_seek_title
        2 -> R.string.player_tutorial_step_brightness_title
        else -> R.string.player_tutorial_step_volume_title
    }
    val descRes = when (step) {
        0 -> R.string.player_tutorial_step_double_tap_desc
        1 -> R.string.player_tutorial_step_seek_desc
        2 -> R.string.player_tutorial_step_brightness_desc
        else -> R.string.player_tutorial_step_volume_desc
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(MpxShapes.large)
                .background(MpxTheme.colors.surfaceVariant)
                .border(MpxHairline, MpxTheme.colors.border, MpxShapes.large)
                .clickable { advance() }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_tutorial_title),
                    color = MpxTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.player_tutorial_skip),
                        tint = MpxTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(titleRes),
                color = MpxTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))
            when (step) {
                0 -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Replay10, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(36.dp))
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MpxTheme.colors.primary, modifier = Modifier.size(44.dp))
                    Icon(Icons.Filled.Forward10, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(36.dp))
                }
                1 -> Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(4.dp)
                        .clip(MpxShapes.extraSmall)
                        .background(MpxTheme.colors.textMuted.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MpxTheme.colors.primary),
                    )
                }
                2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.BrightnessHigh, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(72.dp)
                            .clip(MpxShapes.extraSmall)
                            .background(MpxTheme.colors.textMuted.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MpxTheme.colors.primary),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Icon(Icons.Filled.BrightnessLow, contentDescription = null, tint = MpxTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
                }
                else -> Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(104.dp)
                        .clip(MpxShapes.extraSmall)
                        .background(MpxTheme.colors.textMuted.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MpxTheme.colors.primary),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(descRes),
                color = MpxTheme.colors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == step) MpxTheme.colors.primary
                                else MpxTheme.colors.textMuted.copy(alpha = 0.4f),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.player_tutorial_skip), color = MpxTheme.colors.textSecondary)
                }
                TextButton(onClick = advance) {
                    Text(
                        stringResource(if (isLast) R.string.player_tutorial_got_it else R.string.player_tutorial_next),
                        color = MpxTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
