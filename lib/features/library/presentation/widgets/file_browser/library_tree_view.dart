import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../../data/datasources/directory_browser.dart';
import '../../../domain/entities/file_item.dart';
import '../../../services/library_index_service.dart';
import '../common/library_item_ui.dart';
import '../video/lazy_thumbnail.dart';

class LibraryTreeView extends StatefulWidget {
  final String rootPath;
  final void Function(String path) onFolderTap;
  final void Function(String path) onVideoTap;
  final void Function(String path)? onAddToFavorites;
  final void Function(String path)? onLongPress;
  final void Function(String path)? onSelectionToggle;
  final bool Function(String path) isSelected;
  final bool isSelectionMode;
  final Set<String> favoriteIds;
  final ScrollController? scrollController;

  const LibraryTreeView({
    super.key,
    required this.rootPath,
    required this.onFolderTap,
    required this.onVideoTap,
    this.onAddToFavorites,
    this.onLongPress,
    this.onSelectionToggle,
    required this.isSelected,
    required this.isSelectionMode,
    this.favoriteIds = const {},
    this.scrollController,
  });

  @override
  State<LibraryTreeView> createState() => _LibraryTreeViewState();
}

class _LibraryTreeViewState extends State<LibraryTreeView> {
  final Set<String> _expandedPaths = {};
  final Map<String, List<FileItem>> _childrenCache = {};
  final Map<String, bool> _loadingPaths = {};
  final DirectoryBrowser _browser = DirectoryBrowser();
  final LibraryIndexService _indexService = LibraryIndexService();

  @override
  void initState() {
    super.initState();
    _expandedPaths.add(widget.rootPath);
    _loadChildren(widget.rootPath);
  }

  Future<void> _loadChildren(String path) async {
    if (_childrenCache.containsKey(path)) return;
    setState(() => _loadingPaths[path] = true);
    try {
      final items = await _browser.listDirectory(path);
      final filtered = _prepareVisibleItems(items);
      if (mounted) {
        setState(() {
          _childrenCache[path] = filtered;
          _loadingPaths[path] = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _loadingPaths[path] = false);
      }
    }
  }

  List<FileItem> _prepareVisibleItems(List<FileItem> items) {
    final rootPath = _browser.getRootPath();
    final preparedItems = List<FileItem>.from(items);

    for (final item in preparedItems) {
      if (!item.isDirectory) continue;
      final indexedCount =
          _indexService.getFolderVideoCount(rootPath, item.path);
      final cachedCount = _browser.getVideoCount(item.path);
      item.videoCount = indexedCount ?? cachedCount;
    }

    return preparedItems.where((item) {
      if (item.isVideo) return true;
      if (item.isDirectory) {
        if (item.videoCount == null) return true;
        return item.videoCount! > 0;
      }
      return false;
    }).toList();
  }

  void _toggleExpanded(String path) {
    setState(() {
      if (_expandedPaths.contains(path)) {
        _expandedPaths.remove(path);
      } else {
        _expandedPaths.add(path);
        _loadChildren(path);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async {
        _childrenCache.clear();
        await _loadChildren(widget.rootPath);
      },
      child: ListView.builder(
        controller: widget.scrollController,
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: 1,
        itemBuilder: (context, index) {
          return _buildNode(widget.rootPath, level: 0);
        },
      ),
    );
  }

  Widget _buildNode(String path, {required int level}) {
    final children = _childrenCache[path];
    final isExpanded = _expandedPaths.contains(path);
    final isLoading = _loadingPaths[path] == true;

    if (children == null && !isLoading) {
      _loadChildren(path);
      return const SizedBox.shrink();
    }

    if (children == null || children.isEmpty) {
      return const SizedBox.shrink();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (level > 0)
          _buildFolderTile(path, level, isExpanded),
        if (isExpanded && isLoading)
          Padding(
            padding: EdgeInsets.only(left: 24.0 * (level + 1)),
            child: const SizedBox(
              height: 24,
              width: 24,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          ),
        if (isExpanded)
          ...children.map((child) {
            if (child.isDirectory) {
              return _buildNode(child.path, level: level + 1);
            } else if (child.isVideo) {
              return _buildVideoTile(child, level + 1);
            }
            return const SizedBox.shrink();
          }),
      ],
    );
  }

  Widget _buildFolderTile(String path, int level, bool isExpanded) {
    final theme = Theme.of(context);
    final name = path.split('/').last;
    final isSelected = widget.isSelected(path);

    return RepaintBoundary(
      child: Padding(
        padding: EdgeInsets.only(left: 16.0 * level),
        child: ListTile(
          dense: true,
          leading: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (widget.isSelectionMode)
                GestureDetector(
                  onTap: () => widget.onSelectionToggle?.call(path),
                  child: Container(
                    width: 20,
                    height: 20,
                    margin: const EdgeInsets.only(right: 8),
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
                        ? const Icon(Icons.check, size: 14, color: Colors.white)
                        : null,
                  ),
                ),
              AnimatedRotation(
                turns: isExpanded ? 0.25 : 0,
                duration: const Duration(milliseconds: 200),
                child: Icon(
                  Icons.chevron_right,
                  size: 18,
                  color: theme.faintText,
                ),
              ),
              const SizedBox(width: 4),
              Icon(
                isExpanded ? Icons.folder_open_rounded : Icons.folder_rounded,
                size: 20,
                color: theme.colorScheme.primary,
              ),
            ],
          ),
          title: Text(
            name,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: theme.strongText,
            ),
          ),
          trailing: Text(
            LibraryItemUi.folderVideoLabel(
              _childrenCache[path]?.where((c) => c.isVideo).length ??
                  _browser.getVideoCount(path),
            ),
            style: TextStyle(
              fontSize: 11,
              color: theme.mutedText,
            ),
          ),
          onTap: () {
            if (widget.isSelectionMode) {
              widget.onSelectionToggle?.call(path);
            } else {
              _toggleExpanded(path);
            }
          },
          onLongPress: () => widget.onLongPress?.call(path),
        ),
      ),
    );
  }

