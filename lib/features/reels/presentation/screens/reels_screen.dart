import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:mpx/features/reels/controllers/reels_controller.dart';
import 'package:mpx/features/reels/presentation/widgets/reel_player_item.dart';
import 'package:mpx/features/library/controller/file_browser_controller.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

class ReelsScreen extends StatefulWidget {
  final bool isActive;
  final String? folderPath;

  const ReelsScreen({
    super.key,
    this.isActive = true,
    this.folderPath,
  });

  @override
  State<ReelsScreen> createState() => _ReelsScreenState();
}

class _ReelsScreenState extends State<ReelsScreen> with WidgetsBindingObserver {
  late PageController _pageController;
  int _currentPage = 0;
  bool _isAppActive = true;
  bool _showExitHint = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _pageController = PageController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        Provider.of<ReelsController>(context, listen: false).loadReels();
      }
    });
    Future<void>.delayed(const Duration(seconds: 4), () {
      if (mounted) {
        setState(() => _showExitHint = false);
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (mounted) {
      setState(() {
        _isAppActive = state == AppLifecycleState.resumed;
      });
    }
  }

  @override
  void didUpdateWidget(ReelsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isActive != oldWidget.isActive) {
      // Rebuild to pass new isActive state to children
      setState(() {});
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pageController.dispose();
    super.dispose();
  }

  void _onPageChanged(int page) {
    if (mounted) {
      setState(() {
        _currentPage = page;
      });
    }
  }

  Future<void> _importFolder() async {
    final fileBrowserController =
        Provider.of<FileBrowserController>(context, listen: false);
    final reelsController =
        Provider.of<ReelsController>(context, listen: false);

    final currentPath = fileBrowserController.currentPath;

    if (currentPath.isEmpty) {
      _showSnackBar('Please navigate to a folder first.', Colors.orange);
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
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: color,
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isCustomFolder = widget.folderPath != null;

    return isCustomFolder
        ? ChangeNotifierProvider(
            create: (_) => ReelsController(targetFolderPath: widget.folderPath),
            child: _buildContent(context, theme, isCustomFolder),
          )
        : _buildContent(context, theme, isCustomFolder);
  }

  Widget _buildContent(
      BuildContext context, ThemeData theme, bool isCustomFolder) {
    return Consumer<ReelsController>(
      builder: (context, controller, child) {
        if (controller.isLoading && controller.reelsVideos.isEmpty) {
          return const Scaffold(
            backgroundColor: Colors.black,
            body: Center(child: CircularProgressIndicator()),
          );
        }

        if (controller.error != null) {
          return Scaffold(
            body: Center(
              child: Text(
                'Error: ${controller.error}',
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ),
          );
        }

        if (controller.reelsVideos.isEmpty) {
          return Scaffold(
            backgroundColor: theme.appBackground,
            body: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.video_library_rounded,
                    size: 80,
                    color: theme.faintText,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'No Reels yet!',
                    style: theme.textTheme.headlineSmall
                        ?.copyWith(color: theme.strongText),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Navigate to a folder in Home and tap the import button, or add videos via the video details sheet.',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(color: theme.mutedText),
                  ),
                  if (!isCustomFolder &&
                      controller.reelsFolderPath != null) ...[
                    const SizedBox(height: 24),
                    Container(
                      padding: const EdgeInsets.all(12),
                      margin: const EdgeInsets.symmetric(horizontal: 24),
                      decoration: BoxDecoration(
                        color: theme.elevatedSurface,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: theme.softBorder),
                      ),
                      child: Column(
                        children: [
                          Text(
                            'Or add/delete your own videos externally at:',
                            style: TextStyle(
                                color: theme.strongText,
                                fontWeight: FontWeight.w600,
                                fontSize: 12),
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 6),
                          SelectableText(
                            controller.reelsFolderPath!,
                            style: TextStyle(
                              color: theme.colorScheme.primary,
                              fontFamily: 'monospace',
                              fontSize: 12,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                  if (!isCustomFolder)
                    ElevatedButton.icon(
                      onPressed: _importFolder,
                      icon: const Icon(Icons.folder_open_rounded),
                      label: const Text('Import Current Folder'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: theme.colorScheme.primary,
                        foregroundColor: theme.colorScheme.onPrimary,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 24, vertical: 12),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          );
        }

        return Scaffold(
          backgroundColor: Colors.black,
          body: RefreshIndicator(
            onRefresh: () async {
              await controller.loadReels();
            },
            child: Stack(
              children: [
                PageView.builder(
                  controller: _pageController,
                  scrollDirection: Axis.vertical,
                  onPageChanged: _onPageChanged,
                  itemCount: controller.reelsVideos.length,
                  itemBuilder: (context, index) {
                    final video = controller.reelsVideos[index];
                    return ReelPlayerItem(
                      video: video,
                      isCurrentlyVisible: widget.isActive &&
                          _currentPage == index &&
                          _isAppActive,
                    );
                  },
                ),
                if (!isCustomFolder)
                  Positioned(
                    top: 40,
                    right: 20,
                    child: SafeArea(
                      child: FloatingActionButton(
                        onPressed: _importFolder,
                        mini: true,
                        backgroundColor:
                            theme.elevatedSurface.withValues(alpha: 0.7),
                        child: Icon(Icons.add_box_rounded,
                            color: theme.strongText),
                      ),
                    ),
                  ),
                Positioned(
                  top: 40,
                  right: isCustomFolder ? 20 : 80,
                  child: SafeArea(
                    child: Container(
                      decoration: BoxDecoration(
                        color: theme.elevatedSurface.withValues(alpha: 0.7),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: PopupMenuButton<ReelsSortOrder>(
                        icon: Icon(Icons.sort_rounded, color: theme.strongText),
                        onSelected: controller.changeSortOrder,
                        itemBuilder: (context) => [
                          const PopupMenuItem(
                            value: ReelsSortOrder.dateDesc,
                            child: Text('Newest First'),
                          ),
                          const PopupMenuItem(
                            value: ReelsSortOrder.dateAsc,
                            child: Text('Oldest First'),
                          ),
                          const PopupMenuItem(
                            value: ReelsSortOrder.nameAsc,
                            child: Text('By Name'),
                          ),
                          const PopupMenuItem(
                            value: ReelsSortOrder.shuffle,
                            child: Text('Shuffle'),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                if (!isCustomFolder)
                  Positioned(
                    top: 40,
                    left: 20,
                    child: SafeArea(
                      child: AnimatedOpacity(
                        opacity: _showExitHint ? 1 : 0.72,
                        duration: const Duration(milliseconds: 260),
                        child: IgnorePointer(
                          ignoring: true,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 14,
                              vertical: 10,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.48),
                              borderRadius: BorderRadius.circular(18),
                              border: Border.all(
                                color: Colors.white.withValues(alpha: 0.08),
                              ),
                            ),
                            child: const Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.swipe_right_alt_rounded,
                                  color: Colors.white,
                                  size: 18,
                                ),
                                SizedBox(width: 8),
                                Text(
                                  'Swipe right to leave Reels',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                if (isCustomFolder)
                  Positioned(
                    top: 40,
                    left: 20,
                    child: SafeArea(
                      child: FloatingActionButton(
                        onPressed: () => Navigator.pop(context),
                        mini: true,
                        backgroundColor:
                            theme.elevatedSurface.withValues(alpha: 0.7),
                        child: Icon(Icons.arrow_back_rounded,
                            color: theme.strongText),
                      ),
                    ),
                  ),
                if (isCustomFolder)
                  Positioned(
                    top: 96,
                    left: 20,
                    child: SafeArea(
                      child: IgnorePointer(
                        ignoring: true,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 8,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.4),
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: const Text(
                            'Tap back to return',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                if (controller.isLoading)
                  const Positioned(
                    top: 50,
                    right: 20,
                    child: CircularProgressIndicator(),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}
