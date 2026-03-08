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
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 24,
              offset: const Offset(0, 8),
            ),
          ],
          border: Border.all(color: const Color(0xFFE2E8F0)),
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
            Column(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                if (isFavorite)
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFF1F2),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.favorite_rounded,
                      color: Color(0xFFE11D48),
                      size: 16,
                    ),
                  )
                else
                  const SizedBox(width: 32, height: 32),
                const SizedBox(height: 12),
                GestureDetector(
                  onTap: () => _showContextMenu(context),
                  child: Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: const Color(0xFFF8FAFC),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.more_horiz,
                      color: Color(0xFF475569),
                      size: 18,
                    ),
                  ),
                ),
              ],
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
