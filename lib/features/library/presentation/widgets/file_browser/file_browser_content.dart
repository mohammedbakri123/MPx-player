import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../../controller/file_browser_controller.dart';
import '../../../domain/entities/file_item.dart';
import '../common/library_item_ui.dart';
import '../common/library_item_details_sheet.dart';
import '../home/home_empty_state.dart';
import '../home/home_error_state.dart';
import '../home/home_skeleton_loader.dart';
import '../video/lazy_thumbnail.dart';
import 'file_list_item.dart';

class FileBrowserContent extends StatelessWidget {
  static const double _bottomClearance = 128;

  final FileBrowserController controller;
  final void Function(String path) onVideoTap;
  final void Function(String path) onFolderTap;
  final void Function(String path)? onAddToFavorites;
  final Set<String> favoriteIds;
  final ScrollController? scrollController;

  const FileBrowserContent({
    super.key,
    required this.controller,
    required this.onVideoTap,
    required this.onFolderTap,
    this.onAddToFavorites,
    this.favoriteIds = const {},
    this.scrollController,
  });

  bool _isFavorite(String path) {
    return favoriteIds.contains(path.hashCode.toString());
  }

  @override
  Widget build(BuildContext context) {
    final items = controller.items;

    // Show skeleton whenever loading an empty list
    if (controller.isLoading && items.isEmpty) {
      return HomeSkeletonLoader(isGridView: controller.isGridView);
    }

    if (controller.error != null) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: HomeErrorState(
          errorMessage: controller.error,
          onRetry: controller.refresh,
        ),
      );
    }

    if (items.isEmpty) {
      return RefreshIndicator(
        onRefresh: () async => controller.refresh(silent: true),
        displacement: 20,
        edgeOffset: 20,
        child: ListView(
          padding: const EdgeInsets.only(bottom: _bottomClearance),
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(
              height: MediaQuery.of(context).size.height * 0.7,
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: HomeEmptyState(onTryRefresh: controller.refresh),
              ),
            ),
          ],
        ),
      );
    }

    final content = controller.isGridView
        ? _buildGridView(items)
        : RefreshIndicator(
            onRefresh: () => controller.refresh(silent: true),
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, _bottomClearance),
              controller: scrollController,
              physics: const AlwaysScrollableScrollPhysics(),
              itemCount: items.length,
              separatorBuilder: (context, index) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                final item = items[index];
                final isSelected = controller.isSelected(item.path);

                return FileListItem(
                  item: item,
                  isSelectionMode: controller.isSelectionMode,
                  isSelected: isSelected,
                  isFavorite: item.isVideo ? _isFavorite(item.path) : false,
                  onTap: () {
                    if (controller.isSelectionMode) {
                      controller.toggleSelection(item.path);
                    } else if (item.isDirectory) {
                      onFolderTap(item.path);
                    } else if (item.isVideo) {
                      onVideoTap(item.path);
                    }
                  },
                  onLongPress: () {
                    controller.enterSelectionMode(item.path);
                  },
                  onSelectionToggle: () =>
                      controller.toggleSelection(item.path),
                  onAddToFavorites: item.isVideo && onAddToFavorites != null
                      ? () => onAddToFavorites!(item.path)
                      : null,
                );
              },
            ),
          );

    final theme = Theme.of(context);

    return Stack(
      children: [
        content,
        // Only show refresh indicator after initial load is complete
        if (controller.isLoading && controller.isInitialized)
          Positioned(
            top: 10,
            left: 24,
            right: 24,
            child: IgnorePointer(
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  color: theme.elevatedSurface,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.06),
                      blurRadius: 18,
                      offset: const Offset(0, 6),
                    ),
                  ],
                ),
                child: Row(
                  children: [
                    SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.2,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      'Refreshing library...',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: theme.faintText,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildGridView(List<FileItem> items) {
    return RefreshIndicator(
      onRefresh: () => controller.refresh(silent: true),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;
          final estimatedCount = ((width + 14) / 190).floor();
          final crossAxisCount = estimatedCount < 2
              ? 2
              : estimatedCount > 4
                  ? 4
                  : estimatedCount;
          final cardWidth =
              (width - 48 - ((crossAxisCount - 1) * 14)) / crossAxisCount;
          final previewHeight = cardWidth.clamp(132.0, 180.0);
          final mainAxisExtent = previewHeight + 108;

          return GridView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            controller: scrollController,
            padding: const EdgeInsets.fromLTRB(24, 0, 24, _bottomClearance),
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: crossAxisCount,
              crossAxisSpacing: 14,
              mainAxisSpacing: 14,
              mainAxisExtent: mainAxisExtent,
            ),
            itemCount: items.length,
            itemBuilder: (context, index) {
              final item = items[index];
              final isSelected = controller.isSelected(item.path);

              return _GridItem(
                item: item,
                isSelectionMode: controller.isSelectionMode,
                isSelected: isSelected,
                isFavorite: item.isVideo ? _isFavorite(item.path) : false,
                previewHeight: previewHeight,
                onTap: () {
                  if (controller.isSelectionMode) {
                    controller.toggleSelection(item.path);
                  } else if (item.isDirectory) {
                    onFolderTap(item.path);
                  } else if (item.isVideo) {
                    onVideoTap(item.path);
                  }
                },
                onLongPress: () {
                  controller.enterSelectionMode(item.path);
                },
                onSelectionToggle: () => controller.toggleSelection(item.path),
                onAddToFavorites: item.isVideo && onAddToFavorites != null
                    ? () => onAddToFavorites!(item.path)
                    : null,
              );
            },
          );
        },
      ),
    );
  }
}

