import 'package:flutter/material.dart';

class TopBar extends StatelessWidget {
  final String title;
  final bool isFullscreen;
  final bool subtitlesEnabled;
  final VoidCallback onBack;
  final VoidCallback onToggleFullscreen;
  final VoidCallback onSubtitleSettings;
  final VoidCallback onSettings;

  const TopBar({
    super.key,
    required this.title,
    required this.isFullscreen,
    required this.subtitlesEnabled,
    required this.onBack,
    required this.onToggleFullscreen,
    required this.onSubtitleSettings,
    required this.onSettings,
  });

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
