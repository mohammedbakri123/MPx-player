import 'package:flutter/material.dart';

class VideoThumbnailBadge extends StatelessWidget {
  final String text;
  final bool isTop;
  final bool isLeft;

  const VideoThumbnailBadge({
    super.key,
    required this.text,
    this.isTop = true,
    this.isLeft = true,
  });

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: isTop ? 12 : null,
      bottom: isTop ? null : 12,
      left: isLeft ? 12 : null,
      right: isLeft ? null : 12,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.white.withValues(alpha: 0.2)),
        ),
        child: Text(
          text,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 10,
            fontWeight: FontWeight.bold,
            letterSpacing: 0.5,
          ),
        ),
      ),
    );
  }
}

class VideoPlayOverlay extends StatelessWidget {
  const VideoPlayOverlay({super.key});

  @override
  Widget build(BuildContext context) {
    return Positioned.fill(
      child: Container(
        color: Colors.black.withValues(alpha: 0.1),
        child: Center(
          child: Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.2),
              shape: BoxShape.circle,
              border: Border.all(
                  color: Colors.white.withValues(alpha: 0.3), width: 2),
            ),
            child: const Icon(Icons.play_arrow, color: Colors.white, size: 32),
          ),
        ),
      ),
    );
  }
}
