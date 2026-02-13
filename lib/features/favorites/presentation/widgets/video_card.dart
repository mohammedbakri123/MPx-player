import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import 'video_thumbnail.dart';
import 'video_info.dart';

class VideoCard extends StatelessWidget {
  final VideoFile video;
  final VoidCallback? onTap;
  final bool isLoading;

  const VideoCard({
    super.key,
    required this.video,
    this.onTap,
    this.isLoading = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isLoading ? null : onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 20,
              offset: const Offset(0, 4),
            ),
          ],
          border: Border.all(color: Colors.grey.shade100),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            VideoThumbnail(video: video),
            VideoInfo(video: video),
          ],
        ),
      ),
    );
  }
}
