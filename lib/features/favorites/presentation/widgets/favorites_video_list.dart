import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import 'video_card.dart';

class FavoritesVideoList extends StatelessWidget {
  final List<VideoFile> videos;
  final Future<void> Function() onRefresh;
  final void Function(VideoFile) onVideoTap;
  final void Function(VideoFile) onRemove;
  final bool isNavigating;

  const FavoritesVideoList({
    super.key,
    required this.videos,
    required this.onRefresh,
    required this.onVideoTap,
    required this.onRemove,
    required this.isNavigating,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.builder(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        itemCount: videos.length,
        itemBuilder: (context, index) {
          return VideoCard(
            video: videos[index],
            onTap: () => onVideoTap(videos[index]),
            onRemove: () => onRemove(videos[index]),
            isLoading: isNavigating,
          );
        },
      ),
    );
  }
}
