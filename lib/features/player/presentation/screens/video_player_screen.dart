import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../../../core/services/last_played_service.dart';
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
class VideoPlayerScreen extends StatelessWidget {
  final VideoFile video;

  const VideoPlayerScreen({super.key, required this.video});

  @override
  Widget build(BuildContext context) {
    // Provide PlayerController using ChangeNotifierProvider
    // Provider automatically handles disposal via controller.dispose()
    return ChangeNotifierProvider(
      create: (_) {
        final repository = MediaKitPlayerRepository();
        final controller = PlayerController(repository);
        controller.loadVideo(video.path);
        WakelockPlus.enable();
        controller.startHideTimer();
        return controller;
      },
      child: _VideoPlayerScreenContent(video: video),
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
  @override
  void initState() {
    super.initState();
    // Initialize with portrait orientation
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    // Save as last played video
    LastPlayedService.saveLastPlayedVideo(widget.video);
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
    // Ensure system UI settings are reset when widget is disposed
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    super.dispose();
  }
}
