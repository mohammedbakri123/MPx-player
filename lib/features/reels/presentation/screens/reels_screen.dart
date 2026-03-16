import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:mpx/features/reels/controllers/reels_controller.dart';
import 'package:mpx/features/reels/presentation/widgets/reel_player_item.dart';
import 'package:mpx/features/library/controller/file_browser_controller.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

class ReelsScreen extends StatefulWidget {
  const ReelsScreen({super.key});

  @override
  State<ReelsScreen> createState() => _ReelsScreenState();
}

class _ReelsScreenState extends State<ReelsScreen> {
  late PageController _pageController;
  int _currentPage = 0;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        Provider.of<ReelsController>(context, listen: false).loadReels();
      }
    });
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _onPageChanged(int page) {
    setState(() {
      _currentPage = page;
    });
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
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

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
                  if (controller.reelsFolderPath != null) ...[
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
                      isCurrentlyVisible: _currentPage == index,
                    );
                  },
                ),
                Positioned(
                  top: 40,
                  right: 20,
                  child: SafeArea(
                    child: FloatingActionButton(
                      onPressed: _importFolder,
                      mini: true,
                      backgroundColor:
                          theme.elevatedSurface.withValues(alpha: 0.7),
                      child:
                          Icon(Icons.add_box_rounded, color: theme.strongText),
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