class _GridItem extends StatelessWidget {
  final FileItem item;
  final bool isSelectionMode;
  final bool isSelected;
  final bool isFavorite;
  final double previewHeight;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onSelectionToggle;
  final VoidCallback? onAddToFavorites;

  const _GridItem({
    required this.item,
    required this.isSelectionMode,
    required this.isSelected,
    required this.isFavorite,
    required this.previewHeight,
    required this.onTap,
    required this.onLongPress,
    required this.onSelectionToggle,
    this.onAddToFavorites,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: theme.elevatedSurface,
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Ink(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: isSelected ? theme.colorScheme.primary : theme.softBorder,
              width: isSelected ? 2 : 1,
            ),
            boxShadow: [
              BoxShadow(
                color: theme.cardShadow,
                blurRadius: 20,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Stack(
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildPreview(context, item),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.name,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                              color: theme.strongText,
                              height: 1.25,
                            ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            item.isDirectory
                                ? 'Updated ${LibraryItemUi.relativeDate(item.modified)}'
                                : '${LibraryItemUi.parentFolderName(item.path)} • ${LibraryItemUi.relativeDate(item.modified)}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w500,
                              color: theme.mutedText,
                            ),
                          ),
                          const Spacer(),
                          Row(
                            children: [
                              Expanded(
                                child: _GridMetaChip(
                                  label: item.isDirectory
                                      ? LibraryItemUi.folderVideoLabel(
                                          item.videoCount,
                                        )
                                      : item.formattedSize,
                                  color: item.isDirectory
                                      ? const Color(0xFF2563EB)
                                      : const Color(0xFFEA580C),
                                ),
                              ),
                              if (item.isVideo) ...[
                                const SizedBox(width: 6),
                                _GridMetaChip(
                                  label: item.extension.toUpperCase(),
                                  color: theme.mutedText,
                                ),
                              ],
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              if (!isSelectionMode)
                Positioned(
                  top: 8,
                  right: 8,
                  child: Material(
                    color: Colors.black.withValues(alpha: 0.45),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: IconButton(
                      onPressed: () => _showDetails(context),
                      icon: const Icon(
                        Icons.more_horiz,
                        size: 18,
                        color: Colors.white,
                      ),
                      constraints: const BoxConstraints.tightFor(
                        width: 32,
                        height: 32,
                      ),
                      padding: EdgeInsets.zero,
                      splashRadius: 18,
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                ),
              if (isSelectionMode)
                Positioned(
                  top: 8,
                  right: 8,
                  child: GestureDetector(
                    onTap: onSelectionToggle,
                    child: Container(
                      width: 24,
                      height: 24,
                      decoration: BoxDecoration(
                        color: isSelected
                            ? theme.colorScheme.primary
                            : theme.elevatedSurface,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: isSelected
                              ? theme.colorScheme.primary
                              : theme.faintText,
                          width: 2,
                        ),
                      ),
                      child: isSelected
                          ? const Icon(Icons.check,
                              size: 16, color: Colors.white)
                          : null,
                    ),
                  ),
                ),
              if (item.isVideo && isFavorite)
                Positioned(
                  top: 8,
                  left: 8,
                  child: Container(
                    width: 28,
                    height: 28,
                    decoration: BoxDecoration(
                      color: theme.elevatedSurface.withValues(alpha: 0.92),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.favorite,
                      color: Colors.red.shade400,
                      size: 16,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showDetails(BuildContext context) {
    return LibraryItemDetailsSheet.showForItem(
      context,
      item,
      isFavorite: isFavorite,
      onToggleFavorite: onAddToFavorites,
    );
  }

  Widget _buildPreview(BuildContext context, FileItem item) {
    final theme = Theme.of(context);
    if (item.isDirectory) {
      return Container(
        height: previewHeight,
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              theme.colorScheme.primary.withValues(alpha: 0.20),
              theme.colorScheme.secondary.withValues(alpha: 0.16),
              theme.colorScheme.primary.withValues(alpha: 0.10),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: theme.elevatedSurface.withValues(alpha: 0.76),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(
                Icons.folder_rounded,
                color: theme.colorScheme.primary,
                size: 24,
              ),
            ),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: theme.elevatedSurface.withValues(alpha: 0.76),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                LibraryItemUi.folderVideoLabel(item.videoCount),
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: theme.colorScheme.primary,
                ),
              ),
            ),
          ],
        ),
      );
    }

    if (item.isVideo) {
      final video = LibraryItemUi.videoFromFileItem(item);
      return SizedBox(
        height: previewHeight,
        width: double.infinity,
        child: LazyThumbnail(
          video: video,
          placeholder: const _GridThumbnailPlaceholder(),
        ),
      );
    }

    return Container(
      height: previewHeight,
      width: double.infinity,
      decoration: BoxDecoration(
        color: theme.subtleSurface,
      ),
      child: Icon(
        Icons.insert_drive_file,
        color: theme.mutedText,
        size: 28,
      ),
    );
  }
}

class _GridThumbnailPlaceholder extends StatelessWidget {
  const _GridThumbnailPlaceholder();

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF334155), Color(0xFF0F172A), Color(0xFF1E293B)],
        ),
      ),
      child: const Center(
        child: Icon(
          Icons.play_circle_outline_rounded,
          color: Colors.white24,
          size: 34,
        ),
      ),
    );
  }
}

class _GridMetaChip extends StatelessWidget {
  final String label;
  final Color color;

  const _GridMetaChip({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: theme.isDarkMode ? 0.18 : 0.10),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          color: color,
        ),
      ),
    );
  }
}
