import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import '../common/library_item_details_sheet.dart';
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
              video: video,
              isFavorite: isFavorite,
            ),
            const SizedBox(width: 16),
            VideoInfo(video: video),
            const SizedBox(width: 12),
            GestureDetector(
              onTap: () => _showContextMenu(context),
              child: Container(
                width: 34,
                height: 34,
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  Icons.more_horiz,
                  color: Colors.grey.shade600,
                  size: 18,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showContextMenu(BuildContext context) {
    LibraryItemDetailsSheet.showForVideo(
      context,
      video,
      isFavorite: isFavorite,
      onToggleFavorite: onAddToFavorites,
    );
  }
}
