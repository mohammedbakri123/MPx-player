package com.mpx.player.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.playback.PlaybackService
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.ui.screens.player.components.PlayerBottomChrome
import com.mpx.player.ui.screens.player.components.PlayerDialogs
import com.mpx.player.ui.screens.player.components.PlayerOverlays
import com.mpx.player.ui.screens.player.components.PlayerTopChrome
import com.mpx.player.ui.screens.player.components.PlayerTutorialOverlay
import com.mpx.player.ui.screens.player.components.PlayerVideoSurface
import com.mpx.player.ui.screens.player.components.playerGestureDetector
import com.mpx.player.ui.screens.player.state.rememberPlayerGestureState
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.util.PipUtils
import com.mpx.player.util.SubtitleUtils
import com.mpx.player.util.SubtitleValidationResult
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    subtitleLauncher: ActivityResultLauncher<Array<String>>,
    subtitleResultHandler: ActivityResultHandler<Uri?>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    playbackCoordinator: PlaybackCoordinator,
    isInPipMode: Boolean = false,
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = koinViewModel()
    val activity = LocalView.current.context as? Activity
    val currentSettings by viewModel.settings.collectAsState()

    // Plain holder (not state) so pushing options does not schedule recompositions (§6)
    val lastMpvSubtitleSettings = remember { arrayOfNulls<AppSettings>(1) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val state by viewModel.playbackState.collectAsState()

    LaunchedEffect(isInPipMode) { viewModel.setPipMode(isInPipMode) }
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val playerView = LocalView.current
    DisposableEffect(keepScreenOn) {
        playerView.keepScreenOn = keepScreenOn
        onDispose { playerView.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        viewModel.restoreQueueIfEmpty()
    }

    val isLandscapeOrientation by viewModel.isLandscapeOrientation.collectAsState()
    val targetOrientation by viewModel.targetOrientation.collectAsState()

    DisposableEffect(targetOrientation, activity) {
        activity?.requestedOrientation = targetOrientation
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Window-brightness reset on screen disposal (issue #204) — MUST stay at root scope
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

    // Stop on dispose when background play is off (issue #92) — MUST stay at root scope
    DisposableEffect(Unit) {
        onDispose {
            val activityInPip = activity?.isInPictureInPictureMode == true
            if (!activityInPip && !viewModel.loadSettingsNow().backgroundPlay) {
                viewModel.stop()
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
                        viewModel.loadExternalSubtitle(
                            uri = validation.uri,
                            displayName = validation.displayName,
                            mimeType = validation.mimeType,
                        )
                    }
                    is SubtitleValidationResult.Unsupported -> {
                        subtitleErrorDialogText = context.getString(
                            R.string.player_subtitle_load_error,
                            validation.fileName,
                            validation.reason,
                        )
                    }
                }
            }
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSheetPage by remember { mutableStateOf<PlaybackSheetPage?>(null) }
    val playbackSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showTutorial by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val settings = viewModel.loadSettingsNow()
        if (!settings.playerTutorialSeen && settings.gesturesEnabled) {
            showTutorial = true
        }
    }

    val dismissTutorial = {
        val updated = currentSettings.copy(playerTutorialSeen = true)
        viewModel.updateSettings(updated)
        showTutorial = false
    }

    val scope = rememberCoroutineScope()

    val enterPip = {
        val item = state.currentItem
        if (item?.isVideo != true) {
            Toast.makeText(context, context.getString(R.string.player_pip_unable), Toast.LENGTH_SHORT).show()
        } else {
            val entered = PipUtils.enterPip(
                activity,
                currentSettings,
                state.isPlaying,
                viewModel.videoWidth,
                viewModel.videoHeight,
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
        viewModel.updateSettings(updated)
        if (!nextBackgroundPlay) {
            PlaybackService.stop(context)
            viewModel.setVideoBackgroundActive(false)
            viewModel.setBackgroundPlaying(false)
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

    // VM-owned persistence and dialog state (§6)
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsState()
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    val pendingMediaForPlaylistAdd by viewModel.pendingMediaForPlaylistAdd.collectAsState()

    var isScrubbingSlider by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    val gestureState = rememberPlayerGestureState(
        context = context,
        activity = activity,
        audioManager = audioManager,
    )

    val topBarActionsListState = rememberLazyListState()

    // Auto-hide controls timer: key set MUST be preserved (§6)
    LaunchedEffect(
        controlsVisible,
        state.isPlaying,
        playbackSheetPage,
        isScrubbingSlider,
        gestureState.isDraggingSeek,
        gestureState.showBrightnessIndicator,
        gestureState.showVolumeIndicator,
        gestureState.showZoomIndicator,
        topBarActionsListState.isScrollInProgress,
    ) {
        if (controlsVisible && state.isPlaying && playbackSheetPage == null &&
            !isScrubbingSlider && !gestureState.isDraggingSeek &&
            !gestureState.showBrightnessIndicator && !gestureState.showVolumeIndicator &&
            !gestureState.showZoomIndicator && !topBarActionsListState.isScrollInProgress
        ) {
            delay(3000)
            controlsVisible = false
        }
    }

    // System bars effect (issue #195)
    DisposableEffect(controlsVisible, isInPipMode, activity) {
        val window = activity?.window
        if (window != null && !isInPipMode) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            if (!controlsVisible) {
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose {
            activity?.window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    w.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MpxTheme.colors.black)
            .playerGestureDetector(
                isLocked = state.isLocked,
                currentDuration = currentDuration,
                state = state,
                currentSettings = currentSettings,
                gestureState = gestureState,
                viewModel = viewModel,
                scope = scope,
                context = context,
                activity = activity,
                audioManager = audioManager,
                onToggleControls = { controlsVisible = !controlsVisible },
            ),
    ) {
        // Video Surface
        PlayerVideoSurface(
            viewModel = viewModel,
            currentSettings = currentSettings,
            lastMpvSubtitleSettings = lastMpvSubtitleSettings,
        )

        // Overlays & HUDs
        PlayerOverlays(
            state = state,
            isInPipMode = isInPipMode,
            controlsVisible = controlsVisible,
            currentDuration = currentDuration,
            gestureState = gestureState,
            viewModel = viewModel,
        )

        // Full Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode && !state.isPreparing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerTopChrome(
                    onClose = onClose,
                    state = state,
                    currentSettings = currentSettings,
                    isFavourite = isFavourite,
                    topBarActionsListState = topBarActionsListState,
                    onOpenSheet = { playbackSheetPage = it },
                    onToggleFavourite = { viewModel.toggleFavourite() },
                    onEnterPip = enterPip,
                    onToggleBackgroundPlay = toggleBackgroundPlay,
                    onAddToPlaylist = { state.currentItem?.let { viewModel.showAddToPlaylist(it) } },
                    viewModel = viewModel,
                )

                PlayerBottomChrome(
                    displayedCurrentTime = displayedCurrentTime,
                    currentDuration = currentDuration,
                    sliderProgress = sliderProgress,
                    onSliderChange = {
                        isScrubbingSlider = true
                        scrubFraction = it
                    },
                    onSliderChangeFinished = {
                        val target = (scrubFraction * currentDuration).toLong()
                        viewModel.seekTo(target)
                        isScrubbingSlider = false
                    },
                    state = state,
                    isLandscapeOrientation = isLandscapeOrientation,
                    onOpenSleepSheet = { playbackSheetPage = PlaybackSheetPage.SleepTimer },
                    viewModel = viewModel,
                )
            }
        }
    }

    // Playback Sheet
    playbackSheetPage?.let { sheetPage ->
        PlayerPlaybackSheet(
            page = sheetPage,
            onPageChange = { playbackSheetPage = it },
            state = state,
            currentSettings = currentSettings,
            onSettingsChanged = { updated ->
                viewModel.updateSettings(updated)
            },
            playbackController = viewModel,
            subtitleLauncher = subtitleLauncher,
            isFavourite = isFavourite,
            onToggleFavourite = { viewModel.toggleFavourite() },
            onEnterPip = enterPip,
            onToggleBackgroundPlay = toggleBackgroundPlay,
            onReplayTutorial = {
                tutorialStep = 0
                showTutorial = true
            },
            onAddToPlaylist = { state.currentItem?.let { viewModel.showAddToPlaylist(it) } },
            sheetState = playbackSheetState,
        )
    }

    // Dialogs
    PlayerDialogs(
        viewModel = viewModel,
        showAddToPlaylistDialog = showAddToPlaylistDialog,
        pendingMedia = pendingMediaForPlaylistAdd ?: state.currentItem,
        customPlaylists = customPlaylists,
        showCreatePlaylistDialog = showCreatePlaylistDialog,
        subtitleErrorDialogText = subtitleErrorDialogText,
        onDismissSubtitleError = { subtitleErrorDialogText = null },
        scope = scope,
    )

    // Tutorial overlay
    if (showTutorial && !isInPipMode) {
        PlayerTutorialOverlay(
            step = tutorialStep,
            onStepChange = { tutorialStep = it },
            onDismiss = dismissTutorial,
        )
    }
}
