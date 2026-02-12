import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import 'video_thumbnail.dart';
import 'video_info.dart';
import 'video_action_button.dart';

class VideoListItem extends StatelessWidget {
  final VideoFile video;
  final VoidCallback onTap;
  final bool isLoading;

  const VideoListItem({
    super.key,
    required this.video,
    required this.onTap,
    this.isLoading = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isLoading ? null : onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(12),
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
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            VideoThumbnail(thumbnailPath: video.thumbnailPath),
            const SizedBox(width: 16),
            VideoInfo(video: video),
            VideoActionButton(isLoading: isLoading),
          ],
        ),
      ),
    );
  }
}
