import 'package:flutter/material.dart';
import '../../../controller/file_browser_controller.dart';
import '../../../domain/entities/file_item.dart';
import '../../../domain/entities/video_file.dart';
import '../common/library_item_details_sheet.dart';
import '../video/video_thumbnail.dart';
import 'file_list_item.dart';

class FileBrowserContent extends StatelessWidget {
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
    final items = controller.filteredItems;

    if (controller.isLoading && items.isEmpty) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    if (controller.error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 48, color: Colors.grey[400]),
            const SizedBox(height: 16),
            Text(
              controller.error!,
              style: TextStyle(color: Colors.grey[600]),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: controller.refresh,
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    if (items.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.folder_open, size: 64, color: Colors.grey[300]),
            const SizedBox(height: 16),
            Text(
              controller.showOnlyVideos ? 'No videos found' : 'Empty folder',
              style: TextStyle(
                fontSize: 16,
                color: Colors.grey[600],
              ),
            ),
          ],
        ),
      );
    }

    final content = controller.isGridView
        ? _buildGridView(items)
        : RefreshIndicator(
            onRefresh: () async => controller.refresh(),
            child: ListView.separated(
              controller: scrollController,
              padding: const EdgeInsets.all(24),
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

    return Stack(
      children: [
        content,
        if (controller.isLoading)
          Positioned(
            top: 10,
            left: 24,
            right: 24,
            child: IgnorePointer(
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.95),
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
                    const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2.2),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      'Refreshing library...',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: Colors.grey.shade700,
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
      onRefresh: () async => controller.refresh(),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;
          final crossAxisCount = width >= 1200
              ? 4
              : width >= 840
                  ? 3
                  : width >= 520
                      ? 2
                      : 1;
          final cardWidth =
              (width - 48 - ((crossAxisCount - 1) * 14)) / crossAxisCount;
          final previewHeight = cardWidth.clamp(140.0, 190.0);
          final mainAxisExtent = previewHeight + 120;

          return GridView.builder(
            controller: scrollController,
            padding: const EdgeInsets.all(24),
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
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Container(
        clipBehavior: Clip.antiAlias,
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
        child: Stack(
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildPreview(item),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              item.name,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF0F172A),
                                height: 1.2,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Wrap(
                              spacing: 6,
                              runSpacing: 6,
                              children: [
                                _GridMetaChip(
                                  label: item.isDirectory
                                      ? '${item.videoCount ?? 0} videos'
                                      : item.formattedSize,
                                  color: item.isDirectory
                                      ? const Color(0xFF2563EB)
                                      : const Color(0xFFEA580C),
                                ),
                                _GridMetaChip(
                                  label: item.isDirectory
                                      ? _folderTimeLabel(item)
                                      : item.extension.toUpperCase(),
                                  color: const Color(0xFF334155),
                                ),
                              ],
                            ),
                          ],
                        ),
                        Text(
                          item.isDirectory
                              ? 'Updated ${_relativeDate(item.modified)}'
                              : '${_folderName(item.path)} • ${_relativeDate(item.modified)}',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                            color: Colors.grey.shade600,
                            height: 1.3,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            if (!isSelectionMode)
              Positioned(
                top: 10,
                right: 10,
                child: GestureDetector(
                  onTap: () => _showDetails(context),
                  child: Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.45),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.more_horiz,
                      size: 18,
                      color: Colors.white,
                    ),
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
                      color:
                          isSelected ? const Color(0xFF6366F1) : Colors.white,
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
              ),
            if (item.isVideo && isFavorite)
              Positioned(
                top: 8,
                left: 8,
                child: Icon(
                  Icons.favorite,
                  color: Colors.red.shade400,
                  size: 20,
                ),
              ),
          ],
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

  Widget _buildPreview(FileItem item) {
    if (item.isDirectory) {
      return Container(
        height: previewHeight,
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [
              Color(0xFFDBEAFE),
              Color(0xFFBFDBFE),
              Color(0xFFE0F2FE),
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
                color: Colors.white.withValues(alpha: 0.75),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Icon(
                Icons.folder_rounded,
                color: Color(0xFF2563EB),
                size: 24,
              ),
            ),
            const Spacer(),
            Text(
              '${item.videoCount ?? 0} videos',
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: Color(0xFF1D4ED8),
              ),
            ),
          ],
        ),
      );
    }

    if (item.isVideo) {
      final video = VideoFile.fromFileItem(
        item,
        item.path.substring(0, item.path.lastIndexOf('/')),
      );
      return SizedBox(
        height: previewHeight,
        width: double.infinity,
        child: VideoThumbnail(video: video, isFavorite: false),
      );
    }

    return Container(
      height: previewHeight,
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.grey.shade200,
      ),
      child: Icon(
        Icons.insert_drive_file,
        color: Colors.grey.shade600,
        size: 28,
      ),
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

  String _folderTimeLabel(FileItem item) {
    if (item.videoCount == null) {
      return 'Folder';
    }
    return item.videoCount == 1 ? '1 item' : '${item.videoCount} items';
  }

  String _folderName(String path) {
    final lastSeparator = path.lastIndexOf('/');
    if (lastSeparator <= 0) {
      return '/';
    }
    return path.substring(0, lastSeparator).split('/').last;
  }
}

class _GridMetaChip extends StatelessWidget {
  final String label;
  final Color color;

  const _GridMetaChip({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
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
