import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'package:simple_pip_mode/actions/pip_action.dart';
import 'package:simple_pip_mode/actions/pip_actions_layout.dart';
import 'package:simple_pip_mode/pip_widget.dart';
import 'package:simple_pip_mode/simple_pip.dart';
import '../../../history/services/history_service.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../../library/data/datasources/directory_browser.dart';
import '../../../library/domain/entities/file_item.dart';
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
  bool _isInPipMode = false;

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

  void _setPipMode(bool inPip) {
    _isInPipMode = inPip;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      if (_isInPipMode) return;
      final controller = context.read<PlayerController>();
      controller.pauseVideo();
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
        WakelockPlus.enable();
        controller.startHideTimer();
        return controller;
      },
      child: _VideoPlayerScreenContent(
        video: widget.video,
        playlist: widget.playlist,
        initialPlaylistIndex: widget.initialPlaylistIndex ?? 0,
        onPipModeChanged: _setPipMode,
        isInPipMode: _isInPipMode,
      ),
    );
  }
}

/// The actual screen content that consumes PlayerController from Provider.
class _VideoPlayerScreenContent extends StatefulWidget {
  final VideoFile video;
  final List<VideoFile>? playlist;
  final int initialPlaylistIndex;
  final ValueChanged<bool>? onPipModeChanged;
  final bool isInPipMode;

  const _VideoPlayerScreenContent({
    required this.video,
    this.playlist,
    required this.initialPlaylistIndex,
    this.onPipModeChanged,
    this.isInPipMode = false,
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
  bool _showPipButton = false;

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
      _initPip();
    });
  }

  void _initPip() {
    if (!Platform.isAndroid) return;
    setState(() => _showPipButton = true);
  }

  Future<void> _togglePip() async {
    final controller = context.read<PlayerController>();
    await controller.saveCurrentPosition(force: true);
    controller.cancelHideTimer();
    await SimplePip().enterPipMode();
  }

  Future<void> _recordVideoInHistory() async {
    final existing = await HistoryService.getHistoryEntry(widget.video.id);
    if (existing != null) return;
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
    final isFullscreen = context.select<PlayerController, bool>(
        (controller) => controller.isFullscreen);
    final controller = context.read<PlayerController>();

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
      canPop: false,
      onPopInvokedWithResult: (didPop, Object? result) async {
        if (didPop) return;
        controller.pauseVideo();
        await controller.saveAndCleanup();
        // ignore: use_build_context_synchronously
        final navigator = Navigator.of(context);
        await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
        await SystemChrome.setPreferredOrientations(
            [DeviceOrientation.portraitUp]);
        await Future.delayed(const Duration(milliseconds: 200));
        navigator.pop();
      },
      child: PipWidget(
        pipLayout: PipActionsLayout.media,
        onPipAction: (action) {
          final controller = context.read<PlayerController>();
          switch (action) {
            case PipAction.play:
              controller.togglePlayPause();
            case PipAction.pause:
              controller.pauseVideo();
            default:
              break;
          }
        },
        pipChild: Scaffold(
          backgroundColor: Colors.black,
          body: Center(
            child: PlayerView(
              controller: controller,
              videoTitle: widget.video.title,
              onBack: () {},
              onSubtitleSettings: () {},
              onSettings: () {},
              isInPipMode: true,
            ),
          ),
        ),
        child: Scaffold(
          backgroundColor: Colors.black,
          body: Stack(
            children: [
              PlayerView(
                controller: controller,
                videoTitle: widget.video.title,
                onBack: () async {
                  controller.pauseVideo();
                  await controller.saveAndCleanup();
                  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
                  SystemChrome.setPreferredOrientations(
                      [DeviceOrientation.portraitUp]);
                  await Future.delayed(const Duration(milliseconds: 200));
                  // ignore: use_build_context_synchronously
                  Navigator.pop(context);
                },
                onSubtitleSettings: () =>
                    _showSubtitleSettings(context, controller),
                onSettings: () => _showSettings(context, controller),
                onNext: _playlist.length > 1 ? () => _playNext(context) : null,
                onPrevious:
                    _playlist.length > 1 ? () => _playPrevious(context) : null,
                onTogglePip: _showPipButton ? _togglePip : null,
                showPipButton: _showPipButton,
                isInPipMode: widget.isInPipMode,
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
    ResumePlaybackHelper.closeSnackbar(_resumeSnackBarController);
    _messageTimer?.cancel();
    super.dispose();
  }
}
