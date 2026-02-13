import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import 'favorites_empty_state.dart';
import 'favorites_loading_state.dart';
import 'favorites_video_list.dart';

class FavoritesContent extends StatelessWidget {
  final List<VideoFile> videos;
  final bool isLoading;
  final Future<void> Function() onRefresh;
  final void Function(VideoFile) onVideoTap;
  final bool isNavigating;
  final VoidCallback? onTryDemo;

  const FavoritesContent({
    super.key,
    required this.videos,
    required this.isLoading,
    required this.onRefresh,
    required this.onVideoTap,
    required this.isNavigating,
    this.onTryDemo,
  });

  @override
  Widget build(BuildContext context) {
    if (isLoading) return const FavoritesLoadingState();
    if (videos.isEmpty) return FavoritesEmptyState(onTryDemo: onTryDemo);
    return FavoritesVideoList(
      videos: videos,
      onRefresh: onRefresh,
      onVideoTap: onVideoTap,
      isNavigating: isNavigating,
    );
  }
}
