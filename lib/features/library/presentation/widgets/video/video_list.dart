import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import 'video_list_item.dart';

class VideoList extends StatelessWidget {
  final List<VideoFile> videos;
  final VoidCallback onRefresh;
  final Function(VideoFile) onVideoTap;
  final bool isNavigating;

  const VideoList({
    super.key,
    required this.videos,
    required this.onRefresh,
    required this.onVideoTap,
    required this.isNavigating,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async => onRefresh(),
      child: ListView.builder(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        itemCount: videos.length,
        itemBuilder: (context, index) {
          return VideoListItem(
            video: videos[index],
            onTap: () => onVideoTap(videos[index]),
            isLoading: isNavigating,
          );
        },
      ),
    );
  }
}
