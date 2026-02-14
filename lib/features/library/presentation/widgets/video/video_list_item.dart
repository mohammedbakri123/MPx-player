import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import 'video_thumbnail.dart';
import 'video_info.dart';
// import 'video_action_button.dart';

class VideoListItem extends StatelessWidget {
  final VideoFile video;
  final VoidCallback onTap;
  final VoidCallback onAddToFavorites;
  final bool isLoading;
  final bool isFavorite;

  const VideoListItem({
    super.key,
    required this.video,
    required this.onTap,
    required this.onAddToFavorites,
    this.isLoading = false,
    this.isFavorite = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isLoading ? null : onTap,
      onLongPress: () => _showContextMenu(context),
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
            VideoThumbnail(
              videoPath: video.path,
              existingThumbnailPath: video.thumbnailPath,
              isFavorite: isFavorite,
            ),
            const SizedBox(width: 16),
            VideoInfo(video: video),
            // VideoActionButton(isLoading: isLoading),
          ],
        ),
      ),
    );
  }

  void _showContextMenu(BuildContext context) {
    showModalBottomSheet(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: Icon(isFavorite ? Icons.favorite : Icons.favorite_border,
                  color: Colors.red),
              title: Text(
                  isFavorite ? 'Remove from Favorites' : 'Add to Favorites'),
              onTap: () {
                Navigator.pop(context);
                onAddToFavorites();
              },
            ),
            ListTile(
              leading: const Icon(Icons.info_outline),
              title: const Text('Video Info'),
              onTap: () => Navigator.pop(context),
            ),
          ],
        ),
      ),
    );
  }
}
