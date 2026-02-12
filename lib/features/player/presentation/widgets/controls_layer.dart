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
    return AnimatedOpacity(
      opacity: controller.showControls ? 1.0 : 0.0,
      duration: const Duration(milliseconds: 200),
      child: IgnorePointer(
        ignoring: !controller.showControls,
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                Colors.black.withValues(alpha: 0.7),
                Colors.transparent,
                Colors.transparent,
                Colors.black.withValues(alpha: 0.8),
              ],
              stops: const [0.0, 0.2, 0.7, 1.0],
            ),
          ),
          child: SafeArea(
            bottom: true,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TopBar(
                    title: title,
                    isFullscreen: controller.isFullscreen,
                    subtitlesEnabled: controller.subtitlesEnabled,
                    onBack: onBack,
                    onToggleFullscreen: controller.toggleFullscreen,
                    onSubtitleSettings: onSubtitleSettings,
                    onSettings: onSettings,
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
                    playbackSpeed: controller.playbackSpeed,
                    isPlaying: controller.isPlaying,
                    formatTime: controller.formatDuration,
                    onSeekChanged: (value) {
                      controller.seek(Duration(milliseconds: value.toInt()));
                    },
                    onSeekEnd: (value) {
                      controller.seek(Duration(milliseconds: value.toInt()));
                    },
                    onSeekBack: controller.seekBack,
                    onTogglePlayPause: controller.togglePlayPause,
                    onSeekForward: controller.seekForward,
                    onChangeSpeed: controller.changeSpeed,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
