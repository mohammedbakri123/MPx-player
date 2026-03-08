import 'package:flutter/material.dart';
import '../../../domain/entities/file_item.dart';
import '../../../domain/entities/video_file.dart';
import '../common/library_item_details_sheet.dart';
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
      onLongPress: onLongPress,
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
                  const SizedBox(height: 4),
                  Text(
                    item.isDirectory
                        ? 'Updated ${_relativeDate(item.modified)}'
                        : '${_folderName(item.path)} • ${item.extension.toUpperCase()} • ${_relativeDate(item.modified)}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                      color: Colors.grey.shade400,
                    ),
                  ),
                ],
              ),
            ),
            if (!isSelectionMode)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (item.isVideo && isFavorite)
                    Padding(
                      padding: const EdgeInsets.only(right: 4),
                      child: Icon(
                        Icons.favorite,
                        color: Colors.red.shade400,
                        size: 18,
                      ),
                    ),
                  GestureDetector(
                    onTap: () => _showDetails(context),
                    child: Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        Icons.more_horiz,
                        color: Colors.grey.shade600,
                        size: 18,
                      ),
                    ),
                  ),
                  if (item.isVideo)
                    Padding(
                      padding: const EdgeInsets.only(left: 8),
                      child: Icon(
                        Icons.play_circle_outline,
                        color: const Color(0xFF6366F1).withValues(alpha: 0.6),
                        size: 28,
                      ),
                    )
                  else if (item.isDirectory)
                    Padding(
                      padding: const EdgeInsets.only(left: 8),
                      child: Icon(
                        Icons.chevron_right,
                        color: Colors.grey.shade400,
                      ),
                    ),
                ],
              ),
          ],
        ),
      ),
    );
  }

  void _showDetails(BuildContext context) {
    LibraryItemDetailsSheet.showForItem(
      context,
      item,
      isFavorite: isFavorite,
      onToggleFavorite: onAddToFavorites,
    );
  }

  String _relativeDate(DateTime date) {
    final diff = DateTime.now().difference(date);
    if (diff.inDays == 0) return 'today';
    if (diff.inDays == 1) return 'yesterday';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    if (diff.inDays < 30) return '${(diff.inDays / 7).floor()}w ago';
    if (diff.inDays < 365) return '${(diff.inDays / 30).floor()}mo ago';
    return '${(diff.inDays / 365).floor()}y ago';
  }

  String _folderName(String path) {
    final lastSeparator = path.lastIndexOf('/');
    if (lastSeparator <= 0) {
      return '/';
    }
    return path.substring(0, lastSeparator).split('/').last;
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
