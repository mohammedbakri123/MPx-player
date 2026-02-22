import 'package:flutter/material.dart';
import '../../controller/player_state.dart';
import '../../domain/repositories/player_repository.dart';

class TopBar extends StatelessWidget {
  final String title;
  final bool isFullscreen;
  final bool isLocked;
  final bool subtitlesEnabled;
  final AspectRatioMode aspectRatioMode;
  final List<AudioTrackInfo> audioTracks;
  final int currentAudioTrackIndex;
  final VoidCallback onBack;
  final VoidCallback onToggleLock;
  final VoidCallback onToggleFullscreen;
  final VoidCallback onToggleAspectRatio;
  final VoidCallback onSubtitleSettings;
  final VoidCallback onSettings;
  final VoidCallback? onShowAudioTracks;

  const TopBar({
    super.key,
    required this.title,
    required this.isFullscreen,
    required this.isLocked,
    required this.subtitlesEnabled,
    required this.aspectRatioMode,
    required this.audioTracks,
    required this.currentAudioTrackIndex,
    required this.onBack,
    required this.onToggleLock,
    required this.onToggleFullscreen,
    required this.onToggleAspectRatio,
    required this.onSubtitleSettings,
    required this.onSettings,
    this.onShowAudioTracks,
  });

  String _getAspectRatioLabel(AspectRatioMode mode) {
    switch (mode) {
      case AspectRatioMode.fit:
        return 'Fit';
      case AspectRatioMode.fill:
        return 'Fill';
      case AspectRatioMode.stretch:
        return 'Stretch';
      case AspectRatioMode.ratio16x9:
        return '16:9';
      case AspectRatioMode.ratio4x3:
        return '4:3';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        IconButton(
          onPressed: onBack,
          icon: const Icon(Icons.arrow_back, color: Colors.white),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.w600,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        GestureDetector(
          onTap: onToggleAspectRatio,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            margin: const EdgeInsets.only(right: 4),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(
              _getAspectRatioLabel(aspectRatioMode),
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ),
        if (audioTracks.length > 1)
          IconButton(
            onPressed: onShowAudioTracks,
            icon: const Icon(Icons.audiotrack, color: Colors.white),
          ),
        IconButton(
          onPressed: onToggleLock,
          icon: Icon(
            isLocked ? Icons.lock : Icons.lock_open,
            color: isLocked ? Colors.blue : Colors.white,
          ),
        ),
        IconButton(
          onPressed: onToggleFullscreen,
          icon: Icon(
            isFullscreen ? Icons.fullscreen_exit : Icons.fullscreen,
            color: Colors.white,
          ),
        ),
        Stack(
          children: [
            IconButton(
              onPressed: onSubtitleSettings,
              icon: const Icon(Icons.subtitles, color: Colors.white),
            ),
            if (subtitlesEnabled)
              Positioned(
                right: 8,
                top: 8,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: Colors.blue,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
          ],
        ),
        IconButton(
          onPressed: onSettings,
          icon: const Icon(Icons.settings, color: Colors.white),
        ),
      ],
    );
  }
}
