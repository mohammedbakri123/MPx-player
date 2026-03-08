import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../controller/player_controller.dart';
import '../../data/repositories/media_kit_player_repository.dart';
import '../widgets/player_view.dart';
import '../widgets/subtitle_settings_sheet.dart';
import '../widgets/settings_sheet.dart';
import '../widgets/resume_playback_helper.dart';

/// Wrapper widget that provides PlayerController using Provider.
///
/// This widget creates a PlayerController with proper dependency injection
/// and automatically handles disposal when the widget is removed.
class VideoPlayerScreen extends StatefulWidget {
  final VideoFile video;

  const VideoPlayerScreen({super.key, required this.video});

  @override
  State<VideoPlayerScreen> createState() => _VideoPlayerScreenState();
}

class _VideoPlayerScreenState extends State<VideoPlayerScreen>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      // App going to background - save position and pause
      final controller = context.read<PlayerController>();
      controller.pauseVideo();

      // Fire-and-forget is OK here — OS gives us a brief window,
      // and SharedPreferences writes are fast. Force ensures no throttle.
      //fire and forget means we don't await the future, allowing the app to continue closing without delay.
      controller.savePositionOnBackground();
    }
  }

  @override
  Widget build(BuildContext context) {
    // Provide PlayerController using ChangeNotifierProvider
    // Provider automatically handles disposal via controller.dispose()
    return ChangeNotifierProvider(
      create: (_) {
        final repository = MediaKitPlayerRepository();
        final controller = PlayerController(repository);
        controller.loadVideoFile(widget.video);
        WakelockPlus.enable(); // Keep screen on while player is active
        controller.startHideTimer();
        return controller;
      },
      child: _VideoPlayerScreenContent(video: widget.video),
    );
  }
}

/// The actual screen content that consumes PlayerController from Provider.
class _VideoPlayerScreenContent extends StatefulWidget {
  final VideoFile video;

  const _VideoPlayerScreenContent({required this.video});

  @override
  State<_VideoPlayerScreenContent> createState() =>
      _VideoPlayerScreenContentState();
}

class _VideoPlayerScreenContentState extends State<_VideoPlayerScreenContent> {
  ScaffoldFeatureController<SnackBar, SnackBarClosedReason>?
      _resumeSnackBarController;

  @override
  void initState() {
    super.initState();
    // Initialize with portrait orientation
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    // Check for auto-resume after a short delay to ensure video is loaded
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAndResumePlayback();
    });
  }

  Future<void> _checkAndResumePlayback() async {
    final controller = context.read<PlayerController>();

    final savedPosition = await ResumePlaybackHelper.checkAndResumePlayback(
      controller: controller,
      totalDuration: controller.duration,
      videoId: widget.video.id,
    );

    // Show resume snackbar if position was found and resumed
    if (savedPosition != null && mounted) {
      _resumeSnackBarController = ResumePlaybackHelper.showResumeSnackbar(
        context: context,
        controller: controller,
        position: savedPosition,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    // Get controller from Provider
    final controller = context.watch<PlayerController>();

    // Handle fullscreen state
    if (controller.isFullscreen) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
      SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
    } else {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
      SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    }

    return PopScope(
      canPop: false, // Disable default pop behavior to handle it ourselves
      onPopInvokedWithResult: (didPop, Object? result) async {
        if (didPop) return; // If already popped, do nothing

        // Pause video first so position stops advancing
        controller.pauseVideo();

        // Await the position save — this is the critical fix!
        await controller.saveAndCleanup();

        // Store the navigator reference before the async operations
        // ignore: use_build_context_synchronously
        final navigator = Navigator.of(context);

        // Reset system UI settings when leaving the player
        await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
        await SystemChrome.setPreferredOrientations(
            [DeviceOrientation.portraitUp]);

        //  Small delay to ensure settings apply
        await Future.delayed(const Duration(milliseconds: 200));

        // Perform the navigation pop
        navigator.pop();
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: PlayerView(
          controller: controller,
          videoTitle: widget.video.title,
          onBack: () async {
            // Pause video first so position stops advancing
            controller.pauseVideo();

            // Await the position save before navigating away
            await controller.saveAndCleanup();

            // Reset system UI settings when leaving the player via back button
            SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
            SystemChrome.setPreferredOrientations(
                [DeviceOrientation.portraitUp]);
            // ignore: use_build_context_synchronously
            // Small delay to ensure settings apply
            await Future.delayed(const Duration(milliseconds: 200));

            Navigator.pop(context);
          },
          onSubtitleSettings: () => _showSubtitleSettings(context, controller),
          onSettings: () => _showSettings(context, controller),
        ),
      ),
    );
  }

  void _showSubtitleSettings(
      BuildContext context, PlayerController controller) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => SubtitleSettingsSheet(controller: controller),
    );
  }

  void _showSettings(BuildContext context, PlayerController controller) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => SettingsSheet(
        controller: controller,
        onOpenSubtitleSettings: () =>
            _showSubtitleSettings(this.context, controller),
      ),
    );
  }

  @override
  void dispose() {
    // Close the resume snackbar when leaving the player
    ResumePlaybackHelper.closeSnackbar(_resumeSnackBarController);
    super.dispose();
  }
}
