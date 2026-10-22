import 'package:flutter/material.dart';

import '../../../../core/utils/time_formatter.dart';
import '../../domain/entities/video_file.dart';

class VideoListItem extends StatelessWidget {
  const VideoListItem({super.key, required this.video});

  final VideoFile video;

  @override
  Widget build(BuildContext context) {
    final String folderName = video.folderPath.split('/').last;
    return ListTile(
      leading: const Icon(Icons.play_circle_outline, size: 40),
      title: Text(
        video.name,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text('$folderName • ${formatDuration(video.durationMs)}'),
      onTap: () {},
    );
  }
}
