import 'package:flutter/material.dart';

class VideoThumbnailPlaceholder extends StatelessWidget {
  const VideoThumbnailPlaceholder({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.grey.shade200,
      child: const Icon(Icons.video_file, size: 48, color: Colors.grey),
    );
  }
}
