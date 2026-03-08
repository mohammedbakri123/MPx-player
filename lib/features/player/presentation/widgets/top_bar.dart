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

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.42),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          _MinimalIconButton(
            icon: Icons.arrow_back,
            onPressed: onBack,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          const SizedBox(width: 10),
          _MinimalIconButton(
            icon: Icons.settings_outlined,
            onPressed: onSettings,
          ),
        ],
      ),
    );
  }
}

class _MinimalIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onPressed;

  const _MinimalIconButton({
    required this.icon,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
      ),
      child: IconButton(
        onPressed: onPressed,
        padding: EdgeInsets.zero,
        iconSize: 20,
        splashRadius: 20,
        icon: Icon(icon, color: Colors.white),
      ),
    );
  }
}
