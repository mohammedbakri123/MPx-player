import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';
import '../../domain/repositories/player_repository.dart';
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
                    onShowAudioTracks: controller.audioTracks.length > 1
                        ? () => _showAudioTrackSheet(context)
                        : null,
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
                    repeatMode: controller.repeatMode,
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
                    onToggleRepeat: controller.cycleRepeatMode,
                    onShowSpeedSheet: (context, speed) {
                      controller.setSpeed(speed);
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _showAudioTrackSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _AudioTrackSheet(
        audioTracks: controller.audioTracks,
        currentIndex: controller.currentAudioTrackIndex,
        onSelected: (index) {
          controller.setAudioTrack(index);
          Navigator.pop(context);
        },
      ),
    );
  }
}

class _AudioTrackSheet extends StatelessWidget {
  final List<AudioTrackInfo> audioTracks;
  final int currentIndex;
  final ValueChanged<int> onSelected;

  const _AudioTrackSheet({
    required this.audioTracks,
    required this.currentIndex,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF1E1E1E),
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              margin: const EdgeInsets.only(top: 12),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade600,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Audio Track',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '${audioTracks.length} tracks',
                    style: TextStyle(
                      color: Colors.grey.shade400,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
            ),
            const Divider(color: Colors.grey, height: 1),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 250),
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: audioTracks.length,
                itemBuilder: (context, index) {
                  final track = audioTracks[index];
                  final isSelected = index == currentIndex;

                  return ListTile(
                    title: Text(
                      track.title ?? 'Track ${index + 1}',
                      style: TextStyle(
                        color: isSelected ? Colors.blue : Colors.white,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w600 : FontWeight.normal,
                      ),
                    ),
                    subtitle: track.language != null
                        ? Text(
                            track.language!,
                            style: TextStyle(
                              color: Colors.grey.shade400,
                              fontSize: 12,
                            ),
                          )
                        : null,
                    trailing: isSelected
                        ? const Icon(Icons.check, color: Colors.blue)
                        : null,
                    onTap: () => onSelected(index),
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
