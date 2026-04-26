import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../../domain/entities/file_item.dart';
import '../common/library_item_ui.dart';
import '../common/library_item_details_sheet.dart';
import '../video/lazy_thumbnail.dart';

// Spacing constants
const _kHorizontalPadding = 12.0;
const _kIconContentSpacing = 16.0;
const _kTitleMetaSpacing = 4.0;
const _kMetaSubtitleSpacing = 8.0;
const _kSelectionMarginRight = 12.0;
const _kMetaChipHorizontalPadding = 8.0;
const _kMetaChipVerticalPadding = 4.0;

// Size constants
const _kBorderRadius = 24.0;
const _kItemBorderRadius = 24.0;
const _kSelectionCircleSize = 24.0;
const _kSelectionCheckSize = 16.0;
const _kFolderIconSize = 48.0;
const _kFolderIconInnerSize = 24.0;
const _kVideoThumbnailWidth = 80.0;
const _kVideoThumbnailHeight = 56.0;
const _kThumbnailBorderRadius = 14.0;
const _kActionButtonSize = 32.0;
const _kActionButtonBorderRadius = 10.0;
const _kFavoriteIconSize = 18.0;
const _kMoreIconSize = 18.0;
const _kCompactThumbnailIconSize = 24.0;
const _kLargeThumbnailIconSize = 32.0;
const _kMetaChipBorderRadius = 999.0;

// Text size constants
const _kTitleFontSize = 15.0;
const _kSubtitleFontSize = 11.0;
const _kMetaFontSize = 11.0;

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
        borderRadius: BorderRadius.circular(_kBorderRadius),
        child: Ink(
          padding: const EdgeInsets.all(_kHorizontalPadding),
          decoration: BoxDecoration(
            color: theme.elevatedSurface,
            borderRadius: BorderRadius.circular(_kItemBorderRadius),
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
              _buildSelectionIndicator(context, theme, accent),
              _buildIcon(context),
              const SizedBox(width: _kIconContentSpacing),
              _buildContentColumn(context, theme),
              _buildTrailingActions(context, theme),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSelectionIndicator(
      BuildContext context, ThemeData theme, Color accent) {
    if (!isSelectionMode) return const SizedBox.shrink();

    return GestureDetector(
      onTap: onSelectionToggle,
      child: Container(
        width: _kSelectionCircleSize,
        height: _kSelectionCircleSize,
        margin: const EdgeInsets.only(right: _kSelectionMarginRight),
        decoration: BoxDecoration(
          color: isSelected ? accent : theme.elevatedSurface,
          shape: BoxShape.circle,
          border: Border.all(
            color: isSelected ? accent : theme.faintText,
            width: 2,
          ),
        ),
        child: isSelected
            ? const Icon(Icons.check,
                size: _kSelectionCheckSize, color: Colors.white)
            : null,
      ),
    );
  }

  Widget _buildContentColumn(BuildContext context, ThemeData theme) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Flexible(
            child: Text(
              item.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: _kTitleFontSize,
                fontWeight: FontWeight.w700,
                color: theme.strongText,
              ),
            ),
          ),
          const SizedBox(height: _kTitleMetaSpacing),
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
          const SizedBox(height: _kMetaSubtitleSpacing),
          Flexible(
            child: Text(
              item.isDirectory
                  ? 'Folder'
                  : '${LibraryItemUi.parentFolderName(item.path)} • ${LibraryItemUi.relativeDate(item.modified)}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: _kSubtitleFontSize,
                fontWeight: FontWeight.w500,
              ).copyWith(color: theme.mutedText),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTrailingActions(BuildContext context, ThemeData theme) {
    if (isSelectionMode) return const SizedBox.shrink();

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (item.isVideo && isFavorite)
          Padding(
            padding: const EdgeInsets.only(right: 4),
            child: Icon(
              Icons.favorite,
              color: Colors.red.shade400,
              size: _kFavoriteIconSize,
            ),
          ),
        GestureDetector(
          onTap: () => _showDetails(context),
          child: Container(
            width: _kActionButtonSize,
            height: _kActionButtonSize,
            decoration: BoxDecoration(
              color: theme.subtleSurface,
              borderRadius: BorderRadius.circular(_kActionButtonBorderRadius),
            ),
            child: Icon(
              Icons.more_horiz,
              color: theme.mutedText,
              size: _kMoreIconSize,
            ),
          ),
        ),
      ],
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
        width: _kFolderIconSize,
        height: _kFolderIconSize,
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              theme.colorScheme.primary.withValues(alpha: 0.20),
              theme.colorScheme.secondary.withValues(alpha: 0.14),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(_kThumbnailBorderRadius),
        ),
        child: Icon(
          Icons.folder_rounded,
          color: theme.colorScheme.primary,
          size: _kFolderIconInnerSize,
        ),
      );
    }

    if (item.isVideo) {
      final video = LibraryItemUi.videoFromFileItem(item);
      return ClipRRect(
        borderRadius: BorderRadius.circular(_kThumbnailBorderRadius),
        child: SizedBox(
          width: _kVideoThumbnailWidth,
          height: _kVideoThumbnailHeight,
          child: RepaintBoundary(
            child: LazyThumbnail(
              video: video,
              placeholder: const _ThumbnailPlaceholder(isCompact: true),
            ),
          ),
        ),
      );
    }

    return Container(
      width: _kFolderIconSize,
      height: _kFolderIconSize,
      decoration: BoxDecoration(
        color: theme.subtleSurface,
        borderRadius: BorderRadius.circular(_kThumbnailBorderRadius),
      ),
      child: Icon(
        Icons.insert_drive_file,
        color: theme.mutedText,
        size: _kFolderIconInnerSize,
      ),
    );
  }
}

class _ThumbnailPlaceholder extends StatelessWidget {
  final bool isCompact;

  const _ThumbnailPlaceholder({this.isCompact = false});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF475569), Color(0xFF0F172A)],
        ),
      ),
      child: Center(
        child: Icon(
          Icons.play_circle_outline_rounded,
          size:
              isCompact ? _kCompactThumbnailIconSize : _kLargeThumbnailIconSize,
          color: Colors.white24,
        ),
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
      padding: const EdgeInsets.symmetric(
        horizontal: _kMetaChipHorizontalPadding,
        vertical: _kMetaChipVerticalPadding,
      ),
      decoration: BoxDecoration(
        color: tint.withValues(alpha: theme.isDarkMode ? 0.18 : 0.1),
        borderRadius: BorderRadius.circular(_kMetaChipBorderRadius),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: _kMetaFontSize,
          fontWeight: FontWeight.w700,
          color: tint,
        ),
      ),
    );
  }
}
