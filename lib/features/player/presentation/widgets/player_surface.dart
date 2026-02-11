import 'package:flutter/material.dart';
import 'package:media_kit_video/media_kit_video.dart';

class PlayerSurface extends StatelessWidget {
  final VideoController controller;
  final double subtitleFontSize;
  final Color subtitleColor;
  final bool subtitleHasBackground;

  const PlayerSurface({
    super.key,
    required this.controller,
    required this.subtitleFontSize,
    required this.subtitleColor,
    required this.subtitleHasBackground,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Video(
          controller: controller,
          controls: null,
          subtitleViewConfiguration: SubtitleViewConfiguration(
            style: TextStyle(
              fontSize: subtitleFontSize,
              color: subtitleColor,
              fontWeight: FontWeight.w500,
              backgroundColor: subtitleHasBackground
                  ? Colors.black.withValues(alpha: 0.7)
                  : Colors.transparent,
              shadows: !subtitleHasBackground
                  ? const [
                      Shadow(
                        blurRadius: 4,
                        color: Colors.black,
                        offset: Offset(2, 2),
                      ),
                    ]
                  : null,
            ),
            textAlign: TextAlign.center,
            padding: const EdgeInsets.all(24.0),
          ),
        ),
      ),
    );
  }
}