  Widget _buildVideoTile(FileItem item, int level) {
    final theme = Theme.of(context);
    final isSelected = widget.isSelected(item.path);
    final isFavorite =
        widget.favoriteIds.contains(item.path.hashCode.toString());
    final video = LibraryItemUi.videoFromFileItem(item);

    return RepaintBoundary(
      child: Padding(
        padding: EdgeInsets.only(left: 16.0 * level),
        child: ListTile(
          dense: true,
          leading: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (widget.isSelectionMode)
                GestureDetector(
                  onTap: () => widget.onSelectionToggle?.call(item.path),
                  child: Container(
                    width: 20,
                    height: 20,
                    margin: const EdgeInsets.only(right: 8),
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
                        ? const Icon(Icons.check, size: 14, color: Colors.white)
                        : null,
                  ),
                )
              else
                const SizedBox(width: 22),
              ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: SizedBox(
                  width: 40,
                  height: 28,
                  child: LazyThumbnail(
                    video: video,
                    placeholder: Container(
                      color: theme.subtleSurface,
                      child: Icon(
                        Icons.play_circle_outline,
                        size: 16,
                        color: theme.mutedText,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          title: Text(
            item.name,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: theme.strongText,
            ),
          ),
          subtitle: Text(
            item.formattedSize,
            style: TextStyle(
              fontSize: 11,
              color: theme.mutedText,
            ),
          ),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (isFavorite)
                Icon(
                  Icons.favorite,
                  color: Colors.red.shade400,
                  size: 14,
                ),
              Icon(
                Icons.play_arrow_rounded,
                color: theme.colorScheme.primary.withValues(alpha: 0.7),
                size: 20,
              ),
            ],
          ),
          onTap: () {
            if (widget.isSelectionMode) {
              widget.onSelectionToggle?.call(item.path);
            } else {
              widget.onVideoTap(item.path);
            }
          },
          onLongPress: () => widget.onLongPress?.call(item.path),
        ),
      ),
    );
  }
}
