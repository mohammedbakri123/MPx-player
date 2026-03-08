import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import 'video_metadata.dart';

class VideoInfo extends StatelessWidget {
  final VideoFile video;

  const VideoInfo({
    super.key,
    required this.video,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            video.folderName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: Color(0xFFEA580C),
              letterSpacing: 0.2,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            video.title,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: Color(0xFF1E293B),
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 6),
          VideoMetadata(video: video),
          const SizedBox(height: 8),
          Text(
            video.path,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: Color(0xFF64748B),
            ),
          ),
        ],
      ),
    );
  }
}
