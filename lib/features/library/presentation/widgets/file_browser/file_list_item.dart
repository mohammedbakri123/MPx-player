import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
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
    final theme = Theme.of(context);
    final accent = theme.colorScheme.primary;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        borderRadius: BorderRadius.circular(24),
        child: Ink(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: theme.elevatedSurface,
            borderRadius: BorderRadius.circular(24),
            boxShadow: [
              BoxShadow(
                color: theme.cardShadow,
                blurRadius: 24,
                offset: const Offset(0, 8),
              ),
            ],
            border: Border.all(
              color: isSelected ? accent : theme.softBorder,
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
                      color: isSelected ? accent : theme.elevatedSurface,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: isSelected ? accent : theme.faintText,
                        width: 2,
                      ),
                    ),
                    child: isSelected
                        ? const Icon(Icons.check, size: 16, color: Colors.white)
                        : null,
                  ),
                ),
              _buildIcon(context),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: theme.strongText,
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
                              ? theme.colorScheme.primary
                              : theme.colorScheme.secondary,
                        ),
                        if (item.isVideo)
                          _MetaChip(
                            label: item.extension.toUpperCase(),
                            tint: theme.strongText,
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
                      ).copyWith(color: theme.mutedText),
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
                          color: theme.subtleSurface,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(
                          Icons.more_horiz,
                          color: theme.mutedText,
                          size: 18,
                        ),
                      ),
                    ),
                    if (item.isVideo)
                      Padding(
                        padding: const EdgeInsets.only(left: 8),
                        child: Icon(
                          Icons.play_circle_outline,
                          color: accent.withValues(alpha: 0.75),
                          size: 28,
                        ),
                      )
                    else if (item.isDirectory)
                      Padding(
                        padding: const EdgeInsets.only(left: 8),
                        child: Icon(
                          Icons.chevron_right,
                          color: theme.faintText,
                        ),
                      ),
                  ],
                ),
            ],
          ),
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

  Widget _buildIcon(BuildContext context) {
    final theme = Theme.of(context);
    if (item.isDirectory) {
      return Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              theme.colorScheme.primary.withValues(alpha: 0.20),
              theme.colorScheme.secondary.withValues(alpha: 0.14),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Icon(
          Icons.folder_rounded,
          color: theme.colorScheme.primary,
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
        color: theme.subtleSurface,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Icon(
        Icons.insert_drive_file,
        color: theme.mutedText,
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
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: tint.withValues(alpha: theme.isDarkMode ? 0.18 : 0.1),
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
