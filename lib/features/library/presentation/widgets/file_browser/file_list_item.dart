import 'package:flutter/material.dart';
import '../../../domain/entities/file_item.dart';
import '../../../domain/entities/video_file.dart';
import '../video/video_thumbnail.dart';

class FileListItem extends StatelessWidget {
  final FileItem item;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;
  final VoidCallback? onAddToFavorites;
  final bool isSelectionMode;
  final bool isSelected;
  final bool isFavorite;
  final VoidCallback? onSelectionToggle;

  const FileListItem({
    super.key,
    required this.item,
    required this.onTap,
    this.onLongPress,
    this.onAddToFavorites,
    this.isSelectionMode = false,
    this.isSelected = false,
    this.isFavorite = false,
    this.onSelectionToggle,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      onLongPress: () {
        if (onLongPress != null) {
          onLongPress!();
        } else if (item.isVideo && onAddToFavorites != null) {
          _showContextMenu(context);
        }
      },
      child: Container(
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
          border: Border.all(
            color: isSelected ? const Color(0xFF6366F1) : Colors.grey.shade100,
            width: isSelected ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            if (isSelectionMode)
              GestureDetector(
                onTap: onSelectionToggle,
                child: Container(
                  width: 24,
                  height: 24,
                  margin: const EdgeInsets.only(right: 12),
                  decoration: BoxDecoration(
                    color: isSelected ? const Color(0xFF6366F1) : Colors.white,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: isSelected
                          ? const Color(0xFF6366F1)
                          : Colors.grey.shade400,
                      width: 2,
                    ),
                  ),
                  child: isSelected
                      ? const Icon(Icons.check, size: 16, color: Colors.white)
                      : null,
                ),
              ),
            _buildIcon(),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF1E293B),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    item.isDirectory
                        ? (item.videoCount != null
                            ? '${item.videoCount} videos'
                            : 'Folder')
                        : item.formattedSize,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: Colors.grey.shade500,
                    ),
                  ),
                ],
              ),
            ),
            if (!isSelectionMode)
              item.isVideo
                  ? Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (isFavorite)
                          Padding(
                            padding: const EdgeInsets.only(right: 4),
                            child: Icon(
                              Icons.favorite,
                              color: Colors.red.shade400,
                              size: 18,
                            ),
                          ),
                        Icon(
                          Icons.play_circle_outline,
                          color: const Color(0xFF6366F1).withValues(alpha: 0.6),
                          size: 28,
                        ),
                      ],
                    )
                  : item.isDirectory
                      ? Icon(Icons.chevron_right, color: Colors.grey.shade400)
                      : const SizedBox.shrink(),
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
              leading: Icon(
                isFavorite ? Icons.favorite : Icons.favorite_border,
                color: Colors.red,
              ),
              title: Text(
                  isFavorite ? 'Remove from Favorites' : 'Add to Favorites'),
              onTap: () {
                Navigator.pop(context);
                onAddToFavorites?.call();
              },
            ),
            ListTile(
              leading: const Icon(Icons.info_outline),
              title: const Text('Video Info'),
              onTap: () {
                Navigator.pop(context);
                showDialog(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('File Information'),
                    content: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Name: ${item.name}'),
                        const SizedBox(height: 8),
                        Text('Size: ${item.formattedSize}'),
                        const SizedBox(height: 8),
                        Text('Path: ${item.path}'),
                      ],
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context),
                        child: const Text('Close'),
                      ),
                    ],
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIcon() {
    if (item.isDirectory) {
      return Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: const Color(0xFF6366F1).withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(14),
        ),
        child: const Icon(
          Icons.folder,
          color: Color(0xFF6366F1),
          size: 24,
        ),
      );
    }

    if (item.isVideo) {
      final video = VideoFile.fromFileItem(
        item,
        item.path.substring(0, item.path.lastIndexOf('/')),
      );
      return ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: SizedBox(
          width: 80,
          height: 56,
          child: VideoThumbnail(video: video, isFavorite: false),
        ),
      );
    }

    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: Colors.grey.shade200,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Icon(
        Icons.insert_drive_file,
        color: Colors.grey.shade600,
        size: 24,
      ),
    );
  }
}
