import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';
import 'top_bar.dart';
import 'bottom_controls.dart';
import 'play_pause_button.dart';

class ControlsLayer extends StatelessWidget {
  final PlayerController controller;
  final String title;
  final VoidCallback onBack;
  final VoidCallback onSubtitleSettings;
  final VoidCallback onSettings;

  const ControlsLayer({
    super.key,
    required this.controller,
    required this.title,
    required this.onBack,
    required this.onSubtitleSettings,
    required this.onSettings,
  });

  @override
  Widget build(BuildContext context) {
    if (controller.isLocked) {
      return _buildLockedLayer(context);
    }

    return AnimatedOpacity(
      opacity: controller.showControls ? 1.0 : 0.0,
      duration: const Duration(milliseconds: 200),
      child: IgnorePointer(
        ignoring: !controller.showControls,
        child: _buildControlUI(context),
      ),
    );
  }

  Widget _buildLockedLayer(BuildContext context) {
    return Container(
      color: Colors.transparent,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          child: Align(
            alignment: Alignment.topLeft,
            child: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.72),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              ),
              child: IconButton(
                onPressed: controller.unlock,
                padding: EdgeInsets.zero,
                splashRadius: 16,
                iconSize: 16,
                icon: const Icon(Icons.lock, color: Colors.white),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildControlUI(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Colors.black.withValues(alpha: 0.75),
            Colors.black.withValues(alpha: 0.35),
            Colors.black.withValues(alpha: 0.15),
            Colors.black.withValues(alpha: 0.9),
          ],
          stops: const [0.0, 0.25, 0.6, 1.0],
        ),
      ),
      child: SafeArea(
        bottom: true,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TopBar(
                title: title,
                isFullscreen: controller.isFullscreen,
                isLocked: controller.isLocked,
                subtitlesEnabled: controller.subtitlesEnabled,
                aspectRatioMode: controller.aspectRatioMode,
                audioTracks: controller.audioTracks,
                currentAudioTrackIndex: controller.currentAudioTrackIndex,
                onBack: onBack,
                onToggleLock: controller.toggleLock,
                onToggleFullscreen: controller.toggleFullscreen,
                onToggleAspectRatio: controller.cycleAspectRatio,
                onSubtitleSettings: onSubtitleSettings,
                onSettings: onSettings,
                onShowAudioTracks: null,
              ),
              const Spacer(),
              PlayPauseButton(
                isPlaying: controller.isPlaying,
                onTap: controller.togglePlayPause,
              ),
              const Spacer(),
              BottomControls(
                position: controller.position,
                duration: controller.duration,
                isPlaying: controller.isPlaying,
                isFullscreen: controller.isFullscreen,
                isLocked: controller.isLocked,
                formatTime: controller.formatDuration,
                onSeekChanged: (value) {
                  controller.previewSeek(Duration(milliseconds: value.toInt()));
                },
                onSeekEnd: (value) {
                  controller.seek(Duration(milliseconds: value.toInt()));
                },
                onSeekBack: controller.seekBack,
                onTogglePlayPause: controller.togglePlayPause,
                onSeekForward: controller.seekForward,
                onToggleFullscreen: controller.toggleFullscreen,
                onToggleLock: controller.toggleLock,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
