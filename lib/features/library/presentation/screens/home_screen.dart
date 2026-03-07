import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollDirection;
import 'package:provider/provider.dart';
import '../../controller/file_browser_controller.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/file_item.dart';
import '../widgets/file_browser/file_browser_content.dart';
import '../widgets/file_browser/path_breadcrumb.dart';
import '../widgets/home/home_fab.dart';
import '../widgets/home/home_header.dart';
import '../../../player/presentation/screens/video_player_screen.dart';
import '../../../favorites/services/favorites_service.dart';

import 'search_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late FileBrowserController _controller;
  final ScrollController _scrollController = ScrollController();
  bool _isFabVisible = true;
  Set<String> _favoriteIds = {};

  @override
  void initState() {
    super.initState();
    _controller = FileBrowserController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _controller.initialize();
      _loadFavorites();
    });
    _scrollController.addListener(_onScroll);
  }

  Future<void> _loadFavorites() async {
    final favorites = await FavoritesService.getFavorites();
    if (mounted) {
      setState(() {
        _favoriteIds = favorites.map((v) => v.id).toSet();
      });
    }
  }

  Future<void> _toggleFavorite(VideoFile video) async {
    await FavoritesService.toggleFavorite(video);
    await _loadFavorites();
  }

  void _onScroll() {
    if (_scrollController.position.userScrollDirection ==
        ScrollDirection.reverse) {
      if (_isFabVisible) setState(() => _isFabVisible = false);
    } else if (_scrollController.position.userScrollDirection ==
        ScrollDirection.forward) {
      if (!_isFabVisible) setState(() => _isFabVisible = true);
    }
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: _controller,
      child: Consumer<FileBrowserController>(
        builder: (context, controller, child) {
          final shouldIntercept =
              controller.isSelectionMode || controller.canGoBack;
          return PopScope(
            canPop: !shouldIntercept,
            onPopInvokedWithResult: (didPop, _) {
              if (didPop) return;

              if (controller.isSelectionMode) {
                controller.exitSelectionMode();
                return;
              }

              if (controller.canGoBack) {
                controller.goBack();
                return;
              }
            },
            child: Scaffold(
              backgroundColor: const Color(0xFFF8FAFC),
              body: SafeArea(
                child: Column(
                  children: [
                    if (controller.isSelectionMode)
                      _buildSelectionHeader(context, controller)
                    else
                      HomeHeader(
                        onSearchTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const SearchScreen(),
                            ),
                          );
                        },
                      ),
                    PathBreadcrumb(
                      currentPath: controller.currentPath,
                      onPathTap: (path) =>
                          controller.loadDirectory(path, addToHistory: true),
                    ),
                    Expanded(
                      child: FileBrowserContent(
                        controller: controller,
                        favoriteIds: _favoriteIds,
                        onVideoTap: _openVideo,
                        onFolderTap: (path) =>
                            controller.loadDirectory(path, addToHistory: true),
                        onAddToFavorites: _addToFavorites,
                        scrollController: _scrollController,
                      ),
                    ),
                  ],
                ),
              ),
              floatingActionButton: AnimatedOpacity(
                opacity: _isFabVisible ? 1.0 : 0.0,
                duration: const Duration(milliseconds: 200),
                curve: Curves.easeInOut,
                child: IgnorePointer(
                  ignoring: !_isFabVisible,
                  child: const HomeFAB(),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildSelectionHeader(
      BuildContext context, FileBrowserController controller) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
      child: Row(
        children: [
          GestureDetector(
            onTap: controller.exitSelectionMode,
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(14),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.04),
                    blurRadius: 10,
                    offset: const Offset(0, 2),
                  ),
                ],
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: Icon(Icons.close, color: Colors.grey.shade600, size: 22),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Text(
              '${controller.selectedCount} selected',
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                letterSpacing: -0.5,
              ),
            ),
          ),
          GestureDetector(
            onTap: () async {
              final confirmed = await showDialog<bool>(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('Delete Selected'),
                  content: Text(
                      'Are you sure you want to delete ${controller.selectedCount} items? This cannot be undone.'),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: const Text('Cancel'),
                    ),
                    TextButton(
                      onPressed: () => Navigator.pop(context, true),
                      style: TextButton.styleFrom(foregroundColor: Colors.red),
                      child: const Text('Delete'),
                    ),
                  ],
                ),
              );

              if (confirmed == true) {
                await controller.deleteSelected();
              }
            },
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: Colors.red.shade50,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: Colors.red.shade100),
              ),
              child: Icon(Icons.delete_outline,
                  color: Colors.red.shade400, size: 22),
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: controller.selectAll,
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(14),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.04),
                    blurRadius: 10,
                    offset: const Offset(0, 2),
                  ),
                ],
                border: Border.all(color: Colors.grey.shade200),
              ),
              child:
                  Icon(Icons.select_all, color: Colors.grey.shade600, size: 22),
            ),
          ),
        ],
      ),
    );
  }

  void _openVideo(String path) {
    final fileItem = FileItem(
      path: path,
      name: path.split('/').last,
      isDirectory: false,
      size: 0,
      modified: DateTime.now(),
    );
    final folderPath = _controller.currentPath;
    final videoFile = VideoFile.fromFileItem(fileItem, folderPath);

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => VideoPlayerScreen(video: videoFile),
      ),
    );
  }

  void _addToFavorites(String path) {
    final fileItem = FileItem(
      path: path,
      name: path.split('/').last,
      isDirectory: false,
      size: 0,
      modified: DateTime.now(),
    );
    final folderPath = _controller.currentPath;
    final videoFile = VideoFile.fromFileItem(fileItem, folderPath);
    _toggleFavorite(videoFile);
  }
}
