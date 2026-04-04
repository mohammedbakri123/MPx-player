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
  final VoidCallback? onNext;
  final VoidCallback? onPrevious;
  final VoidCallback? onTogglePip;
  final bool showPipButton;

  const ControlsLayer({
    super.key,
    required this.controller,
    required this.title,
    required this.onBack,
    required this.onSubtitleSettings,
    required this.onSettings,
    this.onNext,
    this.onPrevious,
    this.onTogglePip,
    this.showPipButton = false,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 200 || constraints.maxHeight < 120) {
          return const SizedBox.shrink();
        }
        return _buildContent(context);
      },
    );
  }

  Widget _buildContent(BuildContext context) {
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
    return Stack(
      fit: StackFit.expand,
      children: [
        IgnorePointer(
          child: DecoratedBox(
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
          ),
        ),
        SafeArea(
          bottom: true,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Stack(
              children: [
                Align(
                  alignment: Alignment.topCenter,
                  child: TopBar(
                    title: title,
                    isFullscreen: controller.isFullscreen,
                    isLocked: controller.isLocked,
                    subtitlesEnabled: controller.subtitlesEnabled,
                    aspectRatioMode: controller.aspectRatioMode,
                    audioTracks: controller.audioTracks,
                    currentAudioTrackIndex: controller.currentAudioTrackIndex,
                    onBack: () {
                      controller.registerControlsInteraction();
                      onBack();
                    },
                    onToggleLock: controller.toggleLock,
                    onToggleFullscreen: controller.toggleFullscreen,
                    onToggleAspectRatio: controller.cycleAspectRatio,
                    onSubtitleSettings: () {
                      controller.registerControlsInteraction();
                      onSubtitleSettings();
                    },
                    onSettings: () {
                      controller.registerControlsInteraction();
                      onSettings();
                    },
                    onShowAudioTracks: null,
                  ),
                ),
                Align(
                  alignment: Alignment.center,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (onPrevious != null)
                        _SeekButton(
                          icon: Icons.skip_previous,
                          onTap: () {
                            controller.registerControlsInteraction();
                            onPrevious!();
                          },
                        ),
                      if (onPrevious != null) const SizedBox(width: 24),
                      PlayPauseButton(
                        isPlaying: controller.isPlaying,
                        onTap: controller.togglePlayPause,
                      ),
                      if (onNext != null) const SizedBox(width: 24),
                      if (onNext != null)
                        _SeekButton(
                          icon: Icons.skip_next,
                          onTap: () {
                            controller.registerControlsInteraction();
                            onNext!();
                          },
                        ),
                    ],
                  ),
                ),
                Align(
                  alignment: Alignment.bottomCenter,
                  child: BottomControls(
                    position: controller.position,
                    duration: controller.duration,
                    isPlaying: controller.isPlaying,
                    isFullscreen: controller.isFullscreen,
                    isLocked: controller.isLocked,
                    formatTime: controller.formatDuration,
                    onSeekStart: controller.beginControlsInteraction,
                    onSeekChanged: (value) {
                      controller
                          .previewSeek(Duration(milliseconds: value.toInt()));
                    },
                    onSeekEnd: (value) {
                      controller.seek(Duration(milliseconds: value.toInt()));
                      controller.endControlsInteraction();
                    },
                    onTogglePlayPause: controller.togglePlayPause,
                    onToggleFullscreen: controller.toggleFullscreen,
                    onToggleLock: controller.toggleLock,
                    onTogglePip: onTogglePip,
                    showPipButton: showPipButton,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _SeekButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _SeekButton({
    required this.icon,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.45),
          shape: BoxShape.circle,
          border: Border.all(
            color: Colors.white.withValues(alpha: 0.35),
            width: 1.8,
          ),
        ),
        child: Icon(
          icon,
          color: Colors.white,
          size: 28,
        ),
      ),
    );
  }
}
