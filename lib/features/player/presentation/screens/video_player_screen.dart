import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../../history/services/history_service.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../../library/data/datasources/directory_browser.dart';
import '../../../library/domain/entities/file_item.dart';
import '../../../settings/services/app_settings_service.dart';
import '../../controller/player_controller.dart';
import '../../data/repositories/mpv_player_repository.dart';
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
  final List<VideoFile>? playlist;
  final int? initialPlaylistIndex;

  const VideoPlayerScreen({
    super.key,
    required this.video,
    this.playlist,
    this.initialPlaylistIndex,
  });

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
        final repository = MpvPlayerRepository();
        final controller = PlayerController(repository);
        controller.loadVideoFile(widget.video);
        if (AppSettingsService.keepScreenAwake) {
          WakelockPlus.enable();
        }
        controller.startHideTimer();
        return controller;
      },
      child: _VideoPlayerScreenContent(
        video: widget.video,
        playlist: widget.playlist,
        initialPlaylistIndex: widget.initialPlaylistIndex ?? 0,
      ),
    );
  }
}

/// The actual screen content that consumes PlayerController from Provider.
class _VideoPlayerScreenContent extends StatefulWidget {
  final VideoFile video;
  final List<VideoFile>? playlist;
  final int initialPlaylistIndex;

  const _VideoPlayerScreenContent({
    required this.video,
    this.playlist,
    required this.initialPlaylistIndex,
  });

  @override
  State<_VideoPlayerScreenContent> createState() =>
      _VideoPlayerScreenContentState();
}

class _VideoPlayerScreenContentState extends State<_VideoPlayerScreenContent> {
  ScaffoldFeatureController<SnackBar, SnackBarClosedReason>?
      _resumeSnackBarController;
  late int _currentIndex;
  late List<VideoFile> _playlist;
  String? _overlayMessage;
  Timer? _messageTimer;

  @override
  void initState() {
    super.initState();
    if (widget.playlist != null && widget.playlist!.isNotEmpty) {
      _playlist = widget.playlist!;
      _currentIndex = widget.initialPlaylistIndex;
    } else {
      _playlist = [widget.video];
      _currentIndex = 0;
      _loadFolderPlaylist();
    }
    // Initialize with portrait orientation
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    // Check for auto-resume after a short delay to ensure video is loaded
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAndResumePlayback();
      _recordVideoInHistory();
    });
  }

  Future<void> _recordVideoInHistory() async {
    // Check if video already has a history entry
    final existing = await HistoryService.getHistoryEntry(widget.video.id);
    if (existing != null) return; // Don't overwrite existing entry

    // Record video in history only if it's new
    await HistoryService.recordPlayback(
      video: widget.video,
      position: Duration.zero,
      duration: widget.video.duration > 0
          ? Duration(milliseconds: widget.video.duration)
          : Duration.zero,
    );
  }

  Future<void> _loadFolderPlaylist() async {
    try {
      final browser = DirectoryBrowser();
      final fileItems = await browser.listDirectory(widget.video.folderPath);
      final videos = fileItems
          .where((item) =>
              !item.isDirectory && FileItem.isVideoFileName(item.name))
          .map((item) => VideoFile.fromFileItem(item, widget.video.folderPath))
          .toList();

      if (videos.length > 1) {
        // Find current video's index
        final index = videos.indexWhere((v) => v.id == widget.video.id);
        if (index >= 0 && mounted) {
          setState(() {
            _playlist = videos;
            _currentIndex = index;
          });
        }
      }
    } catch (e) {
      // If loading fails, keep single video playlist
    }
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
    // Use select to only rebuild when isFullscreen changes
    final isFullscreen = context.select<PlayerController, bool>(
        (controller) => controller.isFullscreen);
    final controller = context.read<PlayerController>();

    // Handle fullscreen state
    if (isFullscreen) {
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
        body: Stack(
          children: [
            PlayerView(
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
              onSubtitleSettings: () =>
                  _showSubtitleSettings(context, controller),
              onSettings: () => _showSettings(context, controller),
              onNext: _playlist.length > 1 ? () => _playNext(context) : null,
              onPrevious:
                  _playlist.length > 1 ? () => _playPrevious(context) : null,
            ),
            if (_overlayMessage != null)
              Positioned(
                top: MediaQuery.of(context).padding.top + 60,
                left: 0,
                right: 0,
                child: Center(
                  child: AnimatedOpacity(
                    opacity: _overlayMessage != null ? 1.0 : 0.0,
                    duration: const Duration(milliseconds: 300),
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 8),
                      decoration: BoxDecoration(
                        color: Colors.black54,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        _overlayMessage!,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ],
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

  void _playPrevious(BuildContext context) async {
    if (_currentIndex <= 0) {
      _showOverlayMessage('No previous video');
      return;
    }
    final controller = context.read<PlayerController>();
    controller.pauseVideo();
    await controller.saveAndCleanup();
    _currentIndex--;
    if (context.mounted) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => VideoPlayerScreen(
            video: _playlist[_currentIndex],
            playlist: _playlist,
            initialPlaylistIndex: _currentIndex,
          ),
        ),
      );
    }
  }

  void _playNext(BuildContext context) async {
    if (_currentIndex >= _playlist.length - 1) {
      _showOverlayMessage('No next video');
      return;
    }
    final controller = context.read<PlayerController>();
    controller.pauseVideo();
    await controller.saveAndCleanup();
    _currentIndex++;
    if (context.mounted) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => VideoPlayerScreen(
            video: _playlist[_currentIndex],
            playlist: _playlist,
            initialPlaylistIndex: _currentIndex,
          ),
        ),
      );
    }
  }

  void _showOverlayMessage(String message) {
    setState(() => _overlayMessage = message);
    _messageTimer?.cancel();
    _messageTimer = Timer(const Duration(seconds: 2), () {
      if (mounted) setState(() => _overlayMessage = null);
    });
  }

  @override
  void dispose() {
    // Close the resume snackbar when leaving the player
    ResumePlaybackHelper.closeSnackbar(_resumeSnackBarController);
    _messageTimer?.cancel();
    super.dispose();
  }
}
