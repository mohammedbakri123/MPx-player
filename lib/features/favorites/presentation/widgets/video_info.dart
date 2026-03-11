import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
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
          _buildTitle(context),
          const SizedBox(height: 8),
          VideoMetadata(video: video),
        ],
      ),
    );
  }

  Widget _buildTitle(BuildContext context) {
    final theme = Theme.of(context);
    return Text(
      video.title,
      style: TextStyle(
        fontSize: 15,
        fontWeight: FontWeight.w700,
        color: theme.strongText,
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );
  }
}
