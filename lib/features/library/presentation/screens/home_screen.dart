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
                child: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Color(0xFFF8FAFC), Color(0xFFF1F5F9)],
                    ),
                  ),
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
                      Padding(
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
                        child: PathBreadcrumb(
                          currentPath: controller.currentPath,
                          onPathTap: (path) => controller.loadDirectory(path,
                              addToHistory: true),
                        ),
                      ),
                      Expanded(
                        child: FileBrowserContent(
                          controller: controller,
                          favoriteIds: _favoriteIds,
                          onVideoTap: _openVideo,
                          onFolderTap: (path) => controller.loadDirectory(path,
                              addToHistory: true),
                          onAddToFavorites: _addToFavorites,
                          scrollController: _scrollController,
                        ),
                      ),
                    ],
                  ),
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
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.05),
              blurRadius: 18,
              offset: const Offset(0, 8),
            ),
          ],
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Row(
          children: [
            _SelectionActionButton(
              icon: Icons.close,
              onTap: controller.exitSelectionMode,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${controller.selectedCount} selected',
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(height: 2),
                  const Text(
                    'Select more items or manage them in one go.',
                    style: TextStyle(
                      color: Color(0xFF64748B),
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
            _SelectionActionButton(
              icon: Icons.delete_outline,
              tint: const Color(0xFFDC2626),
              background: const Color(0xFFFEE2E2),
              onTap: () async {
                final confirmed = await showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('Delete Selected'),
                    content: Text(
                      'Are you sure you want to delete ${controller.selectedCount} items? This cannot be undone.',
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context, false),
                        child: const Text('Cancel'),
                      ),
                      TextButton(
                        onPressed: () => Navigator.pop(context, true),
                        style: TextButton.styleFrom(
                          foregroundColor: Colors.red,
                        ),
                        child: const Text('Delete'),
                      ),
                    ],
                  ),
                );

                if (confirmed == true) {
                  await controller.deleteSelected();
                }
              },
            ),
            const SizedBox(width: 8),
            _SelectionActionButton(
              icon: Icons.select_all,
              onTap: controller.selectAll,
            ),
          ],
        ),
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

class _SelectionActionButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  final Color tint;
  final Color background;

  const _SelectionActionButton({
    required this.icon,
    required this.onTap,
    this.tint = const Color(0xFF475569),
    this.background = Colors.white,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Icon(icon, color: tint, size: 22),
      ),
    );
  }
}
