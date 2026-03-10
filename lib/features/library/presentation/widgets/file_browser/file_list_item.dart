import 'package:flutter/material.dart';
import '../../../domain/entities/file_item.dart';
import '../common/library_item_ui.dart';
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
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 24,
              offset: const Offset(0, 8),
            ),
          ],
          border: Border.all(
            color:
                isSelected ? const Color(0xFF2563EB) : const Color(0xFFE2E8F0),
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
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 8,
                    runSpacing: 6,
                    children: [
                      _MetaChip(
                        label: item.isDirectory
                            ? LibraryItemUi.folderVideoLabel(item.videoCount)
                            : item.formattedSize,
                        tint: item.isDirectory
                            ? const Color(0xFF2563EB)
                            : const Color(0xFFEA580C),
                      ),
                      if (item.isVideo)
                        _MetaChip(
                          label: item.extension.toUpperCase(),
                          tint: const Color(0xFF0F766E),
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    item.isDirectory
                        ? 'Folder'
                        : '${LibraryItemUi.parentFolderName(item.path)} • ${LibraryItemUi.relativeDate(item.modified)}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                      color: Color(0xFF64748B),
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
                        color: const Color(0xFFF8FAFC),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(
                        Icons.more_horiz,
                        color: Color(0xFF475569),
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
          color: Color(0xFF2563EB),
          size: 24,
        ),
      );
    }

    if (item.isVideo) {
      final video = LibraryItemUi.videoFromFileItem(item);
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

class _MetaChip extends StatelessWidget {
  final String label;
  final Color tint;

  const _MetaChip({required this.label, required this.tint});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: tint.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: tint,
        ),
      ),
    );
  }
}
