import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:mpx/features/reels/controllers/reels_controller.dart';
import 'package:mpx/features/reels/presentation/widgets/reel_player_item.dart';
import 'package:mpx/features/reels/presentation/widgets/empty_reels_view.dart';
import 'package:mpx/features/reels/presentation/widgets/reels_sort_menu.dart';
import 'package:mpx/features/reels/presentation/widgets/reels_swipe_hint.dart';
import 'package:mpx/features/reels/presentation/widgets/custom_folder_back_button.dart';
import 'package:mpx/features/reels/presentation/widgets/custom_folder_back_hint.dart';
import 'package:mpx/features/library/controller/file_browser_controller.dart';

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
      setState(() {});
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pageController.dispose();
    super.dispose();
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
          return EmptyReelsView(
            isCustomFolder: isCustomFolder,
            onImportFolder: isCustomFolder ? null : _importFolder,
            reelsFolderPath: controller.reelsFolderPath,
          );
        }

        return Scaffold(
          backgroundColor: Colors.black,
          body: RefreshIndicator(
            onRefresh: controller.loadReels,
            child: Stack(
              children: [
                PageView.builder(
                  controller: _pageController,
                  scrollDirection: Axis.vertical,
                  onPageChanged: (page) => setState(() => _currentPage = page),
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
                Positioned(
                  top: 40,
                  right: 20,
                  child: SafeArea(
                    child: ReelsSortMenu(
                      onSortSelected: controller.changeSortOrder,
                      theme: theme,
                    ),
                  ),
                ),
                if (!isCustomFolder)
                  Positioned(
                    top: 40,
                    left: 20,
                    child: SafeArea(
                      child: ReelsSwipeHint(showExitHint: _showExitHint),
                    ),
                  ),
                if (isCustomFolder) ...[
                  Positioned(
                    top: 40,
                    left: 20,
                    child: SafeArea(
                      child: CustomFolderBackButton(
                        onBack: () => Navigator.pop(context),
                        theme: theme,
                      ),
                    ),
                  ),
                  const Positioned(
                    top: 96,
                    left: 20,
                    child: SafeArea(
                      child: CustomFolderBackHint(),
                    ),
                  ),
                ],
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
