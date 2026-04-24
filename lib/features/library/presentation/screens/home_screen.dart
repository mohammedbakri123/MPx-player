import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollDirection;
import 'package:provider/provider.dart';
import '../../../../core/theme/app_theme_tokens.dart';
import '../../controller/file_browser_controller.dart';
import '../../controller/library_view_controller.dart';
import '../../domain/entities/video_file.dart';
import '../widgets/file_browser/file_browser_content.dart';
import '../widgets/file_browser/path_breadcrumb.dart';
import '../widgets/common/library_item_ui.dart';
import '../widgets/home/home_fab.dart';
import '../widgets/home/home_header.dart';
import '../widgets/home/home_selection_header.dart';
import '../widgets/home/home_sort_sheet.dart';
import '../../../player/presentation/screens/video_player_screen.dart';
import '../../../favorites/services/favorites_service.dart';
import '../../../../features/reels/controllers/reels_controller.dart';
import '../../../../features/reels/presentation/screens/reels_screen.dart';
import 'search_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with AutomaticKeepAliveClientMixin {
  final ScrollController _scrollController = ScrollController();
  bool _isFabVisible = true;
  Set<String> _favoriteIds = {};

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final controller = context.read<FileBrowserController>();
      if (!controller.isInitialized) {
        controller.initialize();
      }
      _loadFavorites();
    });
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _importCurrentFolderToReels() async {
    final reelsController =
        Provider.of<ReelsController>(context, listen: false);
    final controller = context.read<FileBrowserController>();
    final currentPath = controller.currentPath;

    if (currentPath.isEmpty) {
      _showSnackBar('Please navigate to a folder first.', Colors.orange);
      return;
    }

    if (currentPath == controller.getRootPath) {
      _showSnackBar(
          'Cannot import root directory. Please navigate to a specific folder.',
          Colors.red);
      return;
    }

    try {
      await reelsController.importFolderToReels(currentPath);
      _showSnackBar('Folder imported to Reels!', Colors.green);
    } catch (e) {
      _showSnackBar('Failed to import folder: $e', Colors.red);
    }
  }

  void _showSnackBar(String message, Color color) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _playCurrentFolderAsReels() {
    final controller = context.read<FileBrowserController>();
    final currentPath = controller.currentPath;

    if (currentPath.isEmpty || currentPath == controller.getRootPath) {
      _showSnackBar('Please navigate to a specific folder to play as Reels.',
          Colors.orange);
      return;
    }

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ReelsScreen(folderPath: currentPath),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final theme = Theme.of(context);
    return Consumer2<FileBrowserController, LibraryViewController>(
      builder: (context, controller, viewController, child) {
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
            backgroundColor: theme.appBackground,
            floatingActionButtonLocation:
                FloatingActionButtonLocation.endFloat,
            body: SafeArea(
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [theme.appBackground, theme.appBackgroundAlt],
                  ),
                ),
                child: Column(
                  children: [
                    if (!controller.isSelectionMode)
                      HomeHeader(
                        viewMode: viewController.viewMode,
                        onSortTap: () =>
                            showHomeSortSheet(context, controller),
                        onViewModeChanged: viewController.setViewMode,
                        onSearchTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                              builder: (_) => const SearchScreen()),
                        ),
                        onAddReelTap: _importCurrentFolderToReels,
                        onPlayFolderAsReelsTap: _playCurrentFolderAsReels,
                      )
                    else
                      HomeSelectionHeader(
                        controller: controller,
                        onClose: controller.exitSelectionMode,
                        onDelete: () => _confirmAndDelete(controller),
                        onSelectAll: controller.selectAll,
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
                        viewController: viewController,
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
    );
  }

  Future<void> _loadFavorites() async {
    final favorites = await FavoritesService.getFavorites();
    if (mounted) {
      setState(() => _favoriteIds = favorites.map((v) => v.id).toSet());
    }
  }

  void _onScroll() {
    final direction = _scrollController.position.userScrollDirection;
    final isVisible = _isFabVisible;
    if (direction == ScrollDirection.reverse && isVisible) {
      setState(() => _isFabVisible = false);
    } else if (direction == ScrollDirection.forward && !isVisible) {
      setState(() => _isFabVisible = true);
    }
  }

  Future<void> _confirmAndDelete(FileBrowserController controller) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Selected'),
        content: Text(
            'Are you sure you want to delete ${controller.selectedCount} items? This cannot be undone.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) await controller.deleteSelected();
  }

  void _openVideo(String path) {
    final videoFile = LibraryItemUi.videoFromPath(path);
    Navigator.push(context,
        MaterialPageRoute(builder: (_) => VideoPlayerScreen(video: videoFile)));
  }

  void _addToFavorites(String path) {
    final videoFile = LibraryItemUi.videoFromPath(path);
    _toggleFavorite(videoFile);
  }

  Future<void> _toggleFavorite(VideoFile video) async {
    await FavoritesService.toggleFavorite(video);
    await _loadFavorites();
  }
}
