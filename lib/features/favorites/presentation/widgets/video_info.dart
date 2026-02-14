import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../../library/presentation/widgets/video/video_metadata.dart';

class VideoInfo extends StatelessWidget {
  final VideoFile video;

  const VideoInfo({super.key, required this.video});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildTitle(),
          const SizedBox(height: 8),
          VideoMetadata(video: video),
        ],
      ),
    );
  }

  Widget _buildTitle() {
    return Text(
      video.title,
      style: const TextStyle(
        fontSize: 15,
        fontWeight: FontWeight.w700,
        color: Color(0xFF1E293B),
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );
  }
}
