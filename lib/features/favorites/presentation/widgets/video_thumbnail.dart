import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import 'video_thumbnail_placeholder.dart';
import 'video_thumbnail_overlay.dart';

class VideoThumbnail extends StatelessWidget {
  final VideoFile video;

  const VideoThumbnail({super.key, required this.video});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Stack(
          fit: StackFit.expand,
          children: [
            const VideoThumbnailPlaceholder(),
            VideoThumbnailBadge(
                text: video.resolution, isTop: true, isLeft: true),
            VideoThumbnailBadge(
                text: video.formattedSize, isTop: false, isLeft: false),
            const VideoPlayOverlay(),
          ],
        ),
      ),
    );
  }
}
