import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../controller/file_browser_controller.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/file_item.dart';
import '../widgets/file_browser/file_browser_content.dart';
import '../widgets/file_browser/path_breadcrumb.dart';
import '../../../player/presentation/screens/video_player_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late FileBrowserController _controller;

  @override
  void initState() {
    super.initState();
    _controller = FileBrowserController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _controller.initialize();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: _controller,
      child: Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        drawer: _buildDrawer(),
        appBar: _buildAppBar(),
        body: SafeArea(
          child: Column(
            children: [
              Consumer<FileBrowserController>(
                builder: (context, controller, _) {
                  return PathBreadcrumb(
                    currentPath: controller.currentPath,
                    onPathTap: (path) =>
                        controller.loadDirectory(path, addToHistory: true),
                  );
                },
              ),
              Expanded(
                child: Consumer<FileBrowserController>(
                  builder: (context, controller, _) {
                    return FileBrowserContent(
                      controller: controller,
                      onVideoTap: _openVideo,
                      onFolderTap: _navigateToFolder,
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      backgroundColor: const Color(0xFFF8FAFC),
      elevation: 0,
      leading: Consumer<FileBrowserController>(
        builder: (context, controller, _) {
          if (controller.isSelectionMode) {
            return IconButton(
              icon: const Icon(Icons.close, color: Color(0xFF1E293B)),
              onPressed: controller.exitSelectionMode,
            );
          }
          if (controller.canGoBack) {
            return IconButton(
              icon: const Icon(Icons.arrow_back, color: Color(0xFF1E293B)),
              onPressed: controller.goBack,
            );
          }
          return Builder(
            builder: (context) => IconButton(
              icon: const Icon(Icons.menu, color: Color(0xFF1E293B)),
              onPressed: () => Scaffold.of(context).openDrawer(),
            ),
          );
        },
      ),
      title: Consumer<FileBrowserController>(
        builder: (context, controller, _) {
          if (controller.isSelectionMode) {
            return Text(
              '${controller.selectedCount} selected',
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: Color(0xFF1E293B),
              ),
            );
          }
          return const Text(
            'Files',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: Color(0xFF1E293B),
            ),
          );
        },
      ),
      actions: [
        Consumer<FileBrowserController>(
          builder: (context, controller, _) {
            if (controller.isSelectionMode) {
              return Row(
                children: [
                  IconButton(
                    icon:
                        const Icon(Icons.select_all, color: Color(0xFF1E293B)),
                    onPressed: controller.selectAll,
                    tooltip: 'Select all',
                  ),
                ],
              );
            }
            return Row(
              children: [
                IconButton(
                  icon: Icon(
                    controller.showOnlyVideos
                        ? Icons.video_library
                        : Icons.video_library_outlined,
                    color: controller.showOnlyVideos
                        ? const Color(0xFF6366F1)
                        : const Color(0xFF1E293B),
                  ),
                  onPressed: controller.toggleShowOnlyVideos,
                  tooltip: controller.showOnlyVideos
                      ? 'Show all files'
                      : 'Show videos only',
                ),
                IconButton(
                  icon: Icon(
                    controller.isGridView ? Icons.list : Icons.grid_view,
                    color: const Color(0xFF1E293B),
                  ),
                  onPressed: controller.toggleViewMode,
                  tooltip: controller.isGridView ? 'List view' : 'Grid view',
                ),
                PopupMenuButton<String>(
                  icon: const Icon(Icons.sort, color: Color(0xFF1E293B)),
                  onSelected: (value) {
                    switch (value) {
                      case 'name':
                        controller.setSortBy(SortBy.name);
                        break;
                      case 'date':
                        controller.setSortBy(SortBy.date);
                        break;
                      case 'size':
                        controller.setSortBy(SortBy.size);
                        break;
                    }
                  },
                  itemBuilder: (context) => [
                    const PopupMenuItem(
                      value: 'name',
                      child: Row(
                        children: [
                          Icon(Icons.sort_by_alpha, size: 20),
                          SizedBox(width: 12),
                          Text('Name'),
                        ],
                      ),
                    ),
                    const PopupMenuItem(
                      value: 'date',
                      child: Row(
                        children: [
                          Icon(Icons.calendar_today, size: 20),
                          SizedBox(width: 12),
                          Text('Date'),
                        ],
                      ),
                    ),
                    const PopupMenuItem(
                      value: 'size',
                      child: Row(
                        children: [
                          Icon(Icons.data_usage, size: 20),
                          SizedBox(width: 12),
                          Text('Size'),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            );
          },
        ),
      ],
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

  void _navigateToFolder(String path) {
    _controller.navigateToFolder(
      FileItem(
        path: path,
        name: path.split('/').last,
        isDirectory: true,
        size: 0,
        modified: DateTime.now(),
      ),
    );
  }

  Widget _buildDrawer() {
    return Drawer(
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(24),
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.folder_copy, size: 40, color: Color(0xFF6366F1)),
                  SizedBox(height: 12),
                  Text(
                    'MPx Player',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF1E293B),
                    ),
                  ),
                  Text(
                    'File Browser',
                    style: TextStyle(
                      fontSize: 14,
                      color: Colors.grey,
                    ),
                  ),
                ],
              ),
            ),
            const Divider(),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Text(
                'QUICK ACCESS',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey[500],
                  letterSpacing: 1,
                ),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.storage, color: Color(0xFF1E293B)),
              title: const Text('Internal Storage'),
              onTap: () {
                Navigator.pop(context);
                _controller.loadDirectory('/storage/emulated/0');
              },
            ),
            ListTile(
              leading: const Icon(Icons.download, color: Color(0xFF1E293B)),
              title: const Text('Downloads'),
              onTap: () {
                Navigator.pop(context);
                _controller.loadDirectory('/storage/emulated/0/Download');
              },
            ),
            ListTile(
              leading: const Icon(Icons.movie, color: Color(0xFF1E293B)),
              title: const Text('Movies'),
              onTap: () {
                Navigator.pop(context);
                _controller.loadDirectory('/storage/emulated/0/Movies');
              },
            ),
            ListTile(
              leading:
                  const Icon(Icons.video_library, color: Color(0xFF1E293B)),
              title: const Text('Videos'),
              onTap: () {
                Navigator.pop(context);
                _controller.loadDirectory('/storage/emulated/0/Videos');
              },
            ),
            const Divider(),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Text(
                'APP',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey[500],
                  letterSpacing: 1,
                ),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.settings, color: Color(0xFF1E293B)),
              title: const Text('Settings'),
              onTap: () {
                Navigator.pop(context);
              },
            ),
          ],
        ),
      ),
    );
  }
}
