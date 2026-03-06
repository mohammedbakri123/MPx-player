import 'package:flutter/material.dart';
import '../../../controller/file_browser_controller.dart';
import '../../../domain/entities/file_item.dart';
import 'file_list_item.dart';

class FileBrowserContent extends StatelessWidget {
  final FileBrowserController controller;
  final void Function(String path) onVideoTap;
  final void Function(String path) onFolderTap;

  const FileBrowserContent({
    super.key,
    required this.controller,
    required this.onVideoTap,
    required this.onFolderTap,
  });

  @override
  Widget build(BuildContext context) {
    if (controller.isLoading) {
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

    final items = controller.filteredItems;

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

    if (controller.isGridView) {
      return _buildGridView(items);
    }

    return RefreshIndicator(
      onRefresh: () async => controller.refresh(),
      child: ListView.separated(
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
          );
        },
      ),
    );
  }

  Widget _buildGridView(List<FileItem> items) {
    return RefreshIndicator(
      onRefresh: () async => controller.refresh(),
      child: GridView.builder(
        padding: const EdgeInsets.all(24),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          childAspectRatio: 0.9,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
        ),
        itemCount: items.length,
        itemBuilder: (context, index) {
          final item = items[index];
          final isSelected = controller.isSelected(item.path);

          return _GridItem(
            item: item,
            isSelectionMode: controller.isSelectionMode,
            isSelected: isSelected,
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
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onSelectionToggle;

  const _GridItem({
    required this.item,
    required this.isSelectionMode,
    required this.isSelected,
    required this.onTap,
    required this.onLongPress,
    required this.onSelectionToggle,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Container(
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
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _buildIcon(item.isDirectory, item.isVideo),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: Text(
                    item.name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF1E293B),
                    ),
                  ),
                ),
                if (!item.isDirectory) ...[
                  const SizedBox(height: 4),
                  Text(
                    item.formattedSize,
                    style: TextStyle(
                      fontSize: 11,
                      color: Colors.grey.shade500,
                    ),
                  ),
                ],
              ],
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
          ],
        ),
      ),
    );
  }

  Widget _buildIcon(bool isDirectory, bool isVideo) {
    if (isDirectory) {
      return Container(
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          color: const Color(0xFF6366F1).withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Icon(
          Icons.folder,
          color: Color(0xFF6366F1),
          size: 28,
        ),
      );
    }

    if (isVideo) {
      return Container(
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          color: const Color(0xFF6366F1).withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Icon(
          Icons.movie,
          color: Color(0xFF6366F1),
          size: 28,
        ),
      );
    }

    return Container(
      width: 56,
      height: 56,
      decoration: BoxDecoration(
        color: Colors.grey.shade200,
        borderRadius: BorderRadius.circular(16),
      ),
      child:
          Icon(Icons.insert_drive_file, color: Colors.grey.shade600, size: 28),
    );
  }
}
