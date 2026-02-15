import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../../../core/services/last_played_service.dart';
import '../../../../core/services/play_history_service.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../controller/player_controller.dart';
import '../../data/repositories/media_kit_player_repository.dart';
import '../widgets/player_view.dart';
import '../widgets/subtitle_settings_sheet.dart';
import '../widgets/settings_sheet.dart';

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
      controller.savePositionOnBackground();
      controller.pauseVideo();
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
        WakelockPlus.enable();
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
    // Save as last played video
    LastPlayedService.saveLastPlayedVideo(widget.video);
    // Check for auto-resume after a short delay to ensure video is loaded
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAndResumePlayback();
    });
  }

  Future<void> _checkAndResumePlayback() async {
    final controller = context.read<PlayerController>();
    final video = widget.video;

    // Wait for duration to be loaded (max 3 seconds)
    var attempts = 0;
    while (controller.duration.inSeconds == 0 && attempts < 30) {
      await Future.delayed(const Duration(milliseconds: 100));
      attempts++;
    }

    // If duration is still 0, video failed to load
    if (controller.duration.inSeconds == 0) return;

    // Get the saved position
    final savedPosition = await PlayHistoryService.getPosition(video.id);
    if (savedPosition == null) return;

    // Check if we should resume (position > 5s from start and > 30s from end)
    final totalSeconds = controller.duration.inSeconds;
    final positionSeconds = savedPosition.inSeconds;
    final remainingSeconds = totalSeconds - positionSeconds;

    // Don't resume if at the beginning or near the end
    if (positionSeconds < 5 || remainingSeconds <= 30) {
      return;
    }

    // Seek to the saved position
    controller.seek(savedPosition);

    // Show resume snackbar
    if (mounted) {
      final formattedTime = formatTime(savedPosition);
      _showResumeSnackbar(controller, formattedTime);
    }
  }

  void _showResumeSnackbar(PlayerController controller, String timeStr) {
    final scaffoldMessenger = ScaffoldMessenger.of(context);

    // Hide any existing snackbars
    scaffoldMessenger.hideCurrentSnackBar();

    _resumeSnackBarController = scaffoldMessenger.showSnackBar(
      SnackBar(
        content: Text(
          'Resumed from $timeStr',
          style: const TextStyle(color: Colors.white, fontSize: 14),
        ),
        duration: const Duration(seconds: 5),
        backgroundColor: Colors.black.withValues(alpha: 0.8),
        action: SnackBarAction(
          label: 'Restart',
          textColor: Colors.white,
          onPressed: () {
            // Seek to beginning
            controller.seek(Duration.zero);
            // Hide the snackbar
            _resumeSnackBarController?.close();
          },
        ),
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.only(bottom: 80, left: 16, right: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
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

        // Pause video and save position before leaving (force save)
        controller.saveCurrentPosition(force: true);
        controller.pauseVideo();

        // Store the navigator reference before the async operations
        final navigator = Navigator.of(context);

        // Reset system UI settings when leaving the player
        await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
        await SystemChrome.setPreferredOrientations(
            [DeviceOrientation.portraitUp]);

        // Perform the navigation pop
        navigator.pop();
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: PlayerView(
          controller: controller,
          videoTitle: widget.video.title,
          onBack: () {
            // Pause video and save position before leaving via back button (force save)
            controller.saveCurrentPosition(force: true);
            controller.pauseVideo();

            // Reset system UI settings when leaving the player via back button
            SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
            SystemChrome.setPreferredOrientations(
                [DeviceOrientation.portraitUp]);
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
      builder: (context) => SettingsSheet(controller: controller),
    );
  }

  @override
  void dispose() {
    // Close the resume snackbar when leaving the player
    _resumeSnackBarController?.close();
    super.dispose();
  }
}
