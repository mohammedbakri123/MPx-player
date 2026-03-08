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
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.45),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          IconButton(
            onPressed: onBack,
            iconSize: 22,
            splashRadius: 22,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
            icon: const Icon(Icons.arrow_back, color: Colors.white),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 17,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.2,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(width: 6),
          SizedBox(
            height: 36,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              reverse: true,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  GestureDetector(
                    onTap: onToggleAspectRatio,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      margin: const EdgeInsets.only(right: 4),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.16),
                        borderRadius: BorderRadius.circular(6),
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
                    _TopBarIconButton(
                      icon: Icons.audiotrack,
                      onPressed: onShowAudioTracks,
                    ),
                  _TopBarIconButton(
                    icon: isLocked ? Icons.lock : Icons.lock_open,
                    color: isLocked ? Colors.blue : Colors.white70,
                    onPressed: onToggleLock,
                  ),
                  _TopBarIconButton(
                    icon:
                        isFullscreen ? Icons.fullscreen_exit : Icons.fullscreen,
                    color: Colors.white,
                    onPressed: onToggleFullscreen,
                  ),
                  SizedBox(
                    width: 40,
                    height: 36,
                    child: Stack(
                      children: [
                        Align(
                          alignment: Alignment.center,
                          child: IconButton(
                            onPressed: onSubtitleSettings,
                            iconSize: 20,
                            splashRadius: 20,
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(
                                minWidth: 36, minHeight: 36),
                            icon: const Icon(Icons.subtitles,
                                color: Colors.white70),
                          ),
                        ),
                        if (subtitlesEnabled)
                          Positioned(
                            right: 6,
                            top: 6,
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
                  ),
                  _TopBarIconButton(
                    icon: Icons.settings,
                    color: Colors.white70,
                    onPressed: onSettings,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _TopBarIconButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final VoidCallback? onPressed;

  const _TopBarIconButton({
    required this.icon,
    this.color = Colors.white,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      onPressed: onPressed,
      iconSize: 20,
      splashRadius: 20,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      icon: Icon(icon, color: color),
    );
  }
}
