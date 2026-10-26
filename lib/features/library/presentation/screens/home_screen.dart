import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controller/library_controller.dart';
import '../../models/file_item.dart';
import '../../models/folder_item.dart';
import '../widgets/breadcrumb_bar.dart';
import '../widgets/file_details_sheet.dart';
import '../widgets/file_list_item.dart';
import '../widgets/folder_card.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final TextEditingController _searchController = TextEditingController();
  bool _isSearching = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<LibraryController>().load();
      }
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _showCreateFolderDialog(BuildContext context) {
    final TextEditingController nameController = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (BuildContext ctx) {
        return AlertDialog(
          title: const Text('Create Folder'),
          content: TextField(
            controller: nameController,
            autofocus: true,
            decoration: const InputDecoration(
              labelText: 'Folder Name',
              hintText: 'e.g. My Favorites',
              border: OutlineInputBorder(),
            ),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final String text = nameController.text.trim();
                if (text.isNotEmpty) {
                  context.read<LibraryController>().addFolder(text);
                  Navigator.of(ctx).pop();
                }
              },
              child: const Text('Create'),
            ),
          ],
        );
      },
    );
  }

  void _showFileDetails(BuildContext context, FileItem file) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (BuildContext ctx) {
        return FileDetailsSheet(
          file: file,
          onDelete: () {
            context.read<LibraryController>().deleteFile(file.id);
          },
        );
      },
    );
  }

  void _playFile(BuildContext context, FileItem file) {
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: <Widget>[
            Icon(
              file.isAudio ? Icons.music_note : Icons.movie,
              color: Colors.white,
              size: 20,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Playing: ${file.name}',
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final LibraryController controller = context.watch<LibraryController>();
    final ColorScheme scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: _isSearching
            ? TextField(
                controller: _searchController,
                autofocus: true,
                decoration: InputDecoration(
                  hintText: 'Search files across library...',
                  border: InputBorder.none,
                  hintStyle: TextStyle(
                    color: scheme.onSurfaceVariant.withValues(alpha: 0.7),
                  ),
                ),
                onChanged: (String val) => controller.setSearchQuery(val),
              )
            : const Text(
                'MPx Library',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
        actions: <Widget>[
          IconButton(
            tooltip: _isSearching ? 'Close search' : 'Search files',
            icon: Icon(_isSearching ? Icons.close : Icons.search_rounded),
            onPressed: () {
              setState(() {
                _isSearching = !_isSearching;
                if (!_isSearching) {
                  _searchController.clear();
                  controller.setSearchQuery('');
                }
              });
            },
          ),
          IconButton(
            tooltip: 'New Folder',
            icon: const Icon(Icons.create_new_folder_outlined),
            onPressed: () => _showCreateFolderDialog(context),
          ),
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert),
            tooltip: 'Options',
            itemBuilder: (BuildContext ctx) => <PopupMenuEntry<String>>[
              const PopupMenuItem<String>(
                value: 'refresh',
                child: Row(
                  children: <Widget>[
                    Icon(Icons.refresh, size: 20),
                    SizedBox(width: 8),
                    Text('Refresh'),
                  ],
                ),
              ),
              const PopupMenuItem<String>(
                value: 'reset_db',
                child: Row(
                  children: <Widget>[
                    Icon(Icons.restart_alt_rounded, size: 20),
                    SizedBox(width: 8),
                    Text('Reset Dummy Data'),
                  ],
                ),
              ),
            ],
            onSelected: (String action) {
              if (action == 'refresh') {
                controller.refresh();
              } else if (action == 'reset_db') {
                controller.resetDummyData();
              }
            },
          ),
        ],
      ),
      body: Column(
        children: <Widget>[
          if (!_isSearching)
            BreadcrumbBar(
              breadcrumbs: controller.breadcrumbs,
              onSelect: (FolderItem? folder) {
                controller.navigateToBreadcrumb(folder);
              },
              onBack: () => controller.navigateUp(),
            ),
          _buildFilterBar(context, controller),
          Expanded(
            child: _buildBody(context, controller),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterBar(BuildContext context, LibraryController controller) {
    final ColorScheme scheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Row(
        children: <Widget>[
          FilterChip(
            label: const Text('All'),
            selected: controller.filterKind == null,
            onSelected: (_) => controller.setFilterKind(null),
            visualDensity: VisualDensity.compact,
          ),
          const SizedBox(width: 8),
          FilterChip(
            avatar: Icon(
              Icons.music_note_rounded,
              size: 16,
              color: controller.filterKind == FileKind.audio
                  ? scheme.onPrimaryContainer
                  : scheme.onSurfaceVariant,
            ),
            label: const Text('Audio'),
            selected: controller.filterKind == FileKind.audio,
            onSelected: (_) => controller.setFilterKind(FileKind.audio),
            visualDensity: VisualDensity.compact,
          ),
          const SizedBox(width: 8),
          FilterChip(
            avatar: Icon(
              Icons.movie_rounded,
              size: 16,
              color: controller.filterKind == FileKind.video
                  ? scheme.onPrimaryContainer
                  : scheme.onSurfaceVariant,
            ),
            label: const Text('Video'),
            selected: controller.filterKind == FileKind.video,
            onSelected: (_) => controller.setFilterKind(FileKind.video),
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
    );
  }

  Widget _buildBody(BuildContext context, LibraryController controller) {
    if (controller.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (controller.errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const Icon(Icons.error_outline, size: 48, color: Colors.redAccent),
              const SizedBox(height: 12),
              Text(
                controller.errorMessage!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.redAccent),
              ),
              const SizedBox(height: 16),
              FilledButton.tonal(
                onPressed: () => controller.refresh(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_isSearching && controller.searchQuery.isNotEmpty) {
      return _buildSearchResults(context, controller);
    }

    final List<FolderItem> folders = controller.folders;
    final List<FileItem> files = controller.files;

    if (folders.isEmpty && files.isEmpty) {
      return _buildEmptyState(context);
    }

    return LayoutBuilder(
      builder: (BuildContext ctx, BoxConstraints constraints) {
        final int folderColumns = constraints.maxWidth > 900
            ? 4
            : constraints.maxWidth > 600
                ? 3
                : constraints.maxWidth > 400
                    ? 2
                    : 1;

        return CustomScrollView(
          slivers: <Widget>[
            if (folders.isNotEmpty) ...<Widget>[
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 6),
                  child: Row(
                    children: <Widget>[
                      const Text(
                        'Folders',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 0.5,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          '${folders.length}',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                sliver: SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: folderColumns,
                    mainAxisExtent: 72,
                    mainAxisSpacing: 8,
                    crossAxisSpacing: 8,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (BuildContext ctx, int index) {
                      final FolderItem folder = folders[index];
                      return FolderCard(
                        folder: folder,
                        onTap: () => controller.openFolder(folder),
                        onDelete: () => controller.deleteFolder(folder.id),
                      );
                    },
                    childCount: folders.length,
                  ),
                ),
              ),
            ],
            if (files.isNotEmpty) ...<Widget>[
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 6),
                  child: Row(
                    children: <Widget>[
                      const Text(
                        'Files',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 0.5,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          '${files.length}',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (BuildContext ctx, int index) {
                      final FileItem file = files[index];
                      return FileListItem(
                        file: file,
                        onTap: () => _showFileDetails(context, file),
                        onPlay: () => _playFile(context, file),
                        onDelete: () => controller.deleteFile(file.id),
                      );
                    },
                    childCount: files.length,
                  ),
                ),
              ),
            ],
            const SliverToBoxAdapter(child: SizedBox(height: 24)),
          ],
        );
      },
    );
  }

  Widget _buildSearchResults(
    BuildContext context,
    LibraryController controller,
  ) {
    final List<FileItem> results = controller.searchResults;
    if (results.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(
              Icons.search_off_rounded,
              size: 48,
              color: Theme.of(context).colorScheme.outline,
            ),
            const SizedBox(height: 12),
            Text(
              'No files matching "${controller.searchQuery}"',
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      itemCount: results.length,
      itemBuilder: (BuildContext ctx, int index) {
        final FileItem file = results[index];
        return FileListItem(
          file: file,
          onTap: () => _showFileDetails(context, file),
          onPlay: () => _playFile(context, file),
          onDelete: () => controller.deleteFile(file.id),
        );
      },
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(
            Icons.folder_open_rounded,
            size: 64,
            color: scheme.outlineVariant,
          ),
          const SizedBox(height: 16),
          Text(
            'This folder is empty',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: scheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Add subfolders or reset dummy data from the top menu.',
            style: TextStyle(
              fontSize: 13,
              color: scheme.outline,
            ),
          ),
          const SizedBox(height: 16),
          FilledButton.tonalIcon(
            icon: const Icon(Icons.add_rounded),
            label: const Text('Add Subfolder'),
            onPressed: () => _showCreateFolderDialog(context),
          ),
        ],
      ),
    );
  }
}
